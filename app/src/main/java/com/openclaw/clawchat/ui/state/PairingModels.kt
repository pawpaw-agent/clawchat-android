package com.openclaw.clawchat.ui.state

import com.openclaw.clawchat.data.FontSize

data class PairingUiState(
    val gatewayUrl: String = "",
    val isPairing: Boolean = false,
    val isInitializing: Boolean = false,
    val pairingStatus: PairingStatus = PairingStatus.Initializing,
    val deviceId: String? = null,
    val publicKey: String? = null,
    val deviceToken: String? = null,
    val pairingStartTime: Long? = null,
    val error: String? = null
)

sealed class PairingStatus {
    data object Initializing : PairingStatus()
    data object WaitingForApproval : PairingStatus()
    data object Approved : PairingStatus()
    data object Rejected : PairingStatus()
    data object Timeout : PairingStatus()
    data class Error(val message: String) : PairingStatus()
}

data class SettingsUiState(
    val currentGateway: GatewayConfigUi? = null,
    val gatewayConfigInput: GatewayConfigInput = GatewayConfigInput(),
    val connectionStatus: ConnectionStatusUi = ConnectionStatusUi.Disconnected,
    val isPaired: Boolean = false,
    val appVersion: String = "1.0.0",
    val messageFontSize: FontSize = FontSize.MEDIUM,
    val themeMode: com.openclaw.clawchat.data.ThemeMode = com.openclaw.clawchat.data.ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val themeColorIndex: Int = 0,
    val isRooted: Boolean = false,
    val rootRiskLevel: com.openclaw.clawchat.security.RootDetector.RootCheckResult.RiskLevel =
        com.openclaw.clawchat.security.RootDetector.RootCheckResult.RiskLevel.NONE
)
