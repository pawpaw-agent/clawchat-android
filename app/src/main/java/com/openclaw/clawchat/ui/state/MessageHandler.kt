package com.openclaw.clawchat.ui.state

import com.openclaw.clawchat.util.JsonUtils
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID

/**
 * 消息处理器（从 SessionViewModel 提取）
 * 职责：消息解析、内容提取、规范化
 * 无状态、无依赖，可单独测试
 */
object MessageHandler {
    
    
    
    /**
     * 格式化工具输出（截断长文本）
     */
    fun formatToolOutput(value: JsonElement?): String? {
        if (value == null) return null
        
        return when (value) {
            is JsonPrimitive -> {
                val content = value.content.trim()
                if (content.isNotBlank() && content.length > 120000) {
                    "${content.take(120000)}\n\n… truncated (${content.length} chars, showing first 120000)."
                } else if (content.isNotBlank()) {
                    content
                } else null
            }
            is JsonObject -> {
                val text = extractText(value["content"] ?: value)
                if (!text.isNullOrBlank()) {
                    if (text.length > 120000) {
                        "${text.take(120000)}\n\n… truncated (${text.length} chars, showing first 120000)."
                    } else text
                } else {
                    try { JsonUtils.json.encodeToString(JsonObject.serializer(), value) } catch (_: Exception) { null }
                }
            }
            is JsonArray -> {
                val text = extractText(value)
                if (!text.isNullOrBlank()) text else null
            }
            else -> null
        }
    }

    /**
     * 从 JsonElement 中提取文本
     */
    fun extractText(content: JsonElement?): String? {
        if (content == null) return null
        
        return when (content) {
            is JsonPrimitive -> content.content.trim().takeIf { it.isNotBlank() }
            is JsonArray -> {
                content.mapNotNull { item ->
                    if (item is JsonObject) {
                        val type = item["type"]?.jsonPrimitive?.content
                        if (type == "text") item["text"]?.jsonPrimitive?.content
                        else null
                    } else null
                }.joinToString("\n").trim().takeIf { it.isNotBlank() }
            }
            is JsonObject -> {
                if (content.containsKey("text")) {
                    content["text"]?.jsonPrimitive?.content?.trim()?.takeIf { it.isNotBlank() }
                } else null
            }
            else -> null
        }
    }

    /**
     * 规范化最终助手消息
     */
    fun normalizeFinalAssistantMessage(msgObj: JsonObject?): MessageUi? {
        if (msgObj == null) return null
        
        val role = msgObj["role"]?.jsonPrimitive?.content?.lowercase() ?: "assistant"
        if (role != "assistant") return null
        
        val content = parseContent(msgObj["content"])
        if (content.isEmpty()) {
            // 允许只有 text 字段
            val text = msgObj["text"]?.jsonPrimitive?.content ?: return null
            return MessageUi(
                id = msgObj["id"]?.jsonPrimitive?.content ?: UUID.randomUUID().toString(),
                content = listOf(MessageContentItem.Text(text)),
                role = MessageRole.ASSISTANT,
                timestamp = msgObj["timestamp"]?.jsonPrimitive?.content?.toLongOrNull() ?: System.currentTimeMillis()
            )
        }
        
        return MessageUi(
            id = msgObj["id"]?.jsonPrimitive?.content ?: UUID.randomUUID().toString(),
            content = content,
            role = MessageRole.ASSISTANT,
            timestamp = msgObj["timestamp"]?.jsonPrimitive?.content?.toLongOrNull() ?: System.currentTimeMillis()
        )
    }

    /**
     * 解析内容为 MessageContentItem 列表
     */
    fun parseContent(content: JsonElement?): List<MessageContentItem> {
        if (content == null) return emptyList()
        
        return when (content) {
            is JsonPrimitive -> listOf(MessageContentItem.Text(content.content))
            is JsonArray -> content.mapNotNull { part ->
                if (part is JsonObject) {
                    val type = part["type"]?.jsonPrimitive?.content?.lowercase()?.replace("_", "")
                    when (type) {
                        "text" -> MessageContentItem.Text(part["text"]?.jsonPrimitive?.content ?: "")
                        "toolcall", "tooluse" -> MessageContentItem.ToolCall(
                            id = part["id"]?.jsonPrimitive?.content,
                            name = part["name"]?.jsonPrimitive?.content ?: "tool",
                            args = part["arguments"]?.jsonObject ?: part["args"]?.jsonObject
                        )
                        "toolresult" -> MessageContentItem.ToolResult(
                            toolCallId = part["toolCallId"]?.jsonPrimitive?.content ?: part["tool_call_id"]?.jsonPrimitive?.content,
                            name = part["name"]?.jsonPrimitive?.content,
                            text = part["text"]?.jsonPrimitive?.content ?: ""
                        )
                        "image", "imageurl" -> MessageContentItem.Image(
                            url = part["url"]?.jsonPrimitive?.content ?: part["imageUrl"]?.jsonPrimitive?.content
                        )
                        else -> null
                    }
                } else if (part is JsonPrimitive) {
                    MessageContentItem.Text(part.content)
                } else null
            }
            else -> emptyList()
        }
    }

    /**
     * 检测是否是静默回复流
     */
    fun isSilentReplyStream(text: String): Boolean {
        return text.trim().matches(Regex("^\\s*NO_REPLY\\s*$", RegexOption.IGNORE_CASE))
    }

    /**
     * 检测是否是助手静默回复
     */
    fun isAssistantSilentReply(message: MessageUi): Boolean {
        if (message.role != MessageRole.ASSISTANT) return false
        val text = message.getTextContent()
        return isSilentReplyStream(text)
    }
}