package com.openclaw.clawchat.network

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * WebSocket 连接状态
 * Based on OpenClaw Gateway Protocol v4.24+
 */
sealed class WebSocketConnectionState {
    /** 已断开 */
    data object Disconnected : WebSocketConnectionState()

    /** 连接中 */
    data object Connecting : WebSocketConnectionState()

    /** 认证中 (等待 hello-ok) */
    data object Authenticating : WebSocketConnectionState()

    /** 已连接 */
    data object Connected : WebSocketConnectionState()

    /** 正在断开 */
    data class Disconnecting(val reason: String) : WebSocketConnectionState()

    /** 连接超时（长时间无 tick，正在尝试重连） */
    data object Stale : WebSocketConnectionState()

    /** 错误状态 */
    data class Error(val throwable: Throwable) : WebSocketConnectionState()
}

/** Stale detection thresholds based on OpenClaw TICK_CONFIG */
object TickConfig {
    const val STALE_CHECK_INTERVAL_MS = 15_000L
    const val STALE_THRESHOLD_MS = 90_000L
}
