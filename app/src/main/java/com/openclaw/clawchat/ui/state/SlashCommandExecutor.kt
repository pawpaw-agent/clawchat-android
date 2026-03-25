package com.openclaw.clawchat.ui.state

import android.util.Log
import com.openclaw.clawchat.util.AppLog
import com.openclaw.clawchat.network.protocol.GatewayConnection
import com.openclaw.clawchat.repository.MessageRepository
import com.openclaw.clawchat.ui.components.SLASH_COMMANDS
import com.openclaw.clawchat.ui.components.SlashCommandCategory
import com.openclaw.clawchat.ui.components.SlashCommandDef
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive

/**
 * 斜杠命令执行器（从 SessionViewModel 提取）
 * 职责：解析和执行斜杠命令
 */
class SlashCommandExecutor(
    private val scope: CoroutineScope,
    private val gateway: GatewayConnection,
    private val messageRepository: MessageRepository,
    private val onStateUpdate: (SessionUiState.() -> SessionUiState) -> Unit
) {
    companion object {
        private const val TAG = "SlashCommandExecutor"
    }

    /**
     * 显示错误消息给用户
     */
    private fun showError(message: String) {
        onStateUpdate {
            copy(
                chatMessages = chatMessages + MessageUi(
                    id = "error-${System.currentTimeMillis()}",
                    content = listOf(MessageContentItem.Text("❌ $message")),
                    role = MessageRole.ASSISTANT,
                    timestamp = System.currentTimeMillis()
                )
            )
        }
    }

    /**
     * 显示成功消息给用户
     */
    private fun showSuccess(message: String) {
        onStateUpdate {
            copy(
                chatMessages = chatMessages + MessageUi(
                    id = "success-${System.currentTimeMillis()}",
                    content = listOf(MessageContentItem.Text("✅ $message")),
                    role = MessageRole.ASSISTANT,
                    timestamp = System.currentTimeMillis()
                )
            )
        }
    }

    /**
     * 执行斜杠命令
     */
    fun execute(command: SlashCommandDef, args: String, sessionId: String?) {
        AppLog.d(TAG, "=== execute: ${command.name}, args=$args")
        
        when (command.name) {
            "clear" -> executeClear(sessionId)
            "help" -> executeHelp()
            "new" -> executeNew()
            "reset" -> executeReset(sessionId)
            "think", "thinking" -> executeThink(sessionId, args)
            "reasoning" -> executeReasoning(sessionId, args)
            "verbose" -> executeVerbose(sessionId, args)
            else -> executeDefault(command, args, sessionId)
        }
    }

    private fun executeClear(sessionId: String?) {
        scope.launch {
            if (sessionId == null) {
                showError("无法清除消息：未选择会话")
                return@launch
            }
            messageRepository.clearMessages(sessionId)
            onStateUpdate {
                copy(
                    chatMessages = emptyList(),
                    chatStream = null,
                    chatStreamSegments = emptyList(),
                    chatToolMessages = emptyList(),
                    toolStreamById = emptyMap(),
                    toolStreamOrder = emptyList(),
                    inputText = ""
                )
            }
        }
    }

    private fun executeHelp() {
        val helpText = buildString {
            appendLine("## 可用命令")
            appendLine()
            SLASH_COMMANDS.groupBy { it.category }.forEach { (category, commands) ->
                val categoryLabel = when (category) {
                    SlashCommandCategory.SESSION -> "会话"
                    SlashCommandCategory.MODEL -> "模型"
                    SlashCommandCategory.AGENTS -> "Agents"
                    SlashCommandCategory.TOOLS -> "工具"
                }
                appendLine("### $categoryLabel")
                commands.forEach { cmd ->
                    val argsHint = if (cmd.args != null) " ${cmd.args}" else ""
                    appendLine("- `/${cmd.name}$argsHint` - ${cmd.description}")
                }
                appendLine()
            }
        }
        onStateUpdate {
            copy(
                inputText = "",
                chatMessages = chatMessages + MessageUi(
                    id = "help-${System.currentTimeMillis()}",
                    content = listOf(MessageContentItem.Text(helpText)),
                    role = MessageRole.ASSISTANT,
                    timestamp = System.currentTimeMillis()
                )
            )
        }
    }

    private fun executeNew() {
        onStateUpdate { copy(inputText = "") }
        // 新会话将在首次消息时自动创建
    }

    private fun executeReset(sessionId: String?) {
        scope.launch {
            if (sessionId == null) {
                showError("无法重置会话：未选择会话")
                return@launch
            }
            try {
                gateway.call("sessions.reset", mapOf("key" to JsonPrimitive(sessionId)))
                onStateUpdate {
                    copy(
                        chatMessages = emptyList(),
                        chatStream = null,
                        chatStreamSegments = emptyList(),
                        chatToolMessages = emptyList(),
                        toolStreamById = emptyMap(),
                        toolStreamOrder = emptyList(),
                        inputText = ""
                    )
                }
                showSuccess("会话已重置")
            } catch (e: Exception) {
                AppLog.e(TAG, "Failed to reset session", e)
                showError("重置会话失败：${e.message ?: "未知错误"}")
            }
        }
    }

    private fun executeThink(sessionId: String?, args: String) {
        scope.launch {
            if (sessionId == null) {
                showError("无法设置思考级别：未选择会话")
                return@launch
            }
            val level = args.trim().lowercase().ifEmpty { "medium" }
            try {
                gateway.call("sessions.patch", mapOf(
                    "key" to JsonPrimitive(sessionId),
                    "thinkingLevel" to JsonPrimitive(level)
                ))
                showSuccess("思考级别已设置为: $level")
            } catch (e: Exception) {
                AppLog.e(TAG, "Failed to set thinking level", e)
                showError("设置思考级别失败：${e.message ?: "未知错误"}")
            }
        }
    }

    private fun executeReasoning(sessionId: String?, args: String) {
        scope.launch {
            if (sessionId == null) {
                showError("无法切换推理模式：未选择会话")
                return@launch
            }
            val enabled = args.trim().lowercase().let { 
                it == "on" || it == "true" || it == "1" 
            }
            try {
                gateway.call("sessions.patch", mapOf(
                    "key" to JsonPrimitive(sessionId),
                    "reasoning" to JsonPrimitive(enabled)
                ))
                val status = if (enabled) "开启" else "关闭"
                showSuccess("推理模式已$status")
            } catch (e: Exception) {
                AppLog.e(TAG, "Failed to toggle reasoning", e)
                showError("切换推理模式失败：${e.message ?: "未知错误"}")
            }
        }
    }

    private fun executeVerbose(sessionId: String?, args: String) {
        scope.launch {
            if (sessionId == null) {
                showError("无法设置详细模式：未选择会话")
                return@launch
            }
            val level = args.trim().lowercase().ifEmpty { "on" }
            try {
                gateway.call("sessions.patch", mapOf(
                    "key" to JsonPrimitive(sessionId),
                    "verboseLevel" to JsonPrimitive(level)
                ))
                showSuccess("详细模式已设置: $level")
            } catch (e: Exception) {
                AppLog.e(TAG, "Failed to set verbose level", e)
                showError("设置详细模式失败：${e.message ?: "未知错误"}")
            }
        }
    }

    private fun executeDefault(command: SlashCommandDef, args: String, sessionId: String?) {
        scope.launch {
            if (sessionId == null) {
                showError("无法执行命令：未选择会话")
                return@launch
            }
            val message = "/${command.name}${if (args.isNotBlank()) " $args" else ""}"
            try {
                gateway.chatSend(sessionId, message)
                onStateUpdate { copy(inputText = "", isSending = true, isLoading = true) }
            } catch (e: Exception) {
                AppLog.e(TAG, "Failed to send command", e)
                showError("执行命令失败：${e.message ?: "未知错误"}")
            }
        }
    }
}
