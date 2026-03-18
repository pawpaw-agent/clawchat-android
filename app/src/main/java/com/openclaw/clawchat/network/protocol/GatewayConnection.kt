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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Gateway 连接管理
 * 
 * 实现完整的 Gateway 协议 v3 连接流程：
 * 1. WebSocket 连接建立
 * 2. Challenge-Response 认证
 * 3. 消息收发
 * 4. 心跳保活
 * 5. 自动重连
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
    
    // Challenge-Response 认证器
    private val authHandler = ChallengeResponseAuth(securityModule)
    
    // WebSocket 连接
    private var webSocket: WebSocket? = null
    private var reconnectJob: Job? = null
    private var heartbeatJob: Job? = null
    
    // 连接信息
    private var currentUrl: String? = null
    private var reconnectAttempt = 0
    
    /**
     * 连接到 Gateway
     * 
     * 完整的连接流程：
     * 1. 建立 WebSocket 连接
     * 2. 发送认证请求
     * 3. 处理挑战 - 响应认证
     * 4. 启动心跳
     */
    suspend fun connect(url: String): Result<Unit> = withContext(Dispatchers.IO) {
        // 检查是否已连接
        if (_connectionState.value is WebSocketConnectionState.Connected) {
            Log.d(TAG, "已连接，跳过")
            return@withContext Result.success(Unit)
        }
        
        // 检查是否正在连接
        if (_connectionState.value is WebSocketConnectionState.Connecting) {
            Log.d(TAG, "正在连接中，跳过")
            return@withContext Result.success(Unit)
        }
        
        Log.d(TAG, "开始连接：$url")
        _connectionState.value = WebSocketConnectionState.Connecting
        currentUrl = url
        reconnectAttempt = 0
        
        try {
            // 步骤 1: 构建认证请求
            val authRequest = authHandler.buildAuthRequest()
            val authRequestJson = json.encodeToString<AuthRequest>(authRequest)
            
            Log.d(TAG, "认证请求：$authRequestJson")
            
            // 步骤 2: 建立 WebSocket 连接并发送认证请求
            val latch = java.util.concurrent.CountDownLatch(1)
            var connectionResult: Result<Unit>? = null
            
            val request = Request.Builder()
                .url(url)
                .addHeader(WebSocketProtocol.PROTOCOL_VERSION_HEADER, WebSocketProtocol.VERSION)
                .build()
            
            webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    Log.d(TAG, "WebSocket 已打开")
                    
                    // 发送认证请求
                    webSocket.send(authRequestJson)
                }
                
                override fun onMessage(webSocket: WebSocket, text: String) {
                    Log.d(TAG, "收到消息：$text")
                    handleIncomingMessage(text)
                }
                
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    Log.e(TAG, "WebSocket 失败：${t.message}", t)
                    _connectionState.value = WebSocketConnectionState.Error(t)
                    connectionResult = Result.failure(t)
                    latch.countDown()
                    
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
            
            // 等待认证完成或超时
            val authCompleted = withTimeoutOrNull(AUTH_TIMEOUT_MS) {
                // 等待认证完成
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
            // 尝试解析为认证相关消息
            val jsonElement = json.parseToJsonElement(text)
            val type = jsonElement.jsonObject["type"]?.jsonPrimitive?.content
            
            when (type) {
                "challenge" -> {
                    // 步骤 2: 处理服务器挑战
                    val challenge = text.toAuthChallenge()
                    if (challenge != null) {
                        handleChallenge(challenge)
                    } else {
                        Log.e(TAG, "无效的 Challenge 格式")
                    }
                }
                
                "auth_success" -> {
                    // 步骤 4: 处理认证成功
                    val success = text.toAuthSuccess()
                    if (success != null) {
                        val result = authHandler.handleAuthSuccess(success)
                        if (result.success) {
                            _connectionState.value = WebSocketConnectionState.Connected
                        } else {
                            _connectionState.value = WebSocketConnectionState.Error(
                                IllegalStateException(result.error ?: "Auth failed")
                            )
                        }
                    }
                }
                
                "error" -> {
                    // 处理错误
                    val error = text.toProtocolError()
                    if (error != null) {
                        val result = authHandler.handleAuthError(error)
                        _connectionState.value = WebSocketConnectionState.Error(
                            IllegalStateException(result.error ?: "Unknown error")
                        )
                    }
                }
                
                else -> {
                    // 解析为普通消息
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
     * 处理 Challenge
     */
    private fun handleChallenge(challenge: AuthChallenge) {
        appScope.launch {
            try {
                // 验证挑战
                val validateResult = authHandler.handleChallenge(challenge)
                if (validateResult.isFailure) {
                    Log.e(TAG, "挑战验证失败：${validateResult.exceptionOrNull()?.message}")
                    _connectionState.value = WebSocketConnectionState.Error(
                        validateResult.exceptionOrNull() ?: IllegalStateException("Challenge validation failed")
                    )
                    return@launch
                }
                
                // 构建认证响应
                val authResponse = authHandler.buildAuthResponse()
                val responseJson = json.encodeToString<AuthResponse>(authResponse)
                
                Log.d(TAG, "发送认证响应：$responseJson")
                
                // 发送响应
                webSocket?.send(responseJson)
                
            } catch (e: Exception) {
                Log.e(TAG, "处理挑战失败：${e.message}", e)
                _connectionState.value = WebSocketConnectionState.Error(e)
            }
        }
    }
    
    /**
     * 发送消息
     */
    suspend fun send(message: GatewayMessage): Result<Unit> = withContext(Dispatchers.IO) {
        val ws = webSocket
            ?: return@withContext Result.failure(IllegalStateException("Not connected"))
        
        return@withContext try {
            val json = MessageParser.serialize(message)
            val success = ws.send(json)
            if (success) {
                Log.d(TAG, "消息已发送：${message.type}")
                Result.success(Unit)
            } else {
                Result.failure(IllegalStateException("Failed to send message"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "发送失败：${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * 断开连接
     */
    suspend fun disconnect(): Result<Unit> = withContext(Dispatchers.IO) {
        Log.d(TAG, "断开连接...")
        
        // 取消重连
        reconnectJob?.cancel()
        reconnectJob = null
        
        // 取消心跳
        heartbeatJob?.cancel()
        heartbeatJob = null
        
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
                    val ping = GatewayMessage.Ping(System.currentTimeMillis())
                    send(ping)
                    Log.d(TAG, "心跳发送")
                } catch (e: Exception) {
                    Log.w(TAG, "心跳发送失败：${e.message}")
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
    
    /**
     * 获取连接延迟
     */
    suspend fun measureLatency(): Long? = withContext(Dispatchers.IO) {
        val ws = webSocket ?: return@withContext null
        
        val start = System.currentTimeMillis()
        val ping = GatewayMessage.Ping(start)
        
        return@withContext try {
            ws.send(MessageParser.serialize(ping))
            delay(100)
            System.currentTimeMillis() - start
        } catch (e: Exception) {
            Log.w(TAG, "延迟测量失败：${e.message}")
            null
        }
    }
}

/**
 * 带超时的连接辅助函数
 */
suspend fun <T> withTimeoutOrNull(timeMillis: Long, block: suspend CoroutineScope.() -> T): T? {
    return try {
        kotlinx.coroutines.withTimeout(timeMillis, block)
    } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
        null
    }
}
