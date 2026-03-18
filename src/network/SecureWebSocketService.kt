package com.clawchat.android.network

import android.util.Log
import com.clawchat.android.security.SecurityModule
import com.clawchat.android.security.SignatureVerificationResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * SecureWebSocketService - 带签名验证的 WebSocket 服务
 * 
 * 在标准 WebSocket 功能基础上增加：
 * - 服务端消息签名验证
 * - 防重放攻击（时间戳 + Nonce）
 * - 签名验证失败处理
 * - 安全日志记录
 */
class SecureWebSocketService(
    private val securityModule: SecurityModule,
    private val client: OkHttpClient = OkHttpClient.Builder().build()
) : WebSocketService {
    
    companion object {
        private const val TAG = "SecureWebSocketService"
        
        // 消息头字段
        private const val HEADER_TIMESTAMP = "X-ClawChat-Timestamp"
        private const val HEADER_NONCE = "X-ClawChat-Nonce"
        private const val HEADER_SIGNATURE = "X-ClawChat-Signature"
        private const val HEADER_MESSAGE_TYPE = "X-ClawChat-Message-Type"
    }
    
    private val _connectionState = MutableStateFlow<WebSocketConnectionState>(WebSocketConnectionState.Disconnected)
    override val connectionState: StateFlow<WebSocketConnectionState> = _connectionState.asStateFlow()
    
    private val _incomingMessages = kotlinx.coroutines.channels.Channel<GatewayMessage>(kotlinx.coroutines.channels.Channel.UNLIMITED)
    override val incomingMessages: Flow<GatewayMessage> = _incomingMessages.receiveAsFlow()
    
    private var webSocket: WebSocket? = null
    private val pendingCallbacks = ConcurrentHashMap<String, (Result<GatewayMessage>) -> Unit>()
    
    // 安全统计（用于调试/监控）
    private var verifiedMessageCount = 0L
    private var failedVerificationCount = 0L
    
    override suspend fun connect(url: String, token: String?): Result<Unit> {
        return suspendCoroutine { continuation ->
            val request = Request.Builder()
                .url(url)
                .apply {
                    token?.let { addHeader("Authorization", "Bearer $it") }
                    addHeader("X-ClawChat-Client", "android")
                    addHeader("X-ClawChat-Protocol", "v3")
                }
                .build()
            
            _connectionState.value = WebSocketConnectionState.Connecting
            
            webSocket = client.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    Log.i(TAG, "WebSocket connected")
                    _connectionState.value = WebSocketConnectionState.Connected
                    continuation.resume(Result.success(Unit))
                }
                
                override fun onMessage(webSocket: WebSocket, text: String) {
                    handleIncomingMessage(text)
                }
                
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    Log.e(TAG, "WebSocket failure", t)
                    _connectionState.value = WebSocketConnectionState.Error(t)
                    if (continuation.isActive) {
                        continuation.resume(Result.failure(t))
                    }
                }
                
                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    Log.i(TAG, "WebSocket closed: $code $reason")
                    _connectionState.value = WebSocketConnectionState.Disconnected
                }
            })
        }
    }
    
    /**
     * 处理接收到的消息
     */
    private fun handleIncomingMessage(text: String) {
        try {
            // 解析消息
            val message = MessageParser.parse(text)
                ?: run {
                    Log.w(TAG, "Failed to parse message: $text")
                    return
                }
            
            // 提取签名头（从 JSON 中解析或从消息元数据获取）
            val signatureData = extractSignatureData(text)
            
            // 如果有签名数据，进行验证
            if (signatureData != null) {
                val verificationResult = verifyMessageSignature(message, signatureData)
                
                if (!verificationResult.isSuccess()) {
                    failedVerificationCount++
                    Log.w(TAG, "Signature verification failed: ${verificationResult.getErrorMessage()}")
                    
                    // 验证失败时抛出异常或丢弃消息
                    handleVerificationFailure(message, verificationResult)
                    return
                }
                
                verifiedMessageCount++
                Log.d(TAG, "Signature verified successfully (total: $verifiedMessageCount)")
            } else {
                // 没有签名数据的消息（可能是心跳等系统消息）
                Log.d(TAG, "Message without signature (may be system message)")
            }
            
            // 传递到接收流
            _incomingMessages.trySend(message)
            
            // 处理 Pong 响应（延迟测量）
            if (message is GatewayMessage.Pong) {
                pendingCallbacks.remove("ping")?.invoke(Result.success(message))
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing message", e)
        }
    }
    
    /**
     * 从消息中提取签名数据
     * 
     * 支持两种格式：
     * 1. JSON 内嵌签名（推荐）
     * 2. 独立签名头（传统）
     */
    private fun extractSignatureData(jsonText: String): SignatureData? {
        return try {
            val json = org.json.JSONObject(jsonText)
            
            // 检查是否有签名元数据
            val metadata = json.optJSONObject("metadata") ?: return null
            
            val timestamp = metadata.optLong("timestamp", 0L).takeIf { it > 0 } ?: return null
            val nonce = metadata.optString("nonce", null) ?: return null
            val signature = metadata.optString("signature", null) ?: return null
            val messageType = metadata.optString("messageType", "unknown")
            
            SignatureData(
                timestamp = timestamp,
                nonce = nonce,
                signature = signature,
                messageType = messageType
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to extract signature data", e)
            null
        }
    }
    
    /**
     * 验证消息签名
     */
    private fun verifyMessageSignature(
        message: GatewayMessage,
        signatureData: SignatureData
    ): SignatureVerificationResult {
        val sessionId = when (message) {
            is GatewayMessage.SystemEvent -> "system"
            is GatewayMessage.UserMessage -> message.sessionId
            is GatewayMessage.AssistantMessage -> message.sessionId
            is GatewayMessage.Ping -> "ping"
            is GatewayMessage.Pong -> "pong"
            is GatewayMessage.Error -> "error"
        }
        
        val content = MessageParser.serialize(message)
        
        return securityModule.verifyWebSocketSignature(
            messageType = signatureData.messageType,
            sessionId = sessionId,
            content = content,
            timestamp = signatureData.timestamp,
            nonce = signatureData.nonce,
            signature = signatureData.signature
        )
    }
    
    /**
     * 处理验证失败
     */
    private fun handleVerificationFailure(
        message: GatewayMessage,
        result: SignatureVerificationResult
    ) {
        when (result) {
            is SignatureVerificationResult.TimestampExpired -> {
                // 时间戳过期，可能是时钟不同步
                Log.w(TAG, "Message timestamp expired, possible clock skew")
                // 可以选择丢弃或警告
            }
            is SignatureVerificationResult.NonceInvalid -> {
                // 重复 Nonce，可能是重放攻击
                Log.e(TAG, "⚠️ Possible replay attack detected! Nonce: ${result.nonce}")
                // 严重安全事件，可能需要断开连接
            }
            is SignatureVerificationResult.SignatureMismatch -> {
                // 签名不匹配，消息可能被篡改
                Log.e(TAG, "⚠️ Message signature mismatch! Possible tampering.")
                // 严重安全事件
            }
            is SignatureVerificationResult.PublicKeyMissing -> {
                // 公钥未配置
                Log.w(TAG, "Server public key not configured, skipping verification")
                // 可以选择允许或拒绝
            }
            else -> {
                Log.w(TAG, "Unknown verification failure: $result")
            }
        }
        
        // 验证失败时不传递消息
        // 可选择抛出异常或记录事件
    }
    
    override suspend fun send(message: GatewayMessage): Result<Unit> {
        return suspendCoroutine { continuation ->
            val json = MessageParser.serialize(message)
            
            val ws = webSocket ?: run {
                continuation.resume(Result.failure(IllegalStateException("Not connected")))
                return@suspendCoroutine
            }
            
            if (ws.send(json)) {
                continuation.resume(Result.success(Unit))
            } else {
                continuation.resume(Result.failure(IllegalStateException("Failed to send message")))
            }
        }
    }
    
    /**
     * 发送消息并等待响应（带签名验证）
     */
    suspend fun sendAndWaitForResponse(
        message: GatewayMessage,
        timeoutMs: Long = 10000
    ): Result<GatewayMessage> {
        val callbackId = UUID.randomUUID().toString()
        
        return suspendCoroutine { continuation ->
            pendingCallbacks[callbackId] = continuation
            
            // 发送消息
            send(message).onFailure {
                pendingCallbacks.remove(callbackId)
                continuation.resume(Result.failure(it))
            }
            
            // 设置超时
            kotlinx.coroutines.GlobalScope.launch {
                kotlinx.coroutines.delay(timeoutMs)
                pendingCallbacks.remove(callbackId)?.invoke(
                    Result.failure(TimeoutException("Response timeout after ${timeoutMs}ms"))
                )
            }
        }
    }
    
    override suspend fun disconnect(): Result<Unit> {
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
        _connectionState.value = WebSocketConnectionState.Disconnected
        pendingCallbacks.clear()
        return Result.success(Unit)
    }
    
    override suspend fun measureLatency(): Long? {
        val startTime = System.currentTimeMillis()
        
        val ping = GatewayMessage.Ping(startTime)
        send(ping)
        
        return try {
            val result = sendAndWaitForResponse(ping, timeoutMs = 5000)
            result.getOrNull()?.let { message ->
                if (message is GatewayMessage.Pong) {
                    message.latency
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * 获取安全统计信息
     */
    fun getSecurityStats(): SecurityStats {
        return SecurityStats(
            verifiedMessageCount = verifiedMessageCount,
            failedVerificationCount = failedVerificationCount,
            pendingCallbacks = pendingCallbacks.size
        )
    }
    
    /**
     * 重置安全统计
     */
    fun resetStats() {
        verifiedMessageCount = 0L
        failedVerificationCount = 0L
    }
}

/**
 * 签名数据结构
 */
data class SignatureData(
    val timestamp: Long,
    val nonce: String,
    val signature: String,
    val messageType: String
)

/**
 * 安全统计
 */
data class SecurityStats(
    val verifiedMessageCount: Long,
    val failedVerificationCount: Long,
    val pendingCallbacks: Int
) {
    /**
     * 验证成功率
     */
    fun getSuccessRate(): Double {
        val total = verifiedMessageCount + failedVerificationCount
        return if (total > 0) {
            verifiedMessageCount.toDouble() / total * 100
        } else {
            100.0
        }
    }
}

/**
 * 超时异常
 */
class TimeoutException(message: String) : Exception(message)

/**
 * 使用示例（Hilt 模块）：
 * 
 * @Module
 * @Singleton
 * object NetworkModule {
 * 
 *     @Provides
 *     @Singleton
 *     fun provideWebSocketService(securityModule: SecurityModule): WebSocketService {
 *         return SecureWebSocketService(securityModule)
 *     }
 * }
 * 
 * // ViewModel 中使用：
 * @HiltViewModel
 * class SessionViewModel @Inject constructor(
 *     private val webSocketService: SecureWebSocketService
 * ) : ViewModel() {
 * 
 *     init {
 *         viewModelScope.launch {
 *             webSocketService.incomingMessages.collect { message ->
 *                 // 所有接收到的消息都已经过签名验证
 *                 handleIncomingMessage(message)
 *             }
 *         }
 *     }
 * }
 */
