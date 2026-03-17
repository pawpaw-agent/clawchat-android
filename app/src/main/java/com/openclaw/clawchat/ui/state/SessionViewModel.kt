package com.openclaw.clawchat.ui.state

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openclaw.clawchat.network.WebSocketService
import com.openclaw.clawchat.network.GatewayMessage
import com.openclaw.clawchat.network.WebSocketConnectionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * 会话界面 ViewModel
 * 
 * 负责管理会话中的消息收发：
 * - 发送用户消息到网关
 * - 接收并显示助手回复
 * - 管理消息历史
 * - 处理连接状态
 */
class SessionViewModel(
    private val webSocketService: WebSocketService
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
    }

    /**
     * 观察 WebSocket 连接状态
     */
    private fun observeWebSocketConnection() {
        viewModelScope.launch {
            webSocketService.connectionState.collect { connectionState ->
                val connectionStatus = when (connectionState) {
                    is WebSocketConnectionState.Connected -> ConnectionStatus.Connected
                    is WebSocketConnectionState.Connecting -> ConnectionStatus.Connecting
                    is WebSocketConnectionState.Disconnecting -> ConnectionStatus.Disconnecting
                    is WebSocketConnectionState.Disconnected -> ConnectionStatus.Disconnected
                    is WebSocketConnectionState.Error -> ConnectionStatus.Error(
                        connectionState.throwable.message ?: "WebSocket 错误"
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
            when (message) {
                is GatewayMessage.AgentMessage -> {
                    // 收到助手回复
                    val assistantMessage = MessageUi(
                        id = UUID.randomUUID().toString(),
                        content = message.content,
                        role = MessageRole.ASSISTANT,
                        timestamp = System.currentTimeMillis()
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
                        content = formatSystemEvent(message),
                        role = MessageRole.SYSTEM,
                        timestamp = System.currentTimeMillis()
                    )

                    _state.update {
                        it.copy(messages = it.messages + systemMessage)
                    }
                }

                is GatewayMessage.ErrorEvent -> {
                    // 错误事件
                    _state.update {
                        it.copy(
                            error = message.error,
                            isLoading = false
                        )
                    }

                    _events.value = SessionEvent.Error(message.error)
                }

                else -> {
                    Log.w(TAG, "收到未知消息类型：$message")
                }
            }
        }
    }

    /**
     * 格式化系统事件为可读文本
     */
    private fun formatSystemEvent(event: GatewayMessage.SystemEvent): String {
        return when (event.eventType) {
            "session.created" -> "会话已创建"
            "session.terminated" -> "会话已结束"
            "session.paused" -> "会话已暂停"
            "agent.thinking" -> "助手正在思考..."
            "agent.completed" -> "助手已完成"
            else -> "系统事件：${event.eventType}"
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

            // 创建用户消息
            val userMessage = MessageUi(
                id = UUID.randomUUID().toString(),
                content = content,
                role = MessageRole.USER,
                timestamp = System.currentTimeMillis()
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
            val gatewayMessage = GatewayMessage.AgentMessage(
                sessionId = _state.value.sessionId ?: "",
                content = content,
                role = "user",
                timestamp = System.currentTimeMillis()
            )

            try {
                // 发送到网关
                val result = webSocketService.send(gatewayMessage)

                result.onFailure { error ->
                    Log.e(TAG, "发送消息失败", error)
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
