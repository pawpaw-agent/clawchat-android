package com.openclaw.clawchat.network.protocol

import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.*
import org.junit.Test

/**
 * RequestFrame and related classes tests
 */
class RequestFrameTest {

    // ─────────────────────────────────────────────────────────────
    // RequestFrame Tests
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `RequestFrame creates with required fields`() {
        val frame = RequestFrame(
            id = "req-1",
            method = "ping"
        )

        assertEquals("req", frame.type)
        assertEquals("req-1", frame.id)
        assertEquals("ping", frame.method)
        assertNull(frame.params)
    }

    @Test
    fun `RequestFrame creates with all fields`() {
        val params = mapOf("key" to JsonPrimitive("value"))
        val frame = RequestFrame(
            type = "req",
            id = "req-2",
            method = "session.send",
            params = params
        )

        assertEquals("req", frame.type)
        assertEquals("req-2", frame.id)
        assertEquals("session.send", frame.method)
        assertEquals(params, frame.params)
    }

    @Test
    fun `RequestFrame copy preserves values`() {
        val original = RequestFrame(
            id = "req-3",
            method = "ping"
        )

        val copied = original.copy(method = "chat.send")

        assertEquals("req-3", copied.id)
        assertEquals("chat.send", copied.method)
    }

    @Test
    fun `RequestFrame equality works`() {
        val frame1 = RequestFrame(id = "req-1", method = "ping")
        val frame2 = RequestFrame(id = "req-1", method = "ping")
        val frame3 = RequestFrame(id = "req-2", method = "ping")

        assertEquals(frame1, frame2)
        assertNotEquals(frame1, frame3)
    }

    // ─────────────────────────────────────────────────────────────
    // RequestIdGenerator Tests
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `RequestIdGenerator generates unique IDs`() {
        val id1 = RequestIdGenerator.generateRequestId()
        val id2 = RequestIdGenerator.generateRequestId()

        assertNotEquals(id1, id2)
        assertTrue(id1.startsWith("req-"))
        assertTrue(id2.startsWith("req-"))
    }

    @Test
    fun `RequestIdGenerator IDs contain timestamp`() {
        val beforeTime = System.currentTimeMillis()
        val id = RequestIdGenerator.generateRequestId()
        val afterTime = System.currentTimeMillis()

        // ID should contain a timestamp between before and after
        assertTrue(id.contains("-"))
        val parts = id.split("-")
        assertTrue(parts.size >= 3)  // req-timestamp-counter
    }

    @Test
    fun `RequestIdGenerator is thread-safe`() {
        val ids = mutableListOf<String>()
        val threads = (1..10).map {
            Thread {
                synchronized(ids) {
                    ids.add(RequestIdGenerator.generateRequestId())
                }
            }
        }

        threads.forEach { it.start() }
        threads.forEach { it.join() }

        // All IDs should be unique
        assertEquals(10, ids.toSet().size)
    }

    // ─────────────────────────────────────────────────────────────
    // GatewayMethod Tests
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `GatewayMethod enum values`() {
        // Authentication
        assertEquals("connect", GatewayMethod.CONNECT.value)

        // Chat
        assertEquals("chat.send", GatewayMethod.CHAT_SEND.value)
        assertEquals("chat.history", GatewayMethod.CHAT_HISTORY.value)
        assertEquals("chat.inject", GatewayMethod.CHAT_INJECT.value)
        assertEquals("chat.abort", GatewayMethod.CHAT_ABORT.value)

        // Sessions
        assertEquals("sessions.list", GatewayMethod.SESSIONS_LIST.value)
        assertEquals("sessions.create", GatewayMethod.SESSIONS_CREATE.value)
        assertEquals("sessions.delete", GatewayMethod.SESSIONS_DELETE.value)
        assertEquals("sessions.reset", GatewayMethod.SESSIONS_RESET.value)
        assertEquals("sessions.patch", GatewayMethod.SESSIONS_PATCH.value)
        assertEquals("sessions.preview", GatewayMethod.SESSIONS_PREVIEW.value)
        assertEquals("sessions.usage", GatewayMethod.SESSIONS_USAGE.value)
        assertEquals("sessions.steer", GatewayMethod.SESSIONS_STEER.value)

        // Agents
        assertEquals("agents.list", GatewayMethod.AGENTS_LIST.value)
        assertEquals("agents.create", GatewayMethod.AGENTS_CREATE.value)
        assertEquals("agents.update", GatewayMethod.AGENTS_UPDATE.value)
        assertEquals("agents.delete", GatewayMethod.AGENTS_DELETE.value)

        // Models
        assertEquals("models.list", GatewayMethod.MODELS_LIST.value)

        // Config
        assertEquals("config.get", GatewayMethod.CONFIG_GET.value)
        assertEquals("config.set", GatewayMethod.CONFIG_SET.value)
        assertEquals("config.patch", GatewayMethod.CONFIG_PATCH.value)
        assertEquals("config.schema", GatewayMethod.CONFIG_SCHEMA.value)

        // Channels
        assertEquals("channels.status", GatewayMethod.CHANNELS_STATUS.value)
        assertEquals("channels.logout", GatewayMethod.CHANNELS_LOGOUT.value)

        // Device
        assertEquals("device.token.rotate", GatewayMethod.DEVICE_TOKEN_ROTATE.value)
        assertEquals("device.token.revoke", GatewayMethod.DEVICE_TOKEN_REVOKE.value)

        // System
        assertEquals("ping", GatewayMethod.PING.value)
    }

    @Test
    fun `GatewayMethod enum has expected count`() {
        assertTrue(GatewayMethod.entries.size >= 25)
    }

    // ─────────────────────────────────────────────────────────────
    // RequestParamsBuilder Tests
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `RequestParamsBuilder puts string`() {
        val params = RequestParamsBuilder()
            .putString("key", "value")
            .build()

        assertEquals(1, params.size)
        assertEquals("value", (params["key"] as JsonPrimitive).content)
    }

    @Test
    fun `RequestParamsBuilder puts int`() {
        val params = RequestParamsBuilder()
            .putInt("count", 42)
            .build()

        assertEquals(1, params.size)
        assertEquals(42, (params["count"] as JsonPrimitive).int)
    }

    @Test
    fun `RequestParamsBuilder puts long`() {
        val params = RequestParamsBuilder()
            .putLong("timestamp", 1234567890L)
            .build()

        assertEquals(1, params.size)
        assertEquals(1234567890L, (params["timestamp"] as JsonPrimitive).long)
    }

    @Test
    fun `RequestParamsBuilder puts boolean`() {
        val params = RequestParamsBuilder()
            .putBoolean("enabled", true)
            .build()

        assertEquals(1, params.size)
        assertTrue((params["enabled"] as JsonPrimitive).boolean)
    }

    @Test
    fun `RequestParamsBuilder chains multiple params`() {
        val params = RequestParamsBuilder()
            .putString("key", "value")
            .putInt("count", 10)
            .putBoolean("enabled", false)
            .build()

        assertEquals(3, params.size)
        assertEquals("value", (params["key"] as JsonPrimitive).content)
        assertEquals(10, (params["count"] as JsonPrimitive).int)
        assertFalse((params["enabled"] as JsonPrimitive).boolean)
    }

    @Test
    fun `RequestParamsBuilder build returns immutable map`() {
        val params = RequestParamsBuilder()
            .putString("key", "value")
            .build()

        // The returned map should not be modifiable
        assertEquals(1, params.size)
    }

    // ─────────────────────────────────────────────────────────────
    // Helper Function Tests
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `requestFrame creates valid frame`() {
        val frame = requestFrame(GatewayMethod.PING) {
            putString("test", "value")
        }

        assertEquals("req", frame.type)
        assertEquals("ping", frame.method)
        assertNotNull(frame.params)
        assertEquals("value", (frame.params!!["test"] as JsonPrimitive).content)
    }

    @Test
    fun `requestFrame with empty params`() {
        val frame = requestFrame(GatewayMethod.SESSIONS_LIST)

        assertNotNull(frame.id)
        assertEquals("sessions.list", frame.method)
        assertTrue(frame.params!!.isEmpty())
    }

    @Test
    fun `chatSendRequest creates valid frame`() {
        val frame = chatSendRequest(
            sessionKey = "session-1",
            message = "Hello world"
        )

        assertEquals("chat.send", frame.method)
        assertEquals("session-1", (frame.params!!["sessionKey"] as JsonPrimitive).content)
        assertEquals("Hello world", (frame.params["message"] as JsonPrimitive).content)
        assertNotNull(frame.params["idempotencyKey"])
    }

    @Test
    fun `sessionsListRequest creates valid frame`() {
        val frame = sessionsListRequest()

        assertEquals("sessions.list", frame.method)
        assertTrue(frame.params!!.isEmpty())
    }

    @Test
    fun `sessionsCreateRequest creates valid frame`() {
        val frame = sessionsCreateRequest(
            key = "session-key",
            agentId = "agent:coder",
            label = "My Session",
            model = "claude-3"
        )

        assertEquals("sessions.create", frame.method)
        assertEquals("session-key", (frame.params!!["key"] as JsonPrimitive).content)
        assertEquals("agent:coder", (frame.params["agentId"] as JsonPrimitive).content)
        assertEquals("My Session", (frame.params["label"] as JsonPrimitive).content)
        assertEquals("claude-3", (frame.params["model"] as JsonPrimitive).content)
    }

    @Test
    fun `sessionsCreateRequest with minimal params`() {
        val frame = sessionsCreateRequest()

        assertEquals("sessions.create", frame.method)
        assertNull(frame.params!!["key"])
        assertNull(frame.params["agentId"])
    }

    @Test
    fun `sessionsDeleteRequest creates valid frame`() {
        val frame = sessionsDeleteRequest(
            sessionKey = "session-1",
            deleteTranscript = true
        )

        assertEquals("sessions.delete", frame.method)
        assertEquals("session-1", (frame.params!!["key"] as JsonPrimitive).content)
        assertTrue((frame.params["deleteTranscript"] as JsonPrimitive).boolean)
    }

    @Test
    fun `sessionsDeleteRequest without deleteTranscript`() {
        val frame = sessionsDeleteRequest(
            sessionKey = "session-1",
            deleteTranscript = false
        )

        assertEquals("sessions.delete", frame.method)
        assertNull(frame.params!!["deleteTranscript"])
    }

    @Test
    fun `sessionsResetRequest creates valid frame`() {
        val frame = sessionsResetRequest(
            sessionKey = "session-1",
            reason = "user requested"
        )

        assertEquals("sessions.reset", frame.method)
        assertEquals("session-1", (frame.params!!["key"] as JsonPrimitive).content)
        assertEquals("user requested", (frame.params["reason"] as JsonPrimitive).content)
    }

    @Test
    fun `chatAbortRequest creates valid frame`() {
        val frame = chatAbortRequest(
            sessionKey = "session-1",
            runId = "run-123"
        )

        assertEquals("chat.abort", frame.method)
        assertEquals("session-1", (frame.params!!["sessionKey"] as JsonPrimitive).content)
        assertEquals("run-123", (frame.params["runId"] as JsonPrimitive).content)
    }

    @Test
    fun `chatAbortRequest without runId`() {
        val frame = chatAbortRequest(sessionKey = "session-1")

        assertEquals("chat.abort", frame.method)
        assertNull(frame.params!!["runId"])
    }

    @Test
    fun `pingRequest creates valid frame`() {
        val frame = pingRequest()

        assertEquals("ping", frame.method)
        assertNotNull(frame.params!!["timestamp"])
        assertTrue((frame.params["timestamp"] as JsonPrimitive).long > 0)
    }
}