package com.openclaw.clawchat.ui.state

import android.util.Base64
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
import java.security.KeyPairGenerator
import javax.inject.Inject

/**
 * 配对状态
 */
sealed class PairingStatus {
    data object Initializing : PairingStatus()
    data object WaitingForApproval : PairingStatus()
    data object Approved : PairingStatus()
    data object Rejected : PairingStatus()
    data object Timeout : PairingStatus()
    data class Error(val message: String) : PairingStatus()
}

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
        viewModelScope.launch {
            _events.tryEmit(PairingEvent.PairingSuccess) // 占位，实际由具体事件替换
        }
    }

    /**
     * 生成设备 ID
     */
    private fun generateDeviceId(): String {
        return securityModule.generateDeviceId()
    }

    /**
     * 生成密钥对并返回公钥（PEM 格式）
     */
    private fun generateKeyPair(): String {
        return securityModule.getOrCreateKeyPair()?.let { keyPair ->
            val publicKeyBytes = keyPair.public.encoded
            val publicKeyPem = Base64.encodeToString(publicKeyBytes, Base64.NO_WRAP)
            "-----BEGIN PUBLIC KEY-----\n$publicKeyPem\n-----END PUBLIC KEY-----"
        } ?: run {
            // 生成新的密钥对
            val kpg = KeyPairGenerator.getInstance("EC")
            kpg.initialize(256)
            val keyPair = kpg.generateKeyPair()
            val publicKeyBytes = keyPair.public.encoded
            val publicKeyPem = Base64.encodeToString(publicKeyBytes, Base64.NO_WRAP)
            "-----BEGIN PUBLIC KEY-----\n$publicKeyPem\n-----END PUBLIC KEY-----"
        }
    }
}
