package com.openclaw.clawchat.network.protocol

import com.openclaw.clawchat.ui.components.ApiAttachment
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.UUID

/**
 * Chat RPC methods extracted from GatewayConnection.
 */
class ChatRpcService(
    private val rpc: suspend (String, Map<String, JsonElement>?) -> ResponseFrame
) {
    /** chat.send — 发送消息（支持附件和扩展参数） */
    suspend fun chatSend(
        sessionKey: String,
        message: String,
        attachments: List<ApiAttachment>? = null,
        mediaUrls: List<String>? = null,
        thinking: String? = null,
        deliver: Boolean? = null,
        originatingChannel: String? = null,
        originatingTo: String? = null,
        timeoutMs: Int? = null
    ): ResponseFrame {
        val params = mutableMapOf<String, JsonElement>(
            "sessionKey" to JsonPrimitive(sessionKey),
            "message" to JsonPrimitive(message),
            "idempotencyKey" to JsonPrimitive(UUID.randomUUID().toString())
        )
        if (!attachments.isNullOrEmpty()) {
            params["attachments"] = JsonArray(attachments.map { att ->
                JsonObject(mapOf(
                    "type" to JsonPrimitive(att.type),
                    "mimeType" to JsonPrimitive(att.mimeType),
                    "content" to JsonPrimitive(att.content)
                ))
            })
        }
        if (!mediaUrls.isNullOrEmpty()) {
            params["mediaUrls"] = JsonArray(mediaUrls.map { JsonPrimitive(it) })
        }
        if (thinking != null) params["thinking"] = JsonPrimitive(thinking)
        if (deliver != null) params["deliver"] = JsonPrimitive(deliver)
        if (originatingChannel != null) params["originatingChannel"] = JsonPrimitive(originatingChannel)
        if (originatingTo != null) params["originatingTo"] = JsonPrimitive(originatingTo)
        if (timeoutMs != null) params["timeoutMs"] = JsonPrimitive(timeoutMs)
        return rpc("chat.send", params)
    }

    /** chat.history */
    suspend fun chatHistory(sessionKey: String, limit: Int? = null): ResponseFrame {
        val params = mutableMapOf<String, JsonElement>(
            "sessionKey" to JsonPrimitive(sessionKey)
        )
        if (limit != null) params["limit"] = JsonPrimitive(limit)
        return rpc("chat.history", params)
    }

    /** chat.abort */
    suspend fun chatAbort(sessionKey: String, runId: String? = null): ResponseFrame {
        val params = mutableMapOf<String, JsonElement>(
            "sessionKey" to JsonPrimitive(sessionKey)
        )
        if (runId != null) params["runId"] = JsonPrimitive(runId)
        return rpc("chat.abort", params)
    }

    /** chat.inject — 注入消息到会话历史 */
    suspend fun chatInject(
        sessionKey: String,
        role: String,
        content: String,
        attachments: List<ApiAttachment>? = null
    ): ResponseFrame {
        val params = mutableMapOf<String, JsonElement>(
            "sessionKey" to JsonPrimitive(sessionKey),
            "role" to JsonPrimitive(role),
            "content" to JsonPrimitive(content)
        )
        if (!attachments.isNullOrEmpty()) {
            params["attachments"] = JsonArray(attachments.map { att ->
                JsonObject(mapOf(
                    "type" to JsonPrimitive(att.type),
                    "mimeType" to JsonPrimitive(att.mimeType),
                    "content" to JsonPrimitive(att.content)
                ))
            })
        }
        return rpc("chat.inject", params)
    }
}
