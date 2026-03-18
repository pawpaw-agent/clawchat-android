package com.openclaw.clawchat.domain.usecase

import com.openclaw.clawchat.domain.model.Attachment
import com.openclaw.clawchat.domain.model.Message
import com.openclaw.clawchat.domain.repository.SessionRepository

/**
 * 发送消息用例
 * 
 * 处理向指定会话发送消息的业务逻辑。
 * 负责验证消息内容、创建消息对象并委托仓库发送。
 * 
 * 设计原则：
 * - 单一职责：只处理发送消息的逻辑
 * - 输入验证：确保消息内容有效
 * - 错误处理：返回 Result 封装可能的错误
 * 
 * @param sessionRepository 会话仓库
 */
class SendMessage(
    private val sessionRepository: SessionRepository
) {
    /**
     * 执行发送消息操作
     * 
     * @param sessionId 目标会话 ID
     * @param content 消息内容
     * @param attachments 可选附件列表
     * @return 发送结果，成功返回发送的消息
     */
    suspend operator fun invoke(
        sessionId: String,
        content: String,
        attachments: List<Attachment> = emptyList()
    ): Result<Message> {
        // 验证会话 ID
        if (sessionId.isBlank()) {
            return Result.failure(IllegalArgumentException("Session ID cannot be empty"))
        }
        
        // 验证消息内容（允许空内容如果存在附件）
        if (content.isBlank() && attachments.isEmpty()) {
            return Result.failure(IllegalArgumentException("Message content or attachments required"))
        }
        
        // 验证附件大小（可选，根据需求调整）
        val totalSize = attachments.sumOf { it.size }
        val maxTotalSize = 50 * 1024 * 1024L // 50MB
        if (totalSize > maxTotalSize) {
            return Result.failure(IllegalArgumentException("Total attachment size exceeds 50MB limit"))
        }
        
        // 验证单个附件大小
        val maxSingleSize = 20 * 1024 * 1024L // 20MB
        for (attachment in attachments) {
            if (attachment.size > maxSingleSize) {
                return Result.failure(IllegalArgumentException(
                    "Attachment '${attachment.name}' exceeds 20MB limit"
                ))
            }
        }
        
        // 委托仓库发送消息
        return sessionRepository.sendMessage(sessionId, content, attachments)
    }
    
    /**
     * 快速发送纯文本消息（便捷方法）
     */
    suspend fun sendText(sessionId: String, content: String): Result<Message> {
        return invoke(sessionId, content)
    }
}
