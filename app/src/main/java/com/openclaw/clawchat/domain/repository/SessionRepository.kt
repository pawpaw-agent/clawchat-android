package com.openclaw.clawchat.domain.repository

import com.openclaw.clawchat.domain.model.Session
import com.openclaw.clawchat.domain.model.SessionStatus
import kotlinx.coroutines.flow.Flow

/**
 * 会话仓库接口
 *
 * Domain 层 Repository 接口定义。
 * 遵循依赖倒置原则，上层依赖此接口而非具体实现。
 */
interface SessionRepository {

    /**
     * 观察会话列表
     */
    fun observeSessions(): Flow<List<Session>>

    /**
     * 获取当前会话
     */
    fun observeCurrentSession(): Flow<Session?>

    /**
     * 添加会话
     */
    suspend fun addSession(session: Session)

    /**
     * 更新会话
     */
    suspend fun updateSession(sessionId: String, update: (Session) -> Session)

    /**
     * 删除会话
     */
    suspend fun deleteSession(sessionId: String)

    /**
     * 获取会话
     */
    fun getSession(sessionId: String): Session?

    /**
     * 设置当前会话
     */
    fun setCurrentSession(sessionId: String?)

    /**
     * 搜索会话
     */
    suspend fun searchSessions(query: String): List<Session>

    /**
     * 获取活跃会话
     */
    fun getActiveSessions(): List<Session>

    /**
     * 获取已暂停会话
     */
    fun getPausedSessions(): List<Session>

    /**
     * 获取已终止会话
     */
    fun getTerminatedSessions(): List<Session>

    /**
     * 清除所有已终止会话
     */
    suspend fun clearTerminatedSessions()

    /**
     * 加载会话（从数据库）
     */
    suspend fun loadSessions()

    /**
     * 清空所有会话
     */
    suspend fun clearAllSessions()
}

/**
 * 会话统计
 */
data class SessionStats(
    val total: Int,
    val running: Int,
    val paused: Int,
    val terminated: Int,
    val totalMessages: Int
)