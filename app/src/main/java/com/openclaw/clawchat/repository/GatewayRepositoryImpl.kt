package com.openclaw.clawchat.repository

import com.openclaw.clawchat.security.EncryptedStorage
import com.openclaw.clawchat.ui.state.GatewayConfigUi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gateway 配置仓库实现
 *
 * 使用 EncryptedStorage 安全存储 Gateway 配置。
 */
@Singleton
class GatewayRepositoryImpl @Inject constructor(
    private val encryptedStorage: EncryptedStorage
) : GatewayRepository {

    private val _gateways = MutableStateFlow<List<GatewayConfigUi>>(emptyList())
    private val _currentGatewayId = MutableStateFlow<String?>(null)

    companion object {
        private const val KEY_GATEWAYS = "gateway_configs"
        private const val KEY_CURRENT_GATEWAY = "current_gateway_id"
    }

    init {
        loadGateways()
    }

    /**
     * 观察所有 Gateway 配置
     */
    override fun observeGateways(): Flow<List<GatewayConfigUi>> {
        return _gateways.asStateFlow()
    }

    /**
     * 获取当前 Gateway
     */
    override fun getCurrentGateway(): GatewayConfigUi? {
        val currentId = _currentGatewayId.value ?: return null
        return _gateways.value.find { it.id == currentId }
    }

    /**
     * 保存 Gateway 配置
     */
    override suspend fun saveGateway(gateway: GatewayConfigUi) {
        _gateways.update { gateways ->
            val existing = gateways.find { it.id == gateway.id }
            if (existing != null) {
                gateways.map { if (it.id == gateway.id) gateway else it }
            } else {
                gateways + gateway
            }
        }
        persistGateways()
    }

    /**
     * 删除 Gateway 配置
     */
    override suspend fun deleteGateway(gatewayId: String) {
        _gateways.update { gateways ->
            gateways.filter { it.id != gatewayId }
        }
        if (_currentGatewayId.value == gatewayId) {
            _currentGatewayId.value = null
        }
        persistGateways()
    }

    /**
     * 设置当前 Gateway
     */
    override suspend fun setCurrentGateway(gatewayId: String?) {
        _currentGatewayId.value = gatewayId
        encryptedStorage.encryptAndStore(KEY_CURRENT_GATEWAY, gatewayId ?: "")
        
        // 更新 isCurrent 标记
        _gateways.update { gateways ->
            gateways.map { it.copy(isCurrent = it.id == gatewayId) }
        }
    }

    private fun loadGateways() {
        // 从 EncryptedStorage 加载配置
        val configs = encryptedStorage.decryptAndRead(KEY_GATEWAYS) ?: "[]"
        // 简单解析（实际应用可用 JSON）
        _gateways.value = parseGateways(configs)
        
        val currentId = encryptedStorage.decryptAndRead(KEY_CURRENT_GATEWAY) ?: ""
        _currentGatewayId.value = currentId.ifBlank { null }
    }

    private fun persistGateways() {
        val configs = serializeGateways(_gateways.value)
        encryptedStorage.encryptAndStore(KEY_GATEWAYS, configs)
    }

    private fun parseGateways(configs: String): List<GatewayConfigUi> {
        // 简单实现，实际应用应使用 JSON 解析
        return emptyList()
    }

    private fun serializeGateways(gateways: List<GatewayConfigUi>): String {
        // 简单实现，实际应用应使用 JSON 序列化
        return "[]"
    }
}