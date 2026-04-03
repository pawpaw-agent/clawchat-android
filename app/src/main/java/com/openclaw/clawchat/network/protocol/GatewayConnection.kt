package com.openclaw.clawchat.network.protocol

import com.openclaw.clawchat.util.AppLog
import com.openclaw.clawchat.BuildConfig
import com.openclaw.clawchat.network.CertificateExceptionFirstTime
import com.openclaw.clawchat.network.CertificateExceptionMismatch
import com.openclaw.clawchat.network.DynamicTrustManager
import com.openclaw.clawchat.network.WebSocketConnectionState
import com.openclaw.clawchat.security.SecurityModule
import com.openclaw.clawchat.util.JsonUtils
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
import kotlinx.coroutines.flow.update
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
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.security.cert.CertificateException
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
    }

    // 使用统一的 Json 配置（带 encodeDefaults）
    private val json = JsonUtils.jsonWithDefaults

    // ── Public state ──

    private val _connectionState = MutableStateFlow<WebSocketConnectionState>(WebSocketConnectionState.Disconnected)
    val connectionState: StateFlow<WebSocketConnectionState> = _connectionState.asStateFlow()

    /** All non-auth frames forwarded as raw JSON */
    private val _incomingMessages = MutableSharedFlow<String>(replay = 1)
    val incomingMessages: SharedFlow<String> = _incomingMessages.asSharedFlow()

    /** Certificate events for TOFU flow */
    private val _certificateEvent = MutableSharedFlow<CertificateEvent>(replay = 0)
    val certificateEvent: SharedFlow<CertificateEvent> = _certificateEvent.asSharedFlow()

    /** Tool stream events for real-time tool card updates */
    private val _toolStreamEvents = MutableStateFlow<Map<String, ToolStreamEvent>>(emptyMap())
    val toolStreamEvents: StateFlow<Map<String, ToolStreamEvent>> = _toolStreamEvents.asStateFlow()

    /** Tool stream order for UI rendering */
    private val _toolStreamOrder = MutableStateFlow<List<String>>(emptyList())
    val toolStreamOrder: StateFlow<List<String>> = _toolStreamOrder.asStateFlow()

    /** hello-ok snapshot (available after connect) */
    var helloOkPayload: JsonObject? = null
        private set

    /** Default session key (from hello-ok snapshot.sessionDefaults.mainSessionKey) */
    var defaultSessionKey: String? = null
        private set

    // ── Internal components ──

    private var authHandler = ChallengeResponseAuth(securityModule)
    private val requestTracker = RequestTracker(timeoutMs = GatewayConfig.REQUEST_TIMEOUT_MS, scope = appScope)
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
        // P0-1: 防止重复连接 - 检查已连接或正在连接状态
        val currentState = _connectionState.value
        if (currentState is WebSocketConnectionState.Connected) {
            return@withContext Result.success(Unit)
        }
        if (currentState is WebSocketConnectionState.Connecting) {
            AppLog.w(TAG, "connect() called while already connecting, skipping")
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
            // 从 WebSocket URL 提取 origin（用于 Gateway origin 检查）
            val origin = extractOrigin(url)
            
            val requestBuilder = Request.Builder()
                .url(url)
            
            // 添加 origin header（Gateway 要求 Control UI 客户端必须发送）
            if (origin != null) {
                requestBuilder.addHeader("Origin", origin)
                AppLog.d(TAG, "Adding Origin header: $origin")
            }
            
            val request = requestBuilder.build()

            webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    AppLog.i(TAG, "WebSocket opened, waiting for connect.challenge...")
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    handleIncomingFrame(text)
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    AppLog.e(TAG, "WebSocket failure: ${t.message}", t)
                    DynamicTrustManager.clearCurrentHostname()
                    
                    // 检查是否是证书问题
                    val certException = findCertificateException(t)
                    if (certException != null) {
                        AppLog.i(TAG, "Certificate verification failed, emitting certificate event")
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
                    AppLog.i(TAG, "WebSocket closed: $code $reason")
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
            val finalState = withTimeoutOrNull(GatewayConfig.AUTH_TIMEOUT_MS) {
                _connectionState.first {
                    it is WebSocketConnectionState.Connected || it is WebSocketConnectionState.Error
                }
            }

            if (finalState is WebSocketConnectionState.Connected) {
                startHeartbeat()
                Result.success(Unit)
            } else {
                // 更新状态为 Error（超时或认证失败）
                val errorState = (finalState as? WebSocketConnectionState.Error)
                    ?: WebSocketConnectionState.Error(IllegalStateException("Authentication timeout"))
                _connectionState.value = errorState
                Result.failure(errorState.throwable)
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
            AppLog.w(TAG, "Frame parse error: ${e.message}")
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
                AppLog.w(TAG, "Failed to parse res frame: ${e.message}")
            }
            // 也透传给上层
            _incomingMessages.emit(text)
        }
    }

    /** Handle type=event */
    private fun handleEventFrame(obj: JsonObject, rawText: String) {
        val event = obj["event"]?.jsonPrimitive?.content ?: return
        
        AppLog.d(TAG, "=== handleEventFrame: event=$event, rawText length=${rawText.length}")

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
                "agent" -> {
                    handleAgentEvent(obj)
                    // 也透传给 ChatEventHandler 处理
                    _incomingMessages.emit(rawText)
                }
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
                AppLog.e(TAG, "connect.challenge: empty nonce")
                return
            }

            if (BuildConfig.DEBUG) {
                AppLog.i(TAG, "connect.challenge received, nonce=${nonce.take(GatewayConfig.NONCE_LOG_PREFIX_LEN)}...")
            } else {
                AppLog.i(TAG, "connect.challenge received")
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
            AppLog.i(TAG, "connect request params: ${JsonObject(connectParams)}")

            // 3. Track request → send → await res
            val deferred = requestTracker.trackRequest(requestId, "connect")
            val frameJson = json.encodeToString(RequestFrame.serializer(), requestFrame)
            AppLog.i(TAG, "connect request frame (will send): $frameJson")
            val sent = webSocket?.send(frameJson) ?: false

            if (!sent) {
                requestTracker.failRequest(requestId, IllegalStateException("WebSocket closed during auth"))
                _connectionState.value = WebSocketConnectionState.Error(
                    IllegalStateException("WebSocket closed during auth")
                )
                return
            }

            AppLog.i(TAG, "connect request sent, waiting for hello-ok res...")

            // 4. Await hello-ok response
            val response = withTimeout(GatewayConfig.AUTH_TIMEOUT_MS) { deferred.await() }

            if (response.ok) {
                handleHelloOk(response)
            } else {
                val errCode = response.error?.code ?: "UNKNOWN"
                val errMsg = response.error?.message ?: "Connect failed"
                AppLog.e(TAG, "connect rejected: $errCode - $errMsg")
                _connectionState.value = WebSocketConnectionState.Error(
                    IllegalStateException("$errCode: $errMsg")
                )
            }
        } catch (e: Exception) {
            AppLog.e(TAG, "connect.challenge handling failed: ${e.message}", e)
            _connectionState.value = WebSocketConnectionState.Error(e)
        }
    }

    /**
     * Handle agent.event for tool stream updates
     * Gateway agent event 结构 (源码验证):
     *   { type: "event", event: "agent", payload: { runId, seq, stream: "tool", ts, data: {...}, sessionKey } }
     *   data: { phase, name, toolCallId, args?, result?, partialResult?, isError? }
     */
    private suspend fun handleAgentEvent(obj: JsonObject) {
        val payload = obj["payload"]?.jsonObject ?: return
        val stream = payload["stream"]?.jsonPrimitive?.content
        
        // 只处理 tool stream 事件
        if (stream != "tool") return
        
        // 工具信息在 payload.data 里
        val data = payload["data"]?.jsonObject ?: return
        
        val toolCallId = data["toolCallId"]?.jsonPrimitive?.content ?: return
        val name = data["name"]?.jsonPrimitive?.content ?: "unknown"
        val phase = data["phase"]?.jsonPrimitive?.content ?: "start"
        val resultContent = data["result"]?.jsonPrimitive?.content
        val partialResultContent = data["partialResult"]?.jsonPrimitive?.content
        val isError = data["isError"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
        val args = data["args"]?.jsonObject
        
        // 获取当前事件（用于追加流式内容）
        val currentEvent = _toolStreamEvents.value[toolCallId]
        val currentOutput = currentEvent?.output ?: ""
        
        // 处理流式内容：partialResult 是增量，result 是最终结果
        val finalOutput = when {
            // phase=result 表示完成：使用最终 result
            phase == "result" -> resultContent ?: partialResultContent ?: currentOutput
            // partialResult 是增量：追加到现有内容
            partialResultContent != null -> currentOutput + partialResultContent
            // result 直接输出：替换
            resultContent != null -> resultContent
            // 保持当前内容
            else -> currentOutput
        }
        
        val event = ToolStreamEvent(
            toolCallId = toolCallId,
            name = name,
            status = phase,  // 用 phase 替代 status
            title = null,
            output = finalOutput,
            error = if (isError) "Error" else null,
            stream = partialResultContent,
            timestamp = System.currentTimeMillis()
        )

        // P0-2: 使用 update{} 原子操作，避免竞态条件
        _toolStreamEvents.update { events ->
            events.toMutableMap().apply { this[toolCallId] = event }
        }

        // P0-2: 同样使用 update{} 原子操作更新顺序
        _toolStreamOrder.update { order ->
            order.toMutableList().apply {
                remove(toolCallId)
                add(toolCallId)
            }
        }

        AppLog.d(TAG, "Tool stream event: ${event.name} [${event.status}] partialResult=${partialResultContent?.take(50)} output=${finalOutput.take(50)}")
    }

    /** Parse hello-ok response */
    private fun handleHelloOk(response: ResponseFrame) {
        val payload = response.payload?.jsonObject
        helloOkPayload = payload

        // Extract deviceToken
        val deviceToken = payload?.get("auth")?.jsonObject?.get("deviceToken")?.jsonPrimitive?.content
        if (!deviceToken.isNullOrBlank()) {
            securityModule.completePairing(deviceToken)
            AppLog.i(TAG, "deviceToken stored")
        }

        // Extract default session key
        defaultSessionKey = payload
            ?.get("snapshot")?.jsonObject
            ?.get("sessionDefaults")?.jsonObject
            ?.get("mainSessionKey")?.jsonPrimitive?.content

        // Log full hello-ok response for debugging
        AppLog.i(TAG, "hello-ok response: ${payload}")

        // Extract granted scopes
        val grantedScopes = payload?.get("grantedScopes")?.jsonArray?.map { it.jsonPrimitive.content }
        AppLog.i(TAG, "hello-ok: defaultSessionKey=$defaultSessionKey, grantedScopes=$grantedScopes")

        _connectionState.value = WebSocketConnectionState.Connected
        reconnectAttempt = 0
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
        AppLog.d("GatewayConnection", "=== call: sending $method, requestId=$requestId, params=$params")
        val sent = webSocket?.send(frameJson) ?: false
        AppLog.d("GatewayConnection", "=== call: sent=$sent, webSocket connected=${webSocket != null}")

        if (!sent) {
            requestTracker.failRequest(requestId, IllegalStateException("WebSocket not connected"))
            throw IllegalStateException("WebSocket not connected")
        }

        return withTimeout(GatewayConfig.REQUEST_TIMEOUT_MS) { deferred.await() }
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
        AppLog.d("GatewayConnection", "=== chatHistory called: sessionKey='$sessionKey', length=${sessionKey.length}, limit=$limit")
        val params = mutableMapOf<String, JsonElement>(
            "sessionKey" to JsonPrimitive(sessionKey)
        )
        if (limit != null) params["limit"] = JsonPrimitive(limit)
        AppLog.d("GatewayConnection", "=== chatHistory: calling 'chat.history' with params=$params")
        val response = call("chat.history", params)
        AppLog.d("GatewayConnection", "=== chatHistory response: ok=${response.isSuccess()}, error=${response.error}, payload type=${response.payload?.javaClass?.simpleName}")
        if (response.payload is JsonObject) {
            val messagesArray = (response.payload as JsonObject)["messages"]?.jsonArray
            AppLog.d("GatewayConnection", "=== chatHistory: messages count=${messagesArray?.size ?: 0}")
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

    /** sessions.steer — 向运行中的会话发送引导消息 */
    suspend fun sessionsSteer(sessionKey: String, text: String): ResponseFrame {
        val params = mapOf(
            "sessionKey" to JsonPrimitive(sessionKey),
            "text" to JsonPrimitive(text)
        )
        return call("sessions.steer", params)
    }

    /** cron.list — 列出定时任务 */
    suspend fun cronList(): ResponseFrame {
        return call("cron.list", null)
    }

    /** cron.add — 创建定时任务 */
    suspend fun cronAdd(
        name: String,
        cron: String,
        sessionKey: String,
        prompt: String,
        enabled: Boolean = true
    ): ResponseFrame {
        val params = mapOf(
            "name" to JsonPrimitive(name),
            "cron" to JsonPrimitive(cron),
            "sessionKey" to JsonPrimitive(sessionKey),
            "prompt" to JsonPrimitive(prompt),
            "enabled" to JsonPrimitive(enabled)
        )
        return call("cron.add", params)
    }

    /** cron.remove — 删除定时任务 */
    suspend fun cronRemove(cronId: String): ResponseFrame {
        val params = mapOf("id" to JsonPrimitive(cronId))
        return call("cron.remove", params)
    }

    /** cron.run — 立即执行定时任务 */
    suspend fun cronRun(cronId: String): ResponseFrame {
        val params = mapOf("id" to JsonPrimitive(cronId))
        return call("cron.run", params)
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
                delay(GatewayConfig.HEARTBEAT_INTERVAL_MS)
            }
        }
    }

    private fun scheduleReconnect(url: String, token: String? = null) {
        reconnectJob?.cancel()

        if (reconnectAttempt >= GatewayConfig.MAX_RECONNECT_ATTEMPTS) {
            AppLog.e(TAG, "Max reconnect attempts (${GatewayConfig.MAX_RECONNECT_ATTEMPTS}) reached, giving up")
            _connectionState.value = WebSocketConnectionState.Error(
                IllegalStateException("Max reconnect attempts reached")
            )
            return
        }

        val delayMs = (GatewayConfig.INITIAL_RECONNECT_DELAY_MS * Math.pow(GatewayConfig.RECONNECT_BACKOFF_FACTOR, reconnectAttempt.toDouble()))
            .toLong().coerceAtMost(GatewayConfig.MAX_RECONNECT_DELAY_MS)
        reconnectAttempt++

        reconnectJob = appScope.launch {
            delay(delayMs)
            if (_connectionState.value !is WebSocketConnectionState.Connected) {
                connect(url, token)
            }
        }
    }

    // ── Certificate handling (moved to GatewayUtils.kt) ──
// ── Data types moved to GatewayTypes.kt ──
}
