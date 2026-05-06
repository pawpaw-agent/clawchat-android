package com.openclaw.clawchat.testapi

import com.openclaw.clawchat.network.protocol.GatewayConnection
import com.openclaw.clawchat.network.WebSocketConnectionState
import com.openclaw.clawchat.ui.state.MainViewModel
import com.openclaw.clawchat.ui.state.SessionViewModel
import com.openclaw.clawchat.ui.state.SessionUi

fun io.ktor.server.routing.Routing.installTestApiRoutes(
    mainVm: MainViewModel,
    sessionVm: SessionViewModel,
    gateway: GatewayConnection,
    server: TestApiServer
) {
    get("/api/health") {
        server.recordRequest()
        call.respondText(JsonResponses.encode(HealthResponse("ok", "ClawChat Test API")), io.ktor.http.ContentType.Application.Json)
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
