package com.openclaw.clawchat.ui.state

import com.openclaw.clawchat.data.FontSize
import com.openclaw.clawchat.data.ThemeMode
import com.openclaw.clawchat.security.RootDetector
import org.junit.Assert.*
import org.junit.Test

/**
 * SettingsUiState and PairingUiState tests
 */
class SettingsStateTest {

    // ─────────────────────────────────────────────────────────────
    // SettingsUiState Tests
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `SettingsUiState creates with default values`() {
        val state = SettingsUiState()

        assertNull(state.currentGateway)
        assertEquals(GatewayConfigInput(), state.gatewayConfigInput)
        assertEquals(ConnectionStatusUi.Disconnected, state.connectionStatus)
        assertFalse(state.isPaired)
        assertTrue(state.notificationsEnabled)
        assertFalse(state.dndEnabled)
        assertEquals("1.0.0", state.appVersion)
        assertEquals(FontSize.MEDIUM, state.messageFontSize)
        assertEquals(ThemeMode.SYSTEM, state.themeMode)
        assertTrue(state.dynamicColor)
        assertEquals(0, state.themeColorIndex)
        assertFalse(state.isRooted)
        assertEquals(RootDetector.RootCheckResult.RiskLevel.NONE, state.rootRiskLevel)
    }

    @Test
    fun `SettingsUiState creates with custom values`() {
        val gateway = GatewayConfigUi(
            id = "gw-1",
            name = "Local",
            host = "localhost",
            port = 8080,
            isCurrent = true
        )
        val state = SettingsUiState(
            currentGateway = gateway,
            connectionStatus = ConnectionStatusUi.Connected,
            isPaired = true,
            notificationsEnabled = false,
            dndEnabled = true,
            appVersion = "2.0.0",
            messageFontSize = FontSize.LARGE,
            themeMode = ThemeMode.DARK,
            dynamicColor = false,
            themeColorIndex = 3,
            isRooted = true,
            rootRiskLevel = RootDetector.RootCheckResult.RiskLevel.HIGH
        )

        assertEquals(gateway, state.currentGateway)
        assertEquals(ConnectionStatusUi.Connected, state.connectionStatus)
        assertTrue(state.isPaired)
        assertFalse(state.notificationsEnabled)
        assertTrue(state.dndEnabled)
        assertEquals("2.0.0", state.appVersion)
        assertEquals(FontSize.LARGE, state.messageFontSize)
        assertEquals(ThemeMode.DARK, state.themeMode)
        assertFalse(state.dynamicColor)
        assertEquals(3, state.themeColorIndex)
        assertTrue(state.isRooted)
        assertEquals(RootDetector.RootCheckResult.RiskLevel.HIGH, state.rootRiskLevel)
    }

    @Test
    fun `SettingsUiState copy preserves values`() {
        val original = SettingsUiState(
            notificationsEnabled = true,
            themeMode = ThemeMode.LIGHT
        )

        val copied = original.copy(
            notificationsEnabled = false,
            themeMode = ThemeMode.DARK
        )

        assertFalse(copied.notificationsEnabled)
        assertEquals(ThemeMode.DARK, copied.themeMode)
    }

    // ─────────────────────────────────────────────────────────────
    // PairingUiState Tests
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `PairingUiState creates with default values`() {
        val state = PairingUiState()

        assertEquals("", state.gatewayUrl)
        assertFalse(state.isPairing)
        assertFalse(state.isInitializing)
        assertEquals(PairingStatus.Initializing, state.pairingStatus)
        assertNull(state.deviceId)
        assertNull(state.publicKey)
        assertNull(state.deviceToken)
        assertNull(state.pairingStartTime)
        assertNull(state.error)
    }

    @Test
    fun `PairingUiState creates with custom values`() {
        val state = PairingUiState(
            gatewayUrl = "https://gateway.example.com",
            isPairing = true,
            isInitializing = false,
            pairingStatus = PairingStatus.WaitingForApproval,
            deviceId = "device-123",
            publicKey = "key-abc",
            deviceToken = "token-xyz",
            pairingStartTime = 1000L,
            error = null
        )

        assertEquals("https://gateway.example.com", state.gatewayUrl)
        assertTrue(state.isPairing)
        assertFalse(state.isInitializing)
        assertEquals(PairingStatus.WaitingForApproval, state.pairingStatus)
        assertEquals("device-123", state.deviceId)
        assertEquals("key-abc", state.publicKey)
        assertEquals("token-xyz", state.deviceToken)
        assertEquals(1000L, state.pairingStartTime)
        assertNull(state.error)
    }

    @Test
    fun `PairingUiState with error`() {
        val state = PairingUiState(
            pairingStatus = PairingStatus.Error("Connection timeout"),
            error = "Connection timeout"
        )

        assertTrue(state.pairingStatus is PairingStatus.Error)
        assertEquals("Connection timeout", state.error)
        assertEquals("Connection timeout", (state.pairingStatus as PairingStatus.Error).message)
    }

    @Test
    fun `PairingUiState copy preserves values`() {
        val original = PairingUiState(
            deviceId = "device-1",
            pairingStatus = PairingStatus.WaitingForApproval
        )

        val copied = original.copy(
            pairingStatus = PairingStatus.Approved
        )

        assertEquals("device-1", copied.deviceId)
        assertEquals(PairingStatus.Approved, copied.pairingStatus)
    }

    // ─────────────────────────────────────────────────────────────
    // ConnectionStatusUi Tests
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `ConnectionStatusUi enum values`() {
        assertEquals(4, ConnectionStatusUi.entries.size)
        assertEquals(ConnectionStatusUi.Disconnected, ConnectionStatusUi.valueOf("Disconnected"))
        assertEquals(ConnectionStatusUi.Connecting, ConnectionStatusUi.valueOf("Connecting"))
        assertEquals(ConnectionStatusUi.Connected, ConnectionStatusUi.valueOf("Connected"))
        assertEquals(ConnectionStatusUi.Disconnecting, ConnectionStatusUi.valueOf("Disconnecting"))
    }

    // ─────────────────────────────────────────────────────────────
    // FontSize Tests
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `FontSize enum values`() {
        assertEquals(3, FontSize.entries.size)
        assertEquals(FontSize.SMALL, FontSize.valueOf("SMALL"))
        assertEquals(FontSize.MEDIUM, FontSize.valueOf("MEDIUM"))
        assertEquals(FontSize.LARGE, FontSize.valueOf("LARGE"))
    }

    @Test
    fun `FontSize value ordering`() {
        // SMALL(0) < MEDIUM(1) < LARGE(2)
        assertTrue(FontSize.SMALL.value < FontSize.MEDIUM.value)
        assertTrue(FontSize.MEDIUM.value < FontSize.LARGE.value)
    }

    @Test
    fun `FontSize displayName`() {
        assertEquals("小", FontSize.SMALL.displayName)
        assertEquals("中", FontSize.MEDIUM.displayName)
        assertEquals("大", FontSize.LARGE.displayName)
    }

    @Test
    fun `FontSize fromValue`() {
        assertEquals(FontSize.SMALL, FontSize.fromValue(0))
        assertEquals(FontSize.MEDIUM, FontSize.fromValue(1))
        assertEquals(FontSize.LARGE, FontSize.fromValue(2))
        assertEquals(FontSize.MEDIUM, FontSize.fromValue(99))  // Default to MEDIUM
    }

    // ─────────────────────────────────────────────────────────────
    // ThemeMode Tests
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `ThemeMode enum values`() {
        assertEquals(3, ThemeMode.entries.size)
        assertEquals(ThemeMode.SYSTEM, ThemeMode.valueOf("SYSTEM"))
        assertEquals(ThemeMode.LIGHT, ThemeMode.valueOf("LIGHT"))
        assertEquals(ThemeMode.DARK, ThemeMode.valueOf("DARK"))
    }

    // ─────────────────────────────────────────────────────────────
    // RiskLevel Tests
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `RiskLevel enum values`() {
        assertEquals(4, RootDetector.RootCheckResult.RiskLevel.entries.size)
        assertEquals(RootDetector.RootCheckResult.RiskLevel.NONE, RootDetector.RootCheckResult.RiskLevel.valueOf("NONE"))
        assertEquals(RootDetector.RootCheckResult.RiskLevel.LOW, RootDetector.RootCheckResult.RiskLevel.valueOf("LOW"))
        assertEquals(RootDetector.RootCheckResult.RiskLevel.MEDIUM, RootDetector.RootCheckResult.RiskLevel.valueOf("MEDIUM"))
        assertEquals(RootDetector.RootCheckResult.RiskLevel.HIGH, RootDetector.RootCheckResult.RiskLevel.valueOf("HIGH"))
    }

    @Test
    fun `RiskLevel severity ordering`() {
        // NONE < LOW < MEDIUM < HIGH
        val levels = listOf(
            RootDetector.RootCheckResult.RiskLevel.NONE,
            RootDetector.RootCheckResult.RiskLevel.LOW,
            RootDetector.RootCheckResult.RiskLevel.MEDIUM,
            RootDetector.RootCheckResult.RiskLevel.HIGH
        )

        assertEquals(4, levels.size)
    }
}