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

    /** All non-auth frames forwarded as raw JSON (legacy — prefer [events] for new consumers) */
    private val _incomingMessages = MutableSharedFlow<String>(replay = 1)
    val incomingMessages: SharedFlow<String> = _incomingMessages.asSharedFlow()

    /** Typed events parsed from incoming frames — use instead of re-parsing [incomingMessages] */
    private val _events = MutableSharedFlow<GatewayEvent>(replay = 0, extraBufferCapacity = 64)
    val events: SharedFlow<GatewayEvent> = _events.asSharedFlow()

    /** Certificate events for TOFU flow */
    private val _certificateEvent = MutableSharedFlow<CertificateEvent>(replay = 0)
    val certificateEvent: SharedFlow<CertificateEvent> = _certificateEvent.asSharedFlow()

    /** Tool stream events for real-time tool card updates */
    private val _toolStreamEvents = MutableStateFlow<Map<String, ToolStreamEvent>>(emptyMap())
    val toolStreamEvents: StateFlow<Map<String, ToolStreamEvent>> = _toolStreamEvents.asStateFlow()

    /** Tool stream order for UI rendering */
    private val _toolStreamOrder = MutableStateFlow<List<String>>(emptyList())
    val toolStreamOrder: StateFlow<List<String>> = _toolStreamOrder.asStateFlow()

    /** Plan stream events for multi-step work tracking (OpenClaw v2026.4.24+) */
    private val _planStreamEvents = MutableStateFlow<PlanStreamEvent?>(null)
    val planStreamEvents: StateFlow<PlanStreamEvent?> = _planStreamEvents.asStateFlow()

    /** Item stream events for work items/tasks (OpenClaw v2026.4.24+) */
    private val _itemStreamEvents = MutableStateFlow<Map<String, ItemStreamEvent>>(emptyMap())
    val itemStreamEvents: StateFlow<Map<String, ItemStreamEvent>> = _itemStreamEvents.asStateFlow()

    /** Patch stream events for context changes (OpenClaw v2026.4.24+) */
    private val _patchStreamEvents = MutableStateFlow<Map<String, PatchStreamEvent>>(emptyMap())
    val patchStreamEvents: StateFlow<Map<String, PatchStreamEvent>> = _patchStreamEvents.asStateFlow()

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

    // ── RPC service delegates ──
    private val chatRpc = ChatRpcService(::call)
    private val sessionRpc = SessionRpcService(::call)
    private val agentRpc = AgentRpcService(::call)
    private val configRpc = ConfigRpcService(::call)
    private val cronRpc = CronRpcService(::call)
    private val sysRpc = SystemRpcService(::call)

    private var webSocket: WebSocket? = null
    private var reconnectJob: Job? = null
    private var tickMonitorJob: Job? = null
    private var currentUrl: String? = null

    /** Last seen tick timestamp (ms since epoch), used for stale detection */
    @Volatile
    private var lastTickTimestamp: Long = 0L

    /** Currently connected gateway URL */
    val connectedUrl: String? get() = currentUrl
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
                    tickMonitorJob?.cancel()
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
                startTickMonitor()
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
        tickMonitorJob?.cancel(); tickMonitorJob = null
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

            val payload = obj["payload"]?.jsonObject

            when (event) {
                "connect.challenge" -> handleConnectChallenge(obj)

                "agent" -> {
                    handleAgentEvent(obj)
                    val stream = payload?.get("stream")?.jsonPrimitive?.content ?: "unknown"
                    payload?.let { _events.emit(GatewayEvent.Agent(it, stream)) }
                    _incomingMessages.emit(rawText)
                }
                "tool.stream" -> {
                    payload?.let { _events.emit(GatewayEvent.ToolStream(it)) }
                    _incomingMessages.emit(rawText)
                }
                "chat" -> {
                    payload?.let { _events.emit(GatewayEvent.Chat(it)) }
                    _incomingMessages.emit(rawText)
                }
                "sessions.changed" -> {
                    _events.emit(GatewayEvent.SessionsChanged(payload ?: JsonObject(emptyMap())))
                    _incomingMessages.emit(rawText)
                }
                "session.message" -> {
                    payload?.let { _events.emit(GatewayEvent.SessionMessage(it)) }
                    _incomingMessages.emit(rawText)
                }
                "session.tool" -> {
                    payload?.let { _events.emit(GatewayEvent.SessionTool(it)) }
                    _incomingMessages.emit(rawText)
                }
                "shutdown" -> {
                    AppLog.i(TAG, "Gateway shutdown event received, disconnecting")
                    _events.emit(GatewayEvent.Shutdown(payload ?: JsonObject(emptyMap())))
                    _incomingMessages.emit(rawText)
                }
                "exec.approval.requested",
                "plugin.approval.requested" -> {
                    payload?.let { _events.emit(GatewayEvent.ApprovalRequested(it, event)) }
                    _incomingMessages.emit(rawText)
                }
                "exec.approval.resolved",
                "plugin.approval.resolved" -> {
                    payload?.let { _events.emit(GatewayEvent.ApprovalResolved(it, event)) }
                    _incomingMessages.emit(rawText)
                }
                "device.pair.requested",
                "device.pair.resolved" -> {
                    payload?.let { _events.emit(GatewayEvent.DevicePairEvent(it, event)) }
                    _incomingMessages.emit(rawText)
                }
                "update.available" -> {
                    payload?.let { _events.emit(GatewayEvent.UpdateAvailable(it)) }
                    _incomingMessages.emit(rawText)
                }
                "talk.mode" -> {
                    payload?.let { _events.emit(GatewayEvent.TalkMode(it)) }
                    _incomingMessages.emit(rawText)
                }
                "health" -> {
                    payload?.let { _events.emit(GatewayEvent.Health(it)) }
                    _incomingMessages.emit(rawText)
                }
                "cron", "presence" -> {
                    _events.emit(GatewayEvent.Passthrough(event, payload ?: JsonObject(emptyMap())))
                    _incomingMessages.emit(rawText)
                }
                "tick" -> {
                    val ts = payload?.get("ts")?.jsonPrimitive?.content?.toLongOrNull()
                        ?: System.currentTimeMillis()
                    lastTickTimestamp = ts
                    _events.emit(GatewayEvent.Passthrough(event, payload ?: JsonObject(emptyMap())))
                    _incomingMessages.emit(rawText)
                }
                "voicewake.changed", "heartbeat" ->
                    _incomingMessages.emit(rawText)
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

        // Handle tool stream events
        if (stream == "tool") {
            handleToolStreamEvent(payload)
            return
        }

        // Handle plan stream events (OpenClaw v2026.4.24+)
        if (stream == "plan") {
            handlePlanStreamEvent(payload)
            return
        }

        // Handle item stream events (OpenClaw v2026.4.24+)
        if (stream == "item") {
            handleItemStreamEvent(payload)
            return
        }

        // Handle patch stream events (OpenClaw v2026.4.24+)
        if (stream == "patch") {
            handlePatchStreamEvent(payload)
            return
        }

        // Handle command_output stream events (legacy rasp gateway compatibility)
        // Legacy gateway sends stream="command_output" instead of stream="tool"
        if (stream == "command_output") {
            handleCommandOutputStreamEvent(payload)
            return
        }
    }

    /**
     * Handle stream == "command_output" agent events (legacy rasp gateway compatibility)
     * Legacy gateway uses stream="command_output" instead of stream="tool"
     * These events have a similar structure but use different field names:
     *   - itemId instead of toolCallId
     *   - output instead of partialResult
     */
    private suspend fun handleCommandOutputStreamEvent(payload: JsonObject) {
        val data = payload["data"]?.jsonObject ?: return

        val itemId = data["itemId"]?.jsonPrimitive?.content ?: return
        val name = data["name"]?.jsonPrimitive?.content ?: "unknown"
        val phase = data["phase"]?.jsonPrimitive?.content ?: "start"
        val isError = data["isError"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
        val output = data["output"]?.jsonPrimitive?.content

        // 获取当前事件（用于追加流式内容）
        val currentEvent = _toolStreamEvents.value[itemId]
        val currentOutput = currentEvent?.output ?: ""

        // 处理流式内容：phase=delta 表示增量，phase=end 表示完成
        val finalOutput = when {
            phase == "end" -> output ?: currentOutput
            output != null -> currentOutput + output
            else -> currentOutput
        }

        val event = ToolStreamEvent(
            toolCallId = itemId,
            name = name,
            status = phase,
            title = data["title"]?.jsonPrimitive?.content,
            output = finalOutput,
            error = if (isError) "Error" else null,
            stream = output,
            timestamp = System.currentTimeMillis()
        )

        _toolStreamEvents.update { events ->
            events.toMutableMap().apply { this[itemId] = event }
        }

        _toolStreamOrder.update { order ->
            order.toMutableList().apply {
                remove(itemId)
                add(itemId)
            }
        }

        AppLog.d(TAG, "CommandOutput stream event (legacy compat): ${event.name} [${event.status}] output=${finalOutput.take(50)}")
    }

    /**
     * Handle stream == "tool" agent events
     * Tool execution streaming with phase: start, update, result
     */
    private suspend fun handleToolStreamEvent(payload: JsonObject) {
        val data = payload["data"]?.jsonObject ?: return

        val toolCallId = data["toolCallId"]?.jsonPrimitive?.content ?: return
        val name = data["name"]?.jsonPrimitive?.content ?: "unknown"
        val phase = data["phase"]?.jsonPrimitive?.content ?: "start"
        val isError = data["isError"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
        val args = data["args"]?.jsonObject

        // result 和 partialResult 是对象结构: { content: [{ type: "text", text: "..." }] }
        // 直接遍历 jsonArray 提取 text
        fun extractTextFromContent(jsonArray: JsonArray?): String? {
            if (jsonArray == null) return null
            // Use indices and get() instead of for-loop since JsonArray doesn't have iterator()
            for (i in 0 until jsonArray.size) {
                val element = jsonArray[i]
                if (element is JsonObject) {
                    val textElement = element.get("text")
                    if (textElement is JsonPrimitive) {
                        return textElement.content
                    }
                }
            }
            return null
        }
        val resultContent = extractTextFromContent(data["result"]?.jsonObject?.get("content")?.jsonArray)
        val partialResultContent = extractTextFromContent(data["partialResult"]?.jsonObject?.get("content")?.jsonArray)

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
            status = phase,
            title = null,
            output = finalOutput,
            error = if (isError) "Error" else null,
            stream = partialResultContent,
            timestamp = System.currentTimeMillis()
        )

        _toolStreamEvents.update { events ->
            events.toMutableMap().apply { this[toolCallId] = event }
        }

        _toolStreamOrder.update { order ->
            order.toMutableList().apply {
                remove(toolCallId)
                add(toolCallId)
            }
        }

        AppLog.d(TAG, "Tool stream event: ${event.name} [${event.status}] partialResult=${partialResultContent?.take(50)} output=${finalOutput.take(50)}")
    }

    /**
     * Handle stream == "plan" agent events (OpenClaw v2026.4.24+)
     * Plan/progress events showing multi-step work tracking
     * data: { phase, title, explanation, steps, source }
     */
    private suspend fun handlePlanStreamEvent(payload: JsonObject) {
        val data = payload["data"]?.jsonObject ?: return

        val phase = data["phase"]?.jsonPrimitive?.content ?: "update"
        val title = data["title"]?.jsonPrimitive?.content ?: "Assistant proposed a plan"
        val explanation = data["explanation"]?.jsonPrimitive?.content
        val stepsElement = data["steps"]
        val steps = if (stepsElement is JsonArray) {
            stepsElement.mapNotNull { it.jsonPrimitive?.content }
        } else null
        val source = data["source"]?.jsonPrimitive?.content

        val event = PlanStreamEvent(
            phase = phase,
            title = title,
            explanation = explanation,
            steps = steps,
            source = source,
            timestamp = System.currentTimeMillis()
        )

        _planStreamEvents.value = event

        AppLog.d(TAG, "Plan stream event: [${event.phase}] ${event.title}, steps=${event.steps?.size ?: 0}")
    }

    /**
     * Handle stream == "item" agent events (OpenClaw v2026.4.24+)
     * Work items, tasks, or checkpoints
     * data: { itemId, kind, title, name, phase, status, summary, progressText, approvalId, approvalSlug }
     */
    private suspend fun handleItemStreamEvent(payload: JsonObject) {
        val data = payload["data"]?.jsonObject ?: return

        val itemId = data["itemId"]?.jsonPrimitive?.content ?: return
        val kind = data["kind"]?.jsonPrimitive?.content
        val title = data["title"]?.jsonPrimitive?.content
        val name = data["name"]?.jsonPrimitive?.content
        val phase = data["phase"]?.jsonPrimitive?.content
        val status = data["status"]?.jsonPrimitive?.content
        val summary = data["summary"]?.jsonPrimitive?.content
        val progressText = data["progressText"]?.jsonPrimitive?.content
        val approvalId = data["approvalId"]?.jsonPrimitive?.content
        val approvalSlug = data["approvalSlug"]?.jsonPrimitive?.content

        val event = ItemStreamEvent(
            itemId = itemId,
            kind = kind,
            title = title,
            name = name,
            phase = phase,
            status = status,
            summary = summary,
            progressText = progressText,
            approvalId = approvalId,
            approvalSlug = approvalSlug,
            timestamp = System.currentTimeMillis()
        )

        _itemStreamEvents.update { events ->
            events.toMutableMap().apply { this[itemId] = event }
        }

        AppLog.d(TAG, "Item stream event: ${event.itemId} [${event.phase}/${event.status}] ${event.title}")
    }

    /**
     * Handle stream == "patch" agent events (OpenClaw v2026.4.24+)
     * Context/session state changes
     * data: { itemId, phase, title, toolCallId, name, added, modified, deleted, summary }
     */
    private suspend fun handlePatchStreamEvent(payload: JsonObject) {
        val data = payload["data"]?.jsonObject ?: return

        val itemId = data["itemId"]?.jsonPrimitive?.content ?: return
        val phase = data["phase"]?.jsonPrimitive?.content
        val title = data["title"]?.jsonPrimitive?.content
        val toolCallId = data["toolCallId"]?.jsonPrimitive?.content
        val name = data["name"]?.jsonPrimitive?.content
        val addedElement = data["added"]
        val added = if (addedElement is JsonArray) {
            addedElement.mapNotNull { it.jsonPrimitive?.content }
        } else null
        val modifiedElement = data["modified"]
        val modified = if (modifiedElement is JsonArray) {
            modifiedElement.mapNotNull { it.jsonPrimitive?.content }
        } else null
        val deletedElement = data["deleted"]
        val deleted = if (deletedElement is JsonArray) {
            deletedElement.mapNotNull { it.jsonPrimitive?.content }
        } else null
        val summary = data["summary"]?.jsonPrimitive?.content

        val event = PatchStreamEvent(
            itemId = itemId,
            phase = phase,
            title = title,
            toolCallId = toolCallId,
            name = name,
            added = added,
            modified = modified,
            deleted = deleted,
            summary = summary,
            timestamp = System.currentTimeMillis()
        )

        _patchStreamEvents.update { events ->
            events.toMutableMap().apply { this[itemId] = event }
        }

        AppLog.d(TAG, "Patch stream event: ${event.itemId} [${event.phase}] ${event.title}, +${added?.size ?: 0}/~${modified?.size ?: 0}/-${deleted?.size ?: 0}")
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

    // ── Delegated RPC methods ──

    // Chat
    suspend fun chatSend(
        sessionKey: String, message: String,
        attachments: List<com.openclaw.clawchat.ui.components.ApiAttachment>? = null,
        mediaUrls: List<String>? = null
    ): ResponseFrame = chatRpc.chatSend(sessionKey, message, attachments, mediaUrls)

    suspend fun chatHistory(sessionKey: String, limit: Int? = null): ResponseFrame =
        chatRpc.chatHistory(sessionKey, limit)

    suspend fun chatAbort(sessionKey: String, runId: String? = null): ResponseFrame =
        chatRpc.chatAbort(sessionKey, runId)

    suspend fun chatInject(
        sessionKey: String, role: String, content: String,
        attachments: List<com.openclaw.clawchat.ui.components.ApiAttachment>? = null
    ): ResponseFrame = chatRpc.chatInject(sessionKey, role, content, attachments)

    // Sessions
    suspend fun sessionsList(
        limit: Int? = null, activeMinutes: Int? = null,
        includeDerivedTitles: Boolean = true, includeLastMessage: Boolean = true
    ): ResponseFrame = sessionRpc.sessionsList(limit, activeMinutes, includeDerivedTitles, includeLastMessage)

    suspend fun sessionsPatch(sessionKey: String, verboseLevel: String? = null): ResponseFrame =
        sessionRpc.sessionsPatch(sessionKey, verboseLevel)

    suspend fun sessionsSteer(sessionKey: String, text: String): ResponseFrame =
        sessionRpc.sessionsSteer(sessionKey, text)

    suspend fun sessionsCreate(
        key: String? = null, agentId: String? = null, label: String? = null,
        model: String? = null, message: String? = null
    ): ResponseFrame = sessionRpc.sessionsCreate(key, agentId, label, model, message)

    suspend fun sessionsDelete(sessionKey: String, deleteTranscript: Boolean = true): ResponseFrame =
        sessionRpc.sessionsDelete(sessionKey, deleteTranscript)

    suspend fun sessionsReset(sessionKey: String, reason: String = "reset"): ResponseFrame =
        sessionRpc.sessionsReset(sessionKey, reason)

    suspend fun sessionsPreview(keys: List<String>, limit: Int? = null, maxChars: Int? = null): ResponseFrame =
        sessionRpc.sessionsPreview(keys, limit, maxChars)

    suspend fun sessionsUsage(
        sessionKey: String? = null, startDate: String? = null,
        endDate: String? = null, limit: Int? = null
    ): ResponseFrame = sessionRpc.sessionsUsage(sessionKey, startDate, endDate, limit)

    suspend fun sessionsMessagesSubscribe(sessionKey: String): ResponseFrame =
        sessionRpc.sessionsMessagesSubscribe(sessionKey)

    suspend fun sessionsMessagesUnsubscribe(sessionKey: String): ResponseFrame =
        sessionRpc.sessionsMessagesUnsubscribe(sessionKey)

    suspend fun sessionsSubscribe(): ResponseFrame = sessionRpc.sessionsSubscribe()
    suspend fun sessionsUnsubscribe(): ResponseFrame = sessionRpc.sessionsUnsubscribe()

    suspend fun sessionsResolve(
        key: String? = null, sessionId: String? = null, label: String? = null, agentId: String? = null
    ): ResponseFrame = sessionRpc.sessionsResolve(key, sessionId, label, agentId)

    suspend fun sessionsSend(sessionKey: String, message: String, idempotencyKey: String? = null): ResponseFrame =
        sessionRpc.sessionsSend(sessionKey, message, idempotencyKey)

    suspend fun sessionsAbort(sessionKey: String, runId: String? = null): ResponseFrame =
        sessionRpc.sessionsAbort(sessionKey, runId)

    // Compaction
    suspend fun sessionsCompactionList(sessionKey: String): ResponseFrame =
        sessionRpc.sessionsCompactionList(sessionKey)

    suspend fun sessionsCompactionGet(sessionKey: String, checkpointId: String): ResponseFrame =
        sessionRpc.sessionsCompactionGet(sessionKey, checkpointId)

    suspend fun sessionsCompactionBranch(
        sessionKey: String, checkpointId: String, newKey: String? = null, label: String? = null
    ): ResponseFrame = sessionRpc.sessionsCompactionBranch(sessionKey, checkpointId, newKey, label)

    suspend fun sessionsCompactionRestore(sessionKey: String, checkpointId: String): ResponseFrame =
        sessionRpc.sessionsCompactionRestore(sessionKey, checkpointId)

    suspend fun sessionsCompact(sessionKey: String, reason: String? = null): ResponseFrame =
        sessionRpc.sessionsCompact(sessionKey, reason)

    // Agents
    suspend fun agentsList(): ResponseFrame = agentRpc.agentsList()

    suspend fun agentsCreate(name: String, workspace: String, emoji: String? = null, avatar: String? = null): ResponseFrame =
        agentRpc.agentsCreate(name, workspace, emoji, avatar)

    suspend fun agentsUpdate(
        agentId: String, name: String? = null, workspace: String? = null,
        model: String? = null, avatar: String? = null
    ): ResponseFrame = agentRpc.agentsUpdate(agentId, name, workspace, model, avatar)

    suspend fun agentsDelete(agentId: String, deleteFiles: Boolean = false): ResponseFrame =
        agentRpc.agentsDelete(agentId, deleteFiles)

    // Config
    suspend fun configGet(key: String? = null): ResponseFrame = configRpc.configGet(key)
    suspend fun configSet(key: String, value: String): ResponseFrame = configRpc.configSet(key, value)
    suspend fun configPatch(patches: Map<String, String>): ResponseFrame = configRpc.configPatch(patches)
    suspend fun configSchema(key: String? = null): ResponseFrame = configRpc.configSchema(key)
    suspend fun configApply(key: String? = null): ResponseFrame = configRpc.configApply(key)

    // Cron
    suspend fun cronList(): ResponseFrame = cronRpc.cronList()

    suspend fun cronAdd(name: String, cron: String, sessionKey: String, prompt: String, enabled: Boolean = true): ResponseFrame =
        cronRpc.cronAdd(name, cron, sessionKey, prompt, enabled)

    suspend fun cronRemove(cronId: String): ResponseFrame = cronRpc.cronRemove(cronId)

    suspend fun cronPatch(
        cronId: String, enabled: Boolean? = null, name: String? = null,
        cron: String? = null, sessionKey: String? = null, prompt: String? = null
    ): ResponseFrame = cronRpc.cronPatch(cronId, enabled, name, cron, sessionKey, prompt)

    suspend fun cronRun(cronId: String): ResponseFrame = cronRpc.cronRun(cronId)
    suspend fun cronStatus(): ResponseFrame = cronRpc.cronStatus()

    suspend fun cronUpdate(
        cronId: String, enabled: Boolean? = null, name: String? = null,
        cron: String? = null, sessionKey: String? = null, prompt: String? = null
    ): ResponseFrame = cronRpc.cronUpdate(cronId, enabled, name, cron, sessionKey, prompt)

    suspend fun cronRuns(cronId: String? = null): ResponseFrame = cronRpc.cronRuns(cronId)

    // System (health, status, update, wizard, logs, models, tools, channels, device, usage, talk)
    suspend fun health(): ResponseFrame = sysRpc.health()
    suspend fun status(): ResponseFrame = sysRpc.status()
    suspend fun gatewayIdentityGet(): ResponseFrame = sysRpc.gatewayIdentityGet()
    suspend fun measureLatency(): Long? = sysRpc.measureLatency()
    suspend fun wizardStart(): ResponseFrame = sysRpc.wizardStart()
    suspend fun wizardNext(step: String, data: JsonObject? = null): ResponseFrame = sysRpc.wizardNext(step, data)
    suspend fun wizardCancel(): ResponseFrame = sysRpc.wizardCancel()
    suspend fun updateRun(): ResponseFrame = sysRpc.updateRun()

    suspend fun logsTail(limit: Int? = null, level: String? = null): ResponseFrame =
        sysRpc.logsTail(limit, level)

    suspend fun modelsList(): ResponseFrame = sysRpc.modelsList()
    suspend fun toolsCatalog(): ResponseFrame = sysRpc.toolsCatalog()

    suspend fun toolsEffective(sessionKey: String? = null): ResponseFrame =
        sysRpc.toolsEffective(sessionKey)

    suspend fun channelsStatus(): ResponseFrame = sysRpc.channelsStatus()

    suspend fun channelsLogout(channelId: String? = null): ResponseFrame =
        sysRpc.channelsLogout(channelId)

    suspend fun deviceTokenRotate(): ResponseFrame = sysRpc.deviceTokenRotate()

    suspend fun deviceTokenRevoke(token: String? = null): ResponseFrame =
        sysRpc.deviceTokenRevoke(token)

    suspend fun devicePairList(): ResponseFrame = sysRpc.devicePairList()
    suspend fun devicePairApprove(deviceId: String): ResponseFrame = sysRpc.devicePairApprove(deviceId)
    suspend fun devicePairReject(deviceId: String): ResponseFrame = sysRpc.devicePairReject(deviceId)
    suspend fun devicePairRemove(deviceId: String): ResponseFrame = sysRpc.devicePairRemove(deviceId)

    suspend fun sessionsUsageTimeseries(hours: Int? = null): ResponseFrame =
        sysRpc.sessionsUsageTimeseries(hours)

    suspend fun usageStatus(): ResponseFrame = sysRpc.usageStatus()
    suspend fun usageCost(): ResponseFrame = sysRpc.usageCost()
    suspend fun talkConfig(): ResponseFrame = sysRpc.talkConfig()

    suspend fun talkSpeak(text: String, provider: String? = null, voice: String? = null): ResponseFrame =
        sysRpc.talkSpeak(text, provider, voice)

    // ── Tick Monitor / Reconnect ──

    /**
     * 基于 tick 事件的连接监控
     * 服务器每 30 秒发送 tick 事件，如果超过 2 倍间隔未收到 tick，认为连接已失效并重连。
     */
    private fun startTickMonitor() {
        tickMonitorJob?.cancel()
        lastTickTimestamp = System.currentTimeMillis()
        tickMonitorJob = appScope.launch {
            while (_connectionState.value is WebSocketConnectionState.Connected) {
                delay(GatewayConfig.TICK_STALE_CHECK_INTERVAL_MS)
                val now = System.currentTimeMillis()
                val timeSinceLastTick = now - lastTickTimestamp
                if (timeSinceLastTick > GatewayConfig.TICK_STALE_THRESHOLD_MS) {
                    AppLog.w(TAG, "No tick received for ${timeSinceLastTick}ms, connection likely stale, reconnecting")
                    _connectionState.value = WebSocketConnectionState.Stale
                    currentUrl?.let { scheduleReconnect(it, currentToken) }
                    break
                }
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
