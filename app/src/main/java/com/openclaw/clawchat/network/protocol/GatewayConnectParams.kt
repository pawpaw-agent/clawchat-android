package com.openclaw.clawchat.network.protocol

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Gateway 连接参数构建器
 */

/**
 * 构建连接请求参数 (aligned with ConnectParamsSchema)
 */
fun buildConnectParams(req: ConnectRequest): Map<String, JsonElement> {
    val hasToken = req.token != null && req.token.isNotBlank()
    
    val baseParams = mutableMapOf(
        "minProtocol" to JsonPrimitive(3),
        "maxProtocol" to JsonPrimitive(3),
        "client" to buildJsonObject {
            put("id", "openclaw-control-ui")  // 使用 control-ui ID 以获得 Gateway 认可
            put("version", req.client.clientVersion)
            put("platform", req.client.platform)
            put("mode", "ui")
            put("deviceFamily", "phone")
            put("modelIdentifier", req.client.deviceModel)
        },
        "role" to JsonPrimitive(req.role),
        "scopes" to JsonArray(req.scopes.map { JsonPrimitive(it) }),
        "caps" to JsonArray(listOf(JsonPrimitive("tool_events"))),
        "commands" to JsonArray(emptyList()),
        "permissions" to JsonObject(emptyMap()),
        "locale" to JsonPrimitive("zh-CN"),
        "userAgent" to JsonPrimitive("openclaw-android/${req.client.clientVersion}")
    )
    
    // Token 模式：只发送 token，不发送 device 签名
    if (hasToken) {
        baseParams["auth"] = buildJsonObject {
            put("token", req.token)
        }
        // 不发送 device 信息，Gateway 会用 token 直接认证
    } else {
        // 配对模式：发送 device 签名
        baseParams["auth"] = JsonObject(emptyMap())
        baseParams["device"] = buildJsonObject {
            put("id", req.device.id)
            put("publicKey", req.device.publicKey)
            put("signature", req.device.signature)
            put("signedAt", req.device.signedAt)
            put("nonce", req.device.nonce)
        }
    }
    
    return baseParams
}