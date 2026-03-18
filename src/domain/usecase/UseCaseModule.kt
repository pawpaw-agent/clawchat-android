package com.openclaw.clawchat.domain.usecase

import com.openclaw.clawchat.domain.repository.ConnectionRepository
import com.openclaw.clawchat.domain.repository.SessionRepository
import com.openclaw.clawchat.domain.repository.SettingsRepository

/**
 * UseCase 组合器
 * 
 * 提供所有 UseCase 的集中访问点。
 * 用于依赖注入配置或手动组装。
 * 
 * 使用示例：
 * ```
 * val useCases = UseCaseModule(sessionRepository, connectionRepository, settingsRepository)
 * useCases.sendMessage.invoke("session_123", "Hello")
 * useCases.createSession.invoke(model = "aliyun/qwen3.5-plus")
 * ```
 */
class UseCaseModule(
    sessionRepository: SessionRepository,
    connectionRepository: ConnectionRepository,
    settingsRepository: SettingsRepository,
    deviceIdProvider: DeviceIdProvider = DefaultDeviceIdProvider()
) {
    // ==================== 会话相关 ====================
    
    /**
     * 创建会话
     */
    val createSession = CreateSession(sessionRepository)
    
    /**
     * 删除会话
     */
    val deleteSession = DeleteSession(sessionRepository)
    
    /**
     * 获取会话历史
     */
    val getSessionHistory = GetSessionHistory(sessionRepository)
    
    // ==================== 消息相关 ====================
    
    /**
     * 发送消息
     */
    val sendMessage = SendMessage(sessionRepository)
    
    /**
     * 接收消息
     */
    val receiveMessage = ReceiveMessage(sessionRepository)
    
    // ==================== 连接相关 ====================
    
    /**
     * 连接 Gateway
     */
    val connectGateway = ConnectGateway(connectionRepository, settingsRepository)
    
    /**
     * 设备配对
     */
    val pairDevice = PairDevice(connectionRepository, deviceIdProvider)
}

/**
 * 扩展函数：快速创建 UseCaseModule
 * 
 * 当只有部分 Repository 时使用。
 */
fun UseCaseModule(
    sessionRepository: SessionRepository
): MinimalUseCaseModule {
    return MinimalUseCaseModule(sessionRepository)
}

/**
 * 最小化 UseCase 模块
 * 
 * 仅包含不依赖 Connection/Settings 的 UseCase。
 */
class MinimalUseCaseModule(
    private val sessionRepository: SessionRepository
) {
    val createSession = CreateSession(sessionRepository)
    val deleteSession = DeleteSession(sessionRepository)
    val getSessionHistory = GetSessionHistory(sessionRepository)
    val sendMessage = SendMessage(sessionRepository)
    val receiveMessage = ReceiveMessage(sessionRepository)
}
