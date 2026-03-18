package com.openclaw.clawchat.network.protocol

import android.util.Base64
import android.util.Log
import com.openclaw.clawchat.security.SecurityModule
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.util.UUID

/**
 * Challenge-Response 认证实现
 * 
 * 遵循 Gateway 协议 v3 的认证流程：
 * 1. 客户端发送 AuthRequest（包含 deviceId 和 publicKey）
 * 2. 服务器返回 AuthChallenge（包含 nonce 和过期时间）
 * 3. 客户端签名 nonce 并返回 AuthResponse
 * 4. 服务器验证签名并返回 AuthSuccess（包含 deviceToken）
 * 
 * 关键修复：
 * - ✅ 签名服务器提供的 nonce（而不是客户端生成的 nonce）
 * - ✅ 验证挑战过期时间
 * - ✅ 使用协议 v3 的 payload 格式
 */
class ChallengeResponseAuth(
    private val securityModule: SecurityModule
) {
    companion object {
        private const val TAG = "ChallengeResponseAuth"
        private const val CHALLENGE_TIMEOUT_MS = 300000L // 5 分钟
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
        val expiresAt: Long,
        val protocolVersion: String
    )
    
    /**
     * 认证状态
     */
    sealed class AuthState {
        data object Initial : AuthState()
        data object RequestSent : AuthState()
        data object ChallengeReceived : AuthState()
        data object ResponseSent : AuthState()
        data class Success(val deviceToken: String) : AuthState()
        data class Error(val message: String, val code: ProtocolErrorCode? = null) : AuthState()
    }
    
    /**
     * 认证结果
     */
    data class AuthResult(
        val success: Boolean,
        val deviceToken: String? = null,
        val error: String? = null,
        val errorCode: ProtocolErrorCode? = null
    )
    
    /**
     * 步骤 1: 构建认证请求
     * 
     * 发送设备信息到服务器，请求挑战
     */
    suspend fun buildAuthRequest(): AuthRequest {
        // 初始化安全模块（生成密钥对和设备 ID）
        val status = securityModule.initialize()
        
        // 获取设备 ID
        val deviceId = status.deviceId 
            ?: throw IllegalStateException("Failed to generate device ID")
        
        // 获取公钥（PEM 格式）
        val publicKey = securityModule.preparePairingRequest("gateway")
            .let { jsonStr ->
                // 从配对请求 JSON 中提取公钥
                try {
                    val orgJson = org.json.JSONObject(jsonStr)
                    orgJson.getJSONObject("device").getString("publicKey")
                } catch (e: Exception) {
                    throw IllegalStateException("Failed to extract public key: ${e.message}")
                }
            }
        
        // 构建客户端信息
        val clientInfo = ClientInfo(
            clientId = "openclaw-android",
            clientVersion = getClientVersion(),
            platform = "android",
            osVersion = android.os.Build.VERSION.RELEASE,
            deviceModel = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}",
            protocolVersion = WebSocketProtocol.VERSION
        )
        
        return AuthRequest(
            deviceId = deviceId,
            publicKey = publicKey,
            clientInfo = clientInfo
        )
    }
    
    /**
     * 步骤 2: 处理服务器返回的挑战
     * 
     * 验证挑战的有效性并存储 nonce
     */
    fun handleChallenge(challenge: AuthChallenge): Result<Unit> {
        // 验证协议版本
        if (!isProtocolVersionCompatible(challenge.protocolVersion)) {
            Log.e(TAG, "协议版本不兼容：${challenge.protocolVersion}")
            return Result.failure(
                IllegalStateException("Protocol version mismatch: ${challenge.protocolVersion}")
            )
        }
        
        // 验证挑战是否过期
        val now = System.currentTimeMillis()
        if (now >= challenge.expiresAt) {
            Log.e(TAG, "挑战已过期：${challenge.expiresAt}")
            pendingChallenge = null
            return Result.failure(
                IllegalStateException("Challenge expired")
            )
        }
        
        // 验证 nonce 格式
        if (!isValidNonce(challenge.nonce)) {
            Log.e(TAG, "无效的 nonce 格式")
            return Result.failure(
                IllegalStateException("Invalid nonce format")
            )
        }
        
        // 存储待处理的挑战
        pendingChallenge = PendingChallenge(
            nonce = challenge.nonce,
            expiresAt = challenge.expiresAt,
            protocolVersion = challenge.protocolVersion
        )
        
        Log.d(TAG, "挑战已接收，过期时间：${challenge.expiresAt - now}ms")
        return Result.success(Unit)
    }
    
    /**
     * 步骤 3: 签名挑战并构建响应
     * 
     * 关键修复：签名服务器提供的 nonce，而不是客户端生成的 nonce
     */
    suspend fun buildAuthResponse(): AuthResponse {
        val challenge = pendingChallenge
            ?: throw IllegalStateException("No pending challenge. Call handleChallenge() first.")
        
        // 检查挑战是否已过期
        val now = System.currentTimeMillis()
        if (now >= challenge.expiresAt) {
            pendingChallenge = null
            throw IllegalStateException("Challenge expired")
        }
        
        // 获取设备 ID
        val deviceId = securityModule.getSecurityStatus().deviceId
            ?: throw IllegalStateException("Device ID not available")
        
        // 关键修复：签名服务器提供的 nonce
        // 协议 v3 要求签名的 payload 格式：
        // "auth\n$nonce\n$timestamp"
        val timestamp = System.currentTimeMillis()
        val payloadToSign = "auth\n${challenge.nonce}\n$timestamp"
        
        Log.d(TAG, "签名 payload: $payloadToSign")
        
        // 使用 SecurityModule 签名
        val signature = securityModule.signChallenge(payloadToSign)
        
        Log.d(TAG, "签名结果：${signature.take(50)}...")
        
        // 清除待处理的挑战
        pendingChallenge = null
        
        return AuthResponse(
            nonce = challenge.nonce,
            signature = signature,
            deviceId = deviceId
        )
    }
    
    /**
     * 步骤 4: 处理认证成功响应
     * 
     * 存储设备 token
     */
    fun handleAuthSuccess(success: AuthSuccess): AuthResult {
        // 验证协议版本
        if (!isProtocolVersionCompatible(success.protocolVersion)) {
            return AuthResult(
                success = false,
                error = "Protocol version mismatch",
                errorCode = ProtocolErrorCode.PROTOCOL_VERSION_MISMATCH
            )
        }
        
        // 验证 token 不为空
        if (success.deviceToken.isBlank()) {
            return AuthResult(
                success = false,
                error = "Empty device token",
                errorCode = ProtocolErrorCode.AUTH_FAILED
            )
        }
        
        // 存储设备 token
        securityModule.completePairing(success.deviceToken)
        
        Log.d(TAG, "认证成功，token 已存储")
        
        return AuthResult(
            success = true,
            deviceToken = success.deviceToken
        )
    }
    
    /**
     * 处理认证错误
     */
    fun handleAuthError(error: ProtocolError): AuthResult {
        Log.e(TAG, "认证错误：${error.code} - ${error.message}")
        
        val errorCode = when (error.code) {
            1001 -> ProtocolErrorCode.PROTOCOL_VERSION_MISMATCH
            1002 -> ProtocolErrorCode.INVALID_MESSAGE_FORMAT
            2000 -> ProtocolErrorCode.AUTH_REQUIRED
            2001 -> ProtocolErrorCode.AUTH_FAILED
            2002 -> ProtocolErrorCode.INVALID_SIGNATURE
            2003 -> ProtocolErrorCode.EXPIRED_CHALLENGE
            2004 -> ProtocolErrorCode.INVALID_NONCE
            2005 -> ProtocolErrorCode.DEVICE_NOT_PAIRED
            2006 -> ProtocolErrorCode.TOKEN_EXPIRED
            2007 -> ProtocolErrorCode.TOKEN_REVOKED
            else -> ProtocolErrorCode.UNKNOWN_ERROR
        }
        
        return AuthResult(
            success = false,
            error = error.message,
            errorCode = errorCode
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
     * 检查协议版本兼容性
     */
    private fun isProtocolVersionCompatible(version: String): Boolean {
        return try {
            val otherVersion = ProtocolVersion.fromString(version)
            val currentVersion = ProtocolVersion.fromString(WebSocketProtocol.VERSION)
            currentVersion.isCompatibleWith(otherVersion)
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 验证 nonce 格式
     * 
     * Nonce 应该是：
     * - UUID 格式
     * - 或 Base64 编码的随机字节
     * - 长度至少 16 字符
     */
    private fun isValidNonce(nonce: String): Boolean {
        if (nonce.length < 16) return false
        
        // 尝试解析为 UUID
        try {
            UUID.fromString(nonce)
            return true
        } catch (e: Exception) {
            // 不是 UUID 格式，继续检查
        }
        
        // 尝试解析为 Base64
        return try {
            Base64.decode(nonce, Base64.DEFAULT)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 获取客户端版本
     */
    private fun getClientVersion(): String {
        // 从 BuildConfig 或硬编码获取版本
        return try {
            // 如果有 BuildConfig
            val clazz = Class.forName("com.openclaw.clawchat.BuildConfig")
            clazz.getField("VERSION_NAME").get(null) as String
        } catch (e: Exception) {
            "1.0.0" // 默认版本
        }
    }
}

/**
 * 认证流程助手函数
 */

/**
 * 从 JSON 解析 AuthChallenge
 */
fun String.toAuthChallenge(): AuthChallenge? {
    return try {
        Json { ignoreUnknownKeys = true }.decodeFromString(this)
    } catch (e: Exception) {
        null
    }
}

/**
 * 从 JSON 解析 AuthSuccess
 */
fun String.toAuthSuccess(): AuthSuccess? {
    return try {
        Json { ignoreUnknownKeys = true }.decodeFromString(this)
    } catch (e: Exception) {
        null
    }
}

/**
 * 从 JSON 解析 ProtocolError
 */
fun String.toProtocolError(): ProtocolError? {
    return try {
        Json { ignoreUnknownKeys = true }.decodeFromString(this)
    } catch (e: Exception) {
        null
    }
}
