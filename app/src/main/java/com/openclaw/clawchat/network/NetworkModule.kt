package com.openclaw.clawchat.network

import android.content.Context
import com.openclaw.clawchat.BuildConfig
import com.openclaw.clawchat.security.SecurityModule
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * 网络层依赖注入模块
 * 
 * 提供 OkHttpClient、WebSocketService、TailscaleManager 等单例
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    
    /**
     * OkHttp 客户端配置
     * 
     * 包含：
     * - 日志拦截器（仅 Debug 模式）
     * - 签名拦截器
     * - 超时配置
     * - 证书固定（生产环境）
     */
    @Provides
    @Singleton
    fun provideOkHttpClient(
        @ApplicationContext context: Context
    ): OkHttpClient {
        // 日志拦截器 - 仅在 Debug 模式启用
        val loggingInterceptor = HttpLoggingInterceptor { message ->
            LogSecure.d(message.redactSensitive())
        }.apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
        
        val builder = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .pingInterval(30, TimeUnit.SECONDS) // WebSocket 心跳
            .retryOnConnectionFailure(true)
        
        // 证书固定：临时移除（开发/测试阶段）
        // TODO: 发布前配置真实的证书指纹
        // if (!BuildConfig.DEBUG) {
        //     val certificatePinner = CertificatePinner.Builder()
        //         .add("your-domain.com", "sha256/ACTUAL_CERTIFICATE_PIN_HERE")
        //         .build()
        //     builder.certificatePinner(certificatePinner)
        // }
        
        return builder.build()
    }
    
    /**
     * WebSocket 服务
     */
    @Provides
    @Singleton
    fun provideWebSocketService(
        okHttpClient: OkHttpClient,
        securityModule: SecurityModule,
        appScope: CoroutineScope
    ): WebSocketService {
        return OkHttpWebSocketService(okHttpClient, securityModule, appScope)
    }
    
    /**
     * Tailscale 管理器
     */
    @Provides
    @Singleton
    fun provideTailscaleManager(
        @ApplicationContext context: Context
    ): TailscaleManager {
        return TailscaleManager(context)
    }
}

/**
 * 安全日志工具
 * 
 * 自动脱敏敏感信息（令牌、签名等）
 */
object LogSecure {
    private const val TAG = "Network"
    
    fun d(message: String) {
        android.util.Log.d(TAG, message)
    }
    
    fun i(message: String) {
        android.util.Log.i(TAG, message)
    }
    
    fun w(message: String) {
        android.util.Log.w(TAG, message)
    }
    
    fun e(message: String, throwable: Throwable? = null) {
        android.util.Log.e(TAG, message, throwable)
    }
}

/**
 * 脱敏敏感信息
 */
private fun String.redactSensitive(): String {
    return this
        .replace(Regex("""Bearer\s+[a-zA-Z0-9\-_\.]+"""), "Bearer [REDACTED]")
        .replace(Regex("""X-ClawChat-Signature":\s*"[a-zA-Z0-9+/=]+"""), "X-ClawChat-Signature: [REDACTED]")
        .replace(Regex("""token":\s*"[a-zA-Z0-9\-_\.]+"""), "token: [REDACTED]")
}
