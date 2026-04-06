package com.openclaw.clawchat.ui.screens.session

import com.openclaw.clawchat.ui.state.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.*
import org.junit.Test

/**
 * 消息分组和工具卡片配对测试
 */
class MessageGroupingTest {

    /**
     * 测试：用户消息不应产生工具卡片
     */
    @Test
    fun `user message should not produce tool cards`() {
        val userMessage = MessageUi(
            id = "msg-1",
            role = MessageRole.USER,
            content = listOf(MessageContentItem.Text("Hello, this is a test message")),
            timestamp = System.currentTimeMillis()
        )

        val toolCards = pairToolCards(userMessage)

        assertTrue("用户消息不应产生工具卡片", toolCards.isEmpty())
    }

    /**
 * 测试：助手纯文本消息不应产生工具卡片
     * 纯文本在消息气泡中显示，不需要工具卡片
     */
    @Test
    fun `assistant text message should not produce tool cards`() {
        val assistantMessage = MessageUi(
            id = "msg-2",
            role = MessageRole.ASSISTANT,
            content = listOf(MessageContentItem.Text("This is the assistant response")),
            timestamp = System.currentTimeMillis()
        )

        val toolCards = pairToolCards(assistantMessage)

        assertTrue("助手纯文本消息不应产生工具卡片", toolCards.isEmpty())
    }

    /**
     * 测试：助手空消息不应产生工具卡片
     */
    @Test
    fun `assistant empty message should not produce tool cards`() {
        val assistantMessage = MessageUi(
            id = "msg-3",
            role = MessageRole.ASSISTANT,
            content = listOf(MessageContentItem.Text("")),
            timestamp = System.currentTimeMillis()
        )

        val toolCards = pairToolCards(assistantMessage)

        assertTrue("助手空消息不应产生工具卡片", toolCards.isEmpty())
    }

    /**
     * 测试：带工具调用的消息应正确配对
     */
    @Test
    fun `message with tool calls should produce tool cards`() {
        val toolCallId = "call-1"
        val assistantMessage = MessageUi(
            id = "msg-4",
            role = MessageRole.ASSISTANT,
            content = listOf(
                MessageContentItem.Text("Let me check that."),
                MessageContentItem.ToolCall(
                    id = toolCallId,
                    name = "read_file",
                    args = JsonObject(mapOf("path" to JsonPrimitive("/test.txt"))),
                    phase = "call"
                )
            ),
            timestamp = System.currentTimeMillis()
        )

        val toolCards = pairToolCards(assistantMessage)

        assertEquals("应产生 1 个工具卡片", 1, toolCards.size)
        assertEquals("工具名应为 read_file", "read_file", toolCards[0].name)
        assertEquals("应处于调用状态", ToolCardKind.CALL, toolCards[0].kind)
        assertEquals("callId 应匹配", toolCallId, toolCards[0].callId)
    }

    /**
     * 测试：已完成工具调用应显示 RESULT 状态
     */
    @Test
    fun `completed tool call should show RESULT status`() {
        val toolCallId = "call-2"
        val assistantMessage = MessageUi(
            id = "msg-5",
            role = MessageRole.ASSISTANT,
            content = listOf(
                MessageContentItem.ToolCall(
                    id = toolCallId,
                    name = "read_file",
                    args = JsonObject(mapOf("path" to JsonPrimitive("/test.txt"))),
                    phase = "result"
                )
            ),
            timestamp = System.currentTimeMillis()
        )

        val toolCards = pairToolCards(assistantMessage)

        assertEquals("phase=result 应显示 RESULT 状态", ToolCardKind.RESULT, toolCards[0].kind)
    }

    /**
     * 测试：工具调用带结果应正确配对
     */
    @Test
    fun `tool call with result should be paired`() {
        val toolCallId = "call-3"
        val assistantMessage = MessageUi(
            id = "msg-6",
            role = MessageRole.ASSISTANT,
            content = listOf(
                MessageContentItem.ToolCall(
                    id = toolCallId,
                    name = "read_file",
                    args = JsonObject(mapOf("path" to JsonPrimitive("/test.txt"))),
                    phase = "result"
                ),
                MessageContentItem.ToolResult(
                    toolCallId = toolCallId,
                    name = "read_file",
                    text = "File content here"
                )
            ),
            timestamp = System.currentTimeMillis()
        )

        val toolCards = pairToolCards(assistantMessage)

        assertEquals("应产生 1 个工具卡片", 1, toolCards.size)
        assertEquals("应有结果", "File content here", toolCards[0].result)
        assertEquals("应显示 RESULT 状态", ToolCardKind.RESULT, toolCards[0].kind)
    }

    /**
     * 测试：多个工具调用应正确处理
     */
    @Test
    fun `multiple tool calls should produce multiple cards`() {
        val assistantMessage = MessageUi(
            id = "msg-7",
            role = MessageRole.ASSISTANT,
            content = listOf(
                MessageContentItem.ToolCall(
                    id = "call-4",
                    name = "read_file",
                    args = JsonObject(mapOf("path" to JsonPrimitive("/a.txt"))),
                    phase = "call"
                ),
                MessageContentItem.ToolCall(
                    id = "call-5",
                    name = "read_file",
                    args = JsonObject(mapOf("path" to JsonPrimitive("/b.txt"))),
                    phase = "call"
                )
            ),
            timestamp = System.currentTimeMillis()
        )

        val toolCards = pairToolCards(assistantMessage)

        assertEquals("应产生 2 个工具卡片", 2, toolCards.size)
    }

    /**
     * 测试：系统消息不应产生工具卡片
     */
    @Test
    fun `system message should not produce tool cards`() {
        val systemMessage = MessageUi(
            id = "msg-8",
            role = MessageRole.SYSTEM,
            content = listOf(MessageContentItem.Text("System instruction")),
            timestamp = System.currentTimeMillis()
        )

        val toolCards = pairToolCards(systemMessage)

        assertTrue("系统消息不应产生工具卡片", toolCards.isEmpty())
    }
}