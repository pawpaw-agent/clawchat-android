package com.openclaw.clawchat.ui.state

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openclaw.clawchat.network.GatewayUrlUtil
import com.openclaw.clawchat.network.WebSocketConnectionState
import com.openclaw.clawchat.network.protocol.GatewayConnection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import javax.inject.Inject

/**
 * 主界面 ViewModel
 *
 * 使用 GatewayConnection 管理连接状态和会话列表
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val gateway: GatewayConnection
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
    }

    private fun observeConnectionState() {
        viewModelScope.launch {
            gateway.connectionState.collect { connectionState ->
                val connectionStatus = when (connectionState) {
                    is WebSocketConnectionState.Connected -> {
                        // 连接成功 → 加载会话列表
                        loadSessionsFromGateway()
                        ConnectionStatus.Connected(latency = gateway.measureLatency())
                    }
                    is WebSocketConnectionState.Connecting -> ConnectionStatus.Connecting
                    is WebSocketConnectionState.Disconnecting -> ConnectionStatus.Disconnecting
                    is WebSocketConnectionState.Disconnected -> ConnectionStatus.Disconnected
                    is WebSocketConnectionState.Error -> ConnectionStatus.Error(
                        message = connectionState.throwable.message ?: "连接错误",
                        throwable = connectionState.throwable
                    )
                }

                _uiState.update { it.copy(connectionStatus = connectionStatus) }

                if (connectionState is WebSocketConnectionState.Disconnected ||
                    connectionState is WebSocketConnectionState.Error) {
                    _events.value = UiEvent.ConnectionLost
                }
            }
        }
    }

    fun connectToGateway(gatewayUrl: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            try {
                val wsUrl = GatewayUrlUtil.normalizeToWebSocketUrl(gatewayUrl)
                val result = gateway.connect(wsUrl)

                result.onSuccess {
                    _uiState.update {
                        it.copy(
                            currentGateway = GatewayConfigUi(
                                id = "default",
                                name = "Gateway",
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
                            message = "连接异常：${e.message}", throwable = e
                        ),
                        isLoading = false
                    )
                }
                _events.value = UiEvent.ShowError("连接异常：${e.message}")
            }
        }
    }

    /**
     * 从 Gateway 加载会话列表（sessions.list RPC）
     */
    private fun loadSessionsFromGateway() {
        viewModelScope.launch {
            try {
                val response = gateway.sessionsList()
                if (!response.isSuccess()) {
                    Log.w(TAG, "sessions.list failed: ${response.error?.message}")
                    return@launch
                }

                val payload = response.payload?.jsonObject ?: return@launch
                val sessionsArray = payload["sessions"]?.jsonArray ?: return@launch

                val sessions = sessionsArray.mapNotNull { element ->
                    try {
                        val obj = element.jsonObject
                        SessionUi(
                            id = obj["key"]?.jsonPrimitive?.content
                                ?: obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                            label = obj["label"]?.jsonPrimitive?.content
                                ?: obj["derivedTitle"]?.jsonPrimitive?.content,
                            model = obj["model"]?.jsonPrimitive?.content,
                            status = SessionStatus.RUNNING,
                            lastActivityAt = obj["lastActivityAt"]?.jsonPrimitive?.long
                                ?: System.currentTimeMillis(),
                            messageCount = 0,
                            lastMessage = obj["lastMessage"]?.jsonPrimitive?.content,
                            thinking = false
                        )
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to parse session: ${e.message}")
                        null
                    }
                }

                _uiState.update { it.copy(sessions = sessions) }
                Log.i(TAG, "Loaded ${sessions.size} sessions from Gateway")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load sessions: ${e.message}")
            }
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            _uiState.update { it.copy(connectionStatus = ConnectionStatus.Disconnecting, currentGateway = null) }
            try {
                gateway.disconnect()
                _uiState.update { it.copy(connectionStatus = ConnectionStatus.Disconnected) }
                _events.value = UiEvent.ShowSuccess("已断开连接")
            } catch (e: Exception) {
                _events.value = UiEvent.ShowError("断开连接失败：${e.message}")
            }
        }
    }

    fun selectSession(sessionId: String) {
        val session = _uiState.value.sessions.find { it.id == sessionId }
        _uiState.update { it.copy(currentSession = session) }
        _events.value = UiEvent.NavigateToSession(sessionId)
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            try {
                gateway.call("sessions.delete", mapOf(
                    "key" to kotlinx.serialization.json.JsonPrimitive(sessionId)
                ))
            } catch (_: Exception) {}
            _uiState.update {
                it.copy(
                    sessions = it.sessions.filter { s -> s.id != sessionId },
                    currentSession = if (it.currentSession?.id == sessionId) null else it.currentSession
                )
            }
            _events.value = UiEvent.ShowSuccess("会话已删除")
        }
    }

    fun refreshSessions() {
        loadSessionsFromGateway()
    }

    fun createSession(model: String = "default", thinking: Boolean = false) {
        viewModelScope.launch {
            // 发送 /new 到默认会话创建新会话
            val sessionKey = gateway.defaultSessionKey ?: "agent:main:main"
            try {
                gateway.call("sessions.reset", mapOf(
                    "key" to kotlinx.serialization.json.JsonPrimitive(sessionKey),
                    "reason" to kotlinx.serialization.json.JsonPrimitive("new")
                ))
                refreshSessions()
            } catch (e: Exception) {
                Log.w(TAG, "Create session failed: ${e.message}")
            }
        }
    }

    fun renameSession(sessionId: String, newName: String) {
        viewModelScope.launch {
            try {
                gateway.call("sessions.patch", mapOf(
                    "key" to kotlinx.serialization.json.JsonPrimitive(sessionId),
                    "label" to kotlinx.serialization.json.JsonPrimitive(newName)
                ))
                _uiState.update { state ->
                    val idx = state.sessions.indexOfFirst { it.id == sessionId }
                    if (idx >= 0) {
                        val updated = state.sessions.toMutableList()
                        updated[idx] = updated[idx].copy(label = newName)
                        state.copy(sessions = updated)
                    } else state
                }
            } catch (e: Exception) {
                Log.w(TAG, "Rename session failed: ${e.message}")
            }
        }
    }

    fun pauseSession(sessionId: String) {
        viewModelScope.launch {
            _uiState.update { state ->
                val idx = state.sessions.indexOfFirst { it.id == sessionId }
                if (idx >= 0) {
                    val updated = state.sessions.toMutableList()
                    updated[idx] = updated[idx].copy(status = SessionStatus.PAUSED)
                    state.copy(sessions = updated)
                } else state
            }
        }
    }

    fun resumeSession(sessionId: String) {
        viewModelScope.launch {
            _uiState.update { state ->
                val idx = state.sessions.indexOfFirst { it.id == sessionId }
                if (idx >= 0) {
                    val updated = state.sessions.toMutableList()
                    updated[idx] = updated[idx].copy(status = SessionStatus.RUNNING, lastActivityAt = System.currentTimeMillis())
                    state.copy(sessions = updated)
                } else state
            }
        }
    }

    fun terminateSession(sessionId: String) {
        deleteSession(sessionId)
    }

    fun clearError() { _uiState.update { it.copy(error = null) } }
    fun consumeEvent() { _events.value = null }
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
