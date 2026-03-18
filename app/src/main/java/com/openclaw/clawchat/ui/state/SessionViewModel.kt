package com.openclaw.clawchat.ui.state

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openclaw.clawchat.data.local.MessageEntity
import com.openclaw.clawchat.data.local.MessageRole as LocalMessageRole
import com.openclaw.clawchat.data.local.MessageStatus
import com.openclaw.clawchat.network.WebSocketService
import com.openclaw.clawchat.network.GatewayMessage
import com.openclaw.clawchat.network.WebSocketConnectionState
import com.openclaw.clawchat.repository.MessageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * 会话界面 ViewModel
 * 
 * 负责管理会话中的消息收发：
 * - 发送用户消息到网关
 * - 接收并显示助手回复
 * - 管理消息历史 (本地缓存)
 * - 处理连接状态
 */
@HiltViewModel
class SessionViewModel @Inject constructor(
    private val webSocketService: WebSocketService,
    private val messageRepository: MessageRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _state = MutableStateFlow(SessionUiState())
    val state: StateFlow<SessionUiState> = _state.asStateFlow()

    private val _events = MutableStateFlow<SessionEvent?>(null)
    val events: StateFlow<SessionEvent?> = _events.asStateFlow()

    companion object {
        private const val TAG = "SessionViewModel"
    }

    init {
        observeWebSocketConnection()
        observeIncomingMessages()
        loadMessageHistory()
    }

    /**
     * 观察 WebSocket 连接状态
     */
    private fun observeWebSocketConnection() {
        viewModelScope.launch {
            webSocketService.connectionState.collect { connectionState ->
                val connectionStatus = when (connectionState) {
                    is WebSocketConnectionState.Connected -> ConnectionStatus.Connected()
                    is WebSocketConnectionState.Connecting -> ConnectionStatus.Connecting
                    is WebSocketConnectionState.Disconnecting -> ConnectionStatus.Disconnecting
                    is WebSocketConnectionState.Disconnected -> ConnectionStatus.Disconnected
                    is WebSocketConnectionState.Error -> ConnectionStatus.Error(
                        connectionState.throwable.message ?: "WebSocket 错误",
                        throwable = connectionState.throwable
                    )
                }

                _state.update { it.copy(connectionStatus = connectionStatus) }

                if (connectionState is WebSocketConnectionState.Error) {
                    _events.value = SessionEvent.Error(connectionState.throwable.message ?: "连接错误")
                }
            }
        }
    }

    /**
     * 加载消息历史（从本地缓存）
     */
    private fun loadMessageHistory() {
        viewModelScope.launch {
            val sessionId = savedStateHandle.get<String>("sessionId") ?: return@launch
            
            messageRepository.getMessages(sessionId).collect { entities ->
                val messages = entities.map { it.toMessageUi() }
                _state.update { it.copy(messages = messages) }
            }
        }
    }

    /**
     * 观察接收到的消息
     */
    private fun observeIncomingMessages() {
        viewModelScope.launch {
            webSocketService.incomingMessages.collect { gatewayMessage ->
                handleIncomingMessage(gatewayMessage)
            }
        }
    }

    /**
     * 处理接收到的网关消息
     */
    private fun handleIncomingMessage(message: GatewayMessage) {
        viewModelScope.launch {
            val sessionId = _state.value.sessionId ?: return@launch
            
            when (message) {
                is GatewayMessage.AssistantMessage -> {
                    // 收到助手回复
                    val assistantMessage = MessageUi(
                        id = UUID.randomUUID().toString(),
                        content = message.content,
                        role = MessageRole.ASSISTANT,
                        timestamp = message.timestamp
                    )

                    // 保存到本地缓存
                    messageRepository.saveMessage(
                        sessionId = sessionId,
                        role = LocalMessageRole.ASSISTANT,
                        content = message.content,
                        timestamp = message.timestamp,
                        status = MessageStatus.DELIVERED
                    )

                    _state.update {
                        it.copy(
                            messages = it.messages + assistantMessage,
                            isLoading = false
                        )
                    }

                    _events.value = SessionEvent.MessageReceived(assistantMessage)
                }

                is GatewayMessage.SystemEvent -> {
                    // 系统事件（如会话状态变更）
                    val systemMessage = MessageUi(
                        id = UUID.randomUUID().toString(),
                        content = message.text,
                        role = MessageRole.SYSTEM,
                        timestamp = message.timestamp
                    )

                    _state.update {
                        it.copy(messages = it.messages + systemMessage)
                    }
                }

                is GatewayMessage.Error -> {
                    // 错误事件
                    _state.update {
                        it.copy(
                            error = message.message,
                            isLoading = false
                        )
                    }

                    _events.value = SessionEvent.Error(message.message)
                }

                else -> {
                    Log.w(TAG, "收到未知消息类型：${message.type}")
                }
            }
        }
    }

    /**
     * 发送用户消息
     * @param content 消息内容
     */
    fun sendMessage(content: String) {
        viewModelScope.launch {
            if (content.isBlank()) {
                _events.value = SessionEvent.Error("消息内容不能为空")
                return@launch
            }

            val connectionStatus = _state.value.connectionStatus
            if (connectionStatus !is ConnectionStatus.Connected) {
                _events.value = SessionEvent.Error("未连接到网关")
                return@launch
            }

            val sessionId = _state.value.sessionId ?: return@launch

            // 创建用户消息
            val userMessage = MessageUi(
                id = UUID.randomUUID().toString(),
                content = content,
                role = MessageRole.USER,
                timestamp = System.currentTimeMillis()
            )

            // 保存到本地缓存
            messageRepository.saveMessage(
                sessionId = sessionId,
                role = userMessage.role.toLocalRole(),
                content = content,
                timestamp = userMessage.timestamp,
                status = MessageStatus.PENDING
            )

            // 添加到消息列表
            _state.update {
                it.copy(
                    messages = it.messages + userMessage,
                    inputText = "",
                    isLoading = true,
                    isSending = false
                )
            }

            // 构建网关消息
            val gatewayMessage = GatewayMessage.UserMessage(
                sessionId = sessionId,
                content = content,
                attachments = emptyList(),
                timestamp = System.currentTimeMillis()
            )

            try {
                // 发送到网关
                val result = webSocketService.send(gatewayMessage)

                result.onSuccess {
                    // 更新消息状态为已发送
                    messageRepository.saveMessage(
                        sessionId = sessionId,
                        role = userMessage.role.toLocalRole(),
                        content = content,
                        timestamp = userMessage.timestamp,
                        status = MessageStatus.SENT
                    )
                }.onFailure { error ->
                    Log.e(TAG, "发送消息失败", error)
                    // 更新消息状态为失败
                    messageRepository.saveMessage(
                        sessionId = sessionId,
                        role = userMessage.role.toLocalRole(),
                        content = content,
                        timestamp = userMessage.timestamp,
                        status = MessageStatus.FAILED
                    )
                    _state.update {
                        it.copy(
                            error = "发送失败：${error.message}",
                            isLoading = false
                        )
                    }
                    _events.value = SessionEvent.Error("发送失败：${error.message}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "发送消息异常", e)
                // 更新消息状态为失败
                messageRepository.saveMessage(
                    sessionId = sessionId,
                    role = userMessage.role.toLocalRole(),
                    content = content,
                    timestamp = userMessage.timestamp,
                    status = MessageStatus.FAILED
                )
                _state.update {
                    it.copy(
                        error = "发送异常：${e.message}",
                        isLoading = false
                    )
                }
                _events.value = SessionEvent.Error("发送异常：${e.message}")
            }
        }
    }

    /**
     * 更新输入框文本
     */
    fun updateInputText(text: String) {
        _state.update { it.copy(inputText = text) }
    }

    /**
     * 清除错误
     */
    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    /**
     * 清除事件
     */
    fun consumeEvent() {
        _events.value = null
    }

    /**
     * 设置当前会话 ID
     */
    fun setSessionId(sessionId: String) {
        _state.update {
            it.copy(
                sessionId = sessionId,
                messages = emptyList() // 切换会话时清空消息列表
            )
        }
    }

    /**
     * 清除当前会话
     */
    fun clearSession() {
        _state.update {
            it.copy(
                sessionId = null,
                session = null,
                messages = emptyList(),
                inputText = "",
                isLoading = false,
                error = null
            )
        }
    }

    /**
     * 模拟接收消息（用于测试）
     */
    fun simulateIncomingMessage(content: String, delayMs: Long = 1000) {
        viewModelScope.launch {
            kotlinx.coroutines.delay(delayMs)

            val assistantMessage = MessageUi(
                id = UUID.randomUUID().toString(),
                content = content,
                role = MessageRole.ASSISTANT,
                timestamp = System.currentTimeMillis()
            )

            _state.update {
                it.copy(
                    messages = it.messages + assistantMessage,
                    isLoading = false
                )
            }
        }
    }
}

/**
 * 会话事件类型
 */
sealed class SessionEvent {
    data class MessageReceived(val message: MessageUi) : SessionEvent()
    data class Error(val message: String) : SessionEvent()
    data object SessionEnded : SessionEvent()
}

/**
 * 消息角色（与 ui.components.MessageRole 保持一致）
 */
enum class MessageRole {
    USER,
    ASSISTANT,
    SYSTEM
}

/**
 * MessageRole 转 LocalMessageRole
 */
private fun MessageRole.toLocalRole(): LocalMessageRole {
    return when (this) {
        MessageRole.USER -> LocalMessageRole.USER
        MessageRole.ASSISTANT -> LocalMessageRole.ASSISTANT
        MessageRole.SYSTEM -> LocalMessageRole.SYSTEM
    }
}

/**
 * LocalMessageRole 转 MessageRole
 */
private fun LocalMessageRole.toUiRole(): MessageRole {
    return when (this) {
        LocalMessageRole.USER -> MessageRole.USER
        LocalMessageRole.ASSISTANT -> MessageRole.ASSISTANT
        LocalMessageRole.SYSTEM -> MessageRole.SYSTEM
    }
}

/**
 * MessageEntity 转 MessageUi
 */
private fun MessageEntity.toMessageUi(): MessageUi {
    return MessageUi(
        id = id.toString(),
        content = content,
        role = role.toUiRole(),
        timestamp = timestamp
    )
}
