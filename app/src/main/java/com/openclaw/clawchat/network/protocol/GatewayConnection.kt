package com.openclaw.clawchat.network.protocol

import android.util.Log
import com.openclaw.clawchat.BuildConfig
import com.openclaw.clawchat.network.DynamicTrustManager
import com.openclaw.clawchat.network.WebSocketConnectionState
import com.openclaw.clawchat.security.SecurityModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.UUID

/**
 * Gateway 连接管理器（协议 v3）
 *
 * 握手流程（源码验证）：
 * 1. WebSocket 连接建立
 * 2. 收到 type=event, event=connect.challenge（含 nonce）
 * 3. 构建 v3 签名载荷 → Ed25519 签名 → 发送 type=req, method=connect
 * 4. 收到 type=res（hello-ok）→ 提取 deviceToken / snapshot / features
 *
 * 消息格式：
 * - 发送 chat.send：含 sessionKey + message + idempotencyKey
 * - 接收 chat 事件：runId / seq / state(delta|final|aborted|error) / message
 */
class GatewayConnection(
    private val okHttpClient: OkHttpClient,
    private val securityModule: SecurityModule,
    private val appScope: CoroutineScope
) {
    companion object {
        private const val TAG = "GatewayConnection"

        private const val AUTH_TIMEOUT_MS = 60_000L
        private const val REQUEST_TIMEOUT_MS = 30_000L
        private const val HEARTBEAT_INTERVAL_MS = 30_000L

        private const val INITIAL_RECONNECT_DELAY_MS = 1000L
        private const val MAX_RECONNECT_DELAY_MS = 30_000L
        private const val RECONNECT_BACKOFF_FACTOR = 2.0
        private const val MAX_RECONNECT_ATTEMPTS = 15

        private const val NONCE_LOG_PREFIX_LEN = 8

        private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    }

    // ── Public state ──

    private val _connectionState = MutableStateFlow<WebSocketConnectionState>(WebSocketConnectionState.Disconnected)
    val connectionState: StateFlow<WebSocketConnectionState> = _connectionState.asStateFlow()

    /** All non-auth frames forwarded as raw JSON */
    private val _incomingMessages = MutableSharedFlow<String>(replay = 0)
    val incomingMessages: SharedFlow<String> = _incomingMessages.asSharedFlow()

    /** hello-ok snapshot (available after connect) */
    var helloOkPayload: JsonObject? = null
        private set

    /** Default session key (from hello-ok snapshot.sessionDefaults.mainSessionKey) */
    var defaultSessionKey: String? = null
        private set

    // ── Internal components ──

    private var authHandler = ChallengeResponseAuth(securityModule)
    private val requestTracker = RequestTracker(timeoutMs = REQUEST_TIMEOUT_MS, scope = appScope)
    private val sequenceManager = SequenceManager()
    private val eventDeduplicator = EventDeduplicator()

    private var webSocket: WebSocket? = null
    private var reconnectJob: Job? = null
    private var heartbeatJob: Job? = null
    private var currentUrl: String? = null
    private var currentToken: String? = null
    private var reconnectAttempt = 0

    // ── Connection ──

    suspend fun connect(url: String, token: String? = null): Result<Unit> = withContext(Dispatchers.IO) {
        if (_connectionState.value is WebSocketConnectionState.Connected) {
            return@withContext Result.success(Unit)
        }

        _connectionState.value = WebSocketConnectionState.Connecting
        currentUrl = url
        currentToken = token
        reconnectAttempt = 0
        helloOkPayload = null
        defaultSessionKey = null

        // 提取 hostname 用于证书验证
        val hostname = try {
            val uri = java.net.URI(url)
            uri.host ?: url
        } catch (e: Exception) {
            url
        }
        DynamicTrustManager.setCurrentHostname(hostname)

        // Rebuild auth handler with token
        authHandler = ChallengeResponseAuth(
            securityModule = securityModule,
            gatewayToken = token ?: securityModule.getAuthToken()
        )

        try {
            val request = Request.Builder()
                .url(url)
                .build()

            webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    Log.i(TAG, "WebSocket opened, waiting for connect.challenge...")
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    handleIncomingFrame(text)
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    Log.e(TAG, "WebSocket failure: ${t.message}", t)
                    DynamicTrustManager.clearCurrentHostname()
                    _connectionState.value = WebSocketConnectionState.Error(t)
                    currentUrl?.let { scheduleReconnect(it, currentToken) }
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    Log.i(TAG, "WebSocket closed: $code $reason")
                    DynamicTrustManager.clearCurrentHostname()
                    _connectionState.value = WebSocketConnectionState.Disconnected
                    this@GatewayConnection.webSocket = null
                    heartbeatJob?.cancel()
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    webSocket.close(code, reason)
                }
            })

            // Wait for auth to complete (suspends until Connected or Error)
            val finalState = withTimeoutOrNull(AUTH_TIMEOUT_MS) {
                _connectionState.first {
                    it is WebSocketConnectionState.Connected || it is WebSocketConnectionState.Error
                }
            }

            if (finalState is WebSocketConnectionState.Connected) {
                startHeartbeat()
                Result.success(Unit)
            } else {
                Result.failure(IllegalStateException("Authentication timeout"))
            }
        } catch (e: Exception) {
            _connectionState.value = WebSocketConnectionState.Error(e)
            Result.failure(e)
        }
    }

    suspend fun disconnect(): Result<Unit> = withContext(Dispatchers.IO) {
        reconnectJob?.cancel(); reconnectJob = null
        heartbeatJob?.cancel(); heartbeatJob = null
        requestTracker.cancelAllRequests("Disconnecting")
        sequenceManager.reset()
        eventDeduplicator.clear()
        requestTracker.stop()
        webSocket?.close(1000, "User disconnect")
        webSocket = null
        _connectionState.value = WebSocketConnectionState.Disconnected
        currentUrl = null
        currentToken = null
        Result.success(Unit)
    }

    // ── Frame dispatch ──

    private fun handleIncomingFrame(text: String) {
        try {
            val obj = json.parseToJsonElement(text).jsonObject
            when (obj["type"]?.jsonPrimitive?.content) {
                "res" -> handleResFrame(text)
                "event" -> handleEventFrame(obj, text)
                else -> appScope.launch { _incomingMessages.emit(text) }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Frame parse error: ${e.message}")
            appScope.launch { _incomingMessages.emit(text) }
        }
    }

    /** Handle type=res: match RequestTracker (including connect hello-ok) */
    private fun handleResFrame(text: String) {
        appScope.launch {
            try {
                val response = json.decodeFromString<ResponseFrame>(text)
                requestTracker.completeRequest(response)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse res frame: ${e.message}")
            }
            // 也透传给上层
            _incomingMessages.emit(text)
        }
    }

    /** Handle type=event */
    private fun handleEventFrame(obj: JsonObject, rawText: String) {
        val event = obj["event"]?.jsonPrimitive?.content ?: return

        appScope.launch {
            // Sequence check + dedup
            val seq = obj["seq"]?.jsonPrimitive?.content?.toIntOrNull()
            when (sequenceManager.checkSequence(seq)) {
                is SequenceManager.SequenceResult.Duplicate,
                is SequenceManager.SequenceResult.Old -> return@launch
                else -> {}
            }
            val eventId = obj["stateVersion"]?.jsonPrimitive?.content
                ?: if (seq != null) "$event-$seq" else "$event-${UUID.randomUUID()}"
            if (eventDeduplicator.isAlreadySeen(eventId, seq)) return@launch

            when (event) {
                "connect.challenge" -> handleConnectChallenge(obj)
                // chat / tick / all other events → forward to upstream
                else -> _incomingMessages.emit(rawText)
            }

            sequenceManager.acknowledge(seq)
        }
    }

    // ── Auth handshake ──

    /**
     * Handle connect.challenge: sign → send connect req → await res via RequestTracker
     */
    private suspend fun handleConnectChallenge(obj: JsonObject) {
        try {
            val payload = obj["payload"]?.jsonObject ?: return
            val nonce = payload["nonce"]?.jsonPrimitive?.content
            val ts = payload["ts"]?.jsonPrimitive?.content?.toLongOrNull() ?: System.currentTimeMillis()

            if (nonce.isNullOrBlank()) {
                Log.e(TAG, "connect.challenge: empty nonce")
                return
            }

            if (BuildConfig.DEBUG) {
                Log.i(TAG, "connect.challenge received, nonce=${nonce.take(NONCE_LOG_PREFIX_LEN)}...")
            } else {
                Log.i(TAG, "connect.challenge received")
            }

            // 1. Handle challenge
            authHandler.handleChallenge(ConnectChallengePayload(nonce = nonce, timestamp = ts))

            // 2. Build connect request
            val connectReq = authHandler.buildConnectRequest()
            val requestId = "connect-${System.currentTimeMillis()}"

            val connectParams = buildConnectParams(connectReq)
            val requestFrame = RequestFrame(
                id = requestId,
                method = "connect",
                params = connectParams
            )

            // 3. Track request → send → await res
            val deferred = requestTracker.trackRequest(requestId, "connect")
            val frameJson = json.encodeToString(RequestFrame.serializer(), requestFrame)
            val sent = webSocket?.send(frameJson) ?: false

            if (!sent) {
                requestTracker.failRequest(requestId, IllegalStateException("WebSocket closed during auth"))
                _connectionState.value = WebSocketConnectionState.Error(
                    IllegalStateException("WebSocket closed during auth")
                )
                return
            }

            Log.i(TAG, "connect request sent, waiting for hello-ok res...")

            // 4. Await hello-ok response
            val response = withTimeout(AUTH_TIMEOUT_MS) { deferred.await() }

            if (response.ok) {
                handleHelloOk(response)
            } else {
                val errCode = response.error?.code ?: "UNKNOWN"
                val errMsg = response.error?.message ?: "Connect failed"
                Log.e(TAG, "connect rejected: $errCode - $errMsg")
                _connectionState.value = WebSocketConnectionState.Error(
                    IllegalStateException("$errCode: $errMsg")
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "connect.challenge handling failed: ${e.message}", e)
            _connectionState.value = WebSocketConnectionState.Error(e)
        }
    }

    /** Parse hello-ok response */
    private fun handleHelloOk(response: ResponseFrame) {
        val payload = response.payload?.jsonObject
        helloOkPayload = payload

        // Extract deviceToken
        val deviceToken = payload?.get("auth")?.jsonObject?.get("deviceToken")?.jsonPrimitive?.content
        if (!deviceToken.isNullOrBlank()) {
            securityModule.completePairing(deviceToken)
            Log.i(TAG, "deviceToken stored")
        }

        // Extract default session key
        defaultSessionKey = payload
            ?.get("snapshot")?.jsonObject
            ?.get("sessionDefaults")?.jsonObject
            ?.get("mainSessionKey")?.jsonPrimitive?.content

        Log.i(TAG, "hello-ok: defaultSessionKey=$defaultSessionKey")

        _connectionState.value = WebSocketConnectionState.Connected
        reconnectAttempt = 0
    }

    /** Build connect request params (aligned with ConnectParamsSchema) */
    private fun buildConnectParams(req: ConnectRequest): Map<String, JsonElement> {
        return mapOf(
            "minProtocol" to JsonPrimitive(3),
            "maxProtocol" to JsonPrimitive(3),
            "client" to buildJsonObject {
                put("id", "openclaw-android")
                put("version", req.client.clientVersion)
                put("platform", req.client.platform)
                put("mode", "ui")
                put("deviceFamily", "phone")
                put("modelIdentifier", req.client.deviceModel)
            },
            "role" to JsonPrimitive(req.role),
            "scopes" to JsonArray(req.scopes.map { JsonPrimitive(it) }),
            "caps" to JsonArray(emptyList()),
            "commands" to JsonArray(emptyList()),
            "permissions" to JsonObject(emptyMap()),
            "auth" to buildJsonObject {
                put("token", req.token ?: "")
            },
            "device" to buildJsonObject {
                put("id", req.device.id)
                put("publicKey", req.device.publicKey)
                put("signature", req.device.signature)
                put("signedAt", req.device.signedAt)
                put("nonce", req.device.nonce)
            },
            "locale" to JsonPrimitive("zh-CN"),
            "userAgent" to JsonPrimitive("openclaw-android/${req.client.clientVersion}")
        )
    }

    // ── RPC ──

    /** Send raw JSON frame */
    fun sendFrame(jsonText: String): Boolean {
        return webSocket?.send(jsonText) ?: false
    }

    /** Generic RPC call */
    suspend fun call(method: String, params: Map<String, JsonElement>? = null): ResponseFrame {
        val requestId = RequestIdGenerator.generateRequestId()
        val frame = RequestFrame(id = requestId, method = method, params = params)

        val deferred = requestTracker.trackRequest(requestId, method)
        val frameJson = json.encodeToString(RequestFrame.serializer(), frame)
        val sent = webSocket?.send(frameJson) ?: false

        if (!sent) {
            requestTracker.failRequest(requestId, IllegalStateException("WebSocket not connected"))
            throw IllegalStateException("WebSocket not connected")
        }

        return withTimeout(REQUEST_TIMEOUT_MS) { deferred.await() }
    }

    /** chat.send — with required idempotencyKey */
    suspend fun chatSend(sessionKey: String, message: String): ResponseFrame {
        return call("chat.send", mapOf(
            "sessionKey" to JsonPrimitive(sessionKey),
            "message" to JsonPrimitive(message),
            "idempotencyKey" to JsonPrimitive(UUID.randomUUID().toString())
        ))
    }

    /** chat.history */
    suspend fun chatHistory(sessionKey: String, limit: Int? = null): ResponseFrame {
        val params = mutableMapOf<String, JsonElement>(
            "sessionKey" to JsonPrimitive(sessionKey)
        )
        if (limit != null) params["limit"] = JsonPrimitive(limit)
        return call("chat.history", params)
    }

    /** chat.abort */
    suspend fun chatAbort(sessionKey: String, runId: String? = null): ResponseFrame {
        val params = mutableMapOf<String, JsonElement>(
            "sessionKey" to JsonPrimitive(sessionKey)
        )
        if (runId != null) params["runId"] = JsonPrimitive(runId)
        return call("chat.abort", params)
    }

    /** sessions.list */
    suspend fun sessionsList(limit: Int? = null, activeMinutes: Int? = null): ResponseFrame {
        val params = mutableMapOf<String, JsonElement>()
        if (limit != null) params["limit"] = JsonPrimitive(limit)
        if (activeMinutes != null) params["activeMinutes"] = JsonPrimitive(activeMinutes)
        return call("sessions.list", params.ifEmpty { null })
    }

    /** ping */
    suspend fun ping(): Result<Long> {
        return try {
            val start = System.currentTimeMillis()
            call("ping", mapOf("timestamp" to JsonPrimitive(start)))
            Result.success(System.currentTimeMillis() - start)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Measure latency (alias for ping) */
    suspend fun measureLatency(): Long? {
        return ping().getOrNull()
    }

    // ── Heartbeat / Reconnect ──

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = appScope.launch {
            while (_connectionState.value is WebSocketConnectionState.Connected) {
                try { ping() } catch (_: Exception) {}
                delay(HEARTBEAT_INTERVAL_MS)
            }
        }
    }

    private fun scheduleReconnect(url: String, token: String? = null) {
        reconnectJob?.cancel()

        if (reconnectAttempt >= MAX_RECONNECT_ATTEMPTS) {
            Log.e(TAG, "Max reconnect attempts ($MAX_RECONNECT_ATTEMPTS) reached, giving up")
            _connectionState.value = WebSocketConnectionState.Error(
                IllegalStateException("Max reconnect attempts reached")
            )
            return
        }

        val delayMs = (INITIAL_RECONNECT_DELAY_MS * Math.pow(RECONNECT_BACKOFF_FACTOR, reconnectAttempt.toDouble()))
            .toLong().coerceAtMost(MAX_RECONNECT_DELAY_MS)
        reconnectAttempt++

        reconnectJob = appScope.launch {
            delay(delayMs)
            if (_connectionState.value !is WebSocketConnectionState.Connected) {
                connect(url, token)
            }
        }
    }
}
