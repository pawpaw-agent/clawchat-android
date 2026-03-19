package com.openclaw.clawchat.ui.state

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openclaw.clawchat.network.GatewayUrlUtil
import com.openclaw.clawchat.network.WebSocketConnectionState
import com.openclaw.clawchat.network.protocol.GatewayConnection
import com.openclaw.clawchat.repository.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import javax.inject.Inject

/**
 * 主界面 ViewModel
 *
 * 数据流：
 * - 启动时从 Room 加载缓存的会话列表（离线可用）
 * - 连接成功后从 Gateway 拉取最新列表 → 同步到 Room
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val gateway: GatewayConnection,
    private val sessionRepository: SessionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val _events = Channel<UiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "Uncaught coroutine exception", throwable)
        _uiState.update { it.copy(error = throwable.message ?: "未知错误", isLoading = false) }
    }

    companion object {
        private const val TAG = "MainViewModel"
        private const val DEFAULT_GATEWAY_PORT = 18789
    }

    init {
        loadSessionsFromCache()
        observeConnectionState()
    }

    /**
     * 启动时从 Room 加载缓存会话（离线可用）
     */
    private fun loadSessionsFromCache() {
        viewModelScope.launch(exceptionHandler) {
            sessionRepository.observeSessions().collect { cachedSessions ->
                // 仅在没有 Gateway 数据时使用缓存
                if (_uiState.value.connectionStatus !is ConnectionStatus.Connected) {
                    _uiState.update { it.copy(sessions = cachedSessions) }
                }
            }
        }
    }

    private fun observeConnectionState() {
        viewModelScope.launch(exceptionHandler) {
            gateway.connectionState.collect { connectionState ->
                val connectionStatus = when (connectionState) {
                    is WebSocketConnectionState.Connected -> {
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
                    _events.trySend(UiEvent.ConnectionLost)
                }
            }
        }
    }

    fun connectToGateway(gatewayUrl: String) {
        viewModelScope.launch(exceptionHandler) {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val wsUrl = GatewayUrlUtil.normalizeToWebSocketUrl(gatewayUrl)
                val result = gateway.connect(wsUrl)

                result.onSuccess {
                    _uiState.update {
                        it.copy(
                            currentGateway = GatewayConfigUi(
                                id = "default", name = "Gateway", host = gatewayUrl,
                                port = DEFAULT_GATEWAY_PORT, useTls = gatewayUrl.startsWith("https://"),
                                isCurrent = true
                            ),
                            isLoading = false
                        )
                    }
                    _events.trySend(UiEvent.ShowSuccess("已连接到网关"))
                }

                result.onFailure { error ->
                    Log.e(TAG, "连接失败", error)
                    _uiState.update {
                        it.copy(
                            connectionStatus = ConnectionStatus.Error(
                                message = "连接失败：${error.message}", throwable = error
                            ),
                            isLoading = false
                        )
                    }
                    _events.trySend(UiEvent.ShowError("连接失败：${error.message}"))
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
                _events.trySend(UiEvent.ShowError("连接异常：${e.message}"))
            }
        }
    }

    /**
     * 从 Gateway 加载会话列表 → 更新 UI + 同步到 Room
     */
    private fun loadSessionsFromGateway() {
        viewModelScope.launch(exceptionHandler) {
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

                // 更新 UI
                _uiState.update { it.copy(sessions = sessions) }

                // 同步到 Room 缓存
                syncSessionsToRoom(sessions)

                Log.i(TAG, "Loaded ${sessions.size} sessions from Gateway, synced to Room")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load sessions: ${e.message}")
            }
        }
    }

    /**
     * 将 Gateway 会话列表同步到 Room（全量替换）
     */
    private suspend fun syncSessionsToRoom(sessions: List<SessionUi>) {
        try {
            // 清除旧缓存，写入新数据
            sessionRepository.clearAllSessions()
            sessions.forEach { session ->
                sessionRepository.addSession(session)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to sync sessions to Room: ${e.message}")
        }
    }

    fun disconnect() {
        viewModelScope.launch(exceptionHandler) {
            _uiState.update { it.copy(connectionStatus = ConnectionStatus.Disconnecting, currentGateway = null) }
            try {
                gateway.disconnect()
                _uiState.update { it.copy(connectionStatus = ConnectionStatus.Disconnected) }
                _events.trySend(UiEvent.ShowSuccess("已断开连接"))
            } catch (e: Exception) {
                _events.trySend(UiEvent.ShowError("断开连接失败：${e.message}"))
            }
        }
    }

    fun selectSession(sessionId: String) {
        val session = _uiState.value.sessions.find { it.id == sessionId }
        _uiState.update { it.copy(currentSession = session) }
        _events.trySend(UiEvent.NavigateToSession(sessionId))
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch(exceptionHandler) {
            try {
                gateway.call("sessions.delete", mapOf("key" to JsonPrimitive(sessionId)))
            } catch (_: Exception) {}
            // 从 Room 删除
            sessionRepository.deleteSession(sessionId)
            _uiState.update {
                it.copy(
                    sessions = it.sessions.filter { s -> s.id != sessionId },
                    currentSession = if (it.currentSession?.id == sessionId) null else it.currentSession
                )
            }
            _events.trySend(UiEvent.ShowSuccess("会话已删除"))
        }
    }

    fun refreshSessions() { loadSessionsFromGateway() }

    fun createSession(model: String = "default", thinking: Boolean = false) {
        viewModelScope.launch(exceptionHandler) {
            val sessionKey = gateway.defaultSessionKey ?: "agent:main:main"
            try {
                gateway.call("sessions.reset", mapOf(
                    "key" to JsonPrimitive(sessionKey),
                    "reason" to JsonPrimitive("new")
                ))
                refreshSessions()
            } catch (e: Exception) {
                Log.w(TAG, "Create session failed: ${e.message}")
            }
        }
    }

    fun renameSession(sessionId: String, newName: String) {
        viewModelScope.launch(exceptionHandler) {
            try {
                gateway.call("sessions.patch", mapOf(
                    "key" to JsonPrimitive(sessionId),
                    "label" to JsonPrimitive(newName)
                ))
                _uiState.update { state ->
                    val idx = state.sessions.indexOfFirst { it.id == sessionId }
                    if (idx >= 0) {
                        val updated = state.sessions.toMutableList()
                        updated[idx] = updated[idx].copy(label = newName)
                        state.copy(sessions = updated)
                    } else state
                }
                // 同步到 Room
                sessionRepository.updateSession(sessionId) { it.copy(label = newName) }
            } catch (e: Exception) {
                Log.w(TAG, "Rename session failed: ${e.message}")
            }
        }
    }

    fun pauseSession(sessionId: String) {
        _uiState.update { state ->
            val idx = state.sessions.indexOfFirst { it.id == sessionId }
            if (idx >= 0) {
                val updated = state.sessions.toMutableList()
                updated[idx] = updated[idx].copy(status = SessionStatus.PAUSED)
                state.copy(sessions = updated)
            } else state
        }
    }

    fun resumeSession(sessionId: String) {
        _uiState.update { state ->
            val idx = state.sessions.indexOfFirst { it.id == sessionId }
            if (idx >= 0) {
                val updated = state.sessions.toMutableList()
                updated[idx] = updated[idx].copy(status = SessionStatus.RUNNING, lastActivityAt = System.currentTimeMillis())
                state.copy(sessions = updated)
            } else state
        }
    }

    fun terminateSession(sessionId: String) { deleteSession(sessionId) }
    fun clearError() { _uiState.update { it.copy(error = null) } }
    fun consumeEvent() { /* no-op: Channel events are consumed on receive */ }
}

sealed class UiEvent {
    data class NavigateToSession(val sessionId: String) : UiEvent()
    data class ShowError(val message: String) : UiEvent()
    data class ShowSuccess(val message: String) : UiEvent()
    data object ShowPairingDialog : UiEvent()
    data object ConnectionLost : UiEvent()
}
