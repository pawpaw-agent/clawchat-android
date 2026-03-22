package com.openclaw.clawchat.ui.screens.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openclaw.clawchat.data.FontSize
import com.openclaw.clawchat.data.UserPreferences
import com.openclaw.clawchat.network.WebSocketConnectionState
import com.openclaw.clawchat.network.protocol.GatewayConnection
import com.openclaw.clawchat.ui.state.ConnectionStatus
import com.openclaw.clawchat.ui.state.GatewayConfigInput
import com.openclaw.clawchat.ui.state.GatewayConfigUi
import com.openclaw.clawchat.ui.state.SettingsUiState
import com.openclaw.clawchat.ui.state.toUiStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val gateway: GatewayConnection,
    private val userPreferences: UserPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    companion object {
        private const val TAG = "SettingsViewModel"
    }

    init {
        loadCurrentConfig()
        observeConnectionState()
        observeFontSettings()
    }

    private fun loadCurrentConfig() {
        _uiState.update {
            it.copy(
                currentGateway = GatewayConfigUi(
                    id = "default", name = "未配置",
                    host = "", port = 18789
                ),
                gatewayConfigInput = GatewayConfigInput(
                    name = "", host = "", port = 18789
                )
            )
        }
    }

    private fun observeConnectionState() {
        viewModelScope.launch {
            gateway.connectionState.collect { connectionState ->
                val status = when (connectionState) {
                    is WebSocketConnectionState.Connected -> {
                        val latency = gateway.measureLatency() ?: 0L
                        ConnectionStatus.Connected(latency = latency)
                    }
                    is WebSocketConnectionState.Connecting -> ConnectionStatus.Connecting
                    is WebSocketConnectionState.Disconnecting -> ConnectionStatus.Disconnecting
                    is WebSocketConnectionState.Disconnected -> ConnectionStatus.Disconnected
                    is WebSocketConnectionState.Error -> ConnectionStatus.Error(
                        message = connectionState.throwable.message ?: "连接错误",
                        throwable = connectionState.throwable
                    )
                }
                _uiState.update { it.copy(connectionStatus = status.toUiStatus()) }
            }
        }
    }
    
    private fun observeFontSettings() {
        viewModelScope.launch {
            userPreferences.messageFontSize.collect { fontSize ->
                _uiState.update { it.copy(messageFontSize = fontSize) }
            }
        }
    }

    fun updateGatewayConfig(config: GatewayConfigInput) {
        _uiState.update {
            it.copy(
                gatewayConfigInput = config,
                currentGateway = GatewayConfigUi(
                    id = "default",
                    name = config.name.ifEmpty { "默认 Gateway" },
                    host = config.host, port = config.port,
                    isCurrent = true
                )
            )
        }
        Log.d(TAG, "Gateway 配置已更新：${config.host}:${config.port}")
    }

    fun disconnect() {
        viewModelScope.launch {
            gateway.disconnect()
            Log.d(TAG, "已断开连接")
        }
    }

    fun toggleNotifications(enabled: Boolean) {
        _uiState.update { it.copy(notificationsEnabled = enabled) }
    }

    fun toggleDnd(enabled: Boolean) {
        _uiState.update { it.copy(dndEnabled = enabled) }
    }
    
    fun setMessageFontSize(fontSize: FontSize) {
        viewModelScope.launch {
            userPreferences.setMessageFontSize(fontSize)
        }
    }
}