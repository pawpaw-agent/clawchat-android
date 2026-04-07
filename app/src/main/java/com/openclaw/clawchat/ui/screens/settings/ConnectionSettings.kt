package com.openclaw.clawchat.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.openclaw.clawchat.R
import com.openclaw.clawchat.ui.state.ConnectionStatusUi
import com.openclaw.clawchat.ui.state.GatewayConfigInput
import com.openclaw.clawchat.ui.state.GatewayConfigUi
import com.openclaw.clawchat.ui.state.getStatusColor

/**
 * Gateway 配置项
 */
@Composable
fun GatewayConfigItem(
    gateway: GatewayConfigUi?,
    connectionStatus: ConnectionStatusUi,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = {
            Text(
                text = gateway?.name ?: stringResource(R.string.connection_not_configured),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            Column {
                Text(
                    text = gateway?.host ?: stringResource(R.string.connection_configure_gateway_hint),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .padding(1.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .let {
                                    when (connectionStatus) {
                                        is ConnectionStatusUi.Connected -> it
                                            .padding(1.dp)
                                            .wrapContentSize()
                                            .then(Modifier)
                                        else -> it
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(if (connectionStatus.isConnected) 8.dp else 6.dp)
                                    .let {
                                        when (connectionStatus) {
                                            is ConnectionStatusUi.Connected -> it
                                            is ConnectionStatusUi.Connecting -> it
                                            is ConnectionStatusUi.Error -> it
                                            else -> it
                                        }
                                    }
                            ) {
                                when (connectionStatus) {
                                    is ConnectionStatusUi.Connected -> Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .padding(1.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Spacer(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .wrapContentSize()
                                        )
                                    }
                                    else -> Unit
                                }
                            }
                        }
                    }
                    Text(
                        text = connectionStatus.displayText,
                        style = MaterialTheme.typography.labelSmall,
                        color = connectionStatus.getStatusColor()
                    )
                }
            }
        },
        leadingContent = {
            Icon(
                imageVector = if (connectionStatus.isConnected) Icons.Default.Cloud else Icons.Outlined.Cloud,
                contentDescription = null,
                tint = if (connectionStatus.isConnected) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    )
}

/**
 * 断开连接项
 */
@Composable
fun DisconnectItem(
    onDisconnect: () -> Unit
) {
    ListItem(
        headlineContent = {
            Text(stringResource(R.string.connection_disconnect))
        },
        supportingContent = {
            Text(stringResource(R.string.connection_disconnect_description))
        },
        leadingContent = {
            Icon(
                imageVector = Icons.Default.Power,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onDisconnect)
    )
}

/**
 * 配对/连接项
 */
@Composable
fun PairingItem(
    isPaired: Boolean,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = {
            Text(if (isPaired) stringResource(R.string.connection_reconnect) else stringResource(R.string.connection_pair_device))
        },
        supportingContent = {
            Text(if (isPaired) stringResource(R.string.connection_reconnect_description) else stringResource(R.string.connection_pair_description))
        },
        leadingContent = {
            Icon(
                imageVector = if (isPaired) Icons.Default.Link else Icons.Default.QrCodeScanner,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    )
}

/**
 * Gateway 配置对话框
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GatewayConfigDialog(
    currentConfig: GatewayConfigInput,
    isPaired: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (GatewayConfigInput) -> Unit
) {
    var name by remember { mutableStateOf(currentConfig.name) }
    var host by remember { mutableStateOf(currentConfig.host) }
    var port by remember { mutableStateOf(currentConfig.port.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.connection_configure_gateway)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.connection_gateway_name)) },
                    placeholder = { Text(stringResource(R.string.connection_gateway_name_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text(stringResource(R.string.connection_gateway_host)) },
                    placeholder = { Text(stringResource(R.string.connection_gateway_host_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it },
                    label = { Text(stringResource(R.string.gateway_port)) },
                    placeholder = { Text(stringResource(R.string.gateway_port_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = stringResource(R.string.gateway_ws_preview, host.ifEmpty { "host" }, port.ifEmpty { "port" }),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        GatewayConfigInput(
                            name = name,
                            host = host,
                            port = port.toIntOrNull() ?: 18789
                        )
                    )
                }
            ) {
                Text(if (isPaired) stringResource(R.string.gateway_save_and_connect) else stringResource(R.string.gateway_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}