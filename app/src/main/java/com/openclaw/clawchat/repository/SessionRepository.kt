package com.openclaw.clawchat.repository

import com.openclaw.clawchat.data.local.SessionDao
import com.openclaw.clawchat.data.local.SessionEntity
import com.openclaw.clawchat.domain.model.Session
import com.openclaw.clawchat.domain.model.SessionStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 会话仓库
 *
 * 负责管理会话数据：
 * - 本地缓存 (Room)
 * - 会话列表排序
 * - 会话搜索
 * - 会话状态管理
 *
 * 使用 Domain 层的 Session 模型，而非 UI 层的 SessionUi。
 */
@Singleton
class SessionRepository @Inject constructor(
    private val sessionDao: SessionDao
) {

    private val _sessions = MutableStateFlow<List<Session>>(emptyList())
    val sessions: StateFlow<List<Session>> = _sessions.asStateFlow()

    private val _currentSession = MutableStateFlow<Session?>(null)
    val currentSession: StateFlow<Session?> = _currentSession.asStateFlow()

    companion object {
        private const val MAX_SESSIONS = 50 // 最大会话数量
    }

    /**
     * 从数据库加载会话列表（返回 Domain 模型）
     */
    fun observeSessions(): Flow<List<Session>> {
        return sessionDao.getActiveSessions().map { entities ->
            entities.map { it.toSession() }
        }
    }

    /**
     * 添加会话（使用 Domain 模型）
     */
    suspend fun addSession(session: Session) {
        // 保存到数据库
        val entity = session.toSessionEntity()
        sessionDao.insert(entity)

        // 更新内存缓存
        _sessions.update { sessions ->
            val updatedList = (sessions + session)
                .sortedByDescending { it.lastActivityAt }
                .take(MAX_SESSIONS)
            updatedList
        }
        _currentSession.value = session
    }

    /**
     * 更新会话（使用 Domain 模型）
     */
    suspend fun updateSession(sessionId: String, update: (Session) -> Session) {
        val currentSessions = _sessions.value
        val sessionToUpdate = currentSessions.find { it.id == sessionId } ?: return

        val updatedSession = update(sessionToUpdate)

        // 更新数据库
        sessionDao.update(updatedSession.toSessionEntity())

        // 更新内存缓存
        _sessions.update { sessions ->
            sessions.map { session ->
                if (session.id == sessionId) {
                    updatedSession
                } else {
                    session
                }
            }.sortedByDescending { it.lastActivityAt }
        }

        // 如果更新的是当前会话，同步更新
        _currentSession.update { current ->
            if (current?.id == sessionId) {
                current?.let { update(it) }
            } else {
                current
            }
        }
    }

    /**
     * 删除会话
     */
    suspend fun deleteSession(sessionId: String) {
        // 从数据库删除
        sessionDao.deleteById(sessionId)

        // 更新内存缓存
        _sessions.update { sessions ->
            sessions.filter { it.id != sessionId }
        }

        // 如果删除的是当前会话，清除当前会话
        if (_currentSession.value?.id == sessionId) {
            _currentSession.value = null
        }
    }

    /**
     * 获取会话（返回 Domain 模型）
     */
    fun getSession(sessionId: String): Session? {
        return _sessions.value.find { it.id == sessionId }
    }

    /**
     * 设置当前会话
     */
    fun setCurrentSession(sessionId: String?) {
        _currentSession.value = sessionId?.let { getSession(it) }
    }

    /**
     * 搜索会话（返回 Domain 模型）
     */
    suspend fun searchSessions(query: String): List<Session> {
        if (query.isBlank()) {
            return _sessions.value
        }

        // 从数据库搜索
        return sessionDao.searchSessions("%$query%", 20).map { it.toSession() }
    }

    /**
     * 获取活跃会话（返回 Domain 模型）
     */
    fun getActiveSessions(): List<Session> {
        return _sessions.value.filter { it.status == SessionStatus.RUNNING }
    }

    /**
     * 获取已暂停会话（返回 Domain 模型）
     */
    fun getPausedSessions(): List<Session> {
        return _sessions.value.filter { it.status == SessionStatus.PAUSED }
    }

    /**
     * 获取已终止会话（返回 Domain 模型）
     */
    fun getTerminatedSessions(): List<Session> {
        return _sessions.value.filter { it.status == SessionStatus.TERMINATED }
    }

    /**
     * 清除所有已终止会话（单条 SQL，不循环删除）
     */
    suspend fun clearTerminatedSessions() {
        sessionDao.deleteInactive()

        _sessions.update { sessions ->
            sessions.filter { it.status != SessionStatus.TERMINATED }
        }
    }

    /**
     * 获取会话统计
     */
    fun getSessionStats(): SessionStats {
        val sessions = _sessions.value
        return SessionStats(
            total = sessions.size,
            running = sessions.count { it.status == SessionStatus.RUNNING },
            paused = sessions.count { it.status == SessionStatus.PAUSED },
            terminated = sessions.count { it.status == SessionStatus.TERMINATED },
            totalMessages = sessions.sumOf { it.messageCount }
        )
    }

    /**
     * 加载会话（从数据库）
     */
    suspend fun loadSessions() {
        val entities = sessionDao.getActiveSessions().firstOrNull() ?: emptyList()
        _sessions.value = entities.map { it.toSession() }
    }

    /**
     * 清空所有会话
     */
    suspend fun clearAllSessions() {
        sessionDao.deleteInactive()
        // Also delete active ones by iterating (or add a deleteAll query)
        val allSessions = sessionDao.getAllSessions().firstOrNull() ?: emptyList()
        allSessions.forEach { sessionDao.delete(it) }

        _sessions.value = emptyList()
        _currentSession.value = null
    }
}

// ─────────────────────────────────────────────────────────────
// 映射函数：Domain Session ←→ Room SessionEntity
// ─────────────────────────────────────────────────────────────

/**
 * Session (Domain) 转 SessionEntity (Room)
 */
private fun Session.toSessionEntity(): SessionEntity {
    return SessionEntity(
        id = id,
        title = label ?: "未命名会话",
        createdAt = lastActivityAt,
        updatedAt = lastActivityAt,
        isActive = status == SessionStatus.RUNNING,
        messageCount = messageCount,
        lastMessagePreview = lastMessage,
        metadata = model
    )
}

/**
 * SessionEntity (Room) 转 Session (Domain)
 */
private fun SessionEntity.toSession(): Session {
    return Session(
        id = id,
        label = title,
        model = metadata ?: "qwen3.5-plus",
        status = if (isActive) SessionStatus.RUNNING else SessionStatus.PAUSED,
        lastActivityAt = updatedAt,
        messageCount = messageCount,
        lastMessage = lastMessagePreview,
        thinking = false
    )
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
