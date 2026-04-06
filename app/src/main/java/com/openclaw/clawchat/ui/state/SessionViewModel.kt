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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
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
        onStateUpdate = { _state.update(it) },
        onUndo = { undoLastConversation() }
    )
    
    // 工具流管理器（不刷新消息，提高 UI 平滑性）
    private val toolStreamManager = ToolStreamManager(state = _state)
    
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
        onToolStreamEvent = { payload -> toolStreamManager.handleToolStreamEvent(payload) },
        onChatComplete = { flushChatQueue() },
        onLifecycleEnd = { onLifecycleEnd() }
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
            AppLog.d(TAG, "=== observeIncomingMessages: starting collect")
            gateway.incomingMessages.collect { rawJson ->
                AppLog.d(TAG, "=== observeIncomingMessages: received rawJson length=${rawJson.length}")
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

    /**
     * lifecycle:end 回调
     * 清除已完成的工具流 + 刷新消息获取完整 toolResult
     */
    private fun onLifecycleEnd() {
        AppLog.d(TAG, "=== onLifecycleEnd: clearing tool messages and refreshing")
        // 清除工具流状态（历史消息中的 toolResult 会显示完整内容）
        toolStreamManager.clear()
        // 刷新消息，获取完整的 toolResult
        messageLoader.refreshMessages()
    }

    // ── 用户操作 ──

    /**
     * 检查是否忙碌（正在发送或等待响应）
     */
    private fun isChatBusy(): Boolean {
        val state = _state.value
        return state.isSending || state.chatRunId != null || !state.chatStream.isNullOrBlank()
    }

    /**
     * 消息入队或发送
     * - 如果不忙，直接发送
     * - 如果忙，加入队列等待
     */
    fun enqueueMessage(message: String) {
        val trimmedMessage = message.trim()
        val attachments = _state.value.attachments
        
        if (trimmedMessage.isEmpty() && attachments.isEmpty()) return
        
        if (isChatBusy()) {
            // 忙碌时入队
            AppLog.d(TAG, "Chat busy, enqueuing message")
            val queueItem = ChatQueueItem(
                id = UUID.randomUUID().toString(),
                text = trimmedMessage,
                timestamp = System.currentTimeMillis(),
                attachments = attachments
            )
            _state.update { it.copy(
                chatQueue = it.chatQueue + queueItem,
                inputText = "",
                attachments = emptyList()
            )}
        } else {
            // 直接发送
            sendMessage(message)
        }
    }
    
    /**
     * 刷新消息队列
     * 在消息发送完成后检查队列，发送下一条
     */
    private fun flushChatQueue() {
        if (isChatBusy()) return
        
        val queue = _state.value.chatQueue
        if (queue.isEmpty()) return
        
        val next = queue.first()
        AppLog.d(TAG, "Flushing queue item: ${next.id}")
        
        // 从队列移除
        _state.update { it.copy(chatQueue = it.chatQueue.drop(1)) }
        
        // 恢复附件并发送
        _state.update { it.copy(attachments = next.attachments) }
        sendMessage(next.text)
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

    /**
     * 编辑消息
     * 编辑后删除原消息，发送新消息
     */
    fun editMessage(messageId: String, newText: String) {
        val sessionId = _state.value.sessionId ?: return
        val trimmedText = newText.trim()

        if (trimmedText.isEmpty()) return

        viewModelScope.launch {
            // 取消编辑状态
            _state.update { it.copy(editingMessageId = null, editingMessageText = null) }

            // 删除原消息
            deleteMessage(messageId)

            // 发送编辑后的消息
            _state.update { it.copy(inputText = trimmedText) }
            sendMessage(trimmedText)
        }
    }

    /**
     * 开始编辑消息
     */
    fun startEditMessage(messageId: String) {
        val message = _state.value.chatMessages.find { it.id == messageId } ?: return
        val text = message.getTextContent()
        _state.update { it.copy(
            editingMessageId = messageId,
            editingMessageText = text
        )}
    }

    /**
     * 取消编辑
     */
    fun cancelEdit() {
        _state.update { it.copy(editingMessageId = null, editingMessageText = null) }
    }

    /**
     * 更新编辑文本
     */
    fun updateEditingText(text: String) {
        _state.update { it.copy(editingMessageText = text) }
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

    /**
     * 撤销上一轮对话
     * 删除最后的用户消息和助手消息（包括工具消息）
     */
    fun undoLastConversation() {
        val sessionId = _state.value.sessionId ?: return
        val messages = _state.value.chatMessages

        // 找到最后一条用户消息
        val lastUserIndex = messages.indexOfLast { it.role == MessageRole.USER }
        if (lastUserIndex < 0) {
            AppLog.w(TAG, "No user message to undo")
            return
        }

        // 找到最后一条用户消息之后的第一条助手消息
        val lastAssistantIndex = messages.indexOfFirst {
            it.role == MessageRole.ASSISTANT && messages.indexOf(it) > lastUserIndex
        }

        viewModelScope.launch {
            // 删除用户消息
            val userMessageId = messages[lastUserIndex].id
            messageRepository.deleteMessage(sessionId, userMessageId)

            // 删除助手消息（如果有）
            if (lastAssistantIndex >= 0) {
                val assistantMessageId = messages[lastAssistantIndex].id
                messageRepository.deleteMessage(sessionId, assistantMessageId)
            }

            // 更新状态
            _state.update { state ->
                val newMessages = messages.toMutableList()
                if (lastAssistantIndex >= 0) {
                    newMessages.removeAt(lastAssistantIndex)
                }
                newMessages.removeAt(lastUserIndex)
                state.copy(chatMessages = newMessages)
            }

            AppLog.d(TAG, "Undid last conversation: userMessage=$userMessageId")
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    fun clearNewMessagesBelow() {
        _state.update { it.copy(chatNewMessagesBelow = false, unreadMessageCount = 0) }
    }

    /**
     * 更新用户滚动状态
     * 参考 webchat: handleChatScroll
     */
    fun updateUserNearBottom(nearBottom: Boolean) {
        _state.update { state ->
            // 用户回到底部时清除新消息提示和未读计数
            if (nearBottom && state.chatNewMessagesBelow) {
                state.copy(chatUserNearBottom = true, chatNewMessagesBelow = false, unreadMessageCount = 0)
            } else {
                state.copy(chatUserNearBottom = nearBottom)
            }
        }
    }

    /**
     * 标记已自动滚动
     * 参考 webchat: chatHasAutoScrolled
     */
    fun markAutoScrolled() {
        _state.update { it.copy(chatHasAutoScrolled = true) }
    }

    /**
     * 设置有新消息在下方（带未读计数）
     * 参考 Stream SDK: 新消息到达时增加未读计数
     */
    fun setNewMessagesBelow() {
        _state.update { state ->
            state.copy(
                chatNewMessagesBelow = true,
                unreadMessageCount = state.unreadMessageCount + 1
            )
        }
    }

    /**
     * 用户滚动离开底部（不增加计数）
     */
    fun setUserScrolledAway() {
        _state.update { state ->
            if (!state.chatNewMessagesBelow) {
                state.copy(chatNewMessagesBelow = true)
            } else {
                state
            }
        }
    }
    
    /**
     * 更新 context token 用量
     * 参考 webchat: renderContextNotice
     */
    fun updateContextTokens(totalTokens: Int?, contextTokensLimit: Int?, fresh: Boolean = true) {
        _state.update { it.copy(
            totalTokens = totalTokens,
            contextTokensLimit = contextTokensLimit,
            totalTokensFresh = fresh
        ) }
    }
    
    /**
     * 更新 compaction 状态
     * 参考 webchat: renderCompactionIndicator
     */
    fun updateCompactionState(active: Boolean, completedAt: Long? = null) {
        _state.update { it.copy(
            compactionActive = active,
            compactionCompletedAt = completedAt
        ) }
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

    // ── Model 切换 ──

    /**
     * 加载可用模型列表
     */
    fun loadModels() {
        if (_state.value.connectionStatus !is ConnectionStatus.Connected) {
            return
        }
        if (_state.value.models.isNotEmpty()) {
            return // 已加载过
        }
        viewModelScope.launch(exceptionHandler) {
            _state.update { it.copy(isLoadingModels = true) }
            try {
                val response = gateway.modelsList()
                if (response.isSuccess()) {
                    val models = response.payload?.jsonObject?.get("models")?.jsonArray?.mapNotNull { element ->
                        try {
                            val obj = element.jsonObject
                            com.openclaw.clawchat.ui.components.ModelItem(
                                id = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                                name = obj["name"]?.jsonPrimitive?.content ?: obj["id"]?.jsonPrimitive?.content ?: "Unknown",
                                provider = obj["provider"]?.jsonPrimitive?.content,
                                supportsVision = obj["supportsVision"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
                            )
                        } catch (e: Exception) {
                            null
                        }
                    } ?: emptyList()

                    _state.update { it.copy(models = models, isLoadingModels = false) }
                    AppLog.i(TAG, "Loaded ${models.size} models")
                } else {
                    _state.update { it.copy(isLoadingModels = false) }
                }
            } catch (e: Exception) {
                AppLog.w(TAG, "Failed to load models: ${e.message}")
                _state.update { it.copy(isLoadingModels = false) }
            }
        }
    }

    /**
     * 切换会话的 Model
     */
    fun changeModel(newModel: String) {
        val sessionId = _state.value.sessionId ?: return

        viewModelScope.launch(exceptionHandler) {
            try {
                val response = gateway.call("sessions.patch", mapOf(
                    "key" to kotlinx.serialization.json.JsonPrimitive(sessionId),
                    "model" to kotlinx.serialization.json.JsonPrimitive(newModel)
                ))

                if (response.isSuccess()) {
                    // 更新本地 session 状态
                    _state.update { state ->
                        state.copy(
                            session = state.session?.copy(model = newModel)
                        )
                    }
                    AppLog.i(TAG, "Model changed to $newModel")
                } else {
                    _state.update { it.copy(error = "切换模型失败：${response.error?.message}") }
                }
            } catch (e: Exception) {
                AppLog.e(TAG, "Failed to change model", e)
                _state.update { it.copy(error = "切换模型失败：${e.message}") }
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