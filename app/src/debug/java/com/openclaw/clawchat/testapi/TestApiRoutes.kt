package com.openclaw.clawchat.testapi

import com.openclaw.clawchat.ui.state.MainViewModel
import com.openclaw.clawchat.ui.state.SessionViewModel
import com.openclaw.clawchat.network.protocol.GatewayConnection
import com.openclaw.clawchat.network.WebSocketConnectionState
import com.openclaw.clawchat.ui.state.SessionUi
import io.ktor.server.routing.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * All Test API route handlers (debug builds only).
 */
fun Routing.testApiRoutes(server: TestApiServer) {
    val mainVm = server.mainViewModel
    val sessionVm = server.sessionViewModel
    val gateway = server.gatewayConnection

    get("/api/health") {
        withContext(Dispatchers.IO) {
            server.recordRequest()
            call.respondJsonText(HttpStatusCode.OK, JsonResponses.encode(HealthResponse("ok", "ClawChat Test API")))
        }
    }

    get("/api/agents") {
        withContext(Dispatchers.IO) {
            server.recordRequest()
            val state = mainVm.uiState.value
            val agents = state.agents.map { ag -> AgentResponse(ag.id, ag.name, ag.emoji, ag.model) }
            call.respondJsonText(HttpStatusCode.OK, JsonResponses.encode(AgentsResponse(agents)))
        }
    }

    get("/api/models") {
        withContext(Dispatchers.IO) {
            server.recordRequest()
            val state = mainVm.uiState.value
            val models = state.models.map { m -> ModelResponse(m.id, m.name, m.provider, m.supportsVision, m.contextWindow) }
            call.respondJsonText(HttpStatusCode.OK, JsonResponses.encode(ModelsResponse(models)))
        }
    }

    get("/api/sessions") {
        withContext(Dispatchers.IO) {
            server.recordRequest()
            val state = mainVm.uiState.value
            val sessions = state.sessions.map { it.toSessionResponse() }
            call.respondJsonText(HttpStatusCode.OK, JsonResponses.encode(SessionsResponse(sessions, state.currentSession?.key)))
        }
    }

    get("/api/sessions/{key}") {
        withContext(Dispatchers.IO) {
            server.recordRequest()
            val key = call.parameters["key"]
            if (key == null) { call.respondJsonTextError(HttpStatusCode.BadRequest, "Missing key"); return@withContext }
            val session = mainVm.uiState.value.sessions.find { it.key == key }
            if (session == null) { call.respondText("null", ContentType.Application.Json, HttpStatusCode.NotFound) }
            else { call.respondJsonText(HttpStatusCode.OK, JsonResponses.encode(session.toSessionResponse())) }
        }
    }

    post("/api/sessions") {
        withContext(Dispatchers.IO) {
            server.recordRequest()
            val body = call.receiveOrDefault { CreateSessionRequest() }
            withContext(Dispatchers.Main) {
                mainVm.createSessionWithAgentModel(body.agentId, body.model, body.initialMessage, body.label)
            }
            val newSession = mainVm.uiState.value.currentSession
            call.respondJsonText(HttpStatusCode.Created, JsonResponses.encode(CreateSessionResponse(newSession?.key ?: "", newSession?.label)))
        }
    }

    delete("/api/sessions/{key}") {
        withContext(Dispatchers.IO) {
            server.recordRequest()
            val key = call.parameters["key"]
            if (key == null) { call.respondJsonTextError(HttpStatusCode.BadRequest, "Missing key"); return@withContext }
            withContext(Dispatchers.Main) { mainVm.deleteSession(key) }
            call.respondJsonText(HttpStatusCode.OK, JsonResponses.encode(DeleteResponse(true)))
        }
    }

    post("/api/sessions/{key}/reset") {
        withContext(Dispatchers.IO) {
            server.recordRequest()
            val key = call.parameters["key"]
            if (key == null) { call.respondJsonTextError(HttpStatusCode.BadRequest, "Missing key"); return@withContext }
            withContext(Dispatchers.Main) { sessionVm.setSessionKey(key); mainVm.clearCurrentSession() }
            call.respondJsonText(HttpStatusCode.OK, JsonResponses.encode(ResetResponse(true)))
        }
    }

    post("/api/sessions/{key}/messages") {
        withContext(Dispatchers.IO) {
            server.recordRequest()
            val key = call.parameters["key"]
            if (key == null) { call.respondJsonTextError(HttpStatusCode.BadRequest, "Missing key"); return@withContext }
            val body = call.receiveOrDefault { MessageRequest("") }
            withContext(Dispatchers.Main) {
                sessionVm.setSessionKey(key)
                body.attachments?.forEach { att ->
                    if (att.type == "base64") {
                        val attachment = com.openclaw.clawchat.ui.state.AttachmentUi(
                            id = java.util.UUID.randomUUID().toString(),
                            uri = null, mimeType = att.mimeType,
                            fileName = att.fileName ?: "attachment",
                            dataUrl = "data:${att.mimeType};base64,${att.content}"
                        )
                        sessionVm.addAttachment(attachment)
                    }
                }
                sessionVm.sendMessage(body.text)
            }
            val runId = sessionVm.state.value.chatRunId
            call.respondJsonText(HttpStatusCode.Accepted, JsonResponses.encode(MessageResponse(runId, "accepted")))
        }
    }

    post("/api/sessions/{key}/abort") {
        withContext(Dispatchers.IO) {
            server.recordRequest()
            val key = call.parameters["key"]
            if (key == null) { call.respondJsonTextError(HttpStatusCode.BadRequest, "Missing key"); return@withContext }
            withContext(Dispatchers.Main) { sessionVm.setSessionKey(key); sessionVm.abortChat() }
            call.respondJsonText(HttpStatusCode.OK, JsonResponses.encode(MessageResponse(status = "aborted")))
        }
    }

    get("/api/sessions/{key}/input") {
        withContext(Dispatchers.IO) {
            server.recordRequest()
            val key = call.parameters["key"]
            if (key == null) { call.respondJsonTextError(HttpStatusCode.BadRequest, "Missing key"); return@withContext }
            withContext(Dispatchers.Main) { sessionVm.setSessionKey(key) }
            val state = sessionVm.state.value
            call.respondJsonText(HttpStatusCode.OK, JsonResponses.encode(InputTextResponse(
                state.inputText,
                state.attachments.map { AttachmentUiResponse(it.id, it.mimeType, it.fileName) }
            )))
        }
    }

    put("/api/sessions/{key}/input") {
        withContext(Dispatchers.IO) {
            server.recordRequest()
            val key = call.parameters["key"]
            if (key == null) { call.respondJsonTextError(HttpStatusCode.BadRequest, "Missing key"); return@withContext }
            val body = call.receiveOrDefault { InputTextRequest("") }
            withContext(Dispatchers.Main) { sessionVm.setSessionKey(key); sessionVm.updateInputText(body.text) }
            call.respondJsonText(HttpStatusCode.OK, JsonResponses.encode(InputTextResponse(
                body.text,
                sessionVm.state.value.attachments.map { AttachmentUiResponse(it.id, it.mimeType, it.fileName) }
            )))
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
            call.respondJsonText(HttpStatusCode.OK, JsonResponses.encode(GatewayStatusResponse(
                state = stateName,
                url = (connState as? WebSocketConnectionState.Connected)?.url,
                latencyMs = mainVm.uiState.value.latency
            )))
        }
    }

    post("/api/gateway/connect") {
        withContext(Dispatchers.IO) {
            server.recordRequest()
            val body = call.receiveOrDefault { GatewayConnectRequest("") }
            withContext(Dispatchers.Main) { mainVm.connectToGateway(body.url) }
            call.respondJsonText(HttpStatusCode.OK, JsonResponses.encode(GatewayConnectResponse(true)))
        }
    }

    get("/api/state") {
        withContext(Dispatchers.IO) {
            server.recordRequest()
            val mainState = mainVm.uiState.value
            val connState = gateway.connectionState.value
            call.respondJsonText(HttpStatusCode.OK, JsonResponses.encode(AppStateResponse(
                GatewayStatusResponse(connState::class.simpleName ?: "Unknown",
                    (connState as? WebSocketConnectionState.Connected)?.url, mainState.latency),
                SessionsSummary(mainState.sessions.size, mainState.currentSession?.key),
                mainState.currentSession?.key
            )))
        }
    }

    get("/api/state/session") {
        withContext(Dispatchers.IO) {
            server.recordRequest()
            val state = sessionVm.state.value
            call.respondJsonText(HttpStatusCode.OK, JsonResponses.encode(SessionStateResponse(
                state.session?.key, state.isSending, state.isLoading, state.inputText,
                state.attachments.map { AttachmentUiResponse(it.id, it.mimeType, it.fileName) },
                state.chatMessages.map { MessageUiResponse(it.id, it.role, it.content, it.createdAt, it.status) },
                state.totalTokens, state.contextTokensLimit, state.chatStream, state.chatRunId
            )))
        }
    }
}

private fun SessionUi.toSessionResponse() = SessionResponse(
    key, kind, label, model, agentId, agentName, status, updatedAt,
    totalTokens, contextTokens, inputTokens, outputTokens, pinned, createdAt
)

private suspend inline fun <reified T : Any> ApplicationCall.receiveOrDefault(default: () -> T): T {
    return try {
        val text = receiveText()
        if (text.isBlank()) default()
        else JsonResponses.decode(text)
    } catch (e: Exception) { default() }
}

private suspend fun ApplicationCall.respondJsonText(status: HttpStatusCode, body: String) {
    respondText(body, ContentType.Application.Json, status)
}

private suspend fun ApplicationCall.respondJsonTextError(status: HttpStatusCode, message: String) {
    respondJsonText(status, JsonResponses.encode(ErrorResponse(message)))
}
