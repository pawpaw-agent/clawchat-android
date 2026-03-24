package com.openclaw.clawchat.network.protocol

import com.openclaw.clawchat.util.JsonUtils
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import java.util.concurrent.atomic.AtomicLong

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
    private val requestCounter = AtomicLong(0)
    
    /**
     * Generate a unique request ID (thread-safe).
     */
    fun generateRequestId(): String {
        val counter = requestCounter.getAndIncrement()
        return "req-${System.currentTimeMillis()}-$counter"
    }
}

/**
 * Gateway 支持的方法列表
 */
/**
 * Gateway 支持的方法列表（Protocol v3 源码验证）
 */
enum class GatewayMethod(val value: String) {
    // 认证
    CONNECT("connect"),

    // 聊天（chat.* 命名空间）
    CHAT_SEND("chat.send"),
    CHAT_HISTORY("chat.history"),
    CHAT_INJECT("chat.inject"),
    CHAT_ABORT("chat.abort"),

    // 会话（sessions.* 命名空间）
    SESSIONS_LIST("sessions.list"),
    SESSIONS_RESET("sessions.reset"),
    SESSIONS_DELETE("sessions.delete"),
    SESSIONS_PATCH("sessions.patch"),

    // 设备
    DEVICE_TOKEN_ROTATE("device.token.rotate"),
    DEVICE_TOKEN_REVOKE("device.token.revoke"),

    // 系统
    PING("ping"),
}

/**
 * 请求参数构建器
 */
class RequestParamsBuilder {
    private val params = mutableMapOf<String, kotlinx.serialization.json.JsonElement>()
    
    private val json = JsonUtils.jsonWithDefaults
    
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
 * 构建 chat.send 请求
 */
fun chatSendRequest(
    sessionKey: String,
    message: String,
    idempotencyKey: String = java.util.UUID.randomUUID().toString()
): RequestFrame {
    return requestFrame(GatewayMethod.CHAT_SEND) {
        putString("sessionKey", sessionKey)
        putString("message", message)
        putString("idempotencyKey", idempotencyKey)
    }
}

/**
 * 构建 sessions.list 请求
 */
fun sessionsListRequest(): RequestFrame {
    return requestFrame(GatewayMethod.SESSIONS_LIST)
}

/**
 * 构建 chat.abort 请求
 */
fun chatAbortRequest(
    sessionKey: String,
    runId: String? = null
): RequestFrame {
    return requestFrame(GatewayMethod.CHAT_ABORT) {
        putString("sessionKey", sessionKey)
        if (runId != null) putString("runId", runId)
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
