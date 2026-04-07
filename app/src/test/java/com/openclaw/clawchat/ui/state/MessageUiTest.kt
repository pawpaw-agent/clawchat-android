package com.openclaw.clawchat.ui.state

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.*
import org.junit.Test

/**
 * MessageUi and MessageContentItem tests
 */
class MessageUiTest {

    // ─────────────────────────────────────────────────────────────
    // MessageUi Tests
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `MessageUi creates with required fields`() {
        val message = MessageUi(
            id = "msg-1",
            content = listOf(MessageContentItem.Text("Hello")),
            role = MessageRole.USER,
            timestamp = 1000L
        )

        assertEquals("msg-1", message.id)
        assertEquals(1, message.content.size)
        assertEquals(MessageRole.USER, message.role)
        assertEquals(1000L, message.timestamp)
        assertFalse(message.isLoading)
        assertNull(message.toolCallId)
        assertNull(message.toolName)
        assertEquals(MessageStatus.SENT, message.status)
    }

    @Test
    fun `MessageUi creates with all fields`() {
        val message = MessageUi(
            id = "msg-2",
            content = listOf(
                MessageContentItem.Text("Text"),
                MessageContentItem.ToolCall(id = "tc-1", name = "read_file")
            ),
            role = MessageRole.ASSISTANT,
            timestamp = 2000L,
            isLoading = true,
            toolCallId = "tc-1",
            toolName = "read_file",
            status = MessageStatus.SENDING
        )

        assertEquals(2, message.content.size)
        assertTrue(message.isLoading)
        assertEquals("tc-1", message.toolCallId)
        assertEquals("read_file", message.toolName)
        assertEquals(MessageStatus.SENDING, message.status)
    }

    @Test
    fun `MessageUi getTextContent returns text items`() {
        val message = MessageUi(
            id = "msg-1",
            content = listOf(
                MessageContentItem.Text("Line 1"),
                MessageContentItem.ToolCall(name = "test"),
                MessageContentItem.Text("Line 2")
            ),
            role = MessageRole.ASSISTANT,
            timestamp = 0
        )

        assertEquals("Line 1\nLine 2", message.getTextContent())
    }

    @Test
    fun `MessageUi getTextContent returns empty when no text`() {
        val message = MessageUi(
            id = "msg-1",
            content = listOf(MessageContentItem.ToolCall(name = "test")),
            role = MessageRole.ASSISTANT,
            timestamp = 0
        )

        assertEquals("", message.getTextContent())
    }

    @Test
    fun `MessageUi getToolCalls returns tool call items`() {
        val message = MessageUi(
            id = "msg-1",
            content = listOf(
                MessageContentItem.Text("Hello"),
                MessageContentItem.ToolCall(id = "tc-1", name = "exec"),
                MessageContentItem.ToolCall(id = "tc-2", name = "read")
            ),
            role = MessageRole.ASSISTANT,
            timestamp = 0
        )

        val toolCalls = message.getToolCalls()
        assertEquals(2, toolCalls.size)
        assertEquals("tc-1", toolCalls[0].id)
        assertEquals("exec", toolCalls[0].name)
    }

    @Test
    fun `MessageUi getToolCalls returns empty when no tool calls`() {
        val message = MessageUi(
            id = "msg-1",
            content = listOf(MessageContentItem.Text("Hello")),
            role = MessageRole.USER,
            timestamp = 0
        )

        assertTrue(message.getToolCalls().isEmpty())
    }

    @Test
    fun `MessageUi getToolResults returns tool result items`() {
        val message = MessageUi(
            id = "msg-1",
            content = listOf(
                MessageContentItem.ToolResult(toolCallId = "tc-1", text = "Result 1"),
                MessageContentItem.ToolResult(toolCallId = "tc-2", text = "Result 2", isError = true)
            ),
            role = MessageRole.TOOL,
            timestamp = 0
        )

        val results = message.getToolResults()
        assertEquals(2, results.size)
        assertEquals("tc-1", results[0].toolCallId)
        assertFalse(results[0].isError)
        assertTrue(results[1].isError)
    }

    @Test
    fun `MessageUi getToolResults creates from TOOL role with toolCallId`() {
        val message = MessageUi(
            id = "msg-1",
            content = listOf(MessageContentItem.Text("Tool output")),
            role = MessageRole.TOOL,
            timestamp = 0,
            toolCallId = "tc-123",
            toolName = "read_file"
        )

        val results = message.getToolResults()
        assertEquals(1, results.size)
        assertEquals("tc-123", results[0].toolCallId)
        assertEquals("read_file", results[0].name)
        assertEquals("Tool output", results[0].text)
    }

    @Test
    fun `MessageUi hasToolContent returns true for tool calls`() {
        val message = MessageUi(
            id = "msg-1",
            content = listOf(
                MessageContentItem.Text("Text"),
                MessageContentItem.ToolCall(name = "test")
            ),
            role = MessageRole.ASSISTANT,
            timestamp = 0
        )

        assertTrue(message.hasToolContent())
    }

    @Test
    fun `MessageUi hasToolContent returns true for tool results`() {
        val message = MessageUi(
            id = "msg-1",
            content = listOf(
                MessageContentItem.ToolResult(toolCallId = "tc-1", text = "Result")
            ),
            role = MessageRole.TOOL,
            timestamp = 0
        )

        assertTrue(message.hasToolContent())
    }

    @Test
    fun `MessageUi hasToolContent returns false for pure text`() {
        val message = MessageUi(
            id = "msg-1",
            content = listOf(MessageContentItem.Text("Hello world")),
            role = MessageRole.USER,
            timestamp = 0
        )

        assertFalse(message.hasToolContent())
    }

    @Test
    fun `MessageUi copy preserves values`() {
        val original = MessageUi(
            id = "msg-1",
            content = listOf(MessageContentItem.Text("Original")),
            role = MessageRole.USER,
            timestamp = 0
        )

        val copied = original.copy(
            content = listOf(MessageContentItem.Text("Updated")),
            status = MessageStatus.DELIVERED
        )

        assertEquals("msg-1", copied.id)
        assertEquals("Updated", copied.getTextContent())
        assertEquals(MessageStatus.DELIVERED, copied.status)
    }

    // ─────────────────────────────────────────────────────────────
    // MessageContentItem Tests
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `MessageContentItem Text creates correctly`() {
        val text = MessageContentItem.Text("Hello world")

        assertEquals("Hello world", text.text)
    }

    @Test
    fun `MessageContentItem ToolCall creates with required fields`() {
        val toolCall = MessageContentItem.ToolCall(name = "read_file")

        assertEquals("read_file", toolCall.name)
        assertNull(toolCall.id)
        assertNull(toolCall.args)
        assertEquals("start", toolCall.phase)
    }

    @Test
    fun `MessageContentItem ToolCall creates with all fields`() {
        val args = JsonObject(mapOf("path" to JsonPrimitive("/test/file.txt")))
        val toolCall = MessageContentItem.ToolCall(
            id = "tc-1",
            name = "exec",
            args = args,
            phase = "result"
        )

        assertEquals("tc-1", toolCall.id)
        assertEquals("exec", toolCall.name)
        assertEquals(args, toolCall.args)
        assertEquals("result", toolCall.phase)
    }

    @Test
    fun `MessageContentItem ToolResult creates with required fields`() {
        val result = MessageContentItem.ToolResult(text = "Success")

        assertEquals("Success", result.text)
        assertNull(result.toolCallId)
        assertNull(result.name)
        assertNull(result.args)
        assertFalse(result.isError)
    }

    @Test
    fun `MessageContentItem ToolResult creates with all fields`() {
        val args = JsonObject(mapOf("cmd" to JsonPrimitive("ls")))
        val result = MessageContentItem.ToolResult(
            toolCallId = "tc-1",
            name = "exec",
            args = args,
            text = "file1\nfile2",
            isError = true
        )

        assertEquals("tc-1", result.toolCallId)
        assertEquals("exec", result.name)
        assertEquals(args, result.args)
        assertEquals("file1\nfile2", result.text)
        assertTrue(result.isError)
    }

    @Test
    fun `MessageContentItem Image creates with url`() {
        val image = MessageContentItem.Image(url = "https://example.com/image.png")

        assertEquals("https://example.com/image.png", image.url)
        assertNull(image.base64)
        assertNull(image.mimeType)
    }

    @Test
    fun `MessageContentItem Image creates with base64`() {
        val image = MessageContentItem.Image(
            base64 = "ABC123",
            mimeType = "image/png"
        )

        assertNull(image.url)
        assertEquals("ABC123", image.base64)
        assertEquals("image/png", image.mimeType)
    }

    @Test
    fun `MessageContentItem sealed class polymorphism`() {
        val items: List<MessageContentItem> = listOf(
            MessageContentItem.Text("Text"),
            MessageContentItem.ToolCall(name = "test"),
            MessageContentItem.ToolResult(text = "Result"),
            MessageContentItem.Image(url = "url")
        )

        assertTrue(items[0] is MessageContentItem.Text)
        assertTrue(items[1] is MessageContentItem.ToolCall)
        assertTrue(items[2] is MessageContentItem.ToolResult)
        assertTrue(items[3] is MessageContentItem.Image)
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
    // MessageRole Tests
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `MessageRole enum values`() {
        assertEquals(4, MessageRole.entries.size)
        assertEquals(MessageRole.USER, MessageRole.valueOf("USER"))
        assertEquals(MessageRole.ASSISTANT, MessageRole.valueOf("ASSISTANT"))
        assertEquals(MessageRole.SYSTEM, MessageRole.valueOf("SYSTEM"))
        assertEquals(MessageRole.TOOL, MessageRole.valueOf("TOOL"))
    }

    @Test
    fun `MessageRole fromString returns correct values`() {
        assertEquals(MessageRole.USER, MessageRole.fromString("user"))
        assertEquals(MessageRole.USER, MessageRole.fromString("USER"))
        assertEquals(MessageRole.ASSISTANT, MessageRole.fromString("assistant"))
        assertEquals(MessageRole.ASSISTANT, MessageRole.fromString("ASSISTANT"))
        assertEquals(MessageRole.SYSTEM, MessageRole.fromString("system"))
        assertEquals(MessageRole.TOOL, MessageRole.fromString("tool"))
    }

    @Test
    fun `MessageRole fromString handles tool variants`() {
        assertEquals(MessageRole.TOOL, MessageRole.fromString("toolresult"))
        assertEquals(MessageRole.TOOL, MessageRole.fromString("tool-result"))
    }

    @Test
    fun `MessageRole fromString defaults to ASSISTANT`() {
        assertEquals(MessageRole.ASSISTANT, MessageRole.fromString("unknown"))
        assertEquals(MessageRole.ASSISTANT, MessageRole.fromString("random"))
    }

    // ─────────────────────────────────────────────────────────────
    // ToolStreamEntry Tests
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `ToolStreamEntry creates with required fields`() {
        val entry = ToolStreamEntry(
            toolCallId = "tc-1",
            runId = "run-1",
            startedAt = 1000L,
            updatedAt = 2000L
        )

        assertEquals("tc-1", entry.toolCallId)
        assertEquals("run-1", entry.runId)
        assertNull(entry.sessionKey)
        assertEquals("tool", entry.name)
        assertNull(entry.args)
        assertNull(entry.output)
        assertEquals("start", entry.phase)
        assertFalse(entry.isError)
        assertEquals(1000L, entry.startedAt)
        assertEquals(2000L, entry.updatedAt)
    }

    @Test
    fun `ToolStreamEntry creates with all fields`() {
        val args = JsonObject(mapOf("path" to JsonPrimitive("/test")))
        val entry = ToolStreamEntry(
            toolCallId = "tc-2",
            runId = "run-2",
            sessionKey = "session-1",
            name = "read_file",
            args = args,
            output = "file content",
            phase = "result",
            isError = false,
            startedAt = 1000L,
            updatedAt = 3000L
        )

        assertEquals("tc-2", entry.toolCallId)
        assertEquals("run-2", entry.runId)
        assertEquals("session-1", entry.sessionKey)
        assertEquals("read_file", entry.name)
        assertEquals(args, entry.args)
        assertEquals("file content", entry.output)
        assertEquals("result", entry.phase)
        assertFalse(entry.isError)
    }

    @Test
    fun `ToolStreamEntry buildMessage creates correct MessageUi`() {
        val entry = ToolStreamEntry(
            toolCallId = "tc-1",
            runId = "run-1",
            name = "exec",
            output = "success",
            phase = "result",
            startedAt = 1000L,
            updatedAt = 2000L
        )

        val message = entry.buildMessage()

        assertEquals("tool:tc-1", message.id)
        assertEquals(MessageRole.ASSISTANT, message.role)
        assertEquals(1000L, message.timestamp)
        assertFalse(message.isLoading)  // phase=result means completed
        assertTrue(message.hasToolContent())
    }

    @Test
    fun `ToolStreamEntry buildMessage isLoading when phase is not result`() {
        val entry = ToolStreamEntry(
            toolCallId = "tc-1",
            runId = "run-1",
            phase = "start",
            startedAt = 1000L,
            updatedAt = 1000L
        )

        val message = entry.buildMessage()

        assertTrue(message.isLoading)
    }

    // ─────────────────────────────────────────────────────────────
    // PairingStatus Tests
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `PairingStatus sealed class values`() {
        val statuses: List<PairingStatus> = listOf(
            PairingStatus.Initializing,
            PairingStatus.WaitingForApproval,
            PairingStatus.Approved,
            PairingStatus.Rejected,
            PairingStatus.Timeout,
            PairingStatus.Error("test")
        )

        assertEquals(6, statuses.size)
        assertTrue(statuses[0] is PairingStatus.Initializing)
        assertTrue(statuses[1] is PairingStatus.WaitingForApproval)
        assertTrue(statuses[2] is PairingStatus.Approved)
        assertTrue(statuses[3] is PairingStatus.Rejected)
        assertTrue(statuses[4] is PairingStatus.Timeout)
        assertTrue(statuses[5] is PairingStatus.Error)
    }

    @Test
    fun `PairingStatus Error stores message`() {
        val error = PairingStatus.Error("Connection failed")

        assertEquals("Connection failed", error.message)
    }

    // ─────────────────────────────────────────────────────────────
    // GatewayConfigInput Tests
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `GatewayConfigInput creates with default values`() {
        val input = GatewayConfigInput()

        assertEquals("", input.name)
        assertEquals("", input.host)
        assertEquals(18789, input.port)
    }

    @Test
    fun `GatewayConfigInput creates with custom values`() {
        val input = GatewayConfigInput(
            name = "Local Gateway",
            host = "192.168.1.1",
            port = 8080
        )

        assertEquals("Local Gateway", input.name)
        assertEquals("192.168.1.1", input.host)
        assertEquals(8080, input.port)
    }
}