package com.openclaw.clawchat.domain.usecase

import com.openclaw.clawchat.domain.model.ConnectionStatus
import com.openclaw.clawchat.domain.model.GatewayConfig
import com.openclaw.clawchat.domain.repository.ConnectionRepository
import com.openclaw.clawchat.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow

/**
 * 连接 Gateway 用例
 * 
 * 处理与 OpenClaw Gateway 建立连接的业务逻辑。
 * 负责获取配置、验证令牌、建立 WebSocket 连接。
 * 
 * 设计原则：
 * - 单一职责：只处理连接 Gateway 的逻辑
 * - 配置管理：自动使用当前选中的 Gateway 配置
 * - 令牌验证：连接前验证设备令牌有效性
 * 
 * @param connectionRepository 连接仓库
 * @param settingsRepository 设置仓库
 */
class ConnectGateway(
    private val connectionRepository: ConnectionRepository,
    private val settingsRepository: SettingsRepository
) {
    /**
     * 执行连接操作
     * 
     * @param gatewayUrl 可选的 Gateway URL，不提供则使用当前配置
     * @return 连接结果
     */
    suspend operator fun invoke(gatewayUrl: String? = null): Result<Unit> {
        // 获取 Gateway 配置
        val config = if (gatewayUrl != null) {
            // 从 URL 解析配置
            parseGatewayConfig(gatewayUrl)
        } else {
            // 使用当前选中的配置
            settingsRepository.getCurrentGatewayConfig()
                ?: return Result.failure(IllegalStateException("No gateway configured"))
        }
        
        // 检查设备是否已配对
        val deviceToken = connectionRepository.getDeviceToken()
        if (deviceToken == null) {
            return Result.failure(IllegalStateException(
                "Device not paired. Please pair device before connecting."
            ))
        }
        
        // 检查令牌是否有效
        if (!deviceToken.isValid()) {
            // 尝试刷新令牌
            val refreshResult = connectionRepository.refreshDeviceToken(config)
            if (refreshResult.isFailure) {
                return Result.failure(IllegalStateException(
                    "Device token expired. Please re-pair device."
                ))
            }
        }
        
        // 建立连接
        return connectionRepository.connect(config)
    }
    
    /**
     * 观察连接状态
     * 
     * @return 连接状态的 Flow
     */
    fun observeConnectionStatus(): Flow<ConnectionStatus> {
        return connectionRepository.observeConnectionStatus()
    }
    
    /**
     * 获取当前连接状态
     * 
     * @return 当前连接状态
     */
    fun getCurrentStatus(): ConnectionStatus {
        return connectionRepository.getCurrentStatus()
    }
    
    /**
     * 断开连接
     * 
     * @return 断开结果
     */
    suspend fun disconnect(): Result<Unit> {
        return connectionRepository.disconnect()
    }
    
    /**
     * 重连
     * 
     * @return 重连结果
     */
    suspend fun reconnect(): Result<Unit> {
        return connectionRepository.reconnect()
    }
    
    /**
     * 从 URL 解析 Gateway 配置
     */
    private fun parseGatewayConfig(url: String): GatewayConfig {
        // 解析格式：ws(s)://host:port
        val regex = Regex("^(wss?)://([^:/]+)(?::(\\d+))?$")
        val match = regex.find(url)
            ?: throw IllegalArgumentException("Invalid gateway URL format: $url")
        
        val protocol = match.groupValues[1]
        val host = match.groupValues[2]
        val port = match.groupValues[3].toIntOrNull() ?: GatewayConfig.DEFAULT_PORT
        val useTls = protocol == "wss"
        
        return GatewayConfig(
            name = "Custom Gateway",
            host = host,
            port = port,
            useTls = useTls
        )
    }
    
    /**
     * 检查是否可以连接
     * 
     * @return 检查结果
     */
    suspend fun canConnect(): Result<Boolean> {
        // 检查是否有配置
        val config = settingsRepository.getCurrentGatewayConfig()
            ?: return Result.success(false)
        
        // 检查是否已配对
        val token = connectionRepository.getDeviceToken()
            ?: return Result.success(false)
        
        // 检查令牌是否有效
        if (!token.isValid()) {
            return Result.success(false)
        }
        
        return Result.success(true)
    }
}
