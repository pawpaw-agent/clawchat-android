package com.openclaw.clawchat.domain.model

/**
 * 会话领域模型
 *
 * Domain 层权威定义，表示一个会话实体。
 * 与 UI 层的 SessionUi 分离，遵循 Clean Architecture 分层原则。
 */
data class Session(
    val id: String,
    val label: String?,
    val model: String?,
    val status: SessionStatus,
    val lastActivityAt: Long,
    val messageCount: Int = 0,
    val lastMessage: String? = null,
    val thinking: Boolean = false
)

/**
 * 会话状态枚举
 *
 * Domain 层权威定义。
 */
enum class SessionStatus {
    /** 运行中 */
    RUNNING,

    /** 已暂停 */
    PAUSED,

    /** 已终止 */
    TERMINATED
}
