package com.openclaw.clawchat.util

import org.junit.Assert.*
import org.junit.Test

/**
 * StringExt 单元测试
 */
class StringExtTest {
    
    // ─────────────────────────────────────────────────────────────
    // 空白检查测试
    // ─────────────────────────────────────────────────────────────
    
    @Test
    fun `isNullOrBlank returns true for null`() {
        val str: String? = null
        assertTrue(str.isNullOrBlank())
    }
    
    @Test
    fun `isNullOrBlank returns true for empty string`() {
        val str: String? = ""
        assertTrue(str.isNullOrBlank())
    }
    
    @Test
    fun `isNullOrBlank returns true for whitespace only`() {
        val str: String? = "   "
        assertTrue(str.isNullOrBlank())
    }
    
    @Test
    fun `isNullOrBlank returns false for non-empty string`() {
        val str: String? = "test"
        assertFalse(str.isNullOrBlank())
    }
    
    @Test
    fun `isNotNullOrBlank returns true for non-empty string`() {
        val str: String? = "test"
        assertTrue(str.isNotNullOrBlank())
    }
    
    @Test
    fun `isNotNullOrBlank returns false for null`() {
        val str: String? = null
        assertFalse(str.isNotNullOrBlank())
    }
    
    // ─────────────────────────────────────────────────────────────
    // 截断测试
    // ─────────────────────────────────────────────────────────────
    
    @Test
    fun `truncate returns original if shorter than max`() {
        val result = "hello".truncate(10)
        assertEquals("hello", result)
    }
    
    @Test
    fun `truncate adds ellipsis if longer than max`() {
        val result = "hello world".truncate(8)
        assertEquals("hello...", result)
    }
    
    @Test
    fun `truncate uses custom ellipsis`() {
        val result = "hello world".truncate(10, "…")
        assertEquals("hello wor…", result)
    }
    
    // ─────────────────────────────────────────────────────────────
    // 协议移除测试
    // ─────────────────────────────────────────────────────────────
    
    @Test
    fun `removeProtocol removes http`() {
        val result = "http://example.com".removeProtocol()
        assertEquals("example.com", result)
    }
    
    @Test
    fun `removeProtocol removes https`() {
        val result = "https://example.com".removeProtocol()
        assertEquals("example.com", result)
    }
    
    @Test
    fun `removeProtocol removes ws`() {
        val result = "ws://example.com".removeProtocol()
        assertEquals("example.com", result)
    }
    
    @Test
    fun `removeProtocol removes wss`() {
        val result = "wss://example.com".removeProtocol()
        assertEquals("example.com", result)
    }
    
    @Test
    fun `removeProtocol returns original if no protocol`() {
        val result = "example.com".removeProtocol()
        assertEquals("example.com", result)
    }
    
    // ─────────────────────────────────────────────────────────────
    // 协议添加测试
    // ─────────────────────────────────────────────────────────────
    
    @Test
    fun `addProtocol adds https by default`() {
        val result = "example.com".addProtocol()
        assertEquals("https://example.com", result)
    }
    
    @Test
    fun `addProtocol adds custom protocol`() {
        val result = "example.com".addProtocol("http")
        assertEquals("http://example.com", result)
    }
    
    @Test
    fun `addProtocol does not duplicate http`() {
        val result = "http://example.com".addProtocol()
        assertEquals("http://example.com", result)
    }
    
    @Test
    fun `addProtocol does not duplicate https`() {
        val result = "https://example.com".addProtocol()
        assertEquals("https://example.com", result)
    }
    
    // ─────────────────────────────────────────────────────────────
    // Base64 提取测试
    // ─────────────────────────────────────────────────────────────
    
    @Test
    fun `extractBase64 extracts from data URL`() {
        val result = "data:image/png;base64,iVBORw0KGgo=".extractBase64()
        assertEquals("iVBORw0KGgo=", result)
    }
    
    @Test
    fun `extractBase64 returns original if no comma`() {
        val result = "iVBORw0KGgo=".extractBase64()
        assertEquals("iVBORw0KGgo=", result)
    }
    
    // ─────────────────────────────────────────────────────────────
    // URL 验证测试
    // ─────────────────────────────────────────────────────────────
    
    @Test
    fun `isValidUrl returns true for http`() {
        assertTrue("http://example.com".isValidUrl())
    }
    
    @Test
    fun `isValidUrl returns true for https`() {
        assertTrue("https://example.com".isValidUrl())
    }
    
    @Test
    fun `isValidUrl returns true for ws`() {
        assertTrue("ws://example.com".isValidUrl())
    }
    
    @Test
    fun `isValidUrl returns true for wss`() {
        assertTrue("wss://example.com".isValidUrl())
    }
    
    @Test
    fun `isValidUrl returns false for invalid`() {
        assertFalse("example.com".isValidUrl())
    }
    
    // ─────────────────────────────────────────────────────────────
    // 首字母大写测试
    // ─────────────────────────────────────────────────────────────
    
    @Test
    fun `capitalize capitalizes first letter`() {
        val result = "hello".capitalize()
        assertEquals("Hello", result)
    }
    
    @Test
    fun `capitalize does not change already capitalized`() {
        val result = "Hello".capitalize()
        assertEquals("Hello", result)
    }
    
    @Test
    fun `capitalize handles empty string`() {
        val result = "".capitalize()
        assertEquals("", result)
    }
    
    // ─────────────────────────────────────────────────────────────
    // 字数统计测试
    // ─────────────────────────────────────────────────────────────
    
    @Test
    fun `countCharacters counts Chinese characters`() {
        val result = "你好世界".countCharacters()
        assertEquals(4, result)
    }
    
    @Test
    fun `countCharacters counts English words`() {
        val result = "hello world test".countCharacters()
        assertEquals(3, result)
    }
    
    @Test
    fun `countCharacters counts mixed content`() {
        val result = "你好hello世界world".countCharacters()
        assertEquals(6, result)  // 4 Chinese + 2 English words
    }
    
    @Test
    fun `countCharacters returns zero for empty string`() {
        val result = "".countCharacters()
        assertEquals(0, result)
    }
    
    @Test
    fun `countCharacters returns zero for blank string`() {
        val result = "   ".countCharacters()
        assertEquals(0, result)
    }
}