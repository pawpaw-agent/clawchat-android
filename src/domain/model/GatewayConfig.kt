package com.openclaw.clawchat.domain.model

/**
 * Gateway 配置领域模型
 * 
 * 表示 OpenClaw Gateway 服务器的连接配置，包含主机地址、端口、TLS 设置等。
 * 支持局域网直连和 Tailscale 远程连接两种场景。
 * 
 * @property id 配置唯一标识符
 * @property name 配置名称（用户自定义，如 "Home Server", "VPS"）
 * @property host 主机地址（IP、域名或 Tailscale MagicDNS）
 * @property port WebSocket 端口（默认 18789）
 * @property useTls 是否使用 TLS 加密（WSS）
 * @property tlsFingerprint TLS 证书指纹（用于证书固定）
 * @property isCurrent 是否为当前选中的配置
 * @property createdAt 创建时间戳（毫秒）
 * @property updatedAt 最后更新时间戳（毫秒）
 */
data class GatewayConfig(
    val id: String = generateConfigId(),
    val name: String,
    val host: String,
    val port: Int = DEFAULT_PORT,
    val useTls: Boolean = false,
    val tlsFingerprint: String? = null,
    val isCurrent: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    /**
     * 构建 WebSocket URL
     * 
     * 根据 TLS 设置自动生成 ws:// 或 wss:// 协议的完整 URL。
     * 
     * @return 完整的 WebSocket URL 字符串
     */
    fun toWebSocketUrl(): String {
        val protocol = if (useTls) "wss" else "ws"
        return "$protocol://$host:$port"
    }
    
    /**
     * 构建 HTTP URL（用于 REST API 调用）
     */
    fun toHttpUrl(): String {
        val protocol = if (useTls) "https" else "http"
        return "$protocol://$host:$port"
    }
    
    /**
     * 检查是否为 Tailscale 连接
     * 
     * 通过检测主机名是否为 Tailscale MagicDNS 格式判断。
     */
    fun isTailscaleConnection(): Boolean {
        return host.endsWith(".ts.net") || host.endsWith(".tailnet")
    }
    
    /**
     * 检查是否为局域网连接
     * 
     * 通过检测 IP 地址是否为私有地址范围判断。
     */
    fun isLocalConnection(): Boolean {
        // 检查常见私有 IP 范围
        val privatePrefixes = listOf(
            "192.168.",
            "10.",
            "172.16.", "172.17.", "172.18.", "172.19.",
            "172.20.", "172.21.", "172.22.", "172.23.",
            "172.24.", "172.25.", "172.26.", "172.27.",
            "172.28.", "172.29.", "172.30.", "172.31.",
            "127."
        )
        return privatePrefixes.any { host.startsWith(it) } || 
               host.endsWith(".local")
    }
    
    /**
     * 复制并更新为当前选中配置
     */
    fun asCurrent(): GatewayConfig = copy(
        isCurrent = true,
        updatedAt = System.currentTimeMillis()
    )
    
    /**
     * 复制并更新为非当前配置
     */
    fun asNotCurrent(): GatewayConfig = copy(
        isCurrent = false,
        updatedAt = System.currentTimeMillis()
    )
    
    /**
     * 更新 TLS 指纹
     */
    fun withTlsFingerprint(fingerprint: String): GatewayConfig = copy(
        tlsFingerprint = fingerprint,
        updatedAt = System.currentTimeMillis()
    )
    
    companion object {
        const val DEFAULT_PORT = 18789
        
        /**
         * 生成配置 ID
         */
        private fun generateConfigId(): String {
            return "gateway_" + java.util.UUID.randomUUID()
                .toString()
                .replace("-", "")
                .take(12)
        }
        
        /**
         * 创建默认本地 Gateway 配置
         */
        fun defaultLocal(name: String = "Local Server"): GatewayConfig {
            return GatewayConfig(
                name = name,
                host = "192.168.1.1",
                port = DEFAULT_PORT,
                useTls = false
            )
        }
        
        /**
         * 创建 Tailscale Gateway 配置
         */
        fun forTailscale(
            tailnetName: String,
            deviceName: String,
            name: String = "Remote (Tailscale)"
        ): GatewayConfig {
            return GatewayConfig(
                name = name,
                host = "$deviceName.$tailnetName.ts.net",
                port = DEFAULT_PORT,
                useTls = true
            )
        }
    }
}

/**
 * 设备令牌
 * 
 * 表示设备配对成功后获得的认证令牌，用于后续连接的的身份验证。
 * 
 * @property token 令牌字符串
 * @property deviceId 设备唯一标识
 * @property issuedAt 颁发时间戳（毫秒）
 * @property expiresAt 过期时间戳（毫秒），null 表示永不过期
 * @property scopes 令牌权限范围
 */
data class DeviceToken(
    val token: String,
    val deviceId: String,
    val issuedAt: Long = System.currentTimeMillis(),
    val expiresAt: Long? = null,
    val scopes: List<String> = listOf("operator.read", "operator.write")
) {
    /**
     * 检查令牌是否已过期
     */
    fun isExpired(): Boolean {
        return expiresAt?.let { System.currentTimeMillis() > it } ?: false
    }
    
    /**
     * 检查令牌是否有效（未过期）
     */
    fun isValid(): Boolean = !isExpired()
    
    /**
     * 检查令牌是否拥有指定权限
     */
    fun hasScope(scope: String): Boolean = scope in scopes
    
    companion object {
        /**
         * 从配对响应创建设备令牌
         */
        fun fromPairingResponse(
            token: String,
            deviceId: String,
            expiresInSeconds: Long? = null
        ): DeviceToken {
            return DeviceToken(
                token = token,
                deviceId = deviceId,
                expiresAt = expiresInSeconds?.let { 
                    System.currentTimeMillis() + it * 1000 
                }
            )
        }
    }
}

/**
 * 连接状态
 * 
 * 表示与 Gateway 的连接状态。
 */
sealed class ConnectionStatus {
    /**
     * 已断开 - 未建立连接
     */
    data object Disconnected : ConnectionStatus()
    
    /**
     * 连接中 - 正在建立连接
     */
    data object Connecting : ConnectionStatus()
    
    /**
     * 已连接 - 连接成功建立
     * 
     * @property latency 当前延迟（毫秒）
     */
    data class Connected(val latency: Long? = null) : ConnectionStatus()
    
    /**
     * 断开中 - 正在关闭连接
     * 
     * @property reason 断开原因
     */
    data class Disconnecting(val reason: String) : ConnectionStatus()
    
    /**
     * 错误状态 - 连接失败
     * 
     * @property error 错误信息
     * @property recoverable 是否可自动恢复
     */
    data class Error(val error: String, val recoverable: Boolean = true) : ConnectionStatus()
    
    /**
     * 检查是否已连接
     */
    fun isConnected(): Boolean = this is Connected
    
    /**
     * 检查是否正在连接
     */
    fun isConnecting(): Boolean = this is Connecting
    
    /**
     * 检查是否已断开
     */
    fun isDisconnected(): Boolean = this is Disconnected
    
    /**
     * 检查是否有错误
     */
    fun hasError(): Boolean = this is Error
}
