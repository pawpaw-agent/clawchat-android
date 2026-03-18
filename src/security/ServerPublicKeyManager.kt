package com.openclaw.clawchat.security

import android.content.Context
import android.util.Base64
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.concurrent.ConcurrentHashMap

/**
 * ServerPublicKeyManager - 服务端公钥管理器
 * 
 * 负责：
 * - 存储和管理服务端公钥
 * - 支持公钥更新机制
 * - 公钥固定（防止中间人攻击）
 * - 安全存储（EncryptedSharedPreferences）
 * 
 * 安全特性：
 * - 公钥存储于加密 SharedPreferences
 * - 支持多个公钥（密钥轮换）
 * - 公钥指纹验证
 */
class ServerPublicKeyManager(context: Context) {
    
    companion object {
        private const val TAG = "ServerPublicKeyManager"
        
        private const val PREFS_NAME = "clawchat_server_keys"
        
        // 存储键名
        private const val KEY_PRIMARY_PUBLIC_KEY = "server_primary_public_key"
        private const val KEY_BACKUP_PUBLIC_KEY = "server_backup_public_key"
        private const val KEY_PRIMARY_FINGERPRINT = "server_primary_fingerprint"
        private const val KEY_LAST_UPDATED = "server_key_last_updated"
        
        // 公钥格式
        private const val KEY_ALGORITHM = "EC"
        private const val KEY_SPEC = "secp256r1"
    }
    
    private val prefs = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    
    // 内存缓存（避免频繁读取）
    private val publicKeyCache = ConcurrentHashMap<String, java.security.PublicKey?>()
    
    /**
     * 保存服务端主公钥
     * 
     * @param publicKeyPem PEM 格式的公钥
     * @return 公钥指纹
     */
    fun savePrimaryPublicKey(publicKeyPem: String): String {
        val publicKey = parsePublicKey(publicKeyPem)
        val fingerprint = generateFingerprint(publicKey)
        
        prefs.edit()
            .putString(KEY_PRIMARY_PUBLIC_KEY, publicKeyPem)
            .putString(KEY_PRIMARY_FINGERPRINT, fingerprint)
            .putLong(KEY_LAST_UPDATED, System.currentTimeMillis())
            .apply()
        
        // 清除缓存
        publicKeyCache.clear()
        
        Log.i(TAG, "Saved primary server public key, fingerprint: $fingerprint")
        return fingerprint
    }
    
    /**
     * 保存备份公钥（用于密钥轮换）
     */
    fun saveBackupPublicKey(publicKeyPem: String) {
        prefs.edit()
            .putString(KEY_BACKUP_PUBLIC_KEY, publicKeyPem)
            .apply()
        
        publicKeyCache.clear()
        Log.i(TAG, "Saved backup server public key")
    }
    
    /**
     * 获取主公钥
     * 
     * @return PEM 格式的公钥，如果不存在返回 null
     */
    fun getPrimaryPublicKey(): String? {
        return prefs.getString(KEY_PRIMARY_PUBLIC_KEY, null)
    }
    
    /**
     * 获取公钥对象（用于验证）
     * 
     * @return 解析后的 PublicKey 对象
     */
    fun getPrimaryPublicKeyObject(): java.security.PublicKey? {
        return publicKeyCache.getOrPut("primary") {
            getPrimaryPublicKey()?.let { parsePublicKey(it) }
        }
    }
    
    /**
     * 获取备份公钥
     */
    fun getBackupPublicKey(): String? {
        return prefs.getString(KEY_BACKUP_PUBLIC_KEY, null)
    }
    
    /**
     * 获取备份公钥对象
     */
    fun getBackupPublicKeyObject(): java.security.PublicKey? {
        return publicKeyCache.getOrPut("backup") {
            getBackupPublicKey()?.let { parsePublicKey(it) }
        }
    }
    
    /**
     * 获取公钥指纹
     */
    fun getPrimaryFingerprint(): String? {
        return prefs.getString(KEY_PRIMARY_FINGERPRINT, null)
    }
    
    /**
     * 获取最后更新时间
     */
    fun getLastUpdated(): Long {
        return prefs.getLong(KEY_LAST_UPDATED, 0L)
    }
    
    /**
     * 验证公钥指纹
     * 
     * 用于首次连接时验证公钥真实性（防止中间人攻击）
     * 
     * @param expectedFingerprint 预期的指纹
     * @return 是否匹配
     */
    fun verifyFingerprint(expectedFingerprint: String): Boolean {
        val storedFingerprint = getPrimaryFingerprint()
        return storedFingerprint != null && storedFingerprint.equals(expectedFingerprint, ignoreCase = true)
    }
    
    /**
     * 检查是否有有效的公钥
     */
    fun hasValidPublicKey(): Boolean {
        return getPrimaryPublicKeyObject() != null
    }
    
    /**
     * 清除所有公钥（用于重置）
     */
    fun clearAllKeys() {
        prefs.edit().clear().apply()
        publicKeyCache.clear()
        Log.i(TAG, "Cleared all server public keys")
    }
    
    // ==================== 内部方法 ====================
    
    /**
     * 解析 PEM 格式的公钥
     */
    private fun parsePublicKey(pem: String): java.security.PublicKey {
        val cleaned = pem
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replace("\\s".toRegex(), "")
        
        val keyBytes = Base64.decode(cleaned, Base64.NO_WRAP)
        val keySpec = X509EncodedKeySpec(keyBytes)
        val keyFactory = KeyFactory.getInstance(KEY_ALGORITHM)
        
        return keyFactory.generatePublic(keySpec)
    }
    
    /**
     * 生成公钥指纹（SHA256）
     */
    private fun generateFingerprint(publicKey: java.security.PublicKey): String {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        val digest = md.digest(publicKey.encoded)
        return digest.joinToString(":") { "%02x".format(it) }
    }
}

/**
 * NonceCache - Nonce 缓存（防重放攻击）
 * 
 * 缓存已使用的 Nonce，防止重放攻击。
 * 自动清理过期的 Nonce（默认 5 分钟）。
 */
class NonceCache {
    
    companion object {
        private const val TAG = "NonceCache"
        
        // Nonce 有效期（毫秒）
        private const val NONCE_TTL_MS = 5 * 60 * 1000L // 5 分钟
        
        // 清理间隔
        private const val CLEANUP_INTERVAL_MS = 60 * 1000L // 1 分钟
    }
    
    // 存储 Nonce 和对应的时间戳
    private val cache = ConcurrentHashMap<String, Long>()
    
    // 最后清理时间
    @Volatile
    private var lastCleanupTime = System.currentTimeMillis()
    
    /**
     * 检查并添加 Nonce
     * 
     * @param nonce 要检查的 Nonce
     * @param timestamp 消息时间戳
     * @return 如果 Nonce 有效（未重复）返回 true，否则返回 false
     */
    fun checkAndAdd(nonce: String, timestamp: Long): Boolean {
        // 定期清理过期 Nonce
        maybeCleanup()
        
        // 检查是否已存在
        if (cache.containsKey(nonce)) {
            Log.w(TAG, "Duplicate nonce detected: $nonce")
            return false
        }
        
        // 检查时间戳是否在有效期内
        val now = System.currentTimeMillis()
        val timeDiff = Math.abs(now - timestamp)
        if (timeDiff > NONCE_TTL_MS) {
            Log.w(TAG, "Nonce timestamp out of valid window: ${timeDiff}ms")
            return false
        }
        
        // 添加到缓存
        cache[nonce] = now
        return true
    }
    
    /**
     * 检查 Nonce 是否有效（不添加到缓存）
     * 
     * 用于预检查
     */
    fun isValid(nonce: String, timestamp: Long): Boolean {
        if (cache.containsKey(nonce)) {
            return false
        }
        
        val now = System.currentTimeMillis()
        val timeDiff = Math.abs(now - timestamp)
        return timeDiff <= NONCE_TTL_MS
    }
    
    /**
     * 清除所有缓存
     */
    fun clear() {
        cache.clear()
        Log.i(TAG, "Nonce cache cleared")
    }
    
    /**
     * 获取缓存大小（用于调试）
     */
    fun size(): Int {
        maybeCleanup()
        return cache.size
    }
    
    /**
     * 清理过期 Nonce
     */
    private fun maybeCleanup() {
        val now = System.currentTimeMillis()
        if (now - lastCleanupTime < CLEANUP_INTERVAL_MS) {
            return
        }
        
        synchronized(this) {
            val toRemove = cache.filterValues { now - it > NONCE_TTL_MS }.keys
            cache.keys.removeAll(toRemove)
            lastCleanupTime = now
            
            if (toRemove.isNotEmpty()) {
                Log.d(TAG, "Cleaned up ${toRemove.size} expired nonces")
            }
        }
    }
}

/**
 * 使用示例：
 * 
 * // 1. 初始化公钥管理器
 * val publicKeyManager = ServerPublicKeyManager(context)
 * 
 * // 2. 保存服务端公钥（首次配对时）
 * val fingerprint = publicKeyManager.savePrimaryPublicKey(serverPublicKeyPem)
 * 
 * // 3. 验证公钥指纹（可选，增强安全性）
 * if (!publicKeyManager.verifyFingerprint(expectedFingerprint)) {
 *     throw SecurityException("Public key fingerprint mismatch!")
 * }
 * 
 * // 4. 使用 Nonce 缓存
 * val nonceCache = NonceCache()
 * if (!nonceCache.checkAndAdd(nonce, timestamp)) {
 *     throw SecurityException("Invalid or duplicate nonce!")
 * }
 */
