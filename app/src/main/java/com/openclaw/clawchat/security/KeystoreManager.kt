package com.openclaw.clawchat.security

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Date

/**
 * KeystoreManager - Android 密钥管理 (Ed25519)
 *
 * Gateway 协议 v3 要求 Ed25519 密钥对：
 * - 签名：Ed25519 native（无需单独 hash）
 * - 公钥：raw 32 bytes base64url（去掉 SPKI 前缀）
 * - deviceId：sha256(raw 32 bytes public key).hex()
 *
 * 实现策略：
 * - API 33+：Android Keystore 硬件级 Ed25519
 * - API 26-32：软件 Ed25519 + EncryptedSharedPreferences 存储
 *
 * 安全特性：
 * - 私钥不可导出（Keystore 模式）
 * - 硬件级保护（TEE/StrongBox，API 33+）
 * - Ed25519 原生签名（无 hash 算法参数）
 */
class KeystoreManager(
    private val alias: String = "clawchat_device_key",
    private val softwareKeyStore: SoftwareKeyStore? = null
) {
    companion object {
        /**
         * Ed25519 SPKI DER 前缀 (12 bytes)
         * SEQUENCE { SEQUENCE { OID 1.3.101.112 } BIT STRING ... }
         * raw public key 是后 32 bytes
         */
        val ED25519_SPKI_PREFIX: ByteArray =
            byteArrayOf(0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00)

        /** API 33+ 支持 Keystore Ed25519 */
        private val USE_KEYSTORE_ED25519 = Build.VERSION.SDK_INT >= 33
    }

    private val keyStore: KeyStore? = if (USE_KEYSTORE_ED25519) {
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    } else null

    // ==================== 公钥操作 ====================

    /**
     * 检查密钥对是否已存在
     */
    fun hasKeyPair(): Boolean {
        return if (USE_KEYSTORE_ED25519) {
            keyStore!!.containsAlias(alias)
        } else {
            softwareKeyStore?.hasKeyPair(alias) ?: false
        }
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

        if (USE_KEYSTORE_ED25519) {
            generateKeystoreEd25519()
        } else {
            generateSoftwareEd25519()
        }

        return getPublicKeyBase64Url()
    }

    /**
     * 获取公钥 raw bytes（去掉 SPKI 前缀的 32 bytes）
     */
    fun getPublicKeyRaw(): ByteArray {
        val spki = getPublicKeySPKI()
        return extractRawFromSPKI(spki)
    }

    /**
     * 获取公钥 raw 32 bytes 的 base64url 编码
     * 这是 Gateway connect 协议要求的格式
     */
    fun getPublicKeyBase64Url(): String {
        return base64UrlEncode(getPublicKeyRaw())
    }

    /**
     * 获取公钥（PEM 格式，保留用于诊断/兼容）
     */
    fun getPublicKeyPem(): String {
        val spki = getPublicKeySPKI()
        val encoded = Base64.encodeToString(spki, Base64.NO_WRAP)
        return buildString {
            appendLine("-----BEGIN PUBLIC KEY-----")
            encoded.chunked(64).forEach { appendLine(it) }
            append("-----END PUBLIC KEY-----")
        }
    }

    /**
     * 计算 deviceId: sha256(raw public key).hex()
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
     * @param data 待签名数据
     * @return 签名 bytes（64 bytes）
     */
    fun sign(data: ByteArray): ByteArray {
        val privateKey = getPrivateKey()
        val signature = Signature.getInstance("Ed25519")
        signature.initSign(privateKey)
        signature.update(data)
        return signature.sign()
    }

    /**
     * Ed25519 签名（String 输入）
     *
     * @param data 待签名字符串
     * @return 签名的 base64url 编码
     */
    fun sign(data: String): String {
        return base64UrlEncode(sign(data.toByteArray(Charsets.UTF_8)))
    }

    /**
     * 对挑战进行签名（兼容旧接口）
     *
     * @param challenge 挑战数据
     * @return 签名 bytes
     */
    fun signChallenge(challenge: ByteArray): ByteArray = sign(challenge)

    /**
     * 对挑战进行签名（String → base64url）
     */
    fun signChallenge(challenge: String): String = sign(challenge)

    // ==================== 密钥管理 ====================

    /**
     * 删除密钥对
     */
    fun deleteKey() {
        if (USE_KEYSTORE_ED25519) {
            if (keyStore!!.containsAlias(alias)) {
                keyStore.deleteEntry(alias)
            }
        } else {
            softwareKeyStore?.deleteKeyPair(alias)
        }
    }

    /**
     * 获取密钥信息（诊断用）
     */
    fun getKeyInfo(): KeyInfo {
        if (!hasKeyPair()) return KeyInfo(null, null, false)

        return if (USE_KEYSTORE_ED25519) {
            val entry = keyStore!!.getEntry(alias, null) as? KeyStore.PrivateKeyEntry
                ?: return KeyInfo(null, null, false)
            KeyInfo(
                algorithm = "Ed25519",
                format = "Keystore",
                isInsideSecureHardware = true // API 33+ Keystore
            )
        } else {
            KeyInfo(
                algorithm = "Ed25519",
                format = "Software (EncryptedSharedPreferences)",
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

    // ==================== 内部方法 ====================

    /**
     * API 33+: 使用 Android Keystore 生成 Ed25519 密钥对
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
     * API 26-32: 使用软件生成 Ed25519 密钥对，存储到 EncryptedSharedPreferences
     */
    private fun generateSoftwareEd25519() {
        requireNotNull(softwareKeyStore) {
            "SoftwareKeyStore is required for API < 33"
        }
        val kpg = KeyPairGenerator.getInstance("Ed25519")
        val keyPair = kpg.generateKeyPair()

        // 存储到加密存储
        softwareKeyStore.saveKeyPair(
            alias = alias,
            publicKeyEncoded = keyPair.public.encoded,  // SPKI DER
            privateKeyEncoded = keyPair.private.encoded  // PKCS8 DER
        )
    }

    /**
     * 获取公钥 SPKI DER 编码
     */
    private fun getPublicKeySPKI(): ByteArray {
        return if (USE_KEYSTORE_ED25519) {
            val entry = keyStore!!.getEntry(alias, null) as? KeyStore.PrivateKeyEntry
                ?: throw IllegalStateException("Key pair not found. Call generateKeyPair() first.")
            entry.certificate.publicKey.encoded
        } else {
            requireNotNull(softwareKeyStore) { "SoftwareKeyStore required" }
            softwareKeyStore.getPublicKeyEncoded(alias)
                ?: throw IllegalStateException("Key pair not found. Call generateKeyPair() first.")
        }
    }

    /**
     * 获取私钥
     */
    private fun getPrivateKey(): PrivateKey {
        return if (USE_KEYSTORE_ED25519) {
            val entry = keyStore!!.getEntry(alias, null) as? KeyStore.PrivateKeyEntry
                ?: throw IllegalStateException("Key pair not found. Call generateKeyPair() first.")
            entry.privateKey
        } else {
            requireNotNull(softwareKeyStore) { "SoftwareKeyStore required" }
            val encoded = softwareKeyStore.getPrivateKeyEncoded(alias)
                ?: throw IllegalStateException("Key pair not found. Call generateKeyPair() first.")
            val keyFactory = KeyFactory.getInstance("Ed25519")
            keyFactory.generatePrivate(PKCS8EncodedKeySpec(encoded))
        }
    }

    /**
     * 从 SPKI DER 中提取 raw 32 bytes 公钥
     */
    private fun extractRawFromSPKI(spki: ByteArray): ByteArray {
        if (spki.size == ED25519_SPKI_PREFIX.size + 32) {
            val prefix = spki.sliceArray(0 until ED25519_SPKI_PREFIX.size)
            if (prefix.contentEquals(ED25519_SPKI_PREFIX)) {
                return spki.sliceArray(ED25519_SPKI_PREFIX.size until spki.size)
            }
        }
        // 兜底：返回完整 SPKI（不应该走到这里）
        return spki
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
 * 私钥存储在 EncryptedSharedPreferences 中（AES-256 加密）。
 */
interface SoftwareKeyStore {
    fun hasKeyPair(alias: String): Boolean
    fun saveKeyPair(alias: String, publicKeyEncoded: ByteArray, privateKeyEncoded: ByteArray)
    fun getPublicKeyEncoded(alias: String): ByteArray?
    fun getPrivateKeyEncoded(alias: String): ByteArray?
    fun deleteKeyPair(alias: String)
}
