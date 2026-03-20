package com.openclaw.clawchat.network

import android.content.Context
import com.openclaw.clawchat.BuildConfig
import com.openclaw.clawchat.network.protocol.GatewayConnection
import com.openclaw.clawchat.security.SecurityModule
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * 网络层依赖注入模块
 *
 * 提供 OkHttpClient、GatewayConnection、WebSocketService、TailscaleManager 等单例
 *
 * OkHttpClient 配置：
 * - 信任系统证书 + Gateway 自签名证书
 * - 30 秒超时
 * - 30 秒 WebSocket 心跳
 * - 自动重试
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(
        @ApplicationContext context: Context,
        gatewayTrustManager: GatewayTrustManager
    ): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor { message ->
            LogSecure.d(message.redactSensitive())
        }.apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

        val trustManager = gatewayTrustManager.createTrustManager()
        val sslSocketFactory = gatewayTrustManager.getSslSocketFactory()

        return OkHttpClient.Builder()
            .sslSocketFactory(sslSocketFactory, trustManager)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .pingInterval(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    /**
     * GatewayConnection — 协议 v3 完整实现
     *
     * 单例：所有 ViewModel 共享同一连接
     */
    @Provides
    @Singleton
    fun provideGatewayConnection(
        okHttpClient: OkHttpClient,
        securityModule: SecurityModule,
        appScope: CoroutineScope
    ): GatewayConnection {
        return GatewayConnection(okHttpClient, securityModule, appScope)
    }

    /**
     * WebSocketService — 薄代理，委托给 GatewayConnection
     */
    @Provides
    @Singleton
    fun provideWebSocketService(
        gateway: GatewayConnection
    ): WebSocketService {
        return OkHttpWebSocketService(gateway)
    }

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
 */
object LogSecure {
    private const val TAG = "Network"

    fun d(message: String) { android.util.Log.d(TAG, message) }
    fun i(message: String) { android.util.Log.i(TAG, message) }
    fun w(message: String) { android.util.Log.w(TAG, message) }
    fun e(message: String, throwable: Throwable? = null) { android.util.Log.e(TAG, message, throwable) }
}

/**
 * 网络层安全日志（使用统一 SecureLogger）
 */
object SecureLogger {
    private const val TAG = "ClawChat-Network"

    fun d(message: String) { android.util.Log.d(TAG, message) }
    fun i(message: String) { android.util.Log.i(TAG, message) }
    fun w(message: String) { android.util.Log.w(TAG, message) }
    fun e(message: String, throwable: Throwable? = null) {
        android.util.Log.e(TAG, message, throwable)
    }
}

private fun String.redactSensitive(): String {
    return this
        .replace(Regex("""Bearer\s+[a-zA-Z0-9\-_\.]+"""), "Bearer [REDACTED]")
        .replace(Regex("""token":\s*"[a-zA-Z0-9\-_\.]+"""), "token: [REDACTED]")
}
