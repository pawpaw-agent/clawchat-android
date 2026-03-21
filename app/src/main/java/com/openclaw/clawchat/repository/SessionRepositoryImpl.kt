package com.openclaw.clawchat.repository

import com.openclaw.clawchat.ui.state.SessionUi
import com.openclaw.clawchat.ui.state.SessionStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 会话仓库实现（内存存储）
 *
 * 会话数据来自 Gateway，这里只做临时缓存。
 * 应用重启后数据清空，由 Gateway 重新加载。
 */
@Singleton
class SessionRepositoryImpl @Inject constructor() : SessionRepository {

    private val sessions = MutableStateFlow<Map<String, SessionUi>>(emptyMap())

    /**
     * 观察会话列表
     */
    override fun observeSessions(): Flow<List<SessionUi>> {
        return sessions.map { map -> 
            map.values.sortedByDescending { it.lastActivityAt } 
        }
    }

    /**
     * 获取会话
     */
    override suspend fun getSession(sessionId: String): SessionUi? {
        return sessions.value[sessionId]
    }

    /**
     * 保存会话
     */
    override suspend fun saveSession(session: SessionUi) {
        sessions.value = sessions.value + (session.id to session)
    }

    /**
     * 保存多个会话
     */
    override suspend fun saveSessions(newSessions: List<SessionUi>) {
        val newMap = sessions.value.toMutableMap()
        newSessions.forEach { session ->
            newMap[session.id] = session
        }
        sessions.value = newMap
    }

    /**
     * 删除会话
     */
    override suspend fun deleteSession(sessionId: String) {
        sessions.value = sessions.value - sessionId
    }

    /**
     * 清理旧会话（内存存储不支持，返回 0）
     */
    override suspend fun cleanupOldSessions(daysToKeep: Int): Int {
        return 0
    }
}