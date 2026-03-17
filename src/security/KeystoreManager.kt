package com.openclaw.clawchat.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Signature
import java.util.Date
import javax.crypto.Cipher

/**
 * KeystoreManager - Android Keystore 密钥管理
 * 
 * 负责：
 * - 生成 ECDSA secp256r1 设备密钥对
 * - 存储私钥于硬件级 Keystore（TEE/StrongBox）
 * - 执行挑战 - 响应签名
 * - 获取设备公钥用于配对
 * 
 * 安全特性：
 * - 私钥不可导出
 * - 硬件级保护（防 Root/防提取）
 * - SHA256 摘要签名
 */
class KeystoreManager(
    private val alias: String = "clawchat_device_key"
) {
    private val keyStore: KeyStore = KeyStore.getInstance("AndroidKeyStore").apply {
        load(null)
    }

    /**
     * 检查密钥对是否已存在
     */
    fun hasKeyPair(): Boolean {
        return keyStore.containsAlias(alias)
    }

    /**
     * 生成新的 ECDSA 密钥对
     * 
     * @return 生成的公钥（PEM 格式）
     * @throws IllegalStateException 如果密钥生成失败
     */
    fun generateKeyPair(): String {
        if (hasKeyPair()) {
            deleteKey()
        }

        val keyPairGenerator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_EC,
            "AndroidKeyStore"
        )

        val spec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        )
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setUserAuthenticationRequired(false)
            .setKeyValidityStartFromDate(Date())
            .setAttestationChallenge(alias.toByteArray())
            .build()

        keyPairGenerator.initialize(spec)
        val keyPair = keyPairGenerator.generateKeyPair()

        return getPublicKeyPem()
    }

    /**
     * 获取公钥（PEM 格式）
     * 
     * @return PEM 格式的公钥字符串
     * @throws IllegalStateException 如果密钥对不存在
     */
    fun getPublicKeyPem(): String {
        val entry = keyStore.getEntry(alias, null) as? KeyStore.PrivateKeyEntry
            ?: throw IllegalStateException("Key pair not found. Call generateKeyPair() first.")

        val publicKey = entry.certificate.publicKey
        val encoded = Base64.encodeToString(publicKey.encoded, Base64.NO_WRAP)

        return buildString {
            appendLine("-----BEGIN PUBLIC KEY-----")
            // 每 64 字符换行（PEM 标准）
            encoded.chunked(64).forEach { appendLine(it) }
            append("-----END PUBLIC KEY-----")
        }
    }

    /**
     * 对挑战进行签名
     * 
     * @param challenge 挑战数据（通常为 server nonce）
     * @return ECDSA 签名（DER 编码）
     * @throws IllegalStateException 如果密钥对不存在
     */
    fun signChallenge(challenge: ByteArray): ByteArray {
        val entry = keyStore.getEntry(alias, null) as? KeyStore.PrivateKeyEntry
            ?: throw IllegalStateException("Key pair not found. Call generateKeyPair() first.")

        val signature = Signature.getInstance("SHA256withECDSA")
        signature.initSign(entry.privateKey)
        signature.update(challenge)

        return signature.sign()
    }

    /**
     * 对挑战进行签名（String 输入）
     * 
     * @param challenge 挑战字符串
     * @return Base64 编码的签名
     */
    fun signChallenge(challenge: String): String {
        val signature = signChallenge(challenge.toByteArray())
        return Base64.encodeToString(signature, Base64.NO_WRAP)
    }

    /**
     * 删除密钥对（用于重置或卸载）
     */
    fun deleteKey() {
        if (hasKeyPair()) {
            keyStore.deleteEntry(alias)
        }
    }

    /**
     * 获取密钥信息（用于调试/诊断）
     */
    fun getKeyInfo(): KeyInfo {
        val entry = keyStore.getEntry(alias, null) as? KeyStore.PrivateKeyEntry
            ?: return KeyInfo(null, null, false)

        val certificate = entry.certificate
        val publicKey = certificate.publicKey

        return KeyInfo(
            algorithm = publicKey.algorithm,
            format = publicKey.format,
            isInsideSecureHardware = entry.isInsideSecureHardware,
            certificateNotBefore = certificate.notBefore,
            certificateNotAfter = certificate.notAfter
        )
    }

    /**
     * 密钥信息数据类
     */
    data class KeyInfo(
        val algorithm: String?,
        val format: String?,
        val isInsideSecureHardware: Boolean,
        val certificateNotBefore: Date?,
        val certificateNotAfter: Date?
    )
}

/**
 * 使用示例：
 * 
 * // 1. 初始化（首次启动时）
 * val keystoreManager = KeystoreManager()
 * if (!keystoreManager.hasKeyPair()) {
 *     val publicKeyPem = keystoreManager.generateKeyPair()
 *     // 发送 publicKeyPem 到 Gateway 进行配对
 * }
 * 
 * // 2. 配对时签名挑战
 * val challenge = serverNonce // 从 Gateway 获取
 * val signature = keystoreManager.signChallenge(challenge)
 * 
 * // 3. 获取公钥用于配对请求
 * val publicKey = keystoreManager.getPublicKeyPem()
 * 
 * // 4. 诊断信息
 * val info = keystoreManager.getKeyInfo()
 * println("Key in secure hardware: ${info.isInsideSecureHardware}")
 */
