package com.openclaw.clawchat.ui.state

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color
import com.openclaw.clawchat.data.FontSize
import kotlinx.serialization.json.JsonObject

/**
 * 会话数据模型
 */
@Stable
data class SessionUi(
    val id: String,
    val label: String?,
    val model: String?,
    val agentId: String? = null,
    val agentName: String? = null,  // Agent 显示名称
    val agentEmoji: String? = null,  // Agent emoji 图标
    val status: SessionStatus,
    val lastActivityAt: Long,
    val messageCount: Int = 0,
    val lastMessage: String? = null,
    val thinking: Boolean = false,
    val isPinned: Boolean = false,  // 置顶
    val isArchived: Boolean = false, // 归档
    // Context 用量（参考 webchat GatewaySessionRow）
    val totalTokens: Int? = null,           // 已使用的 token 数
    val contextTokens: Int? = null,         // context 窗口大小
    val totalTokensFresh: Boolean = true    // token 数据是否新鲜
) {
    fun getDisplayName(): String {
        return when {
            !agentName.isNullOrBlank() -> agentName
            !agentId.isNullOrBlank() -> agentId.removePrefix("agent:").substringBefore(":")
            !label.isNullOrBlank() -> label
            !model.isNullOrBlank() -> model
            else -> "Unnamed session"  // Use string resource in Composable context
        }
    }

    @Composable
    fun getDisplayNameLocalized(): String {
        return when {
            !agentName.isNullOrBlank() -> agentName
            !agentId.isNullOrBlank() -> agentId.removePrefix("agent:").substringBefore(":")
            !label.isNullOrBlank() -> label
            !model.isNullOrBlank() -> model
            else -> androidx.compose.ui.res.stringResource(com.openclaw.clawchat.R.string.session_unnamed)
        }
    }
}

enum class SessionStatus {
    RUNNING,
    PAUSED,
    TERMINATED
}

@Stable
data class GatewayConfigUi(
    val id: String,
    val name: String,
    val host: String,
    val port: Int,
    val isCurrent: Boolean = false
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
    val connectionError: String? = null,  // 自动连接失败时的错误信息
    // Agent 和 Model 选择
    val agents: List<com.openclaw.clawchat.ui.components.AgentItem> = emptyList(),
    val models: List<com.openclaw.clawchat.ui.components.ModelItem> = emptyList(),
    val isLoadingAgentsModels: Boolean = false,
    val showCreateDialog: Boolean = false,
    // 更新通知
    val updateAvailable: UpdateInfo? = null
)

/**
 * Gateway 更新信息
 */
@Stable
data class UpdateInfo(
    val version: String = "",
    val message: String = ""
)

/**
 * 附件数据（用于 UI 显示）
 */
@Stable
data class AttachmentUi(
    val id: String,
    val uri: android.net.Uri,
    val mimeType: String,
    val fileName: String? = null,
    val dataUrl: String? = null,  // base64 data URL
    val isLoading: Boolean = false,
    val error: String? = null
)

/**
 * 斜杠命令补全状态
 */
data class SlashCommandCompletion(
    val commands: List<com.openclaw.clawchat.ui.components.SlashCommandDef> = emptyList(),
    val selectedIndex: Int = 0,
    val visible: Boolean = false
)

/**
 * Compaction 状态（参考 webchat CompactionStatus）
 * v2026.4.8: phase 替代 active 布尔值
 */
@Stable
data class CompactionStatus(
    val phase: String = "active",           // active, retrying, complete
    val runId: String? = null,              // 当前 runId
    val startedAt: Long? = null,            // 开始时间戳
    val completedAt: Long? = null           // 完成时间戳
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
    val selected: String,                   // 当前选中的模型
    val active: String = selected,          // fallback active 时的模型
    val previous: String? = null,           // 之前的 fallback
    val reason: String? = null,             // fallback 原因
    val attempts: List<String> = emptyList(), // 尝试过的模型列表
    val occurredAt: Long = System.currentTimeMillis() // 发生时间
)

/**
 * 消息队列项（busy 时排队等待发送）
 */
@Stable
data class ChatQueueItem(
    val id: String,
    val text: String,
    val timestamp: Long,
    val attachments: List<AttachmentUi> = emptyList()
)

/**
 * 会话界面 UI 状态（1:1 对应 webchat ChatState + ToolStreamHost）
 */
@Stable
data class SessionUiState(
    // 连接状态
    val connectionStatus: ConnectionStatus = ConnectionStatus.Disconnected,
    val isLoading: Boolean = false,
    val isSending: Boolean = false,
    val error: String? = null,

    // 会话信息
    val sessionId: String? = null,
    val session: SessionUi? = null,

    // 消息状态（1:1 对应 webchat）
    val chatMessages: List<MessageUi> = emptyList(),           // 历史消息
    val chatStream: String? = null,                             // 当前流式文本
    val chatStreamStartedAt: Long? = null,                      // 流式开始时间
    val chatRunId: String? = null,                              // 当前 runId
    val chatStreamSegments: List<StreamSegment> = emptyList(),  // 已提交的文本段

    // 消息队列（busy 时排队）
    val chatQueue: List<ChatQueueItem> = emptyList(),

    // 新消息提示（用户滚动后有新消息到达时显示）
    val chatNewMessagesBelow: Boolean = false,
    val unreadMessageCount: Int = 0,              // 未读消息计数（显示在按钮上）

    // 滚动状态（参考 webchat app-scroll.ts）
    val chatUserNearBottom: Boolean = false,     // 用户是否在底部附近（初始为 false，滚动后设为 true）
    val chatHasAutoScrolled: Boolean = false,    // 是否已经自动滚动过（初始加载后设为 true）

    // Context 用量警告（参考 webchat renderContextNotice）
    val totalTokens: Int? = null,                 // 已使用的 token 数
    val contextTokensLimit: Int? = null,          // token 限制
    val totalTokensFresh: Boolean = true,         // token 数据是否新鲜

    // Compaction 指示器（参考 webchat renderCompactionIndicator）
    // v2026.4.8: 使用 CompactionStatus 替代 compactionActive + compactionCompletedAt
    val compactionStatus: CompactionStatus? = null,

    // Fallback 指示器（参考 webchat renderFallbackIndicator）
    val fallbackStatus: FallbackStatus? = null,   // 模型切换状态

    // 工具流状态（1:1 对应 webchat ToolStreamHost）
    val toolStreamById: Map<String, ToolStreamEntry> = emptyMap(),
    val toolStreamOrder: List<String> = emptyList(),
    val chatToolMessages: List<MessageUi> = emptyList(),
    
    // 附件状态
    val attachments: List<AttachmentUi> = emptyList(),
    val isUploadingAttachment: Boolean = false,

    // 斜杠命令补全
    val slashCommandCompletion: SlashCommandCompletion = SlashCommandCompletion(),

    // 输入
    val inputText: String = "",

    // 编辑状态
    val editingMessageId: String? = null,      // 正在编辑的消息 ID
    val editingMessageText: String? = null    // 编辑中的文本
) {
    /**
     * 清除会话数据，保留 sessionId 和 connectionStatus
     * 用于切换会话或清除命令
     */
    fun clearSession(): SessionUiState = SessionUiState(
        connectionStatus = connectionStatus,
        sessionId = sessionId
    )
}

/**
 * 文本段（工具执行前提交的文本）
 */
@Stable
data class StreamSegment(
    val text: String,
    val ts: Long
)

/**
 * 工具流条目（1:1 对应 webchat ToolStreamEntry）
 */
@Stable
data class ToolStreamEntry(
    val toolCallId: String,
    val runId: String,
    val sessionKey: String? = null,
    val name: String = "tool",
    val args: JsonObject? = null,
    val output: String? = null,
    val phase: String = "start",  // start, update, result
    val isError: Boolean = false,
    val startedAt: Long,
    val updatedAt: Long
) {
    /**
     * 构建工具消息（1:1 对应 webchat buildToolStreamMessage）
     */
    fun buildMessage(): MessageUi {
        val content = mutableListOf<MessageContentItem>()
        
        // 添加 toolcall（包含 phase 信息）
        content.add(MessageContentItem.ToolCall(
            id = toolCallId,
            name = name,
            args = args,
            phase = phase  // 传递 phase 用于判断完成状态
        ))
        
        // 添加 toolresult（如果有输出或已完成）
        // phase == "result" 时即使 output 为空也添加，表示完成
        if (!output.isNullOrBlank() || phase == "result") {
            content.add(MessageContentItem.ToolResult(
                toolCallId = toolCallId,
                name = name,
                args = args,
                text = output ?: "",  // 已完成但没有输出时使用空字符串
                isError = isError
            ))
        }
        
        return MessageUi(
            id = "tool:$toolCallId",
            content = content,
            role = MessageRole.ASSISTANT,
            timestamp = startedAt,
            isLoading = phase != "result"  // phase=result 表示已完成
        )
    }
}

data class GatewayConfigInput(
    val name: String = "",
    val host: String = "",
    val port: Int = 18789
)

data class SettingsUiState(
    val currentGateway: GatewayConfigUi? = null,
    val gatewayConfigInput: GatewayConfigInput = GatewayConfigInput(),
    val connectionStatus: ConnectionStatusUi = ConnectionStatusUi.Disconnected,
    val isPaired: Boolean = false,
    val appVersion: String = "1.0.0",
    // 字体大小设置（统一）
    val messageFontSize: FontSize = FontSize.MEDIUM,
    // 主题模式设置
    val themeMode: com.openclaw.clawchat.data.ThemeMode = com.openclaw.clawchat.data.ThemeMode.SYSTEM,
    // 动态颜色（Material You）
    val dynamicColor: Boolean = true,
    // 主题色索引
    val themeColorIndex: Int = 0,
    // 安全状态
    val isRooted: Boolean = false,
    val rootRiskLevel: com.openclaw.clawchat.security.RootDetector.RootCheckResult.RiskLevel =
        com.openclaw.clawchat.security.RootDetector.RootCheckResult.RiskLevel.NONE
)

data class PairingUiState(
    val gatewayUrl: String = "",
    val isPairing: Boolean = false,
    val isInitializing: Boolean = false,
    val pairingStatus: PairingStatus = PairingStatus.Initializing,
    val deviceId: String? = null,
    val publicKey: String? = null,
    val deviceToken: String? = null,
    val pairingStartTime: Long? = null,
    val error: String? = null
)

sealed class PairingStatus {
    data object Initializing : PairingStatus()
    data object WaitingForApproval : PairingStatus()
    data object Approved : PairingStatus()
    data object Rejected : PairingStatus()
    data object Timeout : PairingStatus()
    data class Error(val message: String) : PairingStatus()
}

// ─────────────────────────────────────────────────────────────
// 消息相关数据模型
// ─────────────────────────────────────────────────────────────

/**
 * 消息发送状态
 */
enum class MessageStatus {
    SENDING,    // 发送中
    SENT,       // 已发送
    DELIVERED,  // 已送达
    FAILED      // 发送失败
}

/**
 * 消息数据模型
 */
@Stable
data class MessageUi(
    val id: String,
    val content: List<MessageContentItem>,
    val role: MessageRole,
    val timestamp: Long,
    val isLoading: Boolean = false,
    val toolCallId: String? = null,  // TOOL 消息可能有 toolCallId
    val toolName: String? = null,    // TOOL 消息可能有 toolName
    val status: MessageStatus = MessageStatus.SENT  // 消息发送状态
) {
    fun getTextContent(): String {
        return content
            .filterIsInstance<MessageContentItem.Text>()
            .joinToString("\n") { it.text }
    }
    
    fun getToolCalls(): List<MessageContentItem.ToolCall> {
        return content.filterIsInstance<MessageContentItem.ToolCall>()
    }
    
    fun getToolResults(): List<MessageContentItem.ToolResult> {
        val fromContent = content.filterIsInstance<MessageContentItem.ToolResult>()
        
        // 如果消息本身有 toolCallId，把整条消息当作 ToolResult
        if (fromContent.isEmpty() && role == MessageRole.TOOL && toolCallId != null) {
            val textContent = getTextContent()
            if (textContent.isNotBlank()) {
                return listOf(MessageContentItem.ToolResult(
                    toolCallId = toolCallId,
                    name = toolName,
                    args = null,
                    text = textContent,
                    isError = false
                ))
            }
        }
        
        return fromContent
    }
    
    fun hasToolContent(): Boolean = content.any { 
        it is MessageContentItem.ToolCall || it is MessageContentItem.ToolResult 
    }
}

/**
 * 消息内容项
 */
@Stable
sealed class MessageContentItem {
    @Stable
    data class Text(val text: String) : MessageContentItem()
    @Stable
    data class ToolCall(
        val id: String? = null,
        val name: String,
        val args: JsonObject? = null,
        val phase: String = "start"  // start, update, result
    ) : MessageContentItem()
    @Stable
    data class ToolResult(
        val toolCallId: String? = null,
        val name: String? = null,
        val args: JsonObject? = null,
        val text: String,
        val isError: Boolean = false
    ) : MessageContentItem()
    @Stable
    data class Image(
        val url: String? = null,
        val base64: String? = null,
        val mimeType: String? = null
    ) : MessageContentItem()
}

enum class MessageRole {
    USER,
    ASSISTANT,
    SYSTEM,
    TOOL;

    companion object {
        fun fromString(role: String): MessageRole {
            return when (role.lowercase().replace("_", "").replace("-", "")) {
                "user" -> USER
                "assistant" -> ASSISTANT
                "system" -> SYSTEM
                "toolresult", "tool", "toolresultmessage" -> TOOL
                else -> ASSISTANT
            }
        }
    }
}

/**
 * 消息分组
 */
@Stable
data class MessageGroup(
    val role: MessageRole,
    val messages: List<MessageUi>,
    val timestamp: Long,
    val isStreaming: Boolean = false
) {
    val firstMessage: MessageUi? get() = messages.firstOrNull()
    val lastMessage: MessageUi? get() = messages.lastOrNull()
    
    fun getDisplayContent(): String {
        return messages.joinToString("\n\n") { it.getTextContent() }
    }
}

/**
 * 工具卡片数据
 */
@Stable
data class ToolCard(
    val kind: ToolCardKind,
    val name: String,
    val args: String? = null,
    val result: String? = null,
    val isError: Boolean = false,
    val callId: String? = null,
    val phase: String = "start"  // start, update, result - 用于判断完成状态
)

enum class ToolCardKind {
    CALL,
    RESULT
}

// ─────────────────────────────────────────────────────────────
// 连接状态
// ─────────────────────────────────────────────────────────────

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
        is ConnectionStatus.Disconnecting -> ConnectionStatusUi.Disconnecting
        is ConnectionStatus.Connected -> ConnectionStatusUi.Connected(latency ?: 0)
        is ConnectionStatus.Error -> ConnectionStatusUi.Error(message)
    }
}