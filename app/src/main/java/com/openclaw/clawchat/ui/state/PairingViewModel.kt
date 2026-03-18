package com.openclaw.clawchat.ui.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
 * 配对状态数据
 */
data class PairingState(
    val deviceId: String? = null,
    val publicKey: String? = null,
    val gatewayUrl: String = "",
    val status: PairingStatus = PairingStatus.Initializing,
    val isInitializing: Boolean = false,
    val isPairing: Boolean = false
)

/**
 * 配对 ViewModel
 * 
 * 管理设备配对流程：
 * - 生成设备密钥对
 * - 显示设备信息
 * - 处理配对请求
 */
@HiltViewModel
class PairingViewModel @Inject constructor(
    private val securityModule: SecurityModule
) : ViewModel() {

    private val _state = MutableStateFlow(PairingState())
    val state: StateFlow<PairingState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<PairingEvent>(extraBufferCapacity = 10)
    val events: SharedFlow<PairingEvent> = _events.asSharedFlow()

    /**
     * 初始化配对 - 生成设备密钥对
     */
    fun initializePairing() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isInitializing = true)
            
            try {
                // 生成设备 ID
                val deviceId = generateDeviceId()
                
                // 生成密钥对并获取公钥
                val publicKey = generateKeyPair()
                
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

    /**
     * 设置网关地址并开始配对
     */
    fun setGatewayUrl(url: String) {
        _state.value = _state.value.copy(gatewayUrl = url)
    }

    /**
     * 开始配对流程
     */
    fun startPairing() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isPairing = true)
            
            try {
                // TODO: 实现实际的配对逻辑
                // 目前模拟配对成功
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

    /**
     * 取消配对
     */
    fun cancelPairing() {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                isPairing = false,
                status = PairingStatus.Initializing
            )
        }
    }

    /**
     * 消费事件
     */
    fun consumeEvent() {
        // 事件通过 StateFlow 的 state 属性消费
    }

    /**
     * 生成设备 ID
     */
    private suspend fun generateDeviceId(): String {
        val status = securityModule.initialize()
        return status.deviceId ?: securityModule.getSecurityStatus().deviceId ?: "unknown"
    }

    /**
     * 生成密钥对并返回公钥（PEM 格式）
     */
    private suspend fun generateKeyPair(): String {
        securityModule.initialize()
        return securityModule.preparePairingRequest("default-node")
            .let { 
                val jsonOrg = JSONObject(it)
                jsonOrg.getJSONObject("device").getString("publicKey")
            }
    }
}
