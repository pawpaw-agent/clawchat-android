package com.openclaw.clawchat.util

import com.openclaw.clawchat.network.WebSocketConnectionState
import com.openclaw.clawchat.ui.state.ConnectionStatus

/**
 * WebSocket 连接状态到 UI 连接状态的映射器
 *
 * 提取自 SessionViewModel.kt 和 MainViewModel.kt 中的重复代码
 */
object ConnectionStatusMapper {

    /**
     * 将 WebSocket 连接状态映射为 UI 连接状态
     */
    fun WebSocketConnectionState.toStatus(): ConnectionStatus = when (this) {
        is WebSocketConnectionState.Connected -> ConnectionStatus.Connected()
        is WebSocketConnectionState.Connecting -> ConnectionStatus.Connecting
        is WebSocketConnectionState.Authenticating -> ConnectionStatus.Connecting
        is WebSocketConnectionState.Stale -> ConnectionStatus.Stale
        is WebSocketConnectionState.Disconnecting -> ConnectionStatus.Disconnecting
        is WebSocketConnectionState.Disconnected -> ConnectionStatus.Disconnected
        is WebSocketConnectionState.Error -> ConnectionStatus.Error(
            this.throwable?.message ?: "Unknown error",
            this.throwable
        )
    }
}