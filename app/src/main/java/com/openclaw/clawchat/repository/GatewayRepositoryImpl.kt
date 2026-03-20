package com.openclaw.clawchat.repository

import com.openclaw.clawchat.domain.model.GatewayConfig
import com.openclaw.clawchat.domain.repository.GatewayRepository
import com.openclaw.clawchat.security.EncryptedStorage
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gateway 配置仓库实现
 *
 * 实现 Domain 层的 GatewayRepository 接口。
 * 负责管理 Gateway 配置的持久化。
 */
@Singleton
class GatewayRepositoryImpl @Inject constructor(
    private val encryptedStorage: EncryptedStorage
) : GatewayRepository {
    
    companion object {
        private const val DEFAULT_GATEWAY_ID = "default"
    }
    
    /**
     * 获取 Gateway 配置
     */
    override fun getConfig(id: String): GatewayConfig? {
        val url = encryptedStorage.getGatewayUrl() ?: return null
        return GatewayConfig.fromUrl(url, id, "Gateway")
    }
    
    /**
     * 保存 Gateway 配置
     */
    override fun saveConfig(config: GatewayConfig) {
        val url = config.toWebSocketUrl()
        encryptedStorage.saveGatewayUrl(url)
    }
    
    /**
     * 保存 Gateway URL
     */
    override fun saveGateway(host: String, port: Int, useTls: Boolean) {
        val config = GatewayConfig(
            id = DEFAULT_GATEWAY_ID,
            name = "Gateway",
            host = host,
            port = port,
            useTls = useTls
        )
        saveConfig(config)
    }
    
    /**
     * 删除 Gateway 配置
     */
    override fun deleteConfig(id: String) {
        encryptedStorage.clearGatewayConfig()
    }
    
    /**
     * 检查是否有配置的 Gateway
     */
    override fun hasConfiguredGateway(): Boolean {
        return !encryptedStorage.getGatewayUrl().isNullOrEmpty()
    }
    
    /**
     * 获取所有 Gateway 配置
     */
    override fun getAllConfigs(): List<GatewayConfig> {
        return getConfig()?.let { listOf(it) } ?: emptyList()
    }
    
    /**
     * 设置当前 Gateway
     */
    override fun setCurrentGateway(id: String) {
        // 当前实现只支持单个 Gateway，此方法预留用于未来多 Gateway 支持
    }
}