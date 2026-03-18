package com.openclaw.clawchat.domain.usecase

import com.openclaw.clawchat.domain.model.Message
import com.openclaw.clawchat.domain.repository.SessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 接收消息用例
 * 
 * 处理接收和监听新消息的业务逻辑。
 * 提供消息流的观察和过滤功能。
 * 
 * 设计原则：
 * - 单一职责：只处理接收消息的逻辑
 * - 响应式：使用 Flow 提供实时消息流
 * - 消息过滤：支持按角色、会话过滤消息
 * 
 * @param sessionRepository 会话仓库
 */
class ReceiveMessage(
    private val sessionRepository: SessionRepository
) {
    /**
     * 观察指定会话的消息
     * 
     * @param sessionId 会话 ID
     * @return 消息列表的 Flow
     */
    fun observeMessages(sessionId: String): Flow<List<Message>> {
        require(sessionId.isNotBlank()) { "Session ID cannot be empty" }
        
        return sessionRepository.observeMessages(sessionId)
    }
    
    /**
     * 观察所有会话的新消息
     * 
     * 返回一个 Flow，发射最新的消息（按会话分组）。
     * 
     * @return 消息流的 Flow
     */
    fun observeAllMessages(): Flow<Map<String, List<Message>>> {
        // 获取所有会话
        return sessionRepository.observeSessions()
            .map { sessions ->
                // 为每个会话获取消息
                sessions.associate { session ->
                    session.id to sessionRepository.getMessageHistory(session.id)
                }
            }
    }
    
    /**
     * 观察最新消息
     * 
     * 监听所有会话中的最新消息。
     * 
     * @return 最新消息的 Flow（可能为 null）
     */
    fun observeLatestMessage(): Flow<Message?> {
        return observeAllMessages()
            .map { sessionMessages ->
                sessionMessages.values
                    .flatten()
                    .sortedByDescending { it.timestamp }
                    .firstOrNull()
            }
    }
    
    /**
     * 过滤助手消息
     * 
     * @param sessionId 会话 ID
     * @return 助手消息的 Flow
     */
    fun observeAssistantMessages(sessionId: String): Flow<List<Message>> {
        return observeMessages(sessionId)
            .map { messages ->
                messages.filter { it.isAssistantMessage() }
            }
    }
    
    /**
     * 过滤用户消息
     * 
     * @param sessionId 会话 ID
     * @return 用户消息的 Flow
     */
    fun observeUserMessages(sessionId: String): Flow<List<Message>> {
        return observeMessages(sessionId)
            .map { messages ->
                messages.filter { it.isUserMessage() }
            }
    }
    
    /**
     * 过滤系统消息
     * 
     * @param sessionId 会话 ID
     * @return 系统消息的 Flow
     */
    fun observeSystemMessages(sessionId: String): Flow<List<Message>> {
        return observeMessages(sessionId)
            .map { messages ->
                messages.filter { it.isSystemMessage() }
            }
    }
    
    /**
     * 获取未读消息数量
     * 
     * @param sessionId 会话 ID
     * @param lastReadTimestamp 最后已读消息时间戳
     * @return 未读消息数量
     */
    suspend fun getUnreadCount(
        sessionId: String,
        lastReadTimestamp: Long
    ): Int {
        val messages = sessionRepository.getMessageHistory(sessionId)
        return messages.count { it.timestamp > lastReadTimestamp }
    }
    
    /**
     * 检查会话是否有新消息
     * 
     * @param sessionId 会话 ID
     * @param sinceTimestamp 检查此时间之后的消息
     * @return 是否有新消息
     */
    suspend fun hasNewMessages(
        sessionId: String,
        sinceTimestamp: Long
    ): Boolean {
        val messages = sessionRepository.getMessageHistory(sessionId, limit = 1)
        return messages.firstOrNull()?.timestamp?.let { it > sinceTimestamp } ?: false
    }
}
