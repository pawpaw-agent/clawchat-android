package com.openclaw.clawchat.ui.screens.session

import com.openclaw.clawchat.ui.state.MessageContentItem
import com.openclaw.clawchat.ui.state.MessageRole
import com.openclaw.clawchat.ui.state.MessageUi

/**
 * 消息分组逻辑
 * 对应 WebChat groupMessages
 */
data class MessageGroup(
    val role: MessageRole,
    val messages: List<MessageUi>,
    val isStreaming: Boolean = false
) {
    val lastMessage: MessageUi? get() = messages.lastOrNull()
    val firstMessage: MessageUi? get() = messages.firstOrNull()
}

/**
 * 将消息按角色分组（Slack 风格）
 * 相邻的同角色消息合并为一组
 */
fun groupMessages(messages: List<MessageUi>): List<MessageGroup> {
    if (messages.isEmpty()) return emptyList()
    
    val groups = mutableListOf<MessageGroup>()
    var currentRole = messages.first().role
    var currentMessages = mutableListOf<MessageUi>()
    
    messages.forEach { message ->
        if (message.role == currentRole) {
            currentMessages.add(message)
        } else {
            // 保存当前组
            groups.add(MessageGroup(
                role = currentRole,
                messages = currentMessages.toList()
            ))
            // 开始新组
            currentRole = message.role
            currentMessages = mutableListOf(message)
        }
    }
    
    // 保存最后一组
    if (currentMessages.isNotEmpty()) {
        groups.add(MessageGroup(
            role = currentRole,
            messages = currentMessages.toList()
        ))
    }
    
    return groups
}

/**
 * 格式化时间戳
 */
fun formatTimestamp(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp

    return when {
        diff < 60_000 -> "刚刚"
        diff < 3600_000 -> "${diff / 60_000}分钟前"
        diff < 86400_000 -> "${diff / 3600_000}小时前"
        else -> {
            val sdf = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
            sdf.format(java.util.Date(timestamp))
        }
    }
}

/**
 * 格式化消息为 Markdown
 */
fun formatMessageAsMarkdown(message: MessageUi): String {
    val sb = StringBuilder()
    
    val roleLabel = when (message.role) {
        MessageRole.USER -> "**User:**"
        MessageRole.ASSISTANT -> "**Assistant:**"
        MessageRole.SYSTEM -> "**System:**"
        MessageRole.TOOL -> "**Tool:**"
    }
    sb.appendLine(roleLabel)
    sb.appendLine()
    
    message.content.forEach { item ->
        when (item) {
            is MessageContentItem.Text -> {
                sb.appendLine(item.text)
            }
            is MessageContentItem.Image -> {
                sb.appendLine("![image](data:${item.mimeType ?: "image/png"};base64,${item.base64 ?: ""})")
            }
            is MessageContentItem.ToolCall -> {
                sb.appendLine("```json")
                sb.appendLine("// Tool: ${item.name}")
                item.args?.let { sb.appendLine(it.toString()) }
                sb.appendLine("```")
            }
            is MessageContentItem.ToolResult -> {
                sb.appendLine("```")
                sb.appendLine(item.text)
                sb.appendLine("```")
            }
        }
    }
    
    return sb.toString().trim()
}