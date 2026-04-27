package com.openclaw.clawchat.ui.state

import com.openclaw.clawchat.util.AppLog
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
import kotlinx.coroutines.flow.update
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
    val gatewayName: String = "",
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

    private fun loadSavedConfig() {
        val savedUrl = securityModule.getGatewayUrl()
        if (!savedUrl.isNullOrBlank()) {
            val displayUrl = GatewayUrlUtil.extractDisplayAddress(savedUrl)
            _state.update { it.copy(gatewayUrl = displayUrl) }
        }
        val savedName = securityModule.getGatewayName()
        if (!savedName.isNullOrBlank()) {
            _state.update { it.copy(gatewayName = savedName) }
        }
        val savedToken = securityModule.getGatewayAuthToken()
        if (!savedToken.isNullOrBlank()) {
            _state.update { it.copy(token = savedToken) }
        }
    }

    private fun observeCertificateEvents() {
        viewModelScope.launch {
            gateway.certificateEvent.collect { event ->
                AppLog.i(TAG, "Certificate event received: ${event.hostname}, mismatch=${event.isMismatch}")
                _state.update { it.copy(certificateEvent = event) }
            }
        }
    }

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
                    AppLog.w(TAG, "Failed to parse pairing event: ${e.message}")
                }
            }
        }
    }

    private fun handlePairingEvent(event: String, obj: kotlinx.serialization.json.JsonObject) {
        when (event) {
            "device.pairing.approved" -> {
                AppLog.i(TAG, "Pairing approved by gateway")
                _state.update { it.copy(isPairing = false, status = PairingStatus.Approved) }
                viewModelScope.launch {
                    _events.emit(PairingEvent.PairingSuccess)
                }
            }
            "device.pairing.rejected" -> {
                val payload = obj["payload"]?.jsonObject
                val reason = payload?.get("reason")?.jsonPrimitive?.content ?: "Unknown reason"
                AppLog.w(TAG, "Pairing rejected: $reason")
                _state.update { it.copy(isPairing = false, status = PairingStatus.Rejected) }
                viewModelScope.launch {
                    _events.emit(PairingEvent.PairingRejected)
                }
            }
        }
    }

    fun setGatewayUrl(url: String) {
        _state.update { it.copy(gatewayUrl = url) }
    }

    fun setGatewayName(name: String) {
        _state.update { it.copy(gatewayName = name) }
    }

    fun setConnectMode(mode: ConnectMode) {
        _state.update { it.copy(connectMode = mode) }
    }

    fun setToken(token: String) {
        _state.update { it.copy(token = token) }
    }

    fun connectWithToken() {
        val url = _state.value.gatewayUrl.trim()
        val token = _state.value.token.trim()

        if (url.isEmpty()) { emitError("请输入 Gateway 地址"); return }
        if (token.isEmpty()) { emitError("请输入 Token"); return }

        viewModelScope.launch {
            _state.update { it.copy(isPairing = true, status = PairingStatus.Initializing) }
            try {
                val wsUrl = GatewayUrlUtil.normalizeToWebSocketUrl(url)
                securityModule.saveGatewayConfig(wsUrl)

                val result = gateway.connect(wsUrl, token)

                result.onSuccess {
                    securityModule.saveGatewayAuthToken(token)
                    val name = _state.value.gatewayName.ifBlank { "Gateway" }
                    securityModule.saveGatewayName(name)
                    _state.update { it.copy(isPairing = false, status = PairingStatus.Approved) }
                    _events.emit(PairingEvent.PairingSuccess)
                }

                result.onFailure { e ->
                    AppLog.e(TAG, "Token 连接失败", e)
                    _state.update { it.copy(isPairing = false, status = PairingStatus.Error(e.message ?: "连接失败")) }
                    _events.emit(PairingEvent.PairingError(e.message ?: "连接失败"))
                }
            } catch (e: Exception) {
                AppLog.e(TAG, "Token 连接异常", e)
                _state.update { it.copy(isPairing = false, status = PairingStatus.Error(e.message ?: "连接失败")) }
                _events.emit(PairingEvent.PairingError(e.message ?: "连接失败"))
            }
        }
    }

    fun initializePairing() {
        viewModelScope.launch {
            _state.update { it.copy(isInitializing = true) }
            try {
                val status = securityModule.initialize()
                val deviceId = status.deviceId
                    ?: securityModule.getSecurityStatus().deviceId ?: "unknown"
                val publicKey = securityModule.getPublicKeyBase64Url()

                _state.update { it.copy(
                    deviceId = deviceId,
                    publicKey = publicKey,
                    isInitializing = false,
                    status = PairingStatus.WaitingForApproval
                ) }
            } catch (e: Exception) {
                _state.update { it.copy(isInitializing = false, status = PairingStatus.Error(e.message ?: "初始化失败")) }
                _events.emit(PairingEvent.PairingError(e.message ?: "初始化失败"))
            }
        }
    }

    fun startPairing() {
        viewModelScope.launch {
            val url = _state.value.gatewayUrl.trim()
            if (url.isEmpty()) { emitError("请输入 Gateway 地址"); return@launch }

            _state.update { it.copy(isPairing = true) }
            try {
                val wsUrl = GatewayUrlUtil.normalizeToWebSocketUrl(url)
                securityModule.saveGatewayConfig(wsUrl)

                val result = gateway.connect(wsUrl, token = null)

                result.onSuccess {
                    _state.update { it.copy(isPairing = false, status = PairingStatus.WaitingForApproval) }
                }

                result.onFailure { e ->
                    _state.update { it.copy(isPairing = false, status = PairingStatus.Error(e.message ?: "配对失败")) }
                    _events.emit(PairingEvent.PairingError(e.message ?: "配对失败"))
                }
            } catch (e: Exception) {
                _state.update { it.copy(isPairing = false, status = PairingStatus.Error(e.message ?: "配对失败")) }
                _events.emit(PairingEvent.PairingError(e.message ?: "配对失败"))
            }
        }
    }

    fun cancelPairing() {
        viewModelScope.launch {
            gateway.disconnect()
            _state.update { it.copy(isPairing = false, status = PairingStatus.Initializing) }
        }
    }

    fun confirmCertificateTrust() {
        val event = _state.value.certificateEvent ?: return

        viewModelScope.launch {
            try {
                fingerprintManager.trustCertificate(
                    gatewayId = event.hostname,
                    fingerprint = event.fingerprint,
                    userVerified = true
                )
                AppLog.i(TAG, "User confirmed certificate trust for ${event.hostname}")

                _state.update { it.copy(certificateEvent = null) }

                when (_state.value.connectMode) {
                    ConnectMode.TOKEN -> connectWithToken()
                    ConnectMode.PAIRING -> startPairing()
                }
            } catch (e: Exception) {
                AppLog.e(TAG, "Failed to save certificate trust", e)
                _state.update { it.copy(
                    certificateEvent = null,
                    status = PairingStatus.Error("保存证书信任失败：${e.message}")
                ) }
            }
        }
    }

    fun rejectCertificate() {
        val event = _state.value.certificateEvent ?: return

        viewModelScope.launch {
            AppLog.w(TAG, "User rejected certificate for ${event.hostname}")

            _state.update { it.copy(certificateEvent = null) }

            gateway.disconnect()
            _state.update { it.copy(
                isPairing = false,
                status = PairingStatus.Error("用户拒绝证书：${event.hostname}")
            ) }
            _events.emit(PairingEvent.PairingError("证书不被信任"))
        }
    }

    private fun emitError(message: String) {
        viewModelScope.launch { _events.emit(PairingEvent.PairingError(message)) }
    }
}
