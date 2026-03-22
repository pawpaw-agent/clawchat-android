package com.openclaw.clawchat.repository

import com.openclaw.clawchat.ui.state.MessageUi
import com.openclaw.clawchat.ui.state.MessageRole
import com.openclaw.clawchat.ui.state.MessageContentItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 消息仓库实现（内存存储）
 *
 * 消息数据来自 Gateway，这里只做临时缓存。
 * 应用重启后数据清空，由 Gateway 重新加载。
 */
@Singleton
class MessageRepositoryImpl @Inject constructor() : MessageRepository {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    
    // 按会话 ID 分组存储消息
    private val messagesBySession = MutableStateFlow<Map<String, MutableList<MessageUi>>>(emptyMap())

    /**
     * 观察会话消息
     */
    override fun observeMessages(sessionId: String): Flow<List<MessageUi>> {
        return messagesBySession.map { map ->
            map[sessionId]?.toList() ?: emptyList()
        }
    }

    /**
     * 获取最新消息
     */
    override suspend fun getLatestMessages(sessionId: String, limit: Int): List<MessageUi> {
        val messages = messagesBySession.value[sessionId] ?: return emptyList()
        return messages.takeLast(limit)
    }

    /**
     * 保存消息
     */
    override suspend fun saveMessage(
        sessionId: String,
        role: MessageRole,
        content: String,
        timestamp: Long,
        toolCallId: String? = null,
        toolName: String? = null
    ): String {
        val messageId = java.util.UUID.randomUUID().toString()
        
        // 解析内容
        val contentItems = parseContent(content)
        
        val message = MessageUi(
            id = messageId,
            content = contentItems,
            role = role,
            timestamp = timestamp,
            toolCallId = toolCallId,
            toolName = toolName
        )
        
        val map = messagesBySession.value.toMutableMap()
        val sessionMessages = map.getOrPut(sessionId) { mutableListOf() }
        sessionMessages.add(message)
        messagesBySession.value = map
        
        return messageId
    }

    /**
     * 删除会话消息
     */
    override suspend fun deleteSessionMessages(sessionId: String) {
        val map = messagesBySession.value.toMutableMap()
        map.remove(sessionId)
        messagesBySession.value = map
    }

    /**
     * 搜索消息
     */
    override suspend fun searchMessages(query: String, limit: Int): List<MessageUi> {
        val results = mutableListOf<MessageUi>()
        val lowerQuery = query.lowercase()
        
        for ((_, messages) in messagesBySession.value) {
            for (message in messages) {
                val textContent = message.content
                    .filterIsInstance<MessageContentItem.Text>()
                    .joinToString(" ") { it.text }
                    .lowercase()
                
                if (textContent.contains(lowerQuery)) {
                    results.add(message)
                    if (results.size >= limit) return results
                }
            }
        }
        
        return results
    }

    /**
     * 解析存储的 JSON 内容为 MessageContentItem 列表
     */
    private fun parseContent(content: String): List<MessageContentItem> {
        android.util.Log.d("MessageRepository", "=== parseContent: content=${content.take(200)}")
        return try {
            val array = json.parseToJsonElement(content) as? JsonArray
            android.util.Log.d("MessageRepository", "=== parseContent: array size=${array?.size}")
            array?.mapNotNull { element ->
                val obj = element as? JsonObject ?: return@mapNotNull null
                val type = obj["type"]?.jsonPrimitive?.content
                android.util.Log.d("MessageRepository", "=== parseContent: type=$type, keys=${obj.keys}")
                when (type) {
                    "text" -> MessageContentItem.Text(
                        text = obj["text"]?.jsonPrimitive?.content ?: ""
                    )
                    "tool_call", "tool_use", "toolCall", "toolUse" -> MessageContentItem.ToolCall(
                        id = obj["id"]?.jsonPrimitive?.content,
                        name = obj["name"]?.jsonPrimitive?.content ?: "unknown",
                        args = obj["arguments"]?.jsonObject ?: obj["args"]?.jsonObject
                    )
                    "tool_result", "toolResult" -> MessageContentItem.ToolResult(
                        toolCallId = obj["toolCallId"]?.jsonPrimitive?.content ?: obj["tool_call_id"]?.jsonPrimitive?.content,
                        name = obj["name"]?.jsonPrimitive?.content,
                        args = obj["args"]?.jsonObject,
                        text = obj["text"]?.jsonPrimitive?.content ?: "",
                        isError = obj["isError"]?.jsonPrimitive?.content?.toBoolean() ?: false
                    )
                    "image" -> MessageContentItem.Image(
                        url = obj["url"]?.jsonPrimitive?.content
                    )
                    else -> null // 跳过未知类型如 thinking
                }
            } ?: listOf(MessageContentItem.Text(content))
        } catch (e: Exception) {
            android.util.Log.w("MessageRepository", "=== parseContent error: ${e.message}")
            listOf(MessageContentItem.Text(content))
        }
    }
}