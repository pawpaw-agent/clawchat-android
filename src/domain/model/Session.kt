package com.openclaw.clawchat.domain.model

/**
 * 会话领域模型
 * 
 * 表示与 OpenClaw Agent 的一次交互会话，包含会话的元数据和状态信息。
 * 会话是消息的容器，每个会话可以有多个来回的消息交互。
 * 
 * @property id 会话唯一标识符
 * @property label 会话标签/标题（用户自定义）
 * @property model 使用的 AI 模型标识（如 "aliyun/qwen3.5-plus"）
 * @property status 会话当前状态
 * @property thinking 是否启用深度思考模式
 * @property createdAt 会话创建时间戳（毫秒）
 * @property lastActivityAt 最后活动时间戳（毫秒）
 * @property messageCount 会话中的消息总数
 */
data class Session(
    val id: String,
    val label: String? = null,
    val model: String? = null,
    val status: SessionStatus = SessionStatus.RUNNING,
    val thinking: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val lastActivityAt: Long = System.currentTimeMillis(),
    val messageCount: Int = 0
) {
    /**
     * 检查会话是否处于活动状态
     */
    fun isActive(): Boolean = status == SessionStatus.RUNNING
    
    /**
     * 检查会话是否已结束
     */
    fun isTerminated(): Boolean = status == SessionStatus.TERMINATED
    
    /**
     * 获取会话持续时间（毫秒）
     */
    fun getDuration(): Long = when (status) {
        SessionStatus.TERMINATED -> lastActivityAt - createdAt
        else -> System.currentTimeMillis() - createdAt
    }
    
    /**
     * 复制并更新最后活动时间
     */
    fun withActivityUpdate(): Session = copy(
        lastActivityAt = System.currentTimeMillis()
    )
    
    /**
     * 复制并增加消息计数
     */
    fun withMessageAdded(): Session = copy(
        messageCount = messageCount + 1,
        lastActivityAt = System.currentTimeMillis()
    )
    
    companion object {
        /**
         * 创建新会话的工厂方法
         * 
         * @param model 使用的 AI 模型
         * @param label 可选的会话标签
         * @param thinking 是否启用深度思考
         * @return 新创建的 Session 实例
         */
        fun create(
            model: String? = null,
            label: String? = null,
            thinking: Boolean = false
        ): Session {
            return Session(
                id = generateSessionId(),
                model = model,
                label = label,
                thinking = thinking,
                status = SessionStatus.RUNNING
            )
        }
        
        /**
         * 生成会话 ID
         * 格式：session_ + UUID（去除连字符）
         */
        private fun generateSessionId(): String {
            return "session_" + java.util.UUID.randomUUID()
                .toString()
                .replace("-", "")
                .take(24)
        }
    }
}

/**
 * 会话状态枚举
 * 
 * 定义会话在其生命周期中的各种状态。
 */
enum class SessionStatus {
    /**
     * 运行中 - 会话活跃，可接收和发送消息
     */
    RUNNING,
    
    /**
     * 已暂停 - 会话暂时挂起，不处理新消息
     */
    PAUSED,
    
    /**
     * 已终止 - 会话已结束，不可再发送消息
     */
    TERMINATED,
    
    /**
     * 错误状态 - 会话因错误而中断
     */
    ERROR;
    
    companion object {
        /**
         * 从字符串解析会话状态
         * 
         * @param value 状态字符串（不区分大小写）
         * @return 对应的 SessionStatus 枚举值
         * @throws IllegalArgumentException 当输入不是有效状态时
         */
        fun fromString(value: String): SessionStatus {
            return when (value.lowercase()) {
                "running", "active" -> RUNNING
                "paused", "suspended" -> PAUSED
                "terminated", "ended", "closed" -> TERMINATED
                "error", "failed" -> ERROR
                else -> throw IllegalArgumentException("Unknown session status: $value")
            }
        }
    }
}

/**
 * 会话配置
 * 
 * 用于创建或更新会话时的配置选项。
 * 
 * @property model AI 模型标识
 * @property label 会话标签
 * @property thinking 是否启用深度思考模式
 * @property timeoutSeconds 会话超时时间（秒），0 表示无超时
 */
data class SessionConfig(
    val model: String? = null,
    val label: String? = null,
    val thinking: Boolean = false,
    val timeoutSeconds: Int = 0
)
