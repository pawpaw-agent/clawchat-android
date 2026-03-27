package com.openclaw.clawchat.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openclaw.clawchat.network.GatewayUrlUtil
import com.openclaw.clawchat.ui.state.ConnectMode
import com.openclaw.clawchat.ui.state.PairingEvent
import com.openclaw.clawchat.ui.state.PairingStatus
import com.openclaw.clawchat.ui.state.PairingViewModel

/**
 * 配对底部抽屉
 * 
 * 使用 PairingViewModel 完成实际的配对流程
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PairingBottomSheet(
    onDismiss: () -> Unit,
    onPairingSuccess: () -> Unit,
    viewModel: PairingViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    
    // 监听配对成功事件
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is PairingEvent.PairingSuccess -> {
                    onPairingSuccess()
                }
                else -> {}
            }
        }
    }
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "连接到 Gateway",
                style = MaterialTheme.typography.titleLarge
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 连接模式选择
            ConnectionModeSelector(
                selected = state.connectMode,
                onSelect = { viewModel.setConnectMode(it) },
                enabled = !state.isPairing
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            when (state.connectMode) {
                ConnectMode.TOKEN -> TokenModeContent(
                    gatewayUrl = state.gatewayUrl,
                    token = state.token,
                    onGatewayUrlChange = { viewModel.setGatewayUrl(it) },
                    onTokenChange = { viewModel.setToken(it) },
                    onConnect = { viewModel.connectWithToken() },
                    isPairing = state.isPairing,
                    status = state.status
                )
                ConnectMode.PAIRING -> PairingModeContent(
                    gatewayUrl = state.gatewayUrl,
                    onGatewayUrlChange = { viewModel.setGatewayUrl(it) },
                    onStartPairing = { viewModel.startPairing() },
                    isPairing = state.isPairing,
                    status = state.status,
                    deviceId = state.deviceId
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ConnectionModeSelector(
    selected: ConnectMode,
    onSelect: (ConnectMode) -> Unit,
    enabled: Boolean
) {
    val modes = listOf(
        ConnectMode.TOKEN to "Token",
        ConnectMode.PAIRING to "配对"
    )

    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        modes.forEachIndexed { index, (mode, label) ->
            SegmentedButton(
                selected = selected == mode,
                onClick = { onSelect(mode) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                enabled = enabled
            ) {
                Text(label)
            }
        }
    }
}

@Composable
private fun TokenModeContent(
    gatewayUrl: String,
    token: String,
    onGatewayUrlChange: (String) -> Unit,
    onTokenChange: (String) -> Unit,
    onConnect: () -> Unit,
    isPairing: Boolean,
    status: PairingStatus
) {
    val urlValid = gatewayUrl.isBlank() || GatewayUrlUtil.isValidInput(gatewayUrl)
    val wsPreview = if (gatewayUrl.isNotBlank() && urlValid) {
        GatewayUrlUtil.normalizeToWebSocketUrl(gatewayUrl)
    } else null

    // Gateway 地址
    OutlinedTextField(
        value = gatewayUrl,
        onValueChange = onGatewayUrlChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Gateway 地址") },
        placeholder = { Text("192.168.0.213:18789") },
        enabled = !isPairing,
        singleLine = true,
        isError = gatewayUrl.isNotBlank() && !urlValid,
        supportingText = {
            if (wsPreview != null) {
                Text("→ $wsPreview", fontFamily = FontFamily.Monospace)
            }
        }
    )

    Spacer(modifier = Modifier.height(12.dp))

    // Token
    OutlinedTextField(
        value = token,
        onValueChange = onTokenChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Gateway Token") },
        placeholder = { Text("粘贴 OPENCLAW_GATEWAY_TOKEN") },
        enabled = !isPairing,
        singleLine = true
    )

    Spacer(modifier = Modifier.height(16.dp))

    // 状态显示
    StatusIndicator(status, isPairing)

    Spacer(modifier = Modifier.height(16.dp))

    // 连接按钮
    Button(
        onClick = onConnect,
        modifier = Modifier.fillMaxWidth(),
        enabled = !isPairing
                && gatewayUrl.isNotBlank() && urlValid
                && token.isNotBlank()
    ) {
        if (isPairing) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text("连接")
    }
}

@Composable
private fun PairingModeContent(
    gatewayUrl: String,
    onGatewayUrlChange: (String) -> Unit,
    onStartPairing: () -> Unit,
    isPairing: Boolean,
    status: PairingStatus,
    deviceId: String?
) {
    val urlValid = gatewayUrl.isBlank() || GatewayUrlUtil.isValidInput(gatewayUrl)
    val wsPreview = if (gatewayUrl.isNotBlank() && urlValid) {
        GatewayUrlUtil.normalizeToWebSocketUrl(gatewayUrl)
    } else null

    // Gateway 地址
    OutlinedTextField(
        value = gatewayUrl,
        onValueChange = onGatewayUrlChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Gateway 地址") },
        placeholder = { Text("192.168.0.213:18789") },
        enabled = !isPairing,
        singleLine = true,
        isError = gatewayUrl.isNotBlank() && !urlValid,
        supportingText = {
            if (wsPreview != null) {
                Text("→ $wsPreview", fontFamily = FontFamily.Monospace)
            }
        }
    )

    Spacer(modifier = Modifier.height(12.dp))

    // 设备 ID
    deviceId?.let { id ->
        Text(
            text = "设备 ID: $id",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))
    }

    // 状态显示
    StatusIndicator(status, isPairing)

    Spacer(modifier = Modifier.height(16.dp))

    // 开始配对按钮
    Button(
        onClick = onStartPairing,
        modifier = Modifier.fillMaxWidth(),
        enabled = !isPairing
                && gatewayUrl.isNotBlank() && urlValid
    ) {
        if (isPairing) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text("开始配对")
    }
    
    Spacer(modifier = Modifier.height(8.dp))
    
    Text(
        text = "配对后需在 Gateway 终端运行: openclaw devices approve",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun StatusIndicator(status: PairingStatus, isPairing: Boolean) {
    when (status) {
        is PairingStatus.WaitingForApproval -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                Text("等待管理员批准...")
            }
        }
        is PairingStatus.Approved -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text("连接成功！", color = MaterialTheme.colorScheme.primary)
            }
        }
        is PairingStatus.Error -> {
            Text(
                text = status.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
        else -> {}
    }
}

// 需要导入 Icons
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check