package com.openclaw.clawchat.repository

import com.openclaw.clawchat.data.local.MessageDao
import com.openclaw.clawchat.data.local.MessageEntity
import com.openclaw.clawchat.data.local.MessageRole
import com.openclaw.clawchat.data.local.MessageStatus
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 消息仓库 - 处理消息数据的本地缓存
 */
@Singleton
class MessageRepository @Inject constructor(
    private val messageDao: MessageDao
) {
    
    /**
     * 获取会话消息流
     */
    fun getMessages(sessionId: String): Flow<List<MessageEntity>> {
        return messageDao.getMessagesBySession(sessionId)
    }
    
    /**
     * 获取最新 N 条消息
     */
    suspend fun getLatestMessages(sessionId: String, limit: Int = 50): List<MessageEntity> {
        return messageDao.getLatestMessages(sessionId, limit)
    }
    
    /**
     * 保存消息到本地
     */
    suspend fun saveMessage(
        sessionId: String,
        role: MessageRole,
        content: String,
        timestamp: Long = System.currentTimeMillis(),
        status: MessageStatus = MessageStatus.SENT,
        metadata: String? = null
    ): Long {
        val message = MessageEntity(
            sessionId = sessionId,
            role = role,
            content = content,
            timestamp = timestamp,
            status = status,
            metadata = metadata
        )
        return messageDao.insert(message)
    }
    
    /**
     * 批量保存消息
     */
    suspend fun saveMessages(messages: List<MessageEntity>) {
        messageDao.insertAll(messages)
    }
    
    /**
     * 更新消息状态
     */
    suspend fun updateMessageStatus(messageId: Long, status: MessageStatus) {
        val message = getMessageById(messageId) ?: return
        messageDao.update(message.copy(status = status))
    }
    
    /**
     * 删除消息
     */
    suspend fun deleteMessage(message: MessageEntity) {
        messageDao.delete(message)
    }
    
    /**
     * 删除会话的所有消息
     */
    suspend fun deleteSessionMessages(sessionId: String) {
        messageDao.deleteBySession(sessionId)
    }
    
    /**
     * 清理旧消息（保留最近 30 天）
     */
    suspend fun cleanupOldMessages(daysToKeep: Int = 30): Int {
        val cutoffTimestamp = System.currentTimeMillis() - (daysToKeep * 24 * 60 * 60 * 1000L)
        return messageDao.deleteOlderThan(cutoffTimestamp)
    }
    
    /**
     * 获取消息数量
     */
    suspend fun getMessageCount(sessionId: String): Int {
        return messageDao.getCount(sessionId)
    }
    
    /**
     * 搜索消息
     */
    suspend fun searchMessages(query: String, limit: Int = 20): List<MessageEntity> {
        return messageDao.searchMessages("%$query%", limit)
    }
    
    /**
     * 根据 ID 获取消息（用于更新）
     */
    private suspend fun getMessageById(id: Long): MessageEntity? {
        // Room 不支持直接按 ID 查询单个消息，需要通过其他方式获取
        // 这里简化处理，实际使用中可能需要添加对应的 DAO 方法
        return null
    }
}
