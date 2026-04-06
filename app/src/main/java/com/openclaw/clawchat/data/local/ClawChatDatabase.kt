package com.openclaw.clawchat.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * ClawChat Room 数据库
 */
@Database(
    entities = [
        MessageEntity::class,
        SessionEntity::class,
        PendingMessageEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class ClawChatDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun sessionDao(): SessionDao
    abstract fun pendingMessageDao(): PendingMessageDao
}