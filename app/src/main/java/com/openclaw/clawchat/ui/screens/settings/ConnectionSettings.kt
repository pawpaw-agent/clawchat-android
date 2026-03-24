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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
                text = gateway?.name ?: "未配置",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            Column {
                Text(
                    text = gateway?.host ?: "点击配置 Gateway 地址",
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
            Text("断开连接")
        },
        supportingContent = {
            Text("断开与当前 Gateway 的连接")
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
            Text(if (isPaired) "重新连接" else "配对设备")
        },
        supportingContent = {
            Text(if (isPaired) "连接到已配对的 Gateway" else "扫描二维码或输入配对码")
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
    onDismiss: () -> Unit,
    onSave: (GatewayConfigInput) -> Unit
) {
    var name by remember { mutableStateOf(currentConfig.name) }
    var host by remember { mutableStateOf(currentConfig.host) }
    var port by remember { mutableStateOf(currentConfig.port.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("配置 Gateway") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称") },
                    placeholder = { Text("例如：Home Server") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("主机地址") },
                    placeholder = { Text("例如：192.168.1.100") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it },
                    label = { Text("端口") },
                    placeholder = { Text("18789") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                
                Text(
                    text = "ws://${host.ifEmpty { "host" }}:${port.ifEmpty { "port" }}/ws",
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
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}