package com.openclaw.clawchat.util

/**
 * String 扩展函数
 */

/**
 * 判断是否为空白
 */
fun String?.isNullOrBlank(): Boolean = this == null || this.isBlank()

/**
 * 判断是否不为空白
 */
fun String?.isNotNullOrBlank(): Boolean = !isNullOrBlank()

/**
 * 截断到指定长度
 */
fun String.truncate(maxLength: Int, ellipsis: String = "..."): String {
    return if (length <= maxLength) this else take(maxLength - ellipsis.length) + ellipsis
}

/**
 * 移除协议前缀
 */
fun String.removeProtocol(): String {
    return removePrefix("http://")
        .removePrefix("https://")
        .removePrefix("ws://")
        .removePrefix("wss://")
}

/**
 * 添加协议前缀
 */
fun String.addProtocol(protocol: String = "https"): String {
    return if (startsWith("http://") || startsWith("https://") || startsWith("ws://") || startsWith("wss://")) {
        this
    } else {
        "$protocol://$this"
    }
}

/**
 * 提取 Base64（从 Data URL）
 */
fun String.extractBase64(): String {
    return if (contains(",")) substringAfter(",") else this
}

/**
 * 判断是否为有效的 URL
 */
fun String.isValidUrl(): Boolean {
    return startsWith("http://") || startsWith("https://") || startsWith("ws://") || startsWith("wss://")
}

/**
 * 首字母大写
 */
fun String.capitalize(): String {
    return replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() }
}

/**
 * 计算字数（中文=1，英文单词=1）
 */
fun String.countCharacters(): Int {
    if (isBlank()) return 0
    
    var count = 0
    var inWord = false
    
    forEach { char ->
        when {
            char.isChineseCharacter() -> count++
            char.isLetterOrDigit() -> {
                if (!inWord) {
                    count++
                    inWord = true
                }
            }
            else -> inWord = false
        }
    }
    
    return count
}

/**
 * 判断是否为中文字符
 */
private fun Char.isChineseCharacter(): Boolean {
    return code in 0x4E00..0x9FFF
}