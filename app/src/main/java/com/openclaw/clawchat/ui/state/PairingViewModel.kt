package com.openclaw.clawchat.ui.state

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openclaw.clawchat.network.GatewayUrlUtil
import com.openclaw.clawchat.network.protocol.CertificateEvent
import com.openclaw.clawchat.network.protocol.GatewayConnection
import com.openclaw.clawchat.security.CertificateFingerprintManager
import com.openclaw.clawchat.security.SecurityModule
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import com.openclaw.clawchat.util.JsonUtils
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
    val token: String = "",
    val certificateEvent: CertificateEvent? = null
)

enum class ConnectMode {
    TOKEN,
    PAIRING
}

/**
 * 配对 ViewModel
 *
 * 使用 GatewayConnection（Ed25519 签名 + Protocol v3 握手）
 */
@HiltViewModel
class PairingViewModel @Inject constructor(
    private val securityModule: SecurityModule,
    private val gateway: GatewayConnection,
    private val fingerprintManager: CertificateFingerprintManager
) : ViewModel() {

    companion object {
        private const val TAG = "PairingViewModel"
    }

    private val _state = MutableStateFlow(PairingState())
    val state: StateFlow<PairingState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<PairingEvent>(extraBufferCapacity = 10)
    val events: SharedFlow<PairingEvent> = _events.asSharedFlow()

    init {
        loadSavedConfig()
        observePairingEvents()
        observeCertificateEvents()
    }

    /**
     * 加载保存的 Gateway 配置
     */
    private fun loadSavedConfig() {
        val savedUrl = securityModule.getGatewayUrl()
        if (!savedUrl.isNullOrBlank()) {
            // 提取显示用的地址（不含协议和路径）
            val displayUrl = GatewayUrlUtil.extractDisplayAddress(savedUrl)
            _state.value = _state.value.copy(gatewayUrl = displayUrl)
        }
        // 加载保存的 token
        val savedToken = securityModule.getGatewayAuthToken()
        if (!savedToken.isNullOrBlank()) {
            _state.value = _state.value.copy(token = savedToken)
        }
    }

    /**
     * 监听证书事件（TOFU 流程）
     */
    private fun observeCertificateEvents() {
        viewModelScope.launch {
            gateway.certificateEvent.collect { event ->
                Log.i(TAG, "Certificate event received: ${event.hostname}, mismatch=${event.isMismatch}")
                _state.value = _state.value.copy(certificateEvent = event)
            }
        }
    }

    /**
     * 监听 device.pairing.approved/rejected 事件
     */
    private fun observePairingEvents() {
        viewModelScope.launch {
            gateway.incomingMessages.collect { rawJson ->
                try {
                    val json = JsonUtils.json
                    val obj = json.parseToJsonElement(rawJson).jsonObject
                    
                    val type = obj["type"]?.jsonPrimitive?.content
                    val event = obj["event"]?.jsonPrimitive?.content
                    
                    if (type == "event" && event?.startsWith("device.pairing.") == true) {
                        handlePairingEvent(event, obj)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse pairing event: ${e.message}")
                }
            }
        }
    }

    /**
     * 处理配对事件
     */
    private fun handlePairingEvent(event: String, obj: kotlinx.serialization.json.JsonObject) {
        when (event) {
            "device.pairing.approved" -> {
                Log.i(TAG, "Pairing approved by gateway")
                _state.value = _state.value.copy(
                    isPairing = false,
                    status = PairingStatus.Approved
                )
                viewModelScope.launch {
                    _events.emit(PairingEvent.PairingSuccess)
                }
            }
            "device.pairing.rejected" -> {
                val payload = obj["payload"]?.jsonObject
                val reason = payload?.get("reason")?.jsonPrimitive?.content ?: "Unknown reason"
                Log.w(TAG, "Pairing rejected: $reason")
                _state.value = _state.value.copy(
                    isPairing = false,
                    status = PairingStatus.Rejected
                )
                viewModelScope.launch {
                    _events.emit(PairingEvent.PairingRejected)
                }
            }
        }
    }

    fun setGatewayUrl(url: String) {
        _state.value = _state.value.copy(gatewayUrl = url)
    }

    fun setConnectMode(mode: ConnectMode) {
        _state.value = _state.value.copy(connectMode = mode)
    }

    fun setToken(token: String) {
        _state.value = _state.value.copy(token = token)
    }

    /**
     * Token 直连
     */
    fun connectWithToken() {
        val url = _state.value.gatewayUrl.trim()
        val token = _state.value.token.trim()

        if (url.isEmpty()) { emitError("请输入 Gateway 地址"); return }
        if (token.isEmpty()) { emitError("请输入 Token"); return }

        viewModelScope.launch {
            _state.value = _state.value.copy(isPairing = true, status = PairingStatus.Initializing)
            try {
                val wsUrl = GatewayUrlUtil.normalizeToWebSocketUrl(url)
                securityModule.saveGatewayConfig(wsUrl)

                val result = gateway.connect(wsUrl, token)

                result.onSuccess {
                    securityModule.saveGatewayAuthToken(token)
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

    /**
     * 用户确认信任证书
     */
    fun confirmCertificateTrust() {
        val event = _state.value.certificateEvent ?: return

        viewModelScope.launch {
            try {
                // 保存用户信任的证书指纹
                fingerprintManager.trustCertificate(
                    gatewayId = event.hostname,
                    fingerprint = event.fingerprint,
                    userVerified = true
                )
                Log.i(TAG, "User confirmed certificate trust for ${event.hostname}")

                // 清除证书事件，继续连接
                _state.value = _state.value.copy(certificateEvent = null)

                // 重试连接
                when (_state.value.connectMode) {
                    ConnectMode.TOKEN -> connectWithToken()
                    ConnectMode.PAIRING -> startPairing()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save certificate trust", e)
                _state.value = _state.value.copy(
                    certificateEvent = null,
                    status = PairingStatus.Error("保存证书信任失败：${e.message}")
                )
            }
        }
    }

    /**
     * 用户拒绝证书
     */
    fun rejectCertificate() {
        val event = _state.value.certificateEvent ?: return

        viewModelScope.launch {
            Log.w(TAG, "User rejected certificate for ${event.hostname}")

            // 清除证书事件
            _state.value = _state.value.copy(certificateEvent = null)

            // 断开连接并返回错误状态
            gateway.disconnect()
            _state.value = _state.value.copy(
                isPairing = false,
                status = PairingStatus.Error("用户拒绝证书：${event.hostname}")
            )
            _events.emit(PairingEvent.PairingError("证书不被信任"))
        }
    }

    private fun emitError(message: String) {
        viewModelScope.launch { _events.emit(PairingEvent.PairingError(message)) }
    }
}
