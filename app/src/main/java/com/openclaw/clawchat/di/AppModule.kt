package com.openclaw.clawchat.di

import android.content.Context
import com.openclaw.clawchat.data.local.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

/**
 * 应用级依赖注入模块
 *
 * 提供 Database、DAO 等基础组件。
 * Repository 接口绑定由 DomainModule 处理。
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /**
     * 应用级别的 CoroutineScope
     */
    @Provides
    @Singleton
    fun provideAppScope(): CoroutineScope {
        return CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    /**
     * Room 数据库
     */
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ClawChatDatabase {
        return ClawChatDatabase.getDatabase(context)
    }

    /**
     * Message DAO
     */
    @Provides
    @Singleton
    fun provideMessageDao(database: ClawChatDatabase): MessageDao {
        return database.messageDao()
    }

    /**
     * Session DAO
     */
    @Provides
    @Singleton
    fun provideSessionDao(database: ClawChatDatabase): SessionDao {
        return database.sessionDao()
    }
}