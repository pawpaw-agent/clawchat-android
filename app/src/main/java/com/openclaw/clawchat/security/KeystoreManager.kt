package com.openclaw.clawchat.security

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.Signature
import java.util.Date

/**
 * KeystoreManager - Android Ed25519 密钥管理
 *
 * Gateway 协议 v3 要求 Ed25519 密钥对：
 * - 签名：Ed25519 纯签名（等效 Node.js crypto.sign(null, payload, key)）
 * - 公钥：raw 32 bytes base64url（去掉 SPKI 前缀）
 * - deviceId：sha256(raw 32 bytes public key).hex()
 *
 * 实现策略：
 * - API 33+：Android Keystore 硬件级 Ed25519
 * - API 26-32：BouncyCastle Ed25519 + EncryptedSharedPreferences 存储
 */
class KeystoreManager(
    private val alias: String = "clawchat_device_key",
    private val softwareKeyStore: SoftwareKeyStore? = null
) {
    companion object {
        /**
         * Ed25519 SPKI DER 前缀 (12 bytes)
         * SEQUENCE { SEQUENCE { OID 1.3.101.112 } BIT STRING ... }
         * raw public key = 后 32 bytes
         */
        val ED25519_SPKI_PREFIX: ByteArray =
            byteArrayOf(0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00)

        /** API 33+ 支持 Keystore Ed25519 */
        private val USE_KEYSTORE_ED25519 = Build.VERSION.SDK_INT >= 33

        /** 是否已回退到 BouncyCastle（某些设备 Keystore 不支持 Ed25519） */
        private var fallbackToBouncyCastle = false

        /** BouncyCastle raw key 长度 */
        private const val ED25519_KEY_SIZE = 32

        /**
         * 检查是否应该使用软件密钥存储
         * 
         * 在实例创建时调用，从持久化存储读取 fallback 状态。
         * 这解决了进程重启后静态变量丢失的问题。
         */
        fun shouldUseSoftwareKeystore(softwareKeyStore: SoftwareKeyStore?): Boolean {
            // API < 33 始终使用软件密钥存储
            if (!USE_KEYSTORE_ED25519) return true
            
            // 检查持久化的 fallback 状态
            if (softwareKeyStore is EncryptedStorage) {
                return softwareKeyStore.getFallbackToSoftwareKeystore()
            }
            
            return false
        }
    }

    init {
        // 从持久化存储恢复 fallback 状态
        if (softwareKeyStore is EncryptedStorage) {
            fallbackToBouncyCastle = softwareKeyStore.getFallbackToSoftwareKeystore()
        }
    }

    private val keyStore: KeyStore? = if (USE_KEYSTORE_ED25519 && !fallbackToBouncyCastle) {
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    } else null

    // BouncyCastle 密钥缓存（仅 API < 33）
    private var cachedBcPublicKey: Ed25519PublicKeyParameters? = null
    private var cachedBcPrivateKey: Ed25519PrivateKeyParameters? = null

    /**
     * 保存 fallback 状态到持久化存储
     */
    private fun saveFallbackState(fallback: Boolean) {
        (softwareKeyStore as? EncryptedStorage)?.saveFallbackToSoftwareKeystore(fallback)
    }

    // ==================== 公开 API ====================

    /**
     * 检查密钥对是否已存在
     * 
     * 同时检查 Keystore 和软件存储，如果任一存在则返回 true。
     * 这解决了从旧版本升级时 fallback 状态丢失的问题。
     */
    fun hasKeyPair(): Boolean {
        // 如果已知使用软件密钥存储，只检查软件存储
        if (fallbackToBouncyCastle) {
            return softwareKeyStore?.hasKeyPair(alias) ?: false
        }
        
        // 否则同时检查两种存储
        val keystoreHasKey = if (USE_KEYSTORE_ED25519) {
            keyStore?.containsAlias(alias) ?: false
        } else false
        
        val softwareHasKey = softwareKeyStore?.hasKeyPair(alias) ?: false
        
        // 如果软件存储有密钥但 Keystore 没有，说明需要回退
        if (softwareHasKey && !keystoreHasKey && USE_KEYSTORE_ED25519) {
            fallbackToBouncyCastle = true
            saveFallbackState(true)
        }
        
        return keystoreHasKey || softwareHasKey
    }

    /**
     * 生成新的 Ed25519 密钥对
     *
     * @return 公钥 raw 32 bytes 的 base64url 编码
     */
    fun generateKeyPair(): String {
        if (hasKeyPair()) {
            deleteKey()
        }
        clearBcCache()

        if (USE_KEYSTORE_ED25519 && !fallbackToBouncyCastle) {
            try {
                generateKeystoreEd25519()
                fallbackToBouncyCastle = false
                saveFallbackState(false)
            } catch (e: Exception) {
                // 某些设备（如 Samsung）的 Keystore 不支持 Ed25519，回退到 BouncyCastle
                android.util.Log.w("KeystoreManager", "Keystore Ed25519 not supported, falling back to BouncyCastle: ${e.message}")
                clearBcCache()
                generateBouncyCastleEd25519()
                fallbackToBouncyCastle = true
                saveFallbackState(true)
            }
        } else {
            generateBouncyCastleEd25519()
            fallbackToBouncyCastle = true
            saveFallbackState(true)
        }

        return getPublicKeyBase64Url()
    }

    /**
     * 获取公钥 raw bytes（32 bytes）
     */
    fun getPublicKeyRaw(): ByteArray {
        return if (USE_KEYSTORE_ED25519 && !fallbackToBouncyCastle) {
            val spki = getPublicKeySPKI()
            extractRawFromSPKI(spki)
        } else {
            loadBcPublicKey().encoded
        }
    }

    /**
     * 获取公钥 raw 32 bytes 的 base64url 编码
     *
     * 这是 Gateway connect 协议要求的传输格式
     */
    fun getPublicKeyBase64Url(): String {
        return base64UrlEncode(getPublicKeyRaw())
    }

    /**
     * 计算 deviceId: sha256(raw public key).hex()
     *
     * 与 Gateway 端 fingerprintPublicKey() 一致
     */
    fun deriveDeviceId(): String {
        val raw = getPublicKeyRaw()
        val digest = MessageDigest.getInstance("SHA-256").digest(raw)
        return digest.joinToString("") { "%02x".format(it) }
    }

    // ==================== 签名操作 ====================

    /**
     * Ed25519 签名（ByteArray 输入）
     *
     * 等效 Node.js: crypto.sign(null, Buffer.from(data), privateKey)
     *
     * @param data 待签名数据
     * @return 签名 bytes（64 bytes）
     */
    fun sign(data: ByteArray): ByteArray {
        return if (USE_KEYSTORE_ED25519 && !fallbackToBouncyCastle) {
            signWithKeystore(data)
        } else {
            signWithBouncyCastle(data)
        }
    }

    /**
     * Ed25519 签名（String 输入）
     *
     * @param data 待签名字符串（UTF-8）
     * @return 签名的 base64url 编码（无填充）
     */
    fun sign(data: String): String {
        return base64UrlEncode(sign(data.toByteArray(Charsets.UTF_8)))
    }

    /**
     * 对挑战进行签名（兼容旧接口）
     */
    fun signChallenge(challenge: ByteArray): ByteArray = sign(challenge)
    fun signChallenge(challenge: String): String = sign(challenge)

    // ==================== 密钥管理 ====================

    /**
     * 删除密钥对
     */
    fun deleteKey() {
        if (USE_KEYSTORE_ED25519 && !fallbackToBouncyCastle) {
            if (keyStore!!.containsAlias(alias)) {
                keyStore.deleteEntry(alias)
            }
        } else {
            softwareKeyStore?.deleteKeyPair(alias)
        }
        clearBcCache()
    }

    /**
     * 获取密钥信息（诊断用）
     */
    fun getKeyInfo(): KeyInfo {
        if (!hasKeyPair()) return KeyInfo(null, null, false)

        return if (USE_KEYSTORE_ED25519 && !fallbackToBouncyCastle) {
            KeyInfo(
                algorithm = "Ed25519",
                format = "AndroidKeyStore (TEE/StrongBox)",
                isInsideSecureHardware = true
            )
        } else {
            KeyInfo(
                algorithm = "Ed25519 (BouncyCastle)",
                format = "EncryptedSharedPreferences (AES-256-GCM)",
                isInsideSecureHardware = false
            )
        }
    }

    data class KeyInfo(
        val algorithm: String?,
        val format: String?,
        val isInsideSecureHardware: Boolean,
        val certificateNotBefore: Date? = null,
        val certificateNotAfter: Date? = null
    )

    // ==================== Keystore 路径（API 33+） ====================

    /**
     * API 33+: Android Keystore 生成 Ed25519 密钥对
     */
    private fun generateKeystoreEd25519() {
        val keyPairGenerator = KeyPairGenerator.getInstance(
            "Ed25519",
            "AndroidKeyStore"
        )
        val spec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        )
            .setUserAuthenticationRequired(false)
            .build()

        keyPairGenerator.initialize(spec)
        keyPairGenerator.generateKeyPair()
    }

    /**
     * API 33+: Keystore 签名
     */
    private fun signWithKeystore(data: ByteArray): ByteArray {
        val privateKey = getKeystorePrivateKey()
        val signature = Signature.getInstance("Ed25519")
        signature.initSign(privateKey)
        signature.update(data)
        return signature.sign()
    }

    /**
     * API 33+: 获取 Keystore 私钥
     */
    private fun getKeystorePrivateKey(): PrivateKey {
        val entry = keyStore!!.getEntry(alias, null) as? KeyStore.PrivateKeyEntry
            ?: throw IllegalStateException("Key pair not found in Keystore. Call generateKeyPair() first.")
        return entry.privateKey
    }

    /**
     * API 33+: 获取公钥 SPKI DER 编码
     */
    private fun getPublicKeySPKI(): ByteArray {
        val entry = keyStore!!.getEntry(alias, null) as? KeyStore.PrivateKeyEntry
            ?: throw IllegalStateException("Key pair not found in Keystore. Call generateKeyPair() first.")
        return entry.certificate.publicKey.encoded
    }

    // ==================== BouncyCastle 路径（API 26-32） ====================

    /**
     * API 26-32: BouncyCastle 生成 Ed25519 密钥对
     *
     * 密钥以 raw bytes 形式存储在 EncryptedSharedPreferences 中：
     * - publicKey: 32 bytes → SPKI DER 编码存储（与 Keystore 路径格式一致）
     * - privateKey: 32 bytes → 原始 seed 存储
     */
    private fun generateBouncyCastleEd25519() {
        requireNotNull(softwareKeyStore) {
            "SoftwareKeyStore is required for API < 33 (Ed25519 BouncyCastle fallback)"
        }

        val generator = Ed25519KeyPairGenerator()
        generator.init(Ed25519KeyGenerationParameters(SecureRandom()))
        val keyPair = generator.generateKeyPair()

        val publicKey = keyPair.public as Ed25519PublicKeyParameters
        val privateKey = keyPair.private as Ed25519PrivateKeyParameters

        // 构造 SPKI DER（兼容 Keystore 路径的存储格式）
        val spki = ED25519_SPKI_PREFIX + publicKey.encoded

        // 存储 raw 私钥 seed（32 bytes）和 SPKI 公钥
        softwareKeyStore.saveKeyPair(
            alias = alias,
            publicKeyEncoded = spki,
            privateKeyEncoded = privateKey.encoded  // raw 32 bytes seed
        )

        // 仅缓存公钥（非敏感），私钥不驻留内存
        cachedBcPublicKey = publicKey
    }

    /**
     * API 26-32: BouncyCastle 签名
     *
     * 私钥每次从存储加载，签名完成后立即清除内存引用，
     * 防止 root 设备通过内存转储提取私钥。
     * 性能影响可忽略（AES-GCM 解密 32 bytes < 1ms）。
     */
    private fun signWithBouncyCastle(data: ByteArray): ByteArray {
        val privateKey = loadBcPrivateKey()
        return try {
            val signer = Ed25519Signer()
            signer.init(true, privateKey)
            signer.update(data, 0, data.size)
            signer.generateSignature()
        } finally {
            // 签名完成后立即清除私钥内存引用
            clearBcPrivateKeyCache()
        }
    }

    /**
     * 从存储加载 BouncyCastle 公钥（可缓存，非敏感）
     */
    private fun loadBcPublicKey(): Ed25519PublicKeyParameters {
        cachedBcPublicKey?.let { return it }

        requireNotNull(softwareKeyStore) { "SoftwareKeyStore required" }

        val publicSpki = softwareKeyStore.getPublicKeyEncoded(alias)
            ?: throw IllegalStateException("Key pair not found. Call generateKeyPair() first.")
        val publicRaw = extractRawFromSPKI(publicSpki)
        val publicKey = Ed25519PublicKeyParameters(publicRaw, 0)
        cachedBcPublicKey = publicKey
        return publicKey
    }

    /**
     * 从存储加载 BouncyCastle 私钥（不缓存，用完即弃）
     */
    private fun loadBcPrivateKey(): Ed25519PrivateKeyParameters {
        requireNotNull(softwareKeyStore) { "SoftwareKeyStore required" }

        val privateRaw = softwareKeyStore.getPrivateKeyEncoded(alias)
            ?: throw IllegalStateException("Key pair not found. Call generateKeyPair() first.")
        return Ed25519PrivateKeyParameters(privateRaw, 0)
    }

    /**
     * 清除私钥内存缓存
     */
    private fun clearBcPrivateKeyCache() {
        cachedBcPrivateKey = null
    }

    /**
     * 清除所有 BouncyCastle 密钥缓存（公钥 + 私钥）
     */
    private fun clearBcCache() {
        cachedBcPrivateKey = null
        cachedBcPublicKey = null
    }

    // ==================== 工具方法 ====================

    /**
     * 从 SPKI DER 中提取 raw 32 bytes 公钥
     * 
     * 支持多种格式：
     * - 标准格式 (44 bytes): 12 bytes 前缀 + 32 bytes raw key
     * - 完整 DER 格式 (44+ bytes): 需要解析 DER 结构
     * - Raw 格式 (32 bytes): 直接返回
     */
    private fun extractRawFromSPKI(spki: ByteArray): ByteArray {
        // 如果已经是 raw 32 bytes，直接返回
        if (spki.size == ED25519_KEY_SIZE) return spki
        
        // 标准格式 (44 bytes): 12 bytes 前缀 + 32 bytes raw key
        if (spki.size == ED25519_SPKI_PREFIX.size + ED25519_KEY_SIZE) {
            val prefix = spki.sliceArray(0 until ED25519_SPKI_PREFIX.size)
            if (prefix.contentEquals(ED25519_SPKI_PREFIX)) {
                return spki.sliceArray(ED25519_SPKI_PREFIX.size until spki.size)
            }
        }
        
        // 完整 DER 格式：尝试解析
        // Ed25519 OID: 1.3.101.112 = 06 03 2b 65 70
        val ed25519Oid = byteArrayOf(0x06, 0x03, 0x2b, 0x65, 0x70)
        
        // 在 DER 中查找 Ed25519 OID
        for (i in 0 until spki.size - ed25519Oid.size) {
            if (spki.sliceArray(i until i + ed25519Oid.size).contentEquals(ed25519Oid)) {
                // 找到 OID，后面的结构包含公钥
                // BIT STRING 通常在 OID 后面，格式: 03 xx 00 [raw key]
                // 查找 BIT STRING tag (0x03)
                for (j in i + ed25519Oid.size until minOf(spki.size - 3, i + 50)) {
                    if (spki[j] == 0x03.toByte()) {
                        // BIT STRING: tag (0x03) + length + unused bits (0x00) + data
                        val bitStringLength = spki[j + 1].toInt() and 0xFF
                        val unusedBits = spki[j + 2].toInt()
                        if (unusedBits == 0 && bitStringLength == ED25519_KEY_SIZE + 1) {
                            // 找到正确的 BIT STRING，提取 raw key
                            return spki.sliceArray(j + 3 until j + 3 + ED25519_KEY_SIZE)
                        }
                    }
                }
            }
        }
        
        throw IllegalStateException("Unexpected public key format: size=${spki.size}, cannot extract Ed25519 raw key")
    }

    /**
     * Base64url 编码（无 padding）
     */
    private fun base64UrlEncode(data: ByteArray): String {
        return Base64.encodeToString(data, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }
}

/**
 * 软件密钥存储接口
 *
 * 在 API < 33 时使用，由 EncryptedStorage 实现。
 * 私钥存储在 EncryptedSharedPreferences 中（AES-256-GCM 加密）。
 */
interface SoftwareKeyStore {
    fun hasKeyPair(alias: String): Boolean
    fun saveKeyPair(alias: String, publicKeyEncoded: ByteArray, privateKeyEncoded: ByteArray)
    fun getPublicKeyEncoded(alias: String): ByteArray?
    fun getPrivateKeyEncoded(alias: String): ByteArray?
    fun deleteKeyPair(alias: String)
}
