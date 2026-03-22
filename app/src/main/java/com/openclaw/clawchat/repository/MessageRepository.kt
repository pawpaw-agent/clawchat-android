package com.openclaw.clawchat.repository

import com.openclaw.clawchat.ui.state.MessageUi
import com.openclaw.clawchat.ui.state.MessageRole
import kotlinx.coroutines.flow.Flow

/**
 * 消息仓库接口
 *
 * 本地缓存层，用于离线查看消息历史。
 * 实际消息数据来自 Gateway，这里只做缓存。
 */
interface MessageRepository {

    /**
     * 观察会话消息
     */
    fun observeMessages(sessionId: String): Flow<List<MessageUi>>

    /**
     * 获取最新消息
     */
    suspend fun getLatestMessages(sessionId: String, limit: Int): List<MessageUi>

    /**
     * 保存消息
     */
    suspend fun saveMessage(
        sessionId: String,
        role: MessageRole,
        content: String,
        timestamp: Long,
        toolCallId: String? = null,
        toolName: String? = null
    ): String

    /**
     * 删除会话消息
     */
    suspend fun deleteSessionMessages(sessionId: String)
    
    /**
     * 清空会话消息（别名）
     */
    suspend fun clearMessages(sessionId: String) = deleteSessionMessages(sessionId)

    /**
     * 搜索消息
     */
    suspend fun searchMessages(query: String, limit: Int): List<MessageUi>
}