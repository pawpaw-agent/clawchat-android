package com.openclaw.clawchat.util

import com.openclaw.clawchat.ui.state.MessageContentItem
import com.openclaw.clawchat.ui.state.MessageUi
import com.openclaw.clawchat.ui.state.MessageRole

/**
 * 统计工具类
 * 计算消息相关统计数据
 */
object StatsUtils {
    
    /**
     * 计算文本字数（中文字符算1个，英文单词算1个）
     */
    fun countCharacters(text: String): Int {
        if (text.isBlank()) return 0
        
        var count = 0
        var inWord = false
        
        text.forEach { char ->
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
     * 计算消息字数
     */
    fun countMessageCharacters(message: MessageUi): Int {
        return message.content
            .filterIsInstance<MessageContentItem.Text>()
            .sumOf { countCharacters(it.text) }
    }
    
    /**
     * 计算消息列表统计
     */
    fun calculateMessagesStats(messages: List<MessageUi>): MessagesStats {
        var totalUserChars = 0
        var totalAssistantChars = 0
        var userMessageCount = 0
        var assistantMessageCount = 0
        
        messages.forEach { message ->
            val chars = countMessageCharacters(message)
            when (message.role) {
                MessageRole.USER -> {
                    totalUserChars += chars
                    userMessageCount++
                }
                MessageRole.ASSISTANT -> {
                    totalAssistantChars += chars
                    assistantMessageCount++
                }
                else -> {}
            }
        }
        
        return MessagesStats(
            totalMessages = messages.size,
            userMessages = userMessageCount,
            assistantMessages = assistantMessageCount,
            userCharacters = totalUserChars,
            assistantCharacters = totalAssistantChars,
            totalCharacters = totalUserChars + totalAssistantChars
        )
    }
    
    /**
     * 扩展函数：判断是否为中文字符
     */
    private fun Char.isChineseCharacter(): Boolean {
        return this.code in 0x4E00..0x9FFF
    }
}

/**
 * 消息统计数据
 */
data class MessagesStats(
    val totalMessages: Int,
    val userMessages: Int,
    val assistantMessages: Int,
    val userCharacters: Int,
    val assistantCharacters: Int,
    val totalCharacters: Int
) {
    fun format(): String {
        return """
            📊 会话统计
            ━━━━━━━━━━━
            💬 总消息: $totalMessages 条
            👤 用户消息: $userMessages 条 ($userCharacters 字)
            🤖 AI 回复: $assistantMessages 条 ($assistantCharacters 字)
            📝 总字数: $totalCharacters 字
        """.trimIndent()
    }
}