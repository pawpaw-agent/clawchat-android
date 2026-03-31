package com.openclaw.clawchat.network

import com.openclaw.clawchat.util.AppLog
import com.openclaw.clawchat.network.protocol.GatewayConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

/**
 * WebSocketService 实现 — 委托给 GatewayConnection
 *
 * 所有连接管理、认证握手、帧收发由 GatewayConnection 处理。
 * 本类仅做接口适配，保持 WebSocketService 接口稳定。
 */
class OkHttpWebSocketService(
    val gateway: GatewayConnection
) : WebSocketService {

    companion object {
        private const val TAG = "WebSocketService"
    }

    override val connectionState: StateFlow<WebSocketConnectionState>
        get() = gateway.connectionState

    override val incomingMessages: SharedFlow<String>
        get() = gateway.incomingMessages

    override suspend fun connect(url: String, token: String?): Result<Unit> {
        AppLog.i(TAG, "Connecting to $url")
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
        AppLog.i(TAG, "Disconnecting")
        return gateway.disconnect()
    }

    override suspend fun measureLatency(): Long? {
        return gateway.measureLatency()
    }
}
