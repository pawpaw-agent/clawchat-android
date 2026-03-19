package com.openclaw.clawchat.network.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Event 帧定义 (Gateway 协议 v3)
 * 
 * 服务器推送事件的标准格式：
 * ```json
 * {
 *   "type": "event",
 *   "event": "session.message",
 *   "payload": { ... },
 *   "seq": 1,
 *   "stateVersion": "abc123"
 * }
 * ```
 */
@Serializable
data class EventFrame(
    @SerialName("type")
    val type: String = "event",
    
    @SerialName("event")
    val event: String,
    
    @SerialName("payload")
    val payload: JsonElement? = null,
    
    @SerialName("seq")
    val seq: Int? = null,
    
    @SerialName("stateVersion")
    val stateVersion: String? = null
) {
    /**
     * 解析 payload 为指定类型
     */
    inline fun <reified T> parsePayload(deserializer: kotlinx.serialization.DeserializationStrategy<T>): T? {
        if (payload == null) return null
        return try {
            kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                .decodeFromJsonElement(deserializer, payload)
        } catch (e: Exception) {
            null
        }
    }
}

/**
 * Gateway 支持的事件列表
 */
enum class GatewayEvent(val value: String) {
    // 消息相关
    SESSION_MESSAGE("session.message"),
    SESSION_MESSAGE_UPDATE("session.message.update"),
    SESSION_MESSAGE_DELETE("session.message.delete"),
    
    // 会话相关
    SESSION_CREATE("session.create"),
    SESSION_UPDATE("session.update"),
    SESSION_TERMINATE("session.terminate"),
    SESSION_PAUSE("session.pause"),
    SESSION_RESUME("session.resume"),
    
    // 输入状态
    SESSION_TYPING("session.typing"),
    SESSION_THINKING("session.thinking"),
    
    // 连接相关
    CONNECT_CHALLENGE("connect.challenge"),
    CONNECT_OK("connect.ok"),
    CONNECT_ERROR("connect.error"),
    DISCONNECT("disconnect"),
    
    // 设备相关
    DEVICE_STATUS_UPDATE("device.status.update"),
    DEVICE_PAIRING_REQUEST("device.pairing.request"),
    DEVICE_PAIRING_APPROVED("device.pairing.approved"),
    DEVICE_PAIRING_REJECTED("device.pairing.rejected"),
    
    // 系统相关
    SYSTEM_NOTIFICATION("system.notification"),
    SYSTEM_ERROR("system.error"),
    SYSTEM_UPDATE("system.update"),
    
    // 错误
    ERROR("error")
}

/**
 * 常见事件载荷
 */

/**
 * 新消息事件载荷
 */
@Serializable
data class SessionMessagePayload(
    @SerialName("sessionId")
    val sessionId: String,
    
    @SerialName("message")
    val message: MessageInfo,
    
    @SerialName("seq")
    val seq: Int? = null
)

/**
 * 消息更新事件载荷
 */
@Serializable
data class SessionMessageUpdatePayload(
    @SerialName("sessionId")
    val sessionId: String,
    
    @SerialName("messageId")
    val messageId: String,
    
    @SerialName("updates")
    val updates: MessageUpdates
)

/**
 * 消息更新内容
 */
@Serializable
data class MessageUpdates(
    @SerialName("content")
    val content: String? = null,
    
    @SerialName("metadata")
    val metadata: Map<String, String>? = null,
    
    @SerialName("status")
    val status: String? = null
)

/**
 * 会话创建事件载荷
 */
@Serializable
data class SessionCreatePayload(
    @SerialName("session")
    val session: SessionInfo
)

/**
 * 会话更新事件载荷
 */
@Serializable
data class SessionUpdatePayload(
    @SerialName("sessionId")
    val sessionId: String,
    
    @SerialName("updates")
    val updates: SessionUpdates
)

/**
 * 会话更新内容
 */
@Serializable
data class SessionUpdates(
    @SerialName("label")
    val label: String? = null,
    
    @SerialName("status")
    val status: String? = null,
    
    @SerialName("metadata")
    val metadata: Map<String, String>? = null
)

/**
 * 会话终止事件载荷
 */
@Serializable
data class SessionTerminatePayload(
    @SerialName("sessionId")
    val sessionId: String,
    
    @SerialName("reason")
    val reason: String?,
    
    @SerialName("terminatedAt")
    val terminatedAt: Long
)

/**
 * 会话暂停事件载荷
 */
@Serializable
data class SessionPausePayload(
    @SerialName("sessionId")
    val sessionId: String,
    
    @SerialName("pausedAt")
    val pausedAt: Long
)

/**
 * 会话恢复事件载荷
 */
@Serializable
data class SessionResumePayload(
    @SerialName("sessionId")
    val sessionId: String,
    
    @SerialName("resumedAt")
    val resumedAt: Long
)

/**
 * 输入状态事件载荷
 */
@Serializable
data class SessionTypingPayload(
    @SerialName("sessionId")
    val sessionId: String,
    
    @SerialName("isTyping")
    val isTyping: Boolean,
    
    @SerialName("userId")
    val userId: String? = null
)

/**
 * 思考状态事件载荷
 */
@Serializable
data class SessionThinkingPayload(
    @SerialName("sessionId")
    val sessionId: String,
    
    @SerialName("isThinking")
    val isThinking: Boolean,
    
    @SerialName("model")
    val model: String? = null
)

/**
 * 连接挑战事件载荷
 */
@Serializable
data class ConnectChallengePayload(
    @SerialName("nonce")
    val nonce: String,
    
    @SerialName("ts")
    val timestamp: Long
)

/**
 * 连接成功事件载荷
 */
@Serializable
data class ConnectOkPayload(
    @SerialName("deviceToken")
    val deviceToken: String,
    
    @SerialName("ts")
    val timestamp: Long,
    
    @SerialName("gatewayInfo")
    val gatewayInfo: GatewayInfo? = null
)

/**
 * 连接错误事件载荷
 */
@Serializable
data class ConnectErrorPayload(
    @SerialName("code")
    val code: String,
    
    @SerialName("message")
    val message: String,
    
    @SerialName("details")
    val details: Map<String, String>? = null
)

/**
 * 断开连接事件载荷
 */
@Serializable
data class DisconnectPayload(
    @SerialName("reason")
    val reason: String?,
    
    @SerialName("ts")
    val timestamp: Long
)

/**
 * 设备状态更新事件载荷
 */
@Serializable
data class DeviceStatusUpdatePayload(
    @SerialName("deviceId")
    val deviceId: String,
    
    @SerialName("status")
    val status: String,
    
    @SerialName("metadata")
    val metadata: Map<String, String>? = null
)

/**
 * 设备配对请求事件载荷
 */
@Serializable
data class DevicePairingRequestPayload(
    @SerialName("deviceId")
    val deviceId: String,
    
    @SerialName("publicKey")
    val publicKey: String,
    
    @SerialName("clientInfo")
    val clientInfo: ClientInfo,
    
    @SerialName("requestedAt")
    val requestedAt: Long
)

/**
 * 设备配对批准事件载荷
 */
@Serializable
data class DevicePairingApprovedPayload(
    @SerialName("deviceId")
    val deviceId: String,
    
    @SerialName("deviceToken")
    val deviceToken: String,
    
    @SerialName("approvedAt")
    val approvedAt: Long
)

/**
 * 设备配对拒绝事件载荷
 */
@Serializable
data class DevicePairingRejectedPayload(
    @SerialName("deviceId")
    val deviceId: String,
    
    @SerialName("reason")
    val reason: String?,
    
    @SerialName("rejectedAt")
    val rejectedAt: Long
)

/**
 * 系统通知事件载荷
 */
@Serializable
data class SystemNotificationPayload(
    @SerialName("title")
    val title: String,
    
    @SerialName("message")
    val message: String,
    
    @SerialName("type")
    val type: String? = null,
    
    @SerialName("action")
    val action: SystemAction? = null
)

/**
 * 系统操作
 */
@Serializable
data class SystemAction(
    @SerialName("type")
    val type: String,
    
    @SerialName("params")
    val params: Map<String, String>? = null
)

/**
 * 系统错误事件载荷
 */
@Serializable
data class SystemErrorPayload(
    @SerialName("code")
    val code: String,
    
    @SerialName("message")
    val message: String,
    
    @SerialName("details")
    val details: Map<String, String>? = null,
    
    @SerialName("recoverable")
    val recoverable: Boolean = false
)

/**
 * 系统更新事件载荷
 */
@Serializable
data class SystemUpdatePayload(
    @SerialName("version")
    val version: String,
    
    @SerialName("changes")
    val changes: List<String>? = null,
    
    @SerialName("required")
    val required: Boolean = false
)

/**
 * 错误事件载荷
 */
@Serializable
data class ErrorPayload(
    @SerialName("code")
    val code: String,
    
    @SerialName("message")
    val message: String,
    
    @SerialName("details")
    val details: Map<String, String>? = null,
    
    @SerialName("requestId")
    val requestId: String? = null
)

/**
 * 解析事件帧的扩展函数
 */

/**
 * 解析为 SessionMessagePayload
 */
fun EventFrame.parseSessionMessagePayload(): SessionMessagePayload? {
    return parsePayload(SessionMessagePayload.serializer())
}

/**
 * 解析为 SessionCreatePayload
 */
fun EventFrame.parseSessionCreatePayload(): SessionCreatePayload? {
    return parsePayload(SessionCreatePayload.serializer())
}

/**
 * 解析为 ConnectOkPayload
 */
fun EventFrame.parseConnectOkPayload(): ConnectOkPayload? {
    return parsePayload(ConnectOkPayload.serializer())
}

/**
 * 解析为 ConnectChallengePayload
 */
fun EventFrame.parseConnectChallengePayload(): ConnectChallengePayload? {
    return parsePayload(ConnectChallengePayload.serializer())
}

/**
 * 解析为 SystemNotificationPayload
 */
fun EventFrame.parseSystemNotificationPayload(): SystemNotificationPayload? {
    return parsePayload(SystemNotificationPayload.serializer())
}

/**
 * 解析为 ErrorPayload
 */
fun EventFrame.parseErrorPayload(): ErrorPayload? {
    return parsePayload(ErrorPayload.serializer())
}


