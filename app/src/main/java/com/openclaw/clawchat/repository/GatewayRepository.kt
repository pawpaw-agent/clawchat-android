package com.openclaw.clawchat.repository

import com.openclaw.clawchat.ui.state.GatewayConfigUi
import kotlinx.coroutines.flow.Flow

/**
 * Gateway 配置仓库接口
 *
 * 本地存储 Gateway 连接配置。
 */
interface GatewayRepository {

    /**
     * 观察所有 Gateway 配置
     */
    fun observeGateways(): Flow<List<GatewayConfigUi>>

    /**
     * 获取当前 Gateway
     */
    fun getCurrentGateway(): GatewayConfigUi?

    /**
     * 保存 Gateway 配置
     */
    suspend fun saveGateway(gateway: GatewayConfigUi)

    /**
     * 删除 Gateway 配置
     */
    suspend fun deleteGateway(gatewayId: String)

    /**
     * 设置当前 Gateway
     */
    suspend fun setCurrentGateway(gatewayId: String?)
}