package com.openclaw.clawchat.network.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Gateway 事件类型定义
 * 
 * 遵循 Gateway 协议 v3 的事件格式：
 * {
 *   "type": "event",
 *   "event": "event.name",
 *   "payload": { ... },
 *   "seq": 1,
 *   "stateVersion": "abc123"
 * }
 */

/**
 * 标准事件帧
 */
@Serializable
data class EventFrame(
    @SerialName("type")
    val type: String = "event",
    @SerialName("event")
    val event: String,
    @SerialName("payload")
    val payload: Payload,
    @SerialName("seq")
    val seq: Int? = null,
    @SerialName("stateVersion")
    val stateVersion: String? = null
)

/**
 * 连接挑战事件
 * 
 * Gateway 在 WebSocket 连接后发送
 */
@Serializable
data class ConnectChallenge(
    @SerialName("nonce")
    val nonce: String,
    @SerialName("ts")
    val timestamp: Long
)

/**
 * 连接成功事件
 * 
 * Gateway 在认证成功后发送
 */
@Serializable
data class ConnectOk(
    @SerialName("deviceToken")
    val deviceToken: String,
    @SerialName("ts")
    val timestamp: Long,
    @SerialName("gatewayInfo")
    val gatewayInfo: GatewayInfo? = null
)

/**
 * Gateway 信息
 */
@Serializable
data class GatewayInfo(
    @SerialName("id")
    val id: String,
    @SerialName("version")
    val version: String,
    @SerialName("mode")
    val mode: String
)

/**
 * 错误事件
 */
@Serializable
data class ErrorEvent(
    @SerialName("code")
    val code: String,
    @SerialName("message")
    val message: String,
    @SerialName("details")
    val details: Map<String, String>? = null
)

/**
 * 系统事件
 */
@Serializable
data class SystemEventPayload(
    @SerialName("text")
    val text: String,
    @SerialName("ts")
    val timestamp: Long? = null
)

/**
 * 助手消息事件
 */
@Serializable
data class AssistantMessagePayload(
    @SerialName("sessionId")
    val sessionId: String,
    @SerialName("content")
    val content: String,
    @SerialName("model")
    val model: String?,
    @SerialName("ts")
    val timestamp: Long
)

/**
 * 请求帧格式
 * 
 * 客户端发送请求：
 * {
 *   "type": "req",
 *   "id": "req-123",
 *   "method": "session.create",
 *   "params": { ... }
 * }
 */
@Serializable
data class RequestFrame(
    @SerialName("type")
    val type: String = "req",
    @SerialName("id")
    val id: String,
    @SerialName("method")
    val method: String,
    @SerialName("params")
    val params: Map<String, Any>? = null
)

/**
 * 响应帧格式
 * 
 * 服务器响应请求：
 * {
 *   "type": "res",
 *   "id": "req-123",
 *   "ok": true,
 *   "payload": { ... }
 * }
 * 
 * 或错误响应：
 * {
 *   "type": "res",
 *   "id": "req-123",
 *   "ok": false,
 *   "error": {
 *     "code": "ERROR_CODE",
 *     "message": "Error message"
 *   }
 * }
 */
@Serializable
data class ResponseFrame(
    @SerialName("type")
    val type: String = "res",
    @SerialName("id")
    val id: String,
    @SerialName("ok")
    val ok: Boolean,
    @SerialName("payload")
    val payload: Map<String, Any>? = null,
    @SerialName("error")
    val error: ErrorPayload? = null
)

/**
 * 错误载荷
 */
@Serializable
data class ErrorPayload(
    @SerialName("code")
    val code: String,
    @SerialName("message")
    val message: String
)

/**
 * 认证请求 (connect 方法)
 * 
 * 客户端发送：
 * {
 *   "type": "req",
 *   "id": "auth-1",
 *   "method": "connect",
 *   "params": {
 *     "device": { "id": "...", "publicKey": "..." },
 *     "client": { "id": "...", "version": "..." },
 *     "nonce": "...",
 *     "signature": "..."
 *   }
 * }
 */
@Serializable
data class ConnectRequest(
    @SerialName("device")
    val device: DeviceInfo,
    @SerialName("client")
    val client: ClientInfo,
    @SerialName("nonce")
    val nonce: String,
    @SerialName("signature")
    val signature: String,
    @SerialName("token")
    val token: String? = null
)

/**
 * 设备信息
 */
@Serializable
data class DeviceInfo(
    @SerialName("id")
    val id: String,
    @SerialName("publicKey")
    val publicKey: String
)

/**
 * 解析事件帧
 */
fun String.toEventFrame(): EventFrame? {
    return try {
        kotlinx.serialization.json.Json { ignoreUnknownKeys = true }.decodeFromString(this)
    } catch (e: Exception) {
        null
    }
}

/**
 * 解析连接挑战
 */
fun String.toConnectChallenge(): ConnectChallenge? {
    return try {
        val frame = toEventFrame()
        if (frame?.event == "connect.challenge") {
            kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                .decodeFromString<ConnectChallenge>(frame.payload.toString())
        } else {
            null
        }
    } catch (e: Exception) {
        null
    }
}

/**
 * 解析连接成功
 */
fun String.toConnectOk(): ConnectOk? {
    return try {
        val frame = toEventFrame()
        if (frame?.event == "connect.ok") {
            kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                .decodeFromString<ConnectOk>(frame.payload.toString())
        } else {
            null
        }
    } catch (e: Exception) {
        null
    }
}

/**
 * 解析错误事件
 */
fun String.toErrorEvent(): ErrorEvent? {
    return try {
        val frame = toEventFrame()
        if (frame?.event == "error") {
            kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                .decodeFromString<ErrorEvent>(frame.payload.toString())
        } else {
            null
        }
    } catch (e: Exception) {
        null
    }
}
