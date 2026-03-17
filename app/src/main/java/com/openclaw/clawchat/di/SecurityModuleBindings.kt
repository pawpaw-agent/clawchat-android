package com.openclaw.clawchat.di

import android.content.Context
import com.openclaw.clawchat.security.SecurityModule
import com.openclaw.clawchat.security.KeystoreManager
import com.openclaw.clawchat.security.EncryptedStorage
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 安全模块依赖注入
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
    
    @Provides
    @Singleton
    fun provideKeystoreManager(): KeystoreManager {
        return KeystoreManager("clawchat_device_key")
    }
    
    @Provides
    @Singleton
    fun provideEncryptedStorage(
        @ApplicationContext context: Context
    ): EncryptedStorage {
        return EncryptedStorage(context)
    }
}
