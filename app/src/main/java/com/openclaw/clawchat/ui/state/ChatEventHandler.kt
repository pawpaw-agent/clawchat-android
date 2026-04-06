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
    private val onChatComplete: (() -> Unit)? = null,
    private val onLifecycleEnd: (() -> Unit)? = null
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

                    AppLog.d(TAG, "=== Event: $event, payload keys: ${payload.keys}")

                    when (event) {
                        "agent" -> {
                            // payload.stream 决定事件类型：tool / assistant / lifecycle / error
                            val stream = payload["stream"]?.jsonPrimitive?.content ?: "unknown"
                            AppLog.d(TAG, "=== Agent event: stream=$stream, sessionKey=${payload["sessionKey"]?.jsonPrimitive?.content}, payload keys=${payload.keys}")
                            handleAgentEvent(payload, stream)
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
     * Gateway agent event stream 类型（源码验证）：
     *   - lifecycle: { phase: "start"|"end"|"error"|"fallback"|"fallback_cleared", startedAt?, endedAt?, error? }
     *   - assistant: { text, delta, mediaUrls? } - 通过 chat 事件处理，不需要额外处理
     *   - tool: { phase, name, toolCallId, args?, result?, partialResult?, isError? }
     *   - thinking: { text, delta } - 推理流
     *   - compaction: { phase: "start"|"end" } - 内部事件
     *   - error: { reason, expected, received } - 网关合成错误
     */
    private fun handleAgentEvent(payload: JsonObject, stream: String) {
        AppLog.d(TAG, "=== handleAgentEvent: stream=$stream, payload keys=${payload.keys}")
        when (stream) {
            "tool" -> onToolStreamEvent(payload)
            "lifecycle" -> handleLifecycleEvent(payload)
            "error" -> handleAgentErrorEvent(payload)
            // assistant 通过 chat 事件处理，thinking/compaction 不需要 UI 处理
            else -> AppLog.d(TAG, "=== Ignoring agent event with stream=$stream")
        }
    }

    /**
     * 处理 lifecycle 事件
     * lifecycle:end 到达时 JSONL 已包含本次 run 的全部消息
     * lifecycle:error 表示 run 出错
     */
    private fun handleLifecycleEvent(payload: JsonObject) {
        val data = payload["data"]?.jsonObject
        val phase = data?.get("phase")?.jsonPrimitive?.content ?: return
        val runId = payload["runId"]?.jsonPrimitive?.content ?: return
        
        AppLog.d(TAG, "=== Lifecycle event: runId=$runId, phase=$phase")
        
        when (phase) {
            "error" -> {
                // run 出错，清除状态
                val errorMsg = data["error"]?.jsonPrimitive?.content ?: "Agent run error"
                handleError(runId, errorMsg)
            }
            "end" -> {
                // run 结束，如果没有收到 chat final，强制清除状态
                state.update { currentState ->
                    if (currentState.chatRunId == runId) {
                        currentState.copy(
                            chatRunId = null,
                            isSending = false,
                            isLoading = false
                        )
                    } else {
                        currentState
                    }
                }
                // 触发 lifecycle:end 回调：清除工具流 + 刷新消息
                onLifecycleEnd?.invoke()
            }
            // start/fallback/fallback_cleared 不需要特殊处理
            else -> AppLog.d(TAG, "=== Lifecycle phase=$phase, no action needed")
        }
    }

    /**
     * 处理 agent error stream 事件（网关合成错误，序列号间隙）
     */
    private fun handleAgentErrorEvent(payload: JsonObject) {
        val data = payload["data"]?.jsonObject
        val reason = data?.get("reason")?.jsonPrimitive?.content ?: "Agent error"
        val runId = payload["runId"]?.jsonPrimitive?.content ?: "unknown"
        
        AppLog.e(TAG, "=== Agent error event: runId=$runId, reason=$reason")
        
        // 清除状态
        state.update { currentState ->
            currentState.copy(
                chatRunId = null,
                isSending = false,
                isLoading = false,
                error = reason
            )
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
     * 流式优化：debounce 更新，减少 UI 重组频率
     * 参考 Stream SDK: 使用 snapshotFlow 进行批量更新
     */
    // 流式缓冲区（减少更新频率）
    private var streamBuffer = ""
    private var lastStreamUpdate = 0L
    private val STREAM_UPDATE_INTERVAL = 33L  // ~30fps，平衡流畅性和性能
    private var totalReceivedChars = 0  // 追踪总接收字符数

    private fun handleDelta(runId: String, msgObj: JsonObject?, sessionId: String) {
        // 提取文本
        val textContent = msgObj?.let { extractTextContent(it) }

        if (textContent != null && textContent.isNotBlank()) {
            // 流式优化：缓冲追加，减少 state.update 调用
            streamBuffer = textContent
            totalReceivedChars = textContent.length

            val now = System.currentTimeMillis()
            // 动态调整更新频率：
            // - 内容变化小时降低更新频率
            // - 内容变化大时立即更新
            val currentStream = state.value.chatStream
            val currentLength = currentStream?.length ?: 0
            val contentGrowth = totalReceivedChars - currentLength

            // 内容增长超过 50 字符 或 时间间隔超过阈值时更新
            val shouldUpdate = contentGrowth >= 50 || now - lastStreamUpdate >= STREAM_UPDATE_INTERVAL

            if (shouldUpdate) {
                lastStreamUpdate = now

                state.update { currentState ->
                    val segments = currentState.chatStreamSegments

                    // 检查是否应该追加（内容长度递增）
                    val shouldAppend = currentState.chatStream == null || streamBuffer.length >= (currentState.chatStream?.length ?: 0)

                    val newStream = if (shouldAppend) streamBuffer else currentState.chatStream

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
    }

    /**
     * 处理 final 状态（消息完成）
     * 流式优化：强制刷新缓冲区
     */
    private fun handleFinal(runId: String, msgObj: JsonObject?, sessionId: String) {
        // 流式优化：final 时强制刷新缓冲区
        if (streamBuffer.isNotBlank()) {
            state.update { currentState ->
                currentState.copy(
                    chatStream = streamBuffer  // 确保最终内容完整
                )
            }
            streamBuffer = ""
            lastStreamUpdate = 0L
            totalReceivedChars = 0
        }
        
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