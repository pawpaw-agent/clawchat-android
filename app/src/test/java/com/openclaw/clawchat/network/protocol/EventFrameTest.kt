package com.openclaw.clawchat.network.protocol

import org.junit.Assert.*
import org.junit.Test

/**
 * EventFrame and protocol data classes tests
 */
class EventFrameTest {

    // ─────────────────────────────────────────────────────────────
    // EventFrame Tests
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `EventFrame creates with required fields`() {
        val frame = EventFrame(event = "test.event")

        assertEquals("event", frame.type)
        assertEquals("test.event", frame.event)
        assertNull(frame.payload)
        assertNull(frame.seq)
        assertNull(frame.stateVersion)
    }

    @Test
    fun `EventFrame creates with all fields`() {
        val payload = kotlinx.serialization.json.JsonPrimitive("test")
        val frame = EventFrame(
            type = "event",
            event = "session.message",
            payload = payload,
            seq = 1,
            stateVersion = "v1"
        )

        assertEquals("event", frame.type)
        assertEquals("session.message", frame.event)
        assertEquals(payload, frame.payload)
        assertEquals(1, frame.seq)
        assertEquals("v1", frame.stateVersion)
    }

    // ─────────────────────────────────────────────────────────────
    // GatewayEvent Tests
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `GatewayEvent enum values`() {
        // Message events
        assertEquals("session.message", GatewayEvent.SESSION_MESSAGE.value)
        assertEquals("session.message.update", GatewayEvent.SESSION_MESSAGE_UPDATE.value)
        assertEquals("session.message.delete", GatewayEvent.SESSION_MESSAGE_DELETE.value)

        // Session events
        assertEquals("session.create", GatewayEvent.SESSION_CREATE.value)
        assertEquals("session.update", GatewayEvent.SESSION_UPDATE.value)
        assertEquals("session.terminate", GatewayEvent.SESSION_TERMINATE.value)
        assertEquals("session.pause", GatewayEvent.SESSION_PAUSE.value)
        assertEquals("session.resume", GatewayEvent.SESSION_RESUME.value)

        // Input events
        assertEquals("session.typing", GatewayEvent.SESSION_TYPING.value)
        assertEquals("session.thinking", GatewayEvent.SESSION_THINKING.value)

        // Connection events
        assertEquals("connect.challenge", GatewayEvent.CONNECT_CHALLENGE.value)
        assertEquals("connect.ok", GatewayEvent.CONNECT_OK.value)
        assertEquals("connect.error", GatewayEvent.CONNECT_ERROR.value)
        assertEquals("disconnect", GatewayEvent.DISCONNECT.value)

        // Error
        assertEquals("error", GatewayEvent.ERROR.value)
    }

    // ─────────────────────────────────────────────────────────────
    // SessionMessagePayload Tests
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `SessionMessagePayload creates correctly`() {
        val messageInfo = MessageInfo(
            id = "msg-1",
            role = "user",
            content = emptyList()
        )
        val payload = SessionMessagePayload(
            sessionId = "session-1",
            message = messageInfo,
            seq = 1
        )

        assertEquals("session-1", payload.sessionId)
        assertEquals(messageInfo, payload.message)
        assertEquals(1, payload.seq)
    }

    // ─────────────────────────────────────────────────────────────
    // MessageUpdates Tests
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `MessageUpdates creates with null fields`() {
        val updates = MessageUpdates()

        assertNull(updates.content)
        assertNull(updates.metadata)
        assertNull(updates.status)
    }

    @Test
    fun `MessageUpdates creates with all fields`() {
        val updates = MessageUpdates(
            content = "Updated content",
            metadata = mapOf("key" to "value"),
            status = "delivered"
        )

        assertEquals("Updated content", updates.content)
        assertEquals("value", updates.metadata!!["key"])
        assertEquals("delivered", updates.status)
    }

    // ─────────────────────────────────────────────────────────────
    // SessionUpdates Tests
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `SessionUpdates creates with null fields`() {
        val updates = SessionUpdates()

        assertNull(updates.label)
        assertNull(updates.status)
        assertNull(updates.metadata)
    }

    @Test
    fun `SessionUpdates creates with all fields`() {
        val updates = SessionUpdates(
            label = "New Label",
            status = "running",
            metadata = mapOf("agent" to "coder")
        )

        assertEquals("New Label", updates.label)
        assertEquals("running", updates.status)
        assertEquals("coder", updates.metadata!!["agent"])
    }

    // ─────────────────────────────────────────────────────────────
    // ConnectChallengePayload Tests
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `ConnectChallengePayload creates correctly`() {
        val payload = ConnectChallengePayload(
            nonce = "abc123",
            timestamp = 1000L
        )

        assertEquals("abc123", payload.nonce)
        assertEquals(1000L, payload.timestamp)
    }

    // ─────────────────────────────────────────────────────────────
    // ConnectOkPayload Tests
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `ConnectOkPayload creates with required fields`() {
        val payload = ConnectOkPayload(
            deviceToken = "token-123",
            timestamp = 2000L
        )

        assertEquals("token-123", payload.deviceToken)
        assertEquals(2000L, payload.timestamp)
        assertNull(payload.gatewayInfo)
    }

    @Test
    fun `ConnectOkPayload creates with gatewayInfo`() {
        val gatewayInfo = GatewayInfo(
            version = "1.0.0",
            features = listOf("chat", "tools")
        )
        val payload = ConnectOkPayload(
            deviceToken = "token-123",
            timestamp = 2000L,
            gatewayInfo = gatewayInfo
        )

        assertNotNull(payload.gatewayInfo)
        assertEquals("1.0.0", payload.gatewayInfo!!.version)
    }

    // ─────────────────────────────────────────────────────────────
    // ConnectErrorPayload Tests
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `ConnectErrorPayload creates correctly`() {
        val payload = ConnectErrorPayload(
            code = "AUTH_FAILED",
            message = "Authentication failed"
        )

        assertEquals("AUTH_FAILED", payload.code)
        assertEquals("Authentication failed", payload.message)
        assertNull(payload.details)
    }

    @Test
    fun `ConnectErrorPayload creates with details`() {
        val payload = ConnectErrorPayload(
            code = "RATE_LIMITED",
            message = "Too many requests",
            details = mapOf("retryAfter" to "60")
        )

        assertEquals("RATE_LIMITED", payload.code)
        assertEquals("60", payload.details!!["retryAfter"])
    }

    // ─────────────────────────────────────────────────────────────
    // DisconnectPayload Tests
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `DisconnectPayload creates correctly`() {
        val payload = DisconnectPayload(
            reason = "Server shutdown",
            timestamp = 3000L
        )

        assertEquals("Server shutdown", payload.reason)
        assertEquals(3000L, payload.timestamp)
    }

    // ─────────────────────────────────────────────────────────────
    // SessionTypingPayload Tests
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `SessionTypingPayload creates correctly`() {
        val payload = SessionTypingPayload(
            sessionId = "session-1",
            isTyping = true,
            userId = "user-1"
        )

        assertEquals("session-1", payload.sessionId)
        assertTrue(payload.isTyping)
        assertEquals("user-1", payload.userId)
    }

    // ─────────────────────────────────────────────────────────────
    // SessionThinkingPayload Tests
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `SessionThinkingPayload creates correctly`() {
        val payload = SessionThinkingPayload(
            sessionId = "session-1",
            isThinking = true,
            model = "claude-3"
        )

        assertEquals("session-1", payload.sessionId)
        assertTrue(payload.isThinking)
        assertEquals("claude-3", payload.model)
    }

    // ─────────────────────────────────────────────────────────────
    // SessionTerminatePayload Tests
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `SessionTerminatePayload creates correctly`() {
        val payload = SessionTerminatePayload(
            sessionId = "session-1",
            reason = "User requested",
            terminatedAt = 4000L
        )

        assertEquals("session-1", payload.sessionId)
        assertEquals("User requested", payload.reason)
        assertEquals(4000L, payload.terminatedAt)
    }

    // ─────────────────────────────────────────────────────────────
    // SystemNotificationPayload Tests
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `SystemNotificationPayload creates correctly`() {
        val payload = SystemNotificationPayload(
            title = "Update Available",
            message = "A new version is available",
            type = "info"
        )

        assertEquals("Update Available", payload.title)
        assertEquals("A new version is available", payload.message)
        assertEquals("info", payload.type)
        assertNull(payload.action)
    }

    @Test
    fun `SystemNotificationPayload creates with action`() {
        val action = SystemAction(
            type = "open_url",
            params = mapOf("url" to "https://example.com")
        )
        val payload = SystemNotificationPayload(
            title = "Update Required",
            message = "Please update",
            type = "warning",
            action = action
        )

        assertNotNull(payload.action)
        assertEquals("open_url", payload.action!!.type)
        assertEquals("https://example.com", payload.action.params!!["url"])
    }

    // ─────────────────────────────────────────────────────────────
    // SystemErrorPayload Tests
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `SystemErrorPayload creates correctly`() {
        val payload = SystemErrorPayload(
            code = "INTERNAL_ERROR",
            message = "Something went wrong"
        )

        assertEquals("INTERNAL_ERROR", payload.code)
        assertEquals("Something went wrong", payload.message)
        assertNull(payload.details)
        assertFalse(payload.recoverable)
    }

    @Test
    fun `SystemErrorPayload creates with recoverable flag`() {
        val payload = SystemErrorPayload(
            code = "TIMEOUT",
            message = "Request timed out",
            recoverable = true
        )

        assertTrue(payload.recoverable)
    }

    // ─────────────────────────────────────────────────────────────
    // SystemUpdatePayload Tests
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `SystemUpdatePayload creates correctly`() {
        val payload = SystemUpdatePayload(
            version = "2.0.0",
            changes = listOf("Feature A", "Bug fix B"),
            required = true
        )

        assertEquals("2.0.0", payload.version)
        assertEquals(2, payload.changes!!.size)
        assertTrue(payload.required)
    }

    // ─────────────────────────────────────────────────────────────
    // ErrorPayload Tests
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `ErrorPayload creates correctly`() {
        val payload = ErrorPayload(
            code = "INVALID_REQUEST",
            message = "Invalid request format"
        )

        assertEquals("INVALID_REQUEST", payload.code)
        assertEquals("Invalid request format", payload.message)
        assertNull(payload.details)
        assertNull(payload.requestId)
    }

    @Test
    fun `ErrorPayload creates with all fields`() {
        val payload = ErrorPayload(
            code = "VALIDATION_ERROR",
            message = "Field validation failed",
            details = mapOf("field" to "email"),
            requestId = "req-123"
        )

        assertEquals("email", payload.details!!["field"])
        assertEquals("req-123", payload.requestId)
    }
}