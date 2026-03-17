package com.openclaw.clawchat.ui.state

import com.openclaw.clawchat.network.GatewayMessage

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
    val useTls: Boolean,
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
 * 设置界面 UI 状态
 */
data class SettingsUiState(
    val gatewayConfigs: List<GatewayConfigUi> = emptyList(),
    val currentGateway: GatewayConfigUi? = null,
    val isEditingGateway: Boolean = false,
    val editingGateway: GatewayConfigUi? = null,
    val error: String? = null,
    val isLoading: Boolean = false
)

/**
 * 配对界面 UI 状态
 */
data class PairingUiState(
    val gatewayUrl: String = "",
    val isPairing: Boolean = false,
    val isInitializing: Boolean = false,
    val pairingStatus: PairingStatus = PairingStatus.Idle,
    val deviceId: String? = null,
    val publicKey: String? = null,
    val deviceToken: String? = null,
    val pairingStartTime: Long? = null,
    val error: String? = null
)

/**
 * 配对状态
 */
enum class PairingStatus {
    Idle,
    Requesting,
    WaitingForApproval,
    Paired,
    Failed
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
