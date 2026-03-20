package com.openclaw.clawchat.domain.model

/**
 * 消息领域模型
 *
 * Domain 层权威定义，表示一条消息实体。
 * 与 UI 层的 MessageUi 分离，遵循 Clean Architecture 分层原则。
 */
data class Message(
    val id: String,
    val sessionId: String,
    val content: String,
    val role: MessageRole,
    val timestamp: Long,
    val status: MessageStatus = MessageStatus.SENT,
    val metadata: String? = null
)

/**
 * 消息角色
 *
 * Domain 层权威定义。
 */
enum class MessageRole {
    USER,
    ASSISTANT,
    SYSTEM
}

/**
 * 消息状态
 *
 * Domain 层权威定义。
 */
enum class MessageStatus {
    PENDING,    // 发送中
    SENT,       // 已发送
    DELIVERED,  // 已送达
    FAILED      // 发送失败
}