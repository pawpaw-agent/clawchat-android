package com.openclaw.clawchat.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * ClawChat Room 数据库
 */
@Database(
    entities = [
        MessageEntity::class,
        SessionEntity::class
    ],
    version = 2,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class ClawChatDatabase : RoomDatabase() {
    
    abstract fun messageDao(): MessageDao
    abstract fun sessionDao(): SessionDao
    
    companion object {
        @Volatile
        private var INSTANCE: ClawChatDatabase? = null

        /**
         * Migration 1→2: 占位（schema 未变，仅版本号升级以启用安全迁移策略）
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // no-op: schema unchanged, version bump for migration strategy
            }
        }
        
        fun getDatabase(context: Context): ClawChatDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ClawChatDatabase::class.java,
                    "clawchat_database"
                )
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
