package com.openclaw.clawchat.util

import com.openclaw.clawchat.ui.state.MessageContentItem
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Shared content parser — canonical implementation for parsing Gateway
 * content arrays into [MessageContentItem] lists.
 *
 * Merges the previously duplicated implementations from:
 * - MessageRepositoryImpl.parseContent()
 * - SessionMessageLoader.parseContentArray()
 */
object ContentParser {

    private val json = JsonUtils.json

    /**
     * Parse a JSON string representation of a content array.
     */
    fun parseContent(content: String): List<MessageContentItem> {
        return try {
            val element = json.parseToJsonElement(content)
            parseContentElement(element)
        } catch (e: Exception) {
            listOf(MessageContentItem.Text(content))
        }
    }

    /**
     * Parse a [JsonElement] (array or primitive) into [MessageContentItem] list.
     *
     * Gateway content format:
     * ```
     * [
     *   {"type":"text","text":"..."},
     *   {"type":"thinking","thinking":"..."},
     *   {"type":"tool_call","id":"...","name":"...","arguments":{...}},
     *   {"type":"tool_result","toolCallId":"...","text":"..."},
     *   {"type":"image","url":"..."}
     * ]
     * ```
     */
    fun parseContentElement(element: JsonElement?): List<MessageContentItem> {
        val items = mutableListOf<MessageContentItem>()

        // Plain text fallback
        if (element is JsonPrimitive) {
            val text = element.content
            if (text.isNotBlank()) {
                items.add(MessageContentItem.Text(text))
            }
            return finalize(items)
        }

        val array = element?.jsonArray ?: return finalize(items)

        array.forEach { item ->
            try {
                val obj = item.jsonObject
                val type = obj["type"]?.jsonPrimitive?.content ?: "text"

                when (type) {
                    "text" -> {
                        obj["text"]?.jsonPrimitive?.content?.let { text ->
                            if (text.isNotBlank()) {
                                items.add(MessageContentItem.Text(text))
                            }
                        }
                    }
                    "thinking" -> {
                        obj["thinking"]?.jsonPrimitive?.content?.let { thinking ->
                            if (thinking.isNotBlank()) {
                                items.add(MessageContentItem.Text(thinking))
                            }
                        }
                    }
                    "tool_call", "toolCall", "tool_use", "toolUse" -> {
                        items.add(MessageContentItem.ToolCall(
                            id = obj["id"]?.jsonPrimitive?.content,
                            name = obj["name"]?.jsonPrimitive?.content ?: "unknown",
                            args = obj["arguments"]?.jsonObject ?: obj["args"]?.jsonObject,
                            phase = obj["phase"]?.jsonPrimitive?.content ?: "result"
                        ))
                    }
                    "tool_result", "toolResult" -> {
                        items.add(MessageContentItem.ToolResult(
                            toolCallId = obj["toolCallId"]?.jsonPrimitive?.content
                                ?: obj["tool_call_id"]?.jsonPrimitive?.content,
                            name = obj["name"]?.jsonPrimitive?.content,
                            args = obj["args"]?.jsonObject,
                            text = obj["text"]?.jsonPrimitive?.content ?: "",
                            isError = obj["isError"]?.jsonPrimitive?.content?.toBoolean() ?: false
                        ))
                    }
                    "image" -> {
                        items.add(MessageContentItem.Image(
                            url = obj["url"]?.jsonPrimitive?.content,
                            base64 = obj["data"]?.jsonPrimitive?.content,
                            mimeType = obj["mimeType"]?.jsonPrimitive?.content
                        ))
                    }
                    else -> {
                        // Unknown type — try text fallback
                        val text = obj["text"]?.jsonPrimitive?.content ?: item.toString()
                        if (text.isNotBlank()) {
                            items.add(MessageContentItem.Text(text))
                        }
                    }
                }
            } catch (e: Exception) {
                AppLog.w("ContentParser", "Failed to parse content element: ${e.message}")
            }
        }

        return finalize(items)
    }

    private fun finalize(items: MutableList<MessageContentItem>): List<MessageContentItem> {
        if (items.isEmpty()) {
            items.add(MessageContentItem.Text(""))
        }
        return items
    }
}
