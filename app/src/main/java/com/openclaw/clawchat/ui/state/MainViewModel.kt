package com.openclaw.clawchat.ui.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openclaw.clawchat.network.GatewayUrlUtil
import com.openclaw.clawchat.network.WebSocketConnectionState
import com.openclaw.clawchat.network.protocol.GatewayConnection
import com.openclaw.clawchat.repository.SessionRepository
import com.openclaw.clawchat.security.EncryptedStorage
import com.openclaw.clawchat.util.AppLog
import com.openclaw.clawchat.util.ConnectionStatusMapper.toStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
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
        AppLog.e(TAG, "Uncaught coroutine exception", throwable)
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
        observeGatewayEvents()
        autoConnectIfNeeded()
    }

    /**
     * 自动连接：如果已配对且有保存的 Gateway URL，自动连接
     */
    private fun autoConnectIfNeeded() {
        viewModelScope.launch(exceptionHandler) {
            // 检查是否已配对或有 Gateway auth token
            if (!encryptedStorage.isPaired()) {
                AppLog.i(TAG, "Not paired, skipping auto-connect")
                return@launch
            }

            // 获取保存的 Gateway URL 和 token
            val gatewayUrl = encryptedStorage.getGatewayUrl()
            // 优先使用 deviceToken，否则使用 gatewayAuthToken
            val token = encryptedStorage.getDeviceToken() 
                ?: encryptedStorage.getString("gateway_auth_token")

            if (gatewayUrl.isNullOrBlank()) {
                AppLog.i(TAG, "No saved gateway URL, skipping auto-connect")
                return@launch
            }

            AppLog.i(TAG, "Auto-connecting to $gatewayUrl...")
            _uiState.update { it.copy(isLoading = true) }

            try {
                // 如果 URL 已经是完整的 WebSocket URL，直接使用；否则标准化
                val wsUrl = if (gatewayUrl.startsWith("ws://") || gatewayUrl.startsWith("wss://")) {
                    gatewayUrl
                } else {
                    GatewayUrlUtil.normalizeToWebSocketUrl(gatewayUrl)
                }
                val result = gateway.connect(wsUrl, token)

                result.onSuccess {
                    AppLog.i(TAG, "Auto-connect successful")
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
                    AppLog.w(TAG, "Auto-connect failed: ${error.message}")
                    _uiState.update { 
                        it.copy(
                            isLoading = false,
                            connectionError = error.message ?: "连接失败"
                        )
                    }
                    _events.trySend(UiEvent.ShowConnectionError(error.message ?: "连接失败"))
                }
            } catch (e: Exception) {
                AppLog.w(TAG, "Auto-connect exception: ${e.message}")
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
                    else -> connectionState.toStatus()
                }

                _uiState.update { it.copy(connectionStatus = connectionStatus) }

                if (connectionState is WebSocketConnectionState.Disconnected ||
                    connectionState is WebSocketConnectionState.Error) {
                    _events.trySend(UiEvent.ConnectionLost)
                }
            }
        }
    }

    /**
     * 监听 Gateway 事件（如 update.available）
     */
    private fun observeGatewayEvents() {
        viewModelScope.launch {
            gateway.incomingMessages.collect { rawJson ->
                try {
                    val obj = Json.decodeFromString<JsonObject>(rawJson)
                    val type = obj["type"]?.jsonPrimitive?.content
                    val event = obj["event"]?.jsonPrimitive?.content
                    if (type == "event" && event == "update.available") {
                        val payload = obj["payload"]?.jsonObject
                        val version = payload?.get("version")?.jsonPrimitive?.content ?: ""
                        val message = payload?.get("message")?.jsonPrimitive?.content ?: ""
                        AppLog.i(TAG, "Update available: version=$version")
                        showUpdate(version = version, message = message)
                    }
                } catch (e: Exception) {
                    // 忽略非 JSON 消息
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
                    AppLog.e(TAG, "连接失败", error)
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
                AppLog.e(TAG, "连接异常", e)
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
                    AppLog.w(TAG, "sessions.list failed: ${response.error?.message}")
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

                        // Agent 名称和 emoji（从 Gateway 响应中提取）
                        val agentName = obj["agentName"]?.jsonPrimitive?.content
                        val agentEmoji = obj["agentEmoji"]?.jsonPrimitive?.content

                        // 优先级：derivedTitle > label > displayName
                        val displayLabel = obj["derivedTitle"]?.jsonPrimitive?.content
                            ?: obj["label"]?.jsonPrimitive?.content
                            ?: obj["displayName"]?.jsonPrimitive?.content

                        SessionUi(
                            id = sessionKey,
                            label = displayLabel,
                            model = obj["model"]?.jsonPrimitive?.content,
                            agentId = agentId,
                            agentName = agentName,
                            agentEmoji = agentEmoji,
                            status = SessionStatus.RUNNING,
                            lastActivityAt = obj["lastActivityAt"]?.jsonPrimitive?.long
                                ?: System.currentTimeMillis(),
                            messageCount = 0,
                            lastMessage = obj["lastMessage"]?.jsonPrimitive?.content,
                            thinking = false,
                            // Context token 用量
                            totalTokens = obj["totalTokens"]?.jsonPrimitive?.content?.toIntOrNull(),
                            contextTokens = obj["contextTokens"]?.jsonPrimitive?.content?.toIntOrNull(),
                            totalTokensFresh = obj["totalTokensFresh"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: true
                        )
                    } catch (e: Exception) {
                        AppLog.w(TAG, "Failed to parse session: ${e.message}")
                        null
                    }
                }

                // 更新 UI
                _uiState.update { it.copy(sessions = uiSessions) }

                // 同步到 Room 缓存
                sessionRepository.saveSessions(uiSessions)

                AppLog.i(TAG, "Loaded ${uiSessions.size} sessions from Gateway, synced to Room")
            } catch (e: Exception) {
                AppLog.w(TAG, "Failed to load sessions: ${e.message}")
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
                // 使用 Gateway API 删除会话
                val response = gateway.sessionsDelete(sessionId, deleteTranscript = true)
                if (response.isSuccess()) {
                    AppLog.i(TAG, "Session deleted on gateway: $sessionId")
                } else {
                    AppLog.w(TAG, "Gateway delete failed: ${response.error?.message}")
                }
            } catch (e: Exception) {
                AppLog.w(TAG, "Gateway delete exception: ${e.message}")
            }

            // 从本地 Room 删除
            sessionRepository.deleteSession(sessionId)

            // 更新 UI 状态
            _uiState.update {
                it.copy(
                    sessions = it.sessions.filter { s -> s.id != sessionId },
                    currentSession = if (it.currentSession?.id == sessionId) null else it.currentSession
                )
            }
            _events.trySend(UiEvent.ShowSuccess("会话已删除"))
        }
    }
    
    /**
     * 清除当前会话的所有消息
     */
    fun clearCurrentSession() {
        val sessionId = _uiState.value.currentSession?.id ?: return
        viewModelScope.launch(exceptionHandler) {
            try {
                val response = gateway.sessionsReset(sessionId, "clear")
                if (response.isSuccess()) {
                    _events.trySend(UiEvent.ShowSuccess("会话已清除"))
                } else {
                    _events.trySend(UiEvent.ShowError("清除失败：${response.error?.message}"))
                }
            } catch (e: Exception) {
                _events.trySend(UiEvent.ShowError("清除失败：${e.message}"))
            }
        }
    }

    fun createSession(model: String = "default", thinking: Boolean = false) {
        createSessionWithAgentModel(agentId = null, model = model.takeIf { it != "default" })
    }

    /**
     * 创建会话（支持 Agent/Model 选择）
     */
    fun createSessionWithAgentModel(
        agentId: String? = null,
        model: String? = null,
        initialMessage: String? = null,
        label: String? = null
    ) {
        viewModelScope.launch(exceptionHandler) {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val response = gateway.sessionsCreate(
                    agentId = agentId,
                    model = model,
                    label = label,
                    message = initialMessage
                )

                if (response.isSuccess()) {
                    response.payload?.let { payload ->
                        val sessionObj = payload.jsonObject["session"]?.jsonObject
                        val sessionKey = sessionObj?.get("key")?.jsonPrimitive?.content

                        if (!sessionKey.isNullOrBlank()) {
                            refreshSessions()
                            _events.trySend(UiEvent.NavigateToSession(sessionKey))
                            _events.trySend(UiEvent.ShowSuccess("会话已创建"))
                        }
                    }
                } else {
                    // 如果 sessions.create 不可用，尝试使用 sessions.reset
                    val sessionKey = gateway.defaultSessionKey
                    if (sessionKey != null) {
                        gateway.sessionsReset(sessionKey, "clear")
                        refreshSessions()
                    }
                }
            } catch (e: Exception) {
                AppLog.w(TAG, "Create session failed: ${e.message}")
                _events.trySend(UiEvent.ShowError("创建会话失败：${e.message}"))
            } finally {
                _uiState.update { it.copy(isLoading = false, showCreateDialog = false) }
            }
        }
    }

    /**
     * 显示创建会话对话框
     */
    fun showCreateSessionDialog() {
        // 先加载 agents 和 models
        loadAgentsAndModels()
        _uiState.update { it.copy(showCreateDialog = true) }
    }

    /**
     * 隐藏创建会话对话框
     */
    fun hideCreateSessionDialog() {
        _uiState.update { it.copy(showCreateDialog = false) }
    }

    /**
     * 加载 Agents 和 Models 列表
     */
    private fun loadAgentsAndModels() {
        if (_uiState.value.connectionStatus !is ConnectionStatus.Connected) {
            return
        }
        viewModelScope.launch(exceptionHandler) {
            _uiState.update { it.copy(isLoadingAgentsModels = true) }
            try {
                // 并行加载 agents 和 models
                val agentsDeferred = async { gateway.agentsList() }
                val modelsDeferred = async { gateway.modelsList() }

                val agentsResponse = agentsDeferred.await()
                val modelsResponse = modelsDeferred.await()

                // 解析 Agents
                val agents = if (agentsResponse.isSuccess()) {
                    agentsResponse.payload?.jsonObject?.get("agents")?.jsonArray?.mapNotNull { element ->
                        try {
                            val obj = element.jsonObject
                            com.openclaw.clawchat.ui.components.AgentItem(
                                id = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                                name = obj["name"]?.jsonPrimitive?.content ?: "Unknown",
                                emoji = obj["emoji"]?.jsonPrimitive?.content,
                                avatar = obj["avatar"]?.jsonPrimitive?.content,
                                model = obj["model"]?.jsonPrimitive?.content,
                                description = obj["description"]?.jsonPrimitive?.content
                            )
                        } catch (e: Exception) {
                            null
                        }
                    } ?: emptyList()
                } else {
                    emptyList()
                }

                // 解析 Models
                val models = if (modelsResponse.isSuccess()) {
                    modelsResponse.payload?.jsonObject?.get("models")?.jsonArray?.mapNotNull { element ->
                        try {
                            val obj = element.jsonObject
                            com.openclaw.clawchat.ui.components.ModelItem(
                                id = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                                name = obj["name"]?.jsonPrimitive?.content ?: obj["id"]?.jsonPrimitive?.content ?: "Unknown",
                                provider = obj["provider"]?.jsonPrimitive?.content,
                                supportsVision = obj["supportsVision"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false,
                                description = obj["description"]?.jsonPrimitive?.content,
                                contextWindow = obj["contextWindow"]?.jsonPrimitive?.content?.toIntOrNull()
                            )
                        } catch (e: Exception) {
                            null
                        }
                    } ?: emptyList()
                } else {
                    emptyList()
                }

                _uiState.update {
                    it.copy(
                        agents = agents,
                        models = models,
                        isLoadingAgentsModels = false
                    )
                }

                AppLog.i(TAG, "Loaded ${agents.size} agents and ${models.size} models")
            } catch (e: Exception) {
                AppLog.w(TAG, "Failed to load agents/models: ${e.message}")
                _uiState.update { it.copy(isLoadingAgentsModels = false) }
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

    fun terminateSession(sessionId: String) {
        viewModelScope.launch(exceptionHandler) {
            try {
                // 先中止当前 run
                gateway.chatAbort(sessionId)
            } catch (e: Exception) {
                AppLog.w(TAG, "Gateway abort exception: ${e.message}")
            }

            // 更新 UI 状态
            _uiState.update {
                it.copy(
                    sessions = it.sessions.map { s ->
                        if (s.id == sessionId) s.copy(status = SessionStatus.TERMINATED)
                        else s
                    }
                )
            }
            _events.trySend(UiEvent.ShowSuccess("会话已终止"))
        }
    }
    fun clearError() { _uiState.update { it.copy(error = null) } }
    fun clearConnectionError() { _uiState.update { it.copy(connectionError = null) } }

    /**
     * 触发 Gateway 更新
     */
    fun runUpdate() {
        viewModelScope.launch {
            try {
                gateway.updateRun()
                _uiState.update { it.copy(updateAvailable = null) }
                _events.trySend(UiEvent.ShowSuccess("更新已触发，请稍候..."))
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "更新失败: ${e.message}") }
            }
        }
    }

    /**
     * 关闭更新通知
     */
    fun dismissUpdate() {
        _uiState.update { it.copy(updateAvailable = null) }
    }

    /**
     * 显示更新通知（从 update.available 事件）
     */
    fun showUpdate(version: String = "", message: String = "") {
        _uiState.update { it.copy(updateAvailable = UpdateInfo(version, message)) }
    }
    
    /**
     * 重试连接
     */
    fun retryConnection() {
        val currentConnectionState = gateway.connectionState.value
        // 只有在未连接且未在连接中时才重试
        if (currentConnectionState !is WebSocketConnectionState.Connected &&
            currentConnectionState !is WebSocketConnectionState.Connecting) {
            clearConnectionError()
            autoConnectIfNeeded()
        }
    }

    /**
     * 向会话发送引导消息
     */
    fun steerSession(sessionKey: String, text: String) {
        viewModelScope.launch(exceptionHandler) {
            if (_uiState.value.connectionStatus !is ConnectionStatus.Connected) {
                _events.trySend(UiEvent.ShowError("未连接到 Gateway"))
                return@launch
            }
            try {
                val response = gateway.sessionsSteer(sessionKey, text)
                if (response.isSuccess()) {
                    _events.trySend(UiEvent.ShowSuccess("引导消息已发送"))
                } else {
                    _events.trySend(UiEvent.ShowError(response.error?.message ?: "发送失败"))
                }
            } catch (e: Exception) {
                _events.trySend(UiEvent.ShowError("发送失败：${e.message}"))
            }
        }
    }

    /**
     * 重命名会话
     */
    fun renameSession(sessionId: String, newLabel: String) {
        viewModelScope.launch(exceptionHandler) {
            try {
                gateway.call("sessions.patch", mapOf(
                    "key" to JsonPrimitive(sessionId),
                    "label" to JsonPrimitive(newLabel)
                ))
                _uiState.update { state ->
                    state.copy(
                        sessions = state.sessions.map { session ->
                            if (session.id == sessionId) {
                                session.copy(label = newLabel)
                            } else session
                        }
                    )
                }
                _events.trySend(UiEvent.ShowSuccess("会话已重命名"))
            } catch (e: Exception) {
                _events.trySend(UiEvent.ShowError("重命名失败：${e.message}"))
            }
        }
    }

    /**
     * 切换会话置顶状态
     */
    fun toggleSessionPin(sessionId: String, currentPinned: Boolean) {
        viewModelScope.launch(exceptionHandler) {
            try {
                // Gateway 暂不支持 pin API，先本地更新
                _uiState.update { state ->
                    state.copy(
                        sessions = state.sessions.map { session ->
                            if (session.id == sessionId) {
                                session.copy(isPinned = !currentPinned)
                            } else session
                        }.sortedByDescending { it.isPinned }  // 置顶的排前面
                    )
                }
                _events.trySend(UiEvent.ShowSuccess(if (currentPinned) "已取消置顶" else "已置顶"))
            } catch (e: Exception) {
                _events.trySend(UiEvent.ShowError("操作失败：${e.message}"))
            }
        }
    }

    /**
     * 检查连接状态并在需要时重连（从后台返回前台时调用）
     */
    fun checkAndReconnectIfNeeded() {
        viewModelScope.launch {
            val currentConnectionState = gateway.connectionState.value
            val isPaired = encryptedStorage.isPaired()
            val gatewayUrl = encryptedStorage.getGatewayUrl()
            
            AppLog.d(TAG, "=== checkAndReconnectIfNeeded: state=$currentConnectionState, isPaired=$isPaired, url=$gatewayUrl")
            
            // 如果已配对、有 URL、且未连接/未在连接中，则重连
            if (isPaired && !gatewayUrl.isNullOrBlank() && 
                currentConnectionState !is WebSocketConnectionState.Connected &&
                currentConnectionState !is WebSocketConnectionState.Connecting) {
                AppLog.i(TAG, "App resumed, reconnecting to Gateway...")
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