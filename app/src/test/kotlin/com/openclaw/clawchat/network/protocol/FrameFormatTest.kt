package com.openclaw.clawchat.network.protocol

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.*
import org.junit.Test

/**
 * 帧格式单元测试
 * 
 * 测试 Request/Response/Event 帧的序列化和反序列化
 */
class FrameFormatTest {
    
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    // ==================== Request 帧测试 ====================
    
    @Test
    fun testRequestFrameSerialization() {
        val request = RequestFrame(
            id = "req-123",
            method = "session.send",
            params = mapOf(
                "sessionId" to JsonPrimitive("test-session"),
                "content" to JsonPrimitive("Hello")
            )
        )
        
        val jsonString = json.encodeToString(request)
        
        assertTrue("JSON 应包含 type", jsonString.contains("\"type\":\"req\""))
        assertTrue("JSON 应包含 id", jsonString.contains("req-123"))
        assertTrue("JSON 应包含 method", jsonString.contains("session.send"))
        assertTrue("JSON 应包含 params", jsonString.contains("sessionId"))
    }
    
    @Test
    fun testRequestFrameDeserialization() {
        val jsonString = """
            {
                "type": "req",
                "id": "req-456",
                "method": "session.create",
                "params": {
                    "model": "qwen3.5-plus",
                    "thinking": true
                }
            }
        """.trimIndent()
        
        val request = json.decodeFromString<RequestFrame>(jsonString)
        
        assertEquals("req", request.type)
        assertEquals("req-456", request.id)
        assertEquals("session.create", request.method)
        assertNotNull("params 不应为空", request.params)
    }
    
    @Test
    fun testRequestIdGeneration() {
        val id1 = RequestFrame.generateRequestId()
        val id2 = RequestFrame.generateRequestId()
        
        // ID 应该不同（包含时间戳和计数器）
        assertNotEquals("ID 应该唯一", id1, id2)
        
        // ID 应该有正确的格式
        assertTrue("ID 应以 req-开头", id1.startsWith("req-"))
        assertTrue("ID 应以 req-开头", id2.startsWith("req-"))
    }
    
    @Test
    fun testGatewayMethodValues() {
        assertEquals("session.send", GatewayMethod.SEND_MESSAGE.value)
        assertEquals("session.list", GatewayMethod.GET_SESSIONS.value)
        assertEquals("session.create", GatewayMethod.CREATE_SESSION.value)
        assertEquals("session.terminate", GatewayMethod.TERMINATE_SESSION.value)
        assertEquals("ping", GatewayMethod.PING.value)
    }
    
    // ==================== Response 帧测试 ====================
    
    @Test
    fun testSuccessResponseSerialization() {
        val response = ResponseFrame(
            id = "req-123",
            ok = true,
            payload = JsonPrimitive("{\"messageId\":\"msg-123\"}")
        )
        
        val jsonString = json.encodeToString(response)
        
        assertTrue("JSON 应包含 type", jsonString.contains("\"type\":\"res\""))
        assertTrue("JSON 应包含 ok:true", jsonString.contains("\"ok\":true"))
        assertTrue("JSON 应包含 id", jsonString.contains("req-123"))
    }
    
    @Test
    fun testErrorResponseSerialization() {
        val response = ResponseFrame(
            id = "req-123",
            ok = false,
            error = ResponseError(
                code = "SESSION_NOT_FOUND",
                message = "Session not found",
                details = mapOf("sessionId" to "test-123")
            )
        )
        
        val jsonString = json.encodeToString(response)
        
        assertTrue("JSON 应包含 ok:false", jsonString.contains("\"ok\":false"))
        assertTrue("JSON 应包含 error", jsonString.contains("\"error\""))
        assertTrue("JSON 应包含 code", jsonString.contains("SESSION_NOT_FOUND"))
    }
    
    @Test
    fun testResponseIsSuccess() {
        val successResponse = ResponseFrame(id = "req-1", ok = true)
        val errorResponse = ResponseFrame(id = "req-2", ok = false)
        
        assertTrue("成功响应应该 isSuccess", successResponse.isSuccess())
        assertFalse("错误响应不应该 isSuccess", errorResponse.isSuccess())
    }
    
    @Test
    fun testResponseErrorCodeValues() {
        assertEquals("INVALID_REQUEST", ResponseErrorCode.INVALID_REQUEST.code)
        assertEquals("AUTH_REQUIRED", ResponseErrorCode.AUTH_REQUIRED.code)
        assertEquals("SESSION_NOT_FOUND", ResponseErrorCode.SESSION_NOT_FOUND.code)
        assertEquals("DEVICE_NOT_PAIRED", ResponseErrorCode.DEVICE_NOT_PAIRED.code)
    }
    
    // ==================== Event 帧测试 ====================
    
    @Test
    fun testEventFrameSerialization() {
        val event = EventFrame(
            event = "session.message",
            payload = JsonPrimitive("{\"sessionId\":\"test\",\"content\":\"Hello\"}"),
            seq = 5,
            stateVersion = "v1"
        )
        
        val jsonString = json.encodeToString(event)
        
        assertTrue("JSON 应包含 type", jsonString.contains("\"type\":\"event\""))
        assertTrue("JSON 应包含 event", jsonString.contains("session.message"))
        assertTrue("JSON 应包含 seq", jsonString.contains("5"))
        assertTrue("JSON 应包含 stateVersion", jsonString.contains("v1"))
    }
    
    @Test
    fun testEventFrameDeserialization() {
        val jsonString = """
            {
                "type": "event",
                "event": "session.create",
                "payload": {
                    "session": {
                        "id": "session-123",
                        "label": "Test"
                    }
                },
                "seq": 10,
                "stateVersion": "abc123"
            }
        """.trimIndent()
        
        val event = json.decodeFromString<EventFrame>(jsonString)
        
        assertEquals("event", event.type)
        assertEquals("session.create", event.event)
        assertEquals(10, event.seq)
        assertEquals("abc123", event.stateVersion)
    }
    
    @Test
    fun testGatewayEventValues() {
        assertEquals("session.message", GatewayEvent.SESSION_MESSAGE.value)
        assertEquals("session.create", GatewayEvent.SESSION_CREATE.value)
        assertEquals("connect.challenge", GatewayEvent.CONNECT_CHALLENGE.value)
        assertEquals("connect.ok", GatewayEvent.CONNECT_OK.value)
        assertEquals("system.notification", GatewayEvent.SYSTEM_NOTIFICATION.value)
    }
    
    // ==================== 辅助函数测试 ====================
    
    @Test
    fun testRequestFrameBuilder() {
        val request = requestFrame(GatewayMethod.PING) {
            putLong("timestamp", 1234567890L)
        }
        
        assertEquals("ping", request.method)
        assertNotNull("params 不应为空", request.params)
        assertTrue("ID 应以 req-开头", request.id.startsWith("req-"))
    }
    
    @Test
    fun testErrorResponseHelper() {
        val response = errorResponse(
            requestId = "req-123",
            errorCode = ResponseErrorCode.SESSION_NOT_FOUND,
            details = mapOf("sessionId" to "test")
        )
        
        assertEquals("req-123", response.id)
        assertFalse("应该是错误响应", response.ok)
        assertEquals("SESSION_NOT_FOUND", response.error?.code)
        assertEquals("会话不存在", response.error?.message)
    }
    
    @Test
    fun testSuccessResponseHelper() {
        val response = successResponse(
            requestId = "req-123",
            payload = JsonPrimitive("{\"result\":\"ok\"}")
        )
        
        assertEquals("req-123", response.id)
        assertTrue("应该是成功响应", response.ok)
        assertNotNull("payload 不应为空", response.payload)
    }
    
    // ==================== 载荷测试 ====================
    
    @Test
    fun testSendMessageParamsSerialization() {
        val params = SendMessageParams(
            sessionId = "test-session",
            content = "Hello, Gateway!",
            attachments = listOf(
                AttachmentParams(
                    id = "att-1",
                    name = "test.txt",
                    mimeType = "text/plain",
                    size = 1024
                )
            )
        )
        
        val jsonString = json.encodeToString(params)
        
        assertTrue("JSON 应包含 sessionId", jsonString.contains("test-session"))
        assertTrue("JSON 应包含 content", jsonString.contains("Hello, Gateway!"))
        assertTrue("JSON 应包含 attachments", jsonString.contains("attachments"))
    }
    
    @Test
    fun testCreateSessionParamsSerialization() {
        val params = CreateSessionParams(
            model = "qwen3.5-plus",
            thinking = true
        )
        
        val jsonString = json.encodeToString(params)
        
        assertTrue("JSON 应包含 model", jsonString.contains("qwen3.5-plus"))
        assertTrue("JSON 应包含 thinking", jsonString.contains("true"))
    }
    
    @Test
    fun testSessionInfoSerialization() {
        val session = SessionInfo(
            id = "session-123",
            label = "Test Session",
            model = "qwen3.5-plus",
            status = "running",
            createdAt = 1234567890L,
            lastActivityAt = 1234567900L,
            messageCount = 5
        )
        
        val jsonString = json.encodeToString(session)
        
        assertTrue("JSON 应包含 id", jsonString.contains("session-123"))
        assertTrue("JSON 应包含 label", jsonString.contains("Test Session"))
        assertTrue("JSON 应包含 status", jsonString.contains("running"))
    }
    
    @Test
    fun testPingResponseSerialization() {
        val response = PingResponse(
            timestamp = 1234567890L,
            latency = 50L
        )
        
        val jsonString = json.encodeToString(response)
        
        assertTrue("JSON 应包含 timestamp", jsonString.contains("1234567890"))
        assertTrue("JSON 应包含 latency", jsonString.contains("50"))
    }
}
