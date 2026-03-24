package com.openclaw.clawchat.ui.state

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openclaw.clawchat.data.UserPreferences
import com.openclaw.clawchat.network.WebSocketConnectionState
import com.openclaw.clawchat.network.protocol.ChatAttachmentData
import com.openclaw.clawchat.network.protocol.GatewayConnection
import com.openclaw.clawchat.repository.MessageRepository
import com.openclaw.clawchat.util.AppLog
import com.openclaw.clawchat.util.JsonUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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
import com.openclaw.clawchat.ui.components.SlashCommandDef

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
    
    // 斜杠命令执行器
    private val slashCommandExecutor = SlashCommandExecutor(
        scope = viewModelScope,
        gateway = gateway,
        messageRepository = messageRepository,
        onStateUpdate = { _state.update(it) }
    )
    
    // loadMessageHistory 的 Job（用于取消之前的加载）
    private var loadMessagesJob: Job? = null

    companion object {
        private const val TAG = "SessionViewModel"
        private const val TOOL_STREAM_LIMIT = 50
    }

    init {
        AppLog.d(TAG, "=== SessionViewModel init")
        observeConnectionState()
        observeIncomingMessages()
    }
    
    /**
     * 观察当前会话的消息
     * 当 sessionId 变化时，自动切换订阅
     */
    private var observeMessagesJob: Job? = null
    
    private fun observeSessionMessages(sessionId: String) {
        AppLog.d(TAG, "=== observeSessionMessages: CALLED for $sessionId")
        observeMessagesJob?.cancel()
        observeMessagesJob = viewModelScope.launch(exceptionHandler) {
            AppLog.d(TAG, "=== observeSessionMessages: STARTED collecting for $sessionId")
            messageRepository.observeMessages(sessionId).collect { messages ->
                AppLog.d(TAG, "=== observeSessionMessages: COLLECTED ${messages.size} messages for $sessionId")
                AppLog.d(TAG, "=== observeSessionMessages: message IDs: ${messages.map { it.id }}")
                _state.update { it.copy(chatMessages = messages) }
                AppLog.d(TAG, "=== observeSessionMessages: state updated, _state.value.chatMessages.size = ${_state.value.chatMessages.size}")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // 取消所有 Job
        loadMessagesJob?.cancel()
        observeMessagesJob?.cancel()
        loadMessagesJob = null
        observeMessagesJob = null
        _events.close()
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
                
                // 当连接恢复时，重新加载当前会话的消息
                if (connectionState is WebSocketConnectionState.Connected) {
                    val currentSessionId = _state.value.sessionId
                    if (currentSessionId != null && _state.value.chatMessages.isEmpty()) {
                        AppLog.d(TAG, "=== observeConnectionState: Connection restored, reloading messages for $currentSessionId")
                        loadMessageHistory(currentSessionId)
                    }
                }
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
        val sessionId = _state.value.sessionId
        AppLog.d(TAG, "=== handleIncomingFrame: sessionId=$sessionId, rawJson=${rawJson.take(100)}")
        
        if (sessionId == null) {
            AppLog.w(TAG, "=== handleIncomingFrame: sessionId is null, returning")
            return
        }

        try {
            val obj = JsonUtils.json.parseToJsonElement(rawJson).jsonObject
            val type = obj["type"]?.jsonPrimitive?.content
            val event = obj["event"]?.jsonPrimitive?.content
            AppLog.d(TAG, "=== handleIncomingFrame: type=$type, event=$event")

            if (type != "event") return

            val payload = obj["payload"]?.jsonObject ?: return
            val eventSessionKey = payload["sessionKey"]?.jsonPrimitive?.content
            
            // agent 事件：检查 stream 字段
            if (event == "agent") {
                val stream = payload["stream"]?.jsonPrimitive?.content
                AppLog.d(TAG, "=== handleIncomingFrame: agent event, stream=$stream")
                if (stream != null) {
                    if (eventSessionKey != null && eventSessionKey != sessionId) {
                        return
                    }
                    handleAgentEvent(payload, stream)
                }
                return
            }

            // chat 事件
            if (event != "chat") return
            
            val sessionKey = eventSessionKey ?: return
            AppLog.d(TAG, "=== handleIncomingFrame: eventSessionKey=$sessionKey, payload keys=${payload.keys}")

            if (eventSessionKey != sessionId) return

            // 处理 chat 事件
            handleChatEvent(payload, sessionId)
        } catch (e: Exception) {
            AppLog.w(TAG, "Failed to parse chat event: ${e.message}")
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
        
        AppLog.d(TAG, "=== Tool stream event: toolCallId=$toolCallId, name=$name, phase=$phase")
        
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
                    AppLog.d(TAG, "=== Committed stream text to segment")
                }
            }
            
            // 解析 args 和 output
            val args = if (phase == "start") data["args"]?.jsonObject else null
            val output = when (phase) {
                "update" -> MessageHandler.formatToolOutput(data["partialResult"])
                "result" -> MessageHandler.formatToolOutput(data["result"])
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
     * 处理 chat 事件（1:1 复刻 webchat handleChatEvent）
     */
    private fun handleChatEvent(payload: JsonObject, sessionId: String) {
        val runId = payload["runId"]?.jsonPrimitive?.content ?: return
        val state = payload["state"]?.jsonPrimitive?.content ?: return
        val msgObj = payload["message"]?.jsonObject
        
        AppLog.d(TAG, "=== handleChatEvent: runId=$runId, state=$state, msgObj=$msgObj")
        
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
        val text = MessageHandler.extractText(msgObj?.get("content"))
        AppLog.d(TAG, "=== handleDelta: runId=$runId, text=${text?.take(50)}, textLen=${text?.length}")
        
        if (!text.isNullOrBlank() && !MessageHandler.isSilentReplyStream(text)) {
            _state.update { currentState ->
                val current = currentState.chatStream ?: ""
                // webchat: if (!current || next.length >= current.length)
                val newStream = if (current.isBlank() || text.length >= current.length) text else current
                AppLog.d(TAG, "=== handleDelta: updating chatStream, newLen=${newStream.length}")
                
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
        val finalMessage = MessageHandler.normalizeFinalAssistantMessage(msgObj)
        AppLog.d(TAG, "=== handleFinal: runId=$runId, finalMessage=${finalMessage != null}, chatStream=${_state.value.chatStream?.take(30)}")
        
        _state.update { currentState ->
            val newMessages = if (finalMessage != null && !MessageHandler.isAssistantSilentReply(finalMessage)) {
                AppLog.d(TAG, "=== handleFinal: adding finalMessage to chatMessages")
                currentState.chatMessages + finalMessage
            } else if (!currentState.chatStream.isNullOrBlank() && !MessageHandler.isSilentReplyStream(currentState.chatStream)) {
                AppLog.d(TAG, "=== handleFinal: adding chatStream to chatMessages")
                currentState.chatMessages + MessageUi(
                    id = runId,
                    content = listOf(MessageContentItem.Text(currentState.chatStream.trim())),
                    role = MessageRole.ASSISTANT,
                    timestamp = System.currentTimeMillis()
                )
            } else {
                AppLog.d(TAG, "=== handleFinal: no message to add")
                currentState.chatMessages
            }
            
            AppLog.d(TAG, "=== handleFinal: chatMessages.size=${newMessages.size}")
            
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
    // 数据库存储
    // ─────────────────────────────────────────────────────────────

    private fun saveMessageToDb(runId: String, msgObj: JsonObject?, sessionId: String) {
        viewModelScope.launch {
            try {
                val role = msgObj?.get("role")?.jsonPrimitive?.content ?: "assistant"
                val contentJson = msgObj?.get("content")?.let { JsonUtils.json.encodeToString(JsonElement.serializer(), it) } ?: "{}"
                messageRepository.saveMessage(
                    sessionId = sessionId,
                    role = MessageRole.fromString(role),
                    content = contentJson,
                    timestamp = System.currentTimeMillis()
                )
            } catch (e: Exception) {
                AppLog.w(TAG, "Failed to save message: ${e.message}")
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 公共 API
    // ─────────────────────────────────────────────────────────────

    fun setSessionId(sessionId: String) {
        AppLog.d(TAG, "=== setSessionId: $sessionId")
        AppLog.d(TAG, "=== setSessionId: sessionId='$sessionId', length=${sessionId.length}")
        
        // 取消之前的加载任务
        loadMessagesJob?.cancel()
        
        _state.update { it.copy(sessionId = sessionId) }
        
        // 开始观察这个会话的消息
        observeSessionMessages(sessionId)
        
        // 稍微延迟加载历史，确保 observeSessionMessages 先建立订阅
        viewModelScope.launch {
            delay(100) // 等待 observeSessionMessages 建立订阅
            loadMessageHistory(sessionId)
        }
        
        // 设置 verboseLevel 为 "full" 以接收工具流事件
        viewModelScope.launch {
            try {
                val response = gateway.sessionsPatch(sessionId, verboseLevel = "full")
                if (response.isSuccess()) {
                    AppLog.d(TAG, "=== setSessionId: verboseLevel set to 'full' - SUCCESS")
                } else {
                    AppLog.w(TAG, "=== setSessionId: sessionsPatch FAILED: code=${response.error?.code}, message=${response.error?.message}")
                }
            } catch (e: Exception) {
                AppLog.w(TAG, "=== setSessionId: Exception setting verboseLevel: ${e.message}")
            }
        }
    }

    private fun loadMessageHistory(sessionId: String) {
        AppLog.d(TAG, "=== loadMessageHistory: sessionId=$sessionId")
        
        // 取消之前的加载任务
        loadMessagesJob?.cancel()
        
        // 设置加载状态
        _state.update { it.copy(isLoading = true) }
        
        loadMessagesJob = viewModelScope.launch {
            // 从 Gateway 加载历史消息
            try {
                // 检查连接状态
                if (gateway.connectionState.value !is WebSocketConnectionState.Connected) {
                    AppLog.w(TAG, "=== loadMessageHistory: Gateway not connected, skipping")
                    _state.update { it.copy(isLoading = false) }
                    return@launch
                }
                
                AppLog.d(TAG, "=== loadMessageHistory: fetching from Gateway...")
                AppLog.d(TAG, "=== loadMessageHistory: sessionId='$sessionId', length=${sessionId.length}")
                AppLog.d(TAG, "=== loadMessageHistory: connectionState=${gateway.connectionState.value}")
                val response = gateway.chatHistory(sessionId, limit = 100)
                AppLog.d(TAG, "=== loadMessageHistory: response ok=${response.isSuccess()}, error=${response.error}")
                
                if (!response.isSuccess()) {
                    AppLog.w(TAG, "=== loadMessageHistory: Gateway request failed: ${response.error?.message}")
                    _state.update { it.copy(isLoading = false) }
                    return@launch
                }
                
                if (response.payload !is JsonObject) {
                    AppLog.w(TAG, "=== loadMessageHistory: Invalid response payload type: ${response.payload?.javaClass?.simpleName}")
                    _state.update { it.copy(isLoading = false) }
                    return@launch
                }
                
                val payload = response.payload as JsonObject
                val messagesArray = payload["messages"]?.jsonArray
                AppLog.d(TAG, "=== loadMessageHistory: Gateway returned ${messagesArray?.size ?: 0} messages")
                
                messagesArray?.forEach { msgElement ->
                    try {
                        val msgObj = msgElement.jsonObject
                        val role = msgObj["role"]?.jsonPrimitive?.content ?: "assistant"
                        val content = msgObj["content"]?.let { JsonUtils.json.encodeToString(JsonElement.serializer(), it) } ?: "{}"
                        val timestamp = msgObj["timestamp"]?.jsonPrimitive?.content?.toLongOrNull() ?: System.currentTimeMillis()
                        
                        // 提取 toolCallId 和 toolName（TOOL 消息可能有）
                        val toolCallId = msgObj["toolCallId"]?.jsonPrimitive?.content 
                            ?: msgObj["tool_call_id"]?.jsonPrimitive?.content
                        val toolName = msgObj["toolName"]?.jsonPrimitive?.content
                            ?: msgObj["tool_name"]?.jsonPrimitive?.content
                        
                        AppLog.d(TAG, "=== loadMessageHistory: role=$role, toolCallId=$toolCallId, toolName=$toolName, content=${content.take(100)}")
                        
                        messageRepository.saveMessage(
                            sessionId = sessionId,
                            role = MessageRole.fromString(role),
                            content = content,
                            timestamp = timestamp,
                            toolCallId = toolCallId,
                            toolName = toolName
                        )
                    } catch (e: Exception) {
                        AppLog.w(TAG, "Failed to parse history message: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                AppLog.w(TAG, "Failed to load chat history: ${e.message}")
            }
            // Gateway 加载完成，取消加载状态
            // 直接从 repository 获取消息并更新状态
            val loadedMessages = messageRepository.observeMessages(sessionId).first()
            AppLog.d(TAG, "=== loadMessageHistory: loaded ${loadedMessages.size} messages from repository")
            _state.update { it.copy(
                isLoading = false,
                chatMessages = loadedMessages
            )}
        }  // loadMessagesJob
    }

    fun sendMessage(message: String) {
        val sessionId = _state.value.sessionId ?: return
        val trimmedMessage = message.trim()
        val attachments = _state.value.attachments
        
        if (trimmedMessage.isEmpty() && attachments.isEmpty()) return

        val now = System.currentTimeMillis()
        val runId = UUID.randomUUID().toString()

        // 构建用户消息内容
        val contentBlocks = mutableListOf<MessageContentItem>()
        if (trimmedMessage.isNotEmpty()) {
            contentBlocks.add(MessageContentItem.Text(trimmedMessage))
        }
        // 添加图片预览
        attachments.forEach { att ->
            val dataUrl = att.dataUrl ?: return@forEach
            val base64Content = extractBase64FromDataUrl(dataUrl)
            contentBlocks.add(MessageContentItem.Image(
                base64 = base64Content,
                mimeType = att.mimeType
            ))
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

        // 转换附件为 API 格式
        val apiAttachments = attachments.mapNotNull { att ->
            val dataUrl = att.dataUrl ?: return@mapNotNull null
            val base64Content = extractBase64FromDataUrl(dataUrl)
            ChatAttachmentData(
                mimeType = att.mimeType,
                content = base64Content
            )
        }.takeIf { it.isNotEmpty() }

        viewModelScope.launch {
            try {
                gateway.chatSend(sessionId, trimmedMessage, apiAttachments)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send message", e)
                _state.update { it.copy(error = "发送失败：${e.message}", isLoading = false, isSending = false) }
            }
        }
    }
    
    /**
     * 从 data URL 提取 base64 内容
     */
    private fun extractBase64FromDataUrl(dataUrl: String): String {
        val match = Regex("^data:([^;]+);base64,(.+)$").find(dataUrl)
        return match?.groupValues?.get(2) ?: dataUrl
    }

    fun updateInputText(text: String) {
        _state.update { it.copy(inputText = text) }
    }
    
    /**
     * 添加附件
     */
    fun addAttachment(attachment: AttachmentUi) {
        _state.update { currentState ->
            currentState.copy(
                attachments = currentState.attachments + attachment
            )
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
     * 执行斜杠命令
     */
    fun executeSlashCommand(command: SlashCommandDef, args: String) {
        slashCommandExecutor.execute(command, args, _state.value.sessionId)
    }

    /**
     * 删除消息
     */
    fun deleteMessage(messageId: String) {
        _state.update { state ->
            state.copy(
                chatMessages = state.chatMessages.filter { it.id != messageId }
            )
        }
        // TODO: 同步到 Gateway/Room
    }
    
    /**
     * 重新生成最后一条助手消息
     */
    fun regenerateLastMessage() {
        viewModelScope.launch {
            val sessionId = _state.value.sessionId ?: return@launch
            val messages = _state.value.chatMessages
            
            // 找到最后一条用户消息
            val lastUserMessage = messages.lastOrNull { it.role == MessageRole.USER }
            if (lastUserMessage == null) {
                AppLog.w(TAG, "No user message to regenerate from")
                return@launch
            }
            
            // 移除最后一条助手消息及其后的所有消息
            val lastAssistantIndex = messages.indexOfLast { it.role == MessageRole.ASSISTANT }
            val updatedMessages = if (lastAssistantIndex >= 0) {
                messages.subList(0, lastAssistantIndex)
            } else {
                messages
            }
            
            _state.update { it.copy(
                chatMessages = updatedMessages,
                isLoading = true,
                isSending = true,
                chatStream = null,
                chatStreamSegments = emptyList(),
                chatToolMessages = emptyList(),
                toolStreamById = emptyMap(),
                toolStreamOrder = emptyList()
            )}
            
            // 重新发送最后一条用户消息
            try {
                gateway.chatSend(sessionId, lastUserMessage.getTextContent())
            } catch (e: Exception) {
                Log.e(TAG, "Failed to regenerate message", e)
                _state.update { it.copy(error = "重新生成失败：${e.message}", isLoading = false, isSending = false) }
            }
        }
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