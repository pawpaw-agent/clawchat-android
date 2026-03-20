package com.openclaw.clawchat.network

import android.content.Context
import com.openclaw.clawchat.security.CertificateFingerprintManager
import com.openclaw.clawchat.security.SecureLogger
import com.openclaw.clawchat.security.getSha256Fingerprint
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * Gateway 证书信任管理器（TOFU 模式）
 *
 * 使用 SSH 风格的首次信任（Trust On First Use）模型：
 * 1. 首次连接时显示证书指纹，用户手动确认
 * 2. 保存指纹到加密存储
 * 3. 后续连接验证指纹匹配
 *
 * 不嵌入固定证书，支持任意 Gateway 部署。
 *
 * 安全特性：
 * - 系统证书仍然有效（可访问正常 HTTPS 服务）
 * - 用户手动确认首次连接的证书
 * - 证书变更时告警（防止中间人攻击）
 */
@Singleton
class GatewayTrustManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fingerprintManager: CertificateFingerprintManager
) {
    companion object {
        private const val TAG = "GatewayTrustManager"
    }

    private val _sslSocketFactory: SSLSocketFactory by lazy {
        val trustManager = createTrustManager()
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, arrayOf<TrustManager>(trustManager), null)
        sslContext.socketFactory
    }

    /**
     * 创建自定义 TrustManager
     *
     * 合并系统证书和用户信任的证书：
     * 1. 加载系统默认 TrustManager
     * 2. 创建动态 TrustManager（检查用户保存的指纹）
     * 3. 创建复合 TrustManager
     */
    fun createTrustManager(): X509TrustManager {
        // 获取系统默认 TrustManager
        val systemKeyStore = KeyStore.getInstance(KeyStore.getDefaultType())
        systemKeyStore.load(null, null)
        val systemTmFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        systemTmFactory.init(systemKeyStore)
        val systemTms = systemTmFactory.trustManagers

        // 创建动态 TrustManager（检查用户保存的指纹）
        val dynamicTm = DynamicTrustManager(fingerprintManager)

        // 创建复合 TrustManager
        return CompositeX509TrustManager(dynamicTm, systemTms)
    }

    /**
     * 获取配置好的 SSLSocketFactory
     *
     * 用于 OkHttpClient.Builder().sslSocketFactory()
     */
    fun getSslSocketFactory(): SSLSocketFactory = _sslSocketFactory
}

/**
 * 动态证书信任管理器
 *
 * 检查用户手动确认的证书指纹：
 * 1. 获取当前连接的主机名
 * 2. 从 CertificateFingerprintManager 获取已保存的指纹
 * 3. 验证服务器证书指纹是否匹配
 *
 * 如果未保存过指纹（首次连接），抛出 CertificateException，
 * 触发 UI 层的证书确认流程。
 */
class DynamicTrustManager(
    private val fingerprintManager: CertificateFingerprintManager
) : X509TrustManager {

    companion object {
        // ThreadLocal 用于传递当前连接的主机名
        private val currentHostname = ThreadLocal<String?>()
        
        /**
         * 设置当前连接的主机名（在连接前调用）
         */
        fun setCurrentHostname(hostname: String?) {
            currentHostname.set(hostname)
        }
        
        /**
         * 清除当前连接的主机名（在连接后调用）
         */
        fun clearCurrentHostname() {
            currentHostname.remove()
        }
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        // 客户端验证：不处理
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        if (chain.isNullOrEmpty()) {
            throw CertificateException("Empty certificate chain")
        }

        val serverCert = chain.first() as X509Certificate
        val hostname = getCurrentHostname()
            ?: throw CertificateException("Cannot verify certificate: unknown hostname")

        val fingerprint = serverCert.getSha256Fingerprint()
        val status = fingerprintManager.isTrusted(hostname, fingerprint)

        when (status) {
            is com.openclaw.clawchat.security.TrustStatus.Trusted -> {
                SecureLogger.d("Certificate trusted for $hostname: ${fingerprint.take(20)}...")
                return
            }
            is com.openclaw.clawchat.security.TrustStatus.NotTrusted -> {
                // 首次连接，抛出异常触发 UI 确认
                SecureLogger.i("First connection to $hostname, fingerprint: $fingerprint")
                throw CertificateExceptionFirstTime(hostname, fingerprint, serverCert)
            }
            is com.openclaw.clawchat.security.TrustStatus.Mismatch -> {
                // 证书变更，抛出异常触发 UI 告警
                SecureLogger.w("Certificate mismatch for $hostname: stored=${status.storedFingerprint}, current=$fingerprint")
                throw CertificateExceptionMismatch(hostname, status.storedFingerprint, fingerprint, serverCert)
            }
        }
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> {
        return emptyArray()
    }

    /**
     * 获取当前连接的主机名
     *
     * 通过 ThreadLocal 传递主机名（在连接前由调用方设置）
     */
    private fun getCurrentHostname(): String? {
        return currentHostname.get()
    }
}

/**
 * 首次连接证书异常
 *
 * 携带证书信息，供 UI 层显示确认对话框
 */
class CertificateExceptionFirstTime(
    val hostname: String,
    val fingerprint: String,
    val certificate: X509Certificate
) : CertificateException("First connection to $hostname. Fingerprint: $fingerprint")

/**
 * 证书不匹配异常
 *
 * 携带新旧指纹，供 UI 层显示告警对话框
 */
class CertificateExceptionMismatch(
    val hostname: String,
    val storedFingerprint: String,
    val currentFingerprint: String,
    val certificate: X509Certificate
) : CertificateException("Certificate mismatch for $hostname. Stored: $storedFingerprint, Current: $currentFingerprint")

/**
 * 复合 X509 信任管理器
 *
 * 按顺序检查多个 TrustManager：
 * 1. 先检查动态 TrustManager（用户信任的证书）
 * 2. 再检查系统证书 TrustManager
 *
 * 只要有一个 TrustManager 信任该证书，就接受连接。
 */
class CompositeX509TrustManager(
    private val dynamicTm: DynamicTrustManager,
    private val systemTms: Array<TrustManager>
) : X509TrustManager {

    private val systemDelegate = systemTms
        .filterIsInstance<X509TrustManager>()
        .firstOrNull()
        ?: throw IllegalStateException("No X509TrustManager in system TrustManagers")

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        systemDelegate.checkClientTrusted(chain, authType)
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        if (chain.isNullOrEmpty()) {
            throw CertificateException("Empty certificate chain")
        }

        val serverCert = chain.first()

        // 先尝试动态 TrustManager（用户信任的证书）
        try {
            dynamicTm.checkServerTrusted(chain, authType)
            SecureLogger.d("Server certificate trusted by Dynamic TrustManager: CN=${serverCert.subjectDN}")
            return
        } catch (e: CertificateException) {
            // 动态 TrustManager 拒绝，继续尝试系统 TrustManager
            SecureLogger.d("Dynamic TrustManager rejected: ${e.message}")

            // 如果是首次连接或证书不匹配，直接抛出异常（需要用户确认）
            if (e is CertificateExceptionFirstTime || e is CertificateExceptionMismatch) {
                throw e
            }
        }

        // 再尝试系统 TrustManager
        try {
            systemDelegate.checkServerTrusted(chain, authType)
            SecureLogger.d("Server certificate trusted by system TrustManager: CN=${serverCert.subjectDN}")
            return
        } catch (e: CertificateException) {
            // 都不信任，抛出异常
            SecureLogger.w("Server certificate rejected by both TrustManagers: CN=${serverCert.subjectDN}")
            throw e
        }
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> {
        return (dynamicTm.acceptedIssuers + systemDelegate.acceptedIssuers).distinct().toTypedArray()
    }
}
