package com.openclaw.clawchat.ui.state

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openclaw.clawchat.network.WebSocketConnectionState
import com.openclaw.clawchat.network.protocol.GatewayConnection
import com.openclaw.clawchat.repository.MessageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID
import javax.inject.Inject

/**
 * 会话界面 ViewModel
 *
 * ChatEvent 状态机：
 * - delta → 追加到流式缓冲（按 runId 分组）
 * - final → 完成消息，写入 Room 缓存
 * - aborted → 标记中止，保留部分内容
 * - error → 显示错误
 */
@HiltViewModel
class SessionViewModel @Inject constructor(
    private val gateway: GatewayConnection,
    private val messageRepository: MessageRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _state = MutableStateFlow(SessionUiState())
    val state: StateFlow<SessionUiState> = _state.asStateFlow()

    private val _events = Channel<SessionEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "Uncaught coroutine exception", throwable)
        _state.update { it.copy(error = throwable.message ?: "未知错误", isLoading = false, isSending = false) }
    }

    /** 流式消息缓冲：runId → 累积内容 */
    private val streamingBuffers = LinkedHashMap<String, StringBuilder>()

    /** 已完成的 runId（防止 final 后重复处理 delta） */
    private val completedRuns = LinkedHashSet<String>()

    companion object {
        private const val TAG = "SessionViewModel"
        private const val MAX_TRACKED_RUNS = 100
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    }

    init {
        observeConnectionState()
        observeIncomingMessages()
        loadMessageHistory()
    }

    private fun observeConnectionState() {
        viewModelScope.launch(exceptionHandler) {
            gateway.connectionState.collect { connectionState ->
                val status = when (connectionState) {
                    is WebSocketConnectionState.Connected -> ConnectionStatus.Connected()
                    is WebSocketConnectionState.Connecting -> ConnectionStatus.Connecting
                    is WebSocketConnectionState.Disconnecting -> ConnectionStatus.Disconnecting
                    is WebSocketConnectionState.Disconnected -> ConnectionStatus.Disconnected
                    is WebSocketConnectionState.Error -> ConnectionStatus.Error(
                        connectionState.throwable.message ?: "连接错误",
                        throwable = connectionState.throwable
                    )
                }
                _state.update { it.copy(connectionStatus = status) }
            }
        }
    }

    private fun loadMessageHistory() {
        viewModelScope.launch(exceptionHandler) {
            val sessionId = savedStateHandle.get<String>("sessionId") ?: return@launch
            
            // 先从本地数据库加载（快速显示）
            messageRepository.observeMessages(sessionId).collect { messages ->
                _state.update { it.copy(messages = messages) }
            }
        }
        
        // 同时从 Gateway 获取历史消息（确保完整）
        viewModelScope.launch(exceptionHandler) {
            val sessionId = savedStateHandle.get<String>("sessionId") ?: return@launch
            try {
                val response = gateway.chatHistory(sessionId, limit = 50)
                if (response.isSuccess() && response.payload is JsonObject) {
                    val payload = response.payload as JsonObject
                    val messagesArray = payload["messages"]?.jsonArray
                    if (messagesArray != null) {
                        messagesArray.forEach { msgElement ->
                            try {
                                val msgObj = msgElement.jsonObject
                                val contentItems = extractContent(msgObj["content"])
                                val originalRole = msgObj["role"]?.jsonPrimitive?.content ?: "assistant"
                                val timestamp = msgObj["timestamp"]?.jsonPrimitive?.content?.toLongOrNull() 
                                    ?: System.currentTimeMillis()
                                
                                // 存储为 JSON 字符串
                                val contentJson = json.encodeToString(
                                    kotlinx.serialization.serializer<List<Map<String, String?>>>(),
                                    contentItems.map { item ->
                                        when (item) {
                                            is MessageContentItem.Text -> mapOf("type" to "text", "text" to item.text)
                                            is MessageContentItem.ToolCall -> mapOf(
                                                "type" to "tool_call",
                                                "id" to item.id,
                                                "name" to item.name
                                            )
                                            is MessageContentItem.ToolResult -> mapOf(
                                                "type" to "tool_result",
                                                "name" to item.name,
                                                "args" to item.args?.toString(),
                                                "text" to item.text
                                            )
                                            is MessageContentItem.Image -> mapOf(
                                                "type" to "image",
                                                "url" to item.url
                                            )
                                        }
                                    }
                                )
                                
                                // 保存到本地数据库（使用 resolveMessageRole 检测工具消息）
                                messageRepository.saveMessage(
                                    sessionId = sessionId,
                                    role = resolveMessageRole(msgObj, originalRole),
                                    content = contentJson,
                                    timestamp = timestamp
                                )
                            } catch (e: Exception) {
                                android.util.Log.w("SessionViewModel", "Failed to parse history message: ${e.message}")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("SessionViewModel", "Failed to load chat history: ${e.message}")
            }
        }
    }

    // ── ChatEvent 状态机 ──

    private fun observeIncomingMessages() {
        viewModelScope.launch(exceptionHandler) {
            gateway.incomingMessages.collect { rawJson ->
                handleIncomingFrame(rawJson)
            }
        }
    }

    private fun handleIncomingFrame(rawJson: String) {
        viewModelScope.launch(exceptionHandler) {
            val sessionId = _state.value.sessionId ?: return@launch

            try {
                val obj = json.parseToJsonElement(rawJson).jsonObject
                val type = obj["type"]?.jsonPrimitive?.content
                val event = obj["event"]?.jsonPrimitive?.content

                if (type != "event" || event != "chat") return@launch

                val payload = obj["payload"]?.jsonObject ?: return@launch
                val eventSessionKey = payload["sessionKey"]?.jsonPrimitive?.content ?: return@launch

                // 仅处理当前会话的事件
                if (eventSessionKey != sessionId) return@launch

                val runId = payload["runId"]?.jsonPrimitive?.content ?: return@launch
                val state = payload["state"]?.jsonPrimitive?.content ?: return@launch
                val msgObj = payload["message"]?.jsonObject
                val contentItems = extractContent(msgObj?.get("content"))
                val originalRole = msgObj?.get("role")?.jsonPrimitive?.content ?: "assistant"
                // 使用 resolveMessageRole 检测工具消息
                val uiRole = resolveMessageRole(msgObj, originalRole)

                when (state) {
                    "delta" -> handleDelta(runId, contentItems, uiRole)
                    "final" -> handleFinal(runId, contentItems, uiRole, sessionId)
                    "aborted" -> handleAborted(runId, sessionId)
                    "error" -> {
                        val errorMsg = payload["errorMessage"]?.jsonPrimitive?.content ?: "Unknown error"
                        handleError(runId, errorMsg)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse chat event: ${e.message}")
            }
        }
    }

    /** delta: 追加到流式缓冲，更新 UI 显示 */
    private fun handleDelta(runId: String, contentItems: List<MessageContentItem>, role: MessageRole) {
        if (completedRuns.contains(runId)) return

        // 提取文本内容追加到缓冲
        val textContent = contentItems.filterIsInstance<MessageContentItem.Text>()
            .joinToString("") { it.text }
        
        val buffer = streamingBuffers.getOrPut(runId) { StringBuilder() }
        buffer.append(textContent)

        // 更新或添加流式消息
        _state.update { currentState ->
            val existingIdx = currentState.messages.indexOfFirst { it.id == runId }
            
            // 合并已有内容和新增内容
            val existingItems = if (existingIdx >= 0) {
                currentState.messages[existingIdx].content
            } else {
                emptyList()
            }
            
            // 合并工具调用（保留）和文本（更新）
            val mergedItems = existingItems.filter { 
                it is MessageContentItem.ToolCall || it is MessageContentItem.ToolResult 
            } + listOf(MessageContentItem.Text(buffer.toString()))
            
            val streamingMsg = MessageUi(
                id = runId,
                content = mergedItems,
                role = role,
                timestamp = System.currentTimeMillis(),
                isLoading = true
            )

            val newMessages = if (existingIdx >= 0) {
                currentState.messages.toMutableList().apply { set(existingIdx, streamingMsg) }
            } else {
                currentState.messages + streamingMsg
            }

            currentState.copy(messages = newMessages, isLoading = true)
        }
    }

    /** final: 完成消息，写入缓存 */
    private suspend fun handleFinal(runId: String, contentItems: List<MessageContentItem>, role: MessageRole, sessionId: String) {
        completedRuns.add(runId)
        evictOldRuns()

        // 合并缓冲中的文本内容和 final 的工具内容
        val bufferedText = streamingBuffers.remove(runId)?.toString() ?: ""
        
        // 构建最终内容：如果有缓冲文本，使用缓冲；否则使用 final 内容
        val finalItems = if (bufferedText.isNotBlank()) {
            // 保留工具调用/结果，更新文本
            contentItems.filter { 
                it is MessageContentItem.ToolCall || it is MessageContentItem.ToolResult 
            } + MessageContentItem.Text(bufferedText)
        } else {
            contentItems
        }

        // 写入 Room 缓存（存储为 JSON 字符串）
        val contentJson = json.encodeToString(
            kotlinx.serialization.serializer<List<Map<String, String?>>>(),
            finalItems.map { item ->
                when (item) {
                    is MessageContentItem.Text -> mapOf("type" to "text", "text" to item.text)
                    is MessageContentItem.ToolCall -> mapOf(
                        "type" to "tool_call",
                        "id" to item.id,
                        "name" to item.name
                    )
                    is MessageContentItem.ToolResult -> mapOf(
                        "type" to "tool_result",
                        "name" to item.name,
                        "args" to item.args?.toString(),
                        "text" to item.text,
                        "isError" to item.isError.toString()
                    )
                    is MessageContentItem.Image -> mapOf(
                        "type" to "image",
                        "url" to item.url
                    )
                }
            }
        )
        
        messageRepository.saveMessage(
            sessionId = sessionId,
            role = role,
            content = contentJson,
            timestamp = System.currentTimeMillis()
        )

        val finalMsg = MessageUi(
            id = runId,
            content = finalItems,
            role = role,
            timestamp = System.currentTimeMillis(),
            isLoading = false
        )

        _state.update { currentState ->
            val existingIdx = currentState.messages.indexOfFirst { it.id == runId }
            val newMessages = if (existingIdx >= 0) {
                currentState.messages.toMutableList().apply { set(existingIdx, finalMsg) }
            } else {
                currentState.messages + finalMsg
            }
            currentState.copy(messages = newMessages, isLoading = false)
        }

        _events.trySend(SessionEvent.MessageReceived(finalMsg))
    }

    /** aborted: 标记中止 */
    private fun handleAborted(runId: String, sessionId: String) {
        completedRuns.add(runId)
        val partialContent = streamingBuffers.remove(runId)?.toString() ?: ""

        _state.update { currentState ->
            val existingIdx = currentState.messages.indexOfFirst { it.id == runId }
            if (existingIdx >= 0) {
                val existingMsg = currentState.messages[existingIdx]
                val updatedContent = existingMsg.content.filter { 
                    it is MessageContentItem.ToolCall || it is MessageContentItem.ToolResult 
                } + MessageContentItem.Text(partialContent + "\n[已中止]")
                
                val msg = existingMsg.copy(
                    content = updatedContent,
                    isLoading = false
                )
                currentState.copy(
                    messages = currentState.messages.toMutableList().apply { set(existingIdx, msg) },
                    isLoading = false
                )
            } else {
                currentState.copy(isLoading = false)
            }
        }
    }

    /** error: 显示错误 */
    private fun handleError(runId: String, errorMsg: String) {
        completedRuns.add(runId)
        streamingBuffers.remove(runId)

        _state.update { it.copy(error = errorMsg, isLoading = false) }
        _events.trySend(SessionEvent.Error(errorMsg))
    }

    // ── 容量管理 ──

    /** 清理超出容量的旧 run 记录 */
    private fun evictOldRuns() {
        while (completedRuns.size > MAX_TRACKED_RUNS) {
            val oldest = completedRuns.first()
            completedRuns.remove(oldest)
            streamingBuffers.remove(oldest)
        }
        while (streamingBuffers.size > MAX_TRACKED_RUNS) {
            val oldest = streamingBuffers.keys.first()
            streamingBuffers.remove(oldest)
        }
    }

    // ── 发送消息 ──

    fun sendMessage(content: String) {
        viewModelScope.launch(exceptionHandler) {
            if (content.isBlank()) {
                _events.trySend(SessionEvent.Error("消息内容不能为空"))
                return@launch
            }
            if (_state.value.connectionStatus !is ConnectionStatus.Connected) {
                _events.trySend(SessionEvent.Error("未连接到网关"))
                return@launch
            }

            val sessionKey = _state.value.sessionId
                ?: gateway.defaultSessionKey
                ?: return@launch

            // 显示用户消息
            val userMessage = MessageUi(
                id = UUID.randomUUID().toString(),
                content = listOf(MessageContentItem.Text(content)),
                role = MessageRole.USER,
                timestamp = System.currentTimeMillis()
            )

            // 存储为 JSON
            val contentJson = json.encodeToString(
                kotlinx.serialization.serializer<List<Map<String, String?>>>(),
                listOf(mapOf("type" to "text", "text" to content))
            )

            messageRepository.saveMessage(
                sessionId = sessionKey,
                role = MessageRole.USER,
                content = contentJson,
                timestamp = userMessage.timestamp
            )

            _state.update {
                it.copy(
                    messages = it.messages + userMessage,
                    inputText = "",
                    isLoading = true
                )
            }

            // 发送到 Gateway
            try {
                gateway.chatSend(sessionKey, content)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send message", e)
                _state.update { it.copy(isLoading = false, error = "发送失败：${e.message}") }
                _events.trySend(SessionEvent.Error("发送失败：${e.message}"))
            }
        }
    }

    fun updateInputText(text: String) {
        _state.update { it.copy(inputText = text) }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    fun setSessionId(sessionId: String) {
        _state.update { it.copy(sessionId = sessionId) }
    }

    fun consumeEvent() {
        // Events are consumed via Channel.receiveAsFlow()
    }

    private fun parseRole(role: String): MessageRole = when (role.lowercase().replace("_", "")) {
        "user" -> MessageRole.USER
        "assistant" -> MessageRole.ASSISTANT
        "system" -> MessageRole.SYSTEM
        "toolresult", "tool" -> MessageRole.TOOL
        else -> MessageRole.ASSISTANT
    }
    
    /**
     * 解析消息角色，结合原始角色和工具消息检测
     * 参考 webchat message-normalizer.ts
     */
    private fun resolveMessageRole(msgObj: JsonObject?, originalRole: String): MessageRole {
        if (msgObj == null) return parseRole(originalRole)
        
        // 检测是否为工具消息
        if (detectToolMessage(msgObj)) {
            return MessageRole.TOOL
        }
        
        return parseRole(originalRole)
    }
    
    /**
     * 检测消息是否为工具消息
     * 只有 toolResult（工具执行结果）才应该标记为 TOOL 角色
     * toolCall 是助手消息的一部分，不应标记为 TOOL
     */
    private fun detectToolMessage(msgObj: JsonObject): Boolean {
        val content = msgObj["content"]
        if (content is JsonArray) {
            content.forEach { part ->
                if (part is JsonObject) {
                    val type = part["type"]?.jsonPrimitive?.content?.lowercase()?.replace("_", "")
                    // 只检测工具结果，不检测工具调用
                    if (type == "toolresult") {
                        return true
                    }
                }
            }
        }
        return false
    }

    /**
 * 提取 message.content，支持多种格式：
 * - 字符串：`"content": "Hello"`
 * - 数组：`"content": [{"type": "text", "text": "Hello"}, {"type": "tool_call", ...}]`
 * 
 * 支持 webchat 兼容的类型别名：
 * - tool_call / toolcall / tool_use / tooluse
 * - tool_result / toolresult
 * - image / imageurl
 * 
 * 返回 MessageContentItem 列表，包含 text、tool_call、tool_result 等
 * 
 * 配对逻辑：当同时有 toolCall 和 toolResult 时，按 toolCallId 配对合并为一个 ToolResult
 */
    private fun extractContent(element: JsonElement?): List<MessageContentItem> {
        if (element == null) return emptyList()
        
        android.util.Log.d("ClawChat", "=== extractContent raw: $element")
        
        return when (element) {
            is JsonPrimitive -> listOf(MessageContentItem.Text(element.content))
            is JsonArray -> {
                // 第一遍：解析所有内容项
                val textItems = mutableListOf<MessageContentItem.Text>()
                val toolCalls = mutableListOf<MessageContentItem.ToolCall>()
                val toolResults = mutableListOf<MessageContentItem.ToolResult>()
                val imageItems = mutableListOf<MessageContentItem.Image>()
                
                element.forEach { part ->
                    when {
                        part is JsonObject -> {
                            android.util.Log.d("ClawChat", "=== part: $part")
                            
                            val type = part["type"]?.jsonPrimitive?.content?.lowercase()
                            val typeNormalized = type?.replace("_", "")
                            
                            when (typeNormalized) {
                                "text" -> {
                                    textItems.add(MessageContentItem.Text(
                                        text = part["text"]?.jsonPrimitive?.content ?: ""
                                    ))
                                }
                                "tool" -> {
                                    textItems.add(MessageContentItem.Text(
                                        text = part["text"]?.jsonPrimitive?.content ?: ""
                                    ))
                                }
                                "toolcall", "tooluse" -> {
                                    val toolName = part["name"]?.jsonPrimitive?.content ?: "tool"
                                    val argsElement = part["arguments"] ?: part["args"]
                                    val args = when (argsElement) {
                                        is JsonObject -> argsElement
                                        is JsonPrimitive -> {
                                            try {
                                                json.parseToJsonElement(argsElement.content).jsonObject
                                            } catch (e: Exception) { null }
                                        }
                                        else -> null
                                    }
                                    toolCalls.add(MessageContentItem.ToolCall(
                                        id = part["id"]?.jsonPrimitive?.content,
                                        name = toolName,
                                        args = args
                                    ))
                                }
                                "toolresult" -> {
                                    val toolName = part["name"]?.jsonPrimitive?.content 
                                        ?: part["toolName"]?.jsonPrimitive?.content 
                                        ?: part["tool_name"]?.jsonPrimitive?.content ?: "tool"
                                    val resultText = part["text"]?.jsonPrimitive?.content 
                                        ?: part["content"]?.jsonPrimitive?.content
                                        ?: part["arguments"]?.jsonPrimitive?.content
                                        ?: part["args"]?.jsonPrimitive?.content ?: ""
                                    val isError = part["isError"]?.jsonPrimitive?.content?.toBoolean() ?: false
                                    toolResults.add(MessageContentItem.ToolResult(
                                        toolCallId = part["toolCallId"]?.jsonPrimitive?.content 
                                            ?: part["tool_call_id"]?.jsonPrimitive?.content,
                                        name = toolName,
                                        text = resultText,
                                        isError = isError
                                    ))
                                }
                                "image", "imageurl" -> {
                                    imageItems.add(MessageContentItem.Image(
                                        url = part["url"]?.jsonPrimitive?.content 
                                            ?: part["imageUrl"]?.jsonPrimitive?.content,
                                        base64 = part["base64"]?.jsonPrimitive?.content,
                                        mimeType = part["mimeType"]?.jsonPrimitive?.content
                                    ))
                                }
                                else -> {
                                    // 尝试检测工具调用或工具结果
                                    val name = part["name"]?.jsonPrimitive?.content
                                    val argsElement = part["arguments"] ?: part["args"]
                                    val textContent = part["text"]?.jsonPrimitive?.content ?: part["content"]?.jsonPrimitive?.content
                                    val toolCallId = part["toolCallId"]?.jsonPrimitive?.content ?: part["tool_call_id"]?.jsonPrimitive?.content
                                    
                                    if (toolCallId != null || (textContent != null && argsElement == null)) {
                                        toolResults.add(MessageContentItem.ToolResult(
                                            toolCallId = toolCallId,
                                            name = name ?: "tool",
                                            text = textContent ?: "",
                                            isError = part["isError"]?.jsonPrimitive?.content?.toBoolean() ?: false
                                        ))
                                    } else if (name != null && argsElement != null) {
                                        val args = when (argsElement) {
                                            is JsonObject -> argsElement
                                            is JsonPrimitive -> {
                                                try {
                                                    json.parseToJsonElement(argsElement.content).jsonObject
                                                } catch (e: Exception) { null }
                                            }
                                            else -> null
                                        }
                                        toolCalls.add(MessageContentItem.ToolCall(
                                            id = part["id"]?.jsonPrimitive?.content,
                                            name = name,
                                            args = args
                                        ))
                                    } else if (textContent != null) {
                                        textItems.add(MessageContentItem.Text(textContent))
                                    }
                                }
                            }
                        }
                        part is JsonPrimitive -> {
                            textItems.add(MessageContentItem.Text(part.content))
                        }
                        else -> {}
                    }
                }
                
                // 调试日志：打印解析结果
                android.util.Log.d("ClawChat", "=== toolCalls count: ${toolCalls.size}")
                toolCalls.forEach { call ->
                    android.util.Log.d("ClawChat", "=== toolCall: id=${call.id}, name=${call.name}")
                }
                android.util.Log.d("ClawChat", "=== toolResults count: ${toolResults.size}")
                toolResults.forEach { result ->
                    android.util.Log.d("ClawChat", "=== toolResult: toolCallId=${result.toolCallId}, name=${result.name}, text=${result.text.take(50)}")
                }
                
                // 配对 toolCalls 和 toolResults
                val mergedResults = mutableListOf<MessageContentItem.ToolResult>()
                val callsById = toolCalls.associateBy { it.id }
                val resultsById = toolResults.associateBy { it.toolCallId }
                
                // 已配对的 ID
                val pairedIds = mutableSetOf<String?>()
                
                // 遍历 toolCalls，尝试配对
                toolCalls.forEach { call ->
                    val callId = call.id
                    val matchingResult = if (callId != null) resultsById[callId] else null
                    
                    if (matchingResult != null) {
                        // 配对成功：合并为包含参数的 ToolResult
                        mergedResults.add(MessageContentItem.ToolResult(
                            toolCallId = callId,
                            name = call.name,
                            args = call.args,  // 从 toolCall 获取参数
                            text = matchingResult.text,
                            isError = matchingResult.isError
                        ))
                        pairedIds.add(callId)
                    } else {
                        // 没有配对：保留 toolCall（等待结果）
                        mergedResults.add(MessageContentItem.ToolResult(
                            toolCallId = callId,
                            name = call.name,
                            args = call.args,
                            text = "",  // 暂无结果
                            isError = false
                        ))
                    }
                }
                
                // 添加未配对的 toolResults
                toolResults.forEach { result ->
                    if (result.toolCallId !in pairedIds) {
                        mergedResults.add(result)
                    }
                }
                
                // 调试日志：打印配对结果
                android.util.Log.d("ClawChat", "=== mergedResults count: ${mergedResults.size}")
                mergedResults.forEach { result ->
                    android.util.Log.d("ClawChat", "=== mergedResult: toolCallId=${result.toolCallId}, name=${result.name}, hasArgs=${result.args != null}, text=${result.text.take(50)}")
                }
                
                // 组合所有内容：文本 + 合并的工具结果 + 图片
                textItems + mergedResults + imageItems
            }
            else -> listOf(MessageContentItem.Text(element.jsonPrimitive?.content ?: ""))
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Session Events
// ─────────────────────────────────────────────────────────────

sealed class SessionEvent {
    data class MessageReceived(val message: MessageUi) : SessionEvent()
    data class Error(val message: String) : SessionEvent()
    data object MessageSent : SessionEvent()
}