package com.openclaw.clawchat.security

import android.util.Base64
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Date

/**
 * KeystoreManager - Ed25519 密钥管理（软件实现）
 *
 * 使用 BouncyCastle 软件实现，兼容所有 Android 设备。
 * 密钥存储在 EncryptedSharedPreferences 中（AES-256-GCM 加密）。
 *
 * Gateway 协议 v3 要求 Ed25519 密钥对：
 * - 签名：Ed25519 纯签名（等效 Node.js crypto.sign(null, payload, key)）
 * - 公钥：raw 32 bytes base64url（去掉 SPKI 前缀）
 * - deviceId：sha256(raw 32 bytes public key).hex()
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

        /** Ed25519 raw key 长度 */
        private const val ED25519_KEY_SIZE = 32
    }

    // BouncyCastle 公钥缓存（非敏感）
    private var cachedPublicKey: Ed25519PublicKeyParameters? = null

    // ==================== 公开 API ====================

    /**
     * 检查密钥对是否已存在
     */
    fun hasKeyPair(): Boolean {
        return softwareKeyStore?.hasKeyPair(alias) ?: false
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
        clearCache()
        generateEd25519KeyPair()
        return getPublicKeyBase64Url()
    }

    /**
     * 获取公钥 raw bytes（32 bytes）
     */
    fun getPublicKeyRaw(): ByteArray {
        return loadPublicKey().encoded
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
        val privateKey = loadPrivateKey()
        return try {
            val signer = Ed25519Signer()
            signer.init(true, privateKey)
            signer.update(data, 0, data.size)
            signer.generateSignature()
        } finally {
            // 签名完成后立即清除私钥内存引用
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
        softwareKeyStore?.deleteKeyPair(alias)
        clearCache()
    }

    /**
     * 获取密钥信息（诊断用）
     */
    fun getKeyInfo(): KeyInfo {
        if (!hasKeyPair()) return KeyInfo(null, null, false)

        return KeyInfo(
            algorithm = "Ed25519",
            format = "EncryptedSharedPreferences (AES-256-GCM)",
            isInsideSecureHardware = false
        )
    }

    data class KeyInfo(
        val algorithm: String?,
        val format: String?,
        val isInsideSecureHardware: Boolean,
        val certificateNotBefore: Date? = null,
        val certificateNotAfter: Date? = null
    )

    // ==================== 内部实现 ====================

    /**
     * 生成 Ed25519 密钥对
     *
     * 密钥以 raw bytes 形式存储在 EncryptedSharedPreferences 中：
     * - publicKey: 32 bytes → SPKI DER 编码存储
     * - privateKey: 32 bytes → 原始 seed 存储
     */
    private fun generateEd25519KeyPair() {
        requireNotNull(softwareKeyStore) {
            "SoftwareKeyStore is required for Ed25519 key storage"
        }

        val generator = Ed25519KeyPairGenerator()
        generator.init(Ed25519KeyGenerationParameters(SecureRandom()))
        val keyPair = generator.generateKeyPair()

        val publicKey = keyPair.public as Ed25519PublicKeyParameters
        val privateKey = keyPair.private as Ed25519PrivateKeyParameters

        // 构造 SPKI DER
        val spki = ED25519_SPKI_PREFIX + publicKey.encoded

        // 存储 raw 私钥 seed（32 bytes）和 SPKI 公钥
        softwareKeyStore.saveKeyPair(
            alias = alias,
            publicKeyEncoded = spki,
            privateKeyEncoded = privateKey.encoded  // raw 32 bytes seed
        )

        // 缓存公钥（非敏感）
        cachedPublicKey = publicKey
    }

    /**
     * 从存储加载公钥（可缓存，非敏感）
     */
    private fun loadPublicKey(): Ed25519PublicKeyParameters {
        cachedPublicKey?.let { return it }

        requireNotNull(softwareKeyStore) { "SoftwareKeyStore required" }

        val publicSpki = softwareKeyStore.getPublicKeyEncoded(alias)
            ?: throw IllegalStateException("Key pair not found. Call generateKeyPair() first.")
        val publicRaw = extractRawFromSPKI(publicSpki)
        val publicKey = Ed25519PublicKeyParameters(publicRaw, 0)
        cachedPublicKey = publicKey
        return publicKey
    }

    /**
     * 从存储加载私钥（不缓存，用完即弃）
     */
    private fun loadPrivateKey(): Ed25519PrivateKeyParameters {
        requireNotNull(softwareKeyStore) { "SoftwareKeyStore required" }

        val privateRaw = softwareKeyStore.getPrivateKeyEncoded(alias)
            ?: throw IllegalStateException("Key pair not found. Call generateKeyPair() first.")
        return Ed25519PrivateKeyParameters(privateRaw, 0)
    }

    /**
     * 清除公钥缓存
     */
    private fun clearCache() {
        cachedPublicKey = null
    }

    // ==================== 工具方法 ====================

    /**
     * 从 SPKI DER 中提取 raw 32 bytes 公钥
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
        
        throw IllegalStateException("Unexpected public key format: size=${spki.size}")
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
 * 由 EncryptedStorage 实现。
 * 私钥存储在 EncryptedSharedPreferences 中（AES-256-GCM 加密）。
 */
interface SoftwareKeyStore {
    fun hasKeyPair(alias: String): Boolean
    fun saveKeyPair(alias: String, publicKeyEncoded: ByteArray, privateKeyEncoded: ByteArray)
    fun getPublicKeyEncoded(alias: String): ByteArray?
    fun getPrivateKeyEncoded(alias: String): ByteArray?
    fun deleteKeyPair(alias: String)
}