package com.openclaw.clawchat.network

import android.content.Context
import android.content.SharedPreferences
import com.openclaw.clawchat.util.AppLog
import okhttp3.CertificatePinner
import java.security.MessageDigest
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.util.Base64

/**
 * Certificate Pinning 管理器
 *
 * 功能：
 * - 支持 TOFU (Trust On First Use) 模式
 * - 支持预配置的证书 pin
 * - 支持动态添加/移除 pin
 * - 防止中间人攻击
 */
class CertificatePinningManager(private val context: Context) {

    companion object {
        private const val TAG = "CertPinningManager"
        private const val PREFS_NAME = "certificate_pins"
        private const val KEY_PINS = "pins"

        /**
         * 预配置的 OpenClaw Gateway 证书 pins
         * 这些是已知的 OpenClaw Gateway 证书公钥 SHA-256 哈希
         */
        val PRECONFIGURED_PINS: Set<String> = setOf(
            // 示例 pins，实际使用时需要替换为真实证书的 pin
            // 可以通过 openssl s_client -connect host:port | openssl x509 -pubkey -noout | openssl pkey -pubin -outform der | openssl dgst -sha256 -binary | openssl enc -base64 获取
        )
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * 所有有效的证书 pins
     */
    val allPins: Set<String>
        get() = PRECONFIGURED_PINS + getPersistedPins()

    /**
     * 获取已持久化的 pins
     */
    private fun getPersistedPins(): Set<String> {
        return prefs.getStringSet(KEY_PINS, emptySet()) ?: emptySet()
    }

    /**
     * 添加新的证书 pin
     */
    fun addPin(pin: String) {
        val currentPins = getPersistedPins().toMutableSet()
        if (currentPins.add(pin)) {
            prefs.edit().putStringSet(KEY_PINS, currentPins).apply()
            AppLog.i(TAG, "Added certificate pin: ${pin.take(16)}...")
        }
    }

    /**
     * 移除证书 pin
     */
    fun removePin(pin: String) {
        val currentPins = getPersistedPins().toMutableSet()
        if (currentPins.remove(pin)) {
            prefs.edit().putStringSet(KEY_PINS, currentPins).apply()
            AppLog.i(TAG, "Removed certificate pin: ${pin.take(16)}...")
        }
    }

    /**
     * 清除所有持久化的 pins
     */
    fun clearPins() {
        prefs.edit().remove(KEY_PINS).apply()
        AppLog.i(TAG, "Cleared all persisted certificate pins")
    }

    /**
     * 从证书计算 pin
     */
    fun computePin(certificate: Certificate): String {
        val publicKey = certificate.publicKey.encoded
        val digest = MessageDigest.getInstance("SHA-256").digest(publicKey)
        return "sha256/${Base64.getEncoder().encodeToString(digest).trimEnd('=')}"
    }

    /**
     * 创建 OkHttp CertificatePinner
     */
    fun createCertificatePinner(hostname: String): CertificatePinner {
        val pins = allPins.toList()
        return if (pins.isNotEmpty()) {
            CertificatePinner.Builder()
                .add(hostname, *pins.toTypedArray())
                .build()
        } else {
            CertificatePinner.DEFAULT
        }
    }

    /**
     * 验证证书是否匹配已知的 pins
     */
    fun validateCertificate(certificate: Certificate): Boolean {
        if (allPins.isEmpty()) {
            // 没有配置 pins，允许所有证书
            return true
        }

        val pin = computePin(certificate)
        return allPins.contains(pin)
    }

    /**
     * TOFU: 首次使用时信任证书
     */
    fun trustOnFirstUse(hostname: String, certificate: Certificate): Boolean {
        val pin = computePin(certificate)

        // 如果已有 pins，检查是否匹配
        if (allPins.isNotEmpty()) {
            return allPins.contains(pin)
        }

        // 没有已存储的 pin，添加新 pin
        addPin(pin)
        AppLog.i(TAG, "TOFU: Trusted certificate for $hostname")
        return true
    }
}