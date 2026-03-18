package com.openclaw.clawchat.network

/**
 * Gateway 地址标准化工具
 *
 * 用户输入各种格式的地址，统一转换为 WebSocket URL。
 *
 * 支持的输入格式：
 * - 192.168.0.213:18789
 * - 192.168.0.213
 * - http://192.168.0.213:18789
 * - https://192.168.0.213:18789
 * - ws://192.168.0.213:18789/ws
 * - wss://192.168.0.213:18789/ws
 * - gateway.tailnet.ts.net
 * - gateway.tailnet.ts.net:443
 *
 * 输出格式: ws://host:port/ws 或 wss://host:port/ws
 */
object GatewayUrlUtil {

    private const val DEFAULT_PORT = 18789
    private const val WS_PATH = "/ws"

    /**
     * 将用户输入标准化为 WebSocket URL
     *
     * @param input 用户输入的地址
     * @return 标准化的 ws:// 或 wss:// URL，包含 /ws 路径
     */
    fun normalizeToWebSocketUrl(input: String): String {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return ""

        // 已经是完整的 ws/wss URL
        if (trimmed.startsWith("ws://") || trimmed.startsWith("wss://")) {
            return ensureWsPath(trimmed)
        }

        // http/https → ws/wss
        if (trimmed.startsWith("http://")) {
            val wsUrl = "ws://" + trimmed.removePrefix("http://")
            return ensureWsPath(wsUrl)
        }
        if (trimmed.startsWith("https://")) {
            val wsUrl = "wss://" + trimmed.removePrefix("https://")
            return ensureWsPath(wsUrl)
        }

        // 裸地址: host 或 host:port
        val useTls = looksLikeTlsHost(trimmed)
        val protocol = if (useTls) "wss" else "ws"
        val hostPort = ensurePort(trimmed, if (useTls) 443 else DEFAULT_PORT)
        return "$protocol://$hostPort$WS_PATH"
    }

    /**
     * 验证用户输入是否为有效的 Gateway 地址
     *
     * 宽松验证——只要能解析出 host 就行，不要求协议前缀。
     */
    fun isValidInput(input: String): Boolean {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return false

        // 去掉协议前缀后检查
        val hostPart = trimmed
            .removePrefix("ws://").removePrefix("wss://")
            .removePrefix("http://").removePrefix("https://")
            .removeSuffix("/ws").removeSuffix("/")

        if (hostPart.isEmpty()) return false

        // 提取 host（去掉端口）
        val host = hostPart.substringBefore(":")
        if (host.isEmpty()) return false

        // 检查端口（如果有）
        if (hostPart.contains(":")) {
            val portStr = hostPart.substringAfter(":")
            val port = portStr.toIntOrNull() ?: return false
            if (port !in 1..65535) return false
        }

        return true
    }

    /**
     * 从用户输入提取显示用的地址（不含协议和路径）
     */
    fun extractDisplayAddress(input: String): String {
        return input.trim()
            .removePrefix("ws://").removePrefix("wss://")
            .removePrefix("http://").removePrefix("https://")
            .removeSuffix("/ws").removeSuffix("/")
    }

    /**
     * 确保 URL 以 /ws 结尾
     */
    private fun ensureWsPath(url: String): String {
        val base = url.removeSuffix("/")
        return if (base.endsWith(WS_PATH)) base else "$base$WS_PATH"
    }

    /**
     * 确保 host:port 格式
     */
    private fun ensurePort(hostPort: String, defaultPort: Int): String {
        // 去掉尾部路径
        val clean = hostPort.removeSuffix("/ws").removeSuffix("/")
        return if (clean.contains(":")) clean else "$clean:$defaultPort"
    }

    /**
     * 猜测是否应该用 TLS
     *
     * .ts.net 域名通常走 HTTPS/WSS
     */
    private fun looksLikeTlsHost(host: String): Boolean {
        val clean = host.substringBefore(":").substringBefore("/")
        return clean.endsWith(".ts.net") || clean.endsWith(".tailnet")
    }
}
