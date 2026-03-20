package com.openclaw.clawchat.repository

import com.openclaw.clawchat.data.local.SessionDao
import com.openclaw.clawchat.data.local.SessionEntity
import com.openclaw.clawchat.ui.state.SessionUi
import com.openclaw.clawchat.ui.state.SessionStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 会话仓库实现
 *
 * 本地缓存层，用于离线查看会话列表。
 * 实际会话数据来自 Gateway，这里只做缓存。
 */
@Singleton
class SessionRepositoryImpl @Inject constructor(
    private val sessionDao: SessionDao
) : SessionRepository {

    /**
     * 观察会话列表
     */
    override fun observeSessions(): Flow<List<SessionUi>> {
        return sessionDao.getActiveSessions().map { entities ->
            entities.map { it.toSessionUi() }
        }
    }

    /**
     * 获取会话
     */
    override suspend fun getSession(sessionId: String): SessionUi? {
        return sessionDao.getById(sessionId)?.toSessionUi()
    }

    /**
     * 保存会话
     */
    override suspend fun saveSession(session: SessionUi) {
        sessionDao.insert(session.toSessionEntity())
    }

    /**
     * 保存多个会话
     */
    override suspend fun saveSessions(sessions: List<SessionUi>) {
        sessionDao.insertAll(sessions.map { it.toSessionEntity() })
    }

    /**
     * 删除会话
     */
    override suspend fun deleteSession(sessionId: String) {
        sessionDao.deleteById(sessionId)
    }

    /**
     * 清理旧会话
     */
    override suspend fun cleanupOldSessions(daysToKeep: Int): Int {
        val cutoffTimestamp = System.currentTimeMillis() - (daysToKeep * 24 * 60 * 60 * 1000L)
        return sessionDao.deleteOlderThan(cutoffTimestamp)
    }
}

// ─────────────────────────────────────────────────────────────
// 映射函数
// ─────────────────────────────────────────────────────────────

private fun SessionEntity.toSessionUi(): SessionUi {
    return SessionUi(
        id = id,
        label = title,
        model = metadata,
        status = if (isActive) SessionStatus.RUNNING else SessionStatus.PAUSED,
        lastActivityAt = updatedAt,
        messageCount = messageCount,
        lastMessage = lastMessagePreview,
        thinking = false
    )
}

private fun SessionUi.toSessionEntity(): SessionEntity {
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