package com.openclaw.clawchat.ui.state

import android.util.Base64
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openclaw.clawchat.network.GatewayUrlUtil
import com.openclaw.clawchat.security.SecurityModule
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
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
 * 配对状态数据（内部用，兼容旧 PairingScreen 引用）
 */
data class PairingState(
    val deviceId: String? = null,
    val publicKey: String? = null,
    val gatewayUrl: String = "",
    val status: PairingStatus = PairingStatus.Initializing,
    val isInitializing: Boolean = false,
    val isPairing: Boolean = false,
    // 新增：连接模式
    val connectMode: ConnectMode = ConnectMode.TOKEN,
    val token: String = "",
    val setupCode: String = "",
    val setupCodeParsed: SetupCodeInfo? = null,
    val setupCodeError: String? = null
)

/**
 * 配对 ViewModel
 *
 * 管理三种连接模式：
 * 1. Token 直连：输入 Gateway 地址 + Token → 直接连接
 * 2. 设备配对：Ed25519 签名 + 管理员批准
 * 3. Setup Code：粘贴 base64 配对码 → 解析 url + bootstrapToken → 连接
 */
@HiltViewModel
class PairingViewModel @Inject constructor(
    private val securityModule: SecurityModule
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

    // ==================== Token 模式 ====================

    fun setToken(token: String) {
        _state.value = _state.value.copy(token = token)
    }

    /**
     * Token 直连
     *
     * 使用 Gateway 地址 + Token 直接建立连接。
     * connect params 中带 auth.token，设备签名仍然需要。
     */
    fun connectWithToken() {
        val currentState = _state.value
        val url = currentState.gatewayUrl.trim()
        val token = currentState.token.trim()

        if (url.isEmpty()) {
            viewModelScope.launch {
                _events.emit(PairingEvent.PairingError("请输入 Gateway 地址"))
            }
            return
        }
        if (token.isEmpty()) {
            viewModelScope.launch {
                _events.emit(PairingEvent.PairingError("请输入 Token"))
            }
            return
        }

        viewModelScope.launch {
            _state.value = _state.value.copy(isPairing = true, status = PairingStatus.Initializing)
            try {
                // 初始化设备密钥（Token 模式也需要设备签名）
                securityModule.initialize()

                val wsUrl = GatewayUrlUtil.normalizeToWebSocketUrl(url)
                securityModule.saveGatewayConfig(wsUrl)

                Log.i(TAG, "Token 直连: $wsUrl")

                // TODO: 实际 WebSocket 连接，connect params 带 auth.token
                // 目前标记为成功，等 WebSocketService 集成
                _state.value = _state.value.copy(
                    isPairing = false,
                    status = PairingStatus.Approved
                )
                _events.emit(PairingEvent.PairingSuccess)
            } catch (e: Exception) {
                Log.e(TAG, "Token 连接失败", e)
                _state.value = _state.value.copy(
                    isPairing = false,
                    status = PairingStatus.Error(e.message ?: "连接失败")
                )
                _events.emit(PairingEvent.PairingError(e.message ?: "连接失败"))
            }
        }
    }

    // ==================== 配对模式（已有逻辑） ====================

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

    fun setGatewayUrl(url: String) {
        _state.value = _state.value.copy(gatewayUrl = url)
    }

    fun startPairing() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isPairing = true)
            try {
                val wsUrl = GatewayUrlUtil.normalizeToWebSocketUrl(_state.value.gatewayUrl)
                securityModule.saveGatewayConfig(wsUrl)

                // TODO: 实际配对流程
                kotlinx.coroutines.delay(2000)

                _state.value = _state.value.copy(
                    isPairing = false,
                    status = PairingStatus.Approved
                )
                _events.emit(PairingEvent.PairingSuccess)
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
        _state.value = _state.value.copy(
            isPairing = false,
            status = PairingStatus.Initializing
        )
    }

    // ==================== Setup Code 模式 ====================

    /**
     * 设置 Setup Code 并实时解析
     *
     * Setup Code 是 base64 编码的 JSON:
     * { "url": "ws://...", "bootstrapToken": "..." }
     */
    fun setSetupCode(code: String) {
        val parsed = parseSetupCode(code.trim())
        _state.value = _state.value.copy(
            setupCode = code,
            setupCodeParsed = parsed.first,
            setupCodeError = parsed.second
        )
    }

    /**
     * 使用 Setup Code 连接
     */
    fun connectWithSetupCode() {
        val info = _state.value.setupCodeParsed
        if (info == null) {
            viewModelScope.launch {
                _events.emit(PairingEvent.PairingError("请粘贴有效的 Setup Code"))
            }
            return
        }

        viewModelScope.launch {
            _state.value = _state.value.copy(isPairing = true, status = PairingStatus.Initializing)
            try {
                securityModule.initialize()

                val wsUrl = GatewayUrlUtil.normalizeToWebSocketUrl(info.url)
                securityModule.saveGatewayConfig(wsUrl)

                Log.i(TAG, "Setup Code 连接: $wsUrl")

                // TODO: 实际连接，connect params 带 auth.bootstrapToken
                _state.value = _state.value.copy(
                    isPairing = false,
                    status = PairingStatus.Approved
                )
                _events.emit(PairingEvent.PairingSuccess)
            } catch (e: Exception) {
                Log.e(TAG, "Setup Code 连接失败", e)
                _state.value = _state.value.copy(
                    isPairing = false,
                    status = PairingStatus.Error(e.message ?: "连接失败")
                )
                _events.emit(PairingEvent.PairingError(e.message ?: "连接失败"))
            }
        }
    }

    fun consumeEvent() {}

    // ==================== 内部方法 ====================

    /**
     * 解析 base64 Setup Code
     *
     * @return Pair(解析结果, 错误信息)
     */
    private fun parseSetupCode(code: String): Pair<SetupCodeInfo?, String?> {
        if (code.isBlank()) return Pair(null, null)

        return try {
            val decoded = String(Base64.decode(code, Base64.DEFAULT), Charsets.UTF_8)
            val json = JSONObject(decoded)

            val url = json.optString("url", "").trim()
            val bootstrapToken = json.optString("bootstrapToken", "").trim()

            if (url.isEmpty()) {
                return Pair(null, "Setup Code 缺少 url 字段")
            }
            if (bootstrapToken.isEmpty()) {
                return Pair(null, "Setup Code 缺少 bootstrapToken 字段")
            }

            Pair(SetupCodeInfo(url = url, bootstrapToken = bootstrapToken), null)
        } catch (e: IllegalArgumentException) {
            Pair(null, "无效的 Base64 编码")
        } catch (e: org.json.JSONException) {
            Pair(null, "无效的 JSON 格式")
        } catch (e: Exception) {
            Pair(null, "解析失败: ${e.message}")
        }
    }
}
