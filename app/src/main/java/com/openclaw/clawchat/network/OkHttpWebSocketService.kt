package com.openclaw.clawchat.network

import android.util.Log
import com.openclaw.clawchat.network.protocol.GatewayConnection
import com.openclaw.clawchat.security.SecurityModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import javax.inject.Inject

/**
 * WebSocketService 实现 — 委托给 GatewayConnection
 *
 * 所有连接管理、认证握手、帧收发由 GatewayConnection 处理。
 * 本类仅做接口适配，保持 WebSocketService 接口稳定。
 */
class OkHttpWebSocketService @Inject constructor(
    okHttpClient: OkHttpClient,
    securityModule: SecurityModule,
    appScope: CoroutineScope
) : WebSocketService {

    companion object {
        private const val TAG = "WebSocketService"
    }

    /** 内部 GatewayConnection（完整协议 v3 实现） */
    val gateway = GatewayConnection(okHttpClient, securityModule, appScope)

    override val connectionState: StateFlow<WebSocketConnectionState>
        get() = gateway.connectionState

    override val incomingMessages: SharedFlow<String>
        get() = gateway.incomingMessages

    override suspend fun connect(url: String, token: String?): Result<Unit> {
        Log.i(TAG, "Connecting to $url")
        return gateway.connect(url, token)
    }

    override suspend fun sendRaw(json: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            val sent = gateway.sendFrame(json)
            if (sent) Result.success(Unit)
            else Result.failure(IllegalStateException("WebSocket not connected"))
        }
    }

    override suspend fun disconnect(): Result<Unit> {
        Log.i(TAG, "Disconnecting")
        return gateway.disconnect()
    }

    override suspend fun measureLatency(): Long? {
        return gateway.measureLatency()
    }
}
