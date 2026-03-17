package com.openclaw.clawchat.ui.state

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openclaw.clawchat.security.KeystoreManager
import com.openclaw.clawchat.security.EncryptedStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * 配对流程 ViewModel
 * 
 * 负责管理设备配对流程：
 * - 生成设备密钥对
 * - 发送配对请求到网关
 * - 轮询配对状态
 * - 存储配对令牌
 */
class PairingViewModel(
    private val keystoreManager: KeystoreManager = KeystoreManager(),
    private val encryptedStorage: EncryptedStorage = EncryptedStorage()
) : ViewModel() {

    private val _state = MutableStateFlow(PairingUiState())
    val state: StateFlow<PairingUiState> = _state.asStateFlow()

    private val _events = MutableStateFlow<PairingEvent?>(null)
    val events: StateFlow<PairingEvent?> = _events.asStateFlow()

    companion object {
        private const val TAG = "PairingViewModel"
        private const val POLL_INTERVAL_MS = 5000L // 5 秒轮询一次
        private const val PAIRING_TIMEOUT_MS = 300000L // 5 分钟超时
    }

    /**
     * 初始化配对流程
     * 生成设备密钥对并准备配对请求
     */
    fun initializePairing() {
        viewModelScope.launch {
            try {
                _state.update { it.copy(isInitializing = true, error = null) }

                // 生成设备密钥对（如果不存在）
                val publicKey = keystoreManager.getOrCreateDeviceKeyPair()
                val deviceId = keystoreManager.getDeviceFingerprint()

                Log.d(TAG, "初始化配对 - DeviceId: $deviceId")

                _state.update {
                    it.copy(
                        isInitializing = false,
                        deviceId = deviceId,
                        publicKey = publicKey
                    )
                }

                _events.value = PairingEvent.ReadyForPairing(deviceId, publicKey)
            } catch (e: Exception) {
                Log.e(TAG, "初始化配对失败", e)
                _state.update {
                    it.copy(
                        isInitializing = false,
                        error = "初始化失败：${e.message}"
                    )
                }
                _events.value = PairingEvent.Error("初始化失败：${e.message}")
            }
        }
    }

    /**
     * 发送配对请求到网关
     * @param gatewayUrl 网关地址（如：http://192.168.1.100:18789）
     */
    fun requestPairing(gatewayUrl: String) {
        viewModelScope.launch {
            if (_state.value.publicKey == null) {
                _events.value = PairingEvent.Error("请先初始化配对")
                return@launch
            }

            try {
                _state.update {
                    it.copy(
                        gatewayUrl = gatewayUrl,
                        pairingStatus = PairingStatus.Requesting,
                        isPairing = true,
                        error = null
                    )
                }

                Log.d(TAG, "发送配对请求到：$gatewayUrl")

                // TODO: 调用网络层发送配对请求
                // 模拟配对请求
                val pairingRequest = buildPairingRequest(
                    publicKey = _state.value.publicKey!!,
                    deviceId = _state.value.deviceId ?: "unknown"
                )

                Log.d(TAG, "配对请求：$pairingRequest")

                // 模拟网络延迟
                kotlinx.coroutines.delay(1000)

                // 进入等待批准状态
                _state.update {
                    it.copy(
                        pairingStatus = PairingStatus.WaitingForApproval,
                        pairingStartTime = System.currentTimeMillis()
                    )
                }

                _events.value = PairingEvent.WaitingForApproval

                // 开始轮询配对状态
                startPollingPairingStatus()

            } catch (e: Exception) {
                Log.e(TAG, "配对请求失败", e)
                _state.update {
                    it.copy(
                        pairingStatus = PairingStatus.Failed,
                        isPairing = false,
                        error = "配对请求失败：${e.message}"
                    )
                }
                _events.value = PairingEvent.Error("配对请求失败：${e.message}")
            }
        }
    }

    /**
     * 轮询配对状态
     */
    private fun startPollingPairingStatus() {
        viewModelScope.launch {
            val startTime = _state.value.pairingStartTime ?: System.currentTimeMillis()

            while (System.currentTimeMillis() - startTime < PAIRING_TIMEOUT_MS) {
                // 检查是否还在等待状态
                if (_state.value.pairingStatus != PairingStatus.WaitingForApproval) {
                    break
                }

                kotlinx.coroutines.delay(POLL_INTERVAL_MS)

                // TODO: 调用网络层查询配对状态
                // 模拟：有一定概率配对成功
                val random = kotlin.random.Random.Default.nextDouble()
                
                if (random > 0.7) {
                    // 模拟配对成功
                    handlePairingSuccess("device_token_${System.currentTimeMillis()}")
                    break
                } else if (random < 0.1) {
                    // 模拟配对被拒绝
                    handlePairingRejected("管理员拒绝了配对请求")
                    break
                }
            }

            // 检查是否超时
            if (_state.value.pairingStatus == PairingStatus.WaitingForApproval) {
                handlePairingTimeout()
            }
        }
    }

    /**
     * 构建配对请求 JSON
     */
    private fun buildPairingRequest(publicKey: String, deviceId: String): String {
        val request = mapOf(
            "device" to mapOf(
                "id" to deviceId,
                "publicKey" to publicKey
            ),
            "client" to mapOf(
                "id" to "openclaw-android",
                "version" to "1.0.0",
                "platform" to "android"
            ),
            "role" to "operator",
            "scopes" to listOf("operator.read", "operator.write"),
            "token" to "", // 首次配对为空
            "nonce" to "", // 由服务器提供
            "signedAt" to System.currentTimeMillis(),
            "platform" to "android",
            "deviceFamily" to "phone"
        )

        return Json { prettyPrint = true }.encodeToString(request)
    }

    /**
     * 处理配对成功
     */
    private fun handlePairingSuccess(deviceToken: String) {
        viewModelScope.launch {
            Log.d(TAG, "配对成功，存储令牌")

            // 存储设备令牌
            encryptedStorage.saveDeviceToken(deviceToken)

            _state.update {
                it.copy(
                    pairingStatus = PairingStatus.Paired,
                    isPairing = false,
                    deviceToken = deviceToken
                )
            }

            _events.value = PairingEvent.PairingSuccess(deviceToken)
        }
    }

    /**
     * 处理配对被拒绝
     */
    private fun handlePairingRejected(reason: String) {
        Log.w(TAG, "配对被拒绝：$reason")

        _state.update {
            it.copy(
                pairingStatus = PairingStatus.Failed,
                isPairing = false,
                error = reason
            )
        }

        _events.value = PairingEvent.PairingRejected(reason)
    }

    /**
     * 处理配对超时
     */
    private fun handlePairingTimeout() {
        Log.w(TAG, "配对超时")

        _state.update {
            it.copy(
                pairingStatus = PairingStatus.Failed,
                isPairing = false,
                error = "配对超时，请重试"
            )
        }

        _events.value = PairingEvent.PairingTimeout
    }

    /**
     * 重试配对
     */
    fun retryPairing() {
        _state.update {
            it.copy(
                pairingStatus = PairingStatus.Idle,
                isPairing = false,
                error = null
            )
        }
        
        // 重新发送配对请求
        if (_state.value.gatewayUrl.isNotBlank()) {
            requestPairing(_state.value.gatewayUrl)
        }
    }

    /**
     * 取消配对
     */
    fun cancelPairing() {
        _state.update {
            it.copy(
                pairingStatus = PairingStatus.Idle,
                isPairing = false,
                error = null
            )
        }
    }

    /**
     * 清除错误
     */
    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    /**
     * 清除事件
     */
    fun consumeEvent() {
        _events.value = null
    }
}

/**
 * 配对事件类型
 */
sealed class PairingEvent {
    data class ReadyForPairing(val deviceId: String, val publicKey: String) : PairingEvent()
    data object WaitingForApproval : PairingEvent()
    data class PairingSuccess(val deviceToken: String) : PairingEvent()
    data class PairingRejected(val reason: String) : PairingEvent()
    data object PairingTimeout : PairingEvent()
    data class Error(val message: String) : PairingEvent()
}
