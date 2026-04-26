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
            try {
                val response = gateway.chatHistory(sessionId, limit = 100)

                if (!response.isSuccess()) {
                    AppLog.w(TAG, "Gateway request failed: ${response.error?.message}")
                    return@launch
                }

                if (response.payload !is JsonObject) {
                    AppLog.w(TAG, "Invalid response payload type")
                    return@launch
                }

                val payload = response.payload as JsonObject
                val messagesArray = payload["messages"]?.jsonArray

                // 收集所有消息，批量处理（包含 toolCallId 和 toolName）
                val messagesToSave = mutableListOf<MessageUi>()

                messagesArray?.forEach { msgElement ->
                    try {
                        val msgObj = msgElement.jsonObject
                        val role = msgObj["role"]?.jsonPrimitive?.content ?: "assistant"
                        val timestamp = msgObj["timestamp"]?.jsonPrimitive?.content?.toLongOrNull() ?: System.currentTimeMillis()

                        // 解析 content 数组为 MessageContentItem 列表
                        val contentElement = msgObj["content"]
                        val contentList = parseContentArray(contentElement)

                        // 提取 toolCallId 和 toolName（TOOL 消息特有）
                        val toolCallId = msgObj["toolCallId"]?.jsonPrimitive?.content
                        val toolName = msgObj["name"]?.jsonPrimitive?.content ?: msgObj["toolName"]?.jsonPrimitive?.content

                        messagesToSave.add(MessageUi(
                            id = msgObj["id"]?.jsonPrimitive?.content ?: java.util.UUID.randomUUID().toString(),
                            content = contentList,
                            role = MessageRole.fromString(role),
                            timestamp = timestamp,
                            toolCallId = toolCallId,
                            toolName = toolName,
                            status = MessageStatus.SENT
                        ))
                    } catch (e: Exception) {
                        AppLog.w(TAG, "Failed to parse history message: ${e.message}")
                    }
                }

                // 原子操作：清空并批量保存，避免 clear-then-save 之间的数据丢失
                messageRepository.clearAndSaveMessages(sessionId, messagesToSave)

                AppLog.d(TAG, "Loaded ${messagesToSave.size} messages for session $sessionId")
            } catch (e: Exception) {
                AppLog.w(TAG, "Failed to load chat history: ${e.message}")
            } finally {
                // 无论成功、失败还是异常，都重置加载状态
                val loadedMessages = messageRepository.observeMessages(sessionId).first()
                state.update { it.copy(
                    isLoading = false,
                    chatMessages = loadedMessages
                )}
                onLoadingStateChanged?.invoke(false)
            }
        }
    }

    /**
     * 取消加载任务
     */
    fun cancel() {
        loadMessagesJob?.cancel()
        loadMessagesJob = null
        // 取消时重置加载状态
        state.update { it.copy(isLoading = false) }
        onLoadingStateChanged?.invoke(false)
    }
}

/**
 * 解析 Gateway 返回的 content 数组为 MessageContentItem 列表
 * Gateway content 格式: [{"type":"text","text":"..."},{"type":"thinking","thinking":"..."},{"type":"toolCall","name":"...","arguments":"..."},...]
 */
private fun parseContentArray(contentElement: JsonElement?): List<MessageContentItem> {
    val items = mutableListOf<MessageContentItem>()

    // 兼容纯文本格式
    if (contentElement is kotlinx.serialization.json.JsonPrimitive) {
        val text = contentElement.content
        if (text.isNotBlank()) {
            items.add(MessageContentItem.Text(text))
        }
        return items
    }

    // 解析 JSON 数组
    val array = contentElement?.jsonArray ?: return items

    array.forEach { element ->
        try {
            val obj = element.jsonObject
            val type = obj["type"]?.jsonPrimitive?.content ?: "text"

            when (type) {
                "text" -> {
                    val text = obj["text"]?.jsonPrimitive?.content
                    if (!text.isNullOrBlank()) {
                        items.add(MessageContentItem.Text(text))
                    }
                }
                "thinking" -> {
                    val thinking = obj["thinking"]?.jsonPrimitive?.content
                    if (!thinking.isNullOrBlank()) {
                        items.add(MessageContentItem.Text(thinking))
                    }
                }
                "toolCall", "tool_call" -> {
                    val id = obj["id"]?.jsonPrimitive?.content
                    val name = obj["name"]?.jsonPrimitive?.content ?: ""
                    val argsJson = obj["arguments"]
                    items.add(MessageContentItem.ToolCall(
                        id = id,
                        name = name,
                        args = argsJson as? kotlinx.serialization.json.JsonObject,
                        phase = "result"
                    ))
                }
                "tool_result" -> {
                    val toolCallId = obj["toolCallId"]?.jsonPrimitive?.content
                    val name = obj["name"]?.jsonPrimitive?.content
                    val text = obj["text"]?.jsonPrimitive?.content ?: ""
                    val isError = obj["isError"]?.jsonPrimitive?.content?.toBoolean() ?: false
                    items.add(MessageContentItem.ToolResult(
                        toolCallId = toolCallId,
                        name = name,
                        text = text,
                        isError = isError
                    ))
                }
                "image" -> {
                    val url = obj["url"]?.jsonPrimitive?.content
                    val base64 = obj["data"]?.jsonPrimitive?.content
                    val mimeType = obj["mimeType"]?.jsonPrimitive?.content
                    items.add(MessageContentItem.Image(
                        url = url,
                        base64 = base64,
                        mimeType = mimeType
                    ))
                }
                else -> {
                    // 未知类型，尝试作为文本处理
                    val text = obj["text"]?.jsonPrimitive?.content ?: element.toString()
                    if (text.isNotBlank()) {
                        items.add(MessageContentItem.Text(text))
                    }
                }
            }
        } catch (e: Exception) {
            // 解析单个元素失败不影响其他元素
            AppLog.w("SessionMessageLoader", "Failed to parse content element: ${e.message}")
        }
    }

    // 如果解析后为空，返回一个空文本项避免消息完全丢失
    if (items.isEmpty()) {
        items.add(MessageContentItem.Text(""))
    }

    return items
}