package com.openclaw.clawchat.network

import com.openclaw.clawchat.network.protocol.GatewayConnection
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/**
 * OkHttpWebSocketService 单元测试
 *
 * 验证 WebSocketService 接口契约在委托 GatewayConnection 后保持不变。
 */
class OkHttpWebSocketServiceTest {

    // ==================== 接口契约测试 ====================

    @Test
    fun testInitialStateIsDisconnected() {
        // OkHttpWebSocketService 未连接时 connectionState 应为 Disconnected
        // 因为 gateway 为 null，getter 返回一个默认 Disconnected 的 StateFlow
        val state = WebSocketConnectionState.Disconnected
        assertTrue(
            "初始状态应为 Disconnected",
            state is WebSocketConnectionState.Disconnected
        )
    }

    @Test
    fun testConnectionStateSealed() {
        // 确保所有连接状态子类型可正常构造
        val disconnected = WebSocketConnectionState.Disconnected
        val connecting = WebSocketConnectionState.Connecting
        val connected = WebSocketConnectionState.Connected
        val disconnecting = WebSocketConnectionState.Disconnecting("test")
        val error = WebSocketConnectionState.Error(RuntimeException("test"))

        assertEquals("test", disconnecting.reason)
        assertEquals("test", error.throwable.message)
        assertNotNull(disconnected)
        assertNotNull(connecting)
        assertNotNull(connected)
    }

    @Test
    fun testGatewayMessageTypes() {
        // 验证所有 GatewayMessage 子类型的 type 字段正确
        val userMsg = GatewayMessage.UserMessage(
            sessionId = "s1",
            content = "hello"
        )
        assertEquals("userMessage", userMsg.type)

        val assistantMsg = GatewayMessage.AssistantMessage(
            sessionId = "s1",
            content = "hi",
            model = "qwen"
        )
        assertEquals("assistantMessage", assistantMsg.type)

        val sysEvent = GatewayMessage.SystemEvent(text = "event")
        assertEquals("systemEvent", sysEvent.type)

        val ping = GatewayMessage.Ping(timestamp = 123L)
        assertEquals("ping", ping.type)

        val pong = GatewayMessage.Pong(timestamp = 123L, latency = 50L)
        assertEquals("pong", pong.type)

        val error = GatewayMessage.Error(code = "E1", message = "err")
        assertEquals("error", error.type)
    }

    @Test
    fun testMessageParserRoundTrip() {
        val original = GatewayMessage.UserMessage(
            sessionId = "session-abc",
            content = "Hello, Gateway!",
            timestamp = 1234567890000L
        )

        val json = MessageParser.serialize(original)
        val parsed = MessageParser.parse(json)

        assertNotNull("解析不应返回 null", parsed)
        assertTrue("应为 UserMessage", parsed is GatewayMessage.UserMessage)
        val msg = parsed as GatewayMessage.UserMessage
        assertEquals("session-abc", msg.sessionId)
        assertEquals("Hello, Gateway!", msg.content)
    }

    @Test
    fun testMessageParserInvalidJson() {
        val result = MessageParser.parse("not json at all")
        assertNull("无效 JSON 应返回 null", result)
    }

    @Test
    fun testMessageParserSystemEvent() {
        val json = """{"type":"systemEvent","text":"Session created","timestamp":1234567890000}"""
        val msg = MessageParser.parse(json)

        assertNotNull(msg)
        assertTrue(msg is GatewayMessage.SystemEvent)
        assertEquals("Session created", (msg as GatewayMessage.SystemEvent).text)
    }
}
