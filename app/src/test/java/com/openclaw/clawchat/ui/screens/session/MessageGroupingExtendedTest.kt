package com.openclaw.clawchat.ui.screens.session

import com.openclaw.clawchat.ui.state.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.*
import org.junit.Test

/**
 * MessageGrouping 补充测试
 */
class MessageGroupingExtendedTest {

    // ─────────────────────────────────────────────────────────────
    // formatMessageAsMarkdown 测试
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `formatMessageAsMarkdown formats user message correctly`() {
        val message = MessageUi(
            id = "msg-1",
            role = MessageRole.USER,
            content = listOf(MessageContentItem.Text("Hello world")),
            timestamp = System.currentTimeMillis()
        )

        val markdown = formatMessageAsMarkdown(message)

        assertTrue(markdown.contains("**User:**"))
        assertTrue(markdown.contains("Hello world"))
    }

    @Test
    fun `formatMessageAsMarkdown formats assistant message correctly`() {
        val message = MessageUi(
            id = "msg-2",
            role = MessageRole.ASSISTANT,
            content = listOf(MessageContentItem.Text("Response text")),
            timestamp = System.currentTimeMillis()
        )

        val markdown = formatMessageAsMarkdown(message)

        assertTrue(markdown.contains("**Assistant:**"))
        assertTrue(markdown.contains("Response text"))
    }

    @Test
    fun `formatMessageAsMarkdown formats system message correctly`() {
        val message = MessageUi(
            id = "msg-3",
            role = MessageRole.SYSTEM,
            content = listOf(MessageContentItem.Text("System instruction")),
            timestamp = System.currentTimeMillis()
        )

        val markdown = formatMessageAsMarkdown(message)

        assertTrue(markdown.contains("**System:**"))
        assertTrue(markdown.contains("System instruction"))
    }

    @Test
    fun `formatMessageAsMarkdown formats tool message correctly`() {
        val message = MessageUi(
            id = "msg-4",
            role = MessageRole.TOOL,
            content = listOf(MessageContentItem.Text("Tool output")),
            timestamp = System.currentTimeMillis()
        )

        val markdown = formatMessageAsMarkdown(message)

        assertTrue(markdown.contains("**Tool:**"))
        assertTrue(markdown.contains("Tool output"))
    }

    @Test
    fun `formatMessageAsMarkdown formats tool call correctly`() {
        val message = MessageUi(
            id = "msg-5",
            role = MessageRole.ASSISTANT,
            content = listOf(
                MessageContentItem.ToolCall(
                    id = "call-1",
                    name = "read_file",
                    args = JsonObject(mapOf("path" to JsonPrimitive("/test.txt"))),
                    phase = "call"
                )
            ),
            timestamp = System.currentTimeMillis()
        )

        val markdown = formatMessageAsMarkdown(message)

        assertTrue(markdown.contains("**Assistant:**"))
        assertTrue(markdown.contains("Tool: read_file"))
        assertTrue(markdown.contains("```json"))
    }

    @Test
    fun `formatMessageAsMarkdown formats tool result correctly`() {
        val message = MessageUi(
            id = "msg-6",
            role = MessageRole.ASSISTANT,
            content = listOf(
                MessageContentItem.ToolResult(
                    toolCallId = "call-1",
                    name = "read_file",
                    text = "File content here"
                )
            ),
            timestamp = System.currentTimeMillis()
        )

        val markdown = formatMessageAsMarkdown(message)

        assertTrue(markdown.contains("File content here"))
        assertTrue(markdown.contains("```"))
    }

    @Test
    fun `formatMessageAsMarkdown formats multiple content items`() {
        val message = MessageUi(
            id = "msg-7",
            role = MessageRole.ASSISTANT,
            content = listOf(
                MessageContentItem.Text("First text"),
                MessageContentItem.Text("Second text")
            ),
            timestamp = System.currentTimeMillis()
        )

        val markdown = formatMessageAsMarkdown(message)

        assertTrue(markdown.contains("First text"))
        assertTrue(markdown.contains("Second text"))
    }

    // ─────────────────────────────────────────────────────────────
    // pairToolCards 边界条件测试
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `pairToolCards handles exec command extraction`() {
        val message = MessageUi(
            id = "msg-exec",
            role = MessageRole.ASSISTANT,
            content = listOf(
                MessageContentItem.ToolCall(
                    id = "call-exec",
                    name = "exec",
                    args = JsonObject(mapOf("command" to JsonPrimitive("ls -la"))),
                    phase = "call"
                )
            ),
            timestamp = System.currentTimeMillis()
        )

        val toolCards = pairToolCards(message)

        assertEquals(1, toolCards.size)
        assertEquals("exec", toolCards[0].name)
        // exec 的 args 应提取 command 字段
        assertTrue(toolCards[0].args?.contains("ls") ?: false)
    }

    @Test
    fun `pairToolCards handles tool call without args`() {
        val message = MessageUi(
            id = "msg-noargs",
            role = MessageRole.ASSISTANT,
            content = listOf(
                MessageContentItem.ToolCall(
                    id = "call-noargs",
                    name = "simple_tool",
                    args = null,
                    phase = "call"
                )
            ),
            timestamp = System.currentTimeMillis()
        )

        val toolCards = pairToolCards(message)

        assertEquals(1, toolCards.size)
        assertEquals("simple_tool", toolCards[0].name)
        assertNull(toolCards[0].args)
    }

    @Test
    fun `pairToolCards handles tool result with error`() {
        val message = MessageUi(
            id = "msg-error",
            role = MessageRole.ASSISTANT,
            content = listOf(
                MessageContentItem.ToolCall(
                    id = "call-error",
                    name = "failing_tool",
                    phase = "result"
                ),
                MessageContentItem.ToolResult(
                    toolCallId = "call-error",
                    name = "failing_tool",
                    text = "Error: File not found",
                    isError = true
                )
            ),
            timestamp = System.currentTimeMillis()
        )

        val toolCards = pairToolCards(message)

        assertEquals(1, toolCards.size)
        assertTrue(toolCards[0].isError)
        assertEquals("Error: File not found", toolCards[0].result)
    }

    @Test
    fun `pairToolCards handles partial tool call (running)`() {
        val message = MessageUi(
            id = "msg-running",
            role = MessageRole.ASSISTANT,
            content = listOf(
                MessageContentItem.ToolCall(
                    id = "call-running",
                    name = "long_running_tool",
                    args = JsonObject(mapOf("param" to JsonPrimitive("value"))),
                    phase = "call"
                )
            ),
            timestamp = System.currentTimeMillis()
        )

        val toolCards = pairToolCards(message)

        assertEquals(1, toolCards.size)
        assertEquals(ToolCardKind.CALL, toolCards[0].kind)
    }

    @Test
    fun `pairToolCards handles mixed tool calls and text`() {
        val message = MessageUi(
            id = "msg-mixed",
            role = MessageRole.ASSISTANT,
            content = listOf(
                MessageContentItem.Text("Let me check the file."),
                MessageContentItem.ToolCall(
                    id = "call-1",
                    name = "read_file",
                    args = JsonObject(mapOf("path" to JsonPrimitive("/file.txt"))),
                    phase = "result"
                ),
                MessageContentItem.ToolResult(
                    toolCallId = "call-1",
                    name = "read_file",
                    text = "File contents"
                )
            ),
            timestamp = System.currentTimeMillis()
        )

        val toolCards = pairToolCards(message)

        assertEquals(1, toolCards.size)
        assertEquals(ToolCardKind.RESULT, toolCards[0].kind)
    }

    @Test
    fun `pairToolCards handles empty tool result list`() {
        val message = MessageUi(
            id = "msg-empty",
            role = MessageRole.ASSISTANT,
            content = listOf(
                MessageContentItem.ToolCall(
                    id = "call-empty",
                    name = "test_tool",
                    phase = "result"
                )
            ),
            timestamp = System.currentTimeMillis()
        )

        // 没有提供 group messages
        val toolCards = pairToolCards(message, emptyList())

        assertEquals(1, toolCards.size)
        // phase=result，即使没有 result 内容也应该显示 RESULT
        assertEquals(ToolCardKind.RESULT, toolCards[0].kind)
        assertNull(toolCards[0].result)
    }
}