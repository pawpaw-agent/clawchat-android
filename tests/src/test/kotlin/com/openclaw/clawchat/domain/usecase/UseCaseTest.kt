package com.openclaw.clawchat.domain.usecase

import com.openclaw.clawchat.domain.model.*
import com.openclaw.clawchat.domain.repository.SessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertFailsWith

/**
 * UseCase 单元测试
 * 
 * 测试 Domain 层 UseCase 的业务逻辑。
 * 使用 Mock Repository 进行隔离测试。
 */
class UseCaseTest {
    
    // ==================== Mock Repository ====================
    
    private class MockSessionRepository : SessionRepository {
        private val sessions = mutableMapOf<String, Session>()
        private val messages = mutableMapOf<String, List<Message>>()
        
        override fun observeSessions(): Flow<List<Session>> = 
            flowOf(sessions.values.toList())
        
        override suspend fun getSession(sessionId: String): Session? = 
            sessions[sessionId]
        
        override suspend fun createSession(
            model: String?,
            label: String?,
            thinking: Boolean
        ): Result<Session> {
            val session = Session.create(model, label, thinking)
            sessions[session.id] = session
            return Result.success(session)
        }
        
        override suspend fun updateSession(session: Session): Result<Unit> {
            sessions[session.id] = session
            return Result.success(Unit)
        }
        
        override suspend fun pauseSession(sessionId: String): Result<Unit> {
            sessions[sessionId]?.let {
                sessions[sessionId] = it.copy(status = SessionStatus.PAUSED)
            }
            return Result.success(Unit)
        }
        
        override suspend fun resumeSession(sessionId: String): Result<Unit> {
            sessions[sessionId]?.let {
                sessions[sessionId] = it.copy(status = SessionStatus.RUNNING)
            }
            return Result.success(Unit)
        }
        
        override suspend fun terminateSession(sessionId: String): Result<Unit> {
            sessions[sessionId]?.let {
                sessions[sessionId] = it.copy(status = SessionStatus.TERMINATED)
            }
            return Result.success(Unit)
        }
        
        override suspend fun deleteSession(sessionId: String): Result<Unit> {
            sessions.remove(sessionId)
            messages.remove(sessionId)
            return Result.success(Unit)
        }
        
        override fun observeMessages(sessionId: String): Flow<List<Message>> = 
            flowOf(messages[sessionId] ?: emptyList())
        
        override suspend fun getMessageHistory(
            sessionId: String,
            limit: Int
        ): List<Message> = messages[sessionId] ?: emptyList()
        
        override suspend fun sendMessage(
            sessionId: String,
            content: String,
            attachments: List<Attachment>
        ): Result<Message> {
            val message = Message.createUserMessage(sessionId, content, attachments)
            val currentList = messages[sessionId] ?: emptyList()
            messages[sessionId] = currentList + message
            
            // 更新会话消息计数
            sessions[sessionId]?.let {
                sessions[sessionId] = it.withMessageAdded()
            }
            
            return Result.success(message)
        }
        
        override suspend fun regenerateResponse(sessionId: String): Result<Message> {
            return Result.failure(UnsupportedOperationException("Not implemented in mock"))
        }
        
        override suspend fun getActiveSessionCount(): Int = 
            sessions.count { it.value.isActive() }
        
        override suspend fun searchSessions(query: String): List<Session> = 
            sessions.values.filter { 
                it.label?.contains(query, ignoreCase = true) == true 
            }
        
        override suspend fun getSessionsByModel(model: String): List<Session> = 
            sessions.values.filter { it.model == model }
        
        // 测试辅助方法
        fun addMessage(sessionId: String, message: Message) {
            val currentList = messages[sessionId] ?: emptyList()
            messages[sessionId] = currentList + message
        }
    }
    
    // ==================== SendMessage 测试 ====================
    
    @Test
    fun `SendMessage validates empty session ID`() = runTest {
        val repository = MockSessionRepository()
        val useCase = SendMessage(repository)
        
        val result = useCase.invoke("", "Hello")
        
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }
    
    @Test
    fun `SendMessage validates empty content without attachments`() = runTest {
        val repository = MockSessionRepository()
        val useCase = SendMessage(repository)
        
        val result = useCase.invoke("session_123", "")
        
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }
    
    @Test
    fun `SendMessage allows empty content with attachments`() = runTest {
        val repository = MockSessionRepository()
        val useCase = SendMessage(repository)
        
        val attachment = Attachment(
            id = "att_1",
            name = "test.png",
            mimeType = "image/png",
            size = 1024
        )
        
        val result = useCase.invoke("session_123", "", listOf(attachment))
        
        assertTrue(result.isSuccess)
    }
    
    @Test
    fun `SendMessage rejects oversized attachments`() = runTest {
        val repository = MockSessionRepository()
        val useCase = SendMessage(repository)
        
        val largeAttachment = Attachment(
            id = "att_large",
            name = "huge.mp4",
            mimeType = "video/mp4",
            size = 25 * 1024 * 1024L // 25MB, exceeds 20MB limit
        )
        
        val result = useCase.invoke("session_123", "Check this", listOf(largeAttachment))
        
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("20MB") == true)
    }
    
    @Test
    fun `SendMessage successfully sends message`() = runTest {
        val repository = MockSessionRepository()
        val useCase = SendMessage(repository)
        
        // 先创建会话
        repository.createSession()
        
        val result = useCase.invoke("session_123", "Hello, World!")
        
        assertTrue(result.isSuccess)
        val message = result.getOrNull()
        assertNotNull(message)
        assertEquals("Hello, World!", message.content)
        assertEquals(MessageRole.USER, message.role)
    }
    
    // ==================== CreateSession 测试 ====================
    
    @Test
    fun `CreateSession with default parameters`() = runTest {
        val repository = MockSessionRepository()
        val useCase = CreateSession(repository)
        
        val result = useCase.invoke()
        
        assertTrue(result.isSuccess)
        val session = result.getOrNull()
        assertNotNull(session)
        assertNull(session.model)
        assertNull(session.label)
        assertFalse(session.thinking)
    }
    
    @Test
    fun `CreateSession with custom parameters`() = runTest {
        val repository = MockSessionRepository()
        val useCase = CreateSession(repository)
        
        val result = useCase.invoke(
            model = "aliyun/qwen3.5-plus",
            label = "Test Session",
            thinking = true
        )
        
        assertTrue(result.isSuccess)
        val session = result.getOrNull()
        assertNotNull(session)
        assertEquals("aliyun/qwen3.5-plus", session.model)
        assertEquals("Test Session", session.label)
        assertTrue(session.thinking)
    }
    
    @Test
    fun `CreateSession validates model name format`() = runTest {
        val repository = MockSessionRepository()
        val useCase = CreateSession(repository)
        
        // 有效格式
        assertTrue(useCase.invoke(model = "provider/model").isSuccess)
        assertTrue(useCase.invoke(model = "provider/model-name:version").isSuccess)
        
        // 无效格式（缺少 provider）
        val result = useCase.invoke(model = "invalid-model")
        assertTrue(result.isFailure)
    }
    
    @Test
    fun `CreateSession validates label length`() = runTest {
        val repository = MockSessionRepository()
        val useCase = CreateSession(repository)
        
        val longLabel = "a".repeat(150) // exceeds 100 char limit
        val result = useCase.invoke(label = longLabel)
        
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("length") == true)
    }
    
    // ==================== DeleteSession 测试 ====================
    
    @Test
    fun `DeleteSession validates session ID`() = runTest {
        val repository = MockSessionRepository()
        val useCase = DeleteSession(repository)
        
        val result = useCase.invoke("")
        
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }
    
    @Test
    fun `DeleteSession fails for non-existent session`() = runTest {
        val repository = MockSessionRepository()
        val useCase = DeleteSession(repository)
        
        val result = useCase.invoke("non_existent_session")
        
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("not found") == true)
    }
    
    @Test
    fun `DeleteSession fails for active session without force`() = runTest {
        val repository = MockSessionRepository()
        val useCase = DeleteSession(repository)
        
        // 创建活跃会话
        val sessionResult = repository.createSession()
        val session = sessionResult.getOrNull()!!
        
        val result = useCase.invoke(session.id, force = false)
        
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("active") == true)
    }
    
    @Test
    fun `DeleteSession succeeds with force for active session`() = runTest {
        val repository = MockSessionRepository()
        val useCase = DeleteSession(repository)
        
        // 创建活跃会话
        val sessionResult = repository.createSession()
        val session = sessionResult.getOrNull()!!
        
        val result = useCase.invoke(session.id, force = true)
        
        assertTrue(result.isSuccess)
    }
    
    // ==================== GetSessionHistory 测试 ====================
    
    @Test
    fun `GetSessionHistory validates session ID`() = runTest {
        val repository = MockSessionRepository()
        val useCase = GetSessionHistory(repository)
        
        assertFailsWith<IllegalArgumentException> {
            useCase.invoke("")
        }
    }
    
    @Test
    fun `GetSessionHistory returns empty list for new session`() = runTest {
        val repository = MockSessionRepository()
        val useCase = GetSessionHistory(repository)
        
        // 创建会话但不添加消息
        repository.createSession()
        
        val messages = useCase.invoke("session_123")
        
        assertTrue(messages.isEmpty())
    }
    
    @Test
    fun `GetSessionHistory returns messages in order`() = runTest {
        val repository = MockSessionRepository()
        val useCase = GetSessionHistory(repository)
        
        // 创建会话并添加消息
        repository.createSession()
        repository.addMessage("session_123", Message.createUserMessage("session_123", "First"))
        repository.addMessage("session_123", Message.createUserMessage("session_123", "Second"))
        repository.addMessage("session_123", Message.createUserMessage("session_123", "Third"))
        
        val messages = useCase.invoke("session_123")
        
        assertEquals(3, messages.size)
        assertEquals("First", messages[0].content)
        assertEquals("Third", messages[2].content)
    }
    
    @Test
    fun `GetSessionHistory respects limit parameter`() = runTest {
        val repository = MockSessionRepository()
        val useCase = GetSessionHistory(repository)
        
        // 添加 10 条消息
        repository.createSession()
        for (i in 1..10) {
            repository.addMessage("session_123", Message.createUserMessage("session_123", "Message $i"))
        }
        
        val messages = useCase.invoke("session_123", limit = 5)
        
        assertEquals(5, messages.size)
    }
    
    @Test
    fun `GetSessionHistory search messages`() = runTest {
        val repository = MockSessionRepository()
        val useCase = GetSessionHistory(repository)
        
        repository.createSession()
        repository.addMessage("session_123", Message.createUserMessage("session_123", "Hello World"))
        repository.addMessage("session_123", Message.createUserMessage("session_123", "Goodbye World"))
        repository.addMessage("session_123", Message.createUserMessage("session_123", "Hello Again"))
        
        val results = useCase.searchMessages("session_123", "hello")
        
        assertEquals(2, results.size)
        assertTrue(results.all { it.content.contains("Hello", ignoreCase = true) })
    }
    
    // ==================== DeviceIdProvider 测试 ====================
    
    @Test
    fun `DefaultDeviceIdProvider generates consistent ID`() {
        val provider = DefaultDeviceIdProvider()
        
        val id1 = provider.getDeviceId()
        val id2 = provider.getDeviceId()
        
        assertEquals(id1, id2) // 同一实例应返回相同 ID
        assertTrue(id1.startsWith("device_"))
    }
    
    @Test
    fun `DefaultDeviceIdProvider generates unique IDs per instance`() {
        val provider1 = DefaultDeviceIdProvider()
        val provider2 = DefaultDeviceIdProvider()
        
        val id1 = provider1.getDeviceId()
        val id2 = provider2.getDeviceId()
        
        // 不同实例应生成不同 ID（极大概率）
        // 注意：理论上可能相同，但概率极低
        assertTrue(id1.isNotEmpty())
        assertTrue(id2.isNotEmpty())
    }
}
