package com.openclaw.clawchat.network.protocol

import android.util.Log
import com.openclaw.clawchat.security.SecurityModule
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.json.JSONObject

/**
 * Challenge-Response 认证实现 (Gateway 协议 v3)
 * 
 * 认证流程:
 * 1. 建立 WebSocket 连接
 * 2. 接收 connect.challenge 事件 (包含服务器 nonce)
 * 3. 构建 connect 请求，签名服务器 nonce
 * 4. 接收 connect.ok 事件 (包含 deviceToken)
 * 
 * 关键修复:
 * ✅ 签名服务器提供的 nonce (不是客户端生成的)
 * ✅ 使用正确的事件格式 (type=event, event=connect.challenge)
 * ✅ 使用正确的请求格式 (type=req, method=connect)
 */
class ChallengeResponseAuth(
    private val securityModule: SecurityModule
) {
    companion object {
        private const val TAG = "ChallengeResponseAuth"
        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
    }
    
    // 当前待处理的挑战
    private var pendingChallenge: PendingChallenge? = null
    
    /**
     * 待处理的挑战
     */
    @Serializable
    data class PendingChallenge(
        val nonce: String,
        val timestamp: Long,
        val requestId: String
    )
    
    /**
     * 认证状态
     */
    sealed class AuthState {
        data object Initial : AuthState()
        data object Connected : AuthState()
        data object ChallengeReceived : AuthState()
        data object RequestSent : AuthState()
        data class Success(val deviceToken: String) : AuthState()
        data class Error(val message: String, val code: String? = null) : AuthState()
    }
    
    /**
     * 认证结果
     */
    data class AuthResult(
        val success: Boolean,
        val deviceToken: String? = null,
        val error: String? = null,
        val errorCode: String? = null
    )
    
    /**
     * 步骤 1: 处理 connect.challenge 事件
     * 
     * Gateway 在 WebSocket 连接后自动发送挑战
     */
    fun handleChallenge(challenge: ConnectChallengePayload): Result<String> {
        Log.d(TAG, "收到挑战：nonce=${challenge.nonce}, ts=${challenge.timestamp}")
        
        // 验证 nonce 不为空
        if (challenge.nonce.isBlank()) {
            Log.e(TAG, "无效的 nonce")
            return Result.failure(IllegalStateException("Invalid nonce"))
        }
        
        // 生成请求 ID
        val requestId = "auth-${System.currentTimeMillis()}"
        
        // 存储待处理的挑战
        pendingChallenge = PendingChallenge(
            nonce = challenge.nonce,
            timestamp = challenge.timestamp,
            requestId = requestId
        )
        
        Log.d(TAG, "挑战已存储，requestId=$requestId")
        return Result.success(requestId)
    }
    
    /**
     * 步骤 2: 构建 connect 请求
     * 
     * 签名服务器提供的 nonce 并构建请求
     */
    suspend fun buildConnectRequest(): ConnectRequest {
        val challenge = pendingChallenge
            ?: throw IllegalStateException("No pending challenge. Call handleChallenge() first.")
        
        // 初始化安全模块（生成密钥对和设备 ID）
        val status = securityModule.initialize()
        
        // 获取设备 ID
        val deviceId = status.deviceId 
            ?: throw IllegalStateException("Failed to generate device ID")
        
        // 获取公钥（PEM 格式）
        val publicKey = extractPublicKeyFromPairingRequest()
        
        // 构建签名字符串 (协议 v3 格式)
        // payload = "auth\n$nonce\n$timestamp"
        val timestamp = System.currentTimeMillis()
        val payloadToSign = "auth\n${challenge.nonce}\n$timestamp"
        
        Log.d(TAG, "签名 payload: $payloadToSign")
        
        // 使用 SecurityModule 签名
        val signature = securityModule.signChallenge(payloadToSign)
        
        Log.d(TAG, "签名结果：${signature.take(50)}...")
        
        // 构建客户端信息
        val clientInfo = buildClientInfo()
        
        // 清除待处理的挑战
        pendingChallenge = null
        
        return ConnectRequest(
            device = DeviceInfo(
                id = deviceId,
                publicKey = publicKey
            ),
            client = clientInfo,
            nonce = challenge.nonce,
            signature = signature,
            token = securityModule.getAuthToken()  // 如果有已存储的 token
        )
    }
    
    /**
     * 步骤 3: 处理 connect.ok 响应
     * 
     * 存储 deviceToken
     */
    fun handleConnectOk(connectOk: ConnectOkPayload): AuthResult {
        Log.d(TAG, "认证成功，收到 deviceToken")
        
        // 验证 token 不为空
        if (connectOk.deviceToken.isBlank()) {
            return AuthResult(
                success = false,
                error = "Empty device token"
            )
        }
        
        // 存储 device token
        securityModule.completePairing(connectOk.deviceToken)
        
        Log.d(TAG, "deviceToken 已存储")
        
        return AuthResult(
            success = true,
            deviceToken = connectOk.deviceToken
        )
    }
    
    /**
     * 处理错误事件
     */
    fun handleErrorEvent(error: ErrorPayload): AuthResult {
        Log.e(TAG, "认证错误：${error.code} - ${error.message}")
        
        return AuthResult(
            success = false,
            error = error.message,
            errorCode = error.code
        )
    }
    
    /**
     * 重置认证状态
     */
    fun reset() {
        pendingChallenge = null
    }
    
    // ==================== 内部方法 ====================
    
    /**
     * 从配对请求 JSON 中提取公钥
     */
    private suspend fun extractPublicKeyFromPairingRequest(): String {
        val pairingJson = securityModule.preparePairingRequest("gateway")
        return try {
            val jsonObj = JSONObject(pairingJson)
            jsonObj.getJSONObject("device").getString("publicKey")
        } catch (e: Exception) {
            throw IllegalStateException("Failed to extract public key: ${e.message}")
        }
    }
    
    /**
     * 构建客户端信息
     */
    private fun buildClientInfo(): ClientInfo {
        return ClientInfo(
            clientId = "openclaw-android",
            clientVersion = getClientVersion(),
            platform = "android",
            osVersion = android.os.Build.VERSION.RELEASE,
            deviceModel = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"
        )
    }
    
    /**
     * 获取客户端版本
     */
    private fun getClientVersion(): String {
        return try {
            val clazz = Class.forName("com.openclaw.clawchat.BuildConfig")
            clazz.getField("VERSION_NAME").get(null) as String
        } catch (e: Exception) {
            "1.0.0"
        }
    }
}

/**
 * 认证流程助手函数
 */

/**
 * 从 JSON 解析 ConnectChallengePayload
 */
fun String.toConnectChallenge(): ConnectChallengePayload? {
    return try {
        Json { ignoreUnknownKeys = true }.decodeFromString(this)
    } catch (e: Exception) {
        null
    }
}

/**
 * 从 JSON 解析 ConnectOkPayload
 */
fun String.toConnectOk(): ConnectOkPayload? {
    return try {
        Json { ignoreUnknownKeys = true }.decodeFromString(this)
    } catch (e: Exception) {
        null
    }
}
