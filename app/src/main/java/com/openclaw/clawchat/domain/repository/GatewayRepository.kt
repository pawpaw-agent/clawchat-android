package com.openclaw.clawchat.domain.repository

import com.openclaw.clawchat.domain.model.GatewayConfig

/**
 * Gateway 配置仓库接口
 *
 * Domain 层 Repository 接口定义。
 * 遵循依赖倒置原则，上层依赖此接口而非具体实现。
 */
interface GatewayRepository {

    /**
     * 获取 Gateway 配置
     */
    fun getConfig(id: String = "default"): GatewayConfig?

    /**
     * 保存 Gateway 配置
     */
    fun saveConfig(config: GatewayConfig)

    /**
     * 保存 Gateway URL
     */
    fun saveGateway(host: String, port: Int = GatewayConfig.DEFAULT_PORT, useTls: Boolean = false)

    /**
     * 删除 Gateway 配置
     */
    fun deleteConfig(id: String = "default")

    /**
     * 检查是否有配置的 Gateway
     */
    fun hasConfiguredGateway(): Boolean

    /**
     * 获取所有 Gateway 配置
     */
    fun getAllConfigs(): List<GatewayConfig>

    /**
     * 设置当前 Gateway
     */
    fun setCurrentGateway(id: String)
}