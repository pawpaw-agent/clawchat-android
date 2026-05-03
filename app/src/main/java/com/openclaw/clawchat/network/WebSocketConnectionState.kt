package com.openclaw.clawchat.network

/**
 * WebSocket 连接状态
 * Based on OpenClaw Gateway Protocol v2026.4.29
 */
sealed class WebSocketConnectionState {
    /** 已断开（初始状态或已关闭） */
    data object Disconnected : WebSocketConnectionState()

    /** 连接中（WebSocket 正在建立） */
    data object Connecting : WebSocketConnectionState()

    /** 认证中（等待 connect.challenge -> hello-ok） */
    data object Authenticating : WebSocketConnectionState()

    /** 已连接（认证完成） */
    data object Connected : WebSocketConnectionState()

    /** 正在重连（已计划延迟重连） */
    data object Reconnecting : WebSocketConnectionState()

    /** 连接超时（长时间无 tick） */
    data object Stale : WebSocketConnectionState()

    /** 错误状态（不可恢复） */
    data class Error(val throwable: Throwable) : WebSocketConnectionState()
}
