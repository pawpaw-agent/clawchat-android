package com.openclaw.clawchat.ui.screens.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openclaw.clawchat.network.WebSocketService
import com.openclaw.clawchat.ui.state.ConnectionStatus
import com.openclaw.clawchat.ui.state.ConnectionStatusUi
import com.openclaw.clawchat.ui.state.GatewayConfigUi
import com.openclaw.clawchat.ui.state.toUiStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 设置页面 ViewModel
 * 
 * 负责管理：
 * - Gateway 配置
 * - 通知设置
 * - 连接管理
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val webSocketService: WebSocketService
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    companion object {
        private const val TAG = "SettingsViewModel"
        private const val APP_VERSION = "1.0.0"
    }

    init {
        loadCurrentConfig()
        observeConnectionState()
    }

    /**
     * 加载当前 Gateway 配置
     */
    private fun loadCurrentConfig() {
        // TODO: 从 EncryptedStorage 加载配置
        _uiState.update {
            it.copy(
                currentGateway = GatewayConfigUi(
                    id = "default",
                    name = "未配置",
                    host = "",
                    port = 18789,
                    useTls = false
                ),
                gatewayConfigInput = GatewayConfigInput(
                    name = "",
                    host = "",
                    port = 18789,
                    useTls = false
                )
            )
        }
    }

    /**
     * 观察 WebSocket 连接状态
     */
    private fun observeConnectionState() {
        viewModelScope.launch {
            webSocketService.connectionState.collect { connectionState ->
                val connectionStatus = when (connectionState) {
                    is com.openclaw.clawchat.network.WebSocketConnectionState.Connected -> {
                        val latency = webSocketService.measureLatency() ?: 0L
                        ConnectionStatus.Connected(latency = latency)
                    }
                    is com.openclaw.clawchat.network.WebSocketConnectionState.Connecting -> {
                        ConnectionStatus.Connecting
                    }
                    is com.openclaw.clawchat.network.WebSocketConnectionState.Disconnecting -> {
                        ConnectionStatus.Disconnecting
                    }
                    is com.openclaw.clawchat.network.WebSocketConnectionState.Disconnected -> {
                        ConnectionStatus.Disconnected
                    }
                    is com.openclaw.clawchat.network.WebSocketConnectionState.Error -> {
                        ConnectionStatus.Error(
                            message = connectionState.throwable.message ?: "连接错误",
                            throwable = connectionState.throwable
                        )
                    }
                }

                _uiState.update { it.copy(connectionStatus = connectionStatus.toUiStatus()) }
            }
        }
    }

    /**
     * 更新 Gateway 配置
     */
    fun updateGatewayConfig(config: GatewayConfigInput) {
        _uiState.update {
            it.copy(
                gatewayConfigInput = config,
                currentGateway = GatewayConfigUi(
                    id = "default",
                    name = config.name.ifEmpty { "默认 Gateway" },
                    host = config.host,
                    port = config.port,
                    useTls = config.useTls,
                    isCurrent = true
                )
            )
        }

        // TODO: 保存到 EncryptedStorage

        Log.d(TAG, "Gateway 配置已更新：${config.host}:${config.port}")
    }

    /**
     * 断开连接
     */
    fun disconnect() {
        viewModelScope.launch {
            webSocketService.disconnect()
            Log.d(TAG, "已断开连接")
        }
    }

    /**
     * 切换通知设置
     */
    fun toggleNotifications(enabled: Boolean) {
        _uiState.update { it.copy(notificationsEnabled = enabled) }
        // TODO: 保存到 Preferences
    }

    /**
     * 切换勿扰模式
     */
    fun toggleDnd(enabled: Boolean) {
        _uiState.update { it.copy(dndEnabled = enabled) }
        // TODO: 保存到 Preferences
    }
}
