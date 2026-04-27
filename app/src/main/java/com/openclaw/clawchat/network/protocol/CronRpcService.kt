package com.openclaw.clawchat.network.protocol

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/**
 * Cron RPC methods extracted from GatewayConnection.
 */
class CronRpcService(
    private val rpc: suspend (String, Map<String, JsonElement>?) -> ResponseFrame
) {
    /** cron.list */
    suspend fun cronList(): ResponseFrame {
        return rpc("cron.list", null)
    }

    /** cron.add */
    suspend fun cronAdd(
        name: String,
        cron: String,
        sessionKey: String,
        prompt: String,
        enabled: Boolean = true
    ): ResponseFrame {
        return rpc("cron.add", mapOf(
            "name" to JsonPrimitive(name),
            "cron" to JsonPrimitive(cron),
            "sessionKey" to JsonPrimitive(sessionKey),
            "prompt" to JsonPrimitive(prompt),
            "enabled" to JsonPrimitive(enabled)
        ))
    }

    /** cron.remove */
    suspend fun cronRemove(cronId: String): ResponseFrame {
        return rpc("cron.remove", mapOf("id" to JsonPrimitive(cronId)))
    }

    /** cron.patch */
    suspend fun cronPatch(
        cronId: String,
        enabled: Boolean? = null,
        name: String? = null,
        cron: String? = null,
        sessionKey: String? = null,
        prompt: String? = null
    ): ResponseFrame {
        val params = mutableMapOf<String, JsonElement>(
            "id" to JsonPrimitive(cronId)
        )
        if (enabled != null) params["enabled"] = JsonPrimitive(enabled)
        if (name != null) params["name"] = JsonPrimitive(name)
        if (cron != null) params["cron"] = JsonPrimitive(cron)
        if (sessionKey != null) params["sessionKey"] = JsonPrimitive(sessionKey)
        if (prompt != null) params["prompt"] = JsonPrimitive(prompt)
        return rpc("cron.patch", params)
    }

    /** cron.run */
    suspend fun cronRun(cronId: String): ResponseFrame {
        return rpc("cron.run", mapOf("id" to JsonPrimitive(cronId)))
    }

    /** cron.status */
    suspend fun cronStatus(): ResponseFrame {
        return rpc("cron.status", null)
    }

    /** cron.update */
    suspend fun cronUpdate(
        cronId: String,
        enabled: Boolean? = null,
        name: String? = null,
        cron: String? = null,
        sessionKey: String? = null,
        prompt: String? = null
    ): ResponseFrame {
        val params = mutableMapOf<String, JsonElement>("id" to JsonPrimitive(cronId))
        if (enabled != null) params["enabled"] = JsonPrimitive(enabled)
        if (name != null) params["name"] = JsonPrimitive(name)
        if (cron != null) params["cron"] = JsonPrimitive(cron)
        if (sessionKey != null) params["sessionKey"] = JsonPrimitive(sessionKey)
        if (prompt != null) params["prompt"] = JsonPrimitive(prompt)
        return rpc("cron.update", params)
    }

    /** cron.runs */
    suspend fun cronRuns(cronId: String? = null): ResponseFrame {
        val params = if (cronId != null) mapOf("id" to JsonPrimitive(cronId)) else null
        return rpc("cron.runs", params)
    }
}
