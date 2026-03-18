package com.openclaw.clawchat.ui.state

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openclaw.clawchat.data.local.MessageEntity
import com.openclaw.clawchat.data.local.MessageRole as LocalMessageRole
import com.openclaw.clawchat.data.local.MessageStatus
import com.openclaw.clawchat.network.WebSocketService
import com.openclaw.clawchat.network.WebSocketConnectionState
import com.openclaw.clawchat.repository.MessageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID
import javax.inject.Inject

/**
 * 会话界面 ViewModel
 *
 * 负责管理会话中的消息收发：
 * - 发送用户消息到网关（chat.send RPC）
 * - 接收并显示助手回复（chat 事件流）
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
        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
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
     * 观察接收到的原始 JSON 消息
     */
    private fun observeIncomingMessages() {
        viewModelScope.launch {
            webSocketService.incomingMessages.collect { rawJson ->
                handleIncomingMessage(rawJson)
            }
        }
    }

    /**
     * 处理接收到的原始 JSON 消息
     *
     * 解析 event 帧中的 chat / session.message 事件，
     * 提取助手回复并更新 UI。
     */
    private fun handleIncomingMessage(rawJson: String) {
        viewModelScope.launch {
            val sessionId = _state.value.sessionId ?: return@launch

            try {
                val obj = json.parseToJsonElement(rawJson).jsonObject
                val type = obj["type"]?.jsonPrimitive?.content
                val event = obj["event"]?.jsonPrimitive?.content

                when {
                    // 处理 chat 事件（Protocol v3 标准 chat 流）
                    type == "event" && event == "chat" -> {
                        val payload = obj["payload"]?.jsonObject ?: return@launch
                        val eventSessionKey = payload["sessionKey"]?.jsonPrimitive?.content
                        val state = payload["state"]?.jsonPrimitive?.content
                        val msgObj = payload["message"]?.jsonObject
                        val content = msgObj?.get("content")?.jsonPrimitive?.content ?: ""
                        val role = msgObj?.get("role")?.jsonPrimitive?.content ?: "assistant"

                        if (state == "final" || state == "delta") {
                            val messageRole = when (role) {
                                "user" -> MessageRole.USER
                                "system" -> MessageRole.SYSTEM
                                else -> MessageRole.ASSISTANT
                            }

                            val assistantMessage = MessageUi(
                                id = UUID.randomUUID().toString(),
                                content = content,
                                role = messageRole,
                                timestamp = System.currentTimeMillis()
                            )

                            if (state == "final") {
                                messageRepository.saveMessage(
                                    sessionId = sessionId,
                                    role = messageRole.toLocalRole(),
                                    content = content,
                                    timestamp = System.currentTimeMillis(),
                                    status = MessageStatus.DELIVERED
                                )
                            }

                            _state.update {
                                it.copy(
                                    messages = it.messages + assistantMessage,
                                    isLoading = false
                                )
                            }

                            _events.value = SessionEvent.MessageReceived(assistantMessage)
                        }
                    }

                    // 处理旧风格 session.message 事件
                    type == "event" && (event == "session.message" || event == "session.message.update") -> {
                        val payload = obj["payload"]?.jsonObject ?: return@launch
                        val msgObj = payload["message"]?.jsonObject ?: return@launch
                        val content = msgObj["content"]?.jsonPrimitive?.content ?: ""
                        val role = msgObj["role"]?.jsonPrimitive?.content ?: "assistant"

                        val messageRole = when (role) {
                            "user" -> MessageRole.USER
                            "system" -> MessageRole.SYSTEM
                            else -> MessageRole.ASSISTANT
                        }

                        val assistantMessage = MessageUi(
                            id = UUID.randomUUID().toString(),
                            content = content,
                            role = messageRole,
                            timestamp = System.currentTimeMillis()
                        )

                        messageRepository.saveMessage(
                            sessionId = sessionId,
                            role = messageRole.toLocalRole(),
                            content = content,
                            timestamp = System.currentTimeMillis(),
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

                    // 处理错误事件
                    type == "event" && event == "error" -> {
                        val payload = obj["payload"]?.jsonObject
                        val message = payload?.get("message")?.jsonPrimitive?.content ?: "Unknown error"

                        _state.update {
                            it.copy(error = message, isLoading = false)
                        }
                        _events.value = SessionEvent.Error(message)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse incoming message: ${e.message}")
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
                role = LocalMessageRole.USER,
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

            // 构建 chat.send RPC 请求帧
            val requestId = "req-${System.currentTimeMillis()}"
            val idempotencyKey = UUID.randomUUID().toString()
            val requestFrame = buildJsonObject {
                put("type", JsonPrimitive("req"))
                put("id", JsonPrimitive(requestId))
                put("method", JsonPrimitive("chat.send"))
                put("params", buildJsonObject {
                    put("sessionKey", JsonPrimitive(sessionId))
                    put("message", JsonPrimitive(content))
                    put("idempotencyKey", JsonPrimitive(idempotencyKey))
                })
            }

            try {
                val result = webSocketService.sendRaw(requestFrame.toString())

                result.onSuccess {
                    messageRepository.saveMessage(
                        sessionId = sessionId,
                        role = LocalMessageRole.USER,
                        content = content,
                        timestamp = userMessage.timestamp,
                        status = MessageStatus.SENT
                    )
                }.onFailure { error ->
                    Log.e(TAG, "发送消息失败", error)
                    messageRepository.saveMessage(
                        sessionId = sessionId,
                        role = LocalMessageRole.USER,
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
                messageRepository.saveMessage(
                    sessionId = sessionId,
                    role = LocalMessageRole.USER,
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
                messages = emptyList()
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
