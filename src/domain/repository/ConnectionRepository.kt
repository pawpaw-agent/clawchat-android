package com.openclaw.clawchat.domain.repository

import com.openclaw.clawchat.domain.model.ConnectionStatus
import com.openclaw.clawchat.domain.model.DeviceToken
import com.openclaw.clawchat.domain.model.GatewayConfig
import kotlinx.coroutines.flow.Flow

/**
 * 连接仓库接口
 * 
 * 定义与 OpenClaw Gateway 连接相关的核心操作，包括连接管理、
 * 设备配对和连接状态监控。
 * 
 * 设计原则：
 * - 连接状态统一：使用 Flow 提供实时状态更新
 * - 安全配对：支持挑战 - 响应式设备配对
 * - 灵活配置：支持多 Gateway 配置管理
 */
interface ConnectionRepository {
    
    // ==================== 连接状态 ====================
    
    /**
     * 观察连接状态
     * 
     * 返回一个 Flow，当连接状态发生变化时自动发射新数据。
     * 
     * @return 连接状态的 Flow
     */
    fun observeConnectionStatus(): Flow<ConnectionStatus>
    
    /**
     * 获取当前连接状态
     * 
     * @return 当前连接状态
     */
    fun getCurrentStatus(): ConnectionStatus
    
    /**
     * 获取当前延迟（毫秒）
     * 
     * @return 延迟值，未连接返回 null
     */
    suspend fun getCurrentLatency(): Long?
    
    // ==================== 连接管理 ====================
    
    /**
     * 连接到 Gateway
     * 
     * 使用指定配置建立 WebSocket 连接。如果已有活动连接，
     * 先断开现有连接再建立新连接。
     * 
     * @param config Gateway 配置
     * @return 连接结果
     */
    suspend fun connect(config: GatewayConfig): Result<Unit>
    
    /**
     * 断开连接
     * 
     * 主动断开与 Gateway 的连接。
     * 
     * @param reason 断开原因（可选，用于日志记录）
     * @return 操作结果
     */
    suspend fun disconnect(reason: String? = null): Result<Unit>
    
    /**
     * 重连
     * 
     * 使用当前配置重新建立连接。
     * 
     * @return 连接结果
     */
    suspend fun reconnect(): Result<Unit>
    
    /**
     * 检查连接是否可用
     * 
     * 执行轻量级健康检查（如 Ping）。
     * 
     * @return 是否可用
     */
    suspend fun checkHealth(): Boolean
    
    // ==================== 设备配对 ====================
    
    /**
     * 发起设备配对请求
     * 
     * 向 Gateway 发送配对请求，等待管理员批准。
     * 配对成功后返回设备令牌。
     * 
     * @param config Gateway 配置
     * @param deviceName 设备名称（用于管理员识别）
     * @return 配对结果，成功返回 DeviceToken
     */
    suspend fun requestPairing(
        config: GatewayConfig,
        deviceName: String
    ): Result<DeviceToken>
    
    /**
     * 轮询配对状态
     * 
     * 配对请求发出后，定期轮询配对状态直到批准或超时。
     * 
     * @param config Gateway 配置
     * @param requestId 配对请求 ID
     * @param timeoutMs 超时时间（毫秒）
     * @return 配对结果
     */
    suspend fun pollPairingStatus(
        config: GatewayConfig,
        requestId: String,
        timeoutMs: Long = 300_000 // 5 分钟
    ): Result<DeviceToken>
    
    /**
     * 取消配对请求
     * 
     * 取消正在进行的配对请求。
     * 
     * @param config Gateway 配置
     * @param requestId 配对请求 ID
     * @return 操作结果
     */
    suspend fun cancelPairing(
        config: GatewayConfig,
        requestId: String
    ): Result<Unit>
    
    // ==================== 令牌管理 ====================
    
    /**
     * 获取当前设备令牌
     * 
     * @return 设备令牌，未配对返回 null
     */
    suspend fun getDeviceToken(): DeviceToken?
    
    /**
     * 保存设备令牌
     * 
     * @param token 设备令牌
     * @return 操作结果
     */
    suspend fun saveDeviceToken(token: DeviceToken): Result<Unit>
    
    /**
     * 撤销设备令牌
     * 
     * 清除本地存储的令牌，并通知 Gateway 撤销。
     * 
     * @return 操作结果
     */
    suspend fun revokeDeviceToken(): Result<Unit>
    
    /**
     * 刷新设备令牌
     * 
     * 当令牌即将过期或已过期时，请求新的令牌。
     * 
     * @param config Gateway 配置
     * @return 新令牌
     */
    suspend fun refreshDeviceToken(config: GatewayConfig): Result<DeviceToken>
}
