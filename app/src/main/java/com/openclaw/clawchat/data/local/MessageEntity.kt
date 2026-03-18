package com.openclaw.clawchat.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

/**
 * 消息实体 - Room 数据库表
 */
@Entity(
    tableName = "messages",
    indices = [
        Index(value = ["sessionId"], name = "idx_messages_session_id"),
        Index(value = ["sessionId", "timestamp"], name = "idx_messages_session_timestamp")
    ]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    val sessionId: String,
    
    val role: MessageRole,
    
    val content: String,
    
    val timestamp: Long,
    
    val status: MessageStatus = MessageStatus.SENT,
    
    val metadata: String? = null
)

/**
 * 消息角色
 */
enum class MessageRole {
    USER,
    ASSISTANT,
    SYSTEM
}

/**
 * 消息状态
 */
enum class MessageStatus {
    PENDING,    // 发送中
    SENT,       // 已发送
    DELIVERED,  // 已送达
    FAILED      // 发送失败
}
