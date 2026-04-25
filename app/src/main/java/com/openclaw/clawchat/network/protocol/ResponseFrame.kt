package com.openclaw.clawchat.network.protocol

import com.openclaw.clawchat.util.JsonUtils
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Response 帧定义 (Gateway 协议 v3)
 * 
 * 服务器响应请求的标准格式：
 * 
 * 成功响应:
 * ```json
 * {
 *   "type": "res",
 *   "id": "req-123",
 *   "ok": true,
 *   "payload": { ... }
 * }
 * ```
 * 
 * 错误响应:
 * ```json
 * {
 *   "type": "res",
 *   "id": "req-123",
 *   "ok": false,
 *   "error": {
 *     "code": "ERROR_CODE",
 *     "message": "Error message",
 *     "details": { ... }
 *   }
 * }
 * ```
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
    val payload: JsonElement? = null,
    
    @SerialName("error")
    val error: ResponseError? = null
) {
    /**
     * 检查响应是否成功
     */
    fun isSuccess(): Boolean = ok && error == null
    
    /**
     * 解析 payload 为指定类型
     */
    inline fun <reified T> parsePayload(deserializer: kotlinx.serialization.DeserializationStrategy<T>): T? {
        if (payload == null) return null
        return try {
            JsonUtils.json.decodeFromJsonElement(deserializer, payload)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 获取 payload 为 JsonObject
     */
    fun getPayloadAsJsonObject(): JsonObject? {
        return payload as? JsonObject
    }

    /**
     * 获取 payload 为 JsonArray
     */
    fun getPayloadAsList(): JsonArray? {
        return payload as? JsonArray
    }

    /**
     * 获取错误消息字符串
     */
    fun errorMessage(): String {
        return error?.message ?: "Unknown error"
    }
}

/**
 * 响应错误信息
 */
@Serializable
data class ResponseError(
    @SerialName("code")
    val code: String,
    
    @SerialName("message")
    val message: String,
    
    @SerialName("details")
    val details: Map<String, String>? = null
) {
    /**
     * 获取错误详情字符串
     */
    fun getDetailsString(): String {
        return details?.entries?.joinToString(", ") { "${it.key}: ${it.value}" } ?: ""
    }
}

/**
 * 标准错误码
 */
enum class ResponseErrorCode(
    val code: String,
    val description: String
) {
    // 通用错误 (1xxx)
    UNKNOWN_ERROR("UNKNOWN_ERROR", "未知错误"),
    INVALID_REQUEST("INVALID_REQUEST", "无效的请求"),
    INTERNAL_ERROR("INTERNAL_ERROR", "内部错误"),
    
    // 认证错误 (2xxx)
    AUTH_REQUIRED("AUTH_REQUIRED", "需要认证"),
    AUTH_FAILED("AUTH_FAILED", "认证失败"),
    INVALID_SIGNATURE("INVALID_SIGNATURE", "无效的签名"),
    TOKEN_EXPIRED("TOKEN_EXPIRED", "Token 已过期"),
    TOKEN_REVOKED("TOKEN_REVOKED", "Token 已撤销"),
    DEVICE_NOT_PAIRED("DEVICE_NOT_PAIRED", "设备未配对"),
    DEVICE_AUTH_DEVICE_ID_MISMATCH("DEVICE_AUTH_DEVICE_ID_MISMATCH", "设备 ID 不匹配"),
    DEVICE_AUTH_NONCE_REQUIRED("DEVICE_AUTH_NONCE_REQUIRED", "需要 Nonce"),
    DEVICE_AUTH_NONCE_MISMATCH("DEVICE_AUTH_NONCE_MISMATCH", "Nonce 不匹配"),
    DEVICE_AUTH_SIGNATURE_INVALID("DEVICE_AUTH_SIGNATURE_INVALID", "签名无效"),
    
    // 会话错误 (3xxx)
    SESSION_NOT_FOUND("SESSION_NOT_FOUND", "会话不存在"),
    SESSION_TERMINATED("SESSION_TERMINATED", "会话已终止"),
    SESSION_PAUSED("SESSION_PAUSED", "会话已暂停"),
    SESSION_LIMIT_REACHED("SESSION_LIMIT_REACHED", "会话数超限"),
    
    // 消息错误 (4xxx)
    MESSAGE_TOO_LARGE("MESSAGE_TOO_LARGE", "消息过大"),
    INVALID_ATTACHMENT("INVALID_ATTACHMENT", "无效的附件"),
    MESSAGE_SEND_FAILED("MESSAGE_SEND_FAILED", "消息发送失败"),
    
    // 资源错误 (5xxx)
    NOT_FOUND("NOT_FOUND", "资源不存在"),
    CONFLICT("CONFLICT", "资源冲突"),
    RATE_LIMIT_EXCEEDED("RATE_LIMIT_EXCEEDED", "请求频率超限")
}

/**
 * 常见响应载荷
 */

/**
 * 发送消息响应载荷
 */
@Serializable
data class SendMessageResponse(
    @SerialName("messageId")
    val messageId: String,
    
    @SerialName("sessionId")
    val sessionId: String,
    
    @SerialName("timestamp")
    val timestamp: Long,
    
    @SerialName("status")
    val status: String = "sent"
)

/**
 * 会话列表响应载荷
 */
@Serializable
data class SessionListResponse(
    @SerialName("sessions")
    val sessions: List<SessionInfo>,
    
    @SerialName("total")
    val total: Int,
    
    @SerialName("hasMore")
    val hasMore: Boolean = false
)

/**
 * 会话信息
 */
@Serializable
data class SessionInfo(
    @SerialName("id")
    val id: String,
    
    @SerialName("label")
    val label: String?,
    
    @SerialName("model")
    val model: String?,
    
    @SerialName("status")
    val status: String,
    
    @SerialName("createdAt")
    val createdAt: Long,
    
    @SerialName("lastActivityAt")
    val lastActivityAt: Long,
    
    @SerialName("messageCount")
    val messageCount: Int = 0
)

/**
 * 会话详情响应载荷
 */
@Serializable
data class SessionDetailResponse(
    @SerialName("session")
    val session: SessionInfo,
    
    @SerialName("messages")
    val messages: List<MessageInfo>? = null
)

/**
 * 消息信息
 */
@Serializable
data class MessageInfo(
    @SerialName("id")
    val id: String,
    
    @SerialName("sessionId")
    val sessionId: String,
    
    @SerialName("role")
    val role: String,
    
    @SerialName("content")
    val content: String,
    
    @SerialName("timestamp")
    val timestamp: Long,
    
    @SerialName("attachments")
    val attachments: List<AttachmentInfo>? = null,
    
    @SerialName("metadata")
    val metadata: Map<String, String>? = null
)

/**
 * 附件信息
 */
@Serializable
data class AttachmentInfo(
    @SerialName("id")
    val id: String,
    
    @SerialName("name")
    val name: String,
    
    @SerialName("mimeType")
    val mimeType: String,
    
    @SerialName("size")
    val size: Long,
    
    @SerialName("url")
    val url: String? = null
)

/**
 * 创建会话响应载荷
 */
@Serializable
data class CreateSessionResponse(
    @SerialName("session")
    val session: SessionInfo
)

/**
 * 设备状态响应载荷
 */
@Serializable
data class DeviceStatusResponse(
    @SerialName("deviceId")
    val deviceId: String,
    
    @SerialName("status")
    val status: String,
    
    @SerialName("paired")
    val paired: Boolean,
    
    @SerialName("gateway")
    val gateway: GatewayInfo?,
    
    @SerialName("lastSyncAt")
    val lastSyncAt: Long? = null
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
    val mode: String,
    
    @SerialName("url")
    val url: String? = null
)

/**
 * Ping 响应载荷
 */
@Serializable
data class PingResponse(
    @SerialName("timestamp")
    val timestamp: Long,
    
    @SerialName("latency")
    val latency: Long? = null
)

/**
 * 系统信息响应载荷
 */
@Serializable
data class SystemInfoResponse(
    @SerialName("gateway")
    val gateway: GatewayInfo,
    
    @SerialName("protocol")
    val protocol: ProtocolInfo,
    
    @SerialName("features")
    val features: List<String>? = null
)

/**
 * 协议信息
 */
@Serializable
data class ProtocolInfo(
    @SerialName("version")
    val version: String,
    
    @SerialName("minVersion")
    val minVersion: Int,
    
    @SerialName("maxVersion")
    val maxVersion: Int
)

/**
 * 解析响应帧的扩展函数
 */

/**
 * 解析为 SendMessageResponse
 */
fun ResponseFrame.parseSendMessageResponse(): SendMessageResponse? {
    return parsePayload(SendMessageResponse.serializer())
}

/**
 * 解析为 SessionListResponse
 */
fun ResponseFrame.parseSessionListResponse(): SessionListResponse? {
    return parsePayload(SessionListResponse.serializer())
}

/**
 * 解析为 CreateSessionResponse
 */
fun ResponseFrame.parseCreateSessionResponse(): CreateSessionResponse? {
    return parsePayload(CreateSessionResponse.serializer())
}

/**
 * 解析为 DeviceStatusResponse
 */
fun ResponseFrame.parseDeviceStatusResponse(): DeviceStatusResponse? {
    return parsePayload(DeviceStatusResponse.serializer())
}

/**
 * 解析为 PingResponse
 */
fun ResponseFrame.parsePingResponse(): PingResponse? {
    return parsePayload(PingResponse.serializer())
}

/**
 * 模型列表响应载荷
 */
@Serializable
data class ModelsListResponse(
    @SerialName("models")
    val models: List<ModelInfo>
)

/**
 * 模型信息
 */
@Serializable
data class ModelInfo(
    @SerialName("id")
    val id: String,

    @SerialName("name")
    val name: String? = null,

    @SerialName("provider")
    val provider: String? = null,

    @SerialName("contextWindow")
    val contextWindow: Int? = null,

    @SerialName("maxOutputTokens")
    val maxOutputTokens: Int? = null,

    @SerialName("supportsVision")
    val supportsVision: Boolean = false,

    @SerialName("supportsTools")
    val supportsTools: Boolean = true,

    @SerialName("inputPrice")
    val inputPrice: Double? = null,

    @SerialName("outputPrice")
    val outputPrice: Double? = null
)

/**
 * 解析为 ModelsListResponse
 */
fun ResponseFrame.parseModelsListResponse(): ModelsListResponse? {
    return parsePayload(ModelsListResponse.serializer())
}

// ==================== Agents API 响应 ====================

/**
 * Agent 列表响应载荷
 */
@Serializable
data class AgentsListResponse(
    @SerialName("agents")
    val agents: List<AgentInfo>
)

/**
 * Agent 信息
 */
@Serializable
data class AgentInfo(
    @SerialName("id")
    val id: String,

    @SerialName("name")
    val name: String,

    @SerialName("workspace")
    val workspace: String? = null,

    @SerialName("emoji")
    val emoji: String? = null,

    @SerialName("avatar")
    val avatar: String? = null,

    @SerialName("model")
    val model: String? = null,

    @SerialName("createdAt")
    val createdAt: Long? = null,

    @SerialName("updatedAt")
    val updatedAt: Long? = null
)

/**
 * 创建 Agent 响应载荷
 */
@Serializable
data class AgentsCreateResponse(
    @SerialName("agent")
    val agent: AgentInfo
)

/**
 * 更新 Agent 响应载荷
 */
@Serializable
data class AgentsUpdateResponse(
    @SerialName("agent")
    val agent: AgentInfo
)

/**
 * 解析为 AgentsListResponse
 */
fun ResponseFrame.parseAgentsListResponse(): AgentsListResponse? {
    return parsePayload(AgentsListResponse.serializer())
}

/**
 * 解析为 AgentsCreateResponse
 */
fun ResponseFrame.parseAgentsCreateResponse(): AgentsCreateResponse? {
    return parsePayload(AgentsCreateResponse.serializer())
}

/**
 * 解析为 AgentsUpdateResponse
 */
fun ResponseFrame.parseAgentsUpdateResponse(): AgentsUpdateResponse? {
    return parsePayload(AgentsUpdateResponse.serializer())
}

// ==================== Config API 响应 ====================

/**
 * 配置获取响应载荷
 */
@Serializable
data class ConfigGetResponse(
    @SerialName("key")
    val key: String,

    @SerialName("value")
    val value: String? = null,

    @SerialName("schema")
    val schema: ConfigSchema? = null
)

/**
 * 配置 Schema
 */
@Serializable
data class ConfigSchema(
    @SerialName("type")
    val type: String,

    @SerialName("description")
    val description: String? = null,

    @SerialName("default")
    val defaultValue: String? = null,

    @SerialName("enum")
    val enumValues: List<String>? = null,

    @SerialName("min")
    val min: Double? = null,

    @SerialName("max")
    val max: Double? = null
)

/**
 * 配置 Schema 列表响应
 */
@Serializable
data class ConfigSchemaListResponse(
    @SerialName("schemas")
    val schemas: List<ConfigSchemaEntry>
)

/**
 * 配置 Schema 条目
 */
@Serializable
data class ConfigSchemaEntry(
    @SerialName("key")
    val key: String,

    @SerialName("schema")
    val schema: ConfigSchema
)

/**
 * 解析为 ConfigGetResponse
 */
fun ResponseFrame.parseConfigGetResponse(): ConfigGetResponse? {
    return parsePayload(ConfigGetResponse.serializer())
}

/**
 * 解析为 ConfigSchemaListResponse
 */
fun ResponseFrame.parseConfigSchemaListResponse(): ConfigSchemaListResponse? {
    return parsePayload(ConfigSchemaListResponse.serializer())
}

// ==================== Channels API 响应 ====================

/**
 * 渠道状态响应载荷
 */
@Serializable
data class ChannelsStatusResponse(
    @SerialName("channels")
    val channels: List<ChannelInfo>
)

/**
 * 渠道信息
 */
@Serializable
data class ChannelInfo(
    @SerialName("id")
    val id: String,

    @SerialName("type")
    val type: String,

    @SerialName("name")
    val name: String? = null,

    @SerialName("status")
    val status: String,

    @SerialName("connected")
    val connected: Boolean = false,

    @SerialName("lastActivityAt")
    val lastActivityAt: Long? = null,

    @SerialName("metadata")
    val metadata: Map<String, String>? = null
)

/**
 * 解析为 ChannelsStatusResponse
 */
fun ResponseFrame.parseChannelsStatusResponse(): ChannelsStatusResponse? {
    return parsePayload(ChannelsStatusResponse.serializer())
}

// ==================== Sessions 扩展 API 响应 ====================

/**
 * 会话订阅响应载荷
 */
@Serializable
data class SessionsSubscribeResponse(
    @SerialName("subscribed")
    val subscribed: Boolean,

    @SerialName("sessionKeys")
    val sessionKeys: List<String>? = null
)

/**
 * 会话解析响应载荷
 */
@Serializable
data class SessionsResolveResponse(
    @SerialName("session")
    val session: SessionInfo? = null,

    @SerialName("sessions")
    val sessions: List<SessionInfo>? = null
)

/**
 * 解析为 SessionsSubscribeResponse
 */
fun ResponseFrame.parseSessionsSubscribeResponse(): SessionsSubscribeResponse? {
    return parsePayload(SessionsSubscribeResponse.serializer())
}

/**
 * 解析为 SessionsResolveResponse
 */
fun ResponseFrame.parseSessionsResolveResponse(): SessionsResolveResponse? {
    return parsePayload(SessionsResolveResponse.serializer())
}

// ==================== Chat Extensions 响应 ====================

/**
 * 消息注入响应载荷
 */
@Serializable
data class ChatInjectResponse(
    @SerialName("messageId")
    val messageId: String,

    @SerialName("sessionId")
    val sessionId: String,

    @SerialName("timestamp")
    val timestamp: Long
)

/**
 * 解析为 ChatInjectResponse
 */
fun ResponseFrame.parseChatInjectResponse(): ChatInjectResponse? {
    return parsePayload(ChatInjectResponse.serializer())
}

// ==================== Device Token API 响应 ====================

/**
 * 设备 Token 旋转响应载荷
 */
@Serializable
data class DeviceTokenRotateResponse(
    @SerialName("token")
    val token: String,

    @SerialName("createdAt")
    val createdAt: Long,

    @SerialName("expiresAt")
    val expiresAt: Long? = null
)

/**
 * 设备 Token 撤销响应载荷
 */
@Serializable
data class DeviceTokenRevokeResponse(
    @SerialName("revoked")
    val revoked: Boolean,

    @SerialName("revokedAt")
    val revokedAt: Long? = null
)

/**
 * 解析为 DeviceTokenRotateResponse
 */
fun ResponseFrame.parseDeviceTokenRotateResponse(): DeviceTokenRotateResponse? {
    return parsePayload(DeviceTokenRotateResponse.serializer())
}

/**
 * 解析为 DeviceTokenRevokeResponse
 */
fun ResponseFrame.parseDeviceTokenRevokeResponse(): DeviceTokenRevokeResponse? {
    return parsePayload(DeviceTokenRevokeResponse.serializer())
}
