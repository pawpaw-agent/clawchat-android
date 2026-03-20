package com.openclaw.clawchat.repository

import com.openclaw.clawchat.data.local.MessageDao
import com.openclaw.clawchat.data.local.MessageEntity
import com.openclaw.clawchat.data.local.MessageRole as LocalMessageRole
import com.openclaw.clawchat.ui.state.MessageUi
import com.openclaw.clawchat.ui.state.MessageRole
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
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

    /**
     * 观察会话消息
     */
    override fun observeMessages(sessionId: String): Flow<List<MessageUi>> {
        return messageDao.getMessagesBySession(sessionId).map { entities ->
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
        return messageDao.searchMessages("%$query%", limit).map { it.toMessageUi() }
    }
}

// ─────────────────────────────────────────────────────────────
// 映射函数
// ─────────────────────────────────────────────────────────────

private fun MessageEntity.toMessageUi(): MessageUi {
    return MessageUi(
        id = id.toString(),
        content = content,
        role = role.toUiRole(),
        timestamp = timestamp
    )
}

private fun MessageRole.toLocalRole(): LocalMessageRole = when (this) {
    MessageRole.USER -> LocalMessageRole.USER
    MessageRole.ASSISTANT -> LocalMessageRole.ASSISTANT
    MessageRole.SYSTEM -> LocalMessageRole.SYSTEM
}

private fun LocalMessageRole.toUiRole(): MessageRole = when (this) {
    LocalMessageRole.USER -> MessageRole.USER
    LocalMessageRole.ASSISTANT -> MessageRole.ASSISTANT
    LocalMessageRole.SYSTEM -> MessageRole.SYSTEM
}