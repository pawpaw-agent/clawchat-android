package com.openclaw.clawchat.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * ClawChat Room 数据库
 */
@Database(
    entities = [
        MessageEntity::class,
        SessionEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class ClawChatDatabase : RoomDatabase() {
    
    abstract fun messageDao(): MessageDao
    abstract fun sessionDao(): SessionDao
    
    companion object {
        @Volatile
        private var INSTANCE: ClawChatDatabase? = null
        
        fun getDatabase(context: Context): ClawChatDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ClawChatDatabase::class.java,
                    "clawchat_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
