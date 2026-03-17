package com.openclaw.clawchat.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * 会话数据访问对象
 */
@Dao
interface SessionDao {
    
    /**
     * 获取所有会话（按更新时间倒序）
     */
    @Query("SELECT * FROM sessions ORDER BY updatedAt DESC")
    fun getAllSessions(): Flow<List<SessionEntity>>
    
    /**
     * 获取活跃会话
     */
    @Query("SELECT * FROM sessions WHERE isActive = true ORDER BY updatedAt DESC")
    fun getActiveSessions(): Flow<List<SessionEntity>>
    
    /**
     * 获取单个会话
     */
    @Query("SELECT * FROM sessions WHERE id = :sessionId")
    suspend fun getSession(sessionId: String): SessionEntity?
    
    /**
     * 获取单个会话（Flow）
     */
    @Query("SELECT * FROM sessions WHERE id = :sessionId")
    fun getSessionFlow(sessionId: String): Flow<SessionEntity?>
    
    /**
     * 插入会话
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: SessionEntity)
    
    /**
     * 批量插入会话
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(sessions: List<SessionEntity>)
    
    /**
     * 更新会话
     */
    @Update
    suspend fun update(session: SessionEntity)
    
    /**
     * 删除会话
     */
    @Delete
    suspend fun delete(session: SessionEntity)
    
    /**
     * 删除指定会话
     */
    @Query("DELETE FROM sessions WHERE id = :sessionId")
    suspend fun deleteById(sessionId: String)
    
    /**
     * 更新会话消息计数
     */
    @Query("UPDATE sessions SET messageCount = :count, updatedAt = :timestamp WHERE id = :sessionId")
    suspend fun updateMessageCount(sessionId: String, count: Int, timestamp: Long)
    
    /**
     * 更新会话最后消息预览
     */
    @Query("UPDATE sessions SET lastMessagePreview = :preview, updatedAt = :timestamp WHERE id = :sessionId")
    suspend fun updateLastMessagePreview(sessionId: String, preview: String, timestamp: Long)
    
    /**
     * 设置会话激活状态
     */
    @Query("UPDATE sessions SET isActive = :isActive, updatedAt = :timestamp WHERE id = :sessionId")
    suspend fun setActive(sessionId: String, isActive: Boolean, timestamp: Long)
    
    /**
     * 获取会话数量
     */
    @Query("SELECT COUNT(*) FROM sessions")
    suspend fun getCount(): Int
    
    /**
     * 搜索会话标题
     */
    @Query("SELECT * FROM sessions WHERE title LIKE :query ORDER BY updatedAt DESC LIMIT :limit")
    suspend fun searchSessions(query: String, limit: Int = 20): List<SessionEntity>
}
