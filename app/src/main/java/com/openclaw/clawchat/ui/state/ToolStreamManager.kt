package com.openclaw.clawchat.ui.state

import com.openclaw.clawchat.util.AppLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 工具流管理器
 * 
 * 负责管理工具流状态：
 * - handleToolStreamEvent
 * - toolStreamById / toolStreamOrder 管理
 * - chatToolMessages 构建
 * 
 * 与 WebChat 保持一致：不保存到本地 DB，依赖 Gateway reload
 */
class ToolStreamManager(
    private val state: MutableStateFlow<SessionUiState>,
    private val limit: Int = 50
) {
    companion object {
        private const val TAG = "ToolStreamManager"
    }

    /**
     * 处理工具流事件
     * Gateway agent event 结构 (源码验证):
     *   { runId, seq, stream: "tool", ts, data: { phase, name, toolCallId, args?, result?, partialResult?, isError? }, sessionKey }
     */
    fun handleToolStreamEvent(payload: JsonObject) {
        AppLog.d(TAG, "=== handleToolStreamEvent called, payload keys: ${payload.keys}")
        
        // 从 payload.data 提取工具信息
        val data = payload["data"]?.jsonObject
        if (data == null) {
            AppLog.w(TAG, "=== handleToolStreamEvent: no data field in payload")
            return
        }
        AppLog.d(TAG, "=== data keys: ${data.keys}, data=$data")
        
        val toolCallId = data["toolCallId"]?.jsonPrimitive?.content ?: return
        val name = data["name"]?.jsonPrimitive?.content ?: "tool"
        val phase = data["phase"]?.jsonPrimitive?.content ?: "start"
        val resultContent = data["result"]?.jsonPrimitive?.content
        val partialResultContent = data["partialResult"]?.jsonPrimitive?.content
        val isError = data["isError"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
        
        AppLog.d(TAG, "=== result=$resultContent, partialResult=$partialResultContent")
        val runId = payload["runId"]?.jsonPrimitive?.content ?: ""
        val sessionKey = payload["sessionKey"]?.jsonPrimitive?.content
        
        AppLog.d(TAG, "=== Tool stream event: toolCallId=$toolCallId, name=$name, phase=$phase")
        
        state.update { currentState ->
            val now = System.currentTimeMillis()
            
            // 获取或创建 entry
            val toolStreamById = currentState.toolStreamById.toMutableMap()
            val toolStreamOrder = currentState.toolStreamOrder.toMutableList()
            var chatStreamSegments = currentState.chatStreamSegments
            var chatStream = currentState.chatStream
            var chatStreamStartedAt = currentState.chatStreamStartedAt
            
            // 如果是新的 toolCall，提交当前流式文本到 segments
            if (toolCallId !in toolStreamById) {
                if (!chatStream.isNullOrBlank()) {
                    chatStreamSegments = chatStreamSegments + StreamSegment(chatStream.trim(), now)
                    chatStream = null
                    chatStreamStartedAt = null
                    AppLog.d(TAG, "=== Committed stream text to segment")
                }
            }
            
            // 获取现有 entry 并追加流式内容
            val existingEntry = toolStreamById[toolCallId]
            val currentOutput = existingEntry?.output ?: ""
            
            // 处理流式内容：partialResult 是增量，result 是最终结果
            val finalOutput = when {
                // phase=result 表示完成：使用最终 result
                phase == "result" -> resultContent ?: partialResultContent ?: currentOutput
                // partialResult 是增量：追加到现有内容
                partialResultContent != null -> currentOutput + partialResultContent
                // result 直接输出：替换
                resultContent != null -> resultContent
                // 错误情况
                isError -> currentOutput
                // 保持当前内容
                else -> currentOutput
            }
            
            // 更新或创建 entry
            val newEntry = if (existingEntry != null) {
                existingEntry.copy(
                    name = name,
                    output = finalOutput,
                    phase = phase,
                    isError = isError,
                    updatedAt = now
                )
            } else {
                val ts = payload["ts"]?.jsonPrimitive?.content?.toLongOrNull() ?: now
                // 从 data 提取 args（phase=start 时有 args）
                val args = if (phase == "start") {
                    data["args"]?.jsonObject
                } else null
                ToolStreamEntry(
                    toolCallId = toolCallId,
                    runId = runId,
                    sessionKey = sessionKey,
                    name = name,
                    args = args,
                    output = finalOutput,
                    phase = phase,
                    isError = isError,
                    startedAt = ts,
                    updatedAt = now
                )
            }
            
            toolStreamById[toolCallId] = newEntry
            AppLog.d(TAG, "=== Entry created/updated: toolCallId=$toolCallId, phase=${newEntry.phase}, output=${newEntry.output?.take(50)}")
            if (toolCallId !in toolStreamOrder) {
                toolStreamOrder.add(toolCallId)
            }
            
            // Trim overflow
            if (toolStreamOrder.size > limit) {
                val overflow = toolStreamOrder.size - limit
                val removed = toolStreamOrder.take(overflow)
                repeat(overflow) { if (toolStreamOrder.isNotEmpty()) toolStreamOrder.removeAt(0) }
                removed.forEach { toolStreamById.remove(it) }
            }
            
            // 构建 chatToolMessages
            val chatToolMessages = toolStreamOrder.mapNotNull { id ->
                toolStreamById[id]?.buildMessage()
            }
            
            AppLog.d(TAG, "=== handleToolStreamEvent: chatToolMessages.size=${chatToolMessages.size}, toolStreamOrder=${toolStreamOrder.size}")
            
            currentState.copy(
                toolStreamById = toolStreamById.toMap(),
                toolStreamOrder = toolStreamOrder,
                chatToolMessages = chatToolMessages,
                chatStreamSegments = chatStreamSegments,
                chatStream = chatStream,
                chatStreamStartedAt = chatStreamStartedAt
            )
        }
    }

    /**
     * 清除所有工具流状态
     */
    fun clear() {
        state.update { currentState ->
            currentState.copy(
                toolStreamById = emptyMap(),
                toolStreamOrder = emptyList(),
                chatToolMessages = emptyList()
            )
        }
    }
}