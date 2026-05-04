package com.openclaw.clawchat.testapi

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

val testApiJson = Json {
    prettyPrint = true
    encodeDefaults = true
    ignoreUnknownKeys = true
}

/**
 * JSON serialization helpers for Test API responses.
 * Plain Kotlin utilities - no Ktor dependencies.
 */
object JsonResponses {
    fun encode(body: Any): String = testApiJson.encodeToString(body)

    fun decode(text: String): CreateSessionRequest = testApiJson.decodeFromString(text)
    fun decodeMessage(text: String): MessageRequest = testApiJson.decodeFromString(text)
    fun decodeInputText(text: String): InputTextRequest = testApiJson.decodeFromString(text)
    fun decodeGatewayConnect(text: String): GatewayConnectRequest = testApiJson.decodeFromString(text)

    fun error(message: String, code: String? = null) = ErrorResponse(message, code)
}
