package com.openclaw.clawchat.domain.usecase

import com.openclaw.clawchat.domain.repository.SessionRepository

/**
 * 删除会话用例
 * 
 * 处理删除会话的业务逻辑。
 * 负责验证会话存在性、执行删除操作。
 * 
 * 设计原则：
 * - 单一职责：只处理删除会话的逻辑
 * - 安全检查：确认会话存在后再删除
 * - 不可逆操作：删除是永久的，调用方需确认
 * 
 * @param sessionRepository 会话仓库
 */
class DeleteSession(
    private val sessionRepository: SessionRepository
) {
    /**
     * 执行删除会话操作
     * 
     * @param sessionId 要删除的会话 ID
     * @param force 是否强制删除（跳过确认检查）
     * @return 删除结果
     */
    suspend operator fun invoke(
        sessionId: String,
        force: Boolean = false
    ): Result<Unit> {
        // 验证会话 ID
        if (sessionId.isBlank()) {
            return Result.failure(IllegalArgumentException("Session ID cannot be empty"))
        }
        
        // 检查会话是否存在
        val session = sessionRepository.getSession(sessionId)
        if (session == null) {
            return Result.failure(IllegalArgumentException("Session not found: $sessionId"))
        }
        
        // 如果会话正在运行，先终止它
        if (session.isActive() && !force) {
            return Result.failure(IllegalStateException(
                "Cannot delete active session. Terminate it first or use force=true"
            ))
        }
        
        // 终止运行中的会话（如果 force=true）
        if (session.isActive() && force) {
            sessionRepository.terminateSession(sessionId)
        }
        
        // 执行删除
        return sessionRepository.deleteSession(sessionId)
    }
    
    /**
     * 批量删除会话
     * 
     * @param sessionIds 要删除的会话 ID 列表
     * @param force 是否强制删除
     * @return 删除结果列表（按顺序对应输入 ID）
     */
    suspend fun deleteMultiple(
        sessionIds: List<String>,
        force: Boolean = false
    ): List<Result<Unit>> {
        return sessionIds.map { invoke(it, force) }
    }
    
    /**
     * 删除所有已终止的会话
     * 
     * @return 删除的会话数量
     */
    suspend fun deleteTerminatedSessions(): Result<Int> {
        return runCatching {
            val sessions = sessionRepository.observeSessions().first { true }
            val terminatedSessions = sessions.filter { it.isTerminated() }
            
            var deletedCount = 0
            for (session in terminatedSessions) {
                invoke(session.id, force = true).onSuccess {
                    deletedCount++
                }
            }
            deletedCount
        }
    }
}
