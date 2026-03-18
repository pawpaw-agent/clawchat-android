package com.openclaw.clawchat.ui.state

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openclaw.clawchat.network.GatewayUrlUtil
import com.openclaw.clawchat.network.WebSocketService
import com.openclaw.clawchat.security.SecurityModule
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 配对事件
 */
sealed class PairingEvent {
    data object PairingSuccess : PairingEvent()
    data object PairingTimeout : PairingEvent()
    data object PairingRejected : PairingEvent()
    data class PairingError(val message: String) : PairingEvent()
}

/**
 * 配对状态数据
 */
data class PairingState(
    val deviceId: String? = null,
    val publicKey: String? = null,
    val gatewayUrl: String = "",
    val status: PairingStatus = PairingStatus.Initializing,
    val isInitializing: Boolean = false,
    val isPairing: Boolean = false,
    val connectMode: ConnectMode = ConnectMode.TOKEN,
    val token: String = ""
)

/**
 * 配对 ViewModel
 *
 * 管理两种连接模式：
 * 1. Token 直连：输入 Gateway 地址 + Token → WebSocket 连接
 * 2. 设备配对：Ed25519 签名 + 管理员批准
 */
@HiltViewModel
class PairingViewModel @Inject constructor(
    private val securityModule: SecurityModule,
    private val webSocketService: WebSocketService
) : ViewModel() {

    companion object {
        private const val TAG = "PairingViewModel"
    }

    private val _state = MutableStateFlow(PairingState())
    val state: StateFlow<PairingState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<PairingEvent>(extraBufferCapacity = 10)
    val events: SharedFlow<PairingEvent> = _events.asSharedFlow()

    // ==================== 模式切换 ====================

    fun setConnectMode(mode: ConnectMode) {
        _state.value = _state.value.copy(connectMode = mode)
    }

    // ==================== 共用 ====================

    fun setGatewayUrl(url: String) {
        _state.value = _state.value.copy(gatewayUrl = url)
    }

    fun setToken(token: String) {
        _state.value = _state.value.copy(token = token)
    }

    // ==================== Token 模式 ====================

    /**
     * Token 直连
     *
     * 1. 初始化设备密钥（Ed25519 签名仍然需要）
     * 2. 通过 WebSocketService 建立真实连接
     * 3. connect params 中带 auth.token
     */
    fun connectWithToken() {
        val currentState = _state.value
        val url = currentState.gatewayUrl.trim()
        val token = currentState.token.trim()

        if (url.isEmpty()) {
            emitError("请输入 Gateway 地址")
            return
        }
        if (token.isEmpty()) {
            emitError("请输入 Token")
            return
        }

        viewModelScope.launch {
            _state.value = _state.value.copy(isPairing = true, status = PairingStatus.Initializing)
            try {
                // 初始化设备密钥
                securityModule.initialize()

                val wsUrl = GatewayUrlUtil.normalizeToWebSocketUrl(url)
                securityModule.saveGatewayConfig(wsUrl)

                Log.i(TAG, "Token 直连: $wsUrl")

                // 通过 WebSocketService 建立真实连接
                val result = webSocketService.connect(wsUrl, token)

                result.onSuccess {
                    Log.i(TAG, "WebSocket connect() 调用成功，等待 challenge-response 完成...")
                    // 连接状态由 WebSocketService.connectionState 驱动
                    // MainViewModel 会自动观察到 Connected 状态
                    _state.value = _state.value.copy(
                        isPairing = false,
                        status = PairingStatus.Approved
                    )
                    _events.emit(PairingEvent.PairingSuccess)
                }

                result.onFailure { e ->
                    Log.e(TAG, "Token 连接失败", e)
                    _state.value = _state.value.copy(
                        isPairing = false,
                        status = PairingStatus.Error(e.message ?: "连接失败")
                    )
                    _events.emit(PairingEvent.PairingError(e.message ?: "连接失败"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Token 连接异常", e)
                _state.value = _state.value.copy(
                    isPairing = false,
                    status = PairingStatus.Error(e.message ?: "连接失败")
                )
                _events.emit(PairingEvent.PairingError(e.message ?: "连接失败"))
            }
        }
    }

    // ==================== 配对模式 ====================

    fun initializePairing() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isInitializing = true)
            try {
                val status = securityModule.initialize()
                val deviceId = status.deviceId
                    ?: securityModule.getSecurityStatus().deviceId ?: "unknown"
                val publicKey = securityModule.getPublicKeyBase64Url()

                _state.value = _state.value.copy(
                    deviceId = deviceId,
                    publicKey = publicKey,
                    isInitializing = false,
                    status = PairingStatus.WaitingForApproval
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isInitializing = false,
                    status = PairingStatus.Error(e.message ?: "初始化失败")
                )
                _events.emit(PairingEvent.PairingError(e.message ?: "初始化失败"))
            }
        }
    }

    fun startPairing() {
        viewModelScope.launch {
            val url = _state.value.gatewayUrl.trim()
            if (url.isEmpty()) {
                emitError("请输入 Gateway 地址")
                return@launch
            }

            _state.value = _state.value.copy(isPairing = true)
            try {
                val wsUrl = GatewayUrlUtil.normalizeToWebSocketUrl(url)
                securityModule.saveGatewayConfig(wsUrl)

                // 通过 WebSocketService 建立连接（配对模式不带 token）
                val result = webSocketService.connect(wsUrl, token = null)

                result.onSuccess {
                    Log.i(TAG, "配对模式 WebSocket 已连接，等待管理员批准...")
                    _state.value = _state.value.copy(
                        isPairing = false,
                        status = PairingStatus.WaitingForApproval
                    )
                }

                result.onFailure { e ->
                    Log.e(TAG, "配对连接失败", e)
                    _state.value = _state.value.copy(
                        isPairing = false,
                        status = PairingStatus.Error(e.message ?: "配对失败")
                    )
                    _events.emit(PairingEvent.PairingError(e.message ?: "配对失败"))
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isPairing = false,
                    status = PairingStatus.Error(e.message ?: "配对失败")
                )
                _events.emit(PairingEvent.PairingError(e.message ?: "配对失败"))
            }
        }
    }

    fun cancelPairing() {
        viewModelScope.launch {
            webSocketService.disconnect()
            _state.value = _state.value.copy(
                isPairing = false,
                status = PairingStatus.Initializing
            )
        }
    }

    fun consumeEvent() {}

    // ==================== 内部方法 ====================

    private fun emitError(message: String) {
        viewModelScope.launch {
            _events.emit(PairingEvent.PairingError(message))
        }
    }
}
