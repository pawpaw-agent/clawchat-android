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
    val status: SessionStatus,
    val lastActivityAt: Long,
    val messageCount: Int = 0,
    val lastMessage: String? = null,
    val thinking: Boolean = false
) {
    fun getDisplayName(): String {
        val agentName = agentId?.removePrefix("agent:")?.substringBefore(":") ?: agentId
        return when {
            !agentName.isNullOrBlank() -> agentName
            !label.isNullOrBlank() -> label
            !model.isNullOrBlank() -> model
            else -> "未命名会话"
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
    val connectionError: String? = null  // 自动连接失败时的错误信息
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
    val inputText: String = ""
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
    val startedAt: Long,
    val updatedAt: Long
) {
    /**
     * 构建工具消息（1:1 对应 webchat buildToolStreamMessage）
     */
    fun buildMessage(): MessageUi {
        val content = mutableListOf<MessageContentItem>()
        
        // 添加 toolcall
        content.add(MessageContentItem.ToolCall(
            id = toolCallId,
            name = name,
            args = args
        ))
        
        // 添加 toolresult（如果有输出）
        if (!output.isNullOrBlank()) {
            content.add(MessageContentItem.ToolResult(
                toolCallId = toolCallId,
                name = name,
                args = args,
                text = output
            ))
        }
        
        return MessageUi(
            id = "tool:$toolCallId",
            content = content,
            role = MessageRole.ASSISTANT,
            timestamp = startedAt,
            isLoading = output.isNullOrBlank()
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
    val notificationsEnabled: Boolean = true,
    val dndEnabled: Boolean = false,
    val appVersion: String = "1.0.0",
    // 字体大小设置（统一）
    val messageFontSize: FontSize = FontSize.MEDIUM,
    // 主题模式设置
    val themeMode: com.openclaw.clawchat.data.ThemeMode = com.openclaw.clawchat.data.ThemeMode.SYSTEM,
    // 动态颜色（Material You）
    val dynamicColor: Boolean = true
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
        val args: JsonObject? = null
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
            return when (role.lowercase().replace("_", "")) {
                "user" -> USER
                "assistant" -> ASSISTANT
                "system" -> SYSTEM
                "toolresult", "tool" -> TOOL
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
    val callId: String? = null
)

enum class ToolCardKind {
    CALL,
    RESULT
}

// ─────────────────────────────────────────────────────────────
// 连接状态
// ─────────────────────────────────────────────────────────────

sealed class ConnectionStatus {
    data object Disconnected : ConnectionStatus()
    data object Connecting : ConnectionStatus()
    data class Connected(val latency: Long? = null) : ConnectionStatus()
    data object Disconnecting : ConnectionStatus()
    data class Error(val message: String, val throwable: Throwable? = null) : ConnectionStatus()

    val isConnected: Boolean get() = this is Connected
    val isConnecting: Boolean get() = this is Connecting || this is Disconnecting
}

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