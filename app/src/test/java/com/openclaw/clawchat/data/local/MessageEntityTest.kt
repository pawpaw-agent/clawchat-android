package com.openclaw.clawchat.data.local

import org.junit.Assert.*
import org.junit.Test

/**
 * MessageEntity, SessionEntity, and PendingMessageEntity tests
 */
class MessageEntityTest {

    // ─────────────────────────────────────────────────────────────
    // MessageEntity Tests
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `MessageEntity creates with required fields`() {
        val entity = MessageEntity(
            id = "msg-1",
            sessionId = "session-1",
            role = "user",
            content = "{\"text\":\"Hello\"}",
            timestamp = 1000L
        )

        assertEquals("msg-1", entity.id)
        assertEquals("session-1", entity.sessionId)
        assertEquals("user", entity.role)
        assertEquals("{\"text\":\"Hello\"}", entity.content)
        assertEquals(1000L, entity.timestamp)
        assertNull(entity.toolCallId)
        assertNull(entity.toolName)
        assertEquals("SENT", entity.status)
        assertTrue(entity.createdAt > 0)
    }

    @Test
    fun `MessageEntity creates with all fields`() {
        val entity = MessageEntity(
            id = "msg-2",
            sessionId = "session-1",
            role = "assistant",
            content = "Response content",
            timestamp = 2000L,
            toolCallId = "tc-1",
            toolName = "read_file",
            status = "DELIVERED",
            createdAt = 1500L
        )

        assertEquals("tc-1", entity.toolCallId)
        assertEquals("read_file", entity.toolName)
        assertEquals("DELIVERED", entity.status)
        assertEquals(1500L, entity.createdAt)
    }

    @Test
    fun `MessageEntity supports different roles`() {
        val userMessage = MessageEntity(
            id = "msg-user",
            sessionId = "s1",
            role = "user",
            content = "User message",
            timestamp = 0
        )

        val assistantMessage = MessageEntity(
            id = "msg-assistant",
            sessionId = "s1",
            role = "assistant",
            content = "Assistant message",
            timestamp = 0
        )

        val systemMessage = MessageEntity(
            id = "msg-system",
            sessionId = "s1",
            role = "system",
            content = "System message",
            timestamp = 0
        )

        val toolMessage = MessageEntity(
            id = "msg-tool",
            sessionId = "s1",
            role = "tool",
            content = "Tool result",
            timestamp = 0
        )

        assertEquals("user", userMessage.role)
        assertEquals("assistant", assistantMessage.role)
        assertEquals("system", systemMessage.role)
        assertEquals("tool", toolMessage.role)
    }

    @Test
    fun `MessageEntity supports different statuses`() {
        val sending = MessageEntity("m1", "s1", "user", "text", 0, status = "SENDING")
        val sent = MessageEntity("m2", "s1", "user", "text", 0, status = "SENT")
        val delivered = MessageEntity("m3", "s1", "user", "text", 0, status = "DELIVERED")
        val failed = MessageEntity("m4", "s1", "user", "text", 0, status = "FAILED")

        assertEquals("SENDING", sending.status)
        assertEquals("SENT", sent.status)
        assertEquals("DELIVERED", delivered.status)
        assertEquals("FAILED", failed.status)
    }

    @Test
    fun `MessageEntity copy preserves values`() {
        val original = MessageEntity(
            id = "msg-1",
            sessionId = "session-1",
            role = "user",
            content = "Original",
            timestamp = 1000L
        )

        val copied = original.copy(content = "Updated", status = "DELIVERED")

        assertEquals("msg-1", copied.id)
        assertEquals("session-1", copied.sessionId)
        assertEquals("Updated", copied.content)
        assertEquals("DELIVERED", copied.status)
    }

    @Test
    fun `MessageEntity equality works`() {
        val entity1 = MessageEntity("m1", "s1", "user", "text", 1000L)
        val entity2 = MessageEntity("m1", "s1", "user", "text", 1000L)
        val entity3 = MessageEntity("m2", "s1", "user", "text", 1000L)

        assertEquals(entity1, entity2)
        assertNotEquals(entity1, entity3)
    }

    // ─────────────────────────────────────────────────────────────
    // SessionEntity Tests
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `SessionEntity creates with required fields`() {
        val entity = SessionEntity(
            id = "session-1",
            lastActivityAt = 1000L
        )

        assertEquals("session-1", entity.id)
        assertNull(entity.label)
        assertNull(entity.model)
        assertNull(entity.agentId)
        assertNull(entity.agentName)
        assertNull(entity.agentEmoji)
        assertEquals("RUNNING", entity.status)
        assertEquals(1000L, entity.lastActivityAt)
        assertEquals(0, entity.messageCount)
        assertNull(entity.lastMessage)
        assertFalse(entity.thinking)
        assertFalse(entity.isPinned)
        assertFalse(entity.isArchived)
        assertTrue(entity.createdAt > 0)
        assertTrue(entity.updatedAt > 0)
    }

    @Test
    fun `SessionEntity creates with all fields`() {
        val entity = SessionEntity(
            id = "session-2",
            label = "My Session",
            model = "claude-3",
            agentId = "agent:coder",
            agentName = "Coder Agent",
            agentEmoji = "🤖",
            status = "PAUSED",
            lastActivityAt = 2000L,
            messageCount = 10,
            lastMessage = "Hello",
            thinking = true,
            isPinned = true,
            isArchived = false,
            createdAt = 1000L,
            updatedAt = 2000L
        )

        assertEquals("My Session", entity.label)
        assertEquals("claude-3", entity.model)
        assertEquals("agent:coder", entity.agentId)
        assertEquals("Coder Agent", entity.agentName)
        assertEquals("🤖", entity.agentEmoji)
        assertEquals("PAUSED", entity.status)
        assertEquals(10, entity.messageCount)
        assertEquals("Hello", entity.lastMessage)
        assertTrue(entity.thinking)
        assertTrue(entity.isPinned)
    }

    @Test
    fun `SessionEntity supports different statuses`() {
        val running = SessionEntity(id = "s1", lastActivityAt = 0, status = "RUNNING")
        val paused = SessionEntity(id = "s2", lastActivityAt = 0, status = "PAUSED")
        val terminated = SessionEntity(id = "s3", lastActivityAt = 0, status = "TERMINATED")

        assertEquals("RUNNING", running.status)
        assertEquals("PAUSED", paused.status)
        assertEquals("TERMINATED", terminated.status)
    }

    @Test
    fun `SessionEntity with agent info`() {
        val entity = SessionEntity(
            id = "session-agent",
            agentId = "agent:coder:123",
            agentName = "Coder",
            agentEmoji = "💻",
            lastActivityAt = 0
        )

        assertEquals("agent:coder:123", entity.agentId)
        assertEquals("Coder", entity.agentName)
        assertEquals("💻", entity.agentEmoji)
    }

    @Test
    fun `SessionEntity pinned and archived flags`() {
        val pinned = SessionEntity(id = "s1", lastActivityAt = 0, isPinned = true)
        val archived = SessionEntity(id = "s2", lastActivityAt = 0, isArchived = true)
        val both = SessionEntity(id = "s3", lastActivityAt = 0, isPinned = true, isArchived = true)

        assertTrue(pinned.isPinned)
        assertFalse(pinned.isArchived)
        assertTrue(archived.isArchived)
        assertFalse(archived.isPinned)
        assertTrue(both.isPinned)
        assertTrue(both.isArchived)
    }

    @Test
    fun `SessionEntity copy preserves values`() {
        val original = SessionEntity(
            id = "session-1",
            label = "Original",
            lastActivityAt = 1000L
        )

        val copied = original.copy(
            label = "Updated",
            messageCount = 5,
            isPinned = true
        )

        assertEquals("session-1", copied.id)
        assertEquals("Updated", copied.label)
        assertEquals(5, copied.messageCount)
        assertTrue(copied.isPinned)
    }

    @Test
    fun `SessionEntity equality works`() {
        val entity1 = SessionEntity(id = "s1", lastActivityAt = 1000L)
        val entity2 = SessionEntity(id = "s1", lastActivityAt = 1000L)
        val entity3 = SessionEntity(id = "s2", lastActivityAt = 1000L)

        assertEquals(entity1, entity2)
        assertNotEquals(entity1, entity3)
    }

    // ─────────────────────────────────────────────────────────────
    // PendingMessageEntity Tests
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `PendingMessageEntity creates with required fields`() {
        val entity = PendingMessageEntity(
            sessionId = "session-1",
            text = "Hello world"
        )

        assertEquals(0L, entity.id)  // auto-generated
        assertEquals("session-1", entity.sessionId)
        assertEquals("Hello world", entity.text)
        assertNull(entity.attachments)
        assertTrue(entity.createdAt > 0)
        assertEquals(0, entity.retryCount)
        assertNull(entity.lastError)
    }

    @Test
    fun `PendingMessageEntity creates with all fields`() {
        val entity = PendingMessageEntity(
            id = 123L,
            sessionId = "session-2",
            text = "Message with attachment",
            attachments = "[{\"uri\":\"file://test\"}]",
            createdAt = 1000L,
            retryCount = 3,
            lastError = "Network timeout"
        )

        assertEquals(123L, entity.id)
        assertEquals("session-2", entity.sessionId)
        assertEquals("Message with attachment", entity.text)
        assertEquals("[{\"uri\":\"file://test\"}]", entity.attachments)
        assertEquals(1000L, entity.createdAt)
        assertEquals(3, entity.retryCount)
        assertEquals("Network timeout", entity.lastError)
    }

    @Test
    fun `PendingMessageEntity with retry info`() {
        val entity = PendingMessageEntity(
            sessionId = "session-1",
            text = "Retrying message",
            retryCount = 2,
            lastError = "Connection failed"
        )

        assertEquals(2, entity.retryCount)
        assertEquals("Connection failed", entity.lastError)
    }

    @Test
    fun `PendingMessageEntity with attachments`() {
        val attachments = "[{\"id\":\"att-1\",\"name\":\"file.pdf\"}]"
        val entity = PendingMessageEntity(
            sessionId = "session-1",
            text = "Message",
            attachments = attachments
        )

        assertEquals(attachments, entity.attachments)
    }

    @Test
    fun `PendingMessageEntity copy preserves values`() {
        val original = PendingMessageEntity(
            sessionId = "session-1",
            text = "Original",
            retryCount = 0
        )

        val copied = original.copy(
            text = "Updated",
            retryCount = 1,
            lastError = "First error"
        )

        assertEquals("session-1", copied.sessionId)
        assertEquals("Updated", copied.text)
        assertEquals(1, copied.retryCount)
        assertEquals("First error", copied.lastError)
    }

    @Test
    fun `PendingMessageEntity equality works`() {
        val entity1 = PendingMessageEntity(id = 1L, sessionId = "s1", text = "test")
        val entity2 = PendingMessageEntity(id = 1L, sessionId = "s1", text = "test")
        val entity3 = PendingMessageEntity(id = 2L, sessionId = "s1", text = "test")

        assertEquals(entity1, entity2)
        assertNotEquals(entity1, entity3)
    }

    @Test
    fun `PendingMessageEntity autoGenerate id defaults to zero`() {
        val entity = PendingMessageEntity(sessionId = "s1", text = "test")

        assertEquals(0L, entity.id)
    }

    @Test
    fun `PendingMessageEntity retryCount tracks attempts`() {
        var entity = PendingMessageEntity(sessionId = "s1", text = "test")

        // Simulate retry increments
        entity = entity.copy(retryCount = 1)
        assertEquals(1, entity.retryCount)

        entity = entity.copy(retryCount = 2, lastError = "Timeout")
        assertEquals(2, entity.retryCount)
        assertEquals("Timeout", entity.lastError)

        entity = entity.copy(retryCount = 3, lastError = "Server error")
        assertEquals(3, entity.retryCount)
    }
}