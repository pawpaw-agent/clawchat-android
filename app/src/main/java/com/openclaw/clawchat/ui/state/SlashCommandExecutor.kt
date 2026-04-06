package com.openclaw.clawchat.ui.state

import com.openclaw.clawchat.R
import com.openclaw.clawchat.util.AppLog
import com.openclaw.clawchat.util.StringResourceProvider
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
    private val strings: StringResourceProvider,
    private val onStateUpdate: (SessionUiState.() -> SessionUiState) -> Unit,
    private val onUndo: () -> Unit = {}  // 撤销回调
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
            "undo" -> executeUndo(sessionId)
            "think", "thinking" -> executeThink(sessionId, args)
            "reasoning" -> executeReasoning(sessionId, args)
            "verbose" -> executeVerbose(sessionId, args)
            "export" -> executeExport(sessionId)
            else -> executeDefault(command, args, sessionId)
        }
    }

    private fun executeClear(sessionId: String?) {
        scope.launch {
            if (sessionId == null) {
                showError(strings.getString(R.string.slash_error_clear_no_session))
                return@launch
            }
            messageRepository.clearMessages(sessionId)
            onStateUpdate { clearSession() }
        }
    }

    private fun executeHelp() {
        val helpText = buildString {
            appendLine("## ${strings.getString(R.string.slash_help_desc)}")
            appendLine()
            SLASH_COMMANDS.groupBy { it.category }.forEach { (category, commands) ->
                val categoryLabel = when (category) {
                    SlashCommandCategory.SESSION -> strings.getString(R.string.slash_category_session)
                    SlashCommandCategory.MODEL -> strings.getString(R.string.slash_category_model)
                    SlashCommandCategory.AGENTS -> strings.getString(R.string.slash_category_agents)
                    SlashCommandCategory.TOOLS -> strings.getString(R.string.slash_category_tools)
                }
                appendLine("### $categoryLabel")
                commands.forEach { cmd ->
                    val argsHint = if (cmd.args != null) " ${cmd.args}" else ""
                    // Use localized description for known commands
                    val desc = getLocalizedCommandDescription(cmd.name)
                    appendLine("- `/${cmd.name}$argsHint` - $desc")
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

    /**
     * Get localized description for a command
     */
    private fun getLocalizedCommandDescription(name: String): String {
        return when (name) {
            "new" -> strings.getString(R.string.slash_new_desc)
            "reset" -> strings.getString(R.string.slash_reset_desc)
            "compact" -> strings.getString(R.string.slash_compact_desc)
            "stop", "esc", "abort", "wait", "exit" -> strings.getString(R.string.slash_stop_desc)
            "clear" -> strings.getString(R.string.slash_clear_desc)
            "focus" -> strings.getString(R.string.slash_focus_desc)
            "undo" -> strings.getString(R.string.slash_undo_desc)
            "model" -> strings.getString(R.string.slash_model_desc)
            "think", "thinking" -> strings.getString(R.string.slash_think_desc)
            "verbose" -> strings.getString(R.string.slash_verbose_desc)
            "fast" -> strings.getString(R.string.slash_fast_desc)
            "help" -> strings.getString(R.string.slash_help_desc)
            "status" -> strings.getString(R.string.slash_status_desc)
            "export" -> strings.getString(R.string.slash_export_desc)
            "usage" -> strings.getString(R.string.slash_usage_desc)
            "agents" -> strings.getString(R.string.slash_agents_desc)
            "kill" -> strings.getString(R.string.slash_kill_desc)
            "skill" -> strings.getString(R.string.slash_skill_desc)
            "steer" -> strings.getString(R.string.slash_steer_desc)
            else -> "Unknown command"  // fallback
        }
    }

    private fun executeNew() {
        onStateUpdate { copy(inputText = "") }
        // 新会话将在首次消息时自动创建
    }

    private fun executeReset(sessionId: String?) {
        scope.launch {
            if (sessionId == null) {
                showError(strings.getString(R.string.slash_error_reset_no_session))
                return@launch
            }
            try {
                gateway.call("sessions.reset", mapOf("key" to JsonPrimitive(sessionId)))
                onStateUpdate { clearSession() }
                showSuccess(strings.getString(R.string.slash_success_reset))
            } catch (e: Exception) {
                AppLog.e(TAG, "Failed to reset session", e)
                showError(strings.getString(R.string.slash_error_reset_failed, e.message ?: strings.getString(R.string.error_connection_exception, "")))
            }
        }
    }

    /**
     * 撤销上一轮对话
     * 删除最后的用户消息和助手消息
     */
    private fun executeUndo(sessionId: String?) {
        onUndo()
        onStateUpdate { copy(inputText = "") }
    }

    private fun executeThink(sessionId: String?, args: String) {
        scope.launch {
            if (sessionId == null) {
                showError(strings.getString(R.string.slash_error_think_no_session))
                return@launch
            }
            val level = args.trim().lowercase().ifEmpty { "medium" }
            try {
                gateway.call("sessions.patch", mapOf(
                    "key" to JsonPrimitive(sessionId),
                    "thinkingLevel" to JsonPrimitive(level)
                ))
                showSuccess(strings.getString(R.string.slash_success_think_set, level))
            } catch (e: Exception) {
                AppLog.e(TAG, "Failed to set thinking level", e)
                showError(strings.getString(R.string.slash_error_think_failed, e.message ?: ""))
            }
        }
    }

    private fun executeReasoning(sessionId: String?, args: String) {
        scope.launch {
            if (sessionId == null) {
                showError(strings.getString(R.string.slash_error_reasoning_no_session))
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
                val status = if (enabled) strings.getString(R.string.slash_success_reasoning_on)
                             else strings.getString(R.string.slash_success_reasoning_off)
                showSuccess(status)
            } catch (e: Exception) {
                AppLog.e(TAG, "Failed to toggle reasoning", e)
                showError(strings.getString(R.string.slash_error_reasoning_failed, e.message ?: ""))
            }
        }
    }

    private fun executeVerbose(sessionId: String?, args: String) {
        scope.launch {
            if (sessionId == null) {
                showError(strings.getString(R.string.slash_error_verbose_no_session))
                return@launch
            }
            val level = args.trim().lowercase().ifEmpty { "on" }
            try {
                gateway.call("sessions.patch", mapOf(
                    "key" to JsonPrimitive(sessionId),
                    "verboseLevel" to JsonPrimitive(level)
                ))
                showSuccess(strings.getString(R.string.slash_success_verbose_set, level))
            } catch (e: Exception) {
                AppLog.e(TAG, "Failed to set verbose level", e)
                showError(strings.getString(R.string.slash_error_verbose_failed, e.message ?: ""))
            }
        }
    }

    /**
     * 导出会话到 Markdown 格式
     */
    private fun executeExport(sessionId: String?) {
        scope.launch {
            if (sessionId == null) {
                showError(strings.getString(R.string.slash_error_export_no_session))
                return@launch
            }

            showSuccess(strings.getString(R.string.slash_success_export_triggered))
        }
    }

    private fun executeDefault(command: SlashCommandDef, args: String, sessionId: String?) {
        scope.launch {
            if (sessionId == null) {
                showError(strings.getString(R.string.slash_error_exec_no_session))
                return@launch
            }
            val message = "/${command.name}${if (args.isNotBlank()) " $args" else ""}"
            try {
                gateway.chatSend(sessionId, message)
                onStateUpdate { copy(inputText = "", isSending = true, isLoading = true) }
            } catch (e: Exception) {
                AppLog.e(TAG, "Failed to send command", e)
                showError(strings.getString(R.string.slash_error_exec_failed, e.message ?: ""))
            }
        }
    }
}
