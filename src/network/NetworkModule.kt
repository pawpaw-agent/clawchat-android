package com.clawchat.android.network

import android.content.Context
import com.clawchat.android.security.SecurityManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.CertificatePinner
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
     * 应用级别的 CoroutineScope
     */
    @Provides
    @Singleton
    fun provideAppScope(): CoroutineScope {
        return CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
    
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
        securityManager: SecurityManager,
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
        
        // 签名拦截器
        val signatureInterceptor = SignatureInterceptor(securityManager)
        
        val builder = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .addInterceptor(signatureInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .pingInterval(30, TimeUnit.SECONDS) // WebSocket 心跳
            .retryOnConnectionFailure(true)
        
        // 生产环境添加证书固定
        if (!BuildConfig.DEBUG) {
            val certificatePinner = CertificatePinner.Builder()
                // TODO: 替换为实际的证书指纹
                .add("*.openclaw.ai", "sha256/production_pin_1")
                .add("*.openclaw.ai", "sha256/production_pin_2")
                .build()
            builder.certificatePinner(certificatePinner)
        }
        
        return builder.build()
    }
    
    /**
     * WebSocket 服务
     */
    @Provides
    @Singleton
    fun provideWebSocketService(
        okHttpClient: OkHttpClient,
        securityManager: SecurityManager,
        appScope: CoroutineScope
    ): WebSocketService {
        return OkHttpWebSocketService(okHttpClient, securityManager, appScope)
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
