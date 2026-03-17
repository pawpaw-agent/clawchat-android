package com.openclaw.clawchat.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

/**
 * 会话实体 - Room 数据库表
 */
@Entity(
    tableName = "sessions",
    indices = [
        Index(value = ["title"], name = "idx_sessions_title"),
        Index(value = ["isActive"], name = "idx_sessions_active")
    ]
)
data class SessionEntity(
    @PrimaryKey
    val id: String,
    
    val title: String,
    
    val createdAt: Long,
    
    val updatedAt: Long,
    
    val isActive: Boolean = true,
    
    val messageCount: Int = 0,
    
    val lastMessagePreview: String? = null,
    
    val metadata: String? = null
)
