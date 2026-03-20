package com.openclaw.clawchat.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * SessionDao 集成测试
 * 
 * 使用 Room in-memory database 进行测试
 * 测试覆盖：
 * - CRUD 操作
 * - 查询操作
 * - Flow 观察
 */
class SessionDaoTest {

    private lateinit var database: ClawChatDatabase
    private lateinit var sessionDao: SessionDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            ClawChatDatabase::class.java
        ).allowMainThreadQueries().build()
        
        sessionDao = database.sessionDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    // ==================== 插入测试 ====================

    @Test
    fun `insert should add session to database`() = runTest {
        // Given
        val session = SessionEntity(
            id = "session-1",
            title = "Test Session",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            isActive = true
        )

        // When
        sessionDao.insert(session)

        // Then
        val result = sessionDao.getSession("session-1")
        assertNotNull(result)
        assertEquals("Test Session", result.title)
    }

    @Test
    fun `insert should replace existing session with same id`() = runTest {
        // Given
        val session1 = SessionEntity(
            id = "session-1",
            title = "Original Title",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            isActive = true
        )
        val session2 = session1.copy(title = "Updated Title")

        // When
        sessionDao.insert(session1)
        sessionDao.insert(session2)

        // Then
        val result = sessionDao.getSession("session-1")
        assertEquals("Updated Title", result?.title)
    }

    @Test
    fun `insertAll should add multiple sessions`() = runTest {
        // Given
        val sessions = listOf(
            SessionEntity(
                id = "session-1",
                title = "Session 1",
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                isActive = true
            ),
            SessionEntity(
                id = "session-2",
                title = "Session 2",
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                isActive = true
            )
        )

        // When
        sessionDao.insertAll(sessions)

        // Then
        val count = sessionDao.getCount()
        assertEquals(2, count)
    }

    // ==================== 查询测试 ====================

    @Test
    fun `getSession should return null for non-existent session`() = runTest {
        // When
        val result = sessionDao.getSession("non-existent")

        // Then
        assertNull(result)
    }

    @Test
    fun `getAllSessions should return all sessions ordered by updatedAt`() = runTest {
        // Given
        val now = System.currentTimeMillis()
        val sessions = listOf(
            SessionEntity(
                id = "session-1",
                title = "Old Session",
                createdAt = now - 10000,
                updatedAt = now - 5000,
                isActive = true
            ),
            SessionEntity(
                id = "session-2",
                title = "New Session",
                createdAt = now - 10000,
                updatedAt = now,
                isActive = true
            )
        )
        sessionDao.insertAll(sessions)

        // When
        val result = sessionDao.getAllSessions().first()

        // Then
        assertEquals(2, result.size)
        assertEquals("New Session", result.first().title) // Most recent first
    }

    @Test
    fun `getActiveSessions should return only active sessions`() = runTest {
        // Given
        val sessions = listOf(
            SessionEntity(
                id = "session-1",
                title = "Active Session",
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                isActive = true
            ),
            SessionEntity(
                id = "session-2",
                title = "Inactive Session",
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                isActive = false
            )
        )
        sessionDao.insertAll(sessions)

        // When
        val result = sessionDao.getActiveSessions().first()

        // Then
        assertEquals(1, result.size)
        assertEquals("Active Session", result.first().title)
    }

    @Test
    fun `getSessionFlow should emit updates`() = runTest {
        // Given
        val session = SessionEntity(
            id = "session-1",
            title = "Test Session",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            isActive = true
        )

        // When
        val flow = sessionDao.getSessionFlow("session-1")
        sessionDao.insert(session)

        // Then
        val result = flow.first()
        assertNotNull(result)
        assertEquals("Test Session", result.title)
    }

    @Test
    fun `searchSessions should find matching sessions`() = runTest {
        // Given
        val sessions = listOf(
            SessionEntity(
                id = "session-1",
                title = "ClawChat Discussion",
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                isActive = true
            ),
            SessionEntity(
                id = "session-2",
                title = "Random Chat",
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                isActive = true
            )
        )
        sessionDao.insertAll(sessions)

        // When
        val result = sessionDao.searchSessions("%Claw%", 10)

        // Then
        assertEquals(1, result.size)
        assertEquals("ClawChat Discussion", result.first().title)
    }

    // ==================== 更新测试 ====================

    @Test
    fun `update should modify existing session`() = runTest {
        // Given
        val session = SessionEntity(
            id = "session-1",
            title = "Original Title",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            isActive = true
        )
        sessionDao.insert(session)

        // When
        val updated = session.copy(title = "Updated Title")
        sessionDao.update(updated)

        // Then
        val result = sessionDao.getSession("session-1")
        assertEquals("Updated Title", result?.title)
    }

    @Test
    fun `updateMessageCount should update message count`() = runTest {
        // Given
        val session = SessionEntity(
            id = "session-1",
            title = "Test",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            isActive = true,
            messageCount = 0
        )
        sessionDao.insert(session)

        // When
        sessionDao.updateMessageCount("session-1", 10, System.currentTimeMillis())

        // Then
        val result = sessionDao.getSession("session-1")
        assertEquals(10, result?.messageCount)
    }

    @Test
    fun `updateLastMessagePreview should update preview`() = runTest {
        // Given
        val session = SessionEntity(
            id = "session-1",
            title = "Test",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            isActive = true
        )
        sessionDao.insert(session)

        // When
        sessionDao.updateLastMessagePreview("session-1", "Hello, World!", System.currentTimeMillis())

        // Then
        val result = sessionDao.getSession("session-1")
        assertEquals("Hello, World!", result?.lastMessagePreview)
    }

    @Test
    fun `setActive should update active status`() = runTest {
        // Given
        val session = SessionEntity(
            id = "session-1",
            title = "Test",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            isActive = true
        )
        sessionDao.insert(session)

        // When
        sessionDao.setActive("session-1", false, System.currentTimeMillis())

        // Then
        val result = sessionDao.getSession("session-1")
        assertEquals(false, result?.isActive)
    }

    // ==================== 删除测试 ====================

    @Test
    fun `delete should remove session`() = runTest {
        // Given
        val session = SessionEntity(
            id = "session-1",
            title = "Test",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            isActive = true
        )
        sessionDao.insert(session)

        // When
        sessionDao.delete(session)

        // Then
        val result = sessionDao.getSession("session-1")
        assertNull(result)
    }

    @Test
    fun `deleteById should remove session by id`() = runTest {
        // Given
        val session = SessionEntity(
            id = "session-1",
            title = "Test",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            isActive = true
        )
        sessionDao.insert(session)

        // When
        sessionDao.deleteById("session-1")

        // Then
        val result = sessionDao.getSession("session-1")
        assertNull(result)
    }

    @Test
    fun `deleteInactive should remove only inactive sessions`() = runTest {
        // Given
        val sessions = listOf(
            SessionEntity(
                id = "session-1",
                title = "Active",
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                isActive = true
            ),
            SessionEntity(
                id = "session-2",
                title = "Inactive",
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                isActive = false
            )
        )
        sessionDao.insertAll(sessions)

        // When
        val deletedCount = sessionDao.deleteInactive()

        // Then
        assertEquals(1, deletedCount)
        val remaining = sessionDao.getCount()
        assertEquals(1, remaining)
    }

    // ==================== 计数测试 ====================

    @Test
    fun `getCount should return correct count`() = runTest {
        // Given
        val sessions = listOf(
            SessionEntity(
                id = "session-1",
                title = "Test 1",
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                isActive = true
            ),
            SessionEntity(
                id = "session-2",
                title = "Test 2",
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                isActive = true
            ),
            SessionEntity(
                id = "session-3",
                title = "Test 3",
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                isActive = true
            )
        )
        sessionDao.insertAll(sessions)

        // When
        val count = sessionDao.getCount()

        // Then
        assertEquals(3, count)
    }

    @Test
    fun `getCount should return 0 for empty database`() = runTest {
        // When
        val count = sessionDao.getCount()

        // Then
        assertEquals(0, count)
    }
}