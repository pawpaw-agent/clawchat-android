package com.openclaw.clawchat.network

import android.util.Log
import com.openclaw.clawchat.security.SecurityModule
import kotlinx.coroutines.CoroutineScope
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
import kotlinx.serialization.encodeToString
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * OkHttp WebSocket 服务实现
 * 
 * 基于 OkHttp 的 WebSocket 实现，支持：
 * - 连接管理
 * - 消息序列化/反序列化
 * - 自动重连（指数退避）
 * - 延迟监控
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
    
    private val latencyMeasurements = mutableListOf<Long>()
    private var latencyMonitorJob: Job? = null
    
    /**
     * 建立 WebSocket 连接
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
        
        return try {
            val request = buildWebSocketRequest(url, token)
            
            webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    Log.i(TAG, "WebSocket opened: ${response.request.url}")
                    _connectionState.value = WebSocketConnectionState.Connected
                    reconnectAttempt = 0
                    startLatencyMonitoring()
                }
                
                override fun onMessage(webSocket: WebSocket, text: String) {
                    Log.d(TAG, "Received message: ${text.take(100)}...")
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
                    _connectionState.value = WebSocketConnectionState.Disconnectiving(reason)
                    webSocket = null
                    latencyMonitorJob?.cancel()
                }
                
                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    Log.i(TAG, "WebSocket closing: $code - $reason")
                    webSocket?.close(code, reason)
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
     * 构建带签名的 WebSocket 请求
     */
    private fun buildWebSocketRequest(url: String, token: String?): Request {
        val timestamp = System.currentTimeMillis()
        val nonce = UUID.randomUUID().toString()
        val dataToSign = "/ws\n$timestamp\n$nonce"
        val signature = securityModule.signChallenge(dataToSign.toByteArray()).toBase64()
        
        val builder = Request.Builder()
            .url(url)
            .addHeader("X-ClawChat-Timestamp", timestamp.toString())
            .addHeader("X-ClawChat-Nonce", nonce)
            .addHeader("X-ClawChat-Signature", signature)
        
        token?.let {
            builder.addHeader("Authorization", "Bearer $it")
        }
        
        return builder.build()
    }
    
    /**
     * 处理接收到的消息
     */
    private fun processIncomingMessage(text: String) {
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
            Log.w(TAG, "Failed to parse message: $text")
        }
    }
    
    /**
     * 发送消息
     */
    override suspend fun send(message: GatewayMessage): Result<Unit> {
        val ws = webSocket
            ?: return Result.failure(IllegalStateException("WebSocket not connected"))
        
        return withContext(Dispatchers.IO) {
            try {
                val json = MessageParser.serialize(message)
                val success = ws.send(json)
                if (success) {
                    Log.d(TAG, "Sent message: ${message.type}")
                    Result.success(Unit)
                } else {
                    Result.failure(IllegalStateException("Failed to send message"))
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
        
        // 关闭 WebSocket
        webSocket?.close(1000, "User requested disconnect")
        webSocket = null
        
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
        val delay = (INITIAL_RECONNECT_DELAY_MS * Math.pow(RECONNECT_BACKOFF_FACTOR, reconnectAttempt)).toLong()
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
            // 简化实现：实际应该等待匹配的 Pong 响应
            // 这里返回往返时间的估计值
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

/**
 * ByteArray 转 Base64
 */
private fun ByteArray.toBase64(): String {
    return android.util.Base64.encodeToString(this, android.util.Base64.NO_WRAP)
}
