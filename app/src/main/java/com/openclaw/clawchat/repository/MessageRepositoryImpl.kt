package com.openclaw.clawchat.repository

import com.openclaw.clawchat.data.local.MessageDao
import com.openclaw.clawchat.data.local.MessageEntity
import com.openclaw.clawchat.data.local.MessageRole as LocalMessageRole
import com.openclaw.clawchat.data.local.MessageStatus as LocalMessageStatus
import com.openclaw.clawchat.domain.model.Message
import com.openclaw.clawchat.domain.model.MessageRole
import com.openclaw.clawchat.domain.model.MessageStatus
import com.openclaw.clawchat.domain.repository.MessageRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 消息仓库实现
 *
 * 实现 Domain 层的 MessageRepository 接口。
 * 处理消息数据的本地缓存。
 */
@Singleton
class MessageRepositoryImpl @Inject constructor(
    private val messageDao: MessageDao
) : MessageRepository {
    
    /**
     * 获取会话消息流
     */
    override fun observeMessages(sessionId: String): Flow<List<Message>> {
        return messageDao.getMessagesBySession(sessionId).map { entities ->
            entities.map { it.toMessage() }
        }
    }
    
    /**
     * 获取最新 N 条消息
     */
    override suspend fun getLatestMessages(sessionId: String, limit: Int): List<Message> {
        return messageDao.getLatestMessages(sessionId, limit).map { it.toMessage() }
    }
    
    /**
     * 保存消息
     */
    override suspend fun saveMessage(
        sessionId: String,
        role: MessageRole,
        content: String,
        timestamp: Long,
        status: MessageStatus,
        metadata: String?
    ): String {
        val message = MessageEntity(
            sessionId = sessionId,
            role = role.toLocalRole(),
            content = content,
            timestamp = timestamp,
            status = status.toLocalStatus(),
            metadata = metadata
        )
        val id = messageDao.insert(message)
        return id.toString()
    }
    
    /**
     * 批量保存消息
     */
    override suspend fun saveMessages(messages: List<Message>) {
        val entities = messages.map { msg ->
            MessageEntity(
                sessionId = msg.sessionId,
                role = msg.role.toLocalRole(),
                content = msg.content,
                timestamp = msg.timestamp,
                status = msg.status.toLocalStatus(),
                metadata = msg.metadata
            )
        }
        messageDao.insertAll(entities)
    }
    
    /**
     * 更新消息状态
     */
    override suspend fun updateMessageStatus(messageId: String, status: MessageStatus) {
        messageDao.updateStatusById(messageId.toLongOrNull() ?: 0L, status.toLocalStatus())
    }
    
    /**
     * 删除消息
     */
    override suspend fun deleteMessage(messageId: String) {
        messageDao.getById(messageId.toLongOrNull() ?: 0L)?.let {
            messageDao.delete(it)
        }
    }
    
    /**
     * 删除会话的所有消息
     */
    override suspend fun deleteSessionMessages(sessionId: String) {
        messageDao.deleteBySession(sessionId)
    }
    
    /**
     * 清理旧消息
     */
    override suspend fun cleanupOldMessages(daysToKeep: Int): Int {
        val cutoffTimestamp = System.currentTimeMillis() - (daysToKeep * 24 * 60 * 60 * 1000L)
        return messageDao.deleteOlderThan(cutoffTimestamp)
    }
    
    /**
     * 获取消息数量
     */
    override suspend fun getMessageCount(sessionId: String): Int {
        return messageDao.getCount(sessionId)
    }
    
    /**
     * 搜索消息
     */
    override suspend fun searchMessages(query: String, limit: Int): List<Message> {
        return messageDao.searchMessages("%$query%", limit).map { it.toMessage() }
    }
}

// ─────────────────────────────────────────────────────────────
// 映射函数
// ─────────────────────────────────────────────────────────────

private fun MessageEntity.toMessage(): Message {
    return Message(
        id = id.toString(),
        sessionId = sessionId,
        content = content,
        role = role.toDomainRole(),
        timestamp = timestamp,
        status = status.toDomainStatus(),
        metadata = metadata
    )
}

private fun MessageRole.toLocalRole(): LocalMessageRole = when (this) {
    MessageRole.USER -> LocalMessageRole.USER
    MessageRole.ASSISTANT -> LocalMessageRole.ASSISTANT
    MessageRole.SYSTEM -> LocalMessageRole.SYSTEM
}

private fun LocalMessageRole.toDomainRole(): MessageRole = when (this) {
    LocalMessageRole.USER -> MessageRole.USER
    LocalMessageRole.ASSISTANT -> MessageRole.ASSISTANT
    LocalMessageRole.SYSTEM -> MessageRole.SYSTEM
}

private fun MessageStatus.toLocalStatus(): LocalMessageStatus = when (this) {
    MessageStatus.PENDING -> LocalMessageStatus.PENDING
    MessageStatus.SENT -> LocalMessageStatus.SENT
    MessageStatus.DELIVERED -> LocalMessageStatus.DELIVERED
    MessageStatus.FAILED -> LocalMessageStatus.FAILED
}

private fun LocalMessageStatus.toDomainStatus(): MessageStatus = when (this) {
    LocalMessageStatus.PENDING -> MessageStatus.PENDING
    LocalMessageStatus.SENT -> MessageStatus.SENT
    LocalMessageStatus.DELIVERED -> MessageStatus.DELIVERED
    LocalMessageStatus.FAILED -> MessageStatus.FAILED
}