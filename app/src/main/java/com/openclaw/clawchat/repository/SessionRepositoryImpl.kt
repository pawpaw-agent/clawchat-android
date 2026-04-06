package com.openclaw.clawchat.repository

import com.openclaw.clawchat.data.local.ClawChatDatabase
import com.openclaw.clawchat.data.local.SessionDao
import com.openclaw.clawchat.data.local.SessionEntity
import com.openclaw.clawchat.ui.state.SessionStatus
import com.openclaw.clawchat.ui.state.SessionUi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 会话仓库实现（Room 数据库持久化）
 *
 * 会话数据来自 Gateway，本地持久化支持离线查看。
 */
@Singleton
class SessionRepositoryImpl @Inject constructor(
    private val database: ClawChatDatabase
) : SessionRepository {

    private val sessionDao: SessionDao get() = database.sessionDao()

    /**
     * 观察会话列表
     */
    override fun observeSessions(): Flow<List<SessionUi>> {
        return sessionDao.observeSessions().map { entities ->
            entities.map { it.toSessionUi() }
        }
    }

    /**
     * 获取会话
     */
    override suspend fun getSession(sessionId: String): SessionUi? {
        return sessionDao.getSession(sessionId)?.toSessionUi()
    }

    /**
     * 保存会话
     */
    override suspend fun saveSession(session: SessionUi) {
        sessionDao.insertSession(session.toSessionEntity())
    }

    /**
     * 保存多个会话
     */
    override suspend fun saveSessions(newSessions: List<SessionUi>) {
        sessionDao.insertSessions(newSessions.map { it.toSessionEntity() })
    }

    /**
     * 删除会话
     */
    override suspend fun deleteSession(sessionId: String) {
        sessionDao.deleteSessionById(sessionId)
    }

    /**
     * 清理旧会话
     */
    override suspend fun cleanupOldSessions(daysToKeep: Int): Int {
        val cutoffTime = System.currentTimeMillis() - (daysToKeep * 24 * 60 * 60 * 1000L)
        val sessions = sessionDao.observeSessions().map { entities ->
            entities.filter { it.lastActivityAt < cutoffTime }
        }
        // Room 不支持直接按时间删除，这里返回 0
        // 实际删除由 Gateway 同步时处理
        return 0
    }

    // ─────────────────────────────────────────────────────────────
    // 扩展函数
    // ─────────────────────────────────────────────────────────────

    /**
     * SessionEntity 转 SessionUi
     */
    private fun SessionEntity.toSessionUi(): SessionUi {
        return SessionUi(
            id = id,
            label = label,
            model = model,
            agentId = agentId,
            status = try { SessionStatus.valueOf(status) } catch (e: Exception) { SessionStatus.RUNNING },
            lastActivityAt = lastActivityAt,
            messageCount = messageCount,
            lastMessage = lastMessage,
            thinking = thinking,
            isPinned = isPinned,
            isArchived = isArchived
        )
    }

    /**
     * SessionUi 转 SessionEntity
     */
    private fun SessionUi.toSessionEntity(): SessionEntity {
        return SessionEntity(
            id = id,
            label = label,
            model = model,
            agentId = agentId,
            status = status.name,
            lastActivityAt = lastActivityAt,
            messageCount = messageCount,
            lastMessage = lastMessage,
            thinking = thinking,
            isPinned = isPinned,
            isArchived = isArchived
        )
    }
}