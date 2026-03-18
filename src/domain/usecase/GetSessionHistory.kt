package com.openclaw.clawchat.domain.usecase

import com.openclaw.clawchat.domain.model.Message
import com.openclaw.clawchat.domain.repository.SessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * 获取会话历史用例
 * 
 * 处理获取会话消息历史的业务逻辑。
 * 支持分页加载和增量更新。
 * 
 * 设计原则：
 * - 单一职责：只处理获取消息历史的逻辑
 * - 响应式支持：提供 Flow 和一次性获取两种方式
 * - 性能优化：支持限制返回数量
 * 
 * @param sessionRepository 会话仓库
 */
class GetSessionHistory(
    private val sessionRepository: SessionRepository
) {
    /**
     * 执行获取会话历史操作（一次性）
     * 
     * @param sessionId 会话 ID
     * @param limit 最大返回数量（0 表示全部）
     * @return 消息列表
     */
    suspend operator fun invoke(
        sessionId: String,
        limit: Int = 0
    ): List<Message> {
        // 验证会话 ID
        require(sessionId.isNotBlank()) { "Session ID cannot be empty" }
        
        // 验证 limit
        require(limit >= 0) { "Limit must be non-negative" }
        
        return sessionRepository.getMessageHistory(sessionId, limit)
    }
    
    /**
     * 观察会话消息（响应式）
     * 
     * 返回一个 Flow，当消息列表变化时自动更新。
     * 
     * @param sessionId 会话 ID
     * @return 消息列表的 Flow
     */
    fun observeMessages(sessionId: String): Flow<List<Message>> {
        require(sessionId.isNotBlank()) { "Session ID cannot be empty" }
        
        return sessionRepository.observeMessages(sessionId)
    }
    
    /**
     * 获取最新消息
     * 
     * @param sessionId 会话 ID
     * @param count 获取数量
     * @return 最新消息列表（按时间倒序）
     */
    suspend fun getLatestMessages(sessionId: String, count: Int = 10): List<Message> {
        require(count > 0) { "Count must be positive" }
        
        val allMessages = invoke(sessionId)
        return allMessages.takeLast(count).reversed()
    }
    
    /**
     * 获取第一条消息
     * 
     * @param sessionId 会话 ID
     * @return 第一条消息，不存在返回 null
     */
    suspend fun getFirstMessage(sessionId: String): Message? {
        val messages = invoke(sessionId, limit = 1)
        return messages.firstOrNull()
    }
    
    /**
     * 获取最后一条消息
     * 
     * @param sessionId 会话 ID
     * @return 最后一条消息，不存在返回 null
     */
    suspend fun getLastMessage(sessionId: String): Message? {
        val messages = invoke(sessionId)
        return messages.lastOrNull()
    }
    
    /**
     * 搜索会话中的消息
     * 
     * @param sessionId 会话 ID
     * @param query 搜索关键词
     * @return 匹配的消息列表
     */
    suspend fun searchMessages(sessionId: String, query: String): List<Message> {
        require(query.isNotBlank()) { "Search query cannot be empty" }
        
        val allMessages = invoke(sessionId)
        val lowerQuery = query.lowercase()
        
        return allMessages.filter { message ->
            message.content.lowercase().contains(lowerQuery)
        }
    }
    
    /**
     * 获取会话统计信息
     * 
     * @param sessionId 会话 ID
     * @return 统计信息
     */
    suspend fun getSessionStats(sessionId: String): SessionStats {
        val messages = invoke(sessionId)
        
        val userMessages = messages.count { it.isUserMessage() }
        val assistantMessages = messages.count { it.isAssistantMessage() }
        val systemMessages = messages.count { it.isSystemMessage() }
        val totalAttachments = messages.sumOf { it.attachments.size }
        
        val firstMessageTime = messages.firstOrNull()?.timestamp
        val lastMessageTime = messages.lastOrNull()?.timestamp
        val duration = if (firstMessageTime != null && lastMessageTime != null) {
            lastMessageTime - firstMessageTime
        } else 0L
        
        return SessionStats(
            totalMessages = messages.size,
            userMessages = userMessages,
            assistantMessages = assistantMessages,
            systemMessages = systemMessages,
            totalAttachments = totalAttachments,
            durationMs = duration,
            firstMessageAt = firstMessageTime,
            lastMessageAt = lastMessageTime
        )
    }
}

/**
 * 会话统计信息
 */
data class SessionStats(
    val totalMessages: Int,
    val userMessages: Int,
    val assistantMessages: Int,
    val systemMessages: Int,
    val totalAttachments: Int,
    val durationMs: Long,
    val firstMessageAt: Long?,
    val lastMessageAt: Long?
) {
    /**
     * 获取平均响应时间（毫秒）
     */
    fun getAverageResponseTime(): Long {
        return if (userMessages > 0) durationMs / userMessages else 0
    }
    
    /**
     * 获取对话轮数（一次问答为一轮）
     */
    fun getConversationRounds(): Int {
        return minOf(userMessages, assistantMessages)
    }
}
