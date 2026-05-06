package com.openclaw.clawchat.testapi

import com.openclaw.clawchat.network.protocol.GatewayConnection
import com.openclaw.clawchat.network.WebSocketConnectionState
import com.openclaw.clawchat.ui.state.MainViewModel
import com.openclaw.clawchat.ui.state.SessionViewModel
import com.openclaw.clawchat.ui.state.SessionUi
import io.ktor.server.routing.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Route handler installer for Test API.
 * Accepts Routing as receiver so { } DSL block works.
 */
fun Routing.installTestApiRoutes(
    mainVm: MainViewModel,
    sessionVm: SessionViewModel,
    gateway: GatewayConnection,
    server: TestApiServer
) {
    // ─── Health ────────────────────────────────────────────────────────────────
    get("/api/health") {
        val self = this
        withContext(Dispatchers.IO) {
            server.recordRequest()
            self.call.respondText(JsonResponses.encode(HealthResponse("ok", "ClawChat Test API")), ContentType.Application.Json)
        }
    }

    // ─── Agents / Models ────────────────────────────────────────────────────────
    get("/api/agents") {
        val self = this
        withContext(Dispatchers.IO) {
            server.recordRequest()
            val state = mainVm.uiState.value
            val agents = state.agents.map { ag ->
                AgentResponse(
                    id = ag.id,
                    name = ag.name,
                    emoji = ag.emoji,
                    model = ag.model
                )
            }
            self.call.respondText(JsonResponses.encode(AgentsResponse(agents)), ContentType.Application.Json)
        }
    }

    get("/api/models") {
        val self = this
        withContext(Dispatchers.IO) {
            server.recordRequest()
            val state = mainVm.uiState.value
            val models = state.models.map { m ->
                ModelResponse(
                    id = m.id,
                    name = m.name,
                    provider = m.provider,
                    supportsVision = m.supportsVision,
                    contextWindow = m.contextWindow
                )
            }
            self.call.respondText(JsonResponses.encode(ModelsResponse(models)), ContentType.Application.Json)
        }
    }

    // ─── Sessions ──────────────────────────────────────────────────────────────
    get("/api/sessions") {
        val self = this
        withContext(Dispatchers.IO) {
            server.recordRequest()
            val state = mainVm.uiState.value
            val sessions = state.sessions.map { it.toSessionResponse() }
            self.call.respondText(JsonResponses.encode(SessionsResponse(
                sessions = sessions,
                currentSessionKey = state.currentSession?.key
            )), ContentType.Application.Json)
        }
    }

    get("/api/sessions/{key}") {
        val self = this
        withContext(Dispatchers.IO) {
            server.recordRequest()
            val key = self.call.parameters["key"]
            if (key == null) {
                self.call.respondText(JsonResponses.encode(ErrorResponse("Missing key")), ContentType.Application.Json, HttpStatusCode.BadRequest)
                return@withContext
            }
            val session = mainVm.uiState.value.sessions.find { it.key == key }
            if (session == null) {
                self.call.respondText("null", ContentType.Application.Json, HttpStatusCode.NotFound)
            } else {
                self.call.respondText(JsonResponses.encode(session.toSessionResponse()), ContentType.Application.Json)
            }
        }
    }

    post("/api/sessions") {
        val self = this
        withContext(Dispatchers.IO) {
            server.recordRequest()
            val body = try {
                val text = self.call.receiveText()
                if (text.isBlank()) CreateSessionRequest() else JsonResponses.decode(text)
            } catch (e: Exception) { CreateSessionRequest() }

            withContext(Dispatchers.Main) {
                mainVm.createSessionWithAgentModel(body.agentId, body.model, body.initialMessage, body.label)
            }
            val newSession = mainVm.uiState.value.currentSession
            self.call.respondText(
                JsonResponses.encode(CreateSessionResponse(newSession?.key ?: "", newSession?.label)),
                ContentType.Application.Json,
                HttpStatusCode.Created
            )
        }
    }

    delete("/api/sessions/{key}") {
        val self = this
        withContext(Dispatchers.IO) {
            server.recordRequest()
            val key = self.call.parameters["key"]
            if (key == null) {
                self.call.respondText(JsonResponses.encode(ErrorResponse("Missing key")), ContentType.Application.Json, HttpStatusCode.BadRequest)
                return@withContext
            }
            withContext(Dispatchers.Main) { mainVm.deleteSession(key) }
            self.call.respondText(JsonResponses.encode(DeleteResponse(true)), ContentType.Application.Json)
        }
    }

    post("/api/sessions/{key}/reset") {
        val self = this
        withContext(Dispatchers.IO) {
            server.recordRequest()
            val key = self.call.parameters["key"]
            if (key == null) {
                self.call.respondText(JsonResponses.encode(ErrorResponse("Missing key")), ContentType.Application.Json, HttpStatusCode.BadRequest)
                return@withContext
            }
            withContext(Dispatchers.Main) { sessionVm.setSessionKey(key); mainVm.clearCurrentSession() }
            self.call.respondText(JsonResponses.encode(ResetResponse(true)), ContentType.Application.Json)
        }
    }

    post("/api/sessions/{key}/messages") {
        val self = this
        withContext(Dispatchers.IO) {
            server.recordRequest()
            val key = self.call.parameters["key"]
            if (key == null) {
                self.call.respondText(JsonResponses.encode(ErrorResponse("Missing key")), ContentType.Application.Json, HttpStatusCode.BadRequest)
                return@withContext
            }
            val body = try {
                val text = self.call.receiveText()
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
            self.call.respondText(
                JsonResponses.encode(MessageResponse(runId, "accepted")),
                ContentType.Application.Json,
                HttpStatusCode.Accepted
            )
        }
    }

    post("/api/sessions/{key}/abort") {
        val self = this
        withContext(Dispatchers.IO) {
            server.recordRequest()
            val key = self.call.parameters["key"]
            if (key == null) {
                self.call.respondText(JsonResponses.encode(ErrorResponse("Missing key")), ContentType.Application.Json, HttpStatusCode.BadRequest)
                return@withContext
            }
            withContext(Dispatchers.Main) { sessionVm.setSessionKey(key); sessionVm.abortChat() }
            self.call.respondText(JsonResponses.encode(MessageResponse(status = "aborted")), ContentType.Application.Json)
        }
    }

    get("/api/sessions/{key}/input") {
        val self = this
        withContext(Dispatchers.IO) {
            server.recordRequest()
            val key = self.call.parameters["key"]
            if (key == null) {
                self.call.respondText(JsonResponses.encode(ErrorResponse("Missing key")), ContentType.Application.Json, HttpStatusCode.BadRequest)
                return@withContext
            }
            withContext(Dispatchers.Main) { sessionVm.setSessionKey(key) }
            val state = sessionVm.state.value
            self.call.respondText(
                JsonResponses.encode(InputTextResponse(
                    state.inputText,
                    state.attachments.map { AttachmentUiResponse(it.id, it.mimeType, it.fileName) }
                )),
                ContentType.Application.Json
            )
        }
    }

    put("/api/sessions/{key}/input") {
        val self = this
        withContext(Dispatchers.IO) {
            server.recordRequest()
            val key = self.call.parameters["key"]
            if (key == null) {
                self.call.respondText(JsonResponses.encode(ErrorResponse("Missing key")), ContentType.Application.Json, HttpStatusCode.BadRequest)
                return@withContext
            }
            val body = try {
                val text = self.call.receiveText()
                if (text.isBlank()) InputTextRequest("") else JsonResponses.decodeInputText(text)
            } catch (e: Exception) { InputTextRequest("") }

            withContext(Dispatchers.Main) { sessionVm.setSessionKey(key); sessionVm.updateInputText(body.text) }
            self.call.respondText(
                JsonResponses.encode(InputTextResponse(
                    body.text,
                    sessionVm.state.value.attachments.map { AttachmentUiResponse(it.id, it.mimeType, it.fileName) }
                )),
                ContentType.Application.Json
            )
        }
    }

    // ─── Gateway ───────────────────────────────────────────────────────────────
    get("/api/gateway/status") {
        val self = this
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
            self.call.respondText(
                JsonResponses.encode(GatewayStatusResponse(
                    state = stateName,
                    url = gateway.connectedUrl,
                    latencyMs = mainVm.uiState.value.latency
                )),
                ContentType.Application.Json
            )
        }
    }

    post("/api/gateway/connect") {
        val self = this
        withContext(Dispatchers.IO) {
            server.recordRequest()
            val body = try {
                val text = self.call.receiveText()
                if (text.isBlank()) GatewayConnectRequest("") else JsonResponses.decodeGatewayConnect(text)
            } catch (e: Exception) { GatewayConnectRequest("") }

            withContext(Dispatchers.Main) { mainVm.connectToGateway(body.url) }
            self.call.respondText(JsonResponses.encode(GatewayConnectResponse(true)), ContentType.Application.Json)
        }
    }

    // ─── State ─────────────────────────────────────────────────────────────────
    get("/api/state") {
        val self = this
        withContext(Dispatchers.IO) {
            server.recordRequest()
            val mainState = mainVm.uiState.value
            val connState = gateway.connectionState.value
            self.call.respondText(
                JsonResponses.encode(AppStateResponse(
                    gateway = GatewayStatusResponse(
                        state = connState::class.simpleName ?: "Unknown",
                        url = gateway.connectedUrl,
                        latencyMs = mainState.latency
                    ),
                    sessions = SessionsSummary(
                        count = mainState.sessions.size,
                        currentKey = mainState.currentSession?.key
                    ),
                    currentSessionKey = mainState.currentSession?.key
                )),
                ContentType.Application.Json
            )
        }
    }

    get("/api/state/session") {
        val self = this
        withContext(Dispatchers.IO) {
            server.recordRequest()
            val state = sessionVm.state.value
            self.call.respondText(
                JsonResponses.encode(SessionStateResponse(
                    sessionKey = state.session?.key,
                    isSending = state.isSending,
                    isLoading = state.isLoading,
                    inputText = state.inputText,
                    attachments = state.attachments.map { att ->
                        AttachmentUiResponse(att.id, att.mimeType, att.fileName)
                    },
                    chatMessages = state.chatMessages.map { msg ->
                        MessageUiResponse(
                            id = msg.id,
                            role = msg.role.name,
                            content = msg.getTextContent(),
                            createdAt = null,
                            status = msg.status.name
                        )
                    },
                    totalTokens = state.totalTokens,
                    contextTokens = state.contextTokens,
                    chatStream = state.chatStream,
                    chatRunId = state.chatRunId
                )),
                ContentType.Application.Json
            )
        }
    }
}

private fun SessionUi.toSessionResponse() = SessionResponse(
    key = key,
    kind = kind,
    label = label,
    model = model,
    agentId = agentId,
    agentName = agentName,
    status = status?.name,
    updatedAt = updatedAt,
    totalTokens = totalTokens,
    contextTokens = contextTokens,
    inputTokens = inputTokens,
    outputTokens = outputTokens,
    pinned = isPinned,
    createdAt = null
)
