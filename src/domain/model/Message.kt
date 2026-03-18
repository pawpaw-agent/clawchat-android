package com.openclaw.clawchat.domain.model

/**
 * 消息领域模型
 * 
 * 表示会话中的单条消息，包含消息内容、角色、时间戳和可选附件。
 * 消息是 ClawChat 中最基本的通信单元。
 * 
 * @property id 消息唯一标识符
 * @property sessionId 所属会话 ID
 * @property role 消息发送者角色
 * @property content 消息文本内容
 * @property attachments 附件列表（图片、文件等）
 * @property timestamp 消息创建时间戳（毫秒）
 * @property metadata 额外元数据（JSON 字符串）
 */
data class Message(
    val id: String,
    val sessionId: String,
    val role: MessageRole,
    val content: String,
    val attachments: List<Attachment> = emptyList(),
    val timestamp: Long = System.currentTimeMillis(),
    val metadata: Map<String, String> = emptyMap()
) {
    /**
     * 检查消息是否包含附件
     */
    fun hasAttachments(): Boolean = attachments.isNotEmpty()
    
    /**
     * 检查消息是否为用户发送
     */
    fun isUserMessage(): Boolean = role == MessageRole.USER
    
    /**
     * 检查消息是否为助手响应
     */
    fun isAssistantMessage(): Boolean = role == MessageRole.ASSISTANT
    
    /**
     * 检查消息是否为系统消息
     */
    fun isSystemMessage(): Boolean = role == MessageRole.SYSTEM
    
    /**
     * 获取内容预览（用于列表显示）
     * 
     * @param maxLength 最大字符数
     * @return 截断后的内容预览
     */
    fun getPreview(maxLength: Int = 100): String {
        return if (content.length <= maxLength) {
            content
        } else {
            content.take(maxLength - 3) + "..."
        }
    }
    
    companion object {
        /**
         * 创建用户消息的工厂方法
         * 
         * @param sessionId 会话 ID
         * @param content 消息内容
         * @param attachments 可选附件列表
         * @return 用户消息实例
         */
        fun createUserMessage(
            sessionId: String,
            content: String,
            attachments: List<Attachment> = emptyList()
        ): Message {
            return Message(
                id = generateMessageId(),
                sessionId = sessionId,
                role = MessageRole.USER,
                content = content,
                attachments = attachments
            )
        }
        
        /**
         * 创建助手消息的工厂方法
         * 
         * @param sessionId 会话 ID
         * @param content 消息内容
         * @param model 使用的模型（可选）
         * @return 助手消息实例
         */
        fun createAssistantMessage(
            sessionId: String,
            content: String,
            model: String? = null
        ): Message {
            return Message(
                id = generateMessageId(),
                sessionId = sessionId,
                role = MessageRole.ASSISTANT,
                content = content,
                metadata = model?.let { mapOf("model" to it) } ?: emptyMap()
            )
        }
        
        /**
         * 创建系统消息的工厂方法
         * 
         * @param sessionId 会话 ID
         * @param content 系统消息内容
         * @return 系统消息实例
         */
        fun createSystemMessage(
            sessionId: String,
            content: String
        ): Message {
            return Message(
                id = generateMessageId(),
                sessionId = sessionId,
                role = MessageRole.SYSTEM,
                content = content
            )
        }
        
        /**
         * 生成消息 ID
         * 格式：msg_ + UUID（去除连字符）
         */
        private fun generateMessageId(): String {
            return "msg_" + java.util.UUID.randomUUID()
                .toString()
                .replace("-", "")
                .take(20)
        }
    }
}

/**
 * 消息角色枚举
 * 
 * 定义消息发送者的身份类型。
 */
enum class MessageRole {
    /**
     * 用户 - 由终端用户发送的消息
     */
    USER,
    
    /**
     * 助手 - AI Agent 的响应消息
     */
    ASSISTANT,
    
    /**
     * 系统 - 系统生成的通知或状态消息
     */
    SYSTEM;
    
    companion object {
        /**
         * 从字符串解析消息角色
         * 
         * @param value 角色字符串（不区分大小写）
         * @return 对应的 MessageRole 枚举值
         * @throws IllegalArgumentException 当输入不是有效角色时
         */
        fun fromString(value: String): MessageRole {
            return when (value.lowercase()) {
                "user" -> USER
                "assistant", "agent", "bot" -> ASSISTANT
                "system" -> SYSTEM
                else -> throw IllegalArgumentException("Unknown message role: $value")
            }
        }
        
        /**
         * 转换为字符串表示
         */
        fun MessageRole.toStringValue(): String = when (this) {
            USER -> "user"
            ASSISTANT -> "assistant"
            SYSTEM -> "system"
        }
    }
}

/**
 * 附件领域模型
 * 
 * 表示消息中的附件（图片、文件等）。
 * 
 * @property id 附件唯一标识符
 * @property name 原始文件名
 * @property mimeType MIME 类型（如 "image/png", "application/pdf"）
 * @property size 文件大小（字节）
 * @property url 远程 URL（如已上传）
 * @property localPath 本地文件路径（如未上传）
 * @property base64 Base64 编码内容（小文件可内联）
 */
data class Attachment(
    val id: String,
    val name: String,
    val mimeType: String,
    val size: Long,
    val url: String? = null,
    val localPath: String? = null,
    val base64: String? = null
) {
    /**
     * 检查附件是否为图片
     */
    fun isImage(): Boolean = mimeType.startsWith("image/")
    
    /**
     * 检查附件是否为文件
     */
    fun isFile(): Boolean = !isImage()
    
    /**
     * 检查附件是否已上传（有远程 URL）
     */
    fun isUploaded(): Boolean = url != null
    
    /**
     * 获取人类可读的文件大小
     */
    fun getHumanReadableSize(): String {
        return when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> "${size / 1024} KB"
            size < 1024 * 1024 * 1024 -> "${size / (1024 * 1024)} MB"
            else -> "${size / (1024 * 1024 * 1024)} GB"
        }
    }
    
    companion object {
        /**
         * 从本地文件创建附件（未上传状态）
         */
        fun fromLocalFile(
            path: String,
            name: String,
            mimeType: String,
            size: Long
        ): Attachment {
            return Attachment(
                id = generateAttachmentId(),
                name = name,
                mimeType = mimeType,
                size = size,
                localPath = path
            )
        }
        
        private fun generateAttachmentId(): String {
            return "att_" + java.util.UUID.randomUUID()
                .toString()
                .replace("-", "")
                .take(16)
        }
    }
}
