package com.openclaw.clawchat.di

import com.openclaw.clawchat.domain.repository.GatewayRepository
import com.openclaw.clawchat.domain.repository.MessageRepository
import com.openclaw.clawchat.domain.repository.SessionRepository
import com.openclaw.clawchat.repository.GatewayRepositoryImpl
import com.openclaw.clawchat.repository.MessageRepositoryImpl
import com.openclaw.clawchat.repository.SessionRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Domain 层依赖注入模块
 *
 * 提供 Repository 接口到具体实现的绑定。
 * 遵循依赖倒置原则，上层依赖接口而非具体实现。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DomainModule {

    @Binds
    @Singleton
    abstract fun bindSessionRepository(impl: SessionRepositoryImpl): SessionRepository

    @Binds
    @Singleton
    abstract fun bindMessageRepository(impl: MessageRepositoryImpl): MessageRepository

    @Binds
    @Singleton
    abstract fun bindGatewayRepository(impl: GatewayRepositoryImpl): GatewayRepository
}