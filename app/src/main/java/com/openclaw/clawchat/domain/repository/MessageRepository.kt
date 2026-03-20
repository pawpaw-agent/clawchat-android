package com.openclaw.clawchat.domain.repository

import com.openclaw.clawchat.domain.model.Message
import com.openclaw.clawchat.domain.model.MessageRole
import com.openclaw.clawchat.domain.model.MessageStatus
import kotlinx.coroutines.flow.Flow

/**
 * 消息仓库接口
 *
 * Domain 层 Repository 接口定义。
 * 遵循依赖倒置原则，上层依赖此接口而非具体实现。
 */
interface MessageRepository {

    /**
     * 获取会话消息流
     */
    fun observeMessages(sessionId: String): Flow<List<Message>>

    /**
     * 获取最新 N 条消息
     */
    suspend fun getLatestMessages(sessionId: String, limit: Int = 50): List<Message>

    /**
     * 保存消息
     */
    suspend fun saveMessage(
        sessionId: String,
        role: MessageRole,
        content: String,
        timestamp: Long = System.currentTimeMillis(),
        status: MessageStatus = MessageStatus.SENT,
        metadata: String? = null
    ): String

    /**
     * 批量保存消息
     */
    suspend fun saveMessages(messages: List<Message>)

    /**
     * 更新消息状态
     */
    suspend fun updateMessageStatus(messageId: String, status: MessageStatus)

    /**
     * 删除消息
     */
    suspend fun deleteMessage(messageId: String)

    /**
     * 删除会话的所有消息
     */
    suspend fun deleteSessionMessages(sessionId: String)

    /**
     * 清理旧消息
     */
    suspend fun cleanupOldMessages(daysToKeep: Int = 30): Int

    /**
     * 获取消息数量
     */
    suspend fun getMessageCount(sessionId: String): Int

    /**
     * 搜索消息
     */
    suspend fun searchMessages(query: String, limit: Int = 20): List<Message>
}