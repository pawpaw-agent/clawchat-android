package com.openclaw.clawchat.ui.state

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openclaw.clawchat.network.GatewayUrlUtil
import com.openclaw.clawchat.network.protocol.GatewayConnection
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

sealed class PairingEvent {
    data object PairingSuccess : PairingEvent()
    data object PairingTimeout : PairingEvent()
    data object PairingRejected : PairingEvent()
    data class PairingError(val message: String) : PairingEvent()
}

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
 * 使用 GatewayConnection（Ed25519 签名 + Protocol v3 握手）
 */
@HiltViewModel
class PairingViewModel @Inject constructor(
    private val securityModule: SecurityModule,
    private val gateway: GatewayConnection
) : ViewModel() {

    companion object {
        private const val TAG = "PairingViewModel"
    }

    private val _state = MutableStateFlow(PairingState())
    val state: StateFlow<PairingState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<PairingEvent>(extraBufferCapacity = 10)
    val events: SharedFlow<PairingEvent> = _events.asSharedFlow()

    fun setConnectMode(mode: ConnectMode) {
        _state.value = _state.value.copy(connectMode = mode)
    }

    fun setGatewayUrl(url: String) {
        _state.value = _state.value.copy(gatewayUrl = url)
    }

    fun setToken(token: String) {
        _state.value = _state.value.copy(token = token)
    }

    /**
     * Token 直连
     *
     * GatewayConnection.connect(url, token) 内部完成：
     * 1. Ed25519 密钥初始化
     * 2. challenge-response 签名
     * 3. hello-ok 接收
     */
    fun connectWithToken() {
        val url = _state.value.gatewayUrl.trim()
        val token = _state.value.token.trim()

        if (url.isEmpty()) { emitError("请输入 Gateway 地址"); return }
        if (token.isEmpty()) { emitError("请输入 Token"); return }

        viewModelScope.launch {
            _state.value = _state.value.copy(isPairing = true, status = PairingStatus.Initializing)
            try {
                securityModule.initialize()

                val wsUrl = GatewayUrlUtil.normalizeToWebSocketUrl(url)
                securityModule.saveGatewayConfig(wsUrl)

                val result = gateway.connect(wsUrl, token)

                result.onSuccess {
                    _state.value = _state.value.copy(isPairing = false, status = PairingStatus.Approved)
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
            if (url.isEmpty()) { emitError("请输入 Gateway 地址"); return@launch }

            _state.value = _state.value.copy(isPairing = true)
            try {
                val wsUrl = GatewayUrlUtil.normalizeToWebSocketUrl(url)
                securityModule.saveGatewayConfig(wsUrl)

                val result = gateway.connect(wsUrl, token = null)

                result.onSuccess {
                    _state.value = _state.value.copy(
                        isPairing = false,
                        status = PairingStatus.WaitingForApproval
                    )
                }

                result.onFailure { e ->
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
            gateway.disconnect()
            _state.value = _state.value.copy(isPairing = false, status = PairingStatus.Initializing)
        }
    }

    fun consumeEvent() {}

    private fun emitError(message: String) {
        viewModelScope.launch { _events.emit(PairingEvent.PairingError(message)) }
    }
}
