package com.openclaw.clawchat.util

import android.content.ContentResolver
import android.content.Context
import android.database.MatrixCursor
import android.net.Uri
import android.provider.OpenableColumns
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream

/**
 * FileUtils 单元测试
 *
 * 注意：需要 Robolectric 或 MockK 来模拟 Android 框架类
 */
class FileUtilsTest {

    private lateinit var mockContext: Context
    private lateinit var mockContentResolver: ContentResolver
    private lateinit var mockUri: Uri

    @Before
    fun setUp() {
        mockContext = mockk(relaxed = true)
        mockContentResolver = mockk(relaxed = true)
        mockUri = mockk(relaxed = true)

        every { mockContext.contentResolver } returns mockContentResolver
    }

    @After
    fun tearDown() {
        // Cleanup mocks
    }

    // ─────────────────────────────────────────────────────────────
    // getFileNameFromUri 测试
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `getFileNameFromUri returns name from content URI`() {
        val cursor = MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME)).apply {
            addRow(arrayOf("test_file.jpg"))
        }

        every { mockUri.scheme } returns "content"
        every { mockContentResolver.query(mockUri, null, null, null, null) } returns cursor

        val result = FileUtils.getFileNameFromUri(mockContext, mockUri)

        assertEquals("test_file.jpg", result)
    }

    @Test
    fun `getFileNameFromUri returns last path segment for file URI`() {
        every { mockUri.scheme } returns "file"
        every { mockUri.lastPathSegment } returns "document.pdf"

        val result = FileUtils.getFileNameFromUri(mockContext, mockUri)

        assertEquals("document.pdf", result)
    }

    @Test
    fun `getFileNameFromUri returns null for unknown scheme`() {
        every { mockUri.scheme } returns "unknown"

        val result = FileUtils.getFileNameFromUri(mockContext, mockUri)

        assertNull(result)
    }

    @Test
    fun `getFileNameFromUri returns null when cursor is null`() {
        every { mockUri.scheme } returns "content"
        every { mockContentResolver.query(mockUri, null, null, null, null) } returns null

        val result = FileUtils.getFileNameFromUri(mockContext, mockUri)

        assertNull(result)
    }

    // ─────────────────────────────────────────────────────────────
    // uriToBase64 测试
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `uriToBase64 converts URI content to base64`() {
        val testContent = "Hello, World!"
        val inputStream = ByteArrayInputStream(testContent.toByteArray())

        every { mockContentResolver.openInputStream(mockUri) } returns inputStream

        val result = FileUtils.uriToBase64(mockContext, mockUri)

        assertNotNull(result)
        // Base64 encoded content should not be empty
        assertTrue(result!!.isNotEmpty())
    }

    @Test
    fun `uriToBase64 returns null when input stream is null`() {
        every { mockContentResolver.openInputStream(mockUri) } returns null

        val result = FileUtils.uriToBase64(mockContext, mockUri)

        assertNull(result)
    }

    @Test
    fun `uriToBase64 returns null on exception`() {
        every { mockContentResolver.openInputStream(mockUri) } throws SecurityException("Access denied")

        val result = FileUtils.uriToBase64(mockContext, mockUri)

        assertNull(result)
    }

    // ─────────────────────────────────────────────────────────────
    // getFileSize 测试
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `getFileSize returns size from content resolver`() {
        val expectedSize = 1024L
        val cursor = MatrixCursor(arrayOf(OpenableColumns.SIZE)).apply {
            addRow(arrayOf(expectedSize))
        }

        every { mockContentResolver.query(mockUri, null, null, null, null) } returns cursor

        val result = FileUtils.getFileSize(mockContext, mockUri)

        assertEquals(expectedSize, result)
    }

    @Test
    fun `getFileSize returns null when cursor is null`() {
        every { mockContentResolver.query(mockUri, null, null, null, null) } returns null

        val result = FileUtils.getFileSize(mockContext, mockUri)

        assertNull(result)
    }

    @Test
    fun `getFileSize returns null on exception`() {
        every { mockContentResolver.query(mockUri, null, null, null, null) } throws SecurityException("Access denied")

        val result = FileUtils.getFileSize(mockContext, mockUri)

        assertNull(result)
    }
}