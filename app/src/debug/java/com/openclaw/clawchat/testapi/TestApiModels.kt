package com.openclaw.clawchat.testapi

import kotlinx.serialization.Serializable

@Serializable
data class SessionResponse(
    val key: String,
    val kind: String? = null,
    val label: String? = null,
    val model: String? = null,
    val agentId: String? = null,
    val agentName: String? = null,
    val status: String? = null,
    val updatedAt: Long? = null,
    val totalTokens: Int? = null,
    val contextTokens: Int? = null,
    val inputTokens: Int? = null,
    val outputTokens: Int? = null,
    val pinned: Boolean = false,
    val createdAt: Long? = null
)

@Serializable
data class SessionsResponse(
    val sessions: List<SessionResponse>,
    val currentSessionKey: String?
)

@Serializable
data class CreateSessionRequest(
    val model: String? = null,
    val agentId: String? = null,
    val label: String? = null,
    val initialMessage: String? = null,
    val thinking: Boolean? = null
)

@Serializable
data class CreateSessionResponse(
    val key: String,
    val label: String?
)

@Serializable
data class DeleteResponse(
    val deleted: Boolean
)

@Serializable
data class ResetResponse(
    val success: Boolean
)

@Serializable
data class MessageRequest(
    val text: String,
    val attachments: List<AttachmentRequest>? = null
)

@Serializable
data class AttachmentRequest(
    val type: String, // "base64"
    val mimeType: String,
    val content: String,
    val fileName: String? = null
)

@Serializable
data class MessageResponse(
    val runId: String? = null,
    val status: String
)

@Serializable
data class InputTextRequest(
    val text: String
)

@Serializable
data class InputTextResponse(
    val text: String,
    val attachments: List<AttachmentUiResponse>
)

@Serializable
data class AttachmentUiResponse(
    val id: String,
    val mimeType: String,
    val fileName: String?
)

@Serializable
data class GatewayStatusResponse(
    val state: String,
    val url: String?,
    val latencyMs: Long? = null,
    val connectedAt: Long? = null,
    val error: String? = null
)

@Serializable
data class GatewayConnectRequest(
    val url: String,
    val token: String? = null
)

@Serializable
data class GatewayConnectResponse(
    val connected: Boolean
)

@Serializable
data class AgentResponse(
    val id: String,
    val name: String,
    val emoji: String?,
    val model: String?
)

@Serializable
data class AgentsResponse(
    val agents: List<AgentResponse>
)

@Serializable
data class ModelResponse(
    val id: String,
    val name: String,
    val provider: String?,
    val supportsVision: Boolean = false,
    val contextWindow: Int? = null
)

@Serializable
data class ModelsResponse(
    val models: List<ModelResponse>
)

@Serializable
data class AppStateResponse(
    val gateway: GatewayStatusResponse,
    val sessions: SessionsSummary,
    val currentSessionKey: String?
)

@Serializable
data class SessionsSummary(
    val count: Int,
    val currentKey: String?
)

@Serializable
data class SessionStateResponse(
    val sessionKey: String?,
    val isSending: Boolean,
    val isLoading: Boolean,
    val inputText: String,
    val attachments: List<AttachmentUiResponse>,
    val chatMessages: List<MessageUiResponse>,
    val totalTokens: Int?,
    val contextTokens: Int?,
    val chatStream: String?,
    val chatRunId: String?
)

@Serializable
data class MessageUiResponse(
    val id: String,
    val role: String,
    val content: String,
    val createdAt: Long?,
    val status: String?
)

@Serializable
data class HealthResponse(
    val status: String,
    val server: String,
    val version: String = "1.0.0"
)

@Serializable
data class ErrorResponse(
    val error: String,
    val code: String? = null
)
