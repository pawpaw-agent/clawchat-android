package com.openclaw.clawchat.network

import android.content.Context
import com.openclaw.clawchat.security.SecureLogger
import java.io.ByteArrayInputStream
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * Gateway 自签名证书信任管理器
 *
 * 信任 OpenClaw Gateway 的自签名证书（CN=openclaw-gateway），
 * 同时保留系统证书信任链（用于正常 HTTPS 连接）。
 *
 * 使用场景：
 * - 局域网直连 Gateway（https://192.168.x.x:18789）
 * - Tailscale 远程连接（https://gateway.tailnet.ts.net:18789）
 * - 其他使用自签名证书的 Gateway 部署
 *
 * 安全特性：
 * - 仅信任内置的 Gateway 证书（不信任所有自签名证书）
 * - 系统证书仍然有效（可访问正常 HTTPS 服务）
 * - 证书固定在 APK 中，防止中间人攻击
 */
@Singleton
class GatewayTrustManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "GatewayTrustManager"
        private const val CERTIFICATE_RESOURCE = R.raw.openclaw_gateway
    }

    private val sslSocketFactory: SSLSocketFactory by lazy {
        val trustManager = createTrustManager()
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, arrayOf<TrustManager>(trustManager), null)
        sslContext.socketFactory
    }

    /**
     * 创建自定义 TrustManager
     *
     * 合并系统证书和 Gateway 内置证书：
     * 1. 加载系统默认 TrustManager
     * 2. 加载 Gateway 自签名证书到独立 KeyStore
     * 3. 创建复合 TrustManager，优先检查 Gateway 证书
     */
    fun createTrustManager(): X509TrustManager {
        // 加载 Gateway 自签名证书
        val gatewayCert = loadGatewayCertificate()
        val gatewayKeyStore = KeyStore.getInstance(KeyStore.getDefaultType())
        gatewayKeyStore.load(null, null)
        gatewayKeyStore.setCertificateEntry("openclaw-gateway", gatewayCert)

        // 创建 Gateway 证书的 TrustManager
        val gatewayTmFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        gatewayTmFactory.init(gatewayKeyStore)
        val gatewayTms = gatewayTmFactory.trustManagers

        // 获取系统默认 TrustManager
        val systemKeyStore = KeyStore.getInstance(KeyStore.getDefaultType())
        systemKeyStore.load(null, null)
        val systemTmFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        systemTmFactory.init(systemKeyStore)
        val systemTms = systemTmFactory.trustManagers

        // 创建复合 TrustManager
        return CompositeX509TrustManager(gatewayTms, systemTms)
    }

    /**
     * 从 raw 资源加载 Gateway 证书
     */
    private fun loadGatewayCertificate(): X509Certificate {
        return try {
            val inputStream = context.resources.openRawResource(CERTIFICATE_RESOURCE)
            val certFactory = CertificateFactory.getInstance("X.509")
            val cert = certFactory.generateCertificate(inputStream) as X509Certificate

            SecureLogger.d("Loaded Gateway certificate: CN=${cert.subjectDN}, valid until=${cert.notAfter}")
            cert
        } catch (e: Exception) {
            SecureLogger.e("Failed to load Gateway certificate", e)
            throw IllegalStateException("Gateway certificate not found in resources", e)
        }
    }

    /**
     * 获取配置好的 SSLSocketFactory
     *
     * 用于 OkHttpClient.Builder().sslSocketFactory()
     */
    fun getSslSocketFactory(): SSLSocketFactory = sslSocketFactory

    /**
     * 获取证书指纹（用于调试/验证）
     */
    fun getCertificateFingerprint(): String {
        return try {
            val cert = loadGatewayCertificate()
            val md = java.security.MessageDigest.getInstance("SHA-256")
            val digest = md.digest(cert.encoded)
            digest.joinToString(":") { "%02X".format(it) }
        } catch (e: Exception) {
            "UNKNOWN"
        }
    }
}

/**
 * 复合 X509 信任管理器
 *
 * 按顺序检查多个 TrustManager：
 * 1. 先检查 Gateway 证书 TrustManager
 * 2. 再检查系统证书 TrustManager
 *
 * 只要有一个 TrustManager 信任该证书，就接受连接。
 */
class CompositeX509TrustManager(
    private val gatewayTms: Array<TrustManager>,
    private val systemTms: Array<TrustManager>
) : X509TrustManager {

    private val gatewayDelegate = gatewayTms
        .filterIsInstance<X509TrustManager>()
        .firstOrNull()
        ?: throw IllegalStateException("No X509TrustManager in Gateway TrustManagers")

    private val systemDelegate = systemTms
        .filterIsInstance<X509TrustManager>()
        .firstOrNull()
        ?: throw IllegalStateException("No X509TrustManager in system TrustManagers")

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        // 客户端验证：委托给系统 TrustManager
        systemDelegate.checkClientTrusted(chain, authType)
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        if (chain.isNullOrEmpty()) {
            throw CertificateException("Empty certificate chain")
        }

        val serverCert = chain.first()

        // 先尝试 Gateway 证书 TrustManager
        try {
            gatewayDelegate.checkServerTrusted(chain, authType)
            SecureLogger.d("Server certificate trusted by Gateway TrustManager: CN=${serverCert.subjectDN}")
            return
        } catch (e: CertificateException) {
            // Gateway 证书不匹配，继续尝试系统 TrustManager
            SecureLogger.d("Gateway TrustManager rejected: ${e.message}")
        }

        // 再尝试系统 TrustManager
        try {
            systemDelegate.checkServerTrusted(chain, authType)
            SecureLogger.d("Server certificate trusted by system TrustManager: CN=${serverCert.subjectDN}")
            return
        } catch (e: CertificateException) {
            // 都不信任，抛出异常
            SecureLogger.w("Server certificate rejected by both TrustManagers: CN=${serverCert.subjectDN}")
            throw CertificateException(
                "Certificate not trusted. CN=${serverCert.subjectDN}, " +
                "Issuer=${serverCert.issuerDN}. Neither Gateway nor system CA trusts this certificate."
            )
        }
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> {
        return (gatewayDelegate.acceptedIssuers + systemDelegate.acceptedIssuers).distinct().toTypedArray()
    }
}

/**
 * 使用示例（Hilt 模块）：
 *
 * @Provides
 * @Singleton
 * fun provideOkHttpClient(
 *     @ApplicationContext context: Context,
 *     gatewayTrustManager: GatewayTrustManager
 * ): OkHttpClient {
 *     return OkHttpClient.Builder()
 *         .sslSocketFactory(
 *             gatewayTrustManager.getSslSocketFactory(),
 *             gatewayTrustManager.createTrustManager()
 *         )
 *         .build()
 * }
 */
