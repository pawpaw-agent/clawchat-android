package com.openclaw.clawchat.testapi

import com.openclaw.clawchat.network.protocol.GatewayConnection
import com.openclaw.clawchat.network.WebSocketConnectionState
import com.openclaw.clawchat.ui.state.MainViewModel
import com.openclaw.clawchat.ui.state.SessionViewModel
import com.openclaw.clawchat.ui.state.SessionUi
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.delete
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode

/**
 * Route handler installer for Test API.
 */
fun Route.installTestApiRoutes(
    mainVm: MainViewModel,
    sessionVm: SessionViewModel,
    gateway: GatewayConnection,
    server: TestApiServer
) {
    route("/api/health") {
        val outerCall = call
        get {
            outerCall.respondText(JsonResponses.encode(HealthResponse("ok", "ClawChat Test API")), ContentType.Application.Json)
        }
    }

    route("/api/agents") {
        val outerCall = call
        get {
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
            outerCall.respondText(JsonResponses.encode(AgentsResponse(agents)), ContentType.Application.Json)
        }
    }

    route("/api/models") {
        val outerCall = call
        get {
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
            outerCall.respondText(JsonResponses.encode(ModelsResponse(models)), ContentType.Application.Json)
        }
    }

    route("/api/sessions") {
        val outerCall = call
        get {
            server.recordRequest()
            val state = mainVm.uiState.value
            val sessions = state.sessions.map { it.toSessionResponse() }
            outerCall.respondText(JsonResponses.encode(SessionsResponse(
                sessions = sessions,
                currentSessionKey = state.currentSession?.key
            )), ContentType.Application.Json)
        }
        post {
            server.recordRequest()
            val body = try {
                val text = outerCall.receiveText()
                if (text.isBlank()) CreateSessionRequest() else JsonResponses.decode(text)
            } catch (e: Exception) { CreateSessionRequest() }

            mainVm.createSessionWithAgentModel(body.agentId, body.model, body.initialMessage, body.label)
            val newSession = mainVm.uiState.value.currentSession
            outerCall.respondText(
                JsonResponses.encode(CreateSessionResponse(newSession?.key ?: "", newSession?.label)),
                ContentType.Application.Json,
                HttpStatusCode.Created
            )
        }
    }

    route("/api/sessions/{key}") {
        val outerCall = call
        get {
            server.recordRequest()
            val key = outerCall.parameters["key"]?.toString()
            if (key == null) {
                outerCall.respondText(JsonResponses.encode(ErrorResponse("Missing key")), ContentType.Application.Json, HttpStatusCode.BadRequest)
                return@get
            }
            val session = mainVm.uiState.value.sessions.find { it.key == key }
            if (session == null) {
                outerCall.respondText("null", ContentType.Application.Json, HttpStatusCode.NotFound)
            } else {
                outerCall.respondText(JsonResponses.encode(session.toSessionResponse()), ContentType.Application.Json)
            }
        }
        delete {
            server.recordRequest()
            val key = outerCall.parameters["key"]?.toString()
            if (key == null) {
                outerCall.respondText(JsonResponses.encode(ErrorResponse("Missing key")), ContentType.Application.Json, HttpStatusCode.BadRequest)
                return@delete
            }
            mainVm.deleteSession(key)
            outerCall.respondText(JsonResponses.encode(DeleteResponse(true)), ContentType.Application.Json)
        }
    }

    route("/api/sessions/{key}/reset") {
        val outerCall = call
        post {
            server.recordRequest()
            val key = outerCall.parameters["key"]?.toString()
            if (key == null) {
                outerCall.respondText(JsonResponses.encode(ErrorResponse("Missing key")), ContentType.Application.Json, HttpStatusCode.BadRequest)
                return@post
            }
            sessionVm.setSessionKey(key)
            mainVm.clearCurrentSession()
            outerCall.respondText(JsonResponses.encode(ResetResponse(true)), ContentType.Application.Json)
        }
    }

    route("/api/sessions/{key}/messages") {
        val outerCall = call
        post {
            server.recordRequest()
            val key = outerCall.parameters["key"]?.toString()
            if (key == null) {
                outerCall.respondText(JsonResponses.encode(ErrorResponse("Missing key")), ContentType.Application.Json, HttpStatusCode.BadRequest)
                return@post
            }
            val body = try {
                val text = outerCall.receiveText()
                if (text.isBlank()) MessageRequest("") else JsonResponses.decodeMessage(text)
            } catch (e: Exception) { MessageRequest("") }

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
            val runId = sessionVm.state.value.chatRunId
            outerCall.respondText(
                JsonResponses.encode(MessageResponse(runId, "accepted")),
                ContentType.Application.Json,
                HttpStatusCode.Accepted
            )
        }
    }

    route("/api/sessions/{key}/abort") {
        val outerCall = call
        post {
            server.recordRequest()
            val key = outerCall.parameters["key"]?.toString()
            if (key == null) {
                outerCall.respondText(JsonResponses.encode(ErrorResponse("Missing key")), ContentType.Application.Json, HttpStatusCode.BadRequest)
                return@post
            }
            sessionVm.setSessionKey(key)
            sessionVm.abortChat()
            outerCall.respondText(JsonResponses.encode(MessageResponse(status = "aborted")), ContentType.Application.Json)
        }
    }

    route("/api/sessions/{key}/input") {
        val outerCall = call
        get {
            server.recordRequest()
            val key = outerCall.parameters["key"]?.toString()
            if (key == null) {
                outerCall.respondText(JsonResponses.encode(ErrorResponse("Missing key")), ContentType.Application.Json, HttpStatusCode.BadRequest)
                return@get
            }
            sessionVm.setSessionKey(key)
            val state = sessionVm.state.value
            outerCall.respondText(
                JsonResponses.encode(InputTextResponse(
                    state.inputText,
                    state.attachments.map { AttachmentUiResponse(it.id, it.mimeType, it.fileName) }
                )),
                ContentType.Application.Json
            )
        }
        put {
            server.recordRequest()
            val key = outerCall.parameters["key"]?.toString()
            if (key == null) {
                outerCall.respondText(JsonResponses.encode(ErrorResponse("Missing key")), ContentType.Application.Json, HttpStatusCode.BadRequest)
                return@put
            }
            val body = try {
                val text = outerCall.receiveText()
                if (text.isBlank()) InputTextRequest("") else JsonResponses.decodeInputText(text)
            } catch (e: Exception) { InputTextRequest("") }

            sessionVm.setSessionKey(key)
            sessionVm.updateInputText(body.text)
            outerCall.respondText(
                JsonResponses.encode(InputTextResponse(
                    body.text,
                    sessionVm.state.value.attachments.map { AttachmentUiResponse(it.id, it.mimeType, it.fileName) }
                )),
                ContentType.Application.Json
            )
        }
    }

    route("/api/gateway/status") {
        val outerCall = call
        get {
            server.recordRequest()
            val connState = gateway.connectionState.value
            val stateName = when (connState) {
                is WebSocketConnectionState.Connected -> "Connected"
                is WebSocketConnectionState.Connecting -> "Connecting"
                is WebSocketConnectionState.Disconnected -> "Disconnected"
                is WebSocketConnectionState.Reconnecting -> "Reconnecting"
                else -> "Unknown"
            }
            outerCall.respondText(
                JsonResponses.encode(GatewayStatusResponse(
                    state = stateName,
                    url = gateway.connectedUrl,
                    latencyMs = mainVm.uiState.value.latency
                )),
                ContentType.Application.Json
            )
        }
    }

    route("/api/gateway/connect") {
        val outerCall = call
        post {
            server.recordRequest()
            val body = try {
                val text = outerCall.receiveText()
                if (text.isBlank()) GatewayConnectRequest("") else JsonResponses.decodeGatewayConnect(text)
            } catch (e: Exception) { GatewayConnectRequest("") }

            mainVm.connectToGateway(body.url)
            outerCall.respondText(JsonResponses.encode(GatewayConnectResponse(true)), ContentType.Application.Json)
        }
    }

    route("/api/state") {
        val outerCall = call
        get {
            server.recordRequest()
            val mainState = mainVm.uiState.value
            val connState = gateway.connectionState.value
            outerCall.respondText(
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

    route("/api/state/session") {
        val outerCall = call
        get {
            server.recordRequest()
            val state = sessionVm.state.value
            outerCall.respondText(
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
