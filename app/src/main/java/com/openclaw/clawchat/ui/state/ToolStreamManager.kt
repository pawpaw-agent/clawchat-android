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
 */
class ToolStreamManager(
    private val state: MutableStateFlow<SessionUiState>,
    private val limit: Int = 50,
    private val onSaveToolMessage: ((sessionKey: String?, toolCallId: String, toolName: String, output: String?) -> Unit)? = null
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
        
        val toolCallId = data["toolCallId"]?.jsonPrimitive?.content ?: return
        val name = data["name"]?.jsonPrimitive?.content ?: "tool"
        val phase = data["phase"]?.jsonPrimitive?.content ?: "start"
        val resultContent = data["result"]?.jsonPrimitive?.content
        val partialResultContent = data["partialResult"]?.jsonPrimitive?.content
        val isError = data["isError"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
        val runId = payload["runId"]?.jsonPrimitive?.content ?: ""
        val sessionKey = payload["sessionKey"]?.jsonPrimitive?.content
        
        AppLog.d(TAG, "=== Tool stream event: toolCallId=$toolCallId, name=$name, phase=$phase")
        
        // 用于保存的输出（在 state.update 外部计算）
        var finalOutputForSave: String? = null
        
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
            
            // 保存用于后续调用 onSaveToolMessage
            finalOutputForSave = finalOutput
            
            // 更新或创建 entry
            val newEntry = if (existingEntry != null) {
                existingEntry.copy(
                    name = name,
                    output = finalOutput,
                    updatedAt = now
                )
            } else {
                val ts = payload["ts"]?.jsonPrimitive?.content?.toLongOrNull() ?: now
                ToolStreamEntry(
                    toolCallId = toolCallId,
                    runId = runId,
                    sessionKey = sessionKey,
                    name = name,
                    args = null,
                    output = finalOutput,
                    startedAt = ts,
                    updatedAt = now
                )
            }
            
            toolStreamById[toolCallId] = newEntry
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
        
        // phase=result 时保存工具消息到 DB（确保返回后可见）
        if (phase == "result" && onSaveToolMessage != null) {
            onSaveToolMessage.invoke(sessionKey, toolCallId, name, finalOutputForSave)
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