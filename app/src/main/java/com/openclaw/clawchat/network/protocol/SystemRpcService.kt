package com.openclaw.clawchat.network.protocol

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * System RPC methods extracted from GatewayConnection.
 * Covers health, status, gateway identity, wizard, update, logs,
 * models, tools, channels, device token, device pair, usage, talk, latency.
 */
class SystemRpcService(
    private val rpc: suspend (String, Map<String, JsonElement>?) -> ResponseFrame
) {
    // ── Health / Status ──

    suspend fun health(): ResponseFrame = rpc("health", null)
    suspend fun status(): ResponseFrame = rpc("status", null)
    suspend fun gatewayIdentityGet(): ResponseFrame = rpc("gateway.identity.get", null)

    /** Measure latency using health RPC. Returns ms or null. */
    suspend fun measureLatency(): Long? {
        return try {
            val start = System.currentTimeMillis()
            val response = health()
            if (response.isSuccess()) System.currentTimeMillis() - start else null
        } catch (e: Exception) {
            null
        }
    }

    // ── Wizard ──

    suspend fun wizardStart(): ResponseFrame = rpc("wizard.start", null)

    suspend fun wizardNext(step: String, data: JsonObject? = null): ResponseFrame {
        val params = mutableMapOf<String, JsonElement>("step" to JsonPrimitive(step))
        if (data != null) params["data"] = data
        return rpc("wizard.next", params)
    }

    suspend fun wizardCancel(): ResponseFrame = rpc("wizard.cancel", null)

    // ── Update ──

    suspend fun updateRun(): ResponseFrame = rpc("update.run", null)

    // ── Logs ──

    suspend fun logsTail(limit: Int? = null, level: String? = null): ResponseFrame {
        val params = mutableMapOf<String, JsonElement>()
        if (limit != null) params["limit"] = JsonPrimitive(limit)
        if (level != null) params["level"] = JsonPrimitive(level)
        return rpc("logs.tail", if (params.isNotEmpty()) params else null)
    }

    // ── Models ──

    suspend fun modelsList(): ResponseFrame = rpc("models.list", null)

    // ── Tools ──

    suspend fun toolsCatalog(): ResponseFrame = rpc("tools.catalog", null)

    suspend fun toolsEffective(sessionKey: String? = null): ResponseFrame {
        val params = if (sessionKey != null) mapOf("sessionKey" to JsonPrimitive(sessionKey)) else null
        return rpc("tools.effective", params)
    }

    // ── Channels ──

    suspend fun channelsStatus(): ResponseFrame = rpc("channels.status", null)

    suspend fun channelsLogout(channelId: String? = null): ResponseFrame {
        val params = if (channelId != null) mapOf("channelId" to JsonPrimitive(channelId)) else null
        return rpc("channels.logout", params)
    }

    // ── Device Token ──

    suspend fun deviceTokenRotate(): ResponseFrame = rpc("device.token.rotate", null)

    suspend fun deviceTokenRevoke(token: String? = null): ResponseFrame {
        val params = if (token != null) mapOf("token" to JsonPrimitive(token)) else null
        return rpc("device.token.revoke", params)
    }

    // ── Device Pair ──

    suspend fun devicePairList(): ResponseFrame = rpc("device.pair.list", null)

    suspend fun devicePairApprove(deviceId: String): ResponseFrame {
        return rpc("device.pair.approve", mapOf("deviceId" to JsonPrimitive(deviceId)))
    }

    suspend fun devicePairReject(deviceId: String): ResponseFrame {
        return rpc("device.pair.reject", mapOf("deviceId" to JsonPrimitive(deviceId)))
    }

    suspend fun devicePairRemove(deviceId: String): ResponseFrame {
        return rpc("device.pair.remove", mapOf("deviceId" to JsonPrimitive(deviceId)))
    }

    // ── Usage ──

    suspend fun sessionsUsageTimeseries(hours: Int? = null): ResponseFrame {
        val params = if (hours != null) mapOf("hours" to JsonPrimitive(hours)) else null
        return rpc("sessions.usage.timeseries", params)
    }

    suspend fun usageStatus(): ResponseFrame = rpc("usage.status", null)
    suspend fun usageCost(): ResponseFrame = rpc("usage.cost", null)

    // ── Talk ──

    suspend fun talkConfig(): ResponseFrame = rpc("talk.config", null)

    suspend fun talkSpeak(
        text: String,
        provider: String? = null,
        voice: String? = null
    ): ResponseFrame {
        val params = mutableMapOf<String, JsonElement>("text" to JsonPrimitive(text))
        if (provider != null) params["provider"] = JsonPrimitive(provider)
        if (voice != null) params["voice"] = JsonPrimitive(voice)
        return rpc("talk.speak", params)
    }
}
