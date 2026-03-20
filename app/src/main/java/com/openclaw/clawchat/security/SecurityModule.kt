package com.openclaw.clawchat.security

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer

/**
 * SecurityModule - 安全模块统一入口
 * 
 * 整合 KeystoreManager、EncryptedStorage，
 * 提供完整的设备配对和安全存储功能。
 * 
 * Gateway 协议 v3 签名规范：
 * - 密钥：Ed25519
 * - 签名 payload v3: "v3|deviceId|clientId|clientMode|role|scopes|signedAtMs|token|nonce|platform|deviceFamily"
 * - 公钥：raw 32 bytes base64url
 * - deviceId: sha256(raw public key).hex()
 * 
 * 使用 Hilt 单例注入，整个应用生命周期只创建一次。
 */
class SecurityModule(private val context: Context) {
    
    companion object {
        private const val TAG = "SecurityModule"
        private const val KEYPAIR_ALIAS = "clawchat_device_key"
    }
    
    // 核心组件
    private val encryptedStorage = EncryptedStorage(context)
    private val keystoreManager = KeystoreManager(
        alias = KEYPAIR_ALIAS,
        softwareKeyStore = encryptedStorage as? SoftwareKeyStore
    )
    
    // ==================== 公开 API ====================
    
    /**
     * 初始化安全模块
     * 
     * 生成密钥对和设备 ID（如不存在）。
     * 应在 Application onCreate 中调用。
     */
    suspend fun initialize(): SecurityStatus = withContext(Dispatchers.IO) {
        try {
            // 生成 Ed25519 密钥对
            if (!keystoreManager.hasKeyPair()) {
                Log.i(TAG, "Generating new Ed25519 device key pair...")
                keystoreManager.generateKeyPair()
            }
            
            // 使用密钥派生 deviceId（与 Gateway 对齐）
            val derivedDeviceId = keystoreManager.deriveDeviceId()
            val storedDeviceId = encryptedStorage.getDeviceId()
            
            if (storedDeviceId != derivedDeviceId) {
                Log.i(TAG, "Updating device ID from key fingerprint...")
                encryptedStorage.saveDeviceId(derivedDeviceId)
            }
            
            getSecurityStatus()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize security module", e)
            SecurityStatus(
                isInitialized = false,
                isPaired = false,
                hasKeyPair = false,
                error = e.message
            )
        }
    }
    
    /**
     * 获取当前安全状态
     */
    fun getSecurityStatus(): SecurityStatus {
        return SecurityStatus(
            isInitialized = true,
            isPaired = encryptedStorage.isPaired(),
            hasKeyPair = keystoreManager.hasKeyPair(),
            deviceId = encryptedStorage.getDeviceId(),
            hasDeviceToken = encryptedStorage.hasDeviceToken()
        )
    }
    
    /**
     * 获取 deviceId
     * 
     * 由 sha256(raw Ed25519 public key) 派生，与 Gateway fingerprintPublicKey() 一致
     */
    fun getDeviceId(): String? {
        return encryptedStorage.getDeviceId()
    }
    
    /**
     * 获取公钥 raw 32 bytes 的 base64url 编码
     * 
     * Gateway connect 协议要求此格式
     */
    fun getPublicKeyBase64Url(): String {
        return keystoreManager.getPublicKeyBase64Url()
    }
    
    /**
     * 构建 v3 签名 payload 并签名
     * 
     * v3 payload 格式: "v3|{deviceId}|{clientId}|{clientMode}|{role}|{scopes}|{signedAtMs}|{token}|{nonce}|{platform}|{deviceFamily}"
     * 
     * @param nonce Server 提供的 challenge nonce
     * @param signedAtMs 签名时间戳（毫秒）
     * @param clientId 客户端 ID
     * @param clientMode 客户端模式（如 "cli"）
     * @param role 连接角色（"operator" 或 "node"）
     * @param scopes 权限范围列表
     * @param token Gateway token（参与签名绑定）
     * @param platform 平台标识
     * @param deviceFamily 设备类型（如 "phone"）
     * @return 签名的 base64url 编码
     */
    fun signV3Payload(
        nonce: String,
        signedAtMs: Long = System.currentTimeMillis(),
        clientId: String = "openclaw-android",
        clientMode: String = "ui",
        role: String = "operator",
        scopes: List<String> = listOf("operator.read", "operator.write"),
        token: String = "",
        platform: String = "android",
        deviceFamily: String = "phone"
    ): SignedPayload {
        val deviceId = getDeviceId()
            ?: throw IllegalStateException("Device not initialized. Call initialize() first.")
        
        // 构建 v3 payload（与 Gateway buildDeviceAuthPayloadV3 对齐）
        val payload = listOf(
            "v3",
            deviceId,
            clientId,
            clientMode,
            role,
            scopes.joinToString(","),
            signedAtMs.toString(),
            token,
            nonce,
            normalizeDeviceMetadata(platform),
            normalizeDeviceMetadata(deviceFamily)
        ).joinToString("|")
        
        val signature = keystoreManager.sign(payload)
        
        return SignedPayload(
            payload = payload,
            signature = signature,
            signedAtMs = signedAtMs,
            deviceId = deviceId,
            publicKeyBase64Url = getPublicKeyBase64Url()
        )
    }
    
    /**
     * 签名挑战（兼容旧接口，已不推荐）
     * 
     * 新代码应使用 signV3Payload()
     */
    @Deprecated("Use signV3Payload() for v3 protocol", ReplaceWith("signV3Payload(nonce)"))
    fun signChallenge(nonce: String): String {
        return keystoreManager.signChallenge(nonce)
    }
    
    /**
     * 准备配对请求
     * 
     * @return 配对请求 JSON（Protocol v3 格式）
     */
    suspend fun preparePairingRequest(
        nodeId: String,
        role: String = "operator",
        scopes: List<String> = listOf("operator.read", "operator.write")
    ): String = withContext(Dispatchers.IO) {
        val publicKey = keystoreManager.getPublicKeyBase64Url()
        val deviceId = getDeviceId()
            ?: throw IllegalStateException("Device not initialized")
        
        val payload = JSONObject().apply {
            put("device", JSONObject().apply {
                put("id", deviceId)
                put("publicKey", publicKey)
            })
            put("client", JSONObject().apply {
                put("id", "openclaw-android")
                put("version", "1.0.0")
                put("platform", "android")
            })
            put("nodeId", nodeId)
            put("role", role)
            put("scopes", JSONArray(scopes.toTypedArray()))
            put("token", "")
            put("signedAt", System.currentTimeMillis())
            put("deviceFamily", "phone")
        }
        
        payload.toString(2)
    }
    
    /**
     * 完成配对
     */
    fun completePairing(deviceToken: String) {
        encryptedStorage.saveDeviceToken(deviceToken)
        encryptedStorage.savePairingStatus(EncryptedStorage.PAIRING_STATUS_APPROVED)
        encryptedStorage.saveLastConnectedTimestamp(System.currentTimeMillis())
        Log.i(TAG, "Pairing completed successfully")
    }
    
    /**
     * 获取认证令牌（用于 WebSocket 连接）
     */
    fun getAuthToken(): String? {
        return encryptedStorage.getDeviceToken()
    }
    
    /**
     * 检查是否需要配对
     */
    fun needsPairing(): Boolean {
        return !encryptedStorage.isPaired()
    }
    
    /**
     * 保存 Gateway 配置
     */
    fun saveGatewayConfig(url: String, tlsFingerprint: String? = null) {
        encryptedStorage.saveGatewayUrl(url)
        tlsFingerprint?.let { encryptedStorage.saveTlsFingerprint(it) }
    }
    
    /**
     * 保存 Gateway auth token（用户输入的 token）
     */
    fun saveGatewayAuthToken(token: String) {
        encryptedStorage.saveString("gateway_auth_token", token)
    }
    
    /**
     * 获取 Gateway auth token（用户输入的 token）
     */
    fun getGatewayAuthToken(): String? {
        return encryptedStorage.getString("gateway_auth_token")
    }
    
    fun getGatewayUrl(): String? = encryptedStorage.getGatewayUrl()
    fun getTlsFingerprint(): String? = encryptedStorage.getTlsFingerprint()
    
    /**
     * 重置配对（保留密钥对）
     */
    fun resetPairing() {
        encryptedStorage.clearPairingData()
        Log.i(TAG, "Pairing reset")
    }
    
    /**
     * 完全重置（清除所有安全数据）
     */
    fun factoryReset() {
        encryptedStorage.clearAll()
        keystoreManager.deleteKey()
        Log.i(TAG, "Factory reset completed")
    }
    
    fun getDeviceDescription(): String {
        return "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} (Android ${android.os.Build.VERSION.RELEASE})"
    }
    fun getKeyInfo(): KeystoreManager.KeyInfo = keystoreManager.getKeyInfo()

    /**
     * 暴露内部 EncryptedStorage 实例
     *
     * 供 Hilt DI 模块使用，确保 GatewayRepository 等外部类
     * 注入的 EncryptedStorage 与 SecurityModule 内部是同一实例。
     */
    fun getEncryptedStorage(): EncryptedStorage = encryptedStorage
    
    // ==================== 内部方法 ====================
    
    /**
     * 标准化设备元数据（与 Gateway normalizeDeviceMetadataForAuth 对齐）
     * 
     * 规则：trim → NFKD → 去变音符 → 小写
     */
    private fun normalizeDeviceMetadata(value: String?): String {
        if (value.isNullOrBlank()) return ""
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return ""
        // NFKD 分解 → 去掉 combining marks → 小写
        val nfkd = Normalizer.normalize(trimmed, Normalizer.Form.NFKD)
        return nfkd.replace(Regex("\\p{M}"), "").lowercase()
    }
}

/**
 * 签名结果
 */
data class SignedPayload(
    val payload: String,
    val signature: String,
    val signedAtMs: Long,
    val deviceId: String,
    val publicKeyBase64Url: String
)

/**
 * 安全状态
 */
data class SecurityStatus(
    val isInitialized: Boolean,
    val isPaired: Boolean,
    val hasKeyPair: Boolean,
    val deviceId: String? = null,
    val hasDeviceToken: Boolean = false,
    val error: String? = null
) {
    fun isReady(): Boolean = isInitialized && hasKeyPair && isPaired
    
    fun getStatusText(): String = when {
        error != null -> "错误：$error"
        !isInitialized -> "初始化中..."
        !hasKeyPair -> "生成密钥中..."
        !isPaired -> "未配对"
        !hasDeviceToken -> "配对完成，等待连接"
        else -> "已就绪"
    }
}

/**
 * 安全日志工具（自动脱敏）
 */
object SecureLogger {
    private const val TAG = "ClawChat-Security"
    
    fun d(message: String) = android.util.Log.d(TAG, message.redactSensitive())
    fun i(message: String) = android.util.Log.i(TAG, message.redactSensitive())
    fun w(message: String) = android.util.Log.w(TAG, message.redactSensitive())
    fun e(message: String, throwable: Throwable? = null) {
        if (throwable != null) android.util.Log.e(TAG, message.redactSensitive(), throwable)
        else android.util.Log.e(TAG, message.redactSensitive())
    }
    
    private fun String.redactSensitive(): String = this
        .replace(Regex("token[\"']?\\s*[:=]\\s*[\"']?[^,}\"'\\s]+"), "token=***")
        .replace(Regex("key[\"']?\\s*[:=]\\s*[\"']?[^,}\"'\\s]+"), "key=***")
        .replace(Regex("signature[\"']?\\s*[:=]\\s*[\"']?[^,}\"'\\s]+"), "signature=***")
        .replace(Regex("secret[\"']?\\s*[:=]\\s*[\"']?[^,}\"'\\s]+"), "secret=***")
        .replace(Regex("password[\"']?\\s*[:=]\\s*[\"']?[^,}\"'\\s]+"), "password=***")
}
