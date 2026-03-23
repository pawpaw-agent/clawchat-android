package com.openclaw.clawchat.ui.screens.session

import com.openclaw.clawchat.ui.state.MessageContentItem
import com.openclaw.clawchat.ui.state.MessageRole
import com.openclaw.clawchat.ui.state.MessageUi
import com.openclaw.clawchat.ui.state.ToolCard
import com.openclaw.clawchat.ui.state.ToolCardKind
import kotlinx.serialization.json.jsonPrimitive

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
 * 配对工具卡片
 * 将消息中的工具调用和结果配对
 */
fun pairToolCards(message: MessageUi): List<ToolCard> {
    val calls = message.getToolCalls()
    val results = message.getToolResults()
    
    if (calls.isEmpty() && results.isEmpty()) {
        val textContent = message.getTextContent()
        return if (textContent.isNotBlank()) {
            listOf(ToolCard(
                kind = ToolCardKind.RESULT,
                name = "output",
                args = null,
                result = textContent,
                isError = false,
                callId = null
            ))
        } else {
            emptyList()
        }
    }
    
    return calls.map { call ->
        val matchingResult = results.find { it.toolCallId == call.id }
        val displayArgs = if (call.name == "exec" && call.args != null) {
            call.args?.get("command")?.jsonPrimitive?.content ?: call.args.toString()
        } else {
            call.args?.toString()
        }
        
        ToolCard(
            kind = if (matchingResult != null) ToolCardKind.RESULT else ToolCardKind.CALL,
            name = call.name,
            args = displayArgs,
            result = matchingResult?.text,
            isError = matchingResult?.isError ?: false,
            callId = call.id
        )
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