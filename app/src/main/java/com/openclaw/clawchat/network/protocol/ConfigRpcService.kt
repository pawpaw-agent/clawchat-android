package com.openclaw.clawchat.network.protocol

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/**
 * Config RPC methods extracted from GatewayConnection.
 */
class ConfigRpcService(
    private val rpc: suspend (String, Map<String, JsonElement>?) -> ResponseFrame
) {
    /** config.get */
    suspend fun configGet(key: String? = null): ResponseFrame {
        val params = if (key != null) mapOf("key" to JsonPrimitive(key)) else null
        return rpc("config.get", params)
    }

    /** config.set */
    suspend fun configSet(key: String, value: String): ResponseFrame {
        return rpc("config.set", mapOf(
            "key" to JsonPrimitive(key),
            "value" to JsonPrimitive(value)
        ))
    }

    /** config.patch */
    suspend fun configPatch(patches: Map<String, String>): ResponseFrame {
        return rpc("config.patch", patches.mapValues { JsonPrimitive(it.value) })
    }

    /** config.schema */
    suspend fun configSchema(key: String? = null): ResponseFrame {
        val params = if (key != null) mapOf("key" to JsonPrimitive(key)) else null
        return rpc("config.schema", params)
    }

    /** config.apply */
    suspend fun configApply(key: String? = null): ResponseFrame {
        val params = if (key != null) mapOf("key" to JsonPrimitive(key)) else null
        return rpc("config.apply", params)
    }
}
