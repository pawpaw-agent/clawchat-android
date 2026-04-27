package com.openclaw.clawchat.network.protocol

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/**
 * Agent RPC methods extracted from GatewayConnection.
 */
class AgentRpcService(
    private val rpc: suspend (String, Map<String, JsonElement>?) -> ResponseFrame
) {
    /** agents.list */
    suspend fun agentsList(): ResponseFrame {
        return rpc("agents.list", null)
    }

    /** agents.create */
    suspend fun agentsCreate(
        name: String,
        workspace: String,
        emoji: String? = null,
        avatar: String? = null
    ): ResponseFrame {
        val params = mutableMapOf<String, JsonElement>(
            "name" to JsonPrimitive(name),
            "workspace" to JsonPrimitive(workspace)
        )
        if (emoji != null) params["emoji"] = JsonPrimitive(emoji)
        if (avatar != null) params["avatar"] = JsonPrimitive(avatar)
        return rpc("agents.create", params)
    }

    /** agents.update */
    suspend fun agentsUpdate(
        agentId: String,
        name: String? = null,
        workspace: String? = null,
        model: String? = null,
        avatar: String? = null
    ): ResponseFrame {
        val params = mutableMapOf<String, JsonElement>(
            "agentId" to JsonPrimitive(agentId)
        )
        if (name != null) params["name"] = JsonPrimitive(name)
        if (workspace != null) params["workspace"] = JsonPrimitive(workspace)
        if (model != null) params["model"] = JsonPrimitive(model)
        if (avatar != null) params["avatar"] = JsonPrimitive(avatar)
        return rpc("agents.update", params)
    }

    /** agents.delete */
    suspend fun agentsDelete(
        agentId: String,
        deleteFiles: Boolean = false
    ): ResponseFrame {
        val params = mutableMapOf<String, JsonElement>(
            "agentId" to JsonPrimitive(agentId)
        )
        if (deleteFiles) params["deleteFiles"] = JsonPrimitive(true)
        return rpc("agents.delete", params)
    }
}
