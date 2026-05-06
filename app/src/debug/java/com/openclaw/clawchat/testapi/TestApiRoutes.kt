package com.openclaw.clawchat.testapi

import com.openclaw.clawchat.network.protocol.GatewayConnection
import com.openclaw.clawchat.network.WebSocketConnectionState
import com.openclaw.clawchat.ui.state.MainViewModel
import com.openclaw.clawchat.ui.state.SessionViewModel
import com.openclaw.clawchat.ui.state.SessionUi
import io.ktor.util.pipeline.PipelineContext
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode

/**
 * Route handler installer for Test API.
 * Routes defined using Ktor's routing DSL. Inside get/post/put/delete handlers,
 * 'call' refers to ApplicationCall via PipelineContext.
 */
fun io.ktor.server.routing.Route.installTestApiRoutes(
    mainVm: MainViewModel,
    sessionVm: SessionViewModel,
    gateway: GatewayConnection,
    server: TestApiServer
) {
    get("/api/health") {
        server.recordRequest()
        call.respondText(JsonResponses.encode(HealthResponse("ok", "ClawChat Test API")), ContentType.Application.Json)
    }

    get("/api/agents") {
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
        call.respondText(JsonResponses.encode(AgentsResponse(agents)), ContentType.Application.Json)
    }

    get("/api/models") {
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
        call.respondText(JsonResponses.encode(ModelsResponse(models)), ContentType.Application.Json)
    }

    get("/api/sessions") {
        server.recordRequest()
        val state = mainVm.uiState.value
        val sessions = state.sessions.map { it.toSessionResponse() }
        call.respondText(JsonResponses.encode(SessionsResponse(
            sessions = sessions,
            currentSessionKey = state.currentSession?.key
        )), ContentType.Application.Json)
    }

    get("/api/sessions/{key}") {
        server.recordRequest()
        val key = call.parameters["key"]
        if (key == null) {
            call.respondText(JsonResponses.encode(ErrorResponse("Missing key")), ContentType.Application.Json, HttpStatusCode.BadRequest)
            return@get
        }
        val session = mainVm.uiState.value.sessions.find { it.key == key }
        if (session == null) {
            call.respondText("null", ContentType.Application.Json, HttpStatusCode.NotFound)
        } else {
            call.respondText(JsonResponses.encode(session.toSessionResponse()), ContentType.Application.Json)
        }
    }

    post("/api/sessions") {
        server.recordRequest()
        val body = try {
            val text = call.receiveText()
            if (text.isBlank()) CreateSessionRequest() else JsonResponses.decode(text)
        } catch (e: Exception) { CreateSessionRequest() }

        mainVm.createSessionWithAgentModel(body.agentId, body.model, body.initialMessage, body.label)
        val newSession = mainVm.uiState.value.currentSession
        call.respondText(
            JsonResponses.encode(CreateSessionResponse(newSession?.key ?: "", newSession?.label)),
            ContentType.Application.Json,
            HttpStatusCode.Created
        )
    }

    delete("/api/sessions/{key}") {
        server.recordRequest()
        val key = call.parameters["key"]
        if (key == null) {
            call.respondText(JsonResponses.encode(ErrorResponse("Missing key")), ContentType.Application.Json, HttpStatusCode.BadRequest)
            return@delete
        }
        mainVm.deleteSession(key)
        call.respondText(JsonResponses.encode(DeleteResponse(true)), ContentType.Application.Json)
    }

    post("/api/sessions/{key}/reset") {
        server.recordRequest()
        val key = call.parameters["key"]
        if (key == null) {
            call.respondText(JsonResponses.encode(ErrorResponse("Missing key")), ContentType.Application.Json, HttpStatusCode.BadRequest)
            return@post
        }
        sessionVm.setSessionKey(key)
        mainVm.clearCurrentSession()
        call.respondText(JsonResponses.encode(ResetResponse(true)), ContentType.Application.Json)
    }

    post("/api/sessions/{key}/messages") {
        server.recordRequest()
        val key = call.parameters["key"]
        if (key == null) {
            call.respondText(JsonResponses.encode(ErrorResponse("Missing key")), ContentType.Application.Json, HttpStatusCode.BadRequest)
            return@post
        }
        val body = try {
            val text = call.receiveText()
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
        call.respondText(
            JsonResponses.encode(MessageResponse(runId, "accepted")),
            ContentType.Application.Json,
            HttpStatusCode.Accepted
        )
    }

    post("/api/sessions/{key}/abort") {
        server.recordRequest()
        val key = call.parameters["key"]
        if (key == null) {
            call.respondText(JsonResponses.encode(ErrorResponse("Missing key")), ContentType.Application.Json, HttpStatusCode.BadRequest)
            return@post
        }
        sessionVm.setSessionKey(key)
        sessionVm.abortChat()
        call.respondText(JsonResponses.encode(MessageResponse(status = "aborted")), ContentType.Application.Json)
    }

    get("/api/sessions/{key}/input") {
        server.recordRequest()
        val key = call.parameters["key"]
        if (key == null) {
            call.respondText(JsonResponses.encode(ErrorResponse("Missing key")), ContentType.Application.Json, HttpStatusCode.BadRequest)
            return@get
        }
        sessionVm.setSessionKey(key)
        val state = sessionVm.state.value
        call.respondText(
            JsonResponses.encode(InputTextResponse(
                state.inputText,
                state.attachments.map { AttachmentUiResponse(it.id, it.mimeType, it.fileName) }
            )),
            ContentType.Application.Json
        )
    }

    put("/api/sessions/{key}/input") {
        server.recordRequest()
        val key = call.parameters["key"]
        if (key == null) {
            call.respondText(JsonResponses.encode(ErrorResponse("Missing key")), ContentType.Application.Json, HttpStatusCode.BadRequest)
            return@put
        }
        val body = try {
            val text = call.receiveText()
            if (text.isBlank()) InputTextRequest("") else JsonResponses.decodeInputText(text)
        } catch (e: Exception) { InputTextRequest("") }

        sessionVm.setSessionKey(key)
        sessionVm.updateInputText(body.text)
        call.respondText(
            JsonResponses.encode(InputTextResponse(
                body.text,
                sessionVm.state.value.attachments.map { AttachmentUiResponse(it.id, it.mimeType, it.fileName) }
            )),
            ContentType.Application.Json
        )
    }

    get("/api/gateway/status") {
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
            JsonResponses.encode(GatewayStatusResponse(
                state = stateName,
                url = gateway.connectedUrl,
                latencyMs = mainVm.uiState.value.latency
            )),
            ContentType.Application.Json
        )
    }

    post("/api/gateway/connect") {
        server.recordRequest()
        val body = try {
            val text = call.receiveText()
            if (text.isBlank()) GatewayConnectRequest("") else JsonResponses.decodeGatewayConnect(text)
        } catch (e: Exception) { GatewayConnectRequest("") }

        mainVm.connectToGateway(body.url)
        call.respondText(JsonResponses.encode(GatewayConnectResponse(true)), ContentType.Application.Json)
    }

    get("/api/state") {
        server.recordRequest()
        val mainState = mainVm.uiState.value
        val connState = gateway.connectionState.value
        call.respondText(
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

    get("/api/state/session") {
        server.recordRequest()
        val state = sessionVm.state.value
        call.respondText(
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
