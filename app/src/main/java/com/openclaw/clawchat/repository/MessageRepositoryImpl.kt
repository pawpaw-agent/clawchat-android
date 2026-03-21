package com.openclaw.clawchat.repository

import com.openclaw.clawchat.data.local.MessageDao
import com.openclaw.clawchat.data.local.MessageEntity
import com.openclaw.clawchat.data.local.MessageRole as LocalMessageRole
import com.openclaw.clawchat.ui.state.MessageUi
import com.openclaw.clawchat.ui.state.MessageRole
import com.openclaw.clawchat.ui.state.MessageContentItem
import kotlinx.coroutines.flow.Flow
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
 * 消息仓库实现
 *
 * 本地缓存层，用于离线查看消息历史。
 * 实际消息数据来自 Gateway，这里只做缓存。
 */
@Singleton
class MessageRepositoryImpl @Inject constructor(
    private val messageDao: MessageDao
) : MessageRepository {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * 观察会话消息
     */
    override fun observeMessages(sessionId: String): Flow<List<MessageUi>> {
        return messageDao.getMessagesBySession(sessionId).map { entities ->
            entities.map { it.toMessageUi(json) }
        }
    }

    /**
     * 获取最新消息
     */
    override suspend fun getLatestMessages(sessionId: String, limit: Int): List<MessageUi> {
        return messageDao.getLatestMessages(sessionId, limit).map { it.toMessageUi(json) }
    }

    /**
     * 保存消息
     */
    override suspend fun saveMessage(
        sessionId: String,
        role: MessageRole,
        content: String,
        timestamp: Long
    ): String {
        val message = MessageEntity(
            sessionId = sessionId,
            role = role.toLocalRole(),
            content = content,
            timestamp = timestamp,
            status = com.openclaw.clawchat.data.local.MessageStatus.SENT
        )
        val id = messageDao.insert(message)
        return id.toString()
    }

    /**
     * 删除会话消息
     */
    override suspend fun deleteSessionMessages(sessionId: String) {
        messageDao.deleteBySession(sessionId)
    }

    /**
     * 搜索消息
     */
    override suspend fun searchMessages(query: String, limit: Int): List<MessageUi> {
        return messageDao.searchMessages("%$query%", limit).map { it.toMessageUi(json) }
    }
}

// ─────────────────────────────────────────────────────────────
// 映射函数
// ─────────────────────────────────────────────────────────────

private fun MessageEntity.toMessageUi(json: Json): MessageUi {
    return MessageUi(
        id = id.toString(),
        content = parseContent(json, content),
        role = role.toUiRole(),
        timestamp = timestamp
    )
}

/**
 * 解析存储的 JSON 内容为 MessageContentItem 列表
 */
private fun parseContent(json: Json, content: String): List<MessageContentItem> {
    return try {
        // 尝试解析为数组
        val array = json.parseToJsonElement(content) as? JsonArray
        array?.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val type = obj["type"]?.jsonPrimitive?.content
            when (type) {
                "text" -> MessageContentItem.Text(
                    text = obj["text"]?.jsonPrimitive?.content ?: ""
                )
                "tool_call" -> MessageContentItem.ToolCall(
                    id = obj["id"]?.jsonPrimitive?.content,
                    name = obj["name"]?.jsonPrimitive?.content ?: "unknown"
                )
                "tool_result" -> MessageContentItem.ToolResult(
                    toolCallId = obj["toolCallId"]?.jsonPrimitive?.content,
                    name = obj["name"]?.jsonPrimitive?.content,
                    text = obj["text"]?.jsonPrimitive?.content ?: "",
                    isError = obj["isError"]?.jsonPrimitive?.content?.toBoolean() ?: false
                )
                "image" -> MessageContentItem.Image(
                    url = obj["url"]?.jsonPrimitive?.content
                )
                else -> null
            }
        } ?: listOf(MessageContentItem.Text(content))
    } catch (e: Exception) {
        // 如果解析失败，作为纯文本处理
        listOf(MessageContentItem.Text(content))
    }
}

private fun MessageRole.toLocalRole(): LocalMessageRole = when (this) {
    MessageRole.USER -> LocalMessageRole.USER
    MessageRole.ASSISTANT -> LocalMessageRole.ASSISTANT
    MessageRole.SYSTEM -> LocalMessageRole.SYSTEM
    MessageRole.TOOL -> LocalMessageRole.TOOL
}

private fun LocalMessageRole.toUiRole(): MessageRole = when (this) {
    LocalMessageRole.USER -> MessageRole.USER
    LocalMessageRole.ASSISTANT -> MessageRole.ASSISTANT
    LocalMessageRole.SYSTEM -> MessageRole.SYSTEM
    LocalMessageRole.TOOL -> MessageRole.TOOL
}