package com.openclaw.clawchat.ui.state

import com.openclaw.clawchat.util.AppLog
import com.openclaw.clawchat.util.ConnectionStatusMapper.toStatus
import com.openclaw.clawchat.util.StringResourceProvider
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openclaw.clawchat.data.UserPreferences
import com.openclaw.clawchat.network.WebSocketConnectionState
import com.openclaw.clawchat.ui.components.ApiAttachment
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
    private val userPreferences: UserPreferences,
    private val strings: StringResourceProvider
) : ViewModel() {

    private val _state = MutableStateFlow(SessionUiState())
    val state: StateFlow<SessionUiState> = _state.asStateFlow()

    // 暴露字体大小设置
    val messageFontSize = userPreferences.messageFontSize

    private val _events = Channel<SessionEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        AppLog.e(TAG, "Uncaught coroutine exception", throwable)
        _state.update { it.copy(error = throwable.message ?: strings.getString(com.openclaw.clawchat.R.string.error_unknown), isLoading = false, isSending = false) }
    }

    // 斜杠命令执行器
    private val slashCommandExecutor = SlashCommandExecutor(
        scope = viewModelScope,
        gateway = gateway,
        messageRepository = messageRepository,
        strings = strings,
        onStateUpdate = { _state.update(it) },
        onUndo = { undoLastConversation() }
    )
    
    // 工具流管理器（80ms节流批量更新）
    private val toolStreamManager = ToolStreamManager(
        state = _state,
        scope = viewModelScope
    )
    
    // 消息加载器
    private val messageLoader = SessionMessageLoader(
        scope = viewModelScope,
        gateway = gateway,
        messageRepository = messageRepository,
        state = _state,
        exceptionHandler = exceptionHandler,
        onLoadingStateChanged = { loading -> isLoadingHistory = loading }
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
        private const val KEY_SESSION_ID = "session_id"
        private const val KEY_SESSION_LABEL = "session_label"
        private const val KEY_SESSION_MODEL = "session_model"
        private const val KEY_SESSION_AGENT_ID = "session_agent_id"
        private const val KEY_SESSION_AGENT_NAME = "session_agent_name"
        private const val KEY_SESSION_AGENT_EMOJI = "session_agent_emoji"
    }

    init {
        AppLog.d(TAG, "=== SessionViewModel init")
        // 从 SavedStateHandle 恢复 sessionId（处理进程死亡后的恢复）
        restoreFromSavedState()
        observeConnectionState()
        observeIncomingMessages()
    }

    /**
     * 从 SavedStateHandle 恢复会话状态
     * 处理进程死亡后 Android 重建 ViewModel 的情况
     */
    private fun restoreFromSavedState() {
        val savedSessionId = savedStateHandle.get<String>(KEY_SESSION_ID)
        if (!savedSessionId.isNullOrBlank()) {
            AppLog.d(TAG, "=== restoreFromSavedState: restoring sessionId=$savedSessionId")

            // 恢复 sessionId 到状态
            _state.update { it.copy(sessionId = savedSessionId) }

            // 恢复 session 基本信息（从 savedStateHandle）
            val savedLabel = savedStateHandle.get<String>(KEY_SESSION_LABEL)
            val savedModel = savedStateHandle.get<String>(KEY_SESSION_MODEL)
            val savedAgentId = savedStateHandle.get<String>(KEY_SESSION_AGENT_ID)
            val savedAgentName = savedStateHandle.get<String>(KEY_SESSION_AGENT_NAME)
            val savedAgentEmoji = savedStateHandle.get<String>(KEY_SESSION_AGENT_EMOJI)

            if (savedLabel != null || savedModel != null || savedAgentId != null) {
                val savedSession = SessionUi(
                    id = savedSessionId,
                    label = savedLabel,
                    model = savedModel,
                    agentId = savedAgentId,
                    agentName = savedAgentName,
                    agentEmoji = savedAgentEmoji,
                    status = SessionStatus.RUNNING,
                    lastActivityAt = System.currentTimeMillis()
                )
                _state.update { it.copy(session = savedSession) }
            }

            // 观察消息（从 Room 数据库，即使 Gateway 未连接也能显示已持久化的消息）
            observeSessionMessages(savedSessionId)

            // 尝试从 Gateway 加载最新历史（如果已连接）
            // 如果未连接，Gateway 连接恢复后会自动刷新（见 observeConnectionState）
            if (gateway.connectionState.value is WebSocketConnectionState.Connected) {
                isLoadingHistory = true
                hasRestoredFromSavedState = true
                messageLoader.loadMessageHistory(savedSessionId)
            } else {
                AppLog.d(TAG, "=== restoreFromSavedState: Gateway not connected, showing cached messages")
                hasRestoredFromSavedState = true
            }
        }
    }
    
    // ── 消息观察 ──

    private var observeMessagesJob: Job? = null
    private var isLoadingHistory = false  // 加载历史时暂停 Flow 更新

    private fun observeSessionMessages(sessionId: String) {
        AppLog.d(TAG, "=== observeSessionMessages: CALLED for $sessionId")
        observeMessagesJob?.cancel()
        observeMessagesJob = viewModelScope.launch(exceptionHandler) {
            AppLog.d(TAG, "=== observeSessionMessages: STARTED collecting for $sessionId")
            messageRepository.observeMessages(sessionId).collect { messages ->
                // 加载历史时不更新，避免 UI 闪烁
                if (isLoadingHistory) {
                    AppLog.d(TAG, "=== observeSessionMessages: SKIPPED during history loading")
                    return@collect
                }
                AppLog.d(TAG, "=== observeSessionMessages: COLLECTED ${messages.size} messages for $sessionId")
                _state.update { it.copy(chatMessages = messages) }
            }
        }
    }

    fun setLoadingHistory(loading: Boolean) {
        isLoadingHistory = loading
    }

    override fun onCleared() {
        super.onCleared()
        observeMessagesJob?.cancel()
        messageLoader.cancel()
    }

    // ── 连接状态观察 ──

    // 记录上次连接状态，用于判断是否是从断开恢复到已连接
    private var wasDisconnected = false
    // 标记是否已经从 restoreFromSavedState 加载过消息
    private var hasRestoredFromSavedState = false

    private fun observeConnectionState() {
        viewModelScope.launch(exceptionHandler) {
            gateway.connectionState.collect { connectionState ->
                _state.update { it.copy(connectionStatus = connectionState.toStatus()) }

                // 当连接恢复时，重新加载当前会话的消息
                if (connectionState is WebSocketConnectionState.Connected) {
                    val sessionId = _state.value.sessionId
                    if (sessionId != null) {
                        // 只在以下情况刷新消息：
                        // 1. 之前是断开的（网络恢复）
                        // 2. 消息为空且还没有从 restoreFromSavedState 加载过
                        // 注意：restoreFromSavedState 已经处理了进程死亡后的恢复
                        if (wasDisconnected) {
                            AppLog.d(TAG, "=== Gateway reconnected after disconnect, refreshing messages for $sessionId")
                            isLoadingHistory = true
                            messageLoader.loadMessageHistory(sessionId)
                        } else if (_state.value.chatMessages.isEmpty() && !hasRestoredFromSavedState) {
                            AppLog.d(TAG, "=== Gateway connected with empty messages, loading history for $sessionId")
                            isLoadingHistory = true
                            messageLoader.loadMessageHistory(sessionId)
                        }
                    }
                    wasDisconnected = false
                } else if (connectionState is WebSocketConnectionState.Disconnected ||
                           connectionState is WebSocketConnectionState.Error) {
                    wasDisconnected = true
                }
            }
        }
    }

    private fun observeIncomingMessages() {
        viewModelScope.launch(exceptionHandler) {
            AppLog.d(TAG, "=== observeIncomingMessages: starting collect")
            gateway.events.collect { event ->
                AppLog.d(TAG, "=== observeIncomingMessages: received event=${event.name}")
                chatEventHandler.handleEvent(event)
            }
        }
    }

    // ── 公共 API ──

    /**
     * 设置当前会话数据（包含 token 信息）
     * 从 MainViewModel 获取 session 数据后调用
     */
    fun setSessionId(sessionId: String) {
        val currentSessionId = _state.value.sessionId

        // 保存到 SavedStateHandle（处理进程死亡）
        savedStateHandle[KEY_SESSION_ID] = sessionId

        if (currentSessionId == sessionId) {
            // sessionId 未变化，检查消息是否为空
            if (_state.value.chatMessages.isEmpty()) {
                // 消息为空，可能需要重新加载
                isLoadingHistory = true
                messageLoader.loadMessageHistory(sessionId)
            }
            return
        }

        AppLog.d(TAG, "=== setSessionId: $currentSessionId -> $sessionId")

        // 取消之前的加载任务
        messageLoader.cancel()

        // 先设置加载状态，阻止 Flow 更新
        isLoadingHistory = true

        // 切换会话：清除旧状态，设置新 sessionId
        _state.update {
            it.clearSession().copy(sessionId = sessionId)
        }

        // 清除工具流
        toolStreamManager.clear()

        // 观察新会话的消息（此时 isLoadingHistory=true，会跳过初始更新）
        observeSessionMessages(sessionId)

        // 加载历史消息（完成后会设置 isLoadingHistory=false）
        messageLoader.loadMessageHistory(sessionId)
    }

    /**
     * 设置当前会话数据（包含 token 信息）
     * 从 MainViewModel 获取 session 数据后调用
     * 同时保存到 SavedStateHandle 以便进程死亡后恢复
     */
    fun setSession(session: SessionUi) {
        // 保存 session 基本信息 到 SavedStateHandle（只保存非空值）
        savedStateHandle[KEY_SESSION_ID] = session.id
        session.label?.let { savedStateHandle[KEY_SESSION_LABEL] = it }
        session.model?.let { savedStateHandle[KEY_SESSION_MODEL] = it }
        session.agentId?.let { savedStateHandle[KEY_SESSION_AGENT_ID] = it }
        session.agentName?.let { savedStateHandle[KEY_SESSION_AGENT_NAME] = it }
        session.agentEmoji?.let { savedStateHandle[KEY_SESSION_AGENT_EMOJI] = it }

        _state.update { it.copy(
            session = session,
            totalTokens = session.totalTokens,
            contextTokensLimit = session.contextTokens,
            totalTokensFresh = session.totalTokensFresh
        ) }
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
            ApiAttachment(
                type = "base64",
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
            // 先删除原消息（数据库）
            messageRepository.deleteMessage(sessionId, messageId)

            // 原子性更新：删除消息 + 设置输入文本
            _state.update { state ->
                state.copy(
                    editingMessageId = null,
                    editingMessageText = null,
                    chatMessages = state.chatMessages.filter { it.id != messageId },
                    inputText = trimmedText
                )
            }

            // 发送编辑后的消息
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

            // 保存最后一条助手消息的 ID 用于成功后删除
            val lastAssistantId = messages.lastOrNull { it.role == MessageRole.ASSISTANT }?.id

            // 重新发送最后一条用户消息
            val userText = lastUserMessage.content.filterIsInstance<MessageContentItem.Text>()
                .firstOrNull()?.text ?: return@launch

            _state.update { it.copy(isSending = true, isLoading = true) }

            try {
                gateway.chatSend(sessionId, userText)
                // 成功后再删除旧的助手消息，避免发送失败后 DB 数据丢失
                if (!lastAssistantId.isNullOrBlank()) {
                    messageRepository.deleteMessage(sessionId, lastAssistantId)
                }
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

        // 找到最后一条用户消息之后的所有非用户消息（助手 + 工具消息）
        val assistantIndices = messages.indices
            .filter { it > lastUserIndex && messages[it].role != MessageRole.USER }
            .toList()

        // 检查 agent 是否正在运行（最后一条助手消息是 SENDING 状态）
        val isAgentRunning = assistantIndices.lastOrNull()?.let { idx ->
            messages[idx].status == MessageStatus.SENDING
        } ?: false

        viewModelScope.launch {
            // 如果 agent 正在运行，先调用 Gateway API 中止
            if (isAgentRunning) {
                try {
                    gateway.chatAbort(sessionId)
                    AppLog.d(TAG, "chat.abort called before undo")
                } catch (e: Exception) {
                    AppLog.w(TAG, "chat.abort failed: ${e.message}")
                }
            }

            // 收集所有要删除的消息 ID
            val messageIdsToDelete = mutableListOf<String>()
            messageIdsToDelete.add(messages[lastUserIndex].id)
            for (idx in assistantIndices) {
                messageIdsToDelete.add(messages[idx].id)
            }

            // 先从数据库删除（使用 gateway API 如果可用）
            for (id in messageIdsToDelete) {
                messageRepository.deleteMessage(sessionId, id)
            }

            // 从状态中移除（从后往前删除，避免索引偏移）
            _state.update { state ->
                val newMessages = state.chatMessages.toMutableList()
                // 先删除索引大的，再删索引小的，避免索引变化
                (assistantIndices + lastUserIndex).sortedDescending().forEach { idx ->
                    if (idx < newMessages.size) {
                        newMessages.removeAt(idx)
                    }
                }
                state.copy(chatMessages = newMessages, isSending = false)
            }

            AppLog.d(TAG, "Undid last conversation: deleted ${messageIdsToDelete.size} messages")
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    /**
     * 重试连接
     * 尝试触发网关重连
     */
    fun retryConnection() {
        val currentStatus = _state.value.connectionStatus
        // 仅在断开或错误状态时尝试重连
        if (currentStatus is ConnectionStatus.Disconnected ||
            currentStatus is ConnectionStatus.Error) {
            // 连接由 MainViewModel 管理，这里只是清理错误状态
            // 实际重连由 gateway 的自动重连机制处理
            _state.update { it.copy(error = null) }
        }
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
     * v2026.4.8: 使用 CompactionStatus 替代 compactionActive + compactionCompletedAt
     */
    fun updateCompactionStatus(status: CompactionStatus?) {
        _state.update { it.copy(compactionStatus = status) }
    }

    /**
     * 兼容旧方法：设置 compaction 状态
     */
    fun updateCompactionState(active: Boolean, completedAt: Long? = null) {
        val status = if (active) {
            CompactionStatus(phase = "active", completedAt = null)
        } else if (completedAt != null) {
            CompactionStatus(phase = "complete", completedAt = completedAt)
        } else {
            null
        }
        updateCompactionStatus(status)
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