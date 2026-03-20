package com.openclaw.clawchat.domain.model

/**
 * Gateway 配置领域模型
 *
 * Domain 层权威定义，表示 Gateway 连接配置。
 * 与 UI 层的 GatewayConfigUi 分离，遵循 Clean Architecture 分层原则。
 */
data class GatewayConfig(
    val id: String,
    val name: String,
    val host: String,
    val port: Int,
    val useTls: Boolean,
    val isCurrent: Boolean = false
) {
    /**
     * 构建 WebSocket URL
     */
    fun toWebSocketUrl(): String {
        val protocol = if (useTls) "wss" else "ws"
        return "$protocol://$host:$port"
    }

    companion object {
        const val DEFAULT_PORT = 18789

        /**
         * 从 URL 解析配置
         */
        fun fromUrl(url: String, id: String = "default", name: String = "Gateway"): GatewayConfig? {
            val useTls = url.startsWith("wss://")
            val withoutProtocol = url.removePrefix("ws://").removePrefix("wss://")

            val parts = withoutProtocol.split(":")
            val host = parts.firstOrNull() ?: return null
            val port = parts.lastOrNull()?.toIntOrNull() ?: DEFAULT_PORT

            return GatewayConfig(
                id = id,
                name = name,
                host = host,
                port = port,
                useTls = useTls
            )
        }
    }
}

/**
 * 连接状态
 *
 * Domain 层权威定义。
 */
sealed class ConnectionStatus {
    data object Disconnected : ConnectionStatus()
    data object Connecting : ConnectionStatus()
    data class Connected(val latency: Long? = null) : ConnectionStatus()
    data object Disconnecting : ConnectionStatus()
    data class Error(val message: String, val throwable: Throwable? = null) : ConnectionStatus()

    val isConnected: Boolean
        get() = this is Connected

    val isConnecting: Boolean
        get() = this is Connecting || this is Disconnecting
}