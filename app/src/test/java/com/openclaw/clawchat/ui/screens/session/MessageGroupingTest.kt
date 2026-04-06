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

    /**
     * 测试：跨消息的 ToolResult 匹配
     */
    @Test
    fun `tool result from group messages should be matched`() {
        val toolCallId = "call-cross-1"
        val assistantMessage = MessageUi(
            id = "msg-9",
            role = MessageRole.ASSISTANT,
            content = listOf(
                MessageContentItem.ToolCall(
                    id = toolCallId,
                    name = "web_search",
                    args = JsonObject(mapOf("query" to JsonPrimitive("test"))),
                    phase = "result"
                )
            ),
            timestamp = System.currentTimeMillis()
        )

        val toolMessage = MessageUi(
            id = "msg-10",
            role = MessageRole.TOOL,
            content = listOf(MessageContentItem.Text("Search results here")),
            timestamp = System.currentTimeMillis(),
            toolCallId = toolCallId,
            toolName = "web_search"
        )

        val toolCards = pairToolCards(assistantMessage, listOf(assistantMessage, toolMessage))

        assertEquals("应产生 1 个工具卡片", 1, toolCards.size)
        assertEquals("应有结果", "Search results here", toolCards[0].result)
    }

    /**
     * 测试：ToolResult 精确匹配 toolCallId
     */
    @Test
    fun `tool result should match exact toolCallId`() {
        val assistantMessage = MessageUi(
            id = "msg-11",
            role = MessageRole.ASSISTANT,
            content = listOf(
                MessageContentItem.ToolCall(
                    id = "call-exact-1",
                    name = "read",
                    phase = "result"
                ),
                MessageContentItem.ToolCall(
                    id = "call-exact-2",
                    name = "write",
                    phase = "result"
                )
            ),
            timestamp = System.currentTimeMillis()
        )

        val toolMessage1 = MessageUi(
            id = "msg-12",
            role = MessageRole.TOOL,
            content = listOf(MessageContentItem.Text("read result")),
            timestamp = System.currentTimeMillis(),
            toolCallId = "call-exact-1",
            toolName = "read"
        )

        val toolMessage2 = MessageUi(
            id = "msg-13",
            role = MessageRole.TOOL,
            content = listOf(MessageContentItem.Text("write result")),
            timestamp = System.currentTimeMillis(),
            toolCallId = "call-exact-2",
            toolName = "write"
        )

        val toolCards = pairToolCards(assistantMessage, listOf(assistantMessage, toolMessage1, toolMessage2))

        assertEquals("应产生 2 个工具卡片", 2, toolCards.size)
        assertEquals("第一个工具应为 read", "read", toolCards[0].name)
        assertEquals("第二个工具应为 write", "write", toolCards[1].name)
        assertEquals("read 结果应正确匹配", "read result", toolCards[0].result)
        assertEquals("write 结果应正确匹配", "write result", toolCards[1].result)
    }

    /**
     * 测试：时间戳格式化
     */
    @Test
    fun `formatTimestamp should return correct format`() {
        val now = System.currentTimeMillis()

        // 刚刚
        val justNow = formatTimestamp(now - 30_000)
        assertEquals("30秒前应显示刚刚", "刚刚", justNow)

        // 分钟前
        val minutesAgo = formatTimestamp(now - 5 * 60_000)
        assertTrue("5分钟前应包含分钟", minutesAgo.contains("分钟前"))

        // 小时前
        val hoursAgo = formatTimestamp(now - 2 * 3600_000)
        assertTrue("2小时前应包含小时", hoursAgo.contains("小时前"))
    }
}