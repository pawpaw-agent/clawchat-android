package com.openclaw.clawchat.network.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * WebSocket 协议 v3 核心常量
 *
 * 遵循 OpenClaw Gateway 协议规范 v3
 * 参考：https://docs.openclaw.ai/gateway/protocol
 */
object WebSocketProtocol {
    /** 协议版本号（整数） */
    const val PROTOCOL_VERSION = 3

    /** WebSocket 连接路径 */
    const val WS_PATH = "/ws"
}

/**
 * 客户端信息
 *
 * 在 connect 请求和设备配对事件中使用。
 */
@Serializable
data class ClientInfo(
    @SerialName("clientId")
    val clientId: String = "openclaw-android",
    @SerialName("clientVersion")
    val clientVersion: String,
    @SerialName("platform")
    val platform: String = "android",
    @SerialName("osVersion")
    val osVersion: String = "",
    @SerialName("deviceModel")
    val deviceModel: String = "",
    @SerialName("protocolVersion")
    val protocolVersion: Int = WebSocketProtocol.PROTOCOL_VERSION
)
