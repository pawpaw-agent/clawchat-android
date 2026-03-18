package com.openclaw.clawchat.ui.state

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openclaw.clawchat.data.local.MessageEntity
import com.openclaw.clawchat.data.local.MessageRole as LocalMessageRole
import com.openclaw.clawchat.data.local.MessageStatus
import com.openclaw.clawchat.network.WebSocketConnectionState
import com.openclaw.clawchat.network.protocol.GatewayConnection
import com.openclaw.clawchat.repository.MessageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
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

    private val _events = MutableStateFlow<SessionEvent?>(null)
    val events: StateFlow<SessionEvent?> = _events.asStateFlow()

    /** 流式消息缓冲：runId → 累积内容 */
    private val streamingBuffers = mutableMapOf<String, StringBuilder>()

    /** 已完成的 runId（防止 final 后重复处理 delta） */
    private val completedRuns = mutableSetOf<String>()

    companion object {
        private const val TAG = "SessionViewModel"
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    }

    init {
        observeConnectionState()
        observeIncomingMessages()
        loadMessageHistory()
    }

    private fun observeConnectionState() {
        viewModelScope.launch {
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
        viewModelScope.launch {
            val sessionId = savedStateHandle.get<String>("sessionId") ?: return@launch
            messageRepository.getMessages(sessionId).collect { entities ->
                _state.update { it.copy(messages = entities.map { e -> e.toMessageUi() }) }
            }
        }
    }

    // ── ChatEvent 状态机 ──

    private fun observeIncomingMessages() {
        viewModelScope.launch {
            gateway.incomingMessages.collect { rawJson ->
                handleIncomingFrame(rawJson)
            }
        }
    }

    private fun handleIncomingFrame(rawJson: String) {
        viewModelScope.launch {
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
                val content = msgObj?.get("content")?.jsonPrimitive?.content ?: ""
                val role = msgObj?.get("role")?.jsonPrimitive?.content ?: "assistant"

                when (state) {
                    "delta" -> handleDelta(runId, content, role)
                    "final" -> handleFinal(runId, content, role, sessionId)
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
    private fun handleDelta(runId: String, content: String, role: String) {
        if (completedRuns.contains(runId)) return

        val buffer = streamingBuffers.getOrPut(runId) { StringBuilder() }
        buffer.append(content)

        // 更新或添加流式消息
        _state.update { currentState ->
            val existingIdx = currentState.messages.indexOfFirst { it.id == runId }
            val streamingMsg = MessageUi(
                id = runId,
                content = buffer.toString(),
                role = parseRole(role),
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
    private suspend fun handleFinal(runId: String, content: String, role: String, sessionId: String) {
        completedRuns.add(runId)

        // 使用缓冲中的完整内容（如果有），否则用 final 的 content
        val finalContent = streamingBuffers.remove(runId)?.toString()?.ifEmpty { content } ?: content
        val messageRole = parseRole(role)

        // 写入 Room 缓存
        messageRepository.saveMessage(
            sessionId = sessionId,
            role = messageRole.toLocalRole(),
            content = finalContent,
            timestamp = System.currentTimeMillis(),
            status = MessageStatus.DELIVERED
        )

        val finalMsg = MessageUi(
            id = runId,
            content = finalContent,
            role = messageRole,
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

        _events.value = SessionEvent.MessageReceived(finalMsg)
    }

    /** aborted: 标记中止 */
    private fun handleAborted(runId: String, sessionId: String) {
        completedRuns.add(runId)
        val partialContent = streamingBuffers.remove(runId)?.toString() ?: ""

        _state.update { currentState ->
            val existingIdx = currentState.messages.indexOfFirst { it.id == runId }
            if (existingIdx >= 0) {
                val msg = currentState.messages[existingIdx].copy(
                    content = partialContent + "\n[已中止]",
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
        _events.value = SessionEvent.Error(errorMsg)
    }

    // ── 发送消息 ──

    fun sendMessage(content: String) {
        viewModelScope.launch {
            if (content.isBlank()) {
                _events.value = SessionEvent.Error("消息内容不能为空")
                return@launch
            }
            if (_state.value.connectionStatus !is ConnectionStatus.Connected) {
                _events.value = SessionEvent.Error("未连接到网关")
                return@launch
            }

            val sessionKey = _state.value.sessionId
                ?: gateway.defaultSessionKey
                ?: return@launch

            // 显示用户消息
            val userMessage = MessageUi(
                id = UUID.randomUUID().toString(),
                content = content,
                role = MessageRole.USER,
                timestamp = System.currentTimeMillis()
            )

            messageRepository.saveMessage(
                sessionId = sessionKey,
                role = LocalMessageRole.USER,
                content = content,
                timestamp = userMessage.timestamp,
                status = MessageStatus.PENDING
            )

            _state.update {
                it.copy(
                    messages = it.messages + userMessage,
                    inputText = "",
                    isLoading = true
                )
            }

            // 通过 GatewayConnection.chatSend（含 idempotencyKey）
            try {
                val response = gateway.chatSend(sessionKey, content)

                if (response.isSuccess()) {
                    messageRepository.saveMessage(
                        sessionId = sessionKey,
                        role = LocalMessageRole.USER,
                        content = content,
                        timestamp = userMessage.timestamp,
                        status = MessageStatus.SENT
                    )
                } else {
                    val errMsg = response.error?.message ?: "发送失败"
                    messageRepository.saveMessage(
                        sessionId = sessionKey,
                        role = LocalMessageRole.USER,
                        content = content,
                        timestamp = userMessage.timestamp,
                        status = MessageStatus.FAILED
                    )
                    _state.update { it.copy(error = errMsg, isLoading = false) }
                    _events.value = SessionEvent.Error(errMsg)
                }
            } catch (e: Exception) {
                Log.e(TAG, "发送消息异常", e)
                messageRepository.saveMessage(
                    sessionId = sessionKey,
                    role = LocalMessageRole.USER,
                    content = content,
                    timestamp = userMessage.timestamp,
                    status = MessageStatus.FAILED
                )
                _state.update { it.copy(error = "发送异常：${e.message}", isLoading = false) }
                _events.value = SessionEvent.Error("发送异常：${e.message}")
            }
        }
    }

    // ── UI 操作 ──

    fun updateInputText(text: String) { _state.update { it.copy(inputText = text) } }
    fun clearError() { _state.update { it.copy(error = null) } }
    fun consumeEvent() { _events.value = null }

    fun setSessionId(sessionId: String) {
        streamingBuffers.clear()
        completedRuns.clear()
        _state.update { it.copy(sessionId = sessionId, messages = emptyList()) }
    }

    fun clearSession() {
        streamingBuffers.clear()
        completedRuns.clear()
        _state.update {
            it.copy(sessionId = null, session = null, messages = emptyList(),
                inputText = "", isLoading = false, error = null)
        }
    }

    // ── 工具 ──

    private fun parseRole(role: String): MessageRole = when (role) {
        "user" -> MessageRole.USER
        "system" -> MessageRole.SYSTEM
        else -> MessageRole.ASSISTANT
    }
}

sealed class SessionEvent {
    data class MessageReceived(val message: MessageUi) : SessionEvent()
    data class Error(val message: String) : SessionEvent()
    data object SessionEnded : SessionEvent()
}

private fun MessageRole.toLocalRole(): LocalMessageRole = when (this) {
    MessageRole.USER -> LocalMessageRole.USER
    MessageRole.ASSISTANT -> LocalMessageRole.ASSISTANT
    MessageRole.SYSTEM -> LocalMessageRole.SYSTEM
}

private fun LocalMessageRole.toUiRole(): MessageRole = when (this) {
    LocalMessageRole.USER -> MessageRole.USER
    LocalMessageRole.ASSISTANT -> MessageRole.ASSISTANT
    LocalMessageRole.SYSTEM -> MessageRole.SYSTEM
}

private fun MessageEntity.toMessageUi(): MessageUi = MessageUi(
    id = id.toString(),
    content = content,
    role = role.toUiRole(),
    timestamp = timestamp
)
