package com.openclaw.clawchat.data.local

import androidx.room.TypeConverter
import java.time.Instant

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
    
    @TypeConverter
    fun fromInstant(instant: Instant): Long {
        return instant.toEpochMilli()
    }
    
    @TypeConverter
    fun toInstant(timestamp: Long): Instant {
        return Instant.ofEpochMilli(timestamp)
    }
}
