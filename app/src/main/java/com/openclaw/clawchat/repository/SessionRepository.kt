package com.openclaw.clawchat.repository

import com.openclaw.clawchat.ui.state.SessionUi
import com.openclaw.clawchat.ui.state.SessionStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 会话仓库
 * 
 * 负责管理会话数据：
 * - 本地缓存
 * - 会话列表排序
 * - 会话搜索
 * - 会话状态管理
 */
@Singleton
class SessionRepository @Inject constructor() {

    private val _sessions = MutableStateFlow<List<SessionUi>>(emptyList())
    val sessions: StateFlow<List<SessionUi>> = _sessions.asStateFlow()

    private val _currentSession = MutableStateFlow<SessionUi?>(null)
    val currentSession: StateFlow<SessionUi?> = _currentSession.asStateFlow()

    companion object {
        private const val MAX_SESSIONS = 50 // 最大会话数量
    }

    /**
     * 添加会话
     */
    fun addSession(session: SessionUi) {
        _sessions.update { sessions ->
            val updatedList = (sessions + session)
                .sortedByDescending { it.lastActivityAt }
                .take(MAX_SESSIONS)
            updatedList
        }
        _currentSession.value = session
    }

    /**
     * 更新会话
     */
    fun updateSession(sessionId: String, update: (SessionUi) -> SessionUi) {
        _sessions.update { sessions ->
            sessions.map { session ->
                if (session.id == sessionId) {
                    update(session)
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
    fun deleteSession(sessionId: String) {
        _sessions.update { sessions ->
            sessions.filter { it.id != sessionId }
        }

        // 如果删除的是当前会话，清除当前会话
        if (_currentSession.value?.id == sessionId) {
            _currentSession.value = null
        }
    }

    /**
     * 获取会话
     */
    fun getSession(sessionId: String): SessionUi? {
        return _sessions.value.find { it.id == sessionId }
    }

    /**
     * 设置当前会话
     */
    fun setCurrentSession(sessionId: String?) {
        _currentSession.value = sessionId?.let { getSession(it) }
    }

    /**
     * 搜索会话
     */
    fun searchSessions(query: String): List<SessionUi> {
        if (query.isBlank()) {
            return _sessions.value
        }

        val lowerQuery = query.lowercase()
        return _sessions.value.filter { session ->
            session.label?.lowercase()?.contains(lowerQuery) == true ||
            session.lastMessage?.lowercase()?.contains(lowerQuery) == true
        }
    }

    /**
     * 获取活跃会话
     */
    fun getActiveSessions(): List<SessionUi> {
        return _sessions.value.filter { it.status == SessionStatus.RUNNING }
    }

    /**
     * 获取已暂停会话
     */
    fun getPausedSessions(): List<SessionUi> {
        return _sessions.value.filter { it.status == SessionStatus.PAUSED }
    }

    /**
     * 获取已终止会话
     */
    fun getTerminatedSessions(): List<SessionUi> {
        return _sessions.value.filter { it.status == SessionStatus.TERMINATED }
    }

    /**
     * 清除所有已终止会话
     */
    fun clearTerminatedSessions() {
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
     * 加载会话（从服务器或本地存储）
     */
    suspend fun loadSessions() {
        // TODO: 从服务器加载真实会话列表
        // 暂时使用模拟数据
        val mockSessions = listOf(
            SessionUi(
                id = "session_1",
                label = "项目讨论",
                model = "qwen3.5-plus",
                status = SessionStatus.RUNNING,
                lastActivityAt = System.currentTimeMillis() - 60000,
                messageCount = 15,
                lastMessage = "好的，我来帮你实现这个功能",
                thinking = false
            ),
            SessionUi(
                id = "session_2",
                label = "代码审查",
                model = "qwen3.5-plus",
                status = SessionStatus.RUNNING,
                lastActivityAt = System.currentTimeMillis() - 3600000,
                messageCount = 8,
                lastMessage = "这段代码需要优化",
                thinking = false
            ),
            SessionUi(
                id = "session_3",
                label = "数据分析",
                model = "qwen3.5-plus",
                status = SessionStatus.PAUSED,
                lastActivityAt = System.currentTimeMillis() - 86400000,
                messageCount = 25,
                lastMessage = "分析完成",
                thinking = false
            )
        )
        _sessions.value = mockSessions
    }

    /**
     * 清空所有会话
     */
    fun clearAllSessions() {
        _sessions.value = emptyList()
        _currentSession.value = null
    }
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
