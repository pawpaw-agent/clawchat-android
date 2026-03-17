package com.openclaw.clawchat.di

import android.content.Context
import com.openclaw.clawchat.network.NetworkModule
import com.openclaw.clawchat.network.OkHttpWebSocketService
import com.openclaw.clawchat.network.WebSocketService
import com.openclaw.clawchat.repository.SessionRepository
import com.openclaw.clawchat.security.SecurityModule
import dagger.Binds
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
     * 会话仓库
     */
    @Provides
    @Singleton
    fun provideSessionRepository(): SessionRepository {
        return SessionRepository()
    }
}

/**
 * 网络模块绑定
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkBindings {

    @Provides
    @Singleton
    fun provideWebSocketService(
        okHttpClient: okhttp3.OkHttpClient,
        securityManager: com.openclaw.clawchat.security.SecurityManager,
        appScope: CoroutineScope
    ): WebSocketService {
        return OkHttpWebSocketService(okHttpClient, securityManager, appScope)
    }
}
