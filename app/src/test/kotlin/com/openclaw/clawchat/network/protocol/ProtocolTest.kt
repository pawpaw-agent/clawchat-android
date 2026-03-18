package com.openclaw.clawchat.network.protocol

import com.openclaw.clawchat.network.GatewayMessage
import com.openclaw.clawchat.network.MessageParser
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

/**
 * WebSocket 协议 v3 单元测试
 * 
 * 测试协议定义、消息格式、认证流程
 */
class ProtocolTest {
    
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    // ==================== 协议版本测试 ====================
    
    @Test
    fun testProtocolVersionCompatibility() {
        // 相同主版本兼容
        val v1 = ProtocolVersion(3, 0, 0)
        val v2 = ProtocolVersion(3, 1, 0)
        assertTrue("主版本相同应该兼容", v1.isCompatibleWith(v2))
        
        // 不同主版本不兼容
        val v3 = ProtocolVersion(2, 0, 0)
        assertFalse("主版本不同不应该兼容", v1.isCompatibleWith(v3))
        
        val v4 = ProtocolVersion(4, 0, 0)
        assertFalse("主版本不同不应该兼容", v1.isCompatibleWith(v4))
    }
    
    @Test
    fun testProtocolVersionParsing() {
        val version = ProtocolVersion.fromString("3.1.2")
        assertEquals(3, version.major)
        assertEquals(1, version.minor)
        assertEquals(2, version.patch)
        
        val version2 = ProtocolVersion.fromString("3.0")
        assertEquals(3, version2.major)
        assertEquals(0, version2.minor)
        assertEquals(0, version2.patch)
    }
    
    // ==================== 认证消息测试 ====================
    
    @Test
    fun testAuthRequestSerialization() {
        val clientInfo = ClientInfo(
            clientId = "openclaw-android",
            clientVersion = "1.0.0",
            platform = "android",
            osVersion = "13",
            deviceModel = "Google Pixel 7",
            protocolVersion = "3.0.0"
        )
        
        val authRequest = AuthRequest(
            deviceId = "test-device-id",
            publicKey = "-----BEGIN PUBLIC KEY-----\nTEST\n-----END PUBLIC KEY-----",
            clientInfo = clientInfo
        )
        
        val jsonStr = json.encodeToString(authRequest)
        
        assertTrue("JSON 应包含 deviceId", jsonStr.contains("test-device-id"))
        assertTrue("JSON 应包含 publicKey", jsonStr.contains("PUBLIC KEY"))
        assertTrue("JSON 应包含 clientInfo", jsonStr.contains("clientInfo"))
        assertTrue("JSON 应包含 protocolVersion", jsonStr.contains("3.0.0"))
    }
    
    @Test
    fun testAuthChallengeParsing() {
        val challengeJson = """
            {
                "type": "challenge",
                "nonce": "550e8400-e29b-41d4-a716-446655440000",
                "expiresAt": 1234567890000,
                "protocolVersion": "3.0.0"
            }
        """.trimIndent()
        
        val challenge = challengeJson.toAuthChallenge()
        
        assertNotNull("Challenge 不应为空", challenge)
        assertEquals("challenge", challenge?.type)
        assertEquals("550e8400-e29b-41d4-a716-446655440000", challenge?.nonce)
        assertEquals(1234567890000L, challenge?.expiresAt)
        assertEquals("3.0.0", challenge?.protocolVersion)
    }
    
    @Test
    fun testAuthResponseSerialization() {
        val authResponse = AuthResponse(
            nonce = "550e8400-e29b-41d4-a716-446655440000",
            signature = "MEUCIQDtest...",
            deviceId = "test-device-id"
        )
        
        val jsonStr = json.encodeToString(authResponse)
        
        assertTrue("JSON 应包含 nonce", jsonStr.contains("550e8400"))
        assertTrue("JSON 应包含 signature", jsonStr.contains("MEUCIQDtest"))
        assertTrue("JSON 应包含 deviceId", jsonStr.contains("test-device-id"))
    }
    
    @Test
    fun testAuthSuccessParsing() {
        val successJson = """
            {
                "type": "auth_success",
                "deviceToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
                "expiresIn": 86400,
                "protocolVersion": "3.0.0"
            }
        """.trimIndent()
        
        val success = successJson.toAuthSuccess()
        
        assertNotNull("AuthSuccess 不应为空", success)
        assertEquals("auth_success", success?.type)
        assertTrue("deviceToken 不应为空", success?.deviceToken?.isNotEmpty() == true)
        assertEquals(86400L, success?.expiresIn)
    }
    
    // ==================== 错误处理测试 ====================
    
    @Test
    fun testProtocolErrorParsing() {
        val errorJson = """
            {
                "code": 2002,
                "message": "Invalid signature",
                "details": "Signature verification failed",
                "timestamp": 1234567890000
            }
        """.trimIndent()
        
        val error = errorJson.toProtocolError()
        
        assertNotNull("ProtocolError 不应为空", error)
        assertEquals(2002, error?.code)
        assertEquals("Invalid signature", error?.message)
        assertEquals("Signature verification failed", error?.details)
    }
    
    @Test
    fun testProtocolErrorCodeMapping() {
        // 测试错误码映射
        assertEquals(2002, ProtocolErrorCode.INVALID_SIGNATURE.code)
        assertEquals(2003, ProtocolErrorCode.EXPIRED_CHALLENGE.code)
        assertEquals(2004, ProtocolErrorCode.INVALID_NONCE.code)
        assertEquals(2005, ProtocolErrorCode.DEVICE_NOT_PAIRED.code)
    }
    
    // ==================== 消息格式测试 ====================
    
    @Test
    fun testGatewayMessageSerialization() {
        val userMessage = GatewayMessage.UserMessage(
            sessionId = "test-session",
            content = "Hello, Gateway!",
            timestamp = System.currentTimeMillis()
        )
        
        val jsonStr = MessageParser.serialize(userMessage)
        
        assertTrue("JSON 应包含 type", jsonStr.contains("\"type\""))
        assertTrue("JSON 应包含 userMessage", jsonStr.contains("userMessage"))
        assertTrue("JSON 应包含 sessionId", jsonStr.contains("test-session"))
        assertTrue("JSON 应包含 content", jsonStr.contains("Hello, Gateway!"))
    }
    
    @Test
    fun testGatewayMessageParsing() {
        val messageJson = """
            {
                "type": "systemEvent",
                "text": "Session created",
                "timestamp": 1234567890000
            }
        """.trimIndent()
        
        val message = MessageParser.parse(messageJson)
        
        assertNotNull("消息不应为空", message)
        assertTrue("应该是 SystemEvent 类型", message is GatewayMessage.SystemEvent)
        assertEquals("systemEvent", message?.type)
    }
    
    // ==================== Frame 格式测试 ====================
    
    @Test
    fun testFrameHeaderSerialization() {
        val header = FrameHeader(
            type = "userMessage",
            version = "3.0.0",
            timestamp = 1234567890000L,
            messageId = "msg-123",
            sessionId = "session-456"
        )
        
        val jsonStr = json.encodeToString(header)
        
        assertTrue("JSON 应包含 type", jsonStr.contains("userMessage"))
        assertTrue("JSON 应包含 version", jsonStr.contains("3.0.0"))
        assertTrue("JSON 应包含 timestamp", jsonStr.contains("1234567890000"))
        assertTrue("JSON 应包含 messageId", jsonStr.contains("msg-123"))
    }
    
    @Test
    fun testWebSocketFrameSerialization() {
        val frame = WebSocketFrame(
            header = FrameHeader(
                type = "userMessage",
                version = "3.0.0",
                timestamp = System.currentTimeMillis()
            ),
            payload = """{"sessionId":"test","content":"hello"}""",
            signature = "test-signature"
        )
        
        val jsonStr = json.encodeToString(frame)
        
        assertTrue("JSON 应包含 header", jsonStr.contains("header"))
        assertTrue("JSON 应包含 payload", jsonStr.contains("payload"))
        assertTrue("JSON 应包含 signature", jsonStr.contains("test-signature"))
    }
    
    // ==================== Nonce 验证测试 ====================
    
    @Test
    fun testNonceValidation() {
        // UUID 格式 nonce
        val uuidNonce = "550e8400-e29b-41d4-a716-446655440000"
        assertTrue("UUID 格式应该有效", isValidNonce(uuidNonce))
        
        // Base64 格式 nonce
        val base64Nonce = "YWJjZGVmZ2hpamtsbW5vcA=="
        assertTrue("Base64 格式应该有效", isValidNonce(base64Nonce))
        
        // 太短的 nonce
        val shortNonce = "abc"
        assertFalse("太短应该无效", isValidNonce(shortNonce))
        
        // 空 nonce
        val emptyNonce = ""
        assertFalse("空应该无效", isValidNonce(emptyNonce))
    }
    
    /**
     * 验证 nonce 格式（与 ChallengeResponseAuth 中相同的逻辑）
     */
    private fun isValidNonce(nonce: String): Boolean {
        if (nonce.length < 16) return false
        
        // 尝试解析为 UUID
        try {
            java.util.UUID.fromString(nonce)
            return true
        } catch (e: Exception) {
            // 不是 UUID 格式，继续检查
        }
        
        // 尝试解析为 Base64
        return try {
            android.util.Base64.decode(nonce, android.util.Base64.DEFAULT)
            true
        } catch (e: Exception) {
            false
        }
    }
}
