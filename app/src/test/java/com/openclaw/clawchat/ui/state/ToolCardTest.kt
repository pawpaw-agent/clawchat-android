package com.openclaw.clawchat.ui.state

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.*
import org.junit.Test

/**
 * ToolCard and related data class tests
 */
class ToolCardTest {

    // ─────────────────────────────────────────────────────────────
    // ToolCard Tests
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `ToolCard creates with required fields`() {
        val card = ToolCard(
            kind = ToolCardKind.CALL,
            name = "read_file"
        )

        assertEquals(ToolCardKind.CALL, card.kind)
        assertEquals("read_file", card.name)
        assertNull(card.args)
        assertNull(card.result)
        assertFalse(card.isError)
        assertNull(card.callId)
        assertEquals("start", card.phase)
    }

    @Test
    fun `ToolCard creates with all fields`() {
        val card = ToolCard(
            kind = ToolCardKind.RESULT,
            name = "exec",
            args = "ls -la",
            result = "file1.txt\nfile2.txt",
            isError = false,
            callId = "call-123",
            phase = "result"
        )

        assertEquals(ToolCardKind.RESULT, card.kind)
        assertEquals("exec", card.name)
        assertEquals("ls -la", card.args)
        assertEquals("file1.txt\nfile2.txt", card.result)
        assertFalse(card.isError)
        assertEquals("call-123", card.callId)
        assertEquals("result", card.phase)
    }

    @Test
    fun `ToolCard isError works correctly`() {
        val successCard = ToolCard(
            kind = ToolCardKind.RESULT,
            name = "test",
            isError = false
        )
        val errorCard = ToolCard(
            kind = ToolCardKind.RESULT,
            name = "test",
            isError = true
        )

        assertFalse(successCard.isError)
        assertTrue(errorCard.isError)
    }

    @Test
    fun `ToolCardKind enum values`() {
        assertEquals(2, ToolCardKind.entries.size)
        assertEquals(ToolCardKind.CALL, ToolCardKind.valueOf("CALL"))
        assertEquals(ToolCardKind.RESULT, ToolCardKind.valueOf("RESULT"))
    }

    // ─────────────────────────────────────────────────────────────
    // StreamSegment Tests
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `StreamSegment creates correctly`() {
        val now = System.currentTimeMillis()
        val segment = StreamSegment(text = "Hello world", ts = now)

        assertEquals("Hello world", segment.text)
        assertEquals(now, segment.ts)
    }

    @Test
    fun `StreamSegment equality works`() {
        val now = System.currentTimeMillis()
        val segment1 = StreamSegment(text = "Test", ts = now)
        val segment2 = StreamSegment(text = "Test", ts = now)
        val segment3 = StreamSegment(text = "Different", ts = now)

        assertEquals(segment1, segment2)
        assertNotEquals(segment1, segment3)
    }

    // ─────────────────────────────────────────────────────────────
    // MessageStatus Tests
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `MessageStatus enum values`() {
        assertEquals(4, MessageStatus.entries.size)
        assertEquals(MessageStatus.SENDING, MessageStatus.valueOf("SENDING"))
        assertEquals(MessageStatus.SENT, MessageStatus.valueOf("SENT"))
        assertEquals(MessageStatus.DELIVERED, MessageStatus.valueOf("DELIVERED"))
        assertEquals(MessageStatus.FAILED, MessageStatus.valueOf("FAILED"))
    }

    // ─────────────────────────────────────────────────────────────
    // SessionStatus Tests
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `SessionStatus enum values`() {
        assertEquals(3, SessionStatus.entries.size)
        assertEquals(SessionStatus.RUNNING, SessionStatus.valueOf("RUNNING"))
        assertEquals(SessionStatus.PAUSED, SessionStatus.valueOf("PAUSED"))
        assertEquals(SessionStatus.TERMINATED, SessionStatus.valueOf("TERMINATED"))
    }

    // ─────────────────────────────────────────────────────────────
    // MessageRole Tests
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `MessageRole enum values`() {
        assertEquals(4, MessageRole.entries.size)
    }

    @Test
    fun `MessageRole fromString works correctly`() {
        assertEquals(MessageRole.USER, MessageRole.fromString("user"))
        assertEquals(MessageRole.USER, MessageRole.fromString("USER"))
        assertEquals(MessageRole.ASSISTANT, MessageRole.fromString("assistant"))
        assertEquals(MessageRole.ASSISTANT, MessageRole.fromString("ASSISTANT"))
        assertEquals(MessageRole.SYSTEM, MessageRole.fromString("system"))
        assertEquals(MessageRole.TOOL, MessageRole.fromString("tool"))
        assertEquals(MessageRole.TOOL, MessageRole.fromString("toolresult"))
        assertEquals(MessageRole.TOOL, MessageRole.fromString("tool-result"))
    }

    @Test
    fun `MessageRole fromString defaults to ASSISTANT`() {
        assertEquals(MessageRole.ASSISTANT, MessageRole.fromString("unknown"))
        assertEquals(MessageRole.ASSISTANT, MessageRole.fromString("random"))
    }

    // ─────────────────────────────────────────────────────────────
    // ConnectionStatus Tests
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `ConnectionStatus isConnected works correctly`() {
        assertFalse(ConnectionStatus.Disconnected.isConnected)
        assertFalse(ConnectionStatus.Connecting.isConnected)
        assertTrue(ConnectionStatus.Connected().isConnected)
        assertFalse(ConnectionStatus.Disconnecting.isConnected)
        assertFalse(ConnectionStatus.Error("test").isConnected)
    }

    @Test
    fun `ConnectionStatus isConnecting works correctly`() {
        assertFalse(ConnectionStatus.Disconnected.isConnecting)
        assertTrue(ConnectionStatus.Connecting.isConnecting)
        assertFalse(ConnectionStatus.Connected().isConnecting)
        assertTrue(ConnectionStatus.Disconnecting.isConnecting)
        assertFalse(ConnectionStatus.Error("test").isConnecting)
    }

    @Test
    fun `ConnectionStatus Connected stores latency`() {
        val connected = ConnectionStatus.Connected(latency = 100)
        assertEquals(100, connected.latency)

        val noLatency = ConnectionStatus.Connected()
        assertNull(noLatency.latency)
    }

    @Test
    fun `ConnectionStatus Error stores message`() {
        val error = ConnectionStatus.Error("Connection failed")
        assertEquals("Connection failed", error.message)
    }
}