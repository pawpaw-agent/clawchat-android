package com.openclaw.clawchat.security

import android.util.Base64
import android.util.Log
import java.security.Signature

/**
 * ServerSignatureVerifier - 服务端签名验证器
 * 
 * 负责验证服务端响应的签名，确保消息来源可信。
 * 
 * 验证流程：
 * 1. 验证时间戳（防止重放攻击，±5 分钟窗口）
 * 2. 验证 Nonce（防止重复请求）
 * 3. 验证签名（使用服务端公钥）
 * 
 * 签名格式：
 * - 算法：ECDSA secp256r1 (SHA256withECDSA)
 * - 签名字符串：method + path + timestamp + nonce + bodyHash
 * 
 * 安全特性：
 * - 时间戳容忍窗口：±5 分钟
 * - Nonce 缓存：5 分钟内不重复
 * - 支持主公钥和备份公钥（密钥轮换）
 */
class ServerSignatureVerifier(
    private val publicKeyManager: ServerPublicKeyManager,
    private val nonceCache: NonceCache = NonceCache()
) {
    
    companion object {
        private const val TAG = "ServerSignatureVerifier"
        
        // 时间戳容忍窗口（毫秒）
        private const val TIMESTAMP_TOLERANCE_MS = 5 * 60 * 1000L // 5 分钟
        
        // 签名算法
        private const val SIGNATURE_ALGORITHM = "SHA256withECDSA"
    }
    
    /**
     * 验证服务端消息签名
     * 
     * @param method HTTP 方法（GET/POST 等）或消息类型
     * @param path 请求路径或消息 ID
     * @param body 消息体（可选）
     * @param timestamp 消息时间戳
     * @param nonce 消息随机数
     * @param signature Base64 编码的签名
     * @return 验证结果
     */
    fun verify(
        method: String,
        path: String,
        body: String?,
        timestamp: Long,
        nonce: String,
        signature: String
    ): SignatureVerificationResult {
        // 1. 验证时间戳
        val timestampResult = verifyTimestamp(timestamp)
        if (timestampResult != SignatureVerificationResult.Success) {
            Log.w(TAG, "Timestamp verification failed: $timestampResult")
            return timestampResult
        }
        
        // 2. 验证 Nonce
        val nonceResult = verifyNonce(nonce, timestamp)
        if (nonceResult != SignatureVerificationResult.Success) {
            Log.w(TAG, "Nonce verification failed: $nonceResult")
            return nonceResult
        }
        
        // 3. 构建签名字符串
        val bodyHash = body?.sha256() ?: ""
        val dataToVerify = "$method\n$path\n$timestamp\n$nonce\n$bodyHash"
        
        // 4. 验证签名（尝试主公钥和备份公钥）
        val signatureResult = verifySignature(dataToVerify, signature)
        if (signatureResult != SignatureVerificationResult.Success) {
            Log.w(TAG, "Signature verification failed: $signatureResult")
            return signatureResult
        }
        
        Log.d(TAG, "Signature verification successful")
        return SignatureVerificationResult.Success
    }
    
    /**
     * 验证 WebSocket 消息签名
     * 
     * WebSocket 消息使用简化的签名格式：
     * - 签名字符串：messageType + sessionId + timestamp + nonce + contentHash
     * 
     * @param messageType 消息类型
     * @param sessionId 会话 ID
     * @param content 消息内容
     * @param timestamp 消息时间戳
     * @param nonce 消息随机数
     * @param signature Base64 编码的签名
     * @return 验证结果
     */
    fun verifyWebSocketMessage(
        messageType: String,
        sessionId: String,
        content: String,
        timestamp: Long,
        nonce: String,
        signature: String
    ): SignatureVerificationResult {
        // 1. 验证时间戳
        val timestampResult = verifyTimestamp(timestamp)
        if (timestampResult != SignatureVerificationResult.Success) {
            return timestampResult
        }
        
        // 2. 验证 Nonce
        val nonceResult = verifyNonce(nonce, timestamp)
        if (nonceResult != SignatureVerificationResult.Success) {
            return nonceResult
        }
        
        // 3. 构建签名字符串
        val contentHash = content.sha256()
        val dataToVerify = "$messageType\n$sessionId\n$timestamp\n$nonce\n$contentHash"
        
        // 4. 验证签名
        val signatureResult = verifySignature(dataToVerify, signature)
        if (signatureResult != SignatureVerificationResult.Success) {
            return signatureResult
        }
        
        return SignatureVerificationResult.Success
    }
    
    // ==================== 内部验证方法 ====================
    
    /**
     * 验证时间戳
     */
    private fun verifyTimestamp(timestamp: Long): SignatureVerificationResult {
        val now = System.currentTimeMillis()
        val timeDiff = Math.abs(now - timestamp)
        
        if (timeDiff > TIMESTAMP_TOLERANCE_MS) {
            return SignatureVerificationResult.TimestampExpired(
                expected = now - TIMESTAMP_TOLERANCE_MS,
                actual = timestamp,
                diff = timeDiff
            )
        }
        
        return SignatureVerificationResult.Success
    }
    
    /**
     * 验证 Nonce
     */
    private fun verifyNonce(nonce: String, timestamp: Long): SignatureVerificationResult {
        if (!nonceCache.checkAndAdd(nonce, timestamp)) {
            return SignatureVerificationResult.NonceInvalid(nonce)
        }
        return SignatureVerificationResult.Success
    }
    
    /**
     * 验证签名（尝试所有可用公钥）
     */
    private fun verifySignature(dataToVerify: String, signatureBase64: String): SignatureVerificationResult {
        val signatureBytes = try {
            Base64.decode(signatureBase64, Base64.NO_WRAP)
        } catch (e: Exception) {
            return SignatureVerificationResult.SignatureDecodeError(e.message ?: "Invalid Base64")
        }
        
        // 尝试主公钥
        publicKeyManager.getPrimaryPublicKeyObject()?.let { publicKey ->
            if (verifyWithKey(publicKey, dataToVerify, signatureBytes)) {
                return SignatureVerificationResult.Success
            }
        }
        
        // 尝试备份公钥（用于密钥轮换）
        publicKeyManager.getBackupPublicKeyObject()?.let { publicKey ->
            if (verifyWithKey(publicKey, dataToVerify, signatureBytes)) {
                Log.i(TAG, "Signature verified with backup public key")
                return SignatureVerificationResult.Success
            }
        }
        
        return SignatureVerificationResult.SignatureMismatch
    }
    
    /**
     * 使用指定公钥验证签名
     */
    private fun verifyWithKey(
        publicKey: java.security.PublicKey,
        data: String,
        signatureBytes: ByteArray
    ): Boolean {
        return try {
            val sig = Signature.getInstance(SIGNATURE_ALGORITHM)
            sig.initVerify(publicKey)
            sig.update(data.toByteArray(Charsets.UTF_8))
            sig.verify(signatureBytes)
        } catch (e: Exception) {
            Log.e(TAG, "Signature verification error", e)
            false
        }
    }
    
    /**
     * 字符串 SHA256 哈希
     */
    private fun String.sha256(): String {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        val digest = md.digest(this.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}

/**
 * 签名验证结果
 */
sealed class SignatureVerificationResult {
    /** 验证成功 */
    data object Success : SignatureVerificationResult()
    
    /** 时间戳过期 */
    data class TimestampExpired(
        val expected: Long,
        val actual: Long,
        val diff: Long
    ) : SignatureVerificationResult()
    
    /** Nonce 无效（重复或过期） */
    data class NonceInvalid(val nonce: String) : SignatureVerificationResult()
    
    /** 签名解码错误 */
    data class SignatureDecodeError(val message: String) : SignatureVerificationResult()
    
    /** 签名不匹配 */
    data object SignatureMismatch : SignatureVerificationResult()
    
    /** 公钥缺失 */
    data object PublicKeyMissing : SignatureVerificationResult()
    
    /**
     * 是否成功
     */
    fun isSuccess(): Boolean = this is Success
    
    /**
     * 获取错误消息
     */
    fun getErrorMessage(): String? {
        return when (this) {
            is Success -> null
            is TimestampExpired -> "Timestamp expired (diff: ${diff / 1000}s)"
            is NonceInvalid -> "Invalid or duplicate nonce"
            is SignatureDecodeError -> "Signature decode error: $message"
            SignatureMismatch -> "Signature mismatch"
            PublicKeyMissing -> "Server public key not configured"
        }
    }
    
    /**
     * 转换为异常
     */
    fun toException(): SecurityException? {
        val message = getErrorMessage() ?: return null
        return SecurityException(message)
    }
}

/**
 * 安全异常类
 */
class SecurityException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

/**
 * 使用示例：
 * 
 * // 1. 初始化
 * val publicKeyManager = ServerPublicKeyManager(context)
 * val verifier = ServerSignatureVerifier(publicKeyManager)
 * 
 * // 2. 验证 HTTP 响应
 * val result = verifier.verify(
 *     method = "GET",
 *     path = "/api/messages",
 *     body = responseBody,
 *     timestamp = timestampHeader.toLong(),
 *     nonce = nonceHeader,
 *     signature = signatureHeader
 * )
 * 
 * if (!result.isSuccess()) {
 *     throw result.toException()
 * }
 * 
 * // 3. 验证 WebSocket 消息
 * val wsResult = verifier.verifyWebSocketMessage(
 *     messageType = "assistantMessage",
 *     sessionId = sessionId,
 *     content = messageContent,
 *     timestamp = messageTimestamp,
 *     nonce = messageNonce,
 *     signature = messageSignature
 * )
 * 
 * if (!wsResult.isSuccess()) {
 *     // 丢弃消息或断开连接
 * }
 */
