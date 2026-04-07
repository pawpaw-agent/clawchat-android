package com.openclaw.clawchat.network.protocol

import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.*
import org.junit.Test

/**
 * ResponseFrame and related classes tests
 */
class ResponseFrameTest {

    // ─────────────────────────────────────────────────────────────
    // ResponseFrame Tests
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `ResponseFrame creates success response`() {
        val frame = ResponseFrame(
            id = "req-1",
            ok = true
        )

        assertEquals("res", frame.type)
        assertEquals("req-1", frame.id)
        assertTrue(frame.ok)
        assertNull(frame.payload)
        assertNull(frame.error)
    }

    @Test
    fun `ResponseFrame creates error response`() {
        val error = ResponseError(
            code = "NOT_FOUND",
            message = "Resource not found"
        )
        val frame = ResponseFrame(
            id = "req-2",
            ok = false,
            error = error
        )

        assertFalse(frame.ok)
        assertEquals(error, frame.error)
    }

    @Test
    fun `ResponseFrame creates with payload`() {
        val payload = JsonPrimitive("test")
        val frame = ResponseFrame(
            id = "req-3",
            ok = true,
            payload = payload
        )

        assertEquals(payload, frame.payload)
    }

    @Test
    fun `ResponseFrame isSuccess returns true for ok without error`() {
        val successFrame = ResponseFrame(id = "req-1", ok = true)
        assertTrue(successFrame.isSuccess())
    }

    @Test
    fun `ResponseFrame isSuccess returns false for error response`() {
        val errorFrame = ResponseFrame(
            id = "req-2",
            ok = false,
            error = ResponseError(code = "ERR", message = "Error")
        )
        assertFalse(errorFrame.isSuccess())
    }

    @Test
    fun `ResponseFrame isSuccess returns false when error exists`() {
        val frameWithUnexpectedError = ResponseFrame(
            id = "req-3",
            ok = true,  // ok=true but has error
            error = ResponseError(code = "ERR", message = "Unexpected error")
        )
        assertFalse(frameWithUnexpectedError.isSuccess())
    }

    @Test
    fun `ResponseFrame copy preserves values`() {
        val original = ResponseFrame(id = "req-1", ok = true)
        val copied = original.copy(ok = false)

        assertEquals("req-1", copied.id)
        assertFalse(copied.ok)
    }

    // ─────────────────────────────────────────────────────────────
    // ResponseError Tests
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `ResponseError creates with required fields`() {
        val error = ResponseError(
            code = "INVALID_REQUEST",
            message = "Invalid request format"
        )

        assertEquals("INVALID_REQUEST", error.code)
        assertEquals("Invalid request format", error.message)
        assertNull(error.details)
    }

    @Test
    fun `ResponseError creates with details`() {
        val error = ResponseError(
            code = "VALIDATION_ERROR",
            message = "Validation failed",
            details = mapOf("field" to "email", "reason" to "invalid format")
        )

        assertEquals(2, error.details!!.size)
        assertEquals("email", error.details["field"])
    }

    @Test
    fun `ResponseError getDetailsString returns formatted string`() {
        val error = ResponseError(
            code = "ERR",
            message = "Error",
            details = mapOf("key1" to "value1", "key2" to "value2")
        )

        val detailsString = error.getDetailsString()
        assertTrue(detailsString.contains("key1: value1"))
        assertTrue(detailsString.contains("key2: value2"))
    }

    @Test
    fun `ResponseError getDetailsString returns empty when no details`() {
        val error = ResponseError(
            code = "ERR",
            message = "Error"
        )

        assertEquals("", error.getDetailsString())
    }

    // ─────────────────────────────────────────────────────────────
    // ResponseErrorCode Tests
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `ResponseErrorCode enum has expected values`() {
        // General errors
        assertEquals("UNKNOWN_ERROR", ResponseErrorCode.UNKNOWN_ERROR.code)
        assertEquals("INVALID_REQUEST", ResponseErrorCode.INVALID_REQUEST.code)
        assertEquals("INTERNAL_ERROR", ResponseErrorCode.INTERNAL_ERROR.code)

        // Auth errors
        assertEquals("AUTH_REQUIRED", ResponseErrorCode.AUTH_REQUIRED.code)
        assertEquals("AUTH_FAILED", ResponseErrorCode.AUTH_FAILED.code)
        assertEquals("TOKEN_EXPIRED", ResponseErrorCode.TOKEN_EXPIRED.code)
        assertEquals("TOKEN_REVOKED", ResponseErrorCode.TOKEN_REVOKED.code)
        assertEquals("DEVICE_NOT_PAIRED", ResponseErrorCode.DEVICE_NOT_PAIRED.code)

        // Session errors
        assertEquals("SESSION_NOT_FOUND", ResponseErrorCode.SESSION_NOT_FOUND.code)
        assertEquals("SESSION_TERMINATED", ResponseErrorCode.SESSION_TERMINATED.code)
        assertEquals("SESSION_PAUSED", ResponseErrorCode.SESSION_PAUSED.code)

        // Message errors
        assertEquals("MESSAGE_TOO_LARGE", ResponseErrorCode.MESSAGE_TOO_LARGE.code)
        assertEquals("INVALID_ATTACHMENT", ResponseErrorCode.INVALID_ATTACHMENT.code)

        // Resource errors
        assertEquals("NOT_FOUND", ResponseErrorCode.NOT_FOUND.code)
        assertEquals("RATE_LIMIT_EXCEEDED", ResponseErrorCode.RATE_LIMIT_EXCEEDED.code)
    }

    @Test
    fun `ResponseErrorCode has descriptions`() {
        assertTrue(ResponseErrorCode.UNKNOWN_ERROR.description.isNotEmpty())
        assertTrue(ResponseErrorCode.AUTH_REQUIRED.description.isNotEmpty())
        assertTrue(ResponseErrorCode.SESSION_NOT_FOUND.description.isNotEmpty())
    }

    // ─────────────────────────────────────────────────────────────
    // SendMessageResponse Tests
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `SendMessageResponse creates correctly`() {
        val response = SendMessageResponse(
            messageId = "msg-1",
            sessionId = "session-1",
            timestamp = 1000L
        )

        assertEquals("msg-1", response.messageId)
        assertEquals("session-1", response.sessionId)
        assertEquals(1000L, response.timestamp)
        assertEquals("sent", response.status)
    }

    @Test
    fun `SendMessageResponse with custom status`() {
        val response = SendMessageResponse(
            messageId = "msg-2",
            sessionId = "session-1",
            timestamp = 2000L,
            status = "delivered"
        )

        assertEquals("delivered", response.status)
    }

    // ─────────────────────────────────────────────────────────────
    // SessionInfo Tests
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `SessionInfo creates with required fields`() {
        val session = SessionInfo(
            id = "session-1",
            label = null,
            model = null,
            status = "running",
            createdAt = 1000L,
            lastActivityAt = 2000L
        )

        assertEquals("session-1", session.id)
        assertNull(session.label)
        assertNull(session.model)
        assertEquals("running", session.status)
        assertEquals(0, session.messageCount)
    }

    @Test
    fun `SessionInfo creates with all fields`() {
        val session = SessionInfo(
            id = "session-2",
            label = "My Session",
            model = "claude-3",
            status = "running",
            createdAt = 1000L,
            lastActivityAt = 2000L,
            messageCount = 10
        )

        assertEquals("My Session", session.label)
        assertEquals("claude-3", session.model)
        assertEquals(10, session.messageCount)
    }

    // ─────────────────────────────────────────────────────────────
    // SessionListResponse Tests
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `SessionListResponse creates correctly`() {
        val sessions = listOf(
            SessionInfo(id = "s1", label = null, model = null, status = "running", createdAt = 0, lastActivityAt = 0)
        )
        val response = SessionListResponse(
            sessions = sessions,
            total = 1
        )

        assertEquals(1, response.sessions.size)
        assertEquals(1, response.total)
        assertFalse(response.hasMore)
    }

    @Test
    fun `SessionListResponse with pagination`() {
        val response = SessionListResponse(
            sessions = emptyList(),
            total = 100,
            hasMore = true
        )

        assertEquals(100, response.total)
        assertTrue(response.hasMore)
    }

    // ─────────────────────────────────────────────────────────────
    // MessageInfo Tests
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `MessageInfo creates with required fields`() {
        val message = MessageInfo(
            id = "msg-1",
            sessionId = "session-1",
            role = "user",
            content = "Hello",
            timestamp = 1000L
        )

        assertEquals("msg-1", message.id)
        assertEquals("session-1", message.sessionId)
        assertEquals("user", message.role)
        assertEquals("Hello", message.content)
        assertNull(message.attachments)
        assertNull(message.metadata)
    }

    @Test
    fun `MessageInfo creates with all fields`() {
        val attachments = listOf(
            AttachmentInfo(id = "att-1", name = "file.pdf", mimeType = "application/pdf", size = 1024)
        )
        val message = MessageInfo(
            id = "msg-2",
            sessionId = "session-1",
            role = "assistant",
            content = "Response",
            timestamp = 2000L,
            attachments = attachments,
            metadata = mapOf("key" to "value")
        )

        assertEquals(1, message.attachments!!.size)
        assertEquals("value", message.metadata!!["key"])
    }

    // ─────────────────────────────────────────────────────────────
    // AttachmentInfo Tests
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `AttachmentInfo creates correctly`() {
        val attachment = AttachmentInfo(
            id = "att-1",
            name = "document.pdf",
            mimeType = "application/pdf",
            size = 2048
        )

        assertEquals("att-1", attachment.id)
        assertEquals("document.pdf", attachment.name)
        assertEquals("application/pdf", attachment.mimeType)
        assertEquals(2048, attachment.size)
        assertNull(attachment.url)
    }

    @Test
    fun `AttachmentInfo with url`() {
        val attachment = AttachmentInfo(
            id = "att-2",
            name = "image.png",
            mimeType = "image/png",
            size = 1024,
            url = "https://example.com/image.png"
        )

        assertEquals("https://example.com/image.png", attachment.url)
    }

    // ─────────────────────────────────────────────────────────────
    // ModelInfo Tests
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `ModelInfo creates with required fields`() {
        val model = ModelInfo(id = "claude-3")

        assertEquals("claude-3", model.id)
        assertNull(model.name)
        assertNull(model.provider)
        assertNull(model.contextWindow)
        assertFalse(model.supportsVision)
        assertTrue(model.supportsTools)
    }

    @Test
    fun `ModelInfo creates with all fields`() {
        val model = ModelInfo(
            id = "claude-3-opus",
            name = "Claude 3 Opus",
            provider = "Anthropic",
            contextWindow = 200000,
            maxOutputTokens = 4096,
            supportsVision = true,
            supportsTools = true,
            inputPrice = 0.015,
            outputPrice = 0.075
        )

        assertEquals("Claude 3 Opus", model.name)
        assertEquals("Anthropic", model.provider)
        assertEquals(200000, model.contextWindow)
        assertEquals(4096, model.maxOutputTokens)
        assertTrue(model.supportsVision)
        assertEquals(0.015, model.inputPrice!!, 0.001)
        assertEquals(0.075, model.outputPrice!!, 0.001)
    }

    // ─────────────────────────────────────────────────────────────
    // AgentInfo Tests
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `AgentInfo creates with required fields`() {
        val agent = AgentInfo(id = "agent-1", name = "Coder Agent")

        assertEquals("agent-1", agent.id)
        assertEquals("Coder Agent", agent.name)
        assertNull(agent.emoji)
        assertNull(agent.avatar)
        assertNull(agent.model)
    }

    @Test
    fun `AgentInfo creates with all fields`() {
        val agent = AgentInfo(
            id = "agent-2",
            name = "Helper Agent",
            workspace = "/home/user/workspace",
            emoji = "🤖",
            avatar = "avatar-url",
            model = "claude-3",
            createdAt = 1000L,
            updatedAt = 2000L
        )

        assertEquals("🤖", agent.emoji)
        assertEquals("/home/user/workspace", agent.workspace)
        assertEquals("claude-3", agent.model)
    }

    // ─────────────────────────────────────────────────────────────
    // GatewayInfo Tests
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `GatewayInfo creates correctly`() {
        val gateway = GatewayInfo(
            id = "gw-1",
            version = "1.0.0",
            mode = "production"
        )

        assertEquals("gw-1", gateway.id)
        assertEquals("1.0.0", gateway.version)
        assertEquals("production", gateway.mode)
        assertNull(gateway.url)
    }

    @Test
    fun `GatewayInfo with url`() {
        val gateway = GatewayInfo(
            id = "gw-2",
            version = "2.0.0",
            mode = "development",
            url = "https://gateway.example.com"
        )

        assertEquals("https://gateway.example.com", gateway.url)
    }

    // ─────────────────────────────────────────────────────────────
    // PingResponse Tests
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `PingResponse creates correctly`() {
        val response = PingResponse(timestamp = 1000L)

        assertEquals(1000L, response.timestamp)
        assertNull(response.latency)
    }

    @Test
    fun `PingResponse with latency`() {
        val response = PingResponse(timestamp = 1000L, latency = 50L)

        assertEquals(50L, response.latency)
    }

    // ─────────────────────────────────────────────────────────────
    // ConfigSchema Tests
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `ConfigSchema creates correctly`() {
        val schema = ConfigSchema(type = "string")

        assertEquals("string", schema.type)
        assertNull(schema.description)
        assertNull(schema.defaultValue)
        assertNull(schema.enumValues)
    }

    @Test
    fun `ConfigSchema with all fields`() {
        val schema = ConfigSchema(
            type = "integer",
            description = "Port number",
            defaultValue = "8080",
            enumValues = null,
            min = 1.0,
            max = 65535.0
        )

        assertEquals("Port number", schema.description)
        assertEquals("8080", schema.defaultValue)
        assertEquals(1.0, schema.min!!, 0.01)
        assertEquals(65535.0, schema.max!!, 0.01)
    }

    @Test
    fun `ConfigSchema with enum values`() {
        val schema = ConfigSchema(
            type = "string",
            enumValues = listOf("light", "dark", "system")
        )

        assertEquals(3, schema.enumValues!!.size)
    }

    // ─────────────────────────────────────────────────────────────
    // ChannelInfo Tests
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `ChannelInfo creates correctly`() {
        val channel = ChannelInfo(
            id = "ch-1",
            type = "websocket",
            status = "connected"
        )

        assertEquals("ch-1", channel.id)
        assertEquals("websocket", channel.type)
        assertEquals("connected", channel.status)
        assertFalse(channel.connected)
    }

    @Test
    fun `ChannelInfo with all fields`() {
        val channel = ChannelInfo(
            id = "ch-2",
            type = "sse",
            name = "Event Stream",
            status = "active",
            connected = true,
            lastActivityAt = 1000L,
            metadata = mapOf("retryCount" to "3")
        )

        assertEquals("Event Stream", channel.name)
        assertTrue(channel.connected)
        assertEquals("3", channel.metadata!!["retryCount"])
    }

    // ─────────────────────────────────────────────────────────────
    // DeviceStatusResponse Tests
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `DeviceStatusResponse creates correctly`() {
        val response = DeviceStatusResponse(
            deviceId = "device-1",
            status = "active",
            paired = true,
            gateway = null
        )

        assertEquals("device-1", response.deviceId)
        assertEquals("active", response.status)
        assertTrue(response.paired)
        assertNull(response.gateway)
        assertNull(response.lastSyncAt)
    }

    @Test
    fun `DeviceStatusResponse with gateway and sync`() {
        val gateway = GatewayInfo(id = "gw-1", version = "1.0.0", mode = "production")
        val response = DeviceStatusResponse(
            deviceId = "device-2",
            status = "active",
            paired = true,
            gateway = gateway,
            lastSyncAt = 1000L
        )

        assertNotNull(response.gateway)
        assertEquals(1000L, response.lastSyncAt)
    }

    // ─────────────────────────────────────────────────────────────
    // DeviceTokenRotateResponse Tests
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `DeviceTokenRotateResponse creates correctly`() {
        val response = DeviceTokenRotateResponse(
            token = "new-token",
            createdAt = 1000L
        )

        assertEquals("new-token", response.token)
        assertEquals(1000L, response.createdAt)
        assertNull(response.expiresAt)
    }

    @Test
    fun `DeviceTokenRotateResponse with expiry`() {
        val response = DeviceTokenRotateResponse(
            token = "new-token",
            createdAt = 1000L,
            expiresAt = 2000L
        )

        assertEquals(2000L, response.expiresAt)
    }

    // ─────────────────────────────────────────────────────────────
    // SessionsSubscribeResponse Tests
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `SessionsSubscribeResponse creates correctly`() {
        val response = SessionsSubscribeResponse(
            subscribed = true,
            sessionKeys = listOf("session-1", "session-2")
        )

        assertTrue(response.subscribed)
        assertEquals(2, response.sessionKeys!!.size)
    }

    @Test
    fun `SessionsSubscribeResponse unsubscribe`() {
        val response = SessionsSubscribeResponse(
            subscribed = false,
            sessionKeys = null
        )

        assertFalse(response.subscribed)
        assertNull(response.sessionKeys)
    }

    // ─────────────────────────────────────────────────────────────
    // ModelsListResponse Tests
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `ModelsListResponse creates correctly`() {
        val models = listOf(
            ModelInfo(id = "claude-3", name = "Claude 3"),
            ModelInfo(id = "gpt-4", name = "GPT-4")
        )
        val response = ModelsListResponse(models = models)

        assertEquals(2, response.models.size)
    }

    // ─────────────────────────────────────────────────────────────
    // AgentsListResponse Tests
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `AgentsListResponse creates correctly`() {
        val agents = listOf(
            AgentInfo(id = "agent-1", name = "Agent 1"),
            AgentInfo(id = "agent-2", name = "Agent 2")
        )
        val response = AgentsListResponse(agents = agents)

        assertEquals(2, response.agents.size)
    }

    // ─────────────────────────────────────────────────────────────
    // ChatInjectResponse Tests
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `ChatInjectResponse creates correctly`() {
        val response = ChatInjectResponse(
            messageId = "msg-1",
            sessionId = "session-1",
            timestamp = 1000L
        )

        assertEquals("msg-1", response.messageId)
        assertEquals("session-1", response.sessionId)
        assertEquals(1000L, response.timestamp)
    }
}