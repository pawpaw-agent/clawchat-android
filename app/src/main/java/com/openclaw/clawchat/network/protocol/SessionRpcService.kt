package com.openclaw.clawchat.network.protocol

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Session RPC methods extracted from GatewayConnection.
 * Covers sessions.*, sessions.compaction.*, sessions.messages.*, sessions.send, sessions.abort.
 */
class SessionRpcService(
    private val rpc: suspend (String, Map<String, JsonElement>?) -> ResponseFrame
) {
    /** sessions.list */
    suspend fun sessionsList(
        limit: Int? = null,
        activeMinutes: Int? = null,
        includeDerivedTitles: Boolean = true,
        includeLastMessage: Boolean = true
    ): ResponseFrame {
        val params = mutableMapOf<String, JsonElement>()
        if (limit != null) params["limit"] = JsonPrimitive(limit)
        if (activeMinutes != null) params["activeMinutes"] = JsonPrimitive(activeMinutes)
        params["includeDerivedTitles"] = JsonPrimitive(includeDerivedTitles)
        params["includeLastMessage"] = JsonPrimitive(includeLastMessage)
        return rpc("sessions.list", params.ifEmpty { null })
    }

    /** sessions.patch */
    suspend fun sessionsPatch(sessionKey: String, verboseLevel: String? = null): ResponseFrame {
        val params = mutableMapOf<String, JsonElement>(
            "key" to JsonPrimitive(sessionKey)
        )
        if (verboseLevel != null) params["verboseLevel"] = JsonPrimitive(verboseLevel)
        return rpc("sessions.patch", params)
    }

    /** sessions.steer */
    suspend fun sessionsSteer(sessionKey: String, text: String): ResponseFrame {
        val params = mapOf(
            "sessionKey" to JsonPrimitive(sessionKey),
            "text" to JsonPrimitive(text)
        )
        return rpc("sessions.steer", params)
    }

    /** sessions.create */
    suspend fun sessionsCreate(
        key: String? = null,
        agentId: String? = null,
        label: String? = null,
        model: String? = null,
        message: String? = null
    ): ResponseFrame {
        val params = mutableMapOf<String, JsonElement>()
        if (key != null) params["key"] = JsonPrimitive(key)
        if (agentId != null) params["agentId"] = JsonPrimitive(agentId)
        if (label != null) params["label"] = JsonPrimitive(label)
        if (model != null) params["model"] = JsonPrimitive(model)
        if (message != null) params["message"] = JsonPrimitive(message)
        return rpc("sessions.create", params.ifEmpty { null })
    }

    /** sessions.delete */
    suspend fun sessionsDelete(
        sessionKey: String,
        deleteTranscript: Boolean = true
    ): ResponseFrame {
        val params = mutableMapOf<String, JsonElement>(
            "key" to JsonPrimitive(sessionKey)
        )
        if (deleteTranscript) params["deleteTranscript"] = JsonPrimitive(true)
        return rpc("sessions.delete", params)
    }

    /** sessions.reset */
    suspend fun sessionsReset(
        sessionKey: String,
        reason: String = "reset"
    ): ResponseFrame {
        val params = mutableMapOf<String, JsonElement>(
            "key" to JsonPrimitive(sessionKey),
            "reason" to JsonPrimitive(reason)
        )
        return rpc("sessions.reset", params)
    }

    /** sessions.preview */
    suspend fun sessionsPreview(
        keys: List<String>,
        limit: Int? = null,
        maxChars: Int? = null
    ): ResponseFrame {
        val params = mutableMapOf<String, JsonElement>(
            "keys" to JsonArray(keys.map { JsonPrimitive(it) })
        )
        if (limit != null) params["limit"] = JsonPrimitive(limit)
        if (maxChars != null) params["maxChars"] = JsonPrimitive(maxChars)
        return rpc("sessions.preview", params)
    }

    /** sessions.usage */
    suspend fun sessionsUsage(
        sessionKey: String? = null,
        startDate: String? = null,
        endDate: String? = null,
        limit: Int? = null
    ): ResponseFrame {
        val params = mutableMapOf<String, JsonElement>()
        if (sessionKey != null) params["key"] = JsonPrimitive(sessionKey)
        if (startDate != null) params["startDate"] = JsonPrimitive(startDate)
        if (endDate != null) params["endDate"] = JsonPrimitive(endDate)
        if (limit != null) params["limit"] = JsonPrimitive(limit)
        return rpc("sessions.usage", params.ifEmpty { null })
    }

    /** sessions.messages.subscribe */
    suspend fun sessionsMessagesSubscribe(sessionKey: String): ResponseFrame {
        return rpc("sessions.messages.subscribe", mapOf("key" to JsonPrimitive(sessionKey)))
    }

    /** sessions.messages.unsubscribe */
    suspend fun sessionsMessagesUnsubscribe(sessionKey: String): ResponseFrame {
        return rpc("sessions.messages.unsubscribe", mapOf("key" to JsonPrimitive(sessionKey)))
    }

    /** sessions.subscribe */
    suspend fun sessionsSubscribe(): ResponseFrame {
        return rpc("sessions.subscribe", null)
    }

    /** sessions.unsubscribe */
    suspend fun sessionsUnsubscribe(): ResponseFrame {
        return rpc("sessions.unsubscribe", null)
    }

    /** sessions.resolve */
    suspend fun sessionsResolve(
        key: String? = null,
        sessionId: String? = null,
        label: String? = null,
        agentId: String? = null
    ): ResponseFrame {
        val params = mutableMapOf<String, JsonElement>()
        if (key != null) params["key"] = JsonPrimitive(key)
        if (sessionId != null) params["sessionId"] = JsonPrimitive(sessionId)
        if (label != null) params["label"] = JsonPrimitive(label)
        if (agentId != null) params["agentId"] = JsonPrimitive(agentId)
        return rpc("sessions.resolve", params.ifEmpty { null })
    }

    /** sessions.send */
    suspend fun sessionsSend(
        sessionKey: String,
        message: String,
        idempotencyKey: String? = null
    ): ResponseFrame {
        val params = mutableMapOf<String, JsonElement>(
            "sessionKey" to JsonPrimitive(sessionKey),
            "message" to JsonPrimitive(message)
        )
        if (idempotencyKey != null) params["idempotencyKey"] = JsonPrimitive(idempotencyKey)
        return rpc("sessions.send", params)
    }

    /** sessions.abort */
    suspend fun sessionsAbort(sessionKey: String, runId: String? = null): ResponseFrame {
        val params = mutableMapOf<String, JsonElement>("sessionKey" to JsonPrimitive(sessionKey))
        if (runId != null) params["runId"] = JsonPrimitive(runId)
        return rpc("sessions.abort", params)
    }

    // ── Compaction ──

    /** sessions.compaction.list */
    suspend fun sessionsCompactionList(sessionKey: String): ResponseFrame {
        return rpc("sessions.compaction.list", mapOf("key" to JsonPrimitive(sessionKey)))
    }

    /** sessions.compaction.get */
    suspend fun sessionsCompactionGet(sessionKey: String, checkpointId: String): ResponseFrame {
        return rpc("sessions.compaction.get", mapOf(
            "key" to JsonPrimitive(sessionKey),
            "checkpointId" to JsonPrimitive(checkpointId)
        ))
    }

    /** sessions.compaction.branch */
    suspend fun sessionsCompactionBranch(
        sessionKey: String,
        checkpointId: String,
        newKey: String? = null,
        label: String? = null
    ): ResponseFrame {
        val params = mutableMapOf<String, JsonElement>(
            "key" to JsonPrimitive(sessionKey),
            "checkpointId" to JsonPrimitive(checkpointId)
        )
        if (newKey != null) params["newKey"] = JsonPrimitive(newKey)
        if (label != null) params["label"] = JsonPrimitive(label)
        return rpc("sessions.compaction.branch", params)
    }

    /** sessions.compaction.restore */
    suspend fun sessionsCompactionRestore(sessionKey: String, checkpointId: String): ResponseFrame {
        return rpc("sessions.compaction.restore", mapOf(
            "key" to JsonPrimitive(sessionKey),
            "checkpointId" to JsonPrimitive(checkpointId)
        ))
    }

    /** sessions.compact */
    suspend fun sessionsCompact(sessionKey: String, reason: String? = null): ResponseFrame {
        val params = mutableMapOf<String, JsonElement>("key" to JsonPrimitive(sessionKey))
        if (reason != null) params["reason"] = JsonPrimitive(reason)
        return rpc("sessions.compact", params)
    }
}
