package com.openclaw.clawchat.ui.state

import androidx.compose.runtime.Stable
import kotlinx.serialization.json.JsonObject

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
 * 消息发送状态
 */
enum class MessageStatus {
    SENDING,
    SENT,
    DELIVERED,
    FAILED
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
    val toolCallId: String? = null,
    val toolName: String? = null,
    val status: MessageStatus = MessageStatus.SENT
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
    val phase: String = "start"
)

enum class ToolCardKind {
    CALL,
    RESULT
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
 * 附件数据（用于 UI 显示）
 */
@Stable
data class AttachmentUi(
    val id: String,
    val uri: android.net.Uri,
    val mimeType: String,
    val fileName: String? = null,
    val dataUrl: String? = null,
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
    val phase: String = "start",
    val isError: Boolean = false,
    val startedAt: Long,
    val updatedAt: Long
) {
    fun buildMessage(): MessageUi {
        val content = mutableListOf<MessageContentItem>()
        content.add(MessageContentItem.ToolCall(
            id = toolCallId,
            name = name,
            args = args,
            phase = phase
        ))
        if (!output.isNullOrBlank() || phase == "result") {
            content.add(MessageContentItem.ToolResult(
                toolCallId = toolCallId,
                name = name,
                args = args,
                text = output ?: "",
                isError = isError
            ))
        }
        return MessageUi(
            id = "tool:$toolCallId",
            content = content,
            role = MessageRole.ASSISTANT,
            timestamp = startedAt,
            isLoading = phase != "result"
        )
    }
}
