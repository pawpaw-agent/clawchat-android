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
 * - ws://192.168.0.213:18789/ws
 *
 * 注意：OpenClaw Gateway 不支持 TLS，统一使用 ws://
 */
object GatewayUrlUtil {

    private const val DEFAULT_PORT = 18789
    private const val WS_PATH = "/ws"

    /**
     * 将用户输入标准化为 WebSocket URL
     *
     * 统一使用 ws://（OpenClaw Gateway 不支持 TLS）
     *
     * @param input 用户输入的地址
     * @return 标准化的 ws:// URL，包含 /ws 路径
     */
    fun normalizeToWebSocketUrl(input: String): String {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return ""

        // 去掉所有协议前缀，统一使用 ws://
        val hostPort = trimmed
            .removePrefix("ws://").removePrefix("wss://")
            .removePrefix("http://").removePrefix("https://")
            .removeSuffix("/ws").removeSuffix("/")

        val withPort = ensurePort(hostPort, DEFAULT_PORT)
        return "ws://$withPort$WS_PATH"
    }

    /**
     * 验证用户输入是否为有效的 Gateway 地址
     *
     * 宽松验证——只要能解析出 host 就行，不要求协议前缀。
     */
    /**
     * Validate user input as a valid Gateway address.
     *
     * Lenient: accepts bare host, host:port, IPv6 [::1], or full URLs.
     */
    fun isValidInput(input: String): Boolean {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return false

        val hostPart = trimmed
            .removePrefix("ws://").removePrefix("wss://")
            .removePrefix("http://").removePrefix("https://")
            .removeSuffix("/ws").removeSuffix("/")

        if (hostPart.isEmpty()) return false

        // IPv6 bracket notation: [::1] or [::1]:18789
        if (hostPart.startsWith("[")) {
            val closeBracket = hostPart.indexOf(']')
            if (closeBracket < 0) return false
            // Optional port after ]
            val afterBracket = hostPart.substring(closeBracket + 1)
            if (afterBracket.isNotEmpty()) {
                if (!afterBracket.startsWith(":")) return false
                val port = afterBracket.substring(1).toIntOrNull() ?: return false
                if (port !in 1..65535) return false
            }
            return true
        }

        // IPv4 or hostname
        val host = hostPart.substringBefore(":")
        if (host.isEmpty()) return false

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
     * Ensure host:port format, with IPv6 bracket support.
     *
     * IPv6 examples: [::1]:18789, [fe80::1%25eth0]:18789
     */
    private fun ensurePort(hostPort: String, defaultPort: Int): String {
        val clean = hostPort.removeSuffix("/ws").removeSuffix("/")

        // IPv6 bracket notation: [::1] or [::1]:port
        if (clean.startsWith("[")) {
            val closeBracket = clean.indexOf(']')
            if (closeBracket >= 0) {
                val afterBracket = clean.substring(closeBracket + 1)
                return if (afterBracket.startsWith(":")) clean
                else "$clean:$defaultPort"
            }
        }

        // IPv4 / hostname: check for exactly one colon (port separator)
        return if (clean.contains(":")) clean else "$clean:$defaultPort"
    }
}
