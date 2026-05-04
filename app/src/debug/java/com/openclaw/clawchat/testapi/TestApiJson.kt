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
    fun <T> encode(body: T): String = testApiJson.encodeToString(body)

    inline fun <reified T> decode(text: String): T = testApiJson.decodeFromString(text)

    fun error(message: String, code: String? = null) = ErrorResponse(message, code)
}
