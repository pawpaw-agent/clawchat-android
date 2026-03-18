package com.openclaw.clawchat.network.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Request 帧定义 (Gateway 协议 v3)
 * 
 * 客户端发送请求的标准格式：
 * ```json
 * {
 *   "type": "req",
 *   "id": "req-123",
 *   "method": "session.send",
 *   "params": { ... }
 * }
 * ```
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
    val params: Map<String, JsonElement>? = null
)

/**
 * 请求 ID 生成器
 */
object RequestIdGenerator {
    private var requestCounter = 0L
    
    /**
     * 生成唯一的请求 ID
     */
    fun generateRequestId(): String {
        val counter = requestCounter++
        return "req-${System.currentTimeMillis()}-$counter"
    }
}

/**
 * Gateway 支持的方法列表
 */
enum class GatewayMethod(val value: String) {
    // 消息相关
    SEND_MESSAGE("session.send"),
    GET_MESSAGES("session.messages"),
    
    // 会话相关
    GET_SESSIONS("session.list"),
    GET_SESSION("session.get"),
    CREATE_SESSION("session.create"),
    TERMINATE_SESSION("session.terminate"),
    PAUSE_SESSION("session.pause"),
    RESUME_SESSION("session.resume"),
    
    // 设备相关
    GET_DEVICE_STATUS("device.status"),
    UPDATE_DEVICE_STATUS("device.update"),
    
    // 认证相关
    CONNECT("connect"),
    DISCONNECT("disconnect"),
    
    // 系统相关
    PING("ping"),
    GET_INFO("system.info")
}

/**
 * 请求参数构建器
 */
class RequestParamsBuilder {
    private val params = mutableMapOf<String, kotlinx.serialization.json.JsonElement>()
    
    private val json = kotlinx.serialization.json.Json
    
    fun putString(key: String, value: String): RequestParamsBuilder {
        params[key] = kotlinx.serialization.json.JsonPrimitive(value)
        return this
    }
    
    fun putInt(key: String, value: Int): RequestParamsBuilder {
        params[key] = kotlinx.serialization.json.JsonPrimitive(value)
        return this
    }
    
    fun putLong(key: String, value: Long): RequestParamsBuilder {
        params[key] = kotlinx.serialization.json.JsonPrimitive(value)
        return this
    }
    
    fun putBoolean(key: String, value: Boolean): RequestParamsBuilder {
        params[key] = kotlinx.serialization.json.JsonPrimitive(value)
        return this
    }
    
    fun <T> putObject(key: String, value: T, serializer: kotlinx.serialization.KSerializer<T>): RequestParamsBuilder {
        params[key] = json.encodeToJsonElement(serializer, value)
        return this
    }
    
    fun build(): Map<String, kotlinx.serialization.json.JsonElement> {
        return params.toMap()
    }
}

/**
 * 创建请求帧的辅助函数
 */
fun requestFrame(
    method: GatewayMethod,
    params: RequestParamsBuilder.() -> Unit = {}
): RequestFrame {
    val builder = RequestParamsBuilder()
    builder.params()
    
    return RequestFrame(
        id = RequestIdGenerator.generateRequestId(),
        method = method.value,
        params = builder.build()
    )
}

/**
 * 常用请求参数
 */

/**
 * 发送消息参数
 */
@Serializable
data class SendMessageParams(
    @SerialName("sessionId")
    val sessionId: String,
    
    @SerialName("content")
    val content: String,
    
    @SerialName("attachments")
    val attachments: List<AttachmentParams>? = null,
    
    @SerialName("metadata")
    val metadata: Map<String, String>? = null
)

/**
 * 附件参数
 */
@Serializable
data class AttachmentParams(
    @SerialName("id")
    val id: String,
    
    @SerialName("name")
    val name: String,
    
    @SerialName("mimeType")
    val mimeType: String,
    
    @SerialName("size")
    val size: Long,
    
    @SerialName("url")
    val url: String? = null,
    
    @SerialName("base64")
    val base64: String? = null
)

/**
 * 创建会话参数
 */
@Serializable
data class CreateSessionParams(
    @SerialName("model")
    val model: String? = null,
    
    @SerialName("thinking")
    val thinking: Boolean? = null,
    
    @SerialName("metadata")
    val metadata: Map<String, String>? = null
)

/**
 * 获取会话参数
 */
@Serializable
data class GetSessionParams(
    @SerialName("sessionId")
    val sessionId: String
)

/**
 * 终止会话参数
 */
@Serializable
data class TerminateSessionParams(
    @SerialName("sessionId")
    val sessionId: String,
    
    @SerialName("reason")
    val reason: String? = null
)

/**
 * 获取消息参数
 */
@Serializable
data class GetMessagesParams(
    @SerialName("sessionId")
    val sessionId: String,
    
    @SerialName("limit")
    val limit: Int? = null,
    
    @SerialName("before")
    val before: String? = null,
    
    @SerialName("after")
    val after: String? = null
)

/**
 * 更新设备状态参数
 */
@Serializable
data class UpdateDeviceStatusParams(
    @SerialName("status")
    val status: String,
    
    @SerialName("metadata")
    val metadata: Map<String, String>? = null
)

/**
 * Ping 参数
 */
@Serializable
data class PingParams(
    @SerialName("timestamp")
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * 构建发送消息请求
 */
fun send_messageRequest(
    sessionId: String,
    content: String,
    attachments: List<AttachmentParams>? = null
): RequestFrame {
    return requestFrame(GatewayMethod.SEND_MESSAGE) {
        putString("sessionId", sessionId)
        putString("content", content)
        if (attachments != null) {
            // 需要序列化为 JsonElement
        }
    }
}

/**
 * 构建获取会话列表请求
 */
fun getSessionListRequest(): RequestFrame {
    return requestFrame(GatewayMethod.GET_SESSIONS)
}

/**
 * 构建创建会话请求
 */
fun createSessionRequest(
    model: String? = null,
    thinking: Boolean? = null
): RequestFrame {
    return requestFrame(GatewayMethod.CREATE_SESSION) {
        if (model != null) putString("model", model)
        if (thinking != null) putBoolean("thinking", thinking)
    }
}

/**
 * 构建终止会话请求
 */
fun terminateSessionRequest(
    sessionId: String,
    reason: String? = null
): RequestFrame {
    return requestFrame(GatewayMethod.TERMINATE_SESSION) {
        putString("sessionId", sessionId)
        if (reason != null) putString("reason", reason)
    }
}

/**
 * 构建 Ping 请求
 */
fun pingRequest(): RequestFrame {
    return requestFrame(GatewayMethod.PING) {
        putLong("timestamp", System.currentTimeMillis())
    }
}
