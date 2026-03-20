package com.openclaw.clawchat.data.local

import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * MessageDao 集成测试
 * 
 * 使用 Room in-memory database 进行测试
 * 测试覆盖：
 * - CRUD 操作
 * - 查询操作
 * - 状态更新
 */
class MessageDaoTest {

    private lateinit var database: ClawChatDatabase
    private lateinit var messageDao: MessageDao

    @Before
    fun setup() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            ClawChatDatabase::class.java
        ).allowMainThreadQueries().build()
        
        messageDao = database.messageDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    // ==================== 插入测试 ====================

    @Test
    fun `insert should add message to database`() = runTest {
        // Given
        val message = MessageEntity(
            id = 0, // Auto-generated
            sessionId = "session-1",
            role = MessageRole.USER,
            content = "Hello, World!",
            timestamp = System.currentTimeMillis(),
            status = MessageStatus.PENDING
        )

        // When
        val id = messageDao.insert(message)

        // Then
        assertTrue(id > 0)
    }

    @Test
    fun `insert should auto-generate id`() = runTest {
        // Given
        val message1 = MessageEntity(
            id = 0,
            sessionId = "session-1",
            role = MessageRole.USER,
            content = "Message 1",
            timestamp = System.currentTimeMillis(),
            status = MessageStatus.PENDING
        )
        val message2 = message1.copy(content = "Message 2")

        // When
        val id1 = messageDao.insert(message1)
        val id2 = messageDao.insert(message2)

        // Then
        assertTrue(id2 > id1)
    }

    @Test
    fun `insertAll should add multiple messages`() = runTest {
        // Given
        val messages = listOf(
            MessageEntity(
                id = 0,
                sessionId = "session-1",
                role = MessageRole.USER,
                content = "Message 1",
                timestamp = System.currentTimeMillis(),
                status = MessageStatus.PENDING
            ),
            MessageEntity(
                id = 0,
                sessionId = "session-1",
                role = MessageRole.ASSISTANT,
                content = "Message 2",
                timestamp = System.currentTimeMillis() + 100,
                status = MessageStatus.DELIVERED
            )
        )

        // When
        messageDao.insertAll(messages)

        // Then
        val count = messageDao.getCount("session-1")
        assertEquals(2, count)
    }

    // ==================== 查询测试 ====================

    @Test
    fun `getMessagesBySession should return messages in chronological order`() = runTest {
        // Given
        val now = System.currentTimeMillis()
        val messages = listOf(
            MessageEntity(
                id = 0,
                sessionId = "session-1",
                role = MessageRole.USER,
                content = "First",
                timestamp = now,
                status = MessageStatus.DELIVERED
            ),
            MessageEntity(
                id = 0,
                sessionId = "session-1",
                role = MessageRole.ASSISTANT,
                content = "Second",
                timestamp = now + 1000,
                status = MessageStatus.DELIVERED
            ),
            MessageEntity(
                id = 0,
                sessionId = "session-1",
                role = MessageRole.USER,
                content = "Third",
                timestamp = now + 2000,
                status = MessageStatus.DELIVERED
            )
        )
        messageDao.insertAll(messages)

        // When
        val result = messageDao.getMessagesBySession("session-1").first()

        // Then
        assertEquals(3, result.size)
        assertEquals("First", result[0].content)
        assertEquals("Second", result[1].content)
        assertEquals("Third", result[2].content)
    }

    @Test
    fun `getMessagesBySession should return empty list for non-existent session`() = runTest {
        // When
        val result = messageDao.getMessagesBySession("non-existent").first()

        // Then
        assertTrue(result.isEmpty())
    }

    @Test
    fun `getLatestMessages should return limited messages`() = runTest {
        // Given
        val now = System.currentTimeMillis()
        val messages = (1..10).map { i ->
            MessageEntity(
                id = 0,
                sessionId = "session-1",
                role = MessageRole.USER,
                content = "Message $i",
                timestamp = now + i * 1000,
                status = MessageStatus.DELIVERED
            )
        }
        messageDao.insertAll(messages)

        // When
        val result = messageDao.getLatestMessages("session-1", 5)

        // Then
        assertEquals(5, result.size)
        // Should be most recent (descending order)
        assertEquals("Message 10", result[0].content)
    }

    @Test
    fun `getById should return correct message`() = runTest {
        // Given
        val message = MessageEntity(
            id = 0,
            sessionId = "session-1",
            role = MessageRole.USER,
            content = "Test Message",
            timestamp = System.currentTimeMillis(),
            status = MessageStatus.DELIVERED
        )
        val id = messageDao.insert(message)

        // When
        val result = messageDao.getById(id)

        // Then
        assertNotNull(result)
        assertEquals("Test Message", result.content)
    }

    @Test
    fun `getById should return null for non-existent id`() = runTest {
        // When
        val result = messageDao.getById(99999)

        // Then
        assertNull(result)
    }

    @Test
    fun `getCount should return correct count for session`() = runTest {
        // Given
        val messages = listOf(
            MessageEntity(
                id = 0,
                sessionId = "session-1",
                role = MessageRole.USER,
                content = "Message 1",
                timestamp = System.currentTimeMillis(),
                status = MessageStatus.DELIVERED
            ),
            MessageEntity(
                id = 0,
                sessionId = "session-1",
                role = MessageRole.ASSISTANT,
                content = "Message 2",
                timestamp = System.currentTimeMillis(),
                status = MessageStatus.DELIVERED
            ),
            MessageEntity(
                id = 0,
                sessionId = "session-2",
                role = MessageRole.USER,
                content = "Message 3",
                timestamp = System.currentTimeMillis(),
                status = MessageStatus.DELIVERED
            )
        )
        messageDao.insertAll(messages)

        // When
        val count1 = messageDao.getCount("session-1")
        val count2 = messageDao.getCount("session-2")

        // Then
        assertEquals(2, count1)
        assertEquals(1, count2)
    }

    @Test
    fun `getAllSessionIds should return unique session ids`() = runTest {
        // Given
        val messages = listOf(
            MessageEntity(
                id = 0,
                sessionId = "session-1",
                role = MessageRole.USER,
                content = "Message",
                timestamp = System.currentTimeMillis(),
                status = MessageStatus.DELIVERED
            ),
            MessageEntity(
                id = 0,
                sessionId = "session-2",
                role = MessageRole.USER,
                content = "Message",
                timestamp = System.currentTimeMillis() + 1000,
                status = MessageStatus.DELIVERED
            ),
            MessageEntity(
                id = 0,
                sessionId = "session-1",
                role = MessageRole.USER,
                content = "Message",
                timestamp = System.currentTimeMillis() + 2000,
                status = MessageStatus.DELIVERED
            )
        )
        messageDao.insertAll(messages)

        // When
        val result = messageDao.getAllSessionIds().first()

        // Then
        assertEquals(2, result.size)
        assertTrue(result.contains("session-1"))
        assertTrue(result.contains("session-2"))
    }

    @Test
    fun `searchMessages should find matching content`() = runTest {
        // Given
        val messages = listOf(
            MessageEntity(
                id = 0,
                sessionId = "session-1",
                role = MessageRole.USER,
                content = "Hello, how are you?",
                timestamp = System.currentTimeMillis(),
                status = MessageStatus.DELIVERED
            ),
            MessageEntity(
                id = 0,
                sessionId = "session-1",
                role = MessageRole.ASSISTANT,
                content = "I'm doing well, thanks!",
                timestamp = System.currentTimeMillis(),
                status = MessageStatus.DELIVERED
            )
        )
        messageDao.insertAll(messages)

        // When
        val result = messageDao.searchMessages("%Hello%", 10)

        // Then
        assertEquals(1, result.size)
        assertTrue(result.first().content.contains("Hello"))
    }

    // ==================== 更新测试 ====================

    @Test
    fun `update should modify existing message`() = runTest {
        // Given
        val message = MessageEntity(
            id = 0,
            sessionId = "session-1",
            role = MessageRole.USER,
            content = "Original content",
            timestamp = System.currentTimeMillis(),
            status = MessageStatus.PENDING
        )
        val id = messageDao.insert(message)

        // When
        val updated = messageDao.getById(id)?.copy(
            content = "Updated content",
            status = MessageStatus.DELIVERED
        )
        if (updated != null) {
            messageDao.update(updated)
        }

        // Then
        val result = messageDao.getById(id)
        assertEquals("Updated content", result?.content)
        assertEquals(MessageStatus.DELIVERED, result?.status)
    }

    @Test
    fun `updateStatusById should update message status`() = runTest {
        // Given
        val message = MessageEntity(
            id = 0,
            sessionId = "session-1",
            role = MessageRole.USER,
            content = "Test",
            timestamp = System.currentTimeMillis(),
            status = MessageStatus.PENDING
        )
        val id = messageDao.insert(message)

        // When
        messageDao.updateStatusById(id, MessageStatus.DELIVERED.name)

        // Then
        val result = messageDao.getById(id)
        assertEquals(MessageStatus.DELIVERED, result?.status)
    }

    // ==================== 删除测试 ====================

    @Test
    fun `delete should remove message`() = runTest {
        // Given
        val message = MessageEntity(
            id = 0,
            sessionId = "session-1",
            role = MessageRole.USER,
            content = "Test",
            timestamp = System.currentTimeMillis(),
            status = MessageStatus.DELIVERED
        )
        val id = messageDao.insert(message)
        val inserted = messageDao.getById(id)
        assertNotNull(inserted)

        // When
        messageDao.delete(inserted)

        // Then
        val result = messageDao.getById(id)
        assertNull(result)
    }

    @Test
    fun `deleteBySession should remove all messages for session`() = runTest {
        // Given
        val messages = listOf(
            MessageEntity(
                id = 0,
                sessionId = "session-1",
                role = MessageRole.USER,
                content = "Message 1",
                timestamp = System.currentTimeMillis(),
                status = MessageStatus.DELIVERED
            ),
            MessageEntity(
                id = 0,
                sessionId = "session-1",
                role = MessageRole.ASSISTANT,
                content = "Message 2",
                timestamp = System.currentTimeMillis(),
                status = MessageStatus.DELIVERED
            ),
            MessageEntity(
                id = 0,
                sessionId = "session-2",
                role = MessageRole.USER,
                content = "Message 3",
                timestamp = System.currentTimeMillis(),
                status = MessageStatus.DELIVERED
            )
        )
        messageDao.insertAll(messages)

        // When
        messageDao.deleteBySession("session-1")

        // Then
        val count1 = messageDao.getCount("session-1")
        val count2 = messageDao.getCount("session-2")
        assertEquals(0, count1)
        assertEquals(1, count2)
    }

    @Test
    fun `deleteOlderThan should remove old messages`() = runTest {
        // Given
        val now = System.currentTimeMillis()
        val oldTimestamp = now - 7 * 24 * 60 * 60 * 1000 // 7 days ago
        val messages = listOf(
            MessageEntity(
                id = 0,
                sessionId = "session-1",
                role = MessageRole.USER,
                content = "Old message",
                timestamp = oldTimestamp,
                status = MessageStatus.DELIVERED
            ),
            MessageEntity(
                id = 0,
                sessionId = "session-1",
                role = MessageRole.USER,
                content = "New message",
                timestamp = now,
                status = MessageStatus.DELIVERED
            )
        )
        messageDao.insertAll(messages)

        // When
        val deletedCount = messageDao.deleteOlderThan(now - 1 * 24 * 60 * 60 * 1000)

        // Then
        assertEquals(1, deletedCount)
        val remaining = messageDao.getMessagesBySession("session-1").first()
        assertEquals(1, remaining.size)
        assertEquals("New message", remaining.first().content)
    }
}