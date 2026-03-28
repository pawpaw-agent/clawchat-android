package com.openclaw.clawchat.ui.screens.settings

import android.util.Log
import com.openclaw.clawchat.util.AppLog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openclaw.clawchat.data.FontSize
import com.openclaw.clawchat.data.ThemeMode
import com.openclaw.clawchat.data.UserPreferences
import com.openclaw.clawchat.network.WebSocketConnectionState
import com.openclaw.clawchat.network.protocol.GatewayConnection
import com.openclaw.clawchat.security.EncryptedStorage
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
    private val userPreferences: UserPreferences,
    private val encryptedStorage: EncryptedStorage
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
        observeThemeSettings()
        checkPairedState()
    }

    private fun loadCurrentConfig() {
        val gatewayUrl = encryptedStorage.getGatewayUrl() ?: ""
        val gatewayName = encryptedStorage.getGatewayName() ?: "Gateway"
        _uiState.update {
            it.copy(
                currentGateway = GatewayConfigUi(
                    id = "default", 
                    name = gatewayName,
                    host = gatewayUrl,
                    port = 18789
                ),
                gatewayConfigInput = GatewayConfigInput(
                    name = gatewayName, 
                    host = gatewayUrl, 
                    port = 18789
                )
            )
        }
    }

    private fun checkPairedState() {
        _uiState.update { it.copy(isPaired = encryptedStorage.isPaired()) }
    }
    
    fun refreshConnectionState() {
        checkPairedState()
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

    private fun observeThemeSettings() {
        viewModelScope.launch {
            userPreferences.themeMode.collect { themeMode ->
                _uiState.update { it.copy(themeMode = themeMode) }
            }
        }
    }

    fun updateGatewayConfig(config: GatewayConfigInput) {
        // 保存 Gateway URL 和名称
        val url = "ws://${config.host}:${config.port}/ws"
        encryptedStorage.saveGatewayUrl(url)
        val name = config.name.ifEmpty { "Gateway" }
        encryptedStorage.saveGatewayName(name)
        
        _uiState.update {
            it.copy(
                gatewayConfigInput = config,
                currentGateway = GatewayConfigUi(
                    id = "default",
                    name = name,
                    host = config.host, port = config.port,
                    isCurrent = true
                )
            )
        }
        AppLog.d(TAG, "Gateway 配置已更新：${config.host}:${config.port}")
        
        // 如果已配对，自动连接
        if (encryptedStorage.isPaired()) {
            val token = encryptedStorage.getDeviceToken()
            viewModelScope.launch {
                AppLog.d(TAG, "自动连接到 Gateway: $url")
                gateway.connect(url, token)
            }
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            gateway.disconnect()
            AppLog.d(TAG, "已断开连接")
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

    fun setThemeMode(themeMode: ThemeMode) {
        viewModelScope.launch {
            userPreferences.setThemeMode(themeMode)
        }
    }
}
