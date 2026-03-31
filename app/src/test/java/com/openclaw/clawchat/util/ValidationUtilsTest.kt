package com.openclaw.clawchat.util

import org.junit.Assert.*
import org.junit.Test

/**
 * ValidationUtils 单元测试
 */
class ValidationUtilsTest {
    
    // ─────────────────────────────────────────────────────────────
    // 邮箱验证测试
    // ─────────────────────────────────────────────────────────────
    
    @Test
    fun `isValidEmail returns true for valid email`() {
        assertTrue(ValidationUtils.isValidEmail("test@example.com"))
    }
    
    @Test
    fun `isValidEmail returns true for email with subdomain`() {
        assertTrue(ValidationUtils.isValidEmail("user@mail.example.com"))
    }
    
    @Test
    fun `isValidEmail returns false for invalid email`() {
        assertFalse(ValidationUtils.isValidEmail("invalid"))
    }
    
    @Test
    fun `isValidEmail returns false for email without domain`() {
        assertFalse(ValidationUtils.isValidEmail("test@"))
    }
    
    @Test
    fun `isValidEmail returns false for null`() {
        assertFalse(ValidationUtils.isValidEmail(null))
    }
    
    // ─────────────────────────────────────────────────────────────
    // 手机号验证测试
    // ─────────────────────────────────────────────────────────────
    
    @Test
    fun `isValidPhone returns true for valid phone`() {
        assertTrue(ValidationUtils.isValidPhone("13812345678"))
    }
    
    @Test
    fun `isValidPhone returns true for phone starting with 15`() {
        assertTrue(ValidationUtils.isValidPhone("15912345678"))
    }
    
    @Test
    fun `isValidPhone returns false for phone starting with 10`() {
        assertFalse(ValidationUtils.isValidPhone("10112345678"))
    }
    
    @Test
    fun `isValidPhone returns false for short phone`() {
        assertFalse(ValidationUtils.isValidPhone("1381234567"))
    }
    
    @Test
    fun `isValidPhone returns false for null`() {
        assertFalse(ValidationUtils.isValidPhone(null))
    }
    
    // ─────────────────────────────────────────────────────────────
    // IP 验证测试
    // ─────────────────────────────────────────────────────────────
    
    @Test
    fun `isValidIp returns true for valid IP`() {
        assertTrue(ValidationUtils.isValidIp("192.168.1.1"))
    }
    
    @Test
    fun `isValidIp returns true for localhost`() {
        assertTrue(ValidationUtils.isValidIp("127.0.0.1"))
    }
    
    @Test
    fun `isValidIp returns true for 255`() {
        assertTrue(ValidationUtils.isValidIp("255.255.255.255"))
    }
    
    @Test
    fun `isValidIp returns false for invalid IP`() {
        assertFalse(ValidationUtils.isValidIp("256.1.1.1"))
    }
    
    @Test
    fun `isValidIp returns false for incomplete IP`() {
        assertFalse(ValidationUtils.isValidIp("192.168.1"))
    }
    
    @Test
    fun `isValidIp returns false for null`() {
        assertFalse(ValidationUtils.isValidIp(null))
    }
    
    // ─────────────────────────────────────────────────────────────
    // 端口验证测试
    // ─────────────────────────────────────────────────────────────
    
    @Test
    fun `isValidPort returns true for valid port`() {
        assertTrue(ValidationUtils.isValidPort("8080"))
    }
    
    @Test
    fun `isValidPort returns true for port 1`() {
        assertTrue(ValidationUtils.isValidPort("1"))
    }
    
    @Test
    fun `isValidPort returns true for port 65535`() {
        assertTrue(ValidationUtils.isValidPort("65535"))
    }
    
    @Test
    fun `isValidPort returns false for port 0`() {
        assertFalse(ValidationUtils.isValidPort("0"))
    }
    
    @Test
    fun `isValidPort returns false for port over 65535`() {
        assertFalse(ValidationUtils.isValidPort("70000"))
    }
    
    @Test
    fun `isValidPort returns false for null`() {
        assertFalse(ValidationUtils.isValidPort(null))
    }
    
    @Test
    fun `isValidPort Int returns true for valid port`() {
        assertTrue(ValidationUtils.isValidPort(8080))
    }
    
    @Test
    fun `isValidPort Int returns false for invalid port`() {
        assertFalse(ValidationUtils.isValidPort(0))
        assertFalse(ValidationUtils.isValidPort(70000))
    }
    
    // ─────────────────────────────────────────────────────────────
    // Gateway 地址验证测试
    // ─────────────────────────────────────────────────────────────
    
    @Test
    fun `isValidGatewayAddress returns true for valid address`() {
        assertTrue(ValidationUtils.isValidGatewayAddress("192.168.1.1:8080"))
    }
    
    @Test
    fun `isValidGatewayAddress returns true for localhost`() {
        assertTrue(ValidationUtils.isValidGatewayAddress("127.0.0.1:18789"))
    }
    
    @Test
    fun `isValidGatewayAddress returns false for missing port`() {
        assertFalse(ValidationUtils.isValidGatewayAddress("192.168.1.1"))
    }
    
    @Test
    fun `isValidGatewayAddress returns false for invalid port`() {
        assertFalse(ValidationUtils.isValidGatewayAddress("192.168.1.1:abc"))
    }
    
    @Test
    fun `isValidGatewayAddress returns false for null`() {
        assertFalse(ValidationUtils.isValidGatewayAddress(null))
    }
    
    // ─────────────────────────────────────────────────────────────
    // Token 验证测试
    // ─────────────────────────────────────────────────────────────
    
    @Test
    fun `isValidToken returns true for valid token`() {
        assertTrue(ValidationUtils.isValidToken("12345678"))
    }
    
    @Test
    fun `isValidToken returns true for long token`() {
        assertTrue(ValidationUtils.isValidToken("abc123def456ghi789"))
    }
    
    @Test
    fun `isValidToken returns false for short token`() {
        assertFalse(ValidationUtils.isValidToken("1234567"))
    }
    
    @Test
    fun `isValidToken returns false for null`() {
        assertFalse(ValidationUtils.isValidToken(null))
    }
    
    @Test
    fun `isValidToken returns false for blank`() {
        assertFalse(ValidationUtils.isValidToken("   "))
    }
    
    // ─────────────────────────────────────────────────────────────
    // 消息内容验证测试
    // ─────────────────────────────────────────────────────────────
    
    @Test
    fun `isValidMessageContent returns true for non-empty content`() {
        assertTrue(ValidationUtils.isValidMessageContent("Hello"))
    }
    
    @Test
    fun `isValidMessageContent returns false for empty content`() {
        assertFalse(ValidationUtils.isValidMessageContent(""))
    }
    
    @Test
    fun `isValidMessageContent returns false for null`() {
        assertFalse(ValidationUtils.isValidMessageContent(null))
    }
    
    // ─────────────────────────────────────────────────────────────
    // 文件大小验证测试
    // ─────────────────────────────────────────────────────────────
    
    @Test
    fun `isValidFileSize returns true for valid size`() {
        assertTrue(ValidationUtils.isValidFileSize(1024))
    }
    
    @Test
    fun `isValidFileSize returns true for exactly max size`() {
        val maxBytes = 10 * 1024 * 1024L
        assertTrue(ValidationUtils.isValidFileSize(maxBytes))
    }
    
    @Test
    fun `isValidFileSize returns false for zero`() {
        assertFalse(ValidationUtils.isValidFileSize(0))
    }
    
    @Test
    fun `isValidFileSize returns false for over max size`() {
        val overMax = 11 * 1024 * 1024L
        assertFalse(ValidationUtils.isValidFileSize(overMax))
    }
    
    @Test
    fun `isValidFileSize respects custom max size`() {
        assertTrue(ValidationUtils.isValidFileSize(1024 * 1024, 2))  // 1MB, max 2MB
        assertFalse(ValidationUtils.isValidFileSize(3 * 1024 * 1024, 2))  // 3MB, max 2MB
    }
}