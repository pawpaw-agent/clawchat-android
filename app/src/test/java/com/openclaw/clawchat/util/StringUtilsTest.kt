package com.openclaw.clawchat.util

import org.junit.Assert.*
import org.junit.Test

/**
 * StringUtils 单元测试
 */
class StringUtilsTest {
    
    // ─────────────────────────────────────────────────────────────
    // 空白检查测试
    // ─────────────────────────────────────────────────────────────
    
    @Test
    fun `isBlank returns true for null`() {
        assertTrue(StringUtils.isBlank(null))
    }
    
    @Test
    fun `isBlank returns true for empty string`() {
        assertTrue(StringUtils.isBlank(""))
    }
    
    @Test
    fun `isBlank returns true for whitespace only`() {
        assertTrue(StringUtils.isBlank("   "))
    }
    
    @Test
    fun `isBlank returns false for non-empty string`() {
        assertFalse(StringUtils.isBlank("test"))
    }
    
    @Test
    fun `isNotBlank returns true for non-empty string`() {
        assertTrue(StringUtils.isNotBlank("test"))
    }
    
    // ─────────────────────────────────────────────────────────────
    // 截断测试
    // ─────────────────────────────────────────────────────────────
    
    @Test
    fun `truncate returns original if shorter than max`() {
        val result = StringUtils.truncate("hello", 10)
        assertEquals("hello", result)
    }
    
    @Test
    fun `truncate adds ellipsis if longer than max`() {
        val result = StringUtils.truncate("hello world", 8)
        assertEquals("hello...", result)
    }
    
    @Test
    fun `truncate returns empty for null`() {
        val result = StringUtils.truncate(null, 10)
        assertEquals("", result)
    }
    
    // ─────────────────────────────────────────────────────────────
    // Base64 提取测试
    // ─────────────────────────────────────────────────────────────
    
    @Test
    fun `extractBase64FromDataUrl extracts base64`() {
        val dataUrl = "data:image/png;base64,iVBORw0KGgo="
        val result = StringUtils.extractBase64FromDataUrl(dataUrl)
        assertEquals("iVBORw0KGgo=", result)
    }
    
    @Test
    fun `extractBase64FromDataUrl returns original if no comma`() {
        val base64 = "iVBORw0KGgo="
        val result = StringUtils.extractBase64FromDataUrl(base64)
        assertEquals(base64, result)
    }
    
    // ─────────────────────────────────────────────────────────────
    // URL 验证测试
    // ─────────────────────────────────────────────────────────────
    
    @Test
    fun `isValidUrl returns true for http`() {
        assertTrue(StringUtils.isValidUrl("http://example.com"))
    }
    
    @Test
    fun `isValidUrl returns true for https`() {
        assertTrue(StringUtils.isValidUrl("https://example.com"))
    }
    
    @Test
    fun `isValidUrl returns true for ws`() {
        assertTrue(StringUtils.isValidUrl("ws://example.com"))
    }
    
    @Test
    fun `isValidUrl returns false for invalid url`() {
        assertFalse(StringUtils.isValidUrl("example.com"))
    }
    
    @Test
    fun `isValidUrl returns false for null`() {
        assertFalse(StringUtils.isValidUrl(null))
    }
    
    // ─────────────────────────────────────────────────────────────
    // 协议移除测试
    // ─────────────────────────────────────────────────────────────
    
    @Test
    fun `removeProtocol removes http`() {
        val result = StringUtils.removeProtocol("http://example.com")
        assertEquals("example.com", result)
    }
    
    @Test
    fun `removeProtocol removes https`() {
        val result = StringUtils.removeProtocol("https://example.com")
        assertEquals("example.com", result)
    }
    
    @Test
    fun `removeProtocol removes ws`() {
        val result = StringUtils.removeProtocol("ws://example.com")
        assertEquals("example.com", result)
    }
    
    // ─────────────────────────────────────────────────────────────
    // 文件大小格式化测试
    // ─────────────────────────────────────────────────────────────
    
    @Test
    fun `formatFileSize returns bytes for less than 1KB`() {
        val result = StringUtils.formatFileSize(512)
        assertEquals("512 B", result)
    }
    
    @Test
    fun `formatFileSize returns KB for less than 1MB`() {
        val result = StringUtils.formatFileSize(1024 * 512)
        assertTrue(result.contains("KB"))
    }
    
    @Test
    fun `formatFileSize returns MB for less than 1GB`() {
        val result = StringUtils.formatFileSize(1024 * 1024 * 512)
        assertTrue(result.contains("MB"))
    }
    
    @Test
    fun `formatFileSize returns GB for more than 1GB`() {
        val result = StringUtils.formatFileSize(1024L * 1024 * 1024 * 2)
        assertTrue(result.contains("GB"))
    }
}