package com.openclaw.clawchat.network.protocol

import android.util.Log
import com.openclaw.clawchat.network.GatewayMessage
import com.openclaw.clawchat.network.MessageParser
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

/**
 * Gateway 连接管理器 v2 (协议 v3 完整版)
 * 
 * 实现完整的 Gateway 协议 v3：
 * 1. WebSocket 连接建立
 * 2. Challenge-Response 认证
 * 3. Request/Response/Event 帧处理
 * 4. 请求追踪
 * 5. 序列号管理
 * 6. 智能重试
 */
class GatewayConnection(
    private val okHttpClient: OkHttpClient,
    private val securityModule: SecurityModule,
    private val appScope: CoroutineScope
) {
    companion object {
        private const val TAG = "GatewayConnection"
        
        // 连接配置
        private const val CONNECT_TIMEOUT_MS = 30000L
        private const val AUTH_TIMEOUT_MS = 60000L
        private const val REQUEST_TIMEOUT_MS = 30000L
        private const val HEARTBEAT_INTERVAL_MS = 30000L
        
        // 重连配置
        private const val INITIAL_RECONNECT_DELAY_MS = 1000L
        private const val MAX_RECONNECT_DELAY_MS = 30000L
        private const val RECONNECT_BACKOFF_FACTOR = 2.0
        
        // JSON 解析器
        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
    }
    
    // 连接状态
    private val _connectionState = MutableStateFlow<WebSocketConnectionState>(WebSocketConnectionState.Disconnected)
    val connectionState: StateFlow<WebSocketConnectionState> = _connectionState.asStateFlow()
    
    // 接收消息流
    private val _incomingMessages = MutableSharedFlow<GatewayMessage>(replay = 0)
    val incomingMessages: SharedFlow<GatewayMessage> = _incomingMessages.asSharedFlow()
    
    // 接收事件流
    private val _incomingEvents = MutableSharedFlow<EventFrame>(replay = 0)
    val incomingEvents: SharedFlow<EventFrame> = _incomingEvents.asSharedFlow()
    
    // Challenge-Response 认证器
    private val authHandler = ChallengeResponseAuth(securityModule)
    
    // 请求追踪器
    private val requestTracker = RequestTracker(timeoutMs = REQUEST_TIMEOUT_MS)
    
    // 序列号管理器
    private val sequenceManager = SequenceManager()
    
    // 事件去重器
    private val eventDeduplicator = EventDeduplicator()
    
    // 重试管理器
    private val retryManager = RetryManager()
    
    // WebSocket 连接
    private var webSocket: WebSocket? = null
    private var reconnectJob: Job? = null
    private var heartbeatJob: Job? = null
    
    // 连接信息
    private var currentUrl: String? = null
    private var reconnectAttempt = 0
    private var deviceToken: String? = null
    
    // 请求执行器
    private lateinit var requestExecutor: RequestExecutor
    
    /**
     * 连接到 Gateway
     */
    suspend fun connect(url: String): Result<Unit> = withContext(Dispatchers.IO) {
        // 检查是否已连接
        if (_connectionState.value is WebSocketConnectionState.Connected) {
            Log.d(TAG, "已连接，跳过")
            return@withContext Result.success(Unit)
        }
        
        Log.d(TAG, "开始连接：$url")
        _connectionState.value = WebSocketConnectionState.Connecting
        currentUrl = url
        reconnectAttempt = 0
        
        try {
            // 建立 WebSocket 连接
            val request = Request.Builder()
                .url(url)
                .addHeader("X-ClawChat-Protocol-Version", WebSocketProtocol.VERSION)
                .build()
            
            webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    Log.d(TAG, "WebSocket 已打开")
                }
                
                override fun onMessage(webSocket: WebSocket, text: String) {
                    Log.d(TAG, "收到消息：$text")
                    handleIncomingMessage(text)
                }
                
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    Log.e(TAG, "WebSocket 失败：${t.message}", t)
                    _connectionState.value = WebSocketConnectionState.Error(t)
                    
                    // 调度重连
                    scheduleReconnect(url)
                }
                
                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    Log.d(TAG, "WebSocket 关闭：$code - $reason")
                    _connectionState.value = WebSocketConnectionState.Disconnected
                    this@GatewayConnection.webSocket = null
                    heartbeatJob?.cancel()
                }
            })
            
            // 初始化请求执行器
            requestExecutor = createRequestExecutor(
                tracker = requestTracker,
                sendFunction = { frame ->
                    webSocket?.send(json.encodeToString(frame))
                },
                timeoutMs = REQUEST_TIMEOUT_MS
            )
            
            // 等待认证完成或超时
            val authCompleted = withTimeoutOrNull(AUTH_TIMEOUT_MS) {
                while (_connectionState.value !is WebSocketConnectionState.Connected &&
                       _connectionState.value !is WebSocketConnectionState.Error) {
                    delay(100)
                }
                _connectionState.value is WebSocketConnectionState.Connected
            }
            
            if (authCompleted == true) {
                Log.d(TAG, "认证成功")
                startHeartbeat()
                Result.success(Unit)
            } else {
                Log.e(TAG, "认证超时或失败")
                Result.failure(IllegalStateException("Authentication timeout"))
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "连接失败：${e.message}", e)
            _connectionState.value = WebSocketConnectionState.Error(e)
            Result.failure(e)
        }
    }
    
    /**
     * 处理接收到的消息
     */
    private fun handleIncomingMessage(text: String) {
        try {
            // 解析为 JSON
            val jsonElement = json.parseToJsonElement(text)
            val type = jsonElement.jsonObject["type"]?.jsonPrimitive?.content
            
            when (type) {
                "res" -> {
                    // 处理响应
                    val response = json.decodeFromString<ResponseFrame>(text)
                    appScope.launch {
                        requestTracker.completeRequest(response)
                    }
                }
                
                "event" -> {
                    // 处理事件
                    val event = json.decodeFromString<EventFrame>(text)
                    handleEvent(event)
                }
                
                else -> {
                    // 尝试解析为旧格式消息
                    val message = MessageParser.parse(text)
                    if (message != null) {
                        appScope.launch {
                            _incomingMessages.emit(message)
                        }
                    } else {
                        Log.w(TAG, "无法解析消息：$text")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "处理消息失败：${e.message}", e)
        }
    }
    
    /**
     * 处理事件
     */
    private fun handleEvent(event: EventFrame) {
        appScope.launch {
            try {
                // 检查序列号
                when (sequenceManager.checkSequence(event.seq)) {
                    is SequenceManager.SequenceResult.Ok -> {
                        // 继续处理
                    }
                    is SequenceManager.SequenceResult.Duplicate -> {
                        Log.d(TAG, "忽略重复事件：${event.event} (seq=${event.seq})")
                        return@launch
                    }
                    is SequenceManager.SequenceResult.Old -> {
                        Log.d(TAG, "忽略过时事件：${event.event} (seq=${event.seq})")
                        return@launch
                    }
                    is SequenceManager.SequenceResult.Gap -> {
                        Log.w(TAG, "检测到事件丢失：${event.event}")
                        // 可以选择请求丢失的事件
                    }
                }
                
                // 检查事件是否重复
                val eventId = event.stateVersion ?: "${event.event}-${event.seq}"
                if (eventDeduplicator.isDuplicate(eventId, event.seq)) {
                    Log.d(TAG, "忽略重复事件：$eventId")
                    return@launch
                }
                
                // 处理特定事件
                when (event.event) {
                    "connect.challenge" -> handleConnectChallenge(event)
                    "connect.ok" -> handleConnectOk(event)
                    "connect.error" -> handleConnectError(event)
                    "session.message" -> handleSessionMessage(event)
                    "error" -> handleErrorEvent(event)
                    else -> {
                        Log.d(TAG, "未知事件：${event.event}")
                        // 转发到事件流
                        _incomingEvents.emit(event)
                    }
                }
                
                // 确认序列号
                sequenceManager.acknowledge(event.seq)
                
            } catch (e: Exception) {
                Log.e(TAG, "处理事件失败：${e.message}", e)
            }
        }
    }
    
    /**
     * 处理连接挑战
     */
    private suspend fun handleConnectChallenge(event: EventFrame) {
        try {
            val challenge = event.parseConnectChallengePayload()
            if (challenge == null) {
                Log.e(TAG, "无效的 Challenge 格式")
                return
            }
            
            Log.d(TAG, "收到挑战：nonce=${challenge.nonce}")
            
            // 处理挑战
            authHandler.handleChallenge(ConnectChallenge(challenge.nonce, challenge.timestamp))
            
            // 构建 connect 请求
            val connectRequest = authHandler.buildConnectRequest()
            
            // 发送 connect 请求
            val requestFrame = RequestFrame(
                id = "auth-${System.currentTimeMillis()}",
                method = "connect",
                params = buildConnectParams(connectRequest)
            )
            
            Log.d(TAG, "发送 Connect 请求")
            webSocket?.send(json.encodeToString(requestFrame))
            
        } catch (e: Exception) {
            Log.e(TAG, "处理挑战失败：${e.message}", e)
            _connectionState.value = WebSocketConnectionState.Error(e)
        }
    }
    
    /**
     * 处理连接成功
     */
    private suspend fun handleConnectOk(event: EventFrame) {
        try {
            val connectOk = event.parseConnectOkPayload()
            if (connectOk == null) {
                Log.e(TAG, "无效的 ConnectOk 格式")
                return
            }
            
            Log.d(TAG, "认证成功，收到 deviceToken")
            
            // 存储 deviceToken
            deviceToken = connectOk.deviceToken
            securityModule.completePairing(connectOk.deviceToken)
            
            // 更新连接状态
            _connectionState.value = WebSocketConnectionState.Connected
            
        } catch (e: Exception) {
            Log.e(TAG, "处理认证成功失败：${e.message}", e)
        }
    }
    
    /**
     * 处理连接错误
     */
    private suspend fun handleConnectError(event: EventFrame) {
        try {
            val error = event.parseErrorPayload()
            if (error == null) {
                Log.e(TAG, "无效的 ConnectError 格式")
                return
            }
            
            Log.e(TAG, "连接错误：${error.code} - ${error.message}")
            
            _connectionState.value = WebSocketConnectionState.Error(
                IllegalStateException("${error.code}: ${error.message}")
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "处理连接错误失败：${e.message}", e)
        }
    }
    
    /**
     * 处理会话消息
     */
    private suspend fun handleSessionMessage(event: EventFrame) {
        try {
            val payload = event.parseSessionMessagePayload()
            if (payload == null) {
                Log.e(TAG, "无效的 SessionMessage 格式")
                return
            }
            
            Log.d(TAG, "收到会话消息：${payload.message.id}")
            
            // 转发到消息流
            // TODO: 转换为 GatewayMessage
            
        } catch (e: Exception) {
            Log.e(TAG, "处理会话消息失败：${e.message}", e)
        }
    }
    
    /**
     * 处理错误事件
     */
    private suspend fun handleErrorEvent(event: EventFrame) {
        try {
            val error = event.parseErrorPayload()
            if (error == null) {
                Log.e(TAG, "无效的 Error 格式")
                return
            }
            
            Log.e(TAG, "错误事件：${error.code} - ${error.message}")
            
            // 如果有 requestId，完成对应的请求
            if (error.requestId != null) {
                requestTracker.failRequest(
                    error.requestId,
                    IllegalStateException("${error.code}: ${error.message}")
                )
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "处理错误事件失败：${e.message}", e)
        }
    }
    
    /**
     * 构建 connect 请求参数
     */
    private fun buildConnectParams(request: ConnectRequest): Map<String, JsonElement> {
        return mapOf(
            "device" to buildJsonObject {
                put("id", JsonPrimitive(request.device.id))
                put("publicKey", JsonPrimitive(request.device.publicKey))
                put("signature", JsonPrimitive(request.device.signature))
                put("signedAt", JsonPrimitive(request.device.signedAt))
                put("nonce", JsonPrimitive(request.device.nonce))
            },
            "client" to buildJsonObject {
                put("id", JsonPrimitive(request.client.id))
                put("version", JsonPrimitive(request.client.version))
                put("platform", JsonPrimitive(request.client.platform))
            },
            "minProtocol" to JsonPrimitive(3),
            "maxProtocol" to JsonPrimitive(3)
        )
    }
    
    /**
     * 构建 JSON 对象
     */
    private inline fun buildJsonObject(block: JsonObjectBuilder.() -> Unit): JsonObject {
        val builder = JsonObjectBuilder()
        builder.block()
        return builder.build()
    }
    
    private class JsonObjectBuilder {
        private val map = mutableMapOf<String, JsonElement>()
        
        fun put(key: String, value: JsonElement) {
            map[key] = value
        }
        
        fun put(key: String, value: String) {
            map[key] = JsonPrimitive(value)
        }
        
        fun put(key: String, value: Int) {
            map[key] = JsonPrimitive(value)
        }
        
        fun put(key: String, value: Long) {
            map[key] = JsonPrimitive(value)
        }
        
        fun build(): JsonObject = JsonObject(map)
    }
    
    /**
     * 发送请求并等待响应
     */
    suspend fun sendRequest(method: GatewayMethod, params: Map<String, JsonElement>? = null): ResponseFrame {
        val request = RequestFrame(
            id = RequestIdGenerator.generateRequestId(),
            method = method.value,
            params = params
        )
        
        return requestExecutor.execute(request)
    }
    
    /**
     * 发送消息
     */
    suspend fun sendMessage(sessionId: String, content: String): Result<SendMessageResponse> {
        return try {
            val params = mapOf(
                "sessionId" to JsonPrimitive(sessionId),
                "content" to JsonPrimitive(content)
            )
            
            val response = sendRequest(GatewayMethod.SEND_MESSAGE, params)
            
            if (!response.isSuccess()) {
                return Result.failure(IllegalStateException("${response.error?.code}: ${response.error?.message}"))
            }
            
            val result = response.parseSendMessageResponse()
            if (result != null) {
                Result.success(result)
            } else {
                Result.failure(IllegalStateException("Failed to parse response"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "发送消息失败：${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * 获取会话列表
     */
    suspend fun getSessionList(): Result<SessionListResponse> {
        return try {
            val response = sendRequest(GatewayMethod.GET_SESSIONS)
            
            if (!response.isSuccess()) {
                return Result.failure(IllegalStateException("${response.error?.code}: ${response.error?.message}"))
            }
            
            val result = response.parseSessionListResponse()
            if (result != null) {
                Result.success(result)
            } else {
                Result.failure(IllegalStateException("Failed to parse response"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取会话列表失败：${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * 创建会话
     */
    suspend fun createSession(model: String? = null, thinking: Boolean? = null): Result<CreateSessionResponse> {
        return try {
            val params = buildMap {
                if (model != null) put("model", JsonPrimitive(model))
                if (thinking != null) put("thinking", JsonPrimitive(thinking))
            }
            
            val response = sendRequest(GatewayMethod.CREATE_SESSION, params)
            
            if (!response.isSuccess()) {
                return Result.failure(IllegalStateException("${response.error?.code}: ${response.error?.message}"))
            }
            
            val result = response.parseCreateSessionResponse()
            if (result != null) {
                Result.success(result)
            } else {
                Result.failure(IllegalStateException("Failed to parse response"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "创建会话失败：${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * 终止会话
     */
    suspend fun terminateSession(sessionId: String, reason: String? = null): Result<Unit> {
        return try {
            val params = mapOf(
                "sessionId" to JsonPrimitive(sessionId),
                "reason" to (reason?.let { JsonPrimitive(it) } ?: JsonPrimitive(""))
            )
            
            val response = sendRequest(GatewayMethod.TERMINATE_SESSION, params)
            
            if (!response.isSuccess()) {
                return Result.failure(IllegalStateException("${response.error?.code}: ${response.error?.message}"))
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "终止会话失败：${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Ping
     */
    suspend fun ping(): Result<Long> {
        return try {
            val startTime = System.currentTimeMillis()
            val params = mapOf("timestamp" to JsonPrimitive(startTime))
            
            val response = sendRequest(GatewayMethod.PING, params)
            
            if (!response.isSuccess()) {
                return Result.failure(IllegalStateException("${response.error?.code}: ${response.error?.message}"))
            }
            
            val result = response.parsePingResponse()
            val latency = System.currentTimeMillis() - startTime
            
            Result.success(result?.latency ?: latency)
        } catch (e: Exception) {
            Log.e(TAG, "Ping 失败：${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * 断开连接
     */
    suspend fun disconnect(): Result<Unit> = withContext(Dispatchers.IO) {
        Log.d(TAG, "断开连接...")
        
        // 取消所有任务
        reconnectJob?.cancel()
        reconnectJob = null
        heartbeatJob?.cancel()
        heartbeatJob = null
        
        // 取消所有请求
        requestTracker.cancelAllRequests("Disconnecting")
        
        // 停止管理器
        sequenceManager.reset()
        eventDeduplicator.clear()
        requestTracker.stop()
        
        // 关闭 WebSocket
        webSocket?.close(1000, "User requested disconnect")
        webSocket = null
        
        _connectionState.value = WebSocketConnectionState.Disconnected
        currentUrl = null
        
        Log.d(TAG, "已断开连接")
        Result.success(Unit)
    }
    
    /**
     * 启动心跳
     */
    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        
        heartbeatJob = appScope.launch {
            while (_connectionState.value is WebSocketConnectionState.Connected) {
                try {
                    val result = ping()
                    if (result.isSuccess) {
                        Log.d(TAG, "心跳：延迟 ${result.getOrNull()}ms")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "心跳失败：${e.message}")
                }
                delay(HEARTBEAT_INTERVAL_MS)
            }
        }
    }
    
    /**
     * 调度重连
     */
    private fun scheduleReconnect(url: String) {
        reconnectJob?.cancel()
        
        val delayMs = calculateBackoffDelay()
        Log.d(TAG, "调度重连：${delayMs}ms (尝试 ${reconnectAttempt + 1})")
        
        reconnectJob = appScope.launch {
            delay(delayMs)
            
            if (reconnectJob?.isActive == true && 
                _connectionState.value !is WebSocketConnectionState.Connected) {
                Log.d(TAG, "执行重连...")
                connect(url)
            }
        }
    }
    
    /**
     * 计算退避延迟
     */
    private fun calculateBackoffDelay(): Long {
        val delay = (INITIAL_RECONNECT_DELAY_MS * 
                    Math.pow(RECONNECT_BACKOFF_FACTOR.toDouble(), reconnectAttempt.toDouble())).toLong()
        reconnectAttempt++
        return delay.coerceAtMost(MAX_RECONNECT_DELAY_MS)
    }
}
