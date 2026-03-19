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
     * 确保 URL 以 /ws 结尾
     */
    private fun ensureWsPath(url: String): String {
        val base = url.removeSuffix("/")
        return if (base.endsWith(WS_PATH)) base else "$base$WS_PATH"
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

    /**
     * 猜测是否应该用 TLS
     *
     * 规则:
     * - .ts.net / .tailnet 域名 → TLS
     * - 局域网 IP (192.168.x.x, 10.x.x.x, 172.16-31.x.x) → TLS (默认安全)
     * - localhost → TLS
     * - 其他 → TLS (默认安全)
     */
    private fun looksLikeTlsHost(host: String): Boolean {
        val clean = host.substringBefore(":").substringBefore("/")
        
        // .ts.net / .tailnet 域名
        if (clean.endsWith(".ts.net") || clean.endsWith(".tailnet")) return true
        
        // localhost
        if (clean.equals("localhost", ignoreCase = true)) return true
        
        // 局域网 IP 默认用 TLS (安全优先)
        if (clean.matches(Regex("""192\.168\.\d+\.\d+"""))) return true
        if (clean.matches(Regex("""10\.\d+\.\d+\.\d+"""))) return true
        if (clean.matches(Regex("""172\.(1[6-9]|2\d|3[0-1])\.\d+\.\d+"""))) return true
        
        // 其他情况默认 TLS (安全优先)
        return true
    }
}
