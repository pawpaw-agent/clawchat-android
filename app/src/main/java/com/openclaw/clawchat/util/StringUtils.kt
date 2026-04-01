package com.openclaw.clawchat.util

import java.util.Locale

/**
 * 字符串工具类
 * 提供字符串处理方法
 */
object StringUtils {
    
    /**
     * 判断字符串是否为空白
     */
    fun isBlank(str: String?): Boolean {
        return str.isNullOrBlank()
    }
    
    /**
     * 判断字符串是否不为空白
     */
    fun isNotBlank(str: String?): Boolean {
        return !isBlank(str)
    }
    
    /**
     * 截断字符串
     * @param str 原字符串
     * @param maxLength 最大长度
     * @param ellipsis 省略号，默认 "..."
     * @return 截断后的字符串
     */
    fun truncate(str: String?, maxLength: Int, ellipsis: String = "..."): String {
        if (str == null) return ""
        if (str.length <= maxLength) return str
        return str.take(maxLength - ellipsis.length) + ellipsis
    }
    
    /**
     * 提取 Base64 数据（从 data URL 中）
     * @param dataUrl data URL 格式字符串
     * @return Base64 数据部分
     */
    fun extractBase64FromDataUrl(dataUrl: String): String {
        return if (dataUrl.contains(",")) {
            dataUrl.substringAfter(",")
        } else {
            dataUrl
        }
    }
    
    /**
     * 判断是否为有效的 URL
     */
    fun isValidUrl(str: String?): Boolean {
        if (isBlank(str)) return false
        return str!!.startsWith("http://") || str.startsWith("https://") || str.startsWith("ws://") || str.startsWith("wss://")
    }
    
    /**
     * 判断是否为有效的 JSON
     */
    fun isValidJson(str: String?): Boolean {
        if (isBlank(str)) return false
        return try {
            str!!.trim().let { it.startsWith("{") && it.endsWith("}") || it.startsWith("[") && it.endsWith("]") }
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 首字母大写
     */
    fun capitalize(str: String?): String {
        if (isBlank(str)) return ""
        return str!!.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    }
    
    /**
     * 移除 URL 协议前缀
     */
    fun removeProtocol(url: String?): String {
        if (isBlank(url)) return ""
        return url!!
            .removePrefix("http://")
            .removePrefix("https://")
            .removePrefix("ws://")
            .removePrefix("wss://")
    }
    
    /**
     * 添加 URL 协议前缀
     */
    fun addProtocol(url: String?, protocol: String = "https"): String {
        if (isBlank(url)) return ""
        val trimmed = url!!.trim()
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://") || 
                   trimmed.startsWith("ws://") || trimmed.startsWith("wss://")) {
            trimmed
        } else {
            "$protocol://$trimmed"
        }
    }
    
    /**
     * 格式化文件大小
     */
    fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024))
            else -> String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024))
        }
    }
}