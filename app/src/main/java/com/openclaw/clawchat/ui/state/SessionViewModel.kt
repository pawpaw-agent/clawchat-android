package com.openclaw.clawchat.ui.state

import com.openclaw.clawchat.util.AppLog
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openclaw.clawchat.data.UserPreferences
import com.openclaw.clawchat.network.WebSocketConnectionState
import com.openclaw.clawchat.network.protocol.ChatAttachmentData
import com.openclaw.clawchat.network.protocol.GatewayConnection
import com.openclaw.clawchat.repository.MessageRepository
import com.openclaw.clawchat.util.AppLog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import com.openclaw.clawchat.ui.components.SlashCommandDef

/**
 * 会话界面 ViewModel（重构版）
 * 
 * 职责：
 * - 状态管理
 * - 用户操作
 * - 协调 ChatEventHandler, ToolStreamManager, SessionMessageLoader
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
    
    // 暴露字体大小设置
    val messageFontSize = userPreferences.messageFontSize

    private val _events = Channel<SessionEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        AppLog.e(TAG, "Uncaught coroutine exception", throwable)
        _state.update { it.copy(error = throwable.message ?: "未知错误", isLoading = false, isSending = false) }
    }
    
    // 斜杠命令执行器
    private val slashCommandExecutor = SlashCommandExecutor(
        scope = viewModelScope,
        gateway = gateway,
        messageRepository = messageRepository,
        onStateUpdate = { _state.update(it) }
    )
    
    // 工具流管理器
    private val toolStreamManager = ToolStreamManager(_state)
    
    // 消息加载器
    private val messageLoader = SessionMessageLoader(
        scope = viewModelScope,
        gateway = gateway,
        messageRepository = messageRepository,
        state = _state,
        exceptionHandler = exceptionHandler
    )
    
    // 聊天事件处理器
    private val chatEventHandler = ChatEventHandler(
        scope = viewModelScope,
        gateway = gateway,
        messageRepository = messageRepository,
        state = _state,
        onToolStreamEvent = { payload -> toolStreamManager.handleToolStreamEvent(payload) }
    )

    companion object {
        private const val TAG = "SessionViewModel"
    }

    init {
        AppLog.d(TAG, "=== SessionViewModel init")
        observeConnectionState()
        observeIncomingMessages()
        observeToolStreamEvents()
    }
    
    // ── 消息观察 ──
    
    private var observeMessagesJob: Job? = null
    
    private fun observeSessionMessages(sessionId: String) {
        AppLog.d(TAG, "=== observeSessionMessages: CALLED for $sessionId")
        observeMessagesJob?.cancel()
        observeMessagesJob = viewModelScope.launch(exceptionHandler) {
            AppLog.d(TAG, "=== observeSessionMessages: STARTED collecting for $sessionId")
            messageRepository.observeMessages(sessionId).collect { messages ->
                AppLog.d(TAG, "=== observeSessionMessages: COLLECTED ${messages.size} messages for $sessionId")
                _state.update { it.copy(chatMessages = messages) }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        observeMessagesJob?.cancel()
        messageLoader.cancel()
    }

    // ── 连接状态观察 ──
    
    private fun observeConnectionState() {
        viewModelScope.launch(exceptionHandler) {
            gateway.connectionState.collect { connectionState ->
                _state.update { it.copy(connectionStatus = connectionState.toStatus()) }
                
                // 当连接恢复时，重新加载当前会话的消息
                if (connectionState is WebSocketConnectionState.Connected) {
                    _state.value.sessionId?.let { sessionId ->
                        if (_state.value.chatMessages.isEmpty()) {
                            messageLoader.loadMessageHistory(sessionId)
                        }
                    }
                }
            }
        }
    }

    private fun observeIncomingMessages() {
        viewModelScope.launch(exceptionHandler) {
            gateway.incomingMessages.collect { rawJson ->
                chatEventHandler.handleIncomingFrame(rawJson)
            }
        }
    }

    private fun observeToolStreamEvents() {
        viewModelScope.launch(exceptionHandler) {
            gateway.toolStreamEvents.collect { eventMap ->
                // Gateway 已经处理了工具流事件
                // 这里只需要更新状态
            }
        }
    }

    // ── 公共 API ──

    fun setSessionId(sessionId: String) {
        val currentSessionId = _state.value.sessionId
        
        if (currentSessionId == sessionId) {
            // sessionId 未变化，可能需要强制刷新
            if (_state.value.chatMessages.isEmpty()) {
                messageLoader.loadMessageHistory(sessionId)
            }
            return
        }

        AppLog.d(TAG, "=== setSessionId: $currentSessionId -> $sessionId")
        
        // 切换会话：清除旧状态，设置新 sessionId
        _state.update { 
            it.clearSession().copy(sessionId = sessionId) 
        }
        
        // 清除工具流
        toolStreamManager.clear()
        
        // 观察新会话的消息
        observeSessionMessages(sessionId)
        
        // 加载历史消息
        messageLoader.loadMessageHistory(sessionId)
    }

    fun refreshMessages() {
        messageLoader.refreshMessages()
    }

    // ── 用户操作 ──

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
                attachments = emptyList(),
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
                AppLog.e(TAG, "Failed to send message", e)
                _state.update { it.copy(error = "发送失败：${e.message}，请检查网络连接后重试", isLoading = false, isSending = false) }
            }
        }
    }
    
    private fun extractBase64FromDataUrl(dataUrl: String): String {
        val match = Regex("^data:([^;]+);base64,(.+)$").find(dataUrl)
        return match?.groupValues?.get(2) ?: dataUrl
    }

    fun updateInputText(text: String) {
        _state.update { it.copy(inputText = text) }
    }
    
    /**
     * 继续生成 - 发送空消息触发助手继续响应
     */
    fun continueGeneration() {
        val sessionId = _state.value.sessionId ?: return
        
        viewModelScope.launch {
            _state.update { it.copy(isSending = true, isLoading = true) }
            try {
                // 发送空消息触发继续生成
                gateway.chatSend(sessionId, "")
            } catch (e: Exception) {
                AppLog.e(TAG, "Failed to continue generation", e)
                _state.update { it.copy(error = "继续生成失败：${e.message}", isLoading = false, isSending = false) }
            }
        }
    }
    
    fun addAttachment(attachment: AttachmentUi) {
        _state.update { currentState ->
            currentState.copy(
                attachments = currentState.attachments + attachment
            )
        }
    }
    
    fun removeAttachment(attachmentId: String) {
        _state.update { currentState ->
            currentState.copy(
                attachments = currentState.attachments.filter { it.id != attachmentId }
            )
        }
    }
    
    fun clearAttachments() {
        _state.update { it.copy(attachments = emptyList()) }
    }
    
    fun executeSlashCommand(command: SlashCommandDef, args: String) {
        slashCommandExecutor.execute(command, args, _state.value.sessionId)
    }

    fun deleteMessage(messageId: String) {
        val sessionId = _state.value.sessionId
        _state.update { state ->
            state.copy(
                chatMessages = state.chatMessages.filter { it.id != messageId }
            )
        }
        if (sessionId != null) {
            viewModelScope.launch {
                messageRepository.deleteMessage(sessionId, messageId)
            }
        }
    }
    
    fun regenerateLastMessage() {
        viewModelScope.launch {
            val sessionId = _state.value.sessionId ?: return@launch
            val messages = _state.value.chatMessages
            
            val lastUserMessage = messages.lastOrNull { it.role == MessageRole.USER }
            if (lastUserMessage == null) {
                AppLog.w(TAG, "No user message to regenerate from")
                return@launch
            }
            
            // 移除最后一条助手消息
            val lastAssistantIndex = messages.indexOfLast { it.role == MessageRole.ASSISTANT }
            if (lastAssistantIndex >= 0) {
                val messageId = messages[lastAssistantIndex].id
                messageRepository.deleteMessage(sessionId, messageId)
            }
            
            // 重新发送最后一条用户消息
            val userText = lastUserMessage.content.filterIsInstance<MessageContentItem.Text>()
                .firstOrNull()?.text ?: return@launch
            
            _state.update { it.copy(isSending = true, isLoading = true) }
            
            try {
                gateway.chatSend(sessionId, userText)
            } catch (e: Exception) {
                AppLog.e(TAG, "Failed to regenerate message", e)
                _state.update { it.copy(error = "重发失败：${e.message}，请检查网络连接后重试", isLoading = false, isSending = false) }
            }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    fun retryMessage(messageId: String) {
        viewModelScope.launch {
            val sessionId = _state.value.sessionId ?: return@launch
            
            // 查找失败的消息
            val failedMessage = _state.value.chatMessages.find { it.id == messageId }
                ?: return@launch
            
            // 如果是用户消息，重新发送
            if (failedMessage.role == MessageRole.USER) {
                val userText = failedMessage.content.filterIsInstance<MessageContentItem.Text>()
                    .firstOrNull()?.text ?: return@launch
                
                // 删除失败的消息
                messageRepository.deleteMessage(sessionId, messageId)
                
                // 重新发送
                sendMessage(userText)
            }
        }
    }
}

// ── 事件定义 ──

sealed class SessionEvent {
    data class MessageReceived(val message: MessageUi) : SessionEvent()
    data class Error(val message: String) : SessionEvent()
    data object MessageSent : SessionEvent()
}

// ── 扩展函数 ──

private fun WebSocketConnectionState.toStatus(): ConnectionStatus = when (this) {
    is WebSocketConnectionState.Connected -> ConnectionStatus.Connected()
    is WebSocketConnectionState.Connecting -> ConnectionStatus.Connecting
    is WebSocketConnectionState.Disconnecting -> ConnectionStatus.Disconnecting
    is WebSocketConnectionState.Disconnected -> ConnectionStatus.Disconnected
    is WebSocketConnectionState.Error -> ConnectionStatus.Error(this.throwable.message ?: "Unknown error", this.throwable)
}