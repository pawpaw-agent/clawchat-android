package com.openclaw.clawchat.domain.usecase

import com.openclaw.clawchat.domain.model.DeviceToken
import com.openclaw.clawchat.domain.model.GatewayConfig
import com.openclaw.clawchat.domain.repository.ConnectionRepository

/**
 * 设备配对用例
 * 
 * 处理与 OpenClaw Gateway 设备配对的业务逻辑。
 * 负责生成设备信息、发起配对请求、轮询配对状态。
 * 
 * 设计原则：
 * - 单一职责：只处理设备配对的逻辑
 * - 安全配对：使用挑战 - 响应机制
 * - 超时处理：配对超时自动取消
 * 
 * @param connectionRepository 连接仓库
 * @param deviceIdProvider 设备 ID 生成器
 */
class PairDevice(
    private val connectionRepository: ConnectionRepository,
    private val deviceIdProvider: DeviceIdProvider = DefaultDeviceIdProvider()
) {
    /**
     * 执行设备配对操作
     * 
     * 完整配对流程：
     * 1. 生成设备 ID 和密钥对
     * 2. 向 Gateway 发起配对请求
     * 3. 轮询配对状态直到批准或超时
     * 4. 保存设备令牌
     * 
     * @param config Gateway 配置
     * @param deviceName 设备名称（用于管理员识别）
     * @param timeoutMs 配对超时时间（毫秒），默认 5 分钟
     * @return 配对结果，成功返回设备令牌
     */
    suspend operator fun invoke(
        config: GatewayConfig,
        deviceName: String,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS
    ): Result<DeviceToken> {
        // 验证设备名称
        if (deviceName.isBlank()) {
            return Result.failure(IllegalArgumentException("Device name cannot be empty"))
        }
        
        if (deviceName.length > MAX_DEVICE_NAME_LENGTH) {
            return Result.failure(IllegalArgumentException(
                "Device name exceeds maximum length of $MAX_DEVICE_NAME_LENGTH characters"
            ))
        }
        
        // 检查设备是否已配对
        val existingToken = connectionRepository.getDeviceToken()
        if (existingToken != null && existingToken.isValid()) {
            return Result.failure(IllegalStateException(
                "Device is already paired. Revoke existing token first."
            ))
        }
        
        // 发起配对请求
        val pairingResult = connectionRepository.requestPairing(config, deviceName)
        if (pairingResult.isFailure) {
            return pairingResult
        }
        
        // 轮询配对状态
        val requestId = pairingResult.getOrNull()?.deviceId ?: ""
        return connectionRepository.pollPairingStatus(config, requestId, timeoutMs)
            .onSuccess { token ->
                // 保存设备令牌
                connectionRepository.saveDeviceToken(token)
            }
    }
    
    /**
     * 取消配对请求
     * 
     * @param config Gateway 配置
     * @param requestId 配对请求 ID
     * @return 取消结果
     */
    suspend fun cancelPairing(
        config: GatewayConfig,
        requestId: String
    ): Result<Unit> {
        return connectionRepository.cancelPairing(config, requestId)
    }
    
    /**
     * 撤销设备配对
     * 
     * 清除本地令牌并通知 Gateway 撤销。
     * 
     * @return 撤销结果
     */
    suspend fun revokePairing(): Result<Unit> {
        return connectionRepository.revokeDeviceToken()
    }
    
    /**
     * 检查设备配对状态
     * 
     * @return 是否已配对
     */
    suspend fun isPaired(): Boolean {
        return connectionRepository.getDeviceToken()?.isValid() == true
    }
    
    companion object {
        private const val DEFAULT_TIMEOUT_MS = 300_000L // 5 分钟
        private const val MAX_DEVICE_NAME_LENGTH = 50
    }
}

/**
 * 设备 ID 提供者接口
 * 
 * 用于生成和获取设备唯一标识。
 */
interface DeviceIdProvider {
    /**
     * 获取或生成设备 ID
     * 
     * @return 设备唯一标识
     */
    fun getDeviceId(): String
    
    /**
     * 获取设备公钥（PEM 格式）
     * 
     * @return 公钥字符串
     */
    fun getPublicKey(): String?
}

/**
 * 默认设备 ID 提供者实现
 * 
 * 使用随机 UUID 作为设备 ID。
 * 实际实现中应使用 Android Keystore 生成持久化密钥对。
 */
class DefaultDeviceIdProvider : DeviceIdProvider {
    private var deviceId: String? = null
    private var publicKey: String? = null
    
    override fun getDeviceId(): String {
        if (deviceId == null) {
            deviceId = generateDeviceId()
        }
        return deviceId!!
    }
    
    override fun getPublicKey(): String? {
        return publicKey
    }
    
    private fun generateDeviceId(): String {
        return "device_" + java.util.UUID.randomUUID()
            .toString()
            .replace("-", "")
            .take(32)
    }
}
