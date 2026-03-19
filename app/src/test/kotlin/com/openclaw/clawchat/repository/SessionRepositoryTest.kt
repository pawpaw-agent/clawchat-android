package com.openclaw.clawchat.repository

import com.openclaw.clawchat.data.local.SessionDao
import com.openclaw.clawchat.data.local.SessionEntity
import com.openclaw.clawchat.domain.model.Session
import com.openclaw.clawchat.domain.model.SessionStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * SessionRepository 单元测试
 * 
 * 测试覆盖率目标：≥80%
 */
@DisplayName("SessionRepository 测试")
class SessionRepositoryTest {

    private lateinit var sessionDao: SessionDao
    private lateinit var sessionRepository: SessionRepository

    @BeforeEach
    fun setup() {
        sessionDao = mockk()
        sessionRepository = SessionRepository(sessionDao)
    }

    @Nested
    @DisplayName("创建会话测试")
    inner class CreateSessionTests {

        @Test
        @DisplayName("添加会话成功")
        fun `addSession adds session successfully`() = runTest {
            // Given
            val session = Session(
                id = "session-123",
                label = "Test Session",
                model = "qwen3.5-plus",
                status = SessionStatus.RUNNING,
                lastActivityAt = 1234567890L,
                messageCount = 0
            )

            coEvery { sessionDao.insert(any()) } returns Unit

            // When
            sessionRepository.addSession(session)

            // Then
            coVerify(exactly = 1) {
                sessionDao.insert(withArg { entity ->
                    assertEquals(session.id, entity.id)
                    assertEquals(session.label, entity.title)
                    assertEquals(session.messageCount, entity.messageCount)
                })
            }
        }

        @Test
        @DisplayName("添加会话后更新内存缓存")
        fun `addSession updates in-memory cache`() = runTest {
            // Given
            val session = Session(
                id = "session-123",
                label = "Test Session",
                model = "qwen3.5-plus",
                status = SessionStatus.RUNNING,
                lastActivityAt = 1234567890L
            )

            coEvery { sessionDao.insert(any()) } returns Unit

            // When
            sessionRepository.addSession(session)

            // Then
            val sessions = sessionRepository.sessions.value
            assertTrue(sessions.isNotEmpty())
            assertEquals(session.id, sessions.first().id)
        }

        @Test
        @DisplayName("添加会话后设置为当前会话")
        fun `addSession sets session as current`() = runTest {
            // Given
            val session = Session(
                id = "session-123",
                label = "Test Session",
                model = "qwen3.5-plus",
                status = SessionStatus.RUNNING,
                lastActivityAt = 1234567890L
            )

            coEvery { sessionDao.insert(any()) } returns Unit

            // When
            sessionRepository.addSession(session)

            // Then
            val currentSession = sessionRepository.currentSession.value
            assertNotNull(currentSession)
            assertEquals(session.id, currentSession?.id)
        }

        @Test
        @DisplayName("添加会话未命名时使用默认标题")
        fun `addSession uses default title when label is null`() = runTest {
            // Given
            val session = Session(
                id = "session-123",
                label = null,
                model = "qwen3.5-plus",
                status = SessionStatus.RUNNING,
                lastActivityAt = 1234567890L
            )

            coEvery { sessionDao.insert(any()) } returns Unit

            // When
            sessionRepository.addSession(session)

            // Then
            coVerify {
                sessionDao.insert(withArg { entity ->
                    assertEquals("未命名会话", entity.title)
                })
            }
        }
    }

    @Nested
    @DisplayName("获取会话测试")
    inner class GetSessionTests {

        @Test
        @DisplayName("获取会话返回正确会话")
        fun `getSession returns correct session`() = runTest {
            // Given
            val session = Session(
                id = "session-123",
                label = "Test Session",
                model = "qwen3.5-plus",
                status = SessionStatus.RUNNING,
                lastActivityAt = 1234567890L
            )

            sessionRepository.addSession(session)

            // When
            val result = sessionRepository.getSession("session-123")

            // Then
            assertNotNull(result)
            assertEquals(session.id, result?.id)
            assertEquals(session.label, result?.label)
        }

        @Test
        @DisplayName("获取不存在的会话返回 null")
        fun `getSession returns null for non-existent session`() = runTest {
            // When
            val result = sessionRepository.getSession("non-existent")

            // Then
            assertNull(result)
        }

        @Test
        @DisplayName("观察会话返回 Flow")
        fun `observeSessions returns flow of sessions`() = runTest {
            // Given
            val entities = listOf(
                SessionEntity(
                    id = "session-123",
                    title = "Test Session",
                    createdAt = 1234567890L,
                    updatedAt = 1234567890L,
                    isActive = true
                )
            )

            every { sessionDao.getActiveSessions() } returns flowOf(entities)

            // When
            val flow = sessionRepository.observeSessions()

            // Then
            assertNotNull(flow)
        }

        @Test
        @DisplayName("加载会话从数据库")
        fun `loadSessions loads from database`() = runTest {
            // Given
            val entities = listOf(
                SessionEntity(
                    id = "session-123",
                    title = "Test Session",
                    createdAt = 1234567890L,
                    updatedAt = 1234567890L,
                    isActive = true,
                    messageCount = 5
                )
            )

            coEvery { sessionDao.getActiveSessions().firstOrNull() } returns entities

            // When
            sessionRepository.loadSessions()

            // Then
            val sessions = sessionRepository.sessions.value
            assertEquals(1, sessions.size)
            assertEquals("session-123", sessions.first().id)
        }
    }

    @Nested
    @DisplayName("更新会话测试")
    inner class UpdateSessionTests {

        @Test
        @DisplayName("更新会话成功")
        fun `updateSession updates session successfully`() = runTest {
            // Given
            val session = Session(
                id = "session-123",
                label = "Original Title",
                model = "qwen3.5-plus",
                status = SessionStatus.RUNNING,
                lastActivityAt = 1234567890L
            )

            sessionRepository.addSession(session)

            coEvery { sessionDao.update(any()) } returns Unit

            // When
            sessionRepository.updateSession("session-123") { it.copy(label = "Updated Title") }

            // Then
            coVerify(exactly = 1) { sessionDao.update(any()) }
            
            val updatedSession = sessionRepository.getSession("session-123")
            assertEquals("Updated Title", updatedSession?.label)
        }

        @Test
        @DisplayName("更新不存在的会话不执行操作")
        fun `updateSession does nothing for non-existent session`() = runTest {
            // Given
            coEvery { sessionDao.update(any()) } returns Unit

            // When
            sessionRepository.updateSession("non-existent") { it.copy(label = "New Title") }

            // Then
            coVerify(exactly = 0) { sessionDao.update(any()) }
        }

        @Test
        @DisplayName("更新当前会话同步更新")
        fun `updateSession updates current session if matching`() = runTest {
            // Given
            val session = Session(
                id = "session-123",
                label = "Original",
                model = "qwen3.5-plus",
                status = SessionStatus.RUNNING,
                lastActivityAt = 1234567890L
            )

            sessionRepository.addSession(session)
            coEvery { sessionDao.update(any()) } returns Unit

            // When
            sessionRepository.updateSession("session-123") { it.copy(label = "Updated") }

            // Then
            val currentSession = sessionRepository.currentSession.value
            assertEquals("Updated", currentSession?.label)
        }
    }

    @Nested
    @DisplayName("删除会话测试")
    inner class DeleteSessionTests {

        @Test
        @DisplayName("删除会话成功")
        fun `deleteSession deletes session successfully`() = runTest {
            // Given
            val session = Session(
                id = "session-123",
                label = "To Delete",
                model = "qwen3.5-plus",
                status = SessionStatus.RUNNING,
                lastActivityAt = 1234567890L
            )

            sessionRepository.addSession(session)
            coEvery { sessionDao.deleteById("session-123") } returns Unit

            // When
            sessionRepository.deleteSession("session-123")

            // Then
            coVerify(exactly = 1) { sessionDao.deleteById("session-123") }
            
            val deletedSession = sessionRepository.getSession("session-123")
            assertNull(deletedSession)
        }

        @Test
        @DisplayName("删除当前会话清除当前会话")
        fun `deleteSession clears current session if deleting current`() = runTest {
            // Given
            val session = Session(
                id = "session-123",
                label = "Current",
                model = "qwen3.5-plus",
                status = SessionStatus.RUNNING,
                lastActivityAt = 1234567890L
            )

            sessionRepository.addSession(session)
            coEvery { sessionDao.deleteById("session-123") } returns Unit

            // When
            sessionRepository.deleteSession("session-123")

            // Then
            assertNull(sessionRepository.currentSession.value)
        }

        @Test
        @DisplayName("删除会话从内存缓存移除")
        fun `deleteSession removes from in-memory cache`() = runTest {
            // Given
            val session = Session(
                id = "session-123",
                label = "To Delete",
                model = "qwen3.5-plus",
                status = SessionStatus.RUNNING,
                lastActivityAt = 1234567890L
            )

            sessionRepository.addSession(session)
            coEvery { sessionDao.deleteById("session-123") } returns Unit

            // When
            sessionRepository.deleteSession("session-123")

            // Then
            val sessions = sessionRepository.sessions.value
            assertTrue(sessions.none { it.id == "session-123" })
        }
    }

    @Nested
    @DisplayName("获取所有会话测试")
    inner class GetAllSessionsTests {

        @Test
        @DisplayName("获取活跃会话")
        fun `getActiveSessions returns running sessions`() = runTest {
            // Given
            val runningSession = Session(
                id = "session-1",
                label = "Running",
                model = "qwen3.5-plus",
                status = SessionStatus.RUNNING,
                lastActivityAt = 1234567890L
            )

            val pausedSession = Session(
                id = "session-2",
                label = "Paused",
                model = "qwen3.5-plus",
                status = SessionStatus.PAUSED,
                lastActivityAt = 1234567890L
            )

            sessionRepository.addSession(runningSession)
            sessionRepository.addSession(pausedSession)

            // When
            val activeSessions = sessionRepository.getActiveSessions()

            // Then
            assertEquals(1, activeSessions.size)
            assertEquals("session-1", activeSessions.first().id)
        }

        @Test
        @DisplayName("获取已暂停会话")
        fun `getPausedSessions returns paused sessions`() = runTest {
            // Given
            val pausedSession = Session(
                id = "session-1",
                label = "Paused",
                model = "qwen3.5-plus",
                status = SessionStatus.PAUSED,
                lastActivityAt = 1234567890L
            )

            sessionRepository.addSession(pausedSession)

            // When
            val pausedSessions = sessionRepository.getPausedSessions()

            // Then
            assertEquals(1, pausedSessions.size)
            assertEquals("session-1", pausedSessions.first().id)
        }

        @Test
        @DisplayName("获取已终止会话")
        fun `getTerminatedSessions returns terminated sessions`() = runTest {
            // Given
            val terminatedSession = Session(
                id = "session-1",
                label = "Terminated",
                model = "qwen3.5-plus",
                status = SessionStatus.TERMINATED,
                lastActivityAt = 1234567890L
            )

            sessionRepository.addSession(terminatedSession)

            // When
            val terminatedSessions = sessionRepository.getTerminatedSessions()

            // Then
            assertEquals(1, terminatedSessions.size)
            assertEquals("session-1", terminatedSessions.first().id)
        }

        @Test
        @DisplayName("获取会话统计")
        fun `getSessionStats returns correct statistics`() = runTest {
            // Given
            val runningSession = Session(
                id = "session-1",
                label = "Running",
                model = "qwen3.5-plus",
                status = SessionStatus.RUNNING,
                lastActivityAt = 1234567890L,
                messageCount = 10
            )

            val pausedSession = Session(
                id = "session-2",
                label = "Paused",
                model = "qwen3.5-plus",
                status = SessionStatus.PAUSED,
                lastActivityAt = 1234567890L,
                messageCount = 5
            )

            sessionRepository.addSession(runningSession)
            sessionRepository.addSession(pausedSession)

            // When
            val stats = sessionRepository.getSessionStats()

            // Then
            assertEquals(2, stats.total)
            assertEquals(1, stats.running)
            assertEquals(1, stats.paused)
            assertEquals(0, stats.terminated)
            assertEquals(15, stats.totalMessages)
        }
    }

    @Nested
    @DisplayName("搜索会话测试")
    inner class SearchSessionTests {

        @Test
        @DisplayName("搜索会话返回匹配结果")
        fun `searchSessions returns matching sessions`() = runTest {
            // Given
            val query = "test"
            val entities = listOf(
                SessionEntity(
                    id = "session-123",
                    title = "Test Session",
                    createdAt = 1234567890L,
                    updatedAt = 1234567890L,
                    isActive = true
                )
            )

            coEvery { sessionDao.searchSessions("%$query%", 20) } returns entities

            // When
            val results = sessionRepository.searchSessions(query)

            // Then
            assertEquals(1, results.size)
            assertEquals("session-123", results.first().id)
        }

        @Test
        @DisplayName("搜索空查询返回所有会话")
        fun `searchSessions returns all sessions when query is blank`() = runTest {
            // Given
            val session = Session(
                id = "session-123",
                label = "Test",
                model = "qwen3.5-plus",
                status = SessionStatus.RUNNING,
                lastActivityAt = 1234567890L
            )

            sessionRepository.addSession(session)

            // When
            val results = sessionRepository.searchSessions("")

            // Then
            assertEquals(1, results.size)
        }
    }

    @Nested
    @DisplayName("会话管理测试")
    inner class SessionManagementTests {

        @Test
        @DisplayName("设置当前会话")
        fun `setCurrentSession sets current session`() = runTest {
            // Given
            val session = Session(
                id = "session-123",
                label = "Test",
                model = "qwen3.5-plus",
                status = SessionStatus.RUNNING,
                lastActivityAt = 1234567890L
            )

            sessionRepository.addSession(session)

            // When
            sessionRepository.setCurrentSession("session-123")

            // Then
            val currentSession = sessionRepository.currentSession.value
            assertNotNull(currentSession)
            assertEquals("session-123", currentSession?.id)
        }

        @Test
        @DisplayName("设置当前会话为 null 清除当前会话")
        fun `setCurrentSession clears current session when null`() = runTest {
            // Given
            val session = Session(
                id = "session-123",
                label = "Test",
                model = "qwen3.5-plus",
                status = SessionStatus.RUNNING,
                lastActivityAt = 1234567890L
            )

            sessionRepository.addSession(session)

            // When
            sessionRepository.setCurrentSession(null)

            // Then
            assertNull(sessionRepository.currentSession.value)
        }

        @Test
        @DisplayName("清除已终止会话")
        fun `clearTerminatedSessions removes terminated sessions`() = runTest {
            // Given
            val terminatedSession = Session(
                id = "session-1",
                label = "Terminated",
                model = "qwen3.5-plus",
                status = SessionStatus.TERMINATED,
                lastActivityAt = 1234567890L
            )

            sessionRepository.addSession(terminatedSession)
            coEvery { sessionDao.deleteById("session-1") } returns Unit

            // When
            sessionRepository.clearTerminatedSessions()

            // Then
            val sessions = sessionRepository.sessions.value
            assertTrue(sessions.none { it.status == SessionStatus.TERMINATED })
        }

        @Test
        @DisplayName("清空所有会话")
        fun `clearAllSessions removes all sessions`() = runTest {
            // Given
            val session = Session(
                id = "session-123",
                label = "Test",
                model = "qwen3.5-plus",
                status = SessionStatus.RUNNING,
                lastActivityAt = 1234567890L
            )

            sessionRepository.addSession(session)

            coEvery { sessionDao.getAllSessions().firstOrNull() } returns listOf(
                SessionEntity(
                    id = "session-123",
                    title = "Test",
                    createdAt = 1234567890L,
                    updatedAt = 1234567890L,
                    isActive = true
                )
            )
            coEvery { sessionDao.delete(any()) } returns Unit

            // When
            sessionRepository.clearAllSessions()

            // Then
            assertTrue(sessionRepository.sessions.value.isEmpty())
            assertNull(sessionRepository.currentSession.value)
        }
    }
}
