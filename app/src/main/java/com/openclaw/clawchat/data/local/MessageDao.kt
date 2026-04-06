package com.openclaw.clawchat.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * 消息 DAO
 */
@Dao
interface MessageDao {

    /**
     * 观察会话消息
     */
    @Query("""
        SELECT * FROM messages
        WHERE sessionId = :sessionId
        ORDER BY timestamp ASC
    """)
    fun observeMessages(sessionId: String): Flow<List<MessageEntity>>

    /**
     * 获取最新消息
     */
    @Query("""
        SELECT * FROM messages
        WHERE sessionId = :sessionId
        ORDER BY timestamp DESC
        LIMIT :limit
    """)
    suspend fun getLatestMessages(sessionId: String, limit: Int): List<MessageEntity>

    /**
     * 插入消息
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    /**
     * 批量插入消息
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<MessageEntity>)

    /**
     * 删除会话所有消息
     */
    @Query("DELETE FROM messages WHERE sessionId = :sessionId")
    suspend fun deleteSessionMessages(sessionId: String)

    /**
     * 删除单条消息
     */
    @Query("DELETE FROM messages WHERE id = :messageId")
    suspend fun deleteMessage(messageId: String)

    /**
     * 清空会话消息（用于 /clear 命令）
     */
    @Query("DELETE FROM messages WHERE sessionId = :sessionId")
    suspend fun clearMessages(sessionId: String)

    /**
     * 搜索消息
     */
    @Query("""
        SELECT * FROM messages
        WHERE content LIKE '%' || :query || '%'
        ORDER BY timestamp DESC
        LIMIT :limit
    """)
    suspend fun searchMessages(query: String, limit: Int): List<MessageEntity>

    /**
     * 获取会话消息数量
     */
    @Query("SELECT COUNT(*) FROM messages WHERE sessionId = :sessionId")
    suspend fun getMessageCount(sessionId: String): Int

    /**
     * 清理旧消息（保留最近 N 条）
     */
    @Query("""
        DELETE FROM messages
        WHERE sessionId = :sessionId
        AND id NOT IN (
            SELECT id FROM messages
            WHERE sessionId = :sessionId
            ORDER BY timestamp DESC
            LIMIT :keepCount
        )
    """)
    suspend fun trimOldMessages(sessionId: String, keepCount: Int)
}

/**
 * 会话 DAO
 */
@Dao
interface SessionDao {

    /**
     * 观察所有会话
     */
    @Query("""
        SELECT * FROM sessions
        WHERE isArchived = 0
        ORDER BY isPinned DESC, lastActivityAt DESC
    """)
    fun observeSessions(): Flow<List<SessionEntity>>

    /**
     * 获取单个会话
     */
    @Query("SELECT * FROM sessions WHERE id = :sessionId")
    suspend fun getSession(sessionId: String): SessionEntity?

    /**
     * 插入或更新会话
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: SessionEntity)

    /**
     * 批量插入会话
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSessions(sessions: List<SessionEntity>)

    /**
     * 删除会话
     */
    @Delete
    suspend fun deleteSession(session: SessionEntity)

    /**
     * 根据 ID 删除会话
     */
    @Query("DELETE FROM sessions WHERE id = :sessionId")
    suspend fun deleteSessionById(sessionId: String)

    /**
     * 更新会话消息计数
     */
    @Query("""
        UPDATE sessions
        SET messageCount = :count, updatedAt = :updatedAt
        WHERE id = :sessionId
    """)
    suspend fun updateMessageCount(sessionId: String, count: Int, updatedAt: Long)

    /**
     * 更新会话最后消息
     */
    @Query("""
        UPDATE sessions
        SET lastMessage = :lastMessage, lastActivityAt = :lastActivityAt, updatedAt = :updatedAt
        WHERE id = :sessionId
    """)
    suspend fun updateLastMessage(
        sessionId: String,
        lastMessage: String?,
        lastActivityAt: Long,
        updatedAt: Long
    )

    /**
     * 切换置顶状态
     */
    @Query("UPDATE sessions SET isPinned = :pinned WHERE id = :sessionId")
    suspend fun updatePinned(sessionId: String, pinned: Boolean)

    /**
     * 归档会话
     */
    @Query("UPDATE sessions SET isArchived = 1 WHERE id = :sessionId")
    suspend fun archiveSession(sessionId: String)

    /**
     * 取消归档
     */
    @Query("UPDATE sessions SET isArchived = 0 WHERE id = :sessionId")
    suspend fun unarchiveSession(sessionId: String)
}

/**
 * 待发送消息 DAO
 */
@Dao
interface PendingMessageDao {

    /**
     * 观察会话的待发送消息
     */
    @Query("SELECT * FROM pending_messages WHERE sessionId = :sessionId ORDER BY createdAt ASC")
    fun observePendingMessages(sessionId: String): Flow<List<PendingMessageEntity>>

    /**
     * 获取所有待发送消息
     */
    @Query("SELECT * FROM pending_messages ORDER BY createdAt ASC")
    suspend fun getAllPendingMessages(): List<PendingMessageEntity>

    /**
     * 插入待发送消息
     */
    @Insert
    suspend fun insertPendingMessage(message: PendingMessageEntity): Long

    /**
     * 更新重试计数
     */
    @Query("""
        UPDATE pending_messages
        SET retryCount = :retryCount, lastError = :error
        WHERE id = :id
    """)
    suspend fun updateRetryCount(id: Long, retryCount: Int, error: String?)

    /**
     * 删除待发送消息
     */
    @Delete
    suspend fun deletePendingMessage(message: PendingMessageEntity)

    /**
     * 清除会话的待发送消息
     */
    @Query("DELETE FROM pending_messages WHERE sessionId = :sessionId")
    suspend fun clearPendingMessages(sessionId: String)

    /**
     * 清除所有待发送消息
     */
    @Query("DELETE FROM pending_messages")
    suspend fun clearAllPendingMessages()
}