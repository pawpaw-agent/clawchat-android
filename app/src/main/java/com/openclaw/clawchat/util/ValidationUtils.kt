package com.openclaw.clawchat.util

/**
 * 验证工具类
 * 提供输入验证方法
 */
object ValidationUtils {
    
    // ─────────────────────────────────────────────────────────────
    // 验证正则
    // ─────────────────────────────────────────────────────────────
    
    private val EMAIL_REGEX = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
    private val PHONE_REGEX = Regex("^1[3-9]\\d{9}$")
    private val IP_REGEX = Regex("^((25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}(25[0-5]|2[0-4]\\d|[01]?\\d\\d?)$")
    private val PORT_REGEX = Regex("^[1-9]\\d{0,4}$")
    
    // ─────────────────────────────────────────────────────────────
    // 验证方法
    // ─────────────────────────────────────────────────────────────
    
    /**
     * 验证邮箱
     */
    fun isValidEmail(email: String?): Boolean {
        if (StringUtils.isBlank(email)) return false
        return EMAIL_REGEX.matches(email!!)
    }
    
    /**
     * 验证手机号（中国大陆）
     */
    fun isValidPhone(phone: String?): Boolean {
        if (StringUtils.isBlank(phone)) return false
        return PHONE_REGEX.matches(phone!!)
    }
    
    /**
     * 验证 IP 地址
     */
    fun isValidIp(ip: String?): Boolean {
        if (StringUtils.isBlank(ip)) return false
        return IP_REGEX.matches(ip!!)
    }
    
    /**
     * 验证端口号
     */
    fun isValidPort(port: String?): Boolean {
        if (StringUtils.isBlank(port)) return false
        if (!PORT_REGEX.matches(port!!)) return false
        val portNum = port.toIntOrNull() ?: return false
        return portNum in 1..65535
    }
    
    /**
     * 验证端口号
     */
    fun isValidPort(port: Int): Boolean {
        return port in 1..65535
    }
    
    /**
     * 验证主机地址（IP 或域名）
     */
    fun isValidHost(host: String?): Boolean {
        if (StringUtils.isBlank(host)) return false
        // IP 地址
        if (isValidIp(host)) return true
        // 域名
        return host!!.contains(".") && host.length <= 253
    }
    
    /**
     * 验证 Gateway 地址（host:port）
     */
    fun isValidGatewayAddress(address: String?): Boolean {
        if (StringUtils.isBlank(address)) return false
        val parts = address!!.split(":")
        if (parts.size != 2) return false
        return isValidHost(parts[0]) && isValidPort(parts[1])
    }
    
    /**
     * 验证 Token（非空）
     */
    fun isValidToken(token: String?): Boolean {
        return StringUtils.isNotBlank(token) && token!!.length >= 8
    }
    
    /**
     * 验证消息内容
     */
    fun isValidMessageContent(content: String?): Boolean {
        return StringUtils.isNotBlank(content)
    }
    
    /**
     * 验证会话名称
     */
    fun isValidSessionName(name: String?): Boolean {
        return StringUtils.isNotBlank(name) && name!!.length <= 100
    }
    
    /**
     * 验证文件大小
     */
    fun isValidFileSize(bytes: Long, maxSizeMB: Int = 10): Boolean {
        val maxSizeBytes = maxSizeMB * 1024L * 1024L
        return bytes > 0 && bytes <= maxSizeBytes
    }
}