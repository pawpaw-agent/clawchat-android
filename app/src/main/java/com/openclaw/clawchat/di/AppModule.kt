package com.openclaw.clawchat.di

import android.content.Context
import androidx.room.Room
import com.openclaw.clawchat.data.local.ClawChatDatabase
import com.openclaw.clawchat.data.local.MessageDao
import com.openclaw.clawchat.data.local.SessionDao
import com.openclaw.clawchat.data.local.PendingMessageDao
import com.openclaw.clawchat.repository.MessageRepository
import com.openclaw.clawchat.repository.MessageRepositoryImpl
import com.openclaw.clawchat.repository.SessionRepository
import com.openclaw.clawchat.repository.SessionRepositoryImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): ClawChatDatabase {
        return Room.databaseBuilder(
            context,
            ClawChatDatabase::class.java,
            "clawchat_database"
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideMessageDao(database: ClawChatDatabase): MessageDao {
        return database.messageDao()
    }

    @Provides
    fun provideSessionDao(database: ClawChatDatabase): SessionDao {
        return database.sessionDao()
    }

    @Provides
    fun providePendingMessageDao(database: ClawChatDatabase): PendingMessageDao {
        return database.pendingMessageDao()
    }

    @Provides
    @Singleton
    fun provideSessionRepository(database: ClawChatDatabase): SessionRepository {
        return SessionRepositoryImpl(database)
    }

    @Provides
    @Singleton
    fun provideMessageRepository(database: ClawChatDatabase): MessageRepository {
        return MessageRepositoryImpl(database)
    }
}