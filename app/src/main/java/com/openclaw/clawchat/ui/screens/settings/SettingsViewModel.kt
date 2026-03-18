package com.openclaw.clawchat.ui.screens.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openclaw.clawchat.network.WebSocketService
import com.openclaw.clawchat.ui.state.GatewayConfigUi
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
                        ConnectionStatusUi.Connected(latency = latency)
                    }
                    is com.openclaw.clawchat.network.WebSocketConnectionState.Connecting -> {
                        ConnectionStatusUi.Connecting
                    }
                    is com.openclaw.clawchat.network.WebSocketConnectionState.Disconnecting -> {
                        ConnectionStatusUi.Disconnecting
                    }
                    is com.openclaw.clawchat.network.WebSocketConnectionState.Disconnected -> {
                        ConnectionStatusUi.Disconnected
                    }
                    is com.openclaw.clawchat.network.WebSocketConnectionState.Error -> {
                        ConnectionStatusUi.Error(
                            message = connectionState.throwable.message ?: "连接错误"
                        )
                    }
                }

                _uiState.update { it.copy(connectionStatus = connectionStatus) }
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

/**
 * 设置页面 UI 状态
 */
data class SettingsUiState(
    val currentGateway: GatewayConfigUi? = null,
    val gatewayConfigInput: GatewayConfigInput = GatewayConfigInput(),
    val connectionStatus: ConnectionStatusUi = ConnectionStatusUi.Disconnected,
    val notificationsEnabled: Boolean = true,
    val dndEnabled: Boolean = false,
    val appVersion: String = APP_VERSION
)

/**
 * 连接状态（UI 用 - Settings 特定）
 */

/**
 * Gateway 配置（输入用）
 */
data class GatewayConfigInput(
    val name: String = "",
    val host: String = "",
    val port: Int = 18789,
    val useTls: Boolean = false
)

/**
 * 连接状态（UI 用）
 */
sealed class ConnectionStatusUi {
    object Disconnected : ConnectionStatusUi()
    object Connecting : ConnectionStatusUi()
    object Disconnecting : ConnectionStatusUi()
    data class Connected(val latency: Long = 0) : ConnectionStatusUi()
    data class Error(val message: String) : ConnectionStatusUi()

    val isConnected: Boolean
        get() = this is Connected

    val displayText: String
        get() = when (this) {
            is Disconnected -> "未连接"
            is Connecting -> "连接中..."
            is Disconnecting -> "断开中..."
            is Connected -> if (latency > 0) "已连接 · ${latency}ms" else "已连接"
            is Error -> "错误：$message"
        }

    val statusColor: androidx.compose.ui.graphics.Color
        @Composable
        get() = when (this) {
            is Disconnected -> androidx.compose.ui.graphics.Color.Gray
            is Connecting -> androidx.compose.ui.graphics.Color.Yellow
            is Disconnecting -> androidx.compose.ui.graphics.Color.Gray
            is Connected -> androidx.compose.ui.graphics.Color.Green
            is Error -> androidx.compose.ui.graphics.Color.Red
        }
}
