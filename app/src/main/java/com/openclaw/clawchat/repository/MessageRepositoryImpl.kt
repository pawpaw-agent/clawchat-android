package com.openclaw.clawchat.repository

import com.openclaw.clawchat.data.local.ClawChatDatabase
import com.openclaw.clawchat.data.local.MessageDao
import com.openclaw.clawchat.data.local.MessageEntity
import com.openclaw.clawchat.data.local.SessionDao
import com.openclaw.clawchat.data.local.SessionEntity
import com.openclaw.clawchat.ui.state.MessageContentItem
import com.openclaw.clawchat.ui.state.MessageRole
import com.openclaw.clawchat.ui.state.MessageStatus
import com.openclaw.clawchat.ui.state.MessageUi
import com.openclaw.clawchat.ui.state.SessionStatus
import com.openclaw.clawchat.ui.state.SessionUi
import com.openclaw.clawchat.util.AppLog
import com.openclaw.clawchat.util.ContentParser
import com.openclaw.clawchat.util.JsonUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 消息仓库实现（Room 数据库持久化）
 *
 * 消息数据来自 Gateway，本地持久化支持离线查看。
 */
@Singleton
class MessageRepositoryImpl @Inject constructor(
    private val database: ClawChatDatabase
) : MessageRepository {

    private val json = JsonUtils.json
    private val messageDao: MessageDao get() = database.messageDao()

    companion object {
        private const val TAG = "MessageRepository"
        private const val MAX_MESSAGES_PER_SESSION = 500
        private const val TRIM_THRESHOLD = 400
    }

    /**
     * 观察会话消息
     */
    override fun observeMessages(sessionId: String): Flow<List<MessageUi>> {
        AppLog.d(TAG, "=== observeMessages called for $sessionId")
        return messageDao.observeMessages(sessionId).map { entities ->
            entities.map { it.toMessageUi() }
        }
    }

    /**
     * 获取最新消息
     */
    override suspend fun getLatestMessages(sessionId: String, limit: Int): List<MessageUi> {
        return messageDao.getLatestMessages(sessionId, limit).map { it.toMessageUi() }
    }

    /**
     * 保存消息
     */
    override suspend fun saveMessage(
        sessionId: String,
        role: MessageRole,
        content: String,
        timestamp: Long,
        toolCallId: String?,
        toolName: String?
    ): String {
        val messageId = java.util.UUID.randomUUID().toString()

        val entity = MessageEntity(
            id = messageId,
            sessionId = sessionId,
            role = role.name,
            content = content,
            timestamp = timestamp,
            toolCallId = toolCallId,
            toolName = toolName,
            status = MessageStatus.SENT.name
        )

        messageDao.insertMessage(entity)

        // 自动清理超出阈值的消息
        val count = messageDao.getMessageCount(sessionId)
        if (count > TRIM_THRESHOLD) {
            messageDao.trimOldMessages(sessionId, MAX_MESSAGES_PER_SESSION)
            AppLog.d(TAG, "Trimmed old messages, count was $count")
        }

        // 更新会话消息计数
        val sessionDao = database.sessionDao()
        sessionDao.updateMessageCount(sessionId, count, System.currentTimeMillis())

        return messageId
    }

    /**
     * 删除会话消息
     */
    override suspend fun deleteSessionMessages(sessionId: String) {
        messageDao.deleteSessionMessages(sessionId)
    }

    /**
     * 删除单条消息
     */
    override suspend fun deleteMessage(sessionId: String, messageId: String) {
        messageDao.deleteMessage(messageId)
        // 更新消息计数
        val count = messageDao.getMessageCount(sessionId)
        database.sessionDao().updateMessageCount(sessionId, count, System.currentTimeMillis())
    }

    /**
     * 清空会话消息（用于 /clear 命令）
     */
    override suspend fun clearMessages(sessionId: String) {
        messageDao.clearMessages(sessionId)
        database.sessionDao().updateMessageCount(sessionId, 0, System.currentTimeMillis())
    }

    /**
     * 搜索消息
     */
    override suspend fun searchMessages(query: String, limit: Int): List<MessageUi> {
        return messageDao.searchMessages(query, limit).map { it.toMessageUi() }
    }

    /**
     * 批量保存消息（用于加载历史）
     */
    override suspend fun saveMessages(sessionId: String, messages: List<MessageUi>) {
        val entities = messages.map { message ->
            val contentJson = json.encodeToString(
                kotlinx.serialization.json.JsonElement.serializer(),
                message.content.map { it.toJsonElement() }.let { JsonArray(it) }
            )
            MessageEntity(
                id = message.id,
                sessionId = sessionId,
                role = message.role.name,
                content = contentJson,
                timestamp = message.timestamp,
                toolCallId = message.toolCallId,
                toolName = message.toolName,
                status = message.status.name
            )
        }
        messageDao.insertMessages(entities)
    }

    /**
     * 原子操作：清空并批量保存消息（用于加载历史，避免 clear-then-save 之间的数据丢失）
     */
    override suspend fun clearAndSaveMessages(sessionId: String, messages: List<MessageUi>) {
        val entities = messages.map { message ->
            val contentJson = json.encodeToString(
                kotlinx.serialization.json.JsonElement.serializer(),
                message.content.map { it.toJsonElement() }.let { JsonArray(it) }
            )
            MessageEntity(
                id = message.id,
                sessionId = sessionId,
                role = message.role.name,
                content = contentJson,
                timestamp = message.timestamp,
                toolCallId = message.toolCallId,
                toolName = message.toolName,
                status = message.status.name
            )
        }
        messageDao.clearAndInsertMessages(sessionId, entities)

        // 更新会话消息计数
        val count = messageDao.getMessageCount(sessionId)
        database.sessionDao().updateMessageCount(sessionId, count, System.currentTimeMillis())
    }

    // ─────────────────────────────────────────────────────────────
    // 扩展函数
    // ─────────────────────────────────────────────────────────────

    /**
     * MessageEntity 转 MessageUi
     */
    private fun MessageEntity.toMessageUi(): MessageUi {
        return MessageUi(
            id = id,
            content = ContentParser.parseContent(content),
            role = MessageRole.fromString(role),
            timestamp = timestamp,
            toolCallId = toolCallId,
            toolName = toolName,
            status = try { MessageStatus.valueOf(status) } catch (e: Exception) { MessageStatus.SENT }
        )
    }

}

/**
 * MessageContentItem 转 JsonElement
 */
private fun MessageContentItem.toJsonElement(): JsonObject {
    return when (this) {
        is MessageContentItem.Text -> JsonObject(mapOf(
            "type" to kotlinx.serialization.json.JsonPrimitive("text"),
            "text" to kotlinx.serialization.json.JsonPrimitive(text)
        ))
        is MessageContentItem.ToolCall -> {
            val map = mutableMapOf<String, kotlinx.serialization.json.JsonElement>(
                "type" to kotlinx.serialization.json.JsonPrimitive("tool_call"),
                "name" to kotlinx.serialization.json.JsonPrimitive(name),
                "phase" to kotlinx.serialization.json.JsonPrimitive(phase)
            )
            id?.let { map["id"] = kotlinx.serialization.json.JsonPrimitive(it) }
            args?.let { map["args"] = it }
            JsonObject(map)
        }
        is MessageContentItem.ToolResult -> {
            val map = mutableMapOf<String, kotlinx.serialization.json.JsonElement>(
                "type" to kotlinx.serialization.json.JsonPrimitive("tool_result"),
                "text" to kotlinx.serialization.json.JsonPrimitive(text),
                "isError" to kotlinx.serialization.json.JsonPrimitive(isError)
            )
            toolCallId?.let { map["toolCallId"] = kotlinx.serialization.json.JsonPrimitive(it) }
            name?.let { map["name"] = kotlinx.serialization.json.JsonPrimitive(it) }
            args?.let { map["args"] = it }
            JsonObject(map)
        }
        is MessageContentItem.Image -> {
            val map = mutableMapOf<String, kotlinx.serialization.json.JsonElement>(
                "type" to kotlinx.serialization.json.JsonPrimitive("image")
            )
            url?.let { map["url"] = kotlinx.serialization.json.JsonPrimitive(it) }
            JsonObject(map)
        }
    }
}