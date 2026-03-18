package com.openclaw.clawchat.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertFailsWith

/**
 * 领域模型单元测试
 * 
 * 测试 Domain 层模型的创建、验证和业务逻辑。
 */
class DomainModelTest {
    
    // ==================== Session 测试 ====================
    
    @Test
    fun `session creation with default values`() {
        val session = Session.create()
        
        assertNotNull(session.id)
        assertTrue(session.id.startsWith("session_"))
        assertEquals(SessionStatus.RUNNING, session.status)
        assertFalse(session.thinking)
        assertNull(session.label)
        assertNull(session.model)
    }
    
    @Test
    fun `session creation with custom parameters`() {
        val session = Session.create(
            model = "aliyun/qwen3.5-plus",
            label = "Test Session",
            thinking = true
        )
        
        assertEquals("aliyun/qwen3.5-plus", session.model)
        assertEquals("Test Session", session.label)
        assertTrue(session.thinking)
        assertTrue(session.isActive())
    }
    
    @Test
    fun `session status transition`() {
        val session = Session.create()
        assertEquals(SessionStatus.RUNNING, session.status)
        
        val terminated = session.copy(status = SessionStatus.TERMINATED)
        assertTrue(terminated.isTerminated())
        assertFalse(terminated.isActive())
    }
    
    @Test
    fun `session message count update`() {
        val session = Session.create()
        assertEquals(0, session.messageCount)
        
        val withMessage = session.withMessageAdded()
        assertEquals(1, withMessage.messageCount)
        assertTrue(withMessage.lastActivityAt >= session.lastActivityAt)
    }
    
    @Test
    fun `session status from string`() {
        assertEquals(SessionStatus.RUNNING, SessionStatus.fromString("running"))
        assertEquals(SessionStatus.RUNNING, SessionStatus.fromString("ACTIVE"))
        assertEquals(SessionStatus.PAUSED, SessionStatus.fromString("paused"))
        assertEquals(SessionStatus.TERMINATED, SessionStatus.fromString("terminated"))
        assertEquals(SessionStatus.ERROR, SessionStatus.fromString("ERROR"))
        
        assertFailsWith<IllegalArgumentException> {
            SessionStatus.fromString("invalid")
        }
    }
    
    // ==================== Message 测试 ====================
    
    @Test
    fun `user message creation`() {
        val message = Message.createUserMessage(
            sessionId = "session_123",
            content = "Hello, World!"
        )
        
        assertNotNull(message.id)
        assertTrue(message.id.startsWith("msg_"))
        assertEquals("session_123", message.sessionId)
        assertEquals(MessageRole.USER, message.role)
        assertEquals("Hello, World!", message.content)
        assertTrue(message.isUserMessage())
        assertFalse(message.isAssistantMessage())
    }
    
    @Test
    fun `message with attachments`() {
        val attachment = Attachment(
            id = "att_123",
            name = "test.png",
            mimeType = "image/png",
            size = 1024
        )
        
        val message = Message.createUserMessage(
            sessionId = "session_123",
            content = "Check this image",
            attachments = listOf(attachment)
        )
        
        assertTrue(message.hasAttachments())
        assertEquals(1, message.attachments.size)
        assertTrue(message.attachments.first().isImage())
    }
    
    @Test
    fun `message preview truncation`() {
        val longContent = "a".repeat(200)
        val message = Message.createUserMessage(
            sessionId = "session_123",
            content = longContent
        )
        
        val preview = message.getPreview(maxLength = 50)
        assertEquals(50, preview.length)
        assertTrue(preview.endsWith("..."))
    }
    
    @Test
    fun `message role parsing`() {
        assertEquals(MessageRole.USER, MessageRole.fromString("user"))
        assertEquals(MessageRole.ASSISTANT, MessageRole.fromString("assistant"))
        assertEquals(MessageRole.ASSISTANT, MessageRole.fromString("AGENT"))
        assertEquals(MessageRole.SYSTEM, MessageRole.fromString("system"))
        
        assertFailsWith<IllegalArgumentException> {
            MessageRole.fromString("invalid")
        }
    }
    
    // ==================== Attachment 测试 ====================
    
    @Test
    fun `attachment type detection`() {
        val image = Attachment(
            id = "att_1",
            name = "photo.png",
            mimeType = "image/png",
            size = 1024
        )
        assertTrue(image.isImage())
        assertFalse(image.isFile())
        
        val document = Attachment(
            id = "att_2",
            name = "doc.pdf",
            mimeType = "application/pdf",
            size = 2048
        )
        assertFalse(document.isImage())
        assertTrue(document.isFile())
    }
    
    @Test
    fun `attachment size formatting`() {
        val small = Attachment(
            id = "att_1",
            name = "small.txt",
            mimeType = "text/plain",
            size = 512
        )
        assertEquals("512 B", small.getHumanReadableSize())
        
        val medium = Attachment(
            id = "att_2",
            name = "medium.jpg",
            mimeType = "image/jpeg",
            size = 1024 * 500
        )
        assertEquals("500 KB", medium.getHumanReadableSize())
        
        val large = Attachment(
            id = "att_3",
            name = "large.mp4",
            mimeType = "video/mp4",
            size = 1024 * 1024 * 100
        )
        assertEquals("100 MB", large.getHumanReadableSize())
    }
    
    // ==================== GatewayConfig 测试 ====================
    
    @Test
    fun `gateway config URL generation`() {
        val httpConfig = GatewayConfig(
            name = "Local",
            host = "192.168.1.1",
            port = 18789,
            useTls = false
        )
        assertEquals("ws://192.168.1.1:18789", httpConfig.toWebSocketUrl())
        assertEquals("http://192.168.1.1:18789", httpConfig.toHttpUrl())
        
        val httpsConfig = GatewayConfig(
            name = "Remote",
            host = "gateway.example.com",
            port = 18789,
            useTls = true
        )
        assertEquals("wss://gateway.example.com:18789", httpsConfig.toWebSocketUrl())
        assertEquals("https://gateway.example.com:18789", httpsConfig.toHttpUrl())
    }
    
    @Test
    fun `gateway config connection type detection`() {
        val localConfig = GatewayConfig(
            name = "Home",
            host = "192.168.1.100",
            useTls = false
        )
        assertTrue(localConfig.isLocalConnection())
        assertFalse(localConfig.isTailscaleConnection())
        
        val tailscaleConfig = GatewayConfig(
            name = "VPS",
            host = "server.mytailnet.ts.net",
            useTls = true
        )
        assertFalse(tailscaleConfig.isLocalConnection())
        assertTrue(tailscaleConfig.isTailscaleConnection())
    }
    
    @Test
    fun `gateway config default port`() {
        val config = GatewayConfig(
            name = "Default",
            host = "localhost"
        )
        assertEquals(18789, config.port)
    }
    
    // ==================== User 测试 ====================
    
    @Test
    fun `user role permissions`() {
        val admin = User(
            id = "user_1",
            name = "Admin",
            role = UserRole.ADMIN,
            scopes = listOf("*")
        )
        assertTrue(admin.isAdmin())
        assertTrue(admin.canWrite())
        assertTrue(admin.hasScope("*"))
        
        val operator = User(
            id = "user_2",
            name = "Operator",
            role = UserRole.OPERATOR,
            scopes = listOf("operator.read", "operator.write")
        )
        assertFalse(operator.isAdmin())
        assertTrue(operator.canWrite())
        assertTrue(operator.hasScope("operator.write"))
        assertFalse(operator.hasScope("admin.settings"))
        
        val viewer = User(
            id = "user_3",
            name = "Viewer",
            role = UserRole.VIEWER,
            scopes = listOf("viewer.read")
        )
        assertFalse(viewer.isAdmin())
        assertFalse(viewer.canWrite())
    }
    
    @Test
    fun `user role parsing`() {
        assertEquals(UserRole.ADMIN, UserRole.fromString("admin"))
        assertEquals(UserRole.ADMIN, UserRole.fromString("ADMINISTRATOR"))
        assertEquals(UserRole.OPERATOR, UserRole.fromString("operator"))
        assertEquals(UserRole.OPERATOR, UserRole.fromString("op"))
        assertEquals(UserRole.VIEWER, UserRole.fromString("viewer"))
        
        assertFailsWith<IllegalArgumentException> {
            UserRole.fromString("invalid")
        }
    }
    
    // ==================== DeviceToken 测试 ====================
    
    @Test
    fun `device token validity check`() {
        val validToken = DeviceToken(
            token = "token_123",
            deviceId = "device_456",
            expiresAt = System.currentTimeMillis() + 3600_000 // 1 hour later
        )
        assertTrue(validToken.isValid())
        assertFalse(validToken.isExpired())
        
        val expiredToken = DeviceToken(
            token = "token_789",
            deviceId = "device_012",
            expiresAt = System.currentTimeMillis() - 3600_000 // 1 hour ago
        )
        assertFalse(expiredToken.isValid())
        assertTrue(expiredToken.isExpired())
        
        val neverExpiresToken = DeviceToken(
            token = "token_permanent",
            deviceId = "device_perm",
            expiresAt = null
        )
        assertTrue(neverExpiresToken.isValid())
        assertFalse(neverExpiresToken.isExpired())
    }
    
    // ==================== ConnectionStatus 测试 ====================
    
    @Test
    fun `connection status type checking`() {
        val disconnected = ConnectionStatus.Disconnected
        assertTrue(disconnected.isDisconnected())
        assertFalse(disconnected.isConnected())
        
        val connecting = ConnectionStatus.Connecting
        assertTrue(connecting.isConnecting())
        
        val connected = ConnectionStatus.Connected(latency = 45)
        assertTrue(connected.isConnected())
        assertEquals(45, connected.latency)
        
        val error = ConnectionStatus.Error("Network error", recoverable = true)
        assertTrue(error.hasError())
        assertTrue(error.recoverable)
    }
}
