package com.openclaw.clawchat.repository

import com.openclaw.clawchat.data.local.SessionEntity
import com.openclaw.clawchat.ui.state.SessionUi
import com.openclaw.clawchat.ui.state.SessionStatus
import kotlinx.coroutines.flow.Flow

/**
 * 会话仓库接口
 *
 * 本地缓存层，用于离线查看会话列表。
 * 实际会话数据来自 Gateway，这里只做缓存。
 */
interface SessionRepository {

    /**
     * 观察会话列表
     */
    fun observeSessions(): Flow<List<SessionUi>>

    /**
     * 获取会话
     */
    suspend fun getSession(sessionId: String): SessionUi?

    /**
     * 保存会话（从 Gateway 同步）
     */
    suspend fun saveSession(session: SessionUi)

    /**
     * 保存多个会话
     */
    suspend fun saveSessions(sessions: List<SessionUi>)

    /**
     * 删除会话
     */
    suspend fun deleteSession(sessionId: String)

    /**
     * 清理旧会话
     */
    suspend fun cleanupOldSessions(daysToKeep: Int): Int
}