package com.openclaw.clawchat.network

import android.util.Log
import com.openclaw.clawchat.network.protocol.ChallengeResponseAuth
import com.openclaw.clawchat.network.protocol.ConnectChallengePayload
import com.openclaw.clawchat.network.protocol.ConnectOkPayload
import com.openclaw.clawchat.network.protocol.RequestFrame
import com.openclaw.clawchat.network.protocol.WebSocketProtocol
import com.openclaw.clawchat.security.SecurityModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * OkHttp WebSocket 服务实现
 * 
 * 基于 OkHttp 的 WebSocket 实现，支持：
 * - 协议 v3 Challenge-Response 认证
 * - 消息序列化/反序列化
 * - 自动重连（指数退避）
 * - 延迟监控
 * 
 * 连接流程 (协议 v3):
 * 1. 建立 WebSocket 连接（仅带协议版本头）
 * 2. 等待 connect.challenge 事件（包含服务器 nonce）
 * 3. 签名服务器 nonce，发送 connect 请求帧
 * 4. 收到 connect.ok 事件 → 连接就绪
 */
class OkHttpWebSocketService @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val securityModule: SecurityModule,
    private val appScope: CoroutineScope
) : WebSocketService {
    
    companion object {
        private const val TAG = "WebSocketService"
        
        // 重连配置
        private const val INITIAL_RECONNECT_DELAY_MS = 1000L
        private const val MAX_RECONNECT_DELAY_MS = 30000L
        private const val RECONNECT_BACKOFF_FACTOR = 2.0
        
        // 延迟监控配置
        private const val LATENCY_CHECK_INTERVAL_MS = 60000L
        private const val LATENCY_SAMPLES_MAX = 10
        
        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
    }
    
    private val _connectionState = MutableStateFlow<WebSocketConnectionState>(WebSocketConnectionState.Disconnected)
    override val connectionState: StateFlow<WebSocketConnectionState> = _connectionState.asStateFlow()
    
    private val _incomingMessages = MutableSharedFlow<GatewayMessage>(replay = 0)
    override val incomingMessages: SharedFlow<GatewayMessage> = _incomingMessages.asSharedFlow()
    
    private var webSocket: WebSocket? = null
    private var reconnectJob: Job? = null
    private var currentUrl: String? = null
    private var currentToken: String? = null
    private var reconnectAttempt = 0
    
    // Challenge-Response 认证器
    private val authHandler = ChallengeResponseAuth(securityModule)
    
    // 请求帧计数器
    private var requestCounter = 0L
    
    private val latencyMeasurements = mutableListOf<Long>()
    private var latencyMonitorJob: Job? = null
    
    /**
     * 建立 WebSocket 连接
     * 
     * 连接后不会立即变为 Connected，而是等待 connect.challenge → 签名 →
     * 发送 connect 请求 → 收到 connect.ok 后才转为 Connected。
     */
    override suspend fun connect(url: String, token: String?): Result<Unit> {
        // 如果已经连接，直接返回成功
        if (_connectionState.value is WebSocketConnectionState.Connected) {
            Log.d(TAG, "Already connected, skipping connect")
            return Result.success(Unit)
        }
        
        // 如果正在连接中，等待或返回
        if (_connectionState.value is WebSocketConnectionState.Connecting) {
            Log.d(TAG, "Already connecting, skipping")
            return Result.success(Unit)
        }
        
        _connectionState.value = WebSocketConnectionState.Connecting
        currentUrl = url
        currentToken = token
        reconnectAttempt = 0
        authHandler.reset()
        
        return try {
            val request = buildWebSocketRequest(url, token)
            
            webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    Log.i(TAG, "WebSocket opened, waiting for connect.challenge...")
                    // 不再在这里置 Connected，等 connect.ok 事件
                }
                
                override fun onMessage(webSocket: WebSocket, text: String) {
                    Log.d(TAG, "Received message: ${text.take(200)}...")
                    processIncomingMessage(text)
                }
                
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    Log.e(TAG, "WebSocket failure: ${t.message}", t)
                    _connectionState.value = WebSocketConnectionState.Error(t)
                    
                    // 调度重连
                    val url = currentUrl
                    val token = currentToken
                    if (url != null) {
                        scheduleReconnect(url, token)
                    }
                }
                
                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    Log.i(TAG, "WebSocket closed: $code - $reason")
                    _connectionState.value = WebSocketConnectionState.Disconnecting(reason)
                    this@OkHttpWebSocketService.webSocket = null
                    latencyMonitorJob?.cancel()
                }
                
                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    Log.i(TAG, "WebSocket closing: $code - $reason")
                    webSocket.close(code, reason)
                }
            })
            
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect: ${e.message}", e)
            _connectionState.value = WebSocketConnectionState.Error(e)
            Result.failure(e)
        }
    }
    
    /**
     * 构建 WebSocket 请求
     * 
     * 协议 v3: 连接时仅携带协议版本头，认证通过 challenge-response 完成。
     */
    private fun buildWebSocketRequest(url: String, token: String?): Request {
        val builder = Request.Builder()
            .url(url)
            .addHeader(WebSocketProtocol.PROTOCOL_VERSION_HEADER, WebSocketProtocol.VERSION)
        
        // 如果有已存储的 token，带上 Authorization 头
        val authToken = token ?: securityModule.getAuthToken()
        authToken?.let {
            builder.addHeader("Authorization", "Bearer $it")
        }
        
        return builder.build()
    }
    
    /**
     * 处理接收到的消息
     * 
     * 根据帧 type 字段分发：
     * - "event" → 处理协议事件（connect.challenge / connect.ok / connect.error / 业务事件）
     * - 其他 → 按旧格式 GatewayMessage 解析
     */
    private fun processIncomingMessage(text: String) {
        try {
            val jsonElement = json.parseToJsonElement(text)
            val type = jsonElement.jsonObject["type"]?.jsonPrimitive?.content
            
            when (type) {
                "event" -> handleEvent(jsonElement.jsonObject, text)
                else -> handleLegacyMessage(text)
            }
        } catch (e: Exception) {
            // JSON 解析失败，尝试旧格式
            handleLegacyMessage(text)
        }
    }
    
    /**
     * 处理协议 v3 事件帧
     */
    private fun handleEvent(obj: JsonObject, raw: String) {
        val event = obj["event"]?.jsonPrimitive?.content ?: return
        
        when (event) {
            "connect.challenge" -> handleConnectChallenge(obj)
            "connect.ok" -> handleConnectOk(obj)
            "connect.error" -> handleConnectError(obj)
            else -> {
                // 业务事件，转为 GatewayMessage 发出
                handleLegacyMessage(raw)
            }
        }
    }
    
    /**
     * 处理 connect.challenge 事件
     * 
     * 签名服务器 nonce，构建 connect 请求帧并发送。
     */
    private fun handleConnectChallenge(obj: JsonObject) {
        appScope.launch {
            try {
                val payloadObj = obj["payload"]?.jsonObject
                if (payloadObj == null) {
                    Log.e(TAG, "connect.challenge missing payload")
                    return@launch
                }
                
                val nonce = payloadObj["nonce"]?.jsonPrimitive?.content
                val ts = payloadObj["ts"]?.jsonPrimitive?.content?.toLongOrNull()
                    ?: System.currentTimeMillis()
                
                if (nonce.isNullOrBlank()) {
                    Log.e(TAG, "connect.challenge has empty nonce")
                    return@launch
                }
                
                Log.i(TAG, "Received connect.challenge, nonce=${nonce.take(8)}...")
                
                // 1) 把 challenge 交给 authHandler
                val challenge = ConnectChallengePayload(nonce = nonce, timestamp = ts)
                authHandler.handleChallenge(challenge)
                
                // 2) 构建签名后的 connect 请求
                val connectRequest = authHandler.buildConnectRequest()
                
                // 3) 组装 RequestFrame
                val params = mapOf(
                    "device" to JsonObject(mapOf(
                        "id" to JsonPrimitive(connectRequest.device.id),
                        "publicKey" to JsonPrimitive(connectRequest.device.publicKey),
                        "signature" to JsonPrimitive(connectRequest.signature),
                        "signedAt" to JsonPrimitive(System.currentTimeMillis()),
                        "nonce" to JsonPrimitive(connectRequest.nonce)
                    )),
                    "client" to JsonObject(mapOf(
                        "id" to JsonPrimitive(connectRequest.client.clientId),
                        "version" to JsonPrimitive(connectRequest.client.clientVersion),
                        "platform" to JsonPrimitive(connectRequest.client.platform)
                    )),
                    "minProtocol" to JsonPrimitive(3),
                    "maxProtocol" to JsonPrimitive(3)
                )
                
                val requestFrame = RequestFrame(
                    id = "auth-${System.currentTimeMillis()}",
                    method = "connect",
                    params = params
                )
                
                // 4) 发送
                val frameJson = json.encodeToString(RequestFrame.serializer(), requestFrame)
                val sent = webSocket?.send(frameJson) ?: false
                if (sent) {
                    Log.i(TAG, "Sent connect request, waiting for connect.ok...")
                } else {
                    Log.e(TAG, "Failed to send connect request (WebSocket closed)")
                    _connectionState.value = WebSocketConnectionState.Error(
                        IllegalStateException("WebSocket closed during auth"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to handle connect.challenge: ${e.message}", e)
                _connectionState.value = WebSocketConnectionState.Error(e)
            }
        }
    }
    
    /**
     * 处理 connect.ok 事件 → 认证完成，连接就绪
     */
    private fun handleConnectOk(obj: JsonObject) {
        try {
            val payloadObj = obj["payload"]?.jsonObject
            val deviceToken = payloadObj?.get("deviceToken")?.jsonPrimitive?.content
            
            if (!deviceToken.isNullOrBlank()) {
                val connectOk = ConnectOkPayload(
                    deviceToken = deviceToken,
                    timestamp = payloadObj["ts"]?.jsonPrimitive?.content?.toLongOrNull()
                        ?: System.currentTimeMillis()
                )
                authHandler.handleConnectOk(connectOk)
                Log.i(TAG, "deviceToken stored")
            }
            
            _connectionState.value = WebSocketConnectionState.Connected
            reconnectAttempt = 0
            startLatencyMonitoring()
            Log.i(TAG, "connect.ok received — connection ready")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to handle connect.ok: ${e.message}", e)
        }
    }
    
    /**
     * 处理 connect.error 事件
     */
    private fun handleConnectError(obj: JsonObject) {
        val payloadObj = obj["payload"]?.jsonObject
        val code = payloadObj?.get("code")?.jsonPrimitive?.content ?: "UNKNOWN"
        val message = payloadObj?.get("message")?.jsonPrimitive?.content ?: "Auth failed"
        
        Log.e(TAG, "connect.error: $code - $message")
        _connectionState.value = WebSocketConnectionState.Error(
            IllegalStateException("$code: $message"))
    }
    
    /**
     * 处理旧格式 GatewayMessage（或未识别的事件帧作为透传）
     */
    private fun handleLegacyMessage(text: String) {
        val message = MessageParser.parse(text)
        if (message != null) {
            appScope.launch {
                _incomingMessages.emit(message)
            }
            
            // 自动响应 Pong
            if (message is GatewayMessage.Ping) {
                val latency = System.currentTimeMillis() - message.timestamp
                val pong = GatewayMessage.Pong(System.currentTimeMillis(), latency)
                appScope.launch {
                    send(pong)
                }
            }
        } else {
            Log.w(TAG, "Failed to parse message: ${text.take(200)}")
        }
    }
    
    /**
     * 发送消息
     *
     * 将 GatewayMessage 包装为 RequestFrame（type=req）通过 WebSocket 发送。
     */
    override suspend fun send(message: GatewayMessage): Result<Unit> {
        val ws = webSocket
            ?: return Result.failure(IllegalStateException("WebSocket not connected"))
        
        return withContext(Dispatchers.IO) {
            try {
                val requestId = "req-${System.currentTimeMillis()}-${requestCounter++}"
                
                val frame: RequestFrame? = when (message) {
                    is GatewayMessage.UserMessage -> RequestFrame(
                        id = requestId,
                        method = "chat.send",
                        params = mapOf(
                            "sessionId" to JsonPrimitive(message.sessionId),
                            "content" to JsonPrimitive(message.content)
                        )
                    )
                    
                    is GatewayMessage.Ping -> RequestFrame(
                        id = requestId,
                        method = "ping",
                        params = mapOf(
                            "timestamp" to JsonPrimitive(message.timestamp)
                        )
                    )
                    
                    is GatewayMessage.Pong -> null  // 心跳响应不需要发帧
                    
                    is GatewayMessage.SystemEvent,
                    is GatewayMessage.AssistantMessage,
                    is GatewayMessage.Error -> {
                        return@withContext Result.failure(
                            IllegalArgumentException("Cannot send ${message.type} from client")
                        )
                    }
                }
                
                if (frame == null) {
                    return@withContext Result.success(Unit)
                }
                
                val frameJson = json.encodeToString(RequestFrame.serializer(), frame)
                val success = ws.send(frameJson)
                if (success) {
                    Log.d(TAG, "Sent RequestFrame: method=${frame.method}, id=${frame.id}")
                    Result.success(Unit)
                } else {
                    Result.failure(IllegalStateException("Failed to send frame"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Send failed: ${e.message}", e)
                Result.failure(e)
            }
        }
    }
    
    /**
     * 断开连接
     */
    override suspend fun disconnect(): Result<Unit> {
        Log.i(TAG, "Disconnecting...")
        
        // 取消重连任务
        reconnectJob?.cancel()
        reconnectJob = null
        
        // 取消延迟监控
        latencyMonitorJob?.cancel()
        latencyMonitorJob = null
        
        // 重置认证状态
        authHandler.reset()
        
        // 关闭 WebSocket
        webSocket?.close(1000, "User requested disconnect")
        this.webSocket = null
        
        _connectionState.value = WebSocketConnectionState.Disconnected
        currentUrl = null
        currentToken = null
        
        Log.i(TAG, "Disconnected")
        return Result.success(Unit)
    }
    
    /**
     * 调度重连（指数退避）
     */
    private fun scheduleReconnect(url: String, token: String?) {
        reconnectJob?.cancel()
        
        // 计算退避延迟
        val delayMs = calculateBackoffDelay()
        
        Log.i(TAG, "Scheduling reconnect in ${delayMs}ms (attempt ${reconnectAttempt + 1})")
        
        reconnectJob = appScope.launch {
            delay(delayMs)
            
            // 检查是否已经被取消或已连接
            if (reconnectJob?.isActive == true && _connectionState.value !is WebSocketConnectionState.Connected) {
                Log.i(TAG, "Attempting reconnect...")
                connect(url, token)
            }
        }
    }
    
    /**
     * 计算指数退避延迟
     */
    private fun calculateBackoffDelay(): Long {
        val delay = (INITIAL_RECONNECT_DELAY_MS * Math.pow(RECONNECT_BACKOFF_FACTOR.toDouble(), reconnectAttempt.toDouble())).toLong()
        reconnectAttempt++
        return delay.coerceAtMost(MAX_RECONNECT_DELAY_MS)
    }
    
    /**
     * 启动延迟监控
     */
    private fun startLatencyMonitoring() {
        latencyMonitorJob?.cancel()
        
        latencyMonitorJob = appScope.launch {
            while (_connectionState.value is WebSocketConnectionState.Connected) {
                val latency = measureLatency()
                latency?.let {
                    latencyMeasurements.add(it)
                    if (latencyMeasurements.size > LATENCY_SAMPLES_MAX) {
                        latencyMeasurements.removeAt(0)
                    }
                    Log.d(TAG, "Latency: ${it}ms (avg: ${getAverageLatency()}ms)")
                }
                delay(LATENCY_CHECK_INTERVAL_MS)
            }
        }
    }
    
    /**
     * 测量连接延迟
     */
    override suspend fun measureLatency(): Long? {
        val ws = webSocket ?: return null
        
        val start = System.currentTimeMillis()
        val pingMessage = GatewayMessage.Ping(start)
        
        return try {
            ws.send(MessageParser.serialize(pingMessage))
            delay(100)
            System.currentTimeMillis() - start
        } catch (e: Exception) {
            Log.w(TAG, "Latency measurement failed: ${e.message}")
            null
        }
    }
    
    /**
     * 获取平均延迟
     */
    fun getAverageLatency(): Long {
        return if (latencyMeasurements.isEmpty()) {
            0
        } else {
            latencyMeasurements.average().toLong()
        }
    }
    
    /**
     * 清除延迟测量数据
     */
    fun clearLatencyMeasurements() {
        latencyMeasurements.clear()
    }
}
