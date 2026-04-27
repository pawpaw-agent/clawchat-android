package com.openclaw.clawchat.network.protocol

import com.openclaw.clawchat.util.JsonUtils
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
            JsonUtils.json.decodeFromJsonElement(deserializer, payload)
        } catch (e: Exception) {
            null
        }
    }
}

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
