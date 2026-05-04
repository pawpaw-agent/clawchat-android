package com.openclaw.clawchat.testapi

import com.openclaw.clawchat.ui.components.AgentItem
import com.openclaw.clawchat.ui.components.ModelItem
import com.openclaw.clawchat.ui.state.MainUiState
import com.openclaw.clawchat.ui.state.MainViewModel
import com.openclaw.clawchat.ui.state.MessageUi
import com.openclaw.clawchat.ui.state.SessionUiState
import com.openclaw.clawchat.ui.state.SessionViewModel
import com.openclaw.clawchat.ui.state.SessionUi
import com.openclaw.clawchat.network.protocol.GatewayConnection
import com.openclaw.clawchat.network.WebSocketConnectionState
import io.ktor.server.routing.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * All Test API route handlers.
 * Delegates to MainViewModel, SessionViewModel, and GatewayConnection.
 */
fun Routing.testApiRoutes(server: TestApiServer) {

    val mainVm: MainViewModel by lazy { server.mainViewModel }
    val sessionVm: SessionViewModel by lazy { server.sessionViewModel }
    val gateway: GatewayConnection by lazy { server.gatewayConnection }

    // ─── Health ────────────────────────────────────────────────────────────────

    get("/api/health") {
        withContext(Dispatchers.IO) {
            server.recordRequest()
            respondJson(HttpStatusCode.OK, HealthResponse("ok", "ClawChat Test API"))
        }
    }

    // ─── Agents / Models ────────────────────────────────────────────────────────

    get("/api/agents") {
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
            respondJson(HttpStatusCode.OK, AgentsResponse(agents))
        }
    }

    get("/api/models") {
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
            respondJson(HttpStatusCode.OK, ModelsResponse(models))
        }
    }

    // ─── Sessions ──────────────────────────────────────────────────────────────

    get("/api/sessions") {
        withContext(Dispatchers.IO) {
            server.recordRequest()
            val state = mainVm.uiState.value
            val sessions = state.sessions.map { it.toSessionResponse() }
            respondJson(HttpStatusCode.OK, SessionsResponse(
                sessions = sessions,
                currentSessionKey = state.currentSession?.key
            ))
        }
    }

    get("/api/sessions/{key}") {
        withContext(Dispatchers.IO) {
            server.recordRequest()
            val key = call.parameters["key"] ?: return@get respondError(HttpStatusCode.BadRequest, "Missing key")
            val state = mainVm.uiState.value
            val session = state.sessions.find { it.key == key }
            if (session == null) {
                respondJsonOrNull(HttpStatusCode.OK, null)
            } else {
                respondJson(HttpStatusCode.OK, session.toSessionResponse())
            }
        }
    }

    post("/api/sessions") {
        withContext(Dispatchers.IO) {
            server.recordRequest()
            val body = call.receiveNullable<CreateSessionRequest>() ?: CreateSessionRequest()
            // Switch to Main to call createSession
            withContext(Dispatchers.Main) {
                mainVm.createSessionWithAgentModel(
                    agentId = body.agentId,
                    model = body.model,
                    initialMessage = body.initialMessage,
                    label = body.label
                )
            }
            val state = mainVm.uiState.value
            val newSession = state.currentSession
            respondJson(HttpStatusCode.Created, CreateSessionResponse(
                key = newSession?.key ?: "",
                label = newSession?.label
            ))
        }
    }

    delete("/api/sessions/{key}") {
        withContext(Dispatchers.IO) {
            server.recordRequest()
            val key = call.parameters["key"] ?: return@get respondError(HttpStatusCode.BadRequest, "Missing key")
            withContext(Dispatchers.Main) {
                mainVm.deleteSession(key)
            }
            respondJson(HttpStatusCode.OK, DeleteResponse(deleted = true))
        }
    }

    post("/api/sessions/{key}/reset") {
        withContext(Dispatchers.IO) {
            server.recordRequest()
            val key = call.parameters["key"] ?: return@get respondError(HttpStatusCode.BadRequest, "Missing key")
            withContext(Dispatchers.Main) {
                sessionVm.setSessionKey(key)
                mainVm.clearCurrentSession()
            }
            respondJson(HttpStatusCode.OK, ResetResponse(success = true))
        }
    }

    // ─── Messages ───────────────────────────────────────────────────────────────

    post("/api/sessions/{key}/messages") {
        withContext(Dispatchers.IO) {
            server.recordRequest()
            val key = call.parameters["key"] ?: return@get respondError(HttpStatusCode.BadRequest, "Missing key")
            val body = call.receiveNullable<MessageRequest>() ?: return@get respondError(HttpStatusCode.BadRequest, "Missing body")

            withContext(Dispatchers.Main) {
                sessionVm.setSessionKey(key)
                // Handle attachments if any
                body.attachments?.forEach { att ->
                    if (att.type == "base64") {
                        val mimeType = att.mimeType
                        val dataUrl = "data:$mimeType;base64,${att.content}"
                        val attachment = com.openclaw.clawchat.ui.state.AttachmentUi(
                            id = java.util.UUID.randomUUID().toString(),
                            uri = null,
                            mimeType = mimeType,
                            fileName = att.fileName ?: "attachment",
                            dataUrl = dataUrl
                        )
                        sessionVm.addAttachment(attachment)
                    }
                }
                sessionVm.sendMessage(body.text)
            }

            val runId = sessionVm.state.value.chatRunId
            respondJson(HttpStatusCode.Accepted, MessageResponse(runId = runId, status = "accepted"))
        }
    }

    post("/api/sessions/{key}/abort") {
        withContext(Dispatchers.IO) {
            server.recordRequest()
            val key = call.parameters["key"] ?: return@get respondError(HttpStatusCode.BadRequest, "Missing key")
            withContext(Dispatchers.Main) {
                sessionVm.setSessionKey(key)
                sessionVm.abortChat()
            }
            respondJson(HttpStatusCode.OK, MessageResponse(status = "aborted"))
        }
    }

    // ─── Input Text ───────────────────────────────────────────────────────────

    get("/api/sessions/{key}/input") {
        withContext(Dispatchers.IO) {
            server.recordRequest()
            val key = call.parameters["key"] ?: return@get respondError(HttpStatusCode.BadRequest, "Missing key")
            withContext(Dispatchers.Main) {
                sessionVm.setSessionKey(key)
            }
            val state = sessionVm.state.value
            respondJson(HttpStatusCode.OK, InputTextResponse(
                text = state.inputText,
                attachments = state.attachments.map { att ->
                    AttachmentUiResponse(
                        id = att.id,
                        mimeType = att.mimeType,
                        fileName = att.fileName
                    )
                }
            ))
        }
    }

    put("/api/sessions/{key}/input") {
        withContext(Dispatchers.IO) {
            server.recordRequest()
            val key = call.parameters["key"] ?: return@get respondError(HttpStatusCode.BadRequest, "Missing key")
            val body = call.receiveNullable<InputTextRequest>() ?: return@get respondError(HttpStatusCode.BadRequest, "Missing body")
            withContext(Dispatchers.Main) {
                sessionVm.setSessionKey(key)
                sessionVm.updateInputText(body.text)
            }
            respondJson(HttpStatusCode.OK, InputTextResponse(
                text = body.text,
                attachments = sessionVm.state.value.attachments.map { att ->
                    AttachmentUiResponse(att.id, att.mimeType, att.fileName)
                }
            ))
        }
    }

    // ─── Gateway ────────────────────────────────────────────────────────────────

    get("/api/gateway/status") {
        withContext(Dispatchers.IO) {
            server.recordRequest()
            val connState = gateway.connectionState.value
            val now = System.currentTimeMillis()
            val connectedAt = when (connState) {
                is WebSocketConnectionState.Connected -> now - (connState.connectedAtMillis ?: 0)
                else -> null
            }
            val latency = mainVm.uiState.value.latency
            respondJson(HttpStatusCode.OK, GatewayStatusResponse(
                state = connState::class.simpleName ?: "Unknown",
                url = (connState as? WebSocketConnectionState.Connected)?.url,
                latencyMs = latency,
                connectedAt = connectedAt
            ))
        }
    }

    post("/api/gateway/connect") {
        withContext(Dispatchers.IO) {
            server.recordRequest()
            val body = call.receiveNullable<GatewayConnectRequest>() ?: return@get respondError(HttpStatusCode.BadRequest, "Missing body")
            withContext(Dispatchers.Main) {
                mainVm.connectToGateway(body.url)
            }
            respondJson(HttpStatusCode.OK, GatewayConnectResponse(connected = true))
        }
    }

    // ─── State ─────────────────────────────────────────────────────────────────

    get("/api/state") {
        withContext(Dispatchers.IO) {
            server.recordRequest()
            val mainState = mainVm.uiState.value
            val connState = gateway.connectionState.value
            respondJson(HttpStatusCode.OK, AppStateResponse(
                gateway = GatewayStatusResponse(
                    state = connState::class.simpleName ?: "Unknown",
                    url = (connState as? WebSocketConnectionState.Connected)?.url,
                    latencyMs = mainState.latency
                ),
                sessions = SessionsSummary(
                    count = mainState.sessions.size,
                    currentKey = mainState.currentSession?.key
                ),
                currentSessionKey = mainState.currentSession?.key
            ))
        }
    }

    get("/api/state/session") {
        withContext(Dispatchers.IO) {
            server.recordRequest()
            val state = sessionVm.state.value
            respondJson(HttpStatusCode.OK, SessionStateResponse(
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
                        role = msg.role,
                        content = msg.content,
                        createdAt = msg.createdAt,
                        status = msg.status
                    )
                },
                totalTokens = state.totalTokens,
                contextTokens = state.contextTokensLimit,
                chatStream = state.chatStream,
                chatRunId = state.chatRunId
            ))
        }
    }
}

// ─── Extension helpers ────────────────────────────────────────────────────────

private fun SessionUi.toSessionResponse() = SessionResponse(
    key = key,
    kind = kind,
    label = label,
    model = model,
    agentId = agentId,
    agentName = agentName,
    status = status,
    updatedAt = updatedAt,
    totalTokens = totalTokens,
    contextTokens = contextTokens,
    inputTokens = inputTokens,
    outputTokens = outputTokens,
    pinned = pinned,
    createdAt = createdAt
)
