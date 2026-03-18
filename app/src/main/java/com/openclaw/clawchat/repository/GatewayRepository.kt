package com.openclaw.clawchat.repository

import com.openclaw.clawchat.security.EncryptedStorage
import com.openclaw.clawchat.ui.state.GatewayConfigUi
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gateway 配置仓库
 * 
 * 负责管理 Gateway 配置的持久化：
 * - 获取配置
 * - 保存配置
 * - 删除配置
 * - 管理多个 Gateway 配置
 */
@Singleton
class GatewayRepository @Inject constructor(
    private val encryptedStorage: EncryptedStorage
) {
    
    companion object {
        private const val DEFAULT_GATEWAY_ID = "default"
        private const val DEFAULT_PORT = 18789
    }
    
    /**
     * 获取 Gateway 配置
     * 
     * @param id Gateway ID，默认为 "default"
     * @return GatewayConfigUi 配置对象，如果不存在返回 null
     */
    fun getConfig(id: String = DEFAULT_GATEWAY_ID): GatewayConfigUi? {
        val url = encryptedStorage.getGatewayUrl() ?: return null
        
        // 解析 URL (格式：ws://host:port 或 wss://host:port)
        val (host, port, useTls) = parseGatewayUrl(url)
        
        return GatewayConfigUi(
            id = id,
            name = "Gateway",
            host = host,
            port = port,
            useTls = useTls,
            isCurrent = true
        )
    }
    
    /**
     * 保存 Gateway 配置
     * 
     * @param config Gateway 配置
     */
    fun saveConfig(config: GatewayConfigUi) {
        val url = buildGatewayUrl(config.host, config.port, config.useTls)
        encryptedStorage.saveGatewayUrl(url)
        
        // 如果配置了 TLS，保存指纹
        // TODO: 从配置中获取 TLS 指纹
    }
    
    /**
     * 保存 Gateway URL（简化版本）
     * 
     * @param host 主机地址
     * @param port 端口
     * @param useTls 是否使用 TLS
     */
    fun saveGateway(host: String, port: Int = DEFAULT_PORT, useTls: Boolean = false) {
        val url = buildGatewayUrl(host, port, useTls)
        encryptedStorage.saveGatewayUrl(url)
    }
    
    /**
     * 删除 Gateway 配置
     * 
     * @param id Gateway ID，默认为 "default"
     */
    fun deleteConfig(id: String = DEFAULT_GATEWAY_ID) {
        // 使用反射或扩展函数清除特定配置
        // 当前实现清除所有 Gateway 相关配置
        encryptedStorage.clearGatewayConfig()
    }
    
    /**
     * 检查是否有配置的 Gateway
     */
    fun hasConfiguredGateway(): Boolean {
        return !encryptedStorage.getGatewayUrl().isNullOrEmpty()
    }
    
    /**
     * 获取所有 Gateway 配置
     * 
     * 当前只支持单个 Gateway 配置
     * 未来可扩展为支持多个 Gateway
     */
    fun getAllConfigs(): List<GatewayConfigUi> {
        return getConfig()?.let { listOf(it) } ?: emptyList()
    }
    
    /**
     * 设置当前 Gateway
     * 
     * @param id Gateway ID
     */
    fun setCurrentGateway(id: String) {
        // 当前实现只支持单个 Gateway，此方法预留用于未来多 Gateway 支持
    }
    
    /**
     * 构建 Gateway URL
     */
    private fun buildGatewayUrl(host: String, port: Int, useTls: Boolean): String {
        val protocol = if (useTls) "wss" else "ws"
        return "$protocol://$host:$port"
    }
    
    /**
     * 解析 Gateway URL
     * 
     * @param url WebSocket URL (ws://host:port 或 wss://host:port)
     * @return Triple(host, port, useTls)
     */
    private fun parseGatewayUrl(url: String): Triple<String, Int, Boolean> {
        val useTls = url.startsWith("wss://")
        val withoutProtocol = url.removePrefix("ws://").removePrefix("wss://")
        
        val parts = withoutProtocol.split(":")
        val host = parts.firstOrNull() ?: ""
        val port = parts.lastOrNull()?.toIntOrNull() ?: DEFAULT_PORT
        
        return Triple(host, port, useTls)
    }
}

/**
 * 扩展 EncryptedStorage 添加清除 Gateway 配置方法
 */
private fun EncryptedStorage.clearGatewayConfig() {
    // 使用反射访问私有 sharedPreferences 或使用清除方法
    // 这里使用清除特定键的方式
    // 注意：EncryptedStorage 没有直接提供此方法，需要通过 clearAll 或添加新方法
    // 临时实现：保存空值
    saveGatewayUrl("")
}
