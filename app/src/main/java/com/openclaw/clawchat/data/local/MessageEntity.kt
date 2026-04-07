package com.openclaw.clawchat.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 消息实体 (Room 数据库表)
 */
@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["sessionId"]),
        Index(value = ["timestamp"]),
        Index(value = ["sessionId", "timestamp"])
    ]
)
data class MessageEntity(
    @PrimaryKey
    val id: String,
    val sessionId: String,
    val role: String,
    val content: String,          // JSON 格式的内容
    val timestamp: Long,
    val toolCallId: String? = null,
    val toolName: String? = null,
    val status: String = "SENT",  // SENDING, SENT, DELIVERED, FAILED
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * 会话实体 (Room 数据库表)
 */
@Entity(
    tableName = "sessions",
    indices = [
        Index(value = ["lastActivityAt"]),
        Index(value = ["isPinned"])
    ]
)
data class SessionEntity(
    @PrimaryKey
    val id: String,
    val label: String? = null,
    val model: String? = null,
    val agentId: String? = null,
    val agentName: String? = null,  // Agent 显示名称
    val agentEmoji: String? = null,  // Agent emoji 图标
    val status: String = "RUNNING",
    val lastActivityAt: Long,
    val messageCount: Int = 0,
    val lastMessage: String? = null,
    val thinking: Boolean = false,
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * 待发送消息队列 (用于离线支持)
 */
@Entity(
    tableName = "pending_messages",
    indices = [
        Index(value = ["sessionId"]),
        Index(value = ["createdAt"])
    ]
)
data class PendingMessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionId: String,
    val text: String,
    val attachments: String? = null,  // JSON 格式的附件列表
    val createdAt: Long = System.currentTimeMillis(),
    val retryCount: Int = 0,
    val lastError: String? = null
)