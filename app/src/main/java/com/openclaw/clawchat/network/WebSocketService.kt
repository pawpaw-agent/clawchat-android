package com.openclaw.clawchat.network

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.WebSocket

/**
 * WebSocket 服务接口
 * 
 * 负责管理 WebSocket 连接、消息收发、自动重连
 */
interface WebSocketService {
    /** 连接状态流 */
    val connectionState: StateFlow<WebSocketConnectionState>
    
    /** 消息接收流 */
    val incomingMessages: Flow<GatewayMessage>
    
    /**
     * 建立 WebSocket 连接
     * 
     * @param url WebSocket 服务器地址 (ws:// 或 wss://)
     * @param token 可选的认证令牌
     * @return 连接结果
     */
    suspend fun connect(url: String, token: String?): Result<Unit>
    
    /**
     * 发送消息
     * 
     * @param message 要发送的消息
     * @return 发送结果
     */
    suspend fun send(message: GatewayMessage): Result<Unit>
    
    /**
     * 断开连接
     * 
     * @return 断开结果
     */
    suspend fun disconnect(): Result<Unit>
    
    /**
     * 测量连接延迟
     * 
     * @return 延迟毫秒数，失败返回 null
     */
    suspend fun measureLatency(): Long?
}

/**
 * WebSocket 连接状态
 */
sealed class WebSocketConnectionState {
    /** 已断开 */
    data object Disconnected : WebSocketConnectionState()
    
    /** 连接中 */
    data object Connecting : WebSocketConnectionState()
    
    /** 已连接 */
    data object Connected : WebSocketConnectionState()
    
    /** 正在断开 */
    data class Disconnecting(val reason: String) : WebSocketConnectionState()
    
    /** 错误状态 */
    data class Error(val throwable: Throwable) : WebSocketConnectionState()
}
