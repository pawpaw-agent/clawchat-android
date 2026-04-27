package com.openclaw.clawchat.ui.state

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * 连接状态
 * 用于 MainUiState 和 SessionUiState
 */
sealed class ConnectionStatus {
    data object Disconnected : ConnectionStatus()
    data object Connecting : ConnectionStatus()
    data object Stale : ConnectionStatus()
    data class Connected(val latency: Long? = null) : ConnectionStatus()
    data object Disconnecting : ConnectionStatus()
    data class Error(val message: String, val throwable: Throwable? = null) : ConnectionStatus()

    val isConnected: Boolean get() = this is Connected
    val isConnecting: Boolean get() = this is Connecting || this is Disconnecting || this is Stale

    val displayText: String
        get() = when (this) {
            is Disconnected -> "未连接"
            is Connecting -> "连接中..."
            is Stale -> "连接超时，重连中..."
            is Disconnecting -> "断开中..."
            is Connected -> if (latency != null && latency > 0) "已连接 · ${latency}ms" else "已连接"
            is Error -> "错误：$message"
        }
}

/**
 * 连接状态 UI 版本
 * 用于设置页面，包含额外的 UI 信息
 */
sealed class ConnectionStatusUi {
    data object Disconnected : ConnectionStatusUi()
    data object Connecting : ConnectionStatusUi()
    data object Disconnecting : ConnectionStatusUi()
    data class Connected(val latency: Long = 0) : ConnectionStatusUi()
    data class Error(val message: String) : ConnectionStatusUi()

    val isConnected: Boolean get() = this is Connected
    val displayText: String
        get() = when (this) {
            is Disconnected -> "未连接"
            is Connecting -> "连接中..."
            is Disconnecting -> "断开中..."
            is Connected -> if (latency > 0) "已连接 · ${latency}ms" else "已连接"
            is Error -> "错误：$message"
        }
}

@Composable
fun ConnectionStatusUi.getStatusColor(): Color {
    return when (this) {
        is ConnectionStatusUi.Disconnected -> MaterialTheme.colorScheme.onSurfaceVariant
        is ConnectionStatusUi.Connecting -> MaterialTheme.colorScheme.tertiary
        is ConnectionStatusUi.Disconnecting -> MaterialTheme.colorScheme.onSurfaceVariant
        is ConnectionStatusUi.Connected -> MaterialTheme.colorScheme.primary
        is ConnectionStatusUi.Error -> MaterialTheme.colorScheme.error
    }
}

fun ConnectionStatus.toUiStatus(): ConnectionStatusUi {
    return when (this) {
        is ConnectionStatus.Disconnected -> ConnectionStatusUi.Disconnected
        is ConnectionStatus.Connecting -> ConnectionStatusUi.Connecting
        is ConnectionStatus.Stale -> ConnectionStatusUi.Connecting
        is ConnectionStatus.Disconnecting -> ConnectionStatusUi.Disconnecting
        is ConnectionStatus.Connected -> ConnectionStatusUi.Connected(latency ?: 0)
        is ConnectionStatus.Error -> ConnectionStatusUi.Error(message)
    }
}
