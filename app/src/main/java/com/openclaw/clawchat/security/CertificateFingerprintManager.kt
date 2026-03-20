package com.openclaw.clawchat.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.openclaw.clawchat.security.SecureLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.cert.X509Certificate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 证书指纹管理器
 *
 * 管理用户对 Gateway 证书的信任：
 * - 首次连接时保存用户确认的证书指纹
 * - 后续连接验证证书指纹匹配
 * - 支持重置信任（清除所有保存的指纹）
 *
 * 使用 EncryptedSharedPreferences 存储，确保：
 * - 指纹数据加密存储（AES-256-GCM）
 * - 仅本应用可访问
 * - Root 设备难以提取
 *
 * SSH 风格信任模型（TOFU - Trust On First Use）：
 * 1. 首次连接时显示证书指纹，用户手动确认
 * 2. 保存指纹到加密存储
 * 3. 后续连接验证指纹，不匹配则告警
 */
@Singleton
class CertificateFingerprintManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "CertFingerprintMgr"
        private const val PREFS_NAME = "clawchat_cert_fingerprints"
    }

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /**
     * 保存用户对证书的信任
     *
     * @param gatewayId Gateway 标识（通常是 hostname 或 IP）
     * @param fingerprint SHA-256 指纹（冒号分隔的十六进制）
     * @param userVerified 是否经过用户手动确认
     */
    fun trustCertificate(
        gatewayId: String,
        fingerprint: String,
        userVerified: Boolean = true
    ) {
        val key = makeKey(gatewayId)
        val value = encodeFingerprintData(fingerprint, userVerified)

        prefs.edit().putString(key, value).apply()
        SecureLogger.d("Trusted certificate for $gatewayId: $fingerprint")
    }

    /**
     * 检查证书是否被信任
     *
     * @param gatewayId Gateway 标识
     * @param fingerprint 当前证书指纹
     * @return 信任状态
     */
    fun isTrusted(gatewayId: String, fingerprint: String): TrustStatus {
        val key = makeKey(gatewayId)
        val stored = prefs.getString(key, null) ?: return TrustStatus.NotTrusted

        val (storedFingerprint, userVerified) = decodeFingerprintData(stored)

        return when {
            storedFingerprint == fingerprint -> {
                TrustStatus.Trusted(userVerified)
            }
            else -> {
                TrustStatus.Mismatch(storedFingerprint, fingerprint)
            }
        }
    }

    /**
     * 获取已保存的证书指纹
     *
     * @param gatewayId Gateway 标识
     * @return 指纹字符串，如果不存在返回 null
     */
    fun getStoredFingerprint(gatewayId: String): String? {
        val key = makeKey(gatewayId)
        val stored = prefs.getString(key, null) ?: return null
        return decodeFingerprintData(stored).first
    }

    /**
     * 移除对特定 Gateway 的信任
     *
     * @param gatewayId Gateway 标识
     */
    fun untrustCertificate(gatewayId: String) {
        val key = makeKey(gatewayId)
        prefs.edit().remove(key).apply()
        SecureLogger.d("Removed trust for $gatewayId")
    }

    /**
     * 清除所有保存的证书信任
     *
     * 用于用户重置证书信任或重新配对所有 Gateway
     */
    fun clearAllTrust() {
        prefs.edit().clear().apply()
        SecureLogger.i("Cleared all certificate trust")
    }

    /**
     * 获取所有已信任的 Gateway 列表
     *
     * @return (gatewayId, fingerprint) 列表
     */
    fun getAllTrustedGateways(): List<TrustedGateway> {
        return prefs.all
            .filterKeys { it.startsWith("gw:") }
            .map { (key, value) ->
                val gatewayId = key.substringAfter("gw:")
                val (fingerprint, userVerified) = decodeFingerprintData(value.toString())
                TrustedGateway(gatewayId, fingerprint, userVerified)
            }
    }

    /**
     * 检查是否有已信任的 Gateway
     */
    fun hasAnyTrustedGateway(): Boolean {
        return prefs.all.any { it.key.startsWith("gw:") }
    }

    // ==================== 内部方法 ====================

    /**
     * 生成存储键名
     */
    private fun makeKey(gatewayId: String): String {
        // 使用 "gw:" 前缀区分其他配置
        return "gw:$gatewayId"
    }

    /**
     * 编码指纹数据
     *
     * 格式：fingerprint|verified (verified: 0/1)
     */
    private fun encodeFingerprintData(fingerprint: String, verified: Boolean): String {
        return "$fingerprint|${if (verified) "1" else "0"}"
    }

    /**
     * 解码指纹数据
     *
     * @return (fingerprint, verified)
     */
    private fun decodeFingerprintData(data: String): Pair<String, Boolean> {
        val parts = data.split("|")
        return when {
            parts.size >= 2 -> parts[0] to (parts[1] == "1")
            else -> parts[0] to false
        }
    }
}

/**
 * 证书信任状态
 */
sealed class TrustStatus {
    /** 未保存过指纹（首次连接） */
    data object NotTrusted : TrustStatus()

    /** 证书已信任 */
    data class Trusted(val userVerified: Boolean) : TrustStatus()

    /** 证书指纹不匹配（可能中间人攻击或证书更新） */
    data class Mismatch(
        val storedFingerprint: String,
        val currentFingerprint: String
    ) : TrustStatus()

    /**
     * 是否需要用户确认
     */
    fun needsUserConfirmation(): Boolean = this is NotTrusted || this is Mismatch
}

/**
 * 已信任的 Gateway 信息
 */
data class TrustedGateway(
    val gatewayId: String,
    val fingerprint: String,
    val userVerified: Boolean
)

/**
 * 从 X509Certificate 提取 SHA-256 指纹
 *
 * 格式：XX:XX:XX:...（冒号分隔的大写十六进制）
 */
fun X509Certificate.getSha256Fingerprint(): String {
    val md = java.security.MessageDigest.getInstance("SHA-256")
    val digest = md.digest(encoded)
    return digest.joinToString(":") { "%02X".format(it) }
}

/**
 * 格式化指纹为人类可读格式
 *
 * 将长指纹分成 8 位一组，便于对比：
 * AB:CD:EF:12:34:56:78:90:AB:CD:EF:12:34:56:78:90
 * → AB:CD:EF:12 34:56:78:90 AB:CD:EF:12 34:56:78:90
 */
fun String.formatFingerprint(): String {
    return this
        .replace(":", "")
        .chunked(8)
        .joinToString(" ") { it.chunked(2).joinToString(":") }
}

/**
 * 使用示例：
 *
 * // 1. 首次连接时
 * val cert = serverCertificate
 * val fingerprint = cert.getSha256Fingerprint()
 *
 * // 显示确认对话框
 * if (fingerprintManager.isTrusted(gatewayUrl, fingerprint) is NotTrusted) {
 *     showCertificateConfirmationDialog(gatewayUrl, fingerprint)
 * }
 *
 * // 用户确认后
 * fingerprintManager.trustCertificate(gatewayUrl, fingerprint, userVerified = true)
 *
 * // 2. 后续连接验证
 * when (val status = fingerprintManager.isTrusted(gatewayUrl, fingerprint)) {
 *     is Trusted -> proceedWithConnection()
 *     is Mismatch -> showCertificateWarningDialog(status.storedFingerprint, status.currentFingerprint)
 *     is NotTrusted -> showCertificateConfirmationDialog()
 * }
 *
 * // 3. 设置页面重置
 * fingerprintManager.clearAllTrust()
 */
