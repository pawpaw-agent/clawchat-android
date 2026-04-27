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

