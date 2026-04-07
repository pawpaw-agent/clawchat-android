package com.openclaw.clawchat.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openclaw.clawchat.R
import com.openclaw.clawchat.network.GatewayUrlUtil
import com.openclaw.clawchat.ui.state.ConnectMode
import com.openclaw.clawchat.ui.state.PairingEvent
import com.openclaw.clawchat.ui.state.PairingStatus
import com.openclaw.clawchat.ui.state.PairingViewModel

/**
 * 配对底部抽屉
 *
 * 使用 PairingViewModel 完成配对流程
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
                text = stringResource(R.string.pairing_sheet_title),
                style = MaterialTheme.typography.titleLarge
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 连接模式选择
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = state.connectMode == ConnectMode.TOKEN,
                    onClick = { viewModel.setConnectMode(ConnectMode.TOKEN) },
                    label = { Text(stringResource(R.string.onboarding_token)) },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = state.connectMode == ConnectMode.PAIRING,
                    onClick = { viewModel.setConnectMode(ConnectMode.PAIRING) },
                    label = { Text(stringResource(R.string.onboarding_pairing)) },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Gateway 地址
            val urlValid = state.gatewayUrl.isBlank() || GatewayUrlUtil.isValidInput(state.gatewayUrl)
            val wsPreview = if (state.gatewayUrl.isNotBlank() && urlValid) {
                GatewayUrlUtil.normalizeToWebSocketUrl(state.gatewayUrl)
            } else null

            OutlinedTextField(
                value = state.gatewayUrl,
                onValueChange = { viewModel.setGatewayUrl(it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.pairing_gateway_address_label)) },
                placeholder = { Text(stringResource(R.string.pairing_gateway_address_hint)) },
                enabled = !state.isPairing,
                singleLine = true,
                isError = state.gatewayUrl.isNotBlank() && !urlValid,
                supportingText = {
                    if (wsPreview != null) {
                        Text("→ $wsPreview", fontFamily = FontFamily.Monospace)
                    }
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Token 模式
            if (state.connectMode == ConnectMode.TOKEN) {
                var tokenVisible by remember { mutableStateOf(false) }

                OutlinedTextField(
                    value = state.token,
                    onValueChange = { viewModel.setToken(it) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.pairing_token_label)) },
                    placeholder = { Text(stringResource(R.string.pairing_token_hint)) },
                    enabled = !state.isPairing,
                    singleLine = true,
                    visualTransformation = if (tokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { tokenVisible = !tokenVisible }) {
                            Icon(
                                imageVector = if (tokenVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = stringResource(if (tokenVisible) R.string.onboarding_password_hide else R.string.onboarding_password_show)
                            )
                        }
                    }
                )
            }

            // 配对模式：显示设备 ID
            if (state.connectMode == ConnectMode.PAIRING) {
                state.deviceId?.let { id ->
                    Text(
                        text = stringResource(R.string.pairing_device_id, id),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            // 状态显示
            StatusIndicator(state.status, state.isPairing)

            Spacer(modifier = Modifier.height(16.dp))

            // 操作按钮
            when (state.status) {
                is PairingStatus.WaitingForApproval -> {
                    OutlinedButton(
                        onClick = { viewModel.cancelPairing() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.onboarding_cancel_pairing))
                    }
                }
                else -> {
                    Button(
                        onClick = {
                            when (state.connectMode) {
                                ConnectMode.TOKEN -> viewModel.connectWithToken()
                                ConnectMode.PAIRING -> viewModel.startPairing()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.isPairing
                                && state.gatewayUrl.isNotBlank() && urlValid
                                && (state.connectMode == ConnectMode.PAIRING || state.token.isNotBlank())
                    ) {
                        if (state.isPairing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.onboarding_connecting))
                        } else {
                            Text(if (state.connectMode == ConnectMode.TOKEN) stringResource(R.string.onboarding_connect) else stringResource(R.string.onboarding_start_pairing))
                        }
                    }

                    if (state.connectMode == ConnectMode.PAIRING) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.pairing_approval_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
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
                Text(stringResource(R.string.pairing_waiting_admin))
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
                Text(stringResource(R.string.pairing_connected), color = MaterialTheme.colorScheme.primary)
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