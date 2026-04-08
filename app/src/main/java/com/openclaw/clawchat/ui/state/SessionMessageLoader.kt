package com.openclaw.clawchat.ui.state

import com.openclaw.clawchat.network.WebSocketConnectionState
import com.openclaw.clawchat.network.protocol.GatewayConnection
import com.openclaw.clawchat.repository.MessageRepository
import com.openclaw.clawchat.util.AppLog
import com.openclaw.clawchat.util.JsonUtils
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 消息保存数据（用于批量处理历史消息）
 */
private data class MessageSaveData(
    val content: String,
    val role: String,
    val timestamp: Long,
    val toolCallId: String? = null,
    val toolName: String? = null
)

/**
 * 会话消息加载器
 *
 * 负责加载历史消息：
 * - loadMessageHistory
 * - refreshMessages
 */
class SessionMessageLoader(
    private val scope: CoroutineScope,
    private val gateway: GatewayConnection,
    private val messageRepository: MessageRepository,
    private val state: MutableStateFlow<SessionUiState>,
    private val exceptionHandler: CoroutineExceptionHandler,
    private val onLoadingStateChanged: ((Boolean) -> Unit)? = null
) {
    companion object {
        private const val TAG = "SessionMessageLoader"
    }

    // loadMessageHistory 的 Job（用于取消之前的加载）
    private var loadMessagesJob: Job? = null

    /**
     * 刷新消息（重新加载当前会话）
     */
    fun refreshMessages() {
        val sessionId = state.value.sessionId ?: return
        loadMessageHistory(sessionId)
    }

    /**
     * 加载消息历史
     */
    fun loadMessageHistory(sessionId: String) {
        // 取消之前的加载任务
        loadMessagesJob?.cancel()

        // 先检查连接状态，避免在未连接时设置加载状态或清除消息
        if (gateway.connectionState.value !is WebSocketConnectionState.Connected) {
            AppLog.w(TAG, "Gateway not connected, skipping loadMessageHistory")
            // 未连接时不清除消息，保持现有消息列表
            return
        }

        // 设置加载状态
        state.update { it.copy(isLoading = true) }
        onLoadingStateChanged?.invoke(true)

        loadMessagesJob = scope.launch(exceptionHandler) {
            // 从 Gateway 加载历史消息
            try {

                val response = gateway.chatHistory(sessionId, limit = 100)

                if (!response.isSuccess()) {
                    AppLog.w(TAG, "Gateway request failed: ${response.error?.message}")
                    state.update { it.copy(isLoading = false) }
                    onLoadingStateChanged?.invoke(false)
                    return@launch
                }

                if (response.payload !is JsonObject) {
                    AppLog.w(TAG, "Invalid response payload type")
                    state.update { it.copy(isLoading = false) }
                    onLoadingStateChanged?.invoke(false)
                    return@launch
                }

                val payload = response.payload as JsonObject
                val messagesArray = payload["messages"]?.jsonArray

                // 收集所有消息，批量处理（包含 toolCallId 和 toolName）
                val messagesToSave = mutableListOf<MessageSaveData>()

                // 先清除该会话的旧消息，避免重复
                messageRepository.clearMessages(sessionId)

                messagesArray?.forEach { msgElement ->
                    try {
                        val msgObj = msgElement.jsonObject
                        val role = msgObj["role"]?.jsonPrimitive?.content ?: "assistant"
                        val content = msgObj["content"]?.let { JsonUtils.json.encodeToString(JsonElement.serializer(), it) } ?: "{}"
                        val timestamp = msgObj["timestamp"]?.jsonPrimitive?.content?.toLongOrNull() ?: System.currentTimeMillis()

                        // 提取 toolCallId 和 toolName（TOOL 消息特有）
                        val toolCallId = msgObj["toolCallId"]?.jsonPrimitive?.content
                        val toolName = msgObj["name"]?.jsonPrimitive?.content ?: msgObj["toolName"]?.jsonPrimitive?.content

                        messagesToSave.add(MessageSaveData(content, role, timestamp, toolCallId, toolName))
                    } catch (e: Exception) {
                        AppLog.w(TAG, "Failed to parse history message: ${e.message}")
                    }
                }

                // 批量保存消息（传递 toolCallId 和 toolName）
                messagesToSave.forEach { data ->
                    messageRepository.saveMessage(
                        sessionId = sessionId,
                        role = MessageRole.fromString(data.role),
                        content = data.content,
                        timestamp = data.timestamp,
                        toolCallId = data.toolCallId,
                        toolName = data.toolName
                    )
                }

                AppLog.d(TAG, "Loaded ${messagesToSave.size} messages for session $sessionId")
            } catch (e: Exception) {
                AppLog.w(TAG, "Failed to load chat history: ${e.message}")
            }

            // 加载完成，一次性更新状态
            val loadedMessages = messageRepository.observeMessages(sessionId).first()
            state.update { it.copy(
                isLoading = false,
                chatMessages = loadedMessages
            )}
            onLoadingStateChanged?.invoke(false)
        }
    }

    /**
     * 取消加载任务
     */
    fun cancel() {
        loadMessagesJob?.cancel()
        loadMessagesJob = null
        // 取消时重置加载状态
        onLoadingStateChanged?.invoke(false)
    }
}