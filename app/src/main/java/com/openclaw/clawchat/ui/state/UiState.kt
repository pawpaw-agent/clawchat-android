package com.openclaw.clawchat.ui.state

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * 会话数据模型
 */
data class SessionUi(
    val id: String,
    val label: String?,
    val model: String?,
    val status: SessionStatus,
    val lastActivityAt: Long,
    val messageCount: Int = 0,
    val lastMessage: String? = null,
    val thinking: Boolean = false
)

/**
 * 会话状态
 */
enum class SessionStatus {
    RUNNING,      // 运行中
    PAUSED,       // 已暂停
    TERMINATED    // 已终止
}

/**
 * 网关配置数据模型
 */
data class GatewayConfigUi(
    val id: String,
    val name: String,
    val host: String,
    val port: Int,
    val isCurrent: Boolean = false
)

/**
 * 主界面 UI 状态
 */
data class MainUiState(
    val connectionStatus: ConnectionStatus = ConnectionStatus.Disconnected,
    val sessions: List<SessionUi> = emptyList(),
    val currentSession: SessionUi? = null,
    val gatewayConfigs: List<GatewayConfigUi> = emptyList(),
    val currentGateway: GatewayConfigUi? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val latency: Long? = null
)

/**
 * 会话界面 UI 状态
 */
data class SessionUiState(
    val sessionId: String? = null,
    val session: SessionUi? = null,
    val messages: List<MessageUi> = emptyList(),
    val inputText: String = "",
    val isSending: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val connectionStatus: ConnectionStatus = ConnectionStatus.Disconnected
)

/**
 * Gateway 配置（输入用）
 */
data class GatewayConfigInput(
    val name: String = "",
    val host: String = "",
    val port: Int = 18789
)

/**
 * 设置界面 UI 状态
 */
data class SettingsUiState(
    val currentGateway: GatewayConfigUi? = null,
    val gatewayConfigInput: GatewayConfigInput = GatewayConfigInput(),
    val connectionStatus: ConnectionStatusUi = ConnectionStatusUi.Disconnected,
    val notificationsEnabled: Boolean = true,
    val dndEnabled: Boolean = false,
    val appVersion: String = "1.0.0"
)

/**
 * 连接模式
 */
enum class ConnectMode {
    TOKEN,       // Token 直连
    PAIRING      // 设备配对（Ed25519 签名 + 管理员批准）
}

/**
 * 配对界面 UI 状态
 */
data class PairingUiState(
    val connectMode: ConnectMode = ConnectMode.TOKEN,
    val gatewayUrl: String = "",
    val isPairing: Boolean = false,
    val isInitializing: Boolean = false,
    val pairingStatus: PairingStatus = PairingStatus.Initializing,
    val deviceId: String? = null,
    val publicKey: String? = null,
    val deviceToken: String? = null,
    val pairingStartTime: Long? = null,
    val error: String? = null,
    // Token 模式
    val token: String = ""
)

/**
 * 配对状态
 */
sealed class PairingStatus {
    data object Initializing : PairingStatus()
    data object WaitingForApproval : PairingStatus()
    data object Approved : PairingStatus()
    data object Rejected : PairingStatus()
    data object Timeout : PairingStatus()
    data class Error(val message: String) : PairingStatus()
}

/**
 * 消息数据模型
 */
data class MessageUi(
    val id: String,
    val content: String,
    val role: MessageRole,
    val timestamp: Long,
    val isLoading: Boolean = false
)

/**
 * 消息角色
 */
enum class MessageRole {
    USER,
    ASSISTANT,
    SYSTEM
}

/**
 * 连接状态
 */
sealed class ConnectionStatus {
    data object Disconnected : ConnectionStatus()
    data object Connecting : ConnectionStatus()
    data class Connected(val latency: Long? = null) : ConnectionStatus()
    data object Disconnecting : ConnectionStatus()
    data class Error(val message: String, val throwable: Throwable? = null) : ConnectionStatus()

    val isConnected: Boolean
        get() = this is Connected

    val isConnecting: Boolean
        get() = this is Connecting || this is Disconnecting
}

/**
 * 连接状态（UI 显示用）
 * 
 * 包含 UI 特定的辅助属性，用于 SettingsScreen 等显示
 */
sealed class ConnectionStatusUi {
    data object Disconnected : ConnectionStatusUi()
    data object Connecting : ConnectionStatusUi()
    data object Disconnecting : ConnectionStatusUi()
    data class Connected(val latency: Long = 0) : ConnectionStatusUi()
    data class Error(val message: String) : ConnectionStatusUi()

    val isConnected: Boolean
        get() = this is Connected

    val displayText: String
        get() = when (this) {
            is Disconnected -> "未连接"
            is Connecting -> "连接中..."
            is Disconnecting -> "断开中..."
            is Connected -> if (latency > 0) "已连接 · ${latency}ms" else "已连接"
            is Error -> "错误：$message"
        }
}

/**
 * 获取连接状态的颜色（Composable 函数）
 * 
 * 使用 MaterialTheme 颜色，自动适配深色/浅色模式
 */
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

/**
 * 将 ConnectionStatus 转换为 ConnectionStatusUi
 */
fun ConnectionStatus.toUiStatus(): ConnectionStatusUi {
    return when (this) {
        is ConnectionStatus.Disconnected -> ConnectionStatusUi.Disconnected
        is ConnectionStatus.Connecting -> ConnectionStatusUi.Connecting
        is ConnectionStatus.Disconnecting -> ConnectionStatusUi.Disconnecting
        is ConnectionStatus.Connected -> ConnectionStatusUi.Connected(latency ?: 0)
        is ConnectionStatus.Error -> ConnectionStatusUi.Error(message)
    }
}
