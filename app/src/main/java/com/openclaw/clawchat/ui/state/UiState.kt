package com.openclaw.clawchat.ui.state

import androidx.compose.runtime.Stable

/**
 * Gateway 更新信息
 */
@Stable
data class UpdateInfo(
    val version: String = "",
    val message: String = ""
)

/**
 * Compaction 状态（参考 webchat CompactionStatus）
 * v2026.4.8: phase 替代 active 布尔值
 */
@Stable
data class CompactionStatus(
    val phase: String = "active",           // active, retrying, complete
    val runId: String? = null,
    val startedAt: Long? = null,
    val completedAt: Long? = null
) {
    val isActive: Boolean get() = phase == "active"
    val isRetrying: Boolean get() = phase == "retrying"
    val isComplete: Boolean get() = phase == "complete"
}

/**
 * Fallback 指示器状态（参考 webchat FallbackStatus）
 */
@Stable
data class FallbackStatus(
    val phase: String = "active",           // active, cleared
    val selected: String,
    val active: String = selected,
    val previous: String? = null,
    val reason: String? = null,
    val attempts: List<String> = emptyList(),
    val occurredAt: Long = System.currentTimeMillis()
)

@Stable
data class MainUiState(
    val connectionStatus: ConnectionStatus = ConnectionStatus.Disconnected,
    val sessions: List<SessionUi> = emptyList(),
    val currentSession: SessionUi? = null,
    val gatewayConfigs: List<GatewayConfigUi> = emptyList(),
    val currentGateway: GatewayConfigUi? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val latency: Long? = null,
    val connectionError: String? = null,
    val agents: List<com.openclaw.clawchat.ui.components.AgentItem> = emptyList(),
    val models: List<com.openclaw.clawchat.ui.components.ModelItem> = emptyList(),
    val isLoadingAgentsModels: Boolean = false,
    val showCreateDialog: Boolean = false,
    val updateAvailable: UpdateInfo? = null
)

/**
 * 会话界面 UI 状态（1:1 对应 webchat ChatState + ToolStreamHost）
 */
@Stable
data class SessionUiState(
    val connectionStatus: ConnectionStatus = ConnectionStatus.Disconnected,
    val isLoading: Boolean = false,
    val isSending: Boolean = false,
    val error: String? = null,

    val sessionId: String? = null,
    val session: SessionUi? = null,

    val chatMessages: List<MessageUi> = emptyList(),
    val chatStream: String? = null,
    val chatStreamStartedAt: Long? = null,
    val chatRunId: String? = null,
    val chatStreamSegments: List<StreamSegment> = emptyList(),

    val chatQueue: List<ChatQueueItem> = emptyList(),

    val chatNewMessagesBelow: Boolean = false,
    val unreadMessageCount: Int = 0,

    val chatUserNearBottom: Boolean = false,
    val chatHasAutoScrolled: Boolean = false,

    val totalTokens: Int? = null,
    val contextTokensLimit: Int? = null,
    val totalTokensFresh: Boolean = true,

    val compactionStatus: CompactionStatus? = null,

    val fallbackStatus: FallbackStatus? = null,

    val toolStreamById: Map<String, ToolStreamEntry> = emptyMap(),
    val toolStreamOrder: List<String> = emptyList(),
    val chatToolMessages: List<MessageUi> = emptyList(),

    val attachments: List<AttachmentUi> = emptyList(),
    val isUploadingAttachment: Boolean = false,

    val slashCommandCompletion: SlashCommandCompletion = SlashCommandCompletion(),

    val inputText: String = "",

    val editingMessageId: String? = null,
    val editingMessageText: String? = null
) {
    fun clearSession(): SessionUiState = SessionUiState(
        connectionStatus = connectionStatus,
        sessionId = sessionId
    )
}
