package com.openclaw.clawchat.di

import android.content.Context
import com.openclaw.clawchat.security.SecurityModule
import com.openclaw.clawchat.security.EncryptedStorage
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 安全模块依赖注入
 *
 * SecurityModule 内部自行创建 KeystoreManager 和 EncryptedStorage，
 * 因此此处只提供 SecurityModule 单例。
 *
 * 其他类需要 EncryptedStorage 时，通过 SecurityModule 暴露的实例获取，
 * 避免出现双实例（Hilt 提供一个 + SecurityModule 内部再 new 一个）。
 */
@Module
@InstallIn(SingletonComponent::class)
object SecurityModuleBindings {

    @Provides
    @Singleton
    fun provideSecurityModule(
        @ApplicationContext context: Context
    ): SecurityModule {
        return SecurityModule(context)
    }

    /**
     * 从 SecurityModule 暴露 EncryptedStorage 单例
     *
     * GatewayRepository 等外部类通过此方法注入 EncryptedStorage，
     * 保证与 SecurityModule 内部使用的是同一个实例。
     */
    @Provides
    @Singleton
    fun provideEncryptedStorage(
        securityModule: SecurityModule
    ): EncryptedStorage {
        return securityModule.getEncryptedStorage()
    }
}
