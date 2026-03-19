package com.openclaw.clawchat.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * 消息数据访问对象
 */
@Dao
interface MessageDao {
    
    /**
     * 获取会话的所有消息（按时间排序）
     */
    @Query("SELECT * FROM messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getMessagesBySession(sessionId: String): Flow<List<MessageEntity>>
    
    /**
     * 获取会话的最新 N 条消息
     */
    @Query("SELECT * FROM messages WHERE sessionId = :sessionId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getLatestMessages(sessionId: String, limit: Int = 50): List<MessageEntity>
    
    /**
     * 插入单条消息
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: MessageEntity): Long
    
    /**
     * 批量插入消息
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<MessageEntity>)
    
    /**
     * 更新消息状态
     */
    @Update
    suspend fun update(message: MessageEntity)
    
    /**
     * 删除单条消息
     */
    @Delete
    suspend fun delete(message: MessageEntity)
    
    /**
     * 删除会话的所有消息
     */
    @Query("DELETE FROM messages WHERE sessionId = :sessionId")
    suspend fun deleteBySession(sessionId: String)
    
    /**
     * 删除指定时间之前的消息（清理旧消息）
     */
    @Query("DELETE FROM messages WHERE timestamp < :beforeTimestamp")
    suspend fun deleteOlderThan(beforeTimestamp: Long): Int
    
    /**
     * 获取消息数量
     */
    @Query("SELECT COUNT(*) FROM messages WHERE sessionId = :sessionId")
    suspend fun getCount(sessionId: String): Int
    
    /**
     * 获取所有会话 ID 列表
     */
    @Query("SELECT sessionId FROM messages GROUP BY sessionId ORDER BY MAX(timestamp) DESC")
    fun getAllSessionIds(): Flow<List<String>>
    
    /**
     * 根据 ID 更新消息状态
     */
    @Query("UPDATE messages SET status = :status WHERE id = :id")
    suspend fun updateStatusById(id: Long, status: String)

    /**
     * 根据 ID 获取单条消息
     */
    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun getById(id: Long): MessageEntity?

    /**
     * 搜索消息内容
     */
    @Query("SELECT * FROM messages WHERE content LIKE :query ORDER BY timestamp DESC LIMIT :limit")
    suspend fun searchMessages(query: String, limit: Int = 20): List<MessageEntity>
}
