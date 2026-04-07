package com.openclaw.clawchat.network

import org.junit.Assert.*
import org.junit.Test

/**
 * GatewayUrlUtil tests
 */
class GatewayUrlUtilTest {

    // ─────────────────────────────────────────────────────────────
    // normalizeToWebSocketUrl Tests
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `normalizeToWebSocketUrl handles IP with port`() {
        val result = GatewayUrlUtil.normalizeToWebSocketUrl("192.168.1.1:18789")

        assertEquals("ws://192.168.1.1:18789/ws", result)
    }

    @Test
    fun `normalizeToWebSocketUrl handles IP without port`() {
        val result = GatewayUrlUtil.normalizeToWebSocketUrl("192.168.1.1")

        assertEquals("ws://192.168.1.1:18789/ws", result)
    }

    @Test
    fun `normalizeToWebSocketUrl handles ws protocol`() {
        val result = GatewayUrlUtil.normalizeToWebSocketUrl("ws://192.168.1.1:18789")

        assertEquals("ws://192.168.1.1:18789/ws", result)
    }

    @Test
    fun `normalizeToWebSocketUrl handles wss protocol`() {
        val result = GatewayUrlUtil.normalizeToWebSocketUrl("wss://192.168.1.1:18789")

        assertEquals("ws://192.168.1.1:18789/ws", result)
    }

    @Test
    fun `normalizeToWebSocketUrl handles http protocol`() {
        val result = GatewayUrlUtil.normalizeToWebSocketUrl("http://192.168.1.1:18789")

        assertEquals("ws://192.168.1.1:18789/ws", result)
    }

    @Test
    fun `normalizeToWebSocketUrl handles https protocol`() {
        val result = GatewayUrlUtil.normalizeToWebSocketUrl("https://192.168.1.1:18789")

        assertEquals("ws://192.168.1.1:18789/ws", result)
    }

    @Test
    fun `normalizeToWebSocketUrl handles URL with ws path`() {
        val result = GatewayUrlUtil.normalizeToWebSocketUrl("192.168.1.1:18789/ws")

        assertEquals("ws://192.168.1.1:18789/ws", result)
    }

    @Test
    fun `normalizeToWebSocketUrl handles URL with trailing slash`() {
        val result = GatewayUrlUtil.normalizeToWebSocketUrl("192.168.1.1:18789/")

        assertEquals("ws://192.168.1.1:18789/ws", result)
    }

    @Test
    fun `normalizeToWebSocketUrl handles hostname`() {
        val result = GatewayUrlUtil.normalizeToWebSocketUrl("gateway.local:8080")

        assertEquals("ws://gateway.local:8080/ws", result)
    }

    @Test
    fun `normalizeToWebSocketUrl handles hostname without port`() {
        val result = GatewayUrlUtil.normalizeToWebSocketUrl("gateway.local")

        assertEquals("ws://gateway.local:18789/ws", result)
    }

    @Test
    fun `normalizeToWebSocketUrl handles IPv6 address`() {
        val result = GatewayUrlUtil.normalizeToWebSocketUrl("[::1]:18789")

        assertEquals("ws://[::1]:18789/ws", result)
    }

    @Test
    fun `normalizeToWebSocketUrl handles IPv6 without port`() {
        val result = GatewayUrlUtil.normalizeToWebSocketUrl("[::1]")

        assertEquals("ws://[::1]:18789/ws", result)
    }

    @Test
    fun `normalizeToWebSocketUrl handles empty string`() {
        val result = GatewayUrlUtil.normalizeToWebSocketUrl("")

        assertEquals("", result)
    }

    @Test
    fun `normalizeToWebSocketUrl trims whitespace`() {
        val result = GatewayUrlUtil.normalizeToWebSocketUrl("  192.168.1.1:18789  ")

        assertEquals("ws://192.168.1.1:18789/ws", result)
    }

    @Test
    fun `normalizeToWebSocketUrl handles localhost`() {
        val result = GatewayUrlUtil.normalizeToWebSocketUrl("localhost:18789")

        assertEquals("ws://localhost:18789/ws", result)
    }

    @Test
    fun `normalizeToWebSocketUrl handles localhost without port`() {
        val result = GatewayUrlUtil.normalizeToWebSocketUrl("localhost")

        assertEquals("ws://localhost:18789/ws", result)
    }

    // ─────────────────────────────────────────────────────────────
    // isValidInput Tests
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `isValidInput returns true for IP with port`() {
        assertTrue(GatewayUrlUtil.isValidInput("192.168.1.1:18789"))
    }

    @Test
    fun `isValidInput returns true for IP without port`() {
        assertTrue(GatewayUrlUtil.isValidInput("192.168.1.1"))
    }

    @Test
    fun `isValidInput returns true for ws URL`() {
        assertTrue(GatewayUrlUtil.isValidInput("ws://192.168.1.1:18789"))
    }

    @Test
    fun `isValidInput returns true for wss URL`() {
        assertTrue(GatewayUrlUtil.isValidInput("wss://192.168.1.1:18789"))
    }

    @Test
    fun `isValidInput returns true for http URL`() {
        assertTrue(GatewayUrlUtil.isValidInput("http://192.168.1.1:18789"))
    }

    @Test
    fun `isValidInput returns true for hostname`() {
        assertTrue(GatewayUrlUtil.isValidInput("gateway.example.com"))
    }

    @Test
    fun `isValidInput returns true for hostname with port`() {
        assertTrue(GatewayUrlUtil.isValidInput("gateway.example.com:8080"))
    }

    @Test
    fun `isValidInput returns true for localhost`() {
        assertTrue(GatewayUrlUtil.isValidInput("localhost"))
        assertTrue(GatewayUrlUtil.isValidInput("localhost:8080"))
    }

    @Test
    fun `isValidInput returns true for IPv6 address`() {
        assertTrue(GatewayUrlUtil.isValidInput("[::1]"))
        assertTrue(GatewayUrlUtil.isValidInput("[::1]:18789"))
        assertTrue(GatewayUrlUtil.isValidInput("[fe80::1%25eth0]:18789"))
    }

    @Test
    fun `isValidInput returns false for empty string`() {
        assertFalse(GatewayUrlUtil.isValidInput(""))
    }

    @Test
    fun `isValidInput returns false for whitespace only`() {
        assertFalse(GatewayUrlUtil.isValidInput("   "))
    }

    @Test
    fun `isValidInput returns false for invalid port`() {
        assertFalse(GatewayUrlUtil.isValidInput("192.168.1.1:99999"))
        assertFalse(GatewayUrlUtil.isValidInput("192.168.1.1:abc"))
        assertFalse(GatewayUrlUtil.isValidInput("192.168.1.1:0"))
    }

    @Test
    fun `isValidInput returns false for malformed IPv6`() {
        assertFalse(GatewayUrlUtil.isValidInput("[::1"))  // Missing closing bracket
        assertFalse(GatewayUrlUtil.isValidInput("[::1]:abc"))  // Invalid port
    }

    @Test
    fun `isValidInput trims whitespace`() {
        assertTrue(GatewayUrlUtil.isValidInput("  192.168.1.1:18789  "))
    }

    // ─────────────────────────────────────────────────────────────
    // extractDisplayAddress Tests
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `extractDisplayAddress removes ws protocol`() {
        val result = GatewayUrlUtil.extractDisplayAddress("ws://192.168.1.1:18789")

        assertEquals("192.168.1.1:18789", result)
    }

    @Test
    fun `extractDisplayAddress removes wss protocol`() {
        val result = GatewayUrlUtil.extractDisplayAddress("wss://192.168.1.1:18789")

        assertEquals("192.168.1.1:18789", result)
    }

    @Test
    fun `extractDisplayAddress removes http protocol`() {
        val result = GatewayUrlUtil.extractDisplayAddress("http://192.168.1.1:18789")

        assertEquals("192.168.1.1:18789", result)
    }

    @Test
    fun `extractDisplayAddress removes https protocol`() {
        val result = GatewayUrlUtil.extractDisplayAddress("https://192.168.1.1:18789")

        assertEquals("192.168.1.1:18789", result)
    }

    @Test
    fun `extractDisplayAddress removes ws path`() {
        val result = GatewayUrlUtil.extractDisplayAddress("192.168.1.1:18789/ws")

        assertEquals("192.168.1.1:18789", result)
    }

    @Test
    fun `extractDisplayAddress removes trailing slash`() {
        val result = GatewayUrlUtil.extractDisplayAddress("192.168.1.1:18789/")

        assertEquals("192.168.1.1:18789", result)
    }

    @Test
    fun `extractDisplayAddress handles bare IP`() {
        val result = GatewayUrlUtil.extractDisplayAddress("192.168.1.1:18789")

        assertEquals("192.168.1.1:18789", result)
    }

    @Test
    fun `extractDisplayAddress handles hostname`() {
        val result = GatewayUrlUtil.extractDisplayAddress("gateway.local:8080")

        assertEquals("gateway.local:8080", result)
    }

    @Test
    fun `extractDisplayAddress trims whitespace`() {
        val result = GatewayUrlUtil.extractDisplayAddress("  192.168.1.1:18789  ")

        assertEquals("192.168.1.1:18789", result)
    }

    @Test
    fun `extractDisplayAddress handles IPv6`() {
        val result = GatewayUrlUtil.extractDisplayAddress("ws://[::1]:18789/ws")

        assertEquals("[::1]:18789", result)
    }

    // ─────────────────────────────────────────────────────────────
    // Round-trip Tests
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `round-trip normalize then extract`() {
        val original = "192.168.1.1:8080"
        val normalized = GatewayUrlUtil.normalizeToWebSocketUrl(original)
        val extracted = GatewayUrlUtil.extractDisplayAddress(normalized)

        assertEquals("192.168.1.1:8080", extracted)
    }

    @Test
    fun `round-trip with hostname`() {
        val original = "gateway.local"
        val normalized = GatewayUrlUtil.normalizeToWebSocketUrl(original)
        val extracted = GatewayUrlUtil.extractDisplayAddress(normalized)

        // Default port is added
        assertEquals("gateway.local:18789", extracted)
    }

    // ─────────────────────────────────────────────────────────────
    // Edge Cases
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `handles full URL with all components`() {
        val input = "https://gateway.example.com:8443/ws"
        val normalized = GatewayUrlUtil.normalizeToWebSocketUrl(input)
        val extracted = GatewayUrlUtil.extractDisplayAddress(input)

        assertEquals("ws://gateway.example.com:8443/ws", normalized)
        assertEquals("gateway.example.com:8443", extracted)
    }

    @Test
    fun `handles IPv6 with zone ID`() {
        val input = "[fe80::1%25eth0]:18789"
        assertTrue(GatewayUrlUtil.isValidInput(input))

        val normalized = GatewayUrlUtil.normalizeToWebSocketUrl(input)
        assertEquals("ws://[fe80::1%25eth0]:18789/ws", normalized)
    }

    @Test
    fun `validates port range`() {
        // Valid ports
        assertTrue(GatewayUrlUtil.isValidInput("host:1"))
        assertTrue(GatewayUrlUtil.isValidInput("host:65535"))

        // Invalid ports
        assertFalse(GatewayUrlUtil.isValidInput("host:0"))
        assertFalse(GatewayUrlUtil.isValidInput("host:65536"))
        assertFalse(GatewayUrlUtil.isValidInput("host:-1"))
    }
}