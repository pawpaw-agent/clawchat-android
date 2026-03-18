package com.openclaw.clawchat.security

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * SecurityModule - 安全模块统一入口
 * 
 * 整合 KeystoreManager、EncryptedStorage、DeviceFingerprint，
 * 提供完整的设备配对和安全存储功能。
 * 
 * 使用 Hilt 单例注入，整个应用生命周期只创建一次。
 */
class SecurityModule(private val context: Context) {
    
    companion object {
        private const val TAG = "SecurityModule"
        private const val KEYPAIR_ALIAS = "clawchat_device_key"
    }
    
    // 核心组件
    private val keystoreManager = KeystoreManager(KEYPAIR_ALIAS)
    private val encryptedStorage = EncryptedStorage(context)
    private val deviceFingerprint = DeviceFingerprint(context)
    
    // ==================== 公开 API ====================
    
    /**
     * 初始化安全模块
     * 
     * 检查是否需要生成密钥对。
     * 应在 Application onCreate 中调用。
     */
    suspend fun initialize(): SecurityStatus = withContext(Dispatchers.IO) {
        try {
            // 检查密钥对
            if (!keystoreManager.hasKeyPair()) {
                Log.i(TAG, "Generating new device key pair...")
                keystoreManager.generateKeyPair()
            }
            
            // 检查设备 ID
            if (encryptedStorage.getDeviceId().isNullOrEmpty()) {
                Log.i(TAG, "Generating new device fingerprint...")
                val deviceId = deviceFingerprint.generateDeviceId()
                encryptedStorage.saveDeviceId(deviceId)
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
     * 准备配对请求
     * 
     * 生成配对所需的公钥和设备信息。
     * 
     * @return 配对请求 JSON（Protocol v3 格式）
     */
    suspend fun preparePairingRequest(
        nodeId: String,
        role: String = "operator",
        scopes: List<String> = listOf("operator.read", "operator.write")
    ): String = withContext(Dispatchers.IO) {
        val publicKey = keystoreManager.getPublicKeyPem()
        val deviceId = encryptedStorage.getDeviceId() 
            ?: deviceFingerprint.generateDeviceId().also { encryptedStorage.saveDeviceId(it) }
        val platformInfo = deviceFingerprint.getPlatformInfo()
        
        // 构建 Protocol v3 格式的签名载荷
        val payload = JSONObject().apply {
            put("device", JSONObject().apply {
                put("id", deviceId)
                put("publicKey", publicKey)
            })
            put("client", JSONObject().apply {
                put("id", "openclaw-android")
                put("version", platformInfo.appVersion)
                put("platform", platformInfo.platform)
            })
            put("nodeId", nodeId)
            put("role", role)
            put("scopes", JSONArray(scopes.toTypedArray()))
            put("token", "") // 空表示首次配对
            put("signedAt", System.currentTimeMillis())
            put("deviceFamily", "phone")
        }
        
        payload.toString(2)
    }
    
    /**
     * 签名挑战
     * 
     * 用于配对流程中的挑战 - 响应认证。
     * 
     * @param nonce Server 提供的挑战 nonce
     * @return Base64 编码的签名
     */
    fun signChallenge(nonce: String): String {
        return keystoreManager.signChallenge(nonce)
    }
    
    /**
     * 完成配对
     * 
     * 配对成功后调用，存储设备令牌。
     * 
     * @param deviceToken Gateway 颁发的设备令牌
     */
    fun completePairing(deviceToken: String) {
        encryptedStorage.saveDeviceToken(deviceToken)
        encryptedStorage.savePairingStatus(EncryptedStorage.PAIRING_STATUS_APPROVED)
        encryptedStorage.saveLastConnectedTimestamp(System.currentTimeMillis())
        
        Log.i(TAG, "Pairing completed successfully")
    }
    
    /**
     * 获取认证令牌
     * 
     * 用于 WebSocket 连接认证。
     * 
     * @return 设备令牌，如果未配对返回 null
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
     * 获取 Gateway URL
     */
    fun getGatewayUrl(): String? {
        return encryptedStorage.getGatewayUrl()
    }
    
    /**
     * 获取 TLS 指纹
     */
    fun getTlsFingerprint(): String? {
        return encryptedStorage.getTlsFingerprint()
    }
    
    /**
     * 重置配对（用于撤销/重新配对）
     * 
     * 清除设备令牌和配对状态，但保留密钥对。
     */
    fun resetPairing() {
        encryptedStorage.clearPairingData()
        Log.i(TAG, "Pairing reset")
    }
    
    /**
     * 完全重置（恢复出厂设置）
     * 
     * 清除所有安全数据，包括密钥对。
     * 下次启动时会重新生成所有凭证。
     */
    fun factoryReset() {
        encryptedStorage.clearAll()
        keystoreManager.deleteKey()
        Log.i(TAG, "Factory reset completed")
    }
    
    /**
     * 获取设备描述（用于显示给用户）
     */
    fun getDeviceDescription(): String {
        return deviceFingerprint.getDeviceDescription()
    }
    
    /**
     * 获取密钥信息（用于诊断）
     */
    fun getKeyInfo(): KeystoreManager.KeyInfo {
        return keystoreManager.getKeyInfo()
    }
}

/**
 * 安全状态数据类
 */
data class SecurityStatus(
    val isInitialized: Boolean,
    val isPaired: Boolean,
    val hasKeyPair: Boolean,
    val deviceId: String? = null,
    val hasDeviceToken: Boolean = false,
    val error: String? = null
) {
    /**
     * 是否完全就绪（可以连接 Gateway）
     */
    fun isReady(): Boolean {
        return isInitialized && hasKeyPair && isPaired
    }
    
    /**
     * 获取状态描述
     */
    fun getStatusText(): String {
        return when {
            error != null -> "错误：$error"
            !isInitialized -> "初始化中..."
            !hasKeyPair -> "生成密钥中..."
            !isPaired -> "未配对"
            !hasDeviceToken -> "配对完成，等待连接"
            else -> "已就绪"
        }
    }
}

/**
 * 安全日志工具
 * 
 * 自动脱敏敏感信息
 */
object SecureLogger {
    private const val TAG = "ClawChat-Security"
    
    fun d(message: String) {
        android.util.Log.d(TAG, message.redactSensitive())
    }
    
    fun i(message: String) {
        android.util.Log.i(TAG, message.redactSensitive())
    }
    
    fun w(message: String) {
        android.util.Log.w(TAG, message.redactSensitive())
    }
    
    fun e(message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            android.util.Log.e(TAG, message.redactSensitive(), throwable)
        } else {
            android.util.Log.e(TAG, message.redactSensitive())
        }
    }
    
    /**
     * 脱敏敏感信息
     */
    private fun String.redactSensitive(): String {
        return this
            .replace(Regex("token[\"']?\\s*[:=]\\s*[\"']?[^,}\"'\\s]+"), "token=***")
            .replace(Regex("key[\"']?\\s*[:=]\\s*[\"']?[^,}\"'\\s]+"), "key=***")
            .replace(Regex("signature[\"']?\\s*[:=]\\s*[\"']?[^,}\"'\\s]+"), "signature=***")
            .replace(Regex("secret[\"']?\\s*[:=]\\s*[\"']?[^,}\"'\\s]+"), "secret=***")
            .replace(Regex("password[\"']?\\s*[:=]\\s*[\"']?[^,}\"'\\s]+"), "password=***")
    }
}

/**
 * 使用示例（Hilt 模块）：
 * 
 * @Module
 * @Singleton
 * object SecurityModule {
 * 
 *     @Provides
 *     @Singleton
 *     fun provideSecurityModule(@ApplicationContext context: Context): SecurityModule {
 *         return SecurityModule(context)
 *     }
 * }
 * 
 * // ViewModel 中使用：
 * @HiltViewModel
 * class MainViewModel @Inject constructor(
 *     private val securityModule: SecurityModule
 * ) : ViewModel() {
 * 
 *     init {
 *         viewModelScope.launch {
 *             val status = securityModule.initialize()
 *             if (status.needsPairing()) {
 *                 // 导航到配对页面
 *             }
 *         }
 *     }
 * }
 */
