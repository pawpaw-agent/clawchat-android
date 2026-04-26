package com.openclaw.clawchat.ui.state

import com.openclaw.clawchat.util.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 工具流管理器 (优化版)
 *
 * 优化点：
 * - 80ms 节流：批量更新而非每事件更新
 * - FIFO 淘汰：超出 50 条上限时移除最旧的
 * - 120K 字符截断：防止超长输出崩溃 UI
 *
 * 与 WebChat 保持一致：不保存到本地 DB，依赖 Gateway reload
 */
class ToolStreamManager(
    private val state: MutableStateFlow<SessionUiState>,
    private val scope: CoroutineScope,
    private val limit: Int = 50,
    private val throttleMs: Long = 80L,
    private val maxPayloadChars: Int = 120_000
) {
    companion object {
        private const val TAG = "ToolStreamManager"
    }

    // 待处理的工具事件（内存中缓存）
    private val pendingEvents = mutableMapOf<String, ToolStreamEvent>()
    private var flushJob: Job? = null

    /**
     * 处理工具流事件（批量节流）
     */
    fun handleToolStreamEvent(payload: JsonObject) {
        val data = payload["data"]?.jsonObject ?: return

        val toolCallId = data["toolCallId"]?.jsonPrimitive?.content ?: return
        val name = data["name"]?.jsonPrimitive?.content ?: "tool"
        val phase = data["phase"]?.jsonPrimitive?.content ?: "start"
        val isError = data["isError"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false

        // result 和 partialResult 是对象结构: { content: [{ type: "text", text: "..." }] }
        fun extractTextFromContent(jsonArray: JsonArray?): String? {
            if (jsonArray == null) return null
            for (i in 0 until jsonArray.size) {
                val element = jsonArray[i]
                if (element is JsonObject) {
                    val textElement = element.get("text")
                    if (textElement is JsonPrimitive) {
                        return textElement.content
                    }
                }
            }
            return null
        }

        val resultContent = extractTextFromContent(data["result"]?.jsonObject?.get("content")?.jsonArray)
        val partialResultContent = extractTextFromContent(data["partialResult"]?.jsonObject?.get("content")?.jsonArray)
        val runId = payload["runId"]?.jsonPrimitive?.content ?: ""
        val sessionKey = payload["sessionKey"]?.jsonPrimitive?.content

        // 获取现有 entry 并计算最终输出
        val existingEntry = pendingEvents[toolCallId]
        val currentOutput = existingEntry?.output ?: ""

        val finalOutput = when {
            phase == "result" -> resultContent ?: partialResultContent ?: currentOutput
            partialResultContent != null -> currentOutput + partialResultContent
            resultContent != null -> resultContent
            isError -> currentOutput
            else -> currentOutput
        }

        // 截断超长输出
        val truncatedOutput = if (finalOutput.length > maxPayloadChars) {
            finalOutput.take(maxPayloadChars)
        } else {
            finalOutput
        }

        // 创建/更新 entry
        val ts = payload["ts"]?.jsonPrimitive?.content?.toLongOrNull() ?: System.currentTimeMillis()
        val args = if (phase == "start") {
            data["args"]?.jsonObject?.let { truncateJson(it) }
        } else null

        pendingEvents[toolCallId] = ToolStreamEvent(
            toolCallId = toolCallId,
            name = name,
            status = phase,
            title = data["title"]?.jsonPrimitive?.content,
            output = truncatedOutput,
            error = if (isError) "Error" else null,
            stream = partialResultContent,
            timestamp = ts
        )

        // 调度批量刷新（80ms 节流）
        scheduleFlush()
    }

    /**
     * 清除所有工具流状态
     */
    fun clear() {
        flushJob?.cancel()
        pendingEvents.clear()
        flushNow()
    }

    /**
     * 调度刷新（节流）
     */
    private fun scheduleFlush() {
        if (flushJob?.isActive == true) return
        flushJob = scope.launch(Dispatchers.Main) {
            delay(throttleMs)
            flushNow()
        }
    }

    /**
     * 立即刷新状态
     */
    private fun flushNow() {
        state.update { currentState ->
            val now = System.currentTimeMillis()

            // 获取现有 entries 并合并新事件
            val toolStreamById = currentState.toolStreamById.toMutableMap()
            val toolStreamOrder = currentState.toolStreamOrder.toMutableList()
            var chatStreamSegments = currentState.chatStreamSegments
            var chatStream = currentState.chatStream
            var chatStreamStartedAt = currentState.chatStreamStartedAt

            // 合并 pendingEvents 到 toolStreamById
            for ((toolCallId, event) in pendingEvents) {
                // 如果是新的 toolCall，提交当前流式文本到 segments
                if (toolCallId !in toolStreamById) {
                    if (!chatStream.isNullOrBlank()) {
                        chatStreamSegments = chatStreamSegments + StreamSegment(chatStream.trim(), now)
                        chatStream = null
                        chatStreamStartedAt = null
                    }
                }

                toolStreamById[toolCallId] = event
                if (toolCallId !in toolStreamOrder) {
                    toolStreamOrder.add(toolCallId)
                }
            }

            // FIFO 淘汰超出上限的旧条目
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

            // 清空 pendingEvents（已合并到 state）
            pendingEvents.clear()

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
     * 截断 JsonObject 字符串
     */
    private fun truncateJson(json: JsonObject): JsonObject {
        val raw = json.toString()
        if (raw.length <= maxPayloadChars) return json
        return try {
            val truncated = raw.take(maxPayloadChars)
            Json.parseToJsonElement(truncated).jsonObject
        } catch (_: Throwable) {
            json
        }
    }
}
