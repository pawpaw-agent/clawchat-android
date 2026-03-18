package com.openclaw.clawchat.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * EncryptedStorage - 加密 SharedPreferences 封装
 * 
 * 负责：
 * - 安全存储设备令牌（Device Token）
 * - 存储 Gateway 配置（可选加密）
 * - 存储用户偏好设置
 * - AES256-GCM 加密保护
 * 
 * 安全特性：
 * - MasterKey 存储于 Android Keystore
 * - 密钥加密：AES256-SIV
 * - 值加密：AES256-GCM
 * - 自动处理密钥轮换
 */
class EncryptedStorage(context: Context) {
    
    private val sharedPreferences: SharedPreferences
    
    // 密钥常量
    companion object {
        private const val PREFS_NAME = "clawchat_secure_prefs"
        
        // 存储键名
        const val KEY_DEVICE_TOKEN = "device_token"
        const val KEY_DEVICE_ID = "device_id"
        const val KEY_GATEWAY_URL = "gateway_url"
        const val KEY_GATEWAY_TLS_FINGERPRINT = "gateway_tls_fingerprint"
        const val KEY_PAIRING_STATUS = "pairing_status"
        const val KEY_LAST_CONNECTED = "last_connected_timestamp"
        
        // 配对状态枚举
        const val PAIRING_STATUS_NONE = "none"
        const val PAIRING_STATUS_PENDING = "pending"
        const val PAIRING_STATUS_APPROVED = "approved"
        const val PAIRING_STATUS_REJECTED = "rejected"
    }
    
    init {
        sharedPreferences = createEncryptedPrefs(context)
    }
    
    /**
     * 创建 EncryptedSharedPreferences 实例
     * 
     * 使用 MasterKey 存储在 Android Keystore 中，
     * 确保即使设备被 Root，加密数据也无法被提取。
     */
    private fun createEncryptedPrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        
        return EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
    
    // ==================== 设备令牌管理 ====================
    
    /**
     * 存储设备令牌
     * 
     * @param token 从 Gateway 获取的设备令牌
     */
    fun saveDeviceToken(token: String) {
        sharedPreferences.edit()
            .putString(KEY_DEVICE_TOKEN, token)
            .apply()
    }
    
    /**
     * 获取设备令牌
     * 
     * @return 设备令牌，如果不存在返回 null
     */
    fun getDeviceToken(): String? {
        return sharedPreferences.getString(KEY_DEVICE_TOKEN, null)
    }
    
    /**
     * 检查是否有有效的设备令牌
     */
    fun hasDeviceToken(): Boolean {
        return !getDeviceToken().isNullOrEmpty()
    }
    
    /**
     * 删除设备令牌（用于撤销/重新配对）
     */
    fun clearDeviceToken() {
        sharedPreferences.edit()
            .remove(KEY_DEVICE_TOKEN)
            .apply()
    }
    
    // ==================== 设备 ID 管理 ====================
    
    /**
     * 存储设备指纹 ID
     */
    fun saveDeviceId(deviceId: String) {
        sharedPreferences.edit()
            .putString(KEY_DEVICE_ID, deviceId)
            .apply()
    }
    
    /**
     * 获取设备指纹 ID
     */
    fun getDeviceId(): String? {
        return sharedPreferences.getString(KEY_DEVICE_ID, null)
    }
    
    // ==================== Gateway 配置 ====================
    
    /**
     * 存储 Gateway URL
     */
    fun saveGatewayUrl(url: String) {
        sharedPreferences.edit()
            .putString(KEY_GATEWAY_URL, url)
            .apply()
    }
    
    /**
     * 获取 Gateway URL
     */
    fun getGatewayUrl(): String? {
        return sharedPreferences.getString(KEY_GATEWAY_URL, null)
    }
    
    /**
     * 存储 TLS 证书指纹（用于证书固定）
     */
    fun saveTlsFingerprint(fingerprint: String) {
        sharedPreferences.edit()
            .putString(KEY_GATEWAY_TLS_FINGERPRINT, fingerprint)
            .apply()
    }
    
    /**
     * 获取 TLS 证书指纹
     */
    fun getTlsFingerprint(): String? {
        return sharedPreferences.getString(KEY_GATEWAY_TLS_FINGERPRINT, null)
    }
    
    // ==================== 配对状态管理 ====================
    
    /**
     * 保存配对状态
     */
    fun savePairingStatus(status: String) {
        sharedPreferences.edit()
            .putString(KEY_PAIRING_STATUS, status)
            .apply()
    }
    
    /**
     * 获取配对状态
     */
    fun getPairingStatus(): String {
        return sharedPreferences.getString(KEY_PAIRING_STATUS, PAIRING_STATUS_NONE) 
            ?: PAIRING_STATUS_NONE
    }
    
    /**
     * 检查是否已完成配对
     */
    fun isPaired(): Boolean {
        return getPairingStatus() == PAIRING_STATUS_APPROVED && hasDeviceToken()
    }
    
    // ==================== 连接时间戳 ====================
    
    /**
     * 保存最后连接时间
     */
    fun saveLastConnectedTimestamp(timestamp: Long) {
        sharedPreferences.edit()
            .putLong(KEY_LAST_CONNECTED, timestamp)
            .apply()
    }
    
    /**
     * 获取最后连接时间
     */
    fun getLastConnectedTimestamp(): Long {
        return sharedPreferences.getLong(KEY_LAST_CONNECTED, 0L)
    }
    
    // ==================== 高级加密操作 ====================
    
    /**
     * 加密并存储自定义数据
     * 
     * 用于存储额外的敏感配置
     */
    fun encryptAndStore(key: String, plaintext: String) {
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
            
            val iv = cipher.iv
            val ciphertext = cipher.doFinal(plaintext.toByteArray(StandardCharsets.UTF_8))
            
            // 存储 IV + 密文（Base64 编码）
            val combined = iv + ciphertext
            val encoded = Base64.encodeToString(combined, Base64.NO_WRAP)
            
            sharedPreferences.edit().putString("encrypted_$key", encoded).apply()
        } catch (e: GeneralSecurityException) {
            throw SecurityException("Failed to encrypt data", e)
        }
    }
    
    /**
     * 解密并读取自定义数据
     */
    fun decryptAndRead(key: String): String? {
        try {
            val encoded = sharedPreferences.getString("encrypted_$key", null) ?: return null
            val combined = Base64.decode(encoded, Base64.NO_WRAP)
            
            val iv = combined.copyOfRange(0, 12) // GCM IV 长度
            val ciphertext = combined.copyOfRange(12, combined.size)
            
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), spec)
            
            val plaintext = cipher.doFinal(ciphertext)
            return String(plaintext, StandardCharsets.UTF_8)
        } catch (e: GeneralSecurityException) {
            return null // 解密失败（可能是密钥已变更）
        }
    }
    
    /**
     * 获取或创建加密密钥（用于高级加密操作）
     */
    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = java.security.KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        
        val entry = keyStore.getEntry("clawchat_master_key", null)
        if (entry != null) {
            return (entry as KeyStore.SecretKeyEntry).secretKey
        }
        
        // 创建新密钥
        val keyGenerator = KeyGenerator.getInstance("AES", "AndroidKeyStore")
        keyGenerator.init(
            KeyGenParameterSpec.Builder("clawchat_master_key", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        
        return keyGenerator.generateKey()
    }
    
    // ==================== 清除操作 ====================
    
    /**
     * 清除所有数据（用于注销/重置）
     */
    fun clearAll() {
        sharedPreferences.edit().clear().apply()
    }
    
    /**
     * 清除配对相关数据（保留配置）
     */
    fun clearPairingData() {
        sharedPreferences.edit()
            .remove(KEY_DEVICE_TOKEN)
            .remove(KEY_DEVICE_ID)
            .remove(KEY_PAIRING_STATUS)
            .remove(KEY_LAST_CONNECTED)
            .apply()
    }
}

/**
 * 使用示例：
 * 
 * // 1. 初始化
 * val storage = EncryptedStorage(context)
 * 
 * // 2. 配对后存储令牌
 * storage.saveDeviceToken(deviceToken)
 * storage.saveDeviceId(deviceFingerprint)
 * storage.savePairingStatus(EncryptedStorage.PAIRING_STATUS_APPROVED)
 * 
 * // 3. 连接时读取
 * val token = storage.getDeviceToken() ?: return // 需要重新配对
 * val gatewayUrl = storage.getGatewayUrl() ?: "ws://192.168.1.1:18789"
 * 
 * // 4. 检查配对状态
 * if (!storage.isPaired()) {
 *     // 启动配对流程
 * }
 * 
 * // 5. 高级加密存储
 * storage.encryptAndStore("api_key", "secret_key_123")
 * val apiKey = storage.decryptAndRead("api_key")
 */
