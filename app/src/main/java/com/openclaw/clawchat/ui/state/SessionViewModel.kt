package com.openclaw.clawchat.ui.state

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openclaw.clawchat.data.UserPreferences
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
 * 会话界面 ViewModel（1:1 复刻 webchat）
 */
@HiltViewModel
class SessionViewModel @Inject constructor(
    private val gateway: GatewayConnection,
    private val messageRepository: MessageRepository,
    private val savedStateHandle: SavedStateHandle,
    private val userPreferences: UserPreferences
) : ViewModel() {

    private val _state = MutableStateFlow(SessionUiState())
    val state: StateFlow<SessionUiState> = _state.asStateFlow()
    
    // 暴露字体大小设置（统一）
    val messageFontSize = userPreferences.messageFontSize

    private val _events = Channel<SessionEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "Uncaught coroutine exception", throwable)
        _state.update { it.copy(error = throwable.message ?: "未知错误", isLoading = false, isSending = false) }
    }

    companion object {
        private const val TAG = "SessionViewModel"
        private const val TOOL_STREAM_LIMIT = 50
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    }

    init {
        Log.d(TAG, "=== SessionViewModel init")
        observeConnectionState()
        observeIncomingMessages()
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

    private fun observeIncomingMessages() {
        viewModelScope.launch(exceptionHandler) {
            gateway.incomingMessages.collect { rawJson ->
                handleIncomingFrame(rawJson)
            }
        }
    }

    /**
     * 处理入站消息（1:1 复刻 webchat）
     */
    private fun handleIncomingFrame(rawJson: String) {
        viewModelScope.launch(exceptionHandler) {
            val sessionId = _state.value.sessionId
            Log.d(TAG, "=== handleIncomingFrame: sessionId=$sessionId, rawJson=${rawJson.take(100)}")
            
            if (sessionId == null) {
                Log.w(TAG, "=== handleIncomingFrame: sessionId is null, returning")
                return@launch
            }

            try {
                val obj = json.parseToJsonElement(rawJson).jsonObject
                val type = obj["type"]?.jsonPrimitive?.content
                val event = obj["event"]?.jsonPrimitive?.content
                Log.d(TAG, "=== handleIncomingFrame: type=$type, event=$event")

                if (type != "event") return@launch

                val payload = obj["payload"]?.jsonObject ?: return@launch
                val eventSessionKey = payload["sessionKey"]?.jsonPrimitive?.content
                
                // agent 事件：检查 stream 字段
                if (event == "agent") {
                    val stream = payload["stream"]?.jsonPrimitive?.content
                    Log.d(TAG, "=== handleIncomingFrame: agent event, stream=$stream")
                    if (stream != null) {
                        if (eventSessionKey != null && eventSessionKey != sessionId) {
                            return@launch
                        }
                        handleAgentEvent(payload, stream)
                    }
                    return@launch
                }

                // chat 事件
                if (event != "chat") return@launch
                
                val sessionKey = eventSessionKey ?: return@launch
                Log.d(TAG, "=== handleIncomingFrame: eventSessionKey=$sessionKey, payload keys=${payload.keys}")

                if (eventSessionKey != sessionId) return@launch

                // 处理 chat 事件
                handleChatEvent(payload, sessionId)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse chat event: ${e.message}")
            }
        }
    }

    /**
     * 处理 agent 事件（1:1 复刻 webchat handleAgentEvent）
     */
    private fun handleAgentEvent(payload: JsonObject, stream: String) {
        when (stream) {
            "tool" -> handleToolStreamEvent(payload)
            // 可以添加其他 stream 类型：compaction, fallback, lifecycle
        }
    }

    /**
     * 处理工具流事件（1:1 复刻 webchat）
     */
    private fun handleToolStreamEvent(payload: JsonObject) {
        val data = payload["data"]?.jsonObject ?: return
        val toolCallId = data["toolCallId"]?.jsonPrimitive?.content ?: return
        val name = data["name"]?.jsonPrimitive?.content ?: "tool"
        val phase = data["phase"]?.jsonPrimitive?.content ?: ""
        val runId = payload["runId"]?.jsonPrimitive?.content ?: ""
        val sessionKey = payload["sessionKey"]?.jsonPrimitive?.content
        
        Log.d(TAG, "=== Tool stream event: toolCallId=$toolCallId, name=$name, phase=$phase")
        
        _state.update { currentState ->
            val now = System.currentTimeMillis()
            
            // 获取或创建 entry
            val toolStreamById = currentState.toolStreamById.toMutableMap()
            val toolStreamOrder = currentState.toolStreamOrder.toMutableList()
            var chatStreamSegments = currentState.chatStreamSegments
            var chatStream = currentState.chatStream
            var chatStreamStartedAt = currentState.chatStreamStartedAt
            
            // 如果是新的 toolCall，提交当前流式文本到 segments
            if (toolCallId !in toolStreamById) {
                // 1:1 复刻 webchat: Commit any in-progress streaming text as a segment
                if (!chatStream.isNullOrBlank()) {
                    chatStreamSegments = chatStreamSegments + StreamSegment(chatStream.trim(), now)
                    chatStream = null
                    chatStreamStartedAt = null
                    Log.d(TAG, "=== Committed stream text to segment")
                }
            }
            
            // 解析 args 和 output
            val args = if (phase == "start") data["args"]?.jsonObject else null
            val output = when (phase) {
                "update" -> formatToolOutput(data["partialResult"])
                "result" -> formatToolOutput(data["result"])
                else -> null
            }
            
            // 更新或创建 entry
            val existingEntry = toolStreamById[toolCallId]
            val newEntry = if (existingEntry != null) {
                existingEntry.copy(
                    name = name,
                    args = args ?: existingEntry.args,
                    output = output ?: existingEntry.output,
                    updatedAt = now
                )
            } else {
                val ts = payload["ts"]?.jsonPrimitive?.content?.toLongOrNull() ?: now
                ToolStreamEntry(
                    toolCallId = toolCallId,
                    runId = runId,
                    sessionKey = sessionKey,
                    name = name,
                    args = args,
                    output = output,
                    startedAt = ts,
                    updatedAt = now
                )
            }
            
            toolStreamById[toolCallId] = newEntry
            if (toolCallId !in toolStreamOrder) {
                toolStreamOrder.add(toolCallId)
            }
            
            // Trim overflow
            if (toolStreamOrder.size > TOOL_STREAM_LIMIT) {
                val overflow = toolStreamOrder.size - TOOL_STREAM_LIMIT
                val removed = toolStreamOrder.take(overflow)
                repeat(overflow) { if (toolStreamOrder.isNotEmpty()) toolStreamOrder.removeAt(0) }
                removed.forEach { toolStreamById.remove(it) }
            }
            
            // 构建 chatToolMessages
            val chatToolMessages = toolStreamOrder.mapNotNull { id ->
                toolStreamById[id]?.buildMessage()
            }
            
            Log.d(TAG, "=== handleToolStreamEvent: chatToolMessages.size=${chatToolMessages.size}, toolStreamOrder=${toolStreamOrder.size}")
            
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
     * 处理 chat 事件（1:1 复刻 webchat handleChatEvent）
     */
    private fun handleChatEvent(payload: JsonObject, sessionId: String) {
        val runId = payload["runId"]?.jsonPrimitive?.content ?: return
        val state = payload["state"]?.jsonPrimitive?.content ?: return
        val msgObj = payload["message"]?.jsonObject
        
        Log.d(TAG, "=== handleChatEvent: runId=$runId, state=$state, msgObj=$msgObj")
        
        when (state) {
            "delta" -> handleDelta(runId, msgObj, sessionId)
            "final" -> handleFinal(runId, msgObj, sessionId)
            "aborted" -> handleAborted(runId, msgObj)
            "error" -> {
                val errorMsg = payload["errorMessage"]?.jsonPrimitive?.content ?: "Unknown error"
                handleError(runId, errorMsg)
            }
        }
    }

    /**
     * 处理 delta（1:1 复刻 webchat）
     */
    private fun handleDelta(runId: String, msgObj: JsonObject?, sessionId: String) {
        // 提取文本
        val text = extractText(msgObj?.get("content"))
        Log.d(TAG, "=== handleDelta: runId=$runId, text=${text?.take(50)}, textLen=${text?.length}")
        
        if (!text.isNullOrBlank() && !isSilentReplyStream(text)) {
            _state.update { currentState ->
                val current = currentState.chatStream ?: ""
                // webchat: if (!current || next.length >= current.length)
                val newStream = if (current.isBlank() || text.length >= current.length) text else current
                Log.d(TAG, "=== handleDelta: updating chatStream, newLen=${newStream.length}")
                
                currentState.copy(
                    chatStream = newStream,
                    chatRunId = runId,
                    chatStreamStartedAt = currentState.chatStreamStartedAt ?: System.currentTimeMillis()
                )
            }
        }
    }

    /**
     * 处理 final（1:1 复刻 webchat）
     */
    private fun handleFinal(runId: String, msgObj: JsonObject?, sessionId: String) {
        val finalMessage = normalizeFinalAssistantMessage(msgObj)
        Log.d(TAG, "=== handleFinal: runId=$runId, finalMessage=${finalMessage != null}, chatStream=${_state.value.chatStream?.take(30)}")
        
        _state.update { currentState ->
            val newMessages = if (finalMessage != null && !isAssistantSilentReply(finalMessage)) {
                Log.d(TAG, "=== handleFinal: adding finalMessage to chatMessages")
                currentState.chatMessages + finalMessage
            } else if (!currentState.chatStream.isNullOrBlank() && !isSilentReplyStream(currentState.chatStream)) {
                Log.d(TAG, "=== handleFinal: adding chatStream to chatMessages")
                currentState.chatMessages + MessageUi(
                    id = runId,
                    content = listOf(MessageContentItem.Text(currentState.chatStream.trim())),
                    role = MessageRole.ASSISTANT,
                    timestamp = System.currentTimeMillis()
                )
            } else {
                Log.d(TAG, "=== handleFinal: no message to add")
                currentState.chatMessages
            }
            
            Log.d(TAG, "=== handleFinal: chatMessages.size=${newMessages.size}")
            
            // 重置流式状态
            currentState.copy(
                chatMessages = newMessages,
                chatStream = null,
                chatRunId = null,
                chatStreamStartedAt = null,
                isLoading = false,
                isSending = false
            )
        }
        
        // 保存到数据库
        saveMessageToDb(runId, msgObj, sessionId)
    }

    /**
     * 处理 aborted
     */
    private fun handleAborted(runId: String, msgObj: JsonObject?) {
        _state.update { currentState ->
            val newMessages = if (!currentState.chatStream.isNullOrBlank()) {
                currentState.chatMessages + MessageUi(
                    id = runId,
                    content = listOf(MessageContentItem.Text(currentState.chatStream.trim())),
                    role = MessageRole.ASSISTANT,
                    timestamp = System.currentTimeMillis()
                )
            } else {
                currentState.chatMessages
            }
            
            currentState.copy(
                chatMessages = newMessages,
                chatStream = null,
                chatRunId = null,
                chatStreamStartedAt = null,
                isLoading = false,
                isSending = false
            )
        }
    }

    /**
     * 处理 error
     */
    private fun handleError(runId: String, errorMsg: String) {
        _state.update { currentState ->
            currentState.copy(
                chatStream = null,
                chatRunId = null,
                chatStreamStartedAt = null,
                error = errorMsg,
                isLoading = false,
                isSending = false
            )
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 工具函数（1:1 复刻 webchat）
    // ─────────────────────────────────────────────────────────────

    private fun formatToolOutput(value: JsonElement?): String? {
        if (value == null) return null
        
        return when (value) {
            is JsonPrimitive -> {
                val content = value.content.trim()
                if (content.isNotBlank() && content.length > 120000) {
                    "${content.take(120000)}\n\n… truncated (${content.length} chars, showing first 120000)."
                } else if (content.isNotBlank()) {
                    content
                } else null
            }
            is JsonObject -> {
                val text = extractText(value["content"] ?: value)
                if (!text.isNullOrBlank()) {
                    if (text.length > 120000) {
                        "${text.take(120000)}\n\n… truncated (${text.length} chars, showing first 120000)."
                    } else text
                } else {
                    try { json.encodeToString(JsonObject.serializer(), value) } catch (_: Exception) { null }
                }
            }
            is JsonArray -> {
                val text = extractText(value)
                if (!text.isNullOrBlank()) text else null
            }
            else -> null
        }
    }

    private fun extractText(content: JsonElement?): String? {
        if (content == null) return null
        
        return when (content) {
            is JsonPrimitive -> content.content.trim().takeIf { it.isNotBlank() }
            is JsonArray -> {
                content.mapNotNull { item ->
                    if (item is JsonObject) {
                        val type = item["type"]?.jsonPrimitive?.content
                        if (type == "text") item["text"]?.jsonPrimitive?.content
                        else null
                    } else null
                }.joinToString("\n").trim().takeIf { it.isNotBlank() }
            }
            is JsonObject -> {
                if (content.containsKey("text")) {
                    content["text"]?.jsonPrimitive?.content?.trim()?.takeIf { it.isNotBlank() }
                } else null
            }
            else -> null
        }
    }

    private fun normalizeFinalAssistantMessage(msgObj: JsonObject?): MessageUi? {
        if (msgObj == null) return null
        
        val role = msgObj["role"]?.jsonPrimitive?.content?.lowercase() ?: "assistant"
        if (role != "assistant") return null
        
        val content = parseContent(msgObj["content"])
        if (content.isEmpty()) {
            // 允许只有 text 字段
            val text = msgObj["text"]?.jsonPrimitive?.content ?: return null
            return MessageUi(
                id = msgObj["id"]?.jsonPrimitive?.content ?: UUID.randomUUID().toString(),
                content = listOf(MessageContentItem.Text(text)),
                role = MessageRole.ASSISTANT,
                timestamp = msgObj["timestamp"]?.jsonPrimitive?.content?.toLongOrNull() ?: System.currentTimeMillis()
            )
        }
        
        return MessageUi(
            id = msgObj["id"]?.jsonPrimitive?.content ?: UUID.randomUUID().toString(),
            content = content,
            role = MessageRole.ASSISTANT,
            timestamp = msgObj["timestamp"]?.jsonPrimitive?.content?.toLongOrNull() ?: System.currentTimeMillis()
        )
    }

    private fun parseContent(content: JsonElement?): List<MessageContentItem> {
        if (content == null) return emptyList()
        
        return when (content) {
            is JsonPrimitive -> listOf(MessageContentItem.Text(content.content))
            is JsonArray -> content.mapNotNull { part ->
                if (part is JsonObject) {
                    val type = part["type"]?.jsonPrimitive?.content?.lowercase()?.replace("_", "")
                    when (type) {
                        "text" -> MessageContentItem.Text(part["text"]?.jsonPrimitive?.content ?: "")
                        "toolcall", "tooluse" -> MessageContentItem.ToolCall(
                            id = part["id"]?.jsonPrimitive?.content,
                            name = part["name"]?.jsonPrimitive?.content ?: "tool",
                            args = part["arguments"]?.jsonObject ?: part["args"]?.jsonObject
                        )
                        "toolresult" -> MessageContentItem.ToolResult(
                            toolCallId = part["toolCallId"]?.jsonPrimitive?.content ?: part["tool_call_id"]?.jsonPrimitive?.content,
                            name = part["name"]?.jsonPrimitive?.content,
                            text = part["text"]?.jsonPrimitive?.content ?: ""
                        )
                        "image", "imageurl" -> MessageContentItem.Image(
                            url = part["url"]?.jsonPrimitive?.content ?: part["imageUrl"]?.jsonPrimitive?.content
                        )
                        else -> null
                    }
                } else if (part is JsonPrimitive) {
                    MessageContentItem.Text(part.content)
                } else null
            }
            else -> emptyList()
        }
    }

    private fun isSilentReplyStream(text: String): Boolean {
        return text.trim().matches(Regex("^\\s*NO_REPLY\\s*$", RegexOption.IGNORE_CASE))
    }

    private fun isAssistantSilentReply(message: MessageUi): Boolean {
        if (message.role != MessageRole.ASSISTANT) return false
        val text = message.getTextContent()
        return isSilentReplyStream(text)
    }

    private fun saveMessageToDb(runId: String, msgObj: JsonObject?, sessionId: String) {
        viewModelScope.launch {
            try {
                val role = msgObj?.get("role")?.jsonPrimitive?.content ?: "assistant"
                val contentJson = msgObj?.get("content")?.let { json.encodeToString(JsonElement.serializer(), it) } ?: "{}"
                messageRepository.saveMessage(
                    sessionId = sessionId,
                    role = MessageRole.fromString(role),
                    content = contentJson,
                    timestamp = System.currentTimeMillis()
                )
            } catch (e: Exception) {
                Log.w(TAG, "Failed to save message: ${e.message}")
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 公共 API
    // ─────────────────────────────────────────────────────────────

    fun setSessionId(sessionId: String) {
        Log.d(TAG, "=== setSessionId: $sessionId")
        _state.update { it.copy(sessionId = sessionId) }
        loadMessageHistory(sessionId)
        
        // 设置 verboseLevel 为 "full" 以接收工具流事件
        viewModelScope.launch {
            try {
                val response = gateway.sessionsPatch(sessionId, verboseLevel = "full")
                if (response.isSuccess()) {
                    Log.d(TAG, "=== setSessionId: verboseLevel set to 'full' - SUCCESS")
                } else {
                    Log.w(TAG, "=== setSessionId: sessionsPatch FAILED: code=${response.error?.code}, message=${response.error?.message}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "=== setSessionId: Exception setting verboseLevel: ${e.message}")
            }
        }
    }

    private fun loadMessageHistory(sessionId: String) {
        Log.d(TAG, "=== loadMessageHistory: sessionId=$sessionId")
        
        // 从本地数据库加载
        viewModelScope.launch {
            messageRepository.observeMessages(sessionId).collect { messages ->
                Log.d(TAG, "=== loadMessageHistory: local DB has ${messages.size} messages")
                _state.update { it.copy(
                    chatMessages = messages,
                    // 重置工具流状态
                    toolStreamById = emptyMap(),
                    toolStreamOrder = emptyList(),
                    chatToolMessages = emptyList(),
                    chatStreamSegments = emptyList()
                )}
            }
        }
        
        // 从 Gateway 加载历史消息
        viewModelScope.launch {
            try {
                Log.d(TAG, "=== loadMessageHistory: fetching from Gateway...")
                val response = gateway.chatHistory(sessionId, limit = 100)
                if (response.isSuccess() && response.payload is JsonObject) {
                    val payload = response.payload as JsonObject
                    val messagesArray = payload["messages"]?.jsonArray
                    Log.d(TAG, "=== loadMessageHistory: Gateway returned ${messagesArray?.size ?: 0} messages")
                    
                    messagesArray?.forEach { msgElement ->
                        try {
                            val msgObj = msgElement.jsonObject
                            val role = msgObj["role"]?.jsonPrimitive?.content ?: "assistant"
                            val content = msgObj["content"]?.let { json.encodeToString(JsonElement.serializer(), it) } ?: "{}"
                            val timestamp = msgObj["timestamp"]?.jsonPrimitive?.content?.toLongOrNull() ?: System.currentTimeMillis()
                            
                            // 提取 toolCallId 和 toolName（TOOL 消息可能有）
                            val toolCallId = msgObj["toolCallId"]?.jsonPrimitive?.content 
                                ?: msgObj["tool_call_id"]?.jsonPrimitive?.content
                            val toolName = msgObj["toolName"]?.jsonPrimitive?.content
                                ?: msgObj["tool_name"]?.jsonPrimitive?.content
                            
                            Log.d(TAG, "=== loadMessageHistory: role=$role, toolCallId=$toolCallId, toolName=$toolName, content=${content.take(100)}")
                            
                            messageRepository.saveMessage(
                                sessionId = sessionId,
                                role = MessageRole.fromString(role),
                                content = content,
                                timestamp = timestamp,
                                toolCallId = toolCallId,
                                toolName = toolName
                            )
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to parse history message: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load chat history: ${e.message}")
            }
        }
    }

    fun sendMessage(message: String) {
        val sessionId = _state.value.sessionId ?: return
        val trimmedMessage = message.trim()
        val attachments = _state.value.attachments
        
        // 检查是否有附件或消息
        val hasAttachments = attachments.isNotEmpty()
        val hasMessage = trimmedMessage.isNotEmpty()
        if (!hasMessage && !hasAttachments) return

        // 处理斜杠命令
        val parsedCommand = com.openclaw.clawchat.ui.components.parseSlashCommand(trimmedMessage)
        if (parsedCommand != null) {
            handleSlashCommand(parsedCommand)
            return
        }

        val now = System.currentTimeMillis()
        val runId = UUID.randomUUID().toString()

        // 构建用户消息内容块（1:1 复刻 webchat）
        val contentBlocks = mutableListOf<MessageContentItem>()
        if (hasMessage) {
            contentBlocks.add(MessageContentItem.Text(trimmedMessage))
        }
        // 添加图片预览到消息
        attachments.forEach { att ->
            if (att.dataUrl != null) {
                contentBlocks.add(MessageContentItem.Image(
                    base64 = att.dataUrl,
                    mimeType = att.mimeType
                ))
            }
        }

        // 添加用户消息
        _state.update { currentState ->
            val userMessage = MessageUi(
                id = UUID.randomUUID().toString(),
                content = contentBlocks,
                role = MessageRole.USER,
                timestamp = now
            )
            currentState.copy(
                chatMessages = currentState.chatMessages + userMessage,
                inputText = "",
                attachments = emptyList(),  // 清空附件
                isSending = true,
                isLoading = true,
                chatRunId = runId,
                chatStream = "",
                chatStreamStartedAt = now
            )
        }

        viewModelScope.launch {
            try {
                // 转换附件为 API 格式
                val apiAttachments = if (hasAttachments) {
                    attachments.mapNotNull { att ->
                        att.dataUrl?.let { dataUrl ->
                            com.openclaw.clawchat.ui.components.dataUrlToBase64(dataUrl)?.let { (mimeType, content) ->
                                com.openclaw.clawchat.ui.components.ApiAttachment(
                                    type = "image",
                                    mimeType = mimeType,
                                    content = content
                                )
                            }
                        }
                    }
                } else null

                if (!apiAttachments.isNullOrEmpty()) {
                    gateway.chatSendWithAttachments(sessionId, trimmedMessage, apiAttachments)
                } else {
                    gateway.chatSend(sessionId, trimmedMessage)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send message", e)
                _state.update { it.copy(error = "发送失败：${e.message}", isLoading = false, isSending = false) }
            }
        }
    }

    /**
     * 处理斜杠命令
     */
    private fun handleSlashCommand(parsed: com.openclaw.clawchat.ui.components.ParsedSlashCommand) {
        val sessionId = _state.value.sessionId ?: return
        val command = parsed.command
        
        Log.i(TAG, "Handling slash command: /${command.name} args=${parsed.args}")

        if (command.executeLocal) {
            // 本地执行的命令
            when (command.name) {
                "clear" -> {
                    // 清空聊天历史
                    _state.update { it.copy(
                        chatMessages = emptyList(),
                        chatStream = null,
                        chatToolMessages = emptyList(),
                        toolStreamById = emptyMap(),
                        toolStreamOrder = emptyList(),
                        inputText = ""
                    )}
                    viewModelScope.launch {
                        messageRepository.clearMessages(sessionId)
                    }
                }
                "stop" -> {
                    // 停止当前运行
                    viewModelScope.launch {
                        try {
                            gateway.chatAbort(sessionId, _state.value.chatRunId)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to abort chat", e)
                        }
                    }
                }
                "help" -> {
                    // 显示帮助信息作为助手消息
                    val helpText = buildHelpMessage()
                    _state.update { currentState ->
                        val helpMessage = MessageUi(
                            id = UUID.randomUUID().toString(),
                            content = listOf(MessageContentItem.Text(helpText)),
                            role = MessageRole.ASSISTANT,
                            timestamp = System.currentTimeMillis()
                        )
                        currentState.copy(
                            chatMessages = currentState.chatMessages + helpMessage,
                            inputText = ""
                        )
                    }
                }
                else -> {
                    // 其他本地命令，发送给 gateway
                    sendCommandToGateway(command.name, parsed.args)
                }
            }
        } else {
            // 非 local 命令，发送给 gateway
            sendCommandToGateway(command.name, parsed.args)
        }
    }

    /**
     * 发送命令给 Gateway
     */
    private fun sendCommandToGateway(name: String, args: String) {
        val sessionId = _state.value.sessionId ?: return
        val fullMessage = "/$name${if (args.isNotBlank()) " $args" else ""}"
        
        _state.update { currentState ->
            val userMessage = MessageUi(
                id = UUID.randomUUID().toString(),
                content = listOf(MessageContentItem.Text(fullMessage)),
                role = MessageRole.USER,
                timestamp = System.currentTimeMillis()
            )
            currentState.copy(
                chatMessages = currentState.chatMessages + userMessage,
                inputText = "",
                isSending = true,
                isLoading = true
            )
        }

        viewModelScope.launch {
            try {
                gateway.chatSend(sessionId, fullMessage)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send command", e)
                _state.update { it.copy(error = "命令发送失败：${e.message}", isLoading = false, isSending = false) }
            }
        }
    }

    /**
     * 构建帮助消息
     */
    private fun buildHelpMessage(): String {
        return buildString {
            appendLine("## 可用命令")
            appendLine()
            com.openclaw.clawchat.ui.components.CATEGORY_ORDER.forEach { category ->
                val label = com.openclaw.clawchat.ui.components.CATEGORY_LABELS[category] ?: category.name
                appendLine("### $label")
                com.openclaw.clawchat.ui.components.SLASH_COMMANDS
                    .filter { it.category == category }
                    .forEach { cmd ->
                        val args = cmd.args?.let { " $it" } ?: ""
                        appendLine("- `/${cmd.name}$args` - ${cmd.description}")
                    }
                appendLine()
            }
        }
    }

    /**
     * 添加附件
     */
    fun addAttachment(context: android.content.Context, uri: android.net.Uri) {
        viewModelScope.launch {
            _state.update { it.copy(isUploadingAttachment = true) }
            
            val attachment = com.openclaw.clawchat.ui.components.loadAttachmentFromUri(context, uri)
            
            _state.update { currentState ->
                if (attachment != null) {
                    val newAttachment = AttachmentUi(
                        id = UUID.randomUUID().toString(),
                        uri = uri,
                        mimeType = attachment.mimeType,
                        fileName = attachment.fileName,
                        dataUrl = attachment.dataUrl
                    )
                    currentState.copy(
                        attachments = currentState.attachments + newAttachment,
                        isUploadingAttachment = false
                    )
                } else {
                    currentState.copy(
                        error = "无法加载附件",
                        isUploadingAttachment = false
                    )
                }
            }
        }
    }

    /**
     * 移除附件
     */
    fun removeAttachment(attachmentId: String) {
        _state.update { currentState ->
            currentState.copy(
                attachments = currentState.attachments.filter { it.id != attachmentId }
            )
        }
    }

    /**
     * 清空所有附件
     */
    fun clearAttachments() {
        _state.update { it.copy(attachments = emptyList()) }
    }

    /**
     * 更新斜杠命令补全状态
     */
    fun updateSlashCommandCompletion(text: String) {
        val trimmed = text.trim()
        if (!trimmed.startsWith("/")) {
            _state.update { it.copy(slashCommandCompletion = SlashCommandCompletion()) }
            return
        }

        val prefix = com.openclaw.clawchat.ui.components.getCommandPrefix(trimmed)
        val commands = com.openclaw.clawchat.ui.components.getSlashCommandCompletions(prefix.drop(1))
        
        _state.update { currentState ->
            currentState.copy(
                slashCommandCompletion = SlashCommandCompletion(
                    commands = commands,
                    selectedIndex = 0,
                    visible = commands.isNotEmpty()
                )
            )
        }
    }

    /**
     * 选择斜杠命令补全
     */
    fun selectSlashCommand(index: Int) {
        _state.update { currentState ->
            val completion = currentState.slashCommandCompletion
            if (index in 0 until completion.commands.size) {
                currentState.copy(
                    slashCommandCompletion = completion.copy(selectedIndex = index)
                )
            } else {
                currentState
            }
        }
    }

    /**
     * 应用选中的斜杠命令
     */
    fun applySelectedSlashCommand(): String? {
        val completion = _state.value.slashCommandCompletion
        val selectedCommand = completion.commands.getOrNull(completion.selectedIndex) ?: return null
        
        val newText = "/${selectedCommand.name}${selectedCommand.args?.let { " $it" } ?: ""}"
        _state.update { it.copy(
            inputText = newText,
            slashCommandCompletion = SlashCommandCompletion()
        )}
        return newText
    }

    /**
     * 隐藏斜杠命令补全
     */
    fun hideSlashCommandCompletion() {
        _state.update { it.copy(slashCommandCompletion = SlashCommandCompletion()) }
    }

    fun updateInputText(text: String) {
        _state.update { it.copy(inputText = text) }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}

sealed class SessionEvent {
    data class MessageReceived(val message: MessageUi) : SessionEvent()
    data class Error(val message: String) : SessionEvent()
    data object MessageSent : SessionEvent()
}