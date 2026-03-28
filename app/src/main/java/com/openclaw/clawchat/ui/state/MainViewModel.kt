package com.openclaw.clawchat.ui.state

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openclaw.clawchat.network.GatewayUrlUtil
import com.openclaw.clawchat.network.WebSocketConnectionState
import com.openclaw.clawchat.network.protocol.GatewayConnection
import com.openclaw.clawchat.repository.SessionRepository
import com.openclaw.clawchat.security.EncryptedStorage
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
 * - 自动连接：如果已配对，自动连接到已保存的 Gateway
 * - 连接成功后从 Gateway 拉取最新列表 → 同步到 Room
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val gateway: GatewayConnection,
    private val sessionRepository: SessionRepository,
    private val encryptedStorage: EncryptedStorage
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    // 是否已配对（用于决定初始导航目的地）
    private val _isPaired = MutableStateFlow(encryptedStorage.isPaired())
    val isPaired: StateFlow<Boolean> = _isPaired.asStateFlow()
    
    /**
     * 刷新配对状态（供 PairingViewModel 在配对成功后调用）
     */
    fun refreshPairedState() {
        _isPaired.value = encryptedStorage.isPaired()
    }

    private val _events = Channel<UiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "Uncaught coroutine exception", throwable)
        _uiState.update { it.copy(error = throwable.message ?: "未知错误", isLoading = false) }
    }

    override fun onCleared() {
        super.onCleared()
        _events.close()
    }

    companion object {
        private const val TAG = "MainViewModel"
        private const val DEFAULT_GATEWAY_PORT = 18789
    }

    init {
        loadSessionsFromCache()
        observeConnectionState()
        autoConnectIfNeeded()
    }

    /**
     * 自动连接：如果已配对且有保存的 Gateway URL，自动连接
     */
    private fun autoConnectIfNeeded() {
        viewModelScope.launch(exceptionHandler) {
            // 检查是否已配对或有 Gateway auth token
            if (!encryptedStorage.isPaired()) {
                Log.i(TAG, "Not paired, skipping auto-connect")
                return@launch
            }

            // 获取保存的 Gateway URL 和 token
            val gatewayUrl = encryptedStorage.getGatewayUrl()
            // 优先使用 deviceToken，否则使用 gatewayAuthToken
            val token = encryptedStorage.getDeviceToken() 
                ?: encryptedStorage.getString("gateway_auth_token")

            if (gatewayUrl.isNullOrBlank()) {
                Log.i(TAG, "No saved gateway URL, skipping auto-connect")
                return@launch
            }

            Log.i(TAG, "Auto-connecting to $gatewayUrl...")
            _uiState.update { it.copy(isLoading = true) }

            try {
                val wsUrl = GatewayUrlUtil.normalizeToWebSocketUrl(gatewayUrl)
                val result = gateway.connect(wsUrl, token)

                result.onSuccess {
                    Log.i(TAG, "Auto-connect successful")
                    _uiState.update {
                        it.copy(
                            currentGateway = GatewayConfigUi(
                                id = "default",
                                name = "Gateway",
                                host = gatewayUrl,
                                port = DEFAULT_GATEWAY_PORT,
                                isCurrent = true
                            ),
                            isLoading = false
                        )
                    }
                }

                result.onFailure { error ->
                    Log.w(TAG, "Auto-connect failed: ${error.message}")
                    _uiState.update { 
                        it.copy(
                            isLoading = false,
                            connectionError = error.message ?: "连接失败"
                        )
                    }
                    _events.trySend(UiEvent.ShowConnectionError(error.message ?: "连接失败"))
                }
            } catch (e: Exception) {
                Log.w(TAG, "Auto-connect exception: ${e.message}")
                _uiState.update { it.copy(isLoading = false) }
            }
        }
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
                                port = DEFAULT_GATEWAY_PORT,
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

                // Parse to UI SessionUi directly
                val uiSessions = sessionsArray.mapNotNull { element ->
                    try {
                        val obj = element.jsonObject
                        val sessionKey = obj["key"]?.jsonPrimitive?.content
                            ?: obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                        
                        // 从 session key 提取 agentId (格式: agent:{agentId}:{sessionId})
                        val agentId = if (sessionKey.startsWith("agent:")) {
                            sessionKey.substringAfter("agent:").substringBefore(":")
                        } else {
                            null
                        }
                        
                        // 优先级：derivedTitle > label > displayName
                        val displayLabel = obj["derivedTitle"]?.jsonPrimitive?.content
                            ?: obj["label"]?.jsonPrimitive?.content
                            ?: obj["displayName"]?.jsonPrimitive?.content
                        
                        SessionUi(
                            id = sessionKey,
                            label = displayLabel,
                            model = obj["model"]?.jsonPrimitive?.content,
                            agentId = agentId,
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
                _uiState.update { it.copy(sessions = uiSessions) }

                // 同步到 Room 缓存
                sessionRepository.saveSessions(uiSessions)

                Log.i(TAG, "Loaded ${uiSessions.size} sessions from Gateway, synced to Room")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load sessions: ${e.message}")
            }
        }
    }

    /**
     * 刷新会话列表（从 Gateway 拉取最新）
     */
    fun refreshSessions() {
        viewModelScope.launch(exceptionHandler) {
            if (_uiState.value.connectionStatus !is ConnectionStatus.Connected) {
                _events.trySend(UiEvent.ShowError("未连接到 Gateway"))
                return@launch
            }
            loadSessionsFromGateway()
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

    fun createSession(model: String = "default", thinking: Boolean = false) {
        viewModelScope.launch(exceptionHandler) {
            try {
                // 使用 sessions.reset 创建新会话（如果默认会话存在则重置）
                val sessionKey = gateway.defaultSessionKey
                if (sessionKey != null) {
                    gateway.call("sessions.reset", mapOf(
                        "key" to JsonPrimitive(sessionKey),
                        "reason" to JsonPrimitive("new")
                    ))
                } else {
                    // 如果没有默认会话，创建新会话需要通过 chat.send 触发
                    Log.w(TAG, "No default session key, sessions will be created on first message")
                }
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
    fun clearConnectionError() { _uiState.update { it.copy(connectionError = null) } }
    fun consumeEvent() { /* no-op: Channel events are consumed on receive */ }
    
    /**
     * 重试连接
     */
    fun retryConnection() {
        clearConnectionError()
        autoConnectIfNeeded()
    }

    /**
     * 检查连接状态并在需要时重连（从后台返回前台时调用）
     */
    fun checkAndReconnectIfNeeded() {
        viewModelScope.launch {
            val currentConnectionState = gateway.connectionState.value
            val isPaired = encryptedStorage.isPaired()
            val gatewayUrl = encryptedStorage.getGatewayUrl()
            
            Log.d(TAG, "=== checkAndReconnectIfNeeded: state=$currentConnectionState, isPaired=$isPaired, url=$gatewayUrl")
            
            // 如果已配对、有 URL、但未连接，则重连
            if (isPaired && !gatewayUrl.isNullOrBlank() && 
                currentConnectionState !is WebSocketConnectionState.Connected) {
                Log.i(TAG, "App resumed, reconnecting to Gateway...")
                autoConnectIfNeeded()
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// UI Events
// ─────────────────────────────────────────────────────────────

sealed class UiEvent {
    data class NavigateToSession(val sessionId: String) : UiEvent()
    data class ShowError(val message: String) : UiEvent()
    data class ShowSuccess(val message: String) : UiEvent()
    data object ShowPairingDialog : UiEvent()
    data object ConnectionLost : UiEvent()
    data class ShowConnectionError(val message: String) : UiEvent()
}