package com.openclaw.clawchat.network.protocol

import android.util.Log
import com.openclaw.clawchat.BuildConfig
import com.openclaw.clawchat.network.CertificateExceptionFirstTime
import com.openclaw.clawchat.network.CertificateExceptionMismatch
import com.openclaw.clawchat.network.DynamicTrustManager
import com.openclaw.clawchat.network.WebSocketConnectionState
import com.openclaw.clawchat.security.SecurityModule
import com.openclaw.clawchat.util.AppLog
import com.openclaw.clawchat.util.JsonUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import com.openclaw.clawchat.util.AppLog
import kotlinx.coroutines.flow.asSharedFlow
import com.openclaw.clawchat.util.AppLog
import kotlinx.coroutines.flow.asStateFlow
import com.openclaw.clawchat.util.AppLog
import kotlinx.coroutines.flow.first
import com.openclaw.clawchat.util.AppLog
import kotlinx.coroutines.launch
import com.openclaw.clawchat.util.AppLog
import kotlinx.coroutines.withContext
import com.openclaw.clawchat.util.AppLog
import kotlinx.coroutines.withTimeout
import com.openclaw.clawchat.util.AppLog
import kotlinx.coroutines.withTimeoutOrNull
import com.openclaw.clawchat.util.AppLog
import kotlinx.serialization.json.Json
import com.openclaw.clawchat.util.AppLog
import kotlinx.serialization.json.JsonArray
import com.openclaw.clawchat.util.AppLog
import kotlinx.serialization.json.JsonElement
import com.openclaw.clawchat.util.AppLog
import kotlinx.serialization.json.JsonObject
import com.openclaw.clawchat.util.AppLog
import kotlinx.serialization.json.JsonPrimitive
import com.openclaw.clawchat.util.AppLog
import kotlinx.serialization.json.buildJsonObject
import com.openclaw.clawchat.util.AppLog
import kotlinx.serialization.json.jsonArray
import com.openclaw.clawchat.util.AppLog
import kotlinx.serialization.json.jsonObject
import com.openclaw.clawchat.util.AppLog
import kotlinx.serialization.json.jsonPrimitive
import com.openclaw.clawchat.util.AppLog
import kotlinx.serialization.json.put
import com.openclaw.clawchat.util.AppLog
import okhttp3.OkHttpClient
import com.openclaw.clawchat.util.AppLog
import okhttp3.Request
import com.openclaw.clawchat.util.AppLog
import okhttp3.Response
import com.openclaw.clawchat.util.AppLog
import okhttp3.WebSocket
import com.openclaw.clawchat.util.AppLog
import okhttp3.WebSocketListener
import com.openclaw.clawchat.util.AppLog
import java.security.cert.CertificateException
import com.openclaw.clawchat.util.AppLog
import java.util.UUID
import com.openclaw.clawchat.util.AppLog

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
    }

    // 使用统一的 Json 配置（带 encodeDefaults）
    private val json = JsonUtils.jsonWithDefaults

    // ── Public state ──

    private val _connectionState = MutableStateFlow<WebSocketConnectionState>(WebSocketConnectionState.Disconnected)
    val connectionState: StateFlow<WebSocketConnectionState> = _connectionState.asStateFlow()

    /** All non-auth frames forwarded as raw JSON */
    private val _incomingMessages = MutableSharedFlow<String>(replay = 0)
    val incomingMessages: SharedFlow<String> = _incomingMessages.asSharedFlow()

    /** Certificate events for TOFU flow */
    private val _certificateEvent = MutableSharedFlow<CertificateEvent>(replay = 0)
    val certificateEvent: SharedFlow<CertificateEvent> = _certificateEvent.asSharedFlow()

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
                    
                    // 检查是否是证书问题
                    val certException = findCertificateException(t)
                    if (certException != null) {
                        Log.i(TAG, "Certificate verification failed, emitting certificate event")
                        val hostname = when (certException) {
                            is CertificateExceptionFirstTime -> certException.hostname
                            is CertificateExceptionMismatch -> certException.hostname
                            else -> "unknown"
                        }
                        val fingerprint = when (certException) {
                            is CertificateExceptionFirstTime -> certException.fingerprint
                            is CertificateExceptionMismatch -> certException.currentFingerprint
                            else -> ""
                        }
                        appScope.launch {
                            _certificateEvent.emit(
                                CertificateEvent(
                                    hostname = hostname,
                                    fingerprint = fingerprint,
                                    isMismatch = certException is CertificateExceptionMismatch,
                                    storedFingerprint = (certException as? CertificateExceptionMismatch)?.storedFingerprint
                                )
                            )
                        }
                        // 不触发重连，等待用户确认
                        _connectionState.value = WebSocketConnectionState.Error(t)
                    } else {
                        _connectionState.value = WebSocketConnectionState.Error(t)
                        currentUrl?.let { scheduleReconnect(it, currentToken) }
                    }
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
            // stateVersion may be a JsonObject { presence: number, health: number } or a primitive
            val stateVersionElement = obj["stateVersion"]
            val eventId = when (stateVersionElement) {
                is JsonPrimitive -> stateVersionElement.content
                is JsonObject -> stateVersionElement.toString()
                else -> if (seq != null) "$event-$seq" else "$event-${UUID.randomUUID()}"
            }
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

            // 调试：打印请求内容
            Log.i(TAG, "connect request params: ${JsonObject(connectParams)}")

            // 3. Track request → send → await res
            val deferred = requestTracker.trackRequest(requestId, "connect")
            val frameJson = json.encodeToString(RequestFrame.serializer(), requestFrame)
            Log.i(TAG, "connect request frame (will send): $frameJson")
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
                // 首次配对使用 pairingToken，已配对使用 token
                if (req.token != null && req.token.isNotBlank()) {
                    // 检查是否是 setupCode 格式（通常是 4-8 位数字或字母）
                    if (req.token.matches(Regex("^[A-Z0-9]{4,8}$"))) {
                        put("pairingToken", req.token)
                    } else {
                        put("token", req.token)
                    }
                }
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
        android.util.AppLog.d("GatewayConnection", "=== call: sending $method, requestId=$requestId, params=$params")
        val sent = webSocket?.send(frameJson) ?: false
        android.util.AppLog.d("GatewayConnection", "=== call: sent=$sent, webSocket connected=${webSocket != null}")

        if (!sent) {
            requestTracker.failRequest(requestId, IllegalStateException("WebSocket not connected"))
            throw IllegalStateException("WebSocket not connected")
        }

        return withTimeout(REQUEST_TIMEOUT_MS) { deferred.await() }
    }

    /** chat.send — with required idempotencyKey and optional attachments */
    suspend fun chatSend(
        sessionKey: String, 
        message: String, 
        attachments: List<ChatAttachmentData>? = null
    ): ResponseFrame {
        val params = mutableMapOf<String, JsonElement>(
            "sessionKey" to JsonPrimitive(sessionKey),
            "message" to JsonPrimitive(message),
            "idempotencyKey" to JsonPrimitive(UUID.randomUUID().toString())
        )
        
        // 添加附件（如果有）
        if (!attachments.isNullOrEmpty()) {
            val attachmentsArray = JsonArray(
                attachments.map { att ->
                    buildJsonObject {
                        put("type", "image")
                        put("mimeType", att.mimeType)
                        put("content", att.content)
                    }
                }
            )
            params["attachments"] = attachmentsArray
        }
        
        return call("chat.send", params)
    }

    /** chat.send — with attachments support (1:1 复刻 webchat) */
    suspend fun chatSendWithAttachments(
        sessionKey: String,
        message: String,
        attachments: List<com.openclaw.clawchat.ui.components.ApiAttachment>? = null
    ): ResponseFrame {
        val params = mutableMapOf<String, JsonElement>(
            "sessionKey" to JsonPrimitive(sessionKey),
            "message" to JsonPrimitive(message),
            "idempotencyKey" to JsonPrimitive(UUID.randomUUID().toString())
        )
        
        // 添加 attachments 参数（参考 webchat chat.ts sendChatMessage）
        if (!attachments.isNullOrEmpty()) {
            params["attachments"] = JsonArray(attachments.map { att ->
                JsonObject(mapOf(
                    "type" to JsonPrimitive(att.type),
                    "mimeType" to JsonPrimitive(att.mimeType),
                    "content" to JsonPrimitive(att.content)
                ))
            })
        }
        
        return call("chat.send", params)
    }

    /** chat.history */
    suspend fun chatHistory(sessionKey: String, limit: Int? = null): ResponseFrame {
        android.util.AppLog.d("GatewayConnection", "=== chatHistory called: sessionKey='$sessionKey', length=${sessionKey.length}, limit=$limit")
        val params = mutableMapOf<String, JsonElement>(
            "sessionKey" to JsonPrimitive(sessionKey)
        )
        if (limit != null) params["limit"] = JsonPrimitive(limit)
        android.util.AppLog.d("GatewayConnection", "=== chatHistory: calling 'chat.history' with params=$params")
        val response = call("chat.history", params)
        android.util.AppLog.d("GatewayConnection", "=== chatHistory response: ok=${response.isSuccess()}, error=${response.error}, payload type=${response.payload?.javaClass?.simpleName}")
        if (response.payload is JsonObject) {
            val messagesArray = (response.payload as JsonObject)["messages"]?.jsonArray
            android.util.AppLog.d("GatewayConnection", "=== chatHistory: messages count=${messagesArray?.size ?: 0}")
        }
        return response
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
    suspend fun sessionsList(
        limit: Int? = null,
        activeMinutes: Int? = null,
        includeDerivedTitles: Boolean = true,
        includeLastMessage: Boolean = true
    ): ResponseFrame {
        val params = mutableMapOf<String, JsonElement>()
        if (limit != null) params["limit"] = JsonPrimitive(limit)
        if (activeMinutes != null) params["activeMinutes"] = JsonPrimitive(activeMinutes)
        params["includeDerivedTitles"] = JsonPrimitive(includeDerivedTitles)
        params["includeLastMessage"] = JsonPrimitive(includeLastMessage)
        return call("sessions.list", params.ifEmpty { null })
    }

    /** sessions.patch — 设置会话属性如 verboseLevel */
    suspend fun sessionsPatch(sessionKey: String, verboseLevel: String? = null): ResponseFrame {
        val params = mutableMapOf<String, JsonElement>(
            "key" to JsonPrimitive(sessionKey)
        )
        if (verboseLevel != null) params["verboseLevel"] = JsonPrimitive(verboseLevel)
        return call("sessions.patch", params)
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

    // ── Certificate handling ──

    /**
     * 递归查找证书异常
     */
    private fun findCertificateException(t: Throwable): CertificateException? {
        var current: Throwable? = t
        while (current != null) {
            if (current is CertificateExceptionFirstTime || current is CertificateExceptionMismatch) {
                return current as CertificateException
            }
            current = current.cause
        }
        return null
    }
}

/**
 * 证书事件（用于 TOFU 流程）
 */
data class CertificateEvent(
    val hostname: String,
    val fingerprint: String,
    val isMismatch: Boolean,
    val storedFingerprint: String? = null
)

/**
 * 附件数据（用于 chat.send）
 */
data class ChatAttachmentData(
    val type: String = "image",
    val mimeType: String,
    val content: String   // base64 content (without data URL prefix)
)
