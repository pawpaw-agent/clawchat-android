package com.openclaw.clawchat.network.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

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
     * 获取错误信息（如果有）
     */
    fun getError(): ResponseError? = error
    
    /**
     * 解析 payload 为指定类型
     */
    inline fun <reified T> parsePayload(): T? {
        if (payload == null) return null
        return try {
            kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                .decodeFromJsonElement(payload)
        } catch (e: Exception) {
            null
        }
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
 * 创建错误响应的辅助函数
 */
fun errorResponse(
    requestId: String,
    errorCode: ResponseErrorCode,
    details: Map<String, String>? = null
): ResponseFrame {
    return ResponseFrame(
        id = requestId,
        ok = false,
        error = ResponseError(
            code = errorCode.code,
            message = errorCode.description,
            details = details
        )
    )
}

/**
 * 创建成功响应的辅助函数
 */
fun successResponse(
    requestId: String,
    payload: kotlinx.serialization.json.JsonElement? = null
): ResponseFrame {
    return ResponseFrame(
        id = requestId,
        ok = true,
        payload = payload
    )
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
    return parsePayload()
}

/**
 * 解析为 SessionListResponse
 */
fun ResponseFrame.parseSessionListResponse(): SessionListResponse? {
    return parsePayload()
}

/**
 * 解析为 CreateSessionResponse
 */
fun ResponseFrame.parseCreateSessionResponse(): CreateSessionResponse? {
    return parsePayload()
}

/**
 * 解析为 DeviceStatusResponse
 */
fun ResponseFrame.parseDeviceStatusResponse(): DeviceStatusResponse? {
    return parsePayload()
}

/**
 * 解析为 PingResponse
 */
fun ResponseFrame.parsePingResponse(): PingResponse? {
    return parsePayload()
}
