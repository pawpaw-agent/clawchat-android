package com.openclaw.clawchat.di

import com.openclaw.clawchat.repository.MessageRepository
import com.openclaw.clawchat.repository.MessageRepositoryImpl
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
    fun provideMessageRepository(): MessageRepository {
        return MessageRepositoryImpl()
    }
}