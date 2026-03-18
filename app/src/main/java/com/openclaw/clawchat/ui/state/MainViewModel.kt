package com.openclaw.clawchat.ui.state

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openclaw.clawchat.network.WebSocketService
import com.openclaw.clawchat.network.GatewayMessage
import com.openclaw.clawchat.network.WebSocketConnectionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

/**
 * 主界面 ViewModel
 * 
 * 负责管理：
 * - 连接状态
 * - 会话列表
 * - 全局 UI 状态
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val webSocketService: WebSocketService
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val _events = MutableStateFlow<UiEvent?>(null)
    val events: StateFlow<UiEvent?> = _events.asStateFlow()

    companion object {
        private const val TAG = "MainViewModel"
    }

    init {
        observeConnectionState()
        loadSessions()
    }

    /**
     * 观察 WebSocket 连接状态
     */
    private fun observeConnectionState() {
        viewModelScope.launch {
            webSocketService.connectionState.collect { connectionState ->
                val connectionStatus = when (connectionState) {
                    is WebSocketConnectionState.Connected -> {
                        // 连接成功后测量延迟
                        val latency = webSocketService.measureLatency()
                        ConnectionStatus.Connected(latency = latency)
                    }
                    is WebSocketConnectionState.Connecting -> ConnectionStatus.Connecting
                    is WebSocketConnectionState.Disconnecting -> ConnectionStatus.Disconnecting
                    is WebSocketConnectionState.Disconnected -> ConnectionStatus.Disconnected
                    is WebSocketConnectionState.Error -> ConnectionStatus.Error(
                        message = connectionState.throwable.message ?: "WebSocket 错误",
                        throwable = connectionState.throwable
                    )
                }

                _uiState.update { it.copy(connectionStatus = connectionStatus) }

                // 连接断开时通知用户
                if (connectionState is WebSocketConnectionState.Disconnected ||
                    connectionState is WebSocketConnectionState.Error) {
                    _events.value = UiEvent.ConnectionLost
                }
            }
        }
    }

    /**
     * 连接到网关
     */
    fun connectToGateway(gatewayUrl: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            try {
                // 构建 WebSocket URL
                val wsUrl = gatewayUrl
                    .replace("http://", "ws://")
                    .replace("https://", "wss://")
                    .plus("/ws")

                val result = webSocketService.connect(wsUrl, token = null)

                result.onSuccess {
                    _uiState.update {
                        it.copy(
                            connectionStatus = ConnectionStatus.Connected(),
                            currentGateway = GatewayConfigUi(
                                id = "default",
                                name = "本地网关",
                                host = gatewayUrl,
                                port = 18789,
                                useTls = gatewayUrl.startsWith("https://"),
                                isCurrent = true
                            ),
                            isLoading = false
                        )
                    }
                    _events.value = UiEvent.ShowSuccess("已连接到网关")
                }

                result.onFailure { error ->
                    Log.e(TAG, "连接失败", error)
                    _uiState.update {
                        it.copy(
                            connectionStatus = ConnectionStatus.Error(
                                message = "连接失败：${error.message}",
                                throwable = error
                            ),
                            isLoading = false
                        )
                    }
                    _events.value = UiEvent.ShowError("连接失败：${error.message}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "连接异常", e)
                _uiState.update {
                    it.copy(
                        connectionStatus = ConnectionStatus.Error(
                            message = "连接异常：${e.message}",
                            throwable = e
                        ),
                        isLoading = false
                    )
                }
                _events.value = UiEvent.ShowError("连接异常：${e.message}")
            }
        }
    }

    /**
     * 断开连接
     */
    fun disconnect() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    connectionStatus = ConnectionStatus.Disconnecting,
                    currentGateway = null
                )
            }

            try {
                webSocketService.disconnect()
                _uiState.update {
                    it.copy(
                        connectionStatus = ConnectionStatus.Disconnected
                    )
                }
                _events.value = UiEvent.ShowSuccess("已断开连接")
            } catch (e: Exception) {
                Log.e(TAG, "断开连接失败", e)
                _events.value = UiEvent.ShowError("断开连接失败：${e.message}")
            }
        }
    }

    /**
     * 选择会话
     */
    fun selectSession(sessionId: String) {
        viewModelScope.launch {
            val session = _uiState.value.sessions.find { it.id == sessionId }
            _uiState.update { it.copy(currentSession = session) }
            _events.value = UiEvent.NavigateToSession(sessionId)
        }
    }

    /**
     * 创建新会话
     */
    fun createSession(model: String = "default", thinking: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            val newSession = SessionUi(
                id = "session_${System.currentTimeMillis()}",
                label = "新会话",
                model = model,
                status = SessionStatus.RUNNING,
                lastActivityAt = System.currentTimeMillis(),
                messageCount = 0,
                thinking = thinking
            )

            _uiState.update {
                it.copy(
                    sessions = it.sessions + newSession,
                    currentSession = newSession,
                    isLoading = false
                )
            }

            // 发送创建会话的系统消息
            sendCreateSessionEvent(newSession.id)

            _events.value = UiEvent.NavigateToSession(newSession.id)
        }
    }

    /**
     * 删除会话
     */
    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    sessions = it.sessions.filter { it.id != sessionId },
                    currentSession = if (it.currentSession?.id == sessionId) null else it.currentSession
                )
            }

            // TODO: 调用 API 删除服务端会话
            _events.value = UiEvent.ShowSuccess("会话已删除")
        }
    }

    /**
     * 暂停会话
     */
    fun pauseSession(sessionId: String) {
        viewModelScope.launch {
            val sessionIndex = _uiState.value.sessions.indexOfFirst { it.id == sessionId }
            if (sessionIndex >= 0) {
                val session = _uiState.value.sessions[sessionIndex]
                val updatedSession = session.copy(status = SessionStatus.PAUSED)

                _uiState.update {
                    it.copy(
                        sessions = it.sessions.toMutableList().apply {
                            set(sessionIndex, updatedSession)
                        }
                    )
                }

                // 发送暂停会话的系统消息
                sendSystemEvent(sessionId, "session.paused")
            }
        }
    }

    /**
     * 恢复会话
     */
    fun resumeSession(sessionId: String) {
        viewModelScope.launch {
            val sessionIndex = _uiState.value.sessions.indexOfFirst { it.id == sessionId }
            if (sessionIndex >= 0) {
                val session = _uiState.value.sessions[sessionIndex]
                val updatedSession = session.copy(
                    status = SessionStatus.RUNNING,
                    lastActivityAt = System.currentTimeMillis()
                )

                _uiState.update {
                    it.copy(
                        sessions = it.sessions.toMutableList().apply {
                            set(sessionIndex, updatedSession)
                        }
                    )
                }

                // 发送恢复会话的系统消息
                sendSystemEvent(sessionId, "session.resumed")
            }
        }
    }

    /**
     * 重命名会话
     */
    fun renameSession(sessionId: String, newName: String) {
        viewModelScope.launch {
            val sessionIndex = _uiState.value.sessions.indexOfFirst { it.id == sessionId }
            if (sessionIndex >= 0) {
                val session = _uiState.value.sessions[sessionIndex]
                val updatedSession = session.copy(label = newName)

                _uiState.update {
                    it.copy(
                        sessions = it.sessions.toMutableList().apply {
                            set(sessionIndex, updatedSession)
                        }
                    )
                }

                _events.value = UiEvent.ShowSuccess("会话已重命名")
            }
        }
    }

    /**
     * 终止会话
     */
    fun terminateSession(sessionId: String) {
        viewModelScope.launch {
            val sessionIndex = _uiState.value.sessions.indexOfFirst { it.id == sessionId }
            if (sessionIndex >= 0) {
                val session = _uiState.value.sessions[sessionIndex]
                val updatedSession = session.copy(
                    status = SessionStatus.TERMINATED,
                    lastActivityAt = System.currentTimeMillis()
                )

                _uiState.update {
                    it.copy(
                        sessions = it.sessions.toMutableList().apply {
                            set(sessionIndex, updatedSession)
                        },
                        currentSession = if (it.currentSession?.id == sessionId) null else it.currentSession
                    )
                }

                // 发送终止会话的系统消息
                sendSystemEvent(sessionId, "session.terminated")

                _events.value = UiEvent.ShowSuccess("会话已终止")
            }
        }
    }

    /**
     * 加载会话列表
     */
    private fun loadSessions() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            // TODO: 从服务器加载真实会话列表
            // 暂时使用模拟数据
            val mockSessions = listOf(
                SessionUi(
                    id = "session_1",
                    label = "项目讨论",
                    model = "qwen3.5-plus",
                    status = SessionStatus.RUNNING,
                    lastActivityAt = System.currentTimeMillis() - 60000,
                    messageCount = 15,
                    lastMessage = "好的，我来帮你实现这个功能",
                    thinking = false
                ),
                SessionUi(
                    id = "session_2",
                    label = "代码审查",
                    model = "qwen3.5-plus",
                    status = SessionStatus.RUNNING,
                    lastActivityAt = System.currentTimeMillis() - 3600000,
                    messageCount = 8,
                    lastMessage = "这段代码需要优化",
                    thinking = false
                )
            )

            _uiState.update {
                it.copy(
                    sessions = mockSessions,
                    isLoading = false
                )
            }
        }
    }

    /**
     * 发送创建会话事件
     */
    private suspend fun sendCreateSessionEvent(sessionId: String) {
        val createEvent = GatewayMessage.SystemEvent(
            text = "session.created:$sessionId",
            timestamp = System.currentTimeMillis()
        )
        webSocketService.send(createEvent)
    }

    /**
     * 发送系统事件
     */
    private suspend fun sendSystemEvent(sessionId: String, eventType: String) {
        val systemEvent = GatewayMessage.SystemEvent(
            text = "$eventType:$sessionId",
            timestamp = System.currentTimeMillis()
        )
        webSocketService.send(systemEvent)
    }

    /**
     * 清除错误
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    /**
     * 清除事件
     */
    fun consumeEvent() {
        _events.value = null
    }
}

/**
 * UI 事件类型
 */
sealed class UiEvent {
    data class NavigateToSession(val sessionId: String) : UiEvent()
    data class ShowError(val message: String) : UiEvent()
    data class ShowSuccess(val message: String) : UiEvent()
    data object ShowPairingDialog : UiEvent()
    data object ConnectionLost : UiEvent()
}


