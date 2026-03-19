package com.openclaw.clawchat.data.local

import androidx.room.TypeConverter

/**
 * Room 类型转换器
 */
class Converters {
    
    @TypeConverter
    fun fromMessageRole(role: MessageRole): String {
        return role.name
    }
    
    @TypeConverter
    fun toMessageRole(value: String): MessageRole {
        return MessageRole.valueOf(value)
    }
    
    @TypeConverter
    fun fromMessageStatus(status: MessageStatus): String {
        return status.name
    }
    
    @TypeConverter
    fun toMessageStatus(value: String): MessageStatus {
        return MessageStatus.valueOf(value)
    }
}
