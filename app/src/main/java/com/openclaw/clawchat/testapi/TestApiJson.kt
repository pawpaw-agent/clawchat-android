package com.openclaw.clawchat.testapi

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import kotlinx.serialization.json.Json

val testApiJson = Json {
    prettyPrint = true
    encodeDefaults = true
    ignoreUnknownKeys = true
}

suspend inline fun <reified T> ApplicationCall.respondJson(
    status: HttpStatusCode = HttpStatusCode.OK,
    body: T
) {
    respondText(
        text = testApiJson.encodeToString(body),
        contentType = ContentType.Application.Json,
        status = status
    )
}

suspend inline fun <reified T> ApplicationCall.respondJsonOrNull(
    status: HttpStatusCode = HttpStatusCode.OK,
    body: T?
) {
    if (body == null) {
        respondText(
            text = testApiJson.encodeToString(ErrorResponse("Not found")),
            contentType = ContentType.Application.Json,
            status = HttpStatusCode.NotFound
        )
        return
    }
    respondJson(status, body)
}

suspend fun ApplicationCall.respondError(
    status: HttpStatusCode = HttpStatusCode.BadRequest,
    message: String,
    code: String? = null
) {
    respondJson(status, ErrorResponse(error = message, code = code))
}
