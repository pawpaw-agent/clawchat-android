package com.openclaw.clawchat.repository

import com.openclaw.clawchat.data.local.ClawChatDatabase
import com.openclaw.clawchat.data.local.PendingMessageDao
import com.openclaw.clawchat.data.local.PendingMessageEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 待发送消息仓库
 * 管理离线消息队列，支持自动重试
 */
@Singleton
class PendingMessageRepository @Inject constructor(
    private val database: ClawChatDatabase
) {
    private val dao: PendingMessageDao get() = database.pendingMessageDao()
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 观察会话的待发送消息
     */
    fun observePendingMessages(sessionId: String): Flow<List<PendingMessageEntity>> {
        return dao.observePendingMessages(sessionId)
    }

    /**
     * 获取所有待发送消息
     */
    suspend fun getAllPendingMessages(): List<PendingMessageEntity> {
        return dao.getAllPendingMessages()
    }

    /**
     * 添加待发送消息
     */
    suspend fun addPendingMessage(
        sessionId: String,
        text: String,
        attachments: List<String>? = null
    ): Long {
        val attachmentsJson = attachments?.takeIf { it.isNotEmpty() }?.let {
            // 简单的 JSON 数组格式
            "[${it.joinToString(",") { "\"$it\"" }}]"
        }

        return dao.insertPendingMessage(
            PendingMessageEntity(
                sessionId = sessionId,
                text = text,
                attachments = attachmentsJson
            )
        )
    }

    /**
     * 更新重试状态
     */
    suspend fun updateRetryCount(id: Long, retryCount: Int, error: String?) {
        dao.updateRetryCount(id, retryCount, error)
    }

    /**
     * 删除待发送消息（发送成功后）
     */
    suspend fun removePendingMessage(message: PendingMessageEntity) {
        dao.deletePendingMessage(message)
    }

    /**
     * 清除会话的待发送消息
     */
    suspend fun clearPendingMessages(sessionId: String) {
        dao.clearPendingMessages(sessionId)
    }

    /**
     * 清除所有待发送消息
     */
    suspend fun clearAllPendingMessages() {
        dao.clearAllPendingMessages()
    }
}