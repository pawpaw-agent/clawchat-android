package com.openclaw.clawchat.testapi

import com.openclaw.clawchat.network.protocol.GatewayConnection
import com.openclaw.clawchat.network.WebSocketConnectionState
import com.openclaw.clawchat.ui.state.MainViewModel
import com.openclaw.clawchat.ui.state.SessionViewModel
import com.openclaw.clawchat.ui.state.SessionUi
import io.ktor.server.routing.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.application.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * All Test API route handlers installed via routing { testApiRoutes(server) }.
 * Extension function on Routing so call receiver is correctly resolved.
 */
fun Routing.testApiRoutes(server: TestApiServer) {
    val mainVm = server.mainViewModel
    val sessionVm = server.sessionViewModel
    val gateway = server.gatewayConnection

    get("/api/health") {
        withContext(Dispatchers.IO) {
            server.recordRequest()
            call.respondText(JsonResponses.encode(HealthResponse("ok", "ClawChat Test API")), ContentType.Application.Json)
        }
    }

    get("/api/agents") {
        withContext(Dispatchers.IO) {
            server.recordRequest()
            val agents = mainVm.uiState.value.agents.map { AgentResponse(it.id, it.name, it.emoji, it.model) }
            call.respondText(JsonResponses.encode(AgentsResponse(agents)), ContentType.Application.Json)
        }
    }

    get("/api/models") {
        withContext(Dispatchers.IO) {
            server.recordRequest()
            val models = mainVm.uiState.value.models.map { ModelResponse(it.id, it.name, it.provider, it.supportsVision, it.contextWindow) }
            call.respondText(JsonResponses.encode(ModelsResponse(models)), ContentType.Application.Json)
        }
    }

    get("/api/sessions") {
        withContext(Dispatchers.IO) {
            server.recordRequest()
            val state = mainVm.uiState.value
            val resp = SessionsResponse(state.sessions.map { it.toSessionResponse() }, state.currentSession?.key)
            call.respondText(JsonResponses.encode(resp), ContentType.Application.Json)
        }
    }

    get("/api/sessions/{key}") {
        withContext(Dispatchers.IO) {
            server.recordRequest()
            val key = call.parameters["key"]
            if (key == null) {
                call.respondText(JsonResponses.encode(ErrorResponse("Missing key")), ContentType.Application.Json, HttpStatusCode.BadRequest)
                return@withContext
            }
            val session = mainVm.uiState.value.sessions.find { it.key == key }
            if (session == null) {
                call.respondText("null", ContentType.Application.Json, HttpStatusCode.NotFound)
            } else {
                call.respondText(JsonResponses.encode(session.toSessionResponse()), ContentType.Application.Json)
            }
        }
    }

    post("/api/sessions") {
        withContext(Dispatchers.IO) {
            server.recordRequest()
            val body = try {
                val text = call.receiveText()
                if (text.isBlank()) CreateSessionRequest() else JsonResponses.decode(text)
            } catch (e: Exception) { CreateSessionRequest() }

            withContext(Dispatchers.Main) {
                mainVm.createSessionWithAgentModel(body.agentId, body.model, body.initialMessage, body.label)
            }
            val newSession = mainVm.uiState.value.currentSession
            call.respondText(
                JsonResponses.encode(CreateSessionResponse(newSession?.key ?: "", newSession?.label)),
                ContentType.Application.Json,
                HttpStatusCode.Created
            )
        }
    }

    delete("/api/sessions/{key}") {
        withContext(Dispatchers.IO) {
            server.recordRequest()
            val key = call.parameters["key"]
            if (key == null) {
                call.respondText(JsonResponses.encode(ErrorResponse("Missing key")), ContentType.Application.Json, HttpStatusCode.BadRequest)
                return@withContext
            }
            withContext(Dispatchers.Main) { mainVm.deleteSession(key) }
            call.respondText(JsonResponses.encode(DeleteResponse(true)), ContentType.Application.Json)
        }
    }

    post("/api/sessions/{key}/reset") {
        withContext(Dispatchers.IO) {
            server.recordRequest()
            val key = call.parameters["key"]
            if (key == null) {
                call.respondText(JsonResponses.encode(ErrorResponse("Missing key")), ContentType.Application.Json, HttpStatusCode.BadRequest)
                return@withContext
            }
            withContext(Dispatchers.Main) { sessionVm.setSessionKey(key); mainVm.clearCurrentSession() }
            call.respondText(JsonResponses.encode(ResetResponse(true)), ContentType.Application.Json)
        }
    }

    post("/api/sessions/{key}/messages") {
        withContext(Dispatchers.IO) {
            server.recordRequest()
            val key = call.parameters["key"]
            if (key == null) {
                call.respondText(JsonResponses.encode(ErrorResponse("Missing key")), ContentType.Application.Json, HttpStatusCode.BadRequest)
                return@withContext
            }
            val body = try {
                val text = call.receiveText()
                if (text.isBlank()) MessageRequest("") else JsonResponses.decodeMessage(text)
            } catch (e: Exception) { MessageRequest("") }

            withContext(Dispatchers.Main) {
                sessionVm.setSessionKey(key)
                body.attachments?.forEach { att ->
                    if (att.type == "base64") {
                        val attachment = com.openclaw.clawchat.ui.state.AttachmentUi(
                            id = java.util.UUID.randomUUID().toString(),
                            uri = android.net.Uri.EMPTY,
                            mimeType = att.mimeType,
                            fileName = att.fileName ?: "attachment",
                            dataUrl = "data:${att.mimeType};base64,${att.content}"
                        )
                        sessionVm.addAttachment(attachment)
                    }
                }
                sessionVm.sendMessage(body.text)
            }
            val runId = sessionVm.state.value.chatRunId
            call.respondText(
                JsonResponses.encode(MessageResponse(runId, "accepted")),
                ContentType.Application.Json,
                HttpStatusCode.Accepted
            )
        }
    }

    post("/api/sessions/{key}/abort") {
        withContext(Dispatchers.IO) {
            server.recordRequest()
            val key = call.parameters["key"]
            if (key == null) {
                call.respondText(JsonResponses.encode(ErrorResponse("Missing key")), ContentType.Application.Json, HttpStatusCode.BadRequest)
                return@withContext
            }
            withContext(Dispatchers.Main) { sessionVm.setSessionKey(key); sessionVm.abortChat() }
            call.respondText(JsonResponses.encode(MessageResponse(status = "aborted")), ContentType.Application.Json)
        }
    }

    get("/api/sessions/{key}/input") {
        withContext(Dispatchers.IO) {
            server.recordRequest()
            val key = call.parameters["key"]
            if (key == null) {
                call.respondText(JsonResponses.encode(ErrorResponse("Missing key")), ContentType.Application.Json, HttpStatusCode.BadRequest)
                return@withContext
            }
            withContext(Dispatchers.Main) { sessionVm.setSessionKey(key) }
            val state = sessionVm.state.value
            call.respondText(
                JsonResponses.encode(InputTextResponse(
                    state.inputText,
                    state.attachments.map { AttachmentUiResponse(it.id, it.mimeType, it.fileName) }
                )),
                ContentType.Application.Json
            )
        }
    }

    put("/api/sessions/{key}/input") {
        withContext(Dispatchers.IO) {
            server.recordRequest()
            val key = call.parameters["key"]
            if (key == null) {
                call.respondText(JsonResponses.encode(ErrorResponse("Missing key")), ContentType.Application.Json, HttpStatusCode.BadRequest)
                return@withContext
            }
            val body = try {
                val text = call.receiveText()
                if (text.isBlank()) InputTextRequest("") else JsonResponses.decodeInputText(text)
            } catch (e: Exception) { InputTextRequest("") }

            withContext(Dispatchers.Main) { sessionVm.setSessionKey(key); sessionVm.updateInputText(body.text) }
            call.respondText(
                JsonResponses.encode(InputTextResponse(
                    body.text,
                    sessionVm.state.value.attachments.map { AttachmentUiResponse(it.id, it.mimeType, it.fileName) }
                )),
                ContentType.Application.Json
            )
        }
    }

    get("/api/gateway/status") {
        withContext(Dispatchers.IO) {
            server.recordRequest()
            val connState = gateway.connectionState.value
            val stateName = when (connState) {
                is WebSocketConnectionState.Connected -> "Connected"
                is WebSocketConnectionState.Connecting -> "Connecting"
                is WebSocketConnectionState.Disconnected -> "Disconnected"
                is WebSocketConnectionState.Reconnecting -> "Reconnecting"
                else -> "Unknown"
            }
            call.respondText(
                JsonResponses.encode(GatewayStatusResponse(stateName, (connState as? WebSocketConnectionState.Connected)?.url, mainVm.uiState.value.latency)),
                ContentType.Application.Json
            )
        }
    }

    post("/api/gateway/connect") {
        withContext(Dispatchers.IO) {
            server.recordRequest()
            val body = try {
                val text = call.receiveText()
                if (text.isBlank()) GatewayConnectRequest("") else JsonResponses.decodeGatewayConnect(text)
            } catch (e: Exception) { GatewayConnectRequest("") }

            withContext(Dispatchers.Main) { mainVm.connectToGateway(body.url) }
            call.respondText(JsonResponses.encode(GatewayConnectResponse(true)), ContentType.Application.Json)
        }
    }

    get("/api/state") {
        withContext(Dispatchers.IO) {
            server.recordRequest()
            val mainState = mainVm.uiState.value
            val connState = gateway.connectionState.value
            call.respondText(
                JsonResponses.encode(AppStateResponse(
                    GatewayStatusResponse(connState::class.simpleName ?: "Unknown", (connState as? WebSocketConnectionState.Connected)?.url, mainState.latency),
                    SessionsSummary(mainState.sessions.size, mainState.currentSession?.key),
                    mainState.currentSession?.key
                )),
                ContentType.Application.Json
            )
        }
    }

    get("/api/state/session") {
        withContext(Dispatchers.IO) {
            server.recordRequest()
            val state = sessionVm.state.value
            call.respondText(
                JsonResponses.encode(SessionStateResponse(
                    state.session?.key, state.isSending, state.isLoading, state.inputText,
                    state.attachments.map { AttachmentUiResponse(it.id, it.mimeType, it.fileName) },
                    state.chatMessages.map { MessageUiResponse(it.id, it.role.name, it.getTextContent(), null, it.status.name) },
                    state.totalTokens, state.contextTokensLimit, state.chatStream, state.chatRunId
                )),
                ContentType.Application.Json
            )
        }
    }
}

private fun SessionUi.toSessionResponse() = SessionResponse(
    key, kind, label, model, agentId, agentName, status?.name, updatedAt,
    totalTokens, contextTokens, inputTokens, outputTokens, isPinned, null
)

private suspend fun ApplicationCall.respondJsonText(status: HttpStatusCode, body: String) {
    respondText(body, ContentType.Application.Json, status)
}

private suspend fun ApplicationCall.respondJsonTextError(status: HttpStatusCode, message: String) {
    respondJsonText(status, JsonResponses.encode(ErrorResponse(message)))
}

private suspend fun ApplicationCall.receiveOrCreateSession(): CreateSessionRequest {
    return try {
        val text = receiveText()
        if (text.isBlank()) CreateSessionRequest() else JsonResponses.decode(text)
    } catch (e: Exception) { CreateSessionRequest() }
}

private suspend fun ApplicationCall.receiveOrMessage(): MessageRequest {
    return try {
        val text = receiveText()
        if (text.isBlank()) MessageRequest("") else JsonResponses.decodeMessage(text)
    } catch (e: Exception) { MessageRequest("") }
}

private suspend fun ApplicationCall.receiveOrInputText(): InputTextRequest {
    return try {
        val text = receiveText()
        if (text.isBlank()) InputTextRequest("") else JsonResponses.decodeInputText(text)
    } catch (e: Exception) { InputTextRequest("") }
}

private suspend fun ApplicationCall.receiveOrGatewayConnect(): GatewayConnectRequest {
    return try {
        val text = receiveText()
        if (text.isBlank()) GatewayConnectRequest("") else JsonResponses.decodeGatewayConnect(text)
    } catch (e: Exception) { GatewayConnectRequest("") }
}
