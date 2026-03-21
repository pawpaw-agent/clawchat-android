package com.openclaw.clawchat.di

import com.openclaw.clawchat.repository.MessageRepository
import com.openclaw.clawchat.repository.MessageRepositoryImpl
import com.openclaw.clawchat.repository.SessionRepository
import com.openclaw.clawchat.repository.SessionRepositoryImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    
    @Provides
    @Singleton
    fun provideSessionRepository(): SessionRepository {
        return SessionRepositoryImpl()
    }
    
    @Provides
    @Singleton
    fun provideMessageRepository(): MessageRepository {
        return MessageRepositoryImpl()
    }
}