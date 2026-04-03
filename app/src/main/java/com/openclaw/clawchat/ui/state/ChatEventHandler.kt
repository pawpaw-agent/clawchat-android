package com.openclaw.clawchat.ui.state

import com.openclaw.clawchat.util.AppLog
import com.openclaw.clawchat.network.protocol.GatewayConnection
import com.openclaw.clawchat.repository.MessageRepository
import com.openclaw.clawchat.util.JsonUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 聊天事件处理器
 * 
 * 负责处理所有来自 Gateway 的聊天事件：
 * - handleIncomingFrame
 * - handleAgentEvent
 * - handleToolStreamEvent
 * - handleChatEvent
 * - handleDelta/Final/Aborted/Error
 */
class ChatEventHandler(
    private val scope: CoroutineScope,
    private val gateway: GatewayConnection,
    private val messageRepository: MessageRepository,
    private val state: MutableStateFlow<SessionUiState>,
    private val onToolStreamEvent: (JsonObject) -> Unit,
    private val onChatComplete: (() -> Unit)? = null
) {
    companion object {
        private const val TAG = "ChatEventHandler"
    }

    /**
     * 处理传入的 JSON 帧
     */
    fun handleIncomingFrame(rawJson: String) {
        try {
            val json = JsonUtils.json.parseToJsonElement(rawJson) as JsonObject
            val type = json["type"]?.jsonPrimitive?.content ?: return

            AppLog.d(TAG, "=== Received frame type=$type")

            when (type) {
                "event" -> {
                    val event = json["event"]?.jsonPrimitive?.content ?: return
                    val payload = json["payload"]?.jsonObject ?: return

                    when (event) {
                        "agent" -> {
                            // payload.kind 决定事件类型：tool / compaction / lifecycle
                            val kind = payload["kind"]?.jsonPrimitive?.content ?: "unknown"
                            handleAgentEvent(payload, kind)
                        }
                        "tool.stream" -> {
                            onToolStreamEvent(payload)
                        }
                        "chat" -> {
                            val sessionKey = payload["sessionKey"]?.jsonPrimitive?.content ?: return
                            handleChatEvent(payload, sessionKey)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to parse incoming frame: ${e.message}")
        }
    }

    /**
     * 处理 agent 事件
     */
    private fun handleAgentEvent(payload: JsonObject, stream: String) {
        when (stream) {
            "tool" -> {
                onToolStreamEvent(payload)
            }
            // 可以添加其他 stream 类型：compaction, fallback, lifecycle
        }
    }

    /**
     * 处理 chat 事件
     */
    private fun handleChatEvent(payload: JsonObject, sessionId: String) {
        val runId = payload["runId"]?.jsonPrimitive?.content ?: return
        val seq = payload["seq"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
        val chatState = payload["state"]?.jsonPrimitive?.content ?: return

        AppLog.d(TAG, "=== Chat event: runId=$runId, seq=$seq, state=$chatState")

        // 提取 message 对象
        val msgObj = payload["message"]?.jsonObject

        when (chatState) {
            "delta" -> handleDelta(runId, msgObj, sessionId)
            "final" -> handleFinal(runId, msgObj, sessionId)
            "aborted" -> handleAborted(runId, msgObj)
            "error" -> {
                val errorMsg = payload["error"]?.jsonPrimitive?.content ?: "Unknown error"
                handleError(runId, errorMsg)
            }
        }
    }

    /**
     * 处理 delta 状态（流式内容）
     */
    private fun handleDelta(runId: String, msgObj: JsonObject?, sessionId: String) {
        // 提取文本
        val textContent = msgObj?.let { extractTextContent(it) }
        
        if (textContent != null && textContent.isNotBlank()) {
            // webchat: if (!current || next.length >= current.length)
            // 流式追加逻辑
            state.update { currentState ->
                val now = System.currentTimeMillis()
                val currentStream = currentState.chatStream
                val segments = currentState.chatStreamSegments
                
                // 检查是否应该追加（内容长度递增）
                val shouldAppend = currentStream == null || textContent.length >= currentStream.length
                
                val newStream = if (shouldAppend) {
                    textContent
                } else {
                    currentStream // 保持旧内容
                }
                
                val newSegments = if (currentState.chatStreamStartedAt == null) {
                    segments + StreamSegment(newStream ?: "", now)
                } else {
                    segments
                }
                
                currentState.copy(
                    chatStream = newStream,
                    chatStreamSegments = newSegments,
                    chatStreamStartedAt = currentState.chatStreamStartedAt ?: now
                )
            }
        }
    }

    /**
     * 处理 final 状态（消息完成）
     */
    private fun handleFinal(runId: String, msgObj: JsonObject?, sessionId: String) {
        // 提交流式文本到 segments
        state.update { currentState ->
            val now = System.currentTimeMillis()
            val segments = currentState.chatStreamSegments
            val stream = currentState.chatStream
            
            val finalSegments = if (!stream.isNullOrBlank()) {
                segments + StreamSegment(stream.trim(), now)
            } else {
                segments
            }
            
            currentState.copy(
                chatStream = null,
                chatStreamSegments = finalSegments,
                chatStreamStartedAt = null,
                chatRunId = null,
                isSending = false,
                isLoading = false
            )
        }

        // 保存消息到数据库
        saveMessageToDb(runId, msgObj, sessionId)
        
        // 触发队列刷新
        onChatComplete?.invoke()
    }

    /**
     * 处理 aborted 状态
     */
    private fun handleAborted(runId: String, msgObj: JsonObject?) {
        AppLog.w(TAG, "Message aborted: runId=$runId")
        
        state.update { currentState ->
            val now = System.currentTimeMillis()
            val segments = currentState.chatStreamSegments
            val stream = currentState.chatStream
            
            // 提交最后的流式文本（如果有）
            val finalSegments = if (!stream.isNullOrBlank()) {
                segments + StreamSegment("(aborted) ${stream.trim()}", now)
            } else {
                segments + StreamSegment("(message aborted)", now)
            }
            
            currentState.copy(
                chatStream = null,
                chatStreamSegments = finalSegments,
                chatStreamStartedAt = null,
                chatRunId = null,
                isSending = false,
                isLoading = false
            )
        }
        
        // 触发队列刷新
        onChatComplete?.invoke()
    }

    /**
     * 处理 error 状态
     */
    private fun handleError(runId: String, errorMsg: String) {
        AppLog.e(TAG, "Message error: runId=$runId, error=$errorMsg")
        
        state.update { currentState ->
            val now = System.currentTimeMillis()
            val segments = currentState.chatStreamSegments
            
            currentState.copy(
                chatStream = null,
                chatStreamSegments = segments + StreamSegment("(error: $errorMsg)", now),
                chatStreamStartedAt = null,
                chatRunId = null,
                isSending = false,
                isLoading = false,
                error = errorMsg
            )
        }
        
        // 触发队列刷新
        onChatComplete?.invoke()
    }

    /**
     * 保存消息到数据库
     */
    private fun saveMessageToDb(runId: String, msgObj: JsonObject?, sessionId: String) {
        if (msgObj == null) return
        
        val role = msgObj["role"]?.jsonPrimitive?.content ?: "assistant"
        val content = msgObj["content"]?.let { JsonUtils.json.encodeToString(JsonElement.serializer(), it) } ?: "{}"
        val timestamp = msgObj["timestamp"]?.jsonPrimitive?.content?.toLongOrNull() ?: System.currentTimeMillis()
        
        // 提取 toolCallId 和 toolName
        val toolCallId = msgObj["toolCallId"]?.jsonPrimitive?.content
        val toolName = msgObj["toolName"]?.jsonPrimitive?.content
        
        // 在协程中调用 suspend 函数
        scope.launch {
            try {
                messageRepository.saveMessage(
                    sessionId = sessionId,
                    role = MessageRole.fromString(role),
                    content = content,
                    timestamp = timestamp,
                    toolCallId = toolCallId,
                    toolName = toolName
                )
                
                // 更新发送状态
                state.update { it.copy(isSending = false) }
            } catch (e: Exception) {
                AppLog.e(TAG, "Failed to save message: ${e.message}")
            }
        }
    }

    /**
     * 提取文本内容
     */
    private fun extractTextContent(msgObj: JsonObject): String? {
        val content = msgObj["content"]
        if (content == null) return null
        
        return when {
            content is JsonPrimitive -> content.jsonPrimitive.content
            content is JsonObject -> {
                // 尝试提取 text 字段
                content["text"]?.jsonPrimitive?.content
            }
            content is kotlinx.serialization.json.JsonArray -> {
                // 数组：提取第一个文本元素
                content.jsonArray.firstOrNull()?.let { elem ->
                    when (elem) {
                        is JsonPrimitive -> elem.jsonPrimitive.content
                        is JsonObject -> elem.jsonObject["text"]?.jsonPrimitive?.content
                        else -> null
                    }
                }
            }
            else -> null
        }
    }
}