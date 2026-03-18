package com.openclaw.clawchat.domain.repository

import com.openclaw.clawchat.domain.model.GatewayConfig
import kotlinx.coroutines.flow.Flow

/**
 * 设置仓库接口
 * 
 * 定义应用设置和配置管理的核心操作，包括 Gateway 配置管理、
 * 用户偏好设置和设备信息管理。
 * 
 * 设计原则：
 * - 配置持久化：所有设置自动持久化到本地存储
 * - 响应式更新：配置变更时自动通知观察者
 * - 类型安全：使用强类型数据类而非原始键值对
 */
interface SettingsRepository {
    
    // ==================== Gateway 配置管理 ====================
    
    /**
     * 观察所有 Gateway 配置
     * 
     * 返回一个 Flow，当配置列表发生变化时自动发射新数据。
     * 
     * @return Gateway 配置列表的 Flow
     */
    fun observeGatewayConfigs(): Flow<List<GatewayConfig>>
    
    /**
     * 获取所有 Gateway 配置
     * 
     * @return 配置列表
     */
    suspend fun getAllGatewayConfigs(): List<GatewayConfig>
    
    /**
     * 获取指定 Gateway 配置
     * 
     * @param id 配置 ID
     * @return 配置对象，不存在返回 null
     */
    suspend fun getGatewayConfig(id: String): GatewayConfig?
    
    /**
     * 获取当前选中的 Gateway 配置
     * 
     * @return 当前配置，未设置返回 null
     */
    suspend fun getCurrentGatewayConfig(): GatewayConfig?
    
    /**
     * 保存 Gateway 配置
     * 
     * 新增或更新配置。如果配置 ID 已存在则更新，否则新增。
     * 
     * @param config Gateway 配置
     * @return 操作结果
     */
    suspend fun saveGatewayConfig(config: GatewayConfig): Result<Unit>
    
    /**
     * 删除 Gateway 配置
     * 
     * @param id 配置 ID
     * @return 操作结果
     */
    suspend fun deleteGatewayConfig(id: String): Result<Unit>
    
    /**
     * 设置当前 Gateway 配置
     * 
     * 将指定配置设为当前使用配置，其他配置自动设为非当前。
     * 
     * @param id 配置 ID
     * @return 操作结果
     */
    suspend fun setCurrentGatewayConfig(id: String): Result<Unit>
    
    /**
     * 导入 Gateway 配置
     * 
     * 从 JSON 字符串导入配置（用于配置备份/恢复）。
     * 
     * @param json JSON 字符串
     * @return 导入的配置列表
     */
    suspend fun importGatewayConfigs(json: String): Result<List<GatewayConfig>>
    
    /**
     * 导出 Gateway 配置
     * 
     * 将所有配置导出为 JSON 字符串（用于备份）。
     * 
     * @return JSON 字符串
     */
    suspend fun exportGatewayConfigs(): String
    
    // ==================== 用户偏好 ====================
    
    /**
     * 获取用户偏好
     * 
     * @return 用户偏好对象
     */
    suspend fun getUserPreferences(): UserPreferences
    
    /**
     * 观察用户偏好
     * 
     * @return 用户偏好的 Flow
     */
    fun observeUserPreferences(): Flow<UserPreferences>
    
    /**
     * 保存用户偏好
     * 
     * @param preferences 用户偏好
     * @return 操作结果
     */
    suspend fun saveUserPreferences(preferences: UserPreferences): Result<Unit>
    
    /**
     * 更新单个偏好设置
     * 
     * @param update 偏好更新函数
     * @return 操作结果
     */
    suspend fun updatePreferences(update: UserPreferences.() -> UserPreferences): Result<Unit>
    
    // ==================== 设备信息 ====================
    
    /**
     * 获取设备 ID
     * 
     * @return 设备唯一标识
     */
    suspend fun getDeviceId(): String
    
    /**
     * 获取设备公钥
     * 
     * @return 设备公钥（PEM 格式）
     */
    suspend fun getDevicePublicKey(): String?
    
    /**
     * 检查设备是否已配对
     * 
     * @return 是否已配对
     */
    suspend fun isDevicePaired(): Boolean
    
    /**
     * 重置设备信息
     * 
     * 清除设备 ID 和密钥，恢复未配对状态。
     * 
     * @return 操作结果
     */
    suspend fun resetDeviceInfo(): Result<Unit>
}

/**
 * 用户偏好
 * 
 * 应用级别的用戶偏好设置。
 * 
 * @property theme 主题模式（system/light/dark）
 * @property language 语言设置
 * @property notificationsEnabled 是否启用通知
 * @property soundEnabled 是否启用提示音
 * @property vibrationEnabled 是否启用振动
 * @property autoReconnectEnabled 是否启用自动重连
 * @property autoReconnectDelay 自动重连延迟（秒）
 * @property messagesCacheLimit 消息缓存数量限制
 * @property showTimestamps 是否显示消息时间戳
 * @property confirmBeforeDelete 删除前是否确认
 */
data class UserPreferences(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val language: String = "zh-CN",
    val notificationsEnabled: Boolean = true,
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val autoReconnectEnabled: Boolean = true,
    val autoReconnectDelay: Int = 5,
    val messagesCacheLimit: Int = 100,
    val showTimestamps: Boolean = true,
    val confirmBeforeDelete: Boolean = true
) {
    /**
     * 检查是否为深色主题
     */
    fun isDarkMode(): Boolean = theme == ThemeMode.DARK
    
    /**
     * 检查是否为浅色主题
     */
    fun isLightMode(): Boolean = theme == ThemeMode.LIGHT
}

/**
 * 主题模式枚举
 */
enum class ThemeMode {
    /** 跟随系统 */
    SYSTEM,
    /** 浅色主题 */
    LIGHT,
    /** 深色主题 */
    DARK
}
