package com.openclaw.clawchat.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.openclaw.clawchat.ui.state.ConnectionStatusUi
import com.openclaw.clawchat.ui.state.GatewayConfigUi
import com.openclaw.clawchat.ui.state.getStatusColor
import java.text.SimpleDateFormat
import java.util.*

/**
 * 设置页面屏幕
 * 
 * 功能：
 * - Gateway 配置管理
 * - 通知设置
 * - 安全设置
 * - 关于页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onShowPairing: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showGatewayDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            // Gateway 配置区域
            SettingsSection(title = "连接") {
                GatewayConfigItem(
                    gateway = state.currentGateway,
                    connectionStatus = state.connectionStatus,
                    onClick = { showGatewayDialog = true }
                )
                
                if (state.connectionStatus.isConnected) {
                    DisconnectItem(
                        onDisconnect = { viewModel.disconnect() }
                    )
                }
            }

            // 通知设置区域
            SettingsSection(title = "通知") {
                ToggleSettingItem(
                    icon = Icons.Outlined.Notifications,
                    title = "推送通知",
                    subtitle = "接收新消息通知",
                    checked = state.notificationsEnabled,
                    onCheckedChange = { viewModel.toggleNotifications(it) }
                )
                
                ToggleSettingItem(
                    icon = Icons.Outlined.DoNotDisturb,
                    title = "勿扰模式",
                    subtitle = "定时静音通知",
                    checked = state.dndEnabled,
                    onCheckedChange = { viewModel.toggleDnd(it) }
                )
            }

            // 安全设置区域
            SettingsSection(title = "安全") {
                ClickableSettingItem(
                    icon = Icons.Outlined.Security,
                    title = "设备信息",
                    subtitle = "查看设备 ID 和配对状态",
                    onClick = { /* TODO: 显示设备信息 */ }
                )
                
                ClickableSettingItem(
                    icon = Icons.Outlined.Fingerprint,
                    title = "生物识别",
                    subtitle = "使用指纹/面部解锁应用",
                    onClick = { /* TODO: 生物识别设置 */ }
                )
            }

            // 关于区域
            SettingsSection(title = "关于") {
                ClickableSettingItem(
                    icon = Icons.Outlined.Info,
                    title = "ClawChat",
                    subtitle = "版本 ${state.appVersion}",
                    onClick = { showAboutDialog = true }
                )
                
                ClickableSettingItem(
                    icon = Icons.Outlined.Description,
                    title = "开源许可",
                    subtitle = "查看第三方库许可",
                    onClick = { /* TODO: 显示许可 */ }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // Gateway 配置对话框
    if (showGatewayDialog) {
        GatewayConfigDialog(
            currentConfig = state.gatewayConfigInput,
            onDismiss = { showGatewayDialog = false },
            onSave = { config ->
                viewModel.updateGatewayConfig(config)
                showGatewayDialog = false
            }
        )
    }

    // 关于对话框
    if (showAboutDialog) {
        AboutDialog(
            onDismiss = { showAboutDialog = false },
            onShowPairing = {
                showAboutDialog = false
                onShowPairing()
            }
        )
    }
}

/**
 * 设置区域容器
 */
@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ) {
            Column(content = content)
        }
        
        Divider(modifier = Modifier.padding(vertical = 8.dp))
    }
}

/**
 * Gateway 配置项
 */
@Composable
private fun GatewayConfigItem(
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
private fun DisconnectItem(
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
 * 开关设置项
 */
@Composable
private fun ToggleSettingItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        },
        modifier = Modifier.fillMaxWidth()
    )
}

/**
 * 可点击设置项
 */
@Composable
private fun ClickableSettingItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingContent = {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "进入",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
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
private fun GatewayConfigDialog(
    currentConfig: GatewayConfigInput,
    onDismiss: () -> Unit,
    onSave: (GatewayConfigInput) -> Unit
) {
    var name by remember { mutableStateOf(currentConfig.name) }
    var host by remember { mutableStateOf(currentConfig.host) }
    var port by remember { mutableStateOf(currentConfig.port.toString()) }
    var useTls by remember { mutableStateOf(currentConfig.useTls) }

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
                    placeholder = { Text("例如：192.168.1.100 或 gateway.tailnet-name.ts.net") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    OutlinedTextField(
                        value = port,
                        onValueChange = { port = it },
                        label = { Text("端口") },
                        placeholder = { Text("18789") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = useTls,
                            onCheckedChange = { useTls = it }
                        )
                        Text("TLS")
                    }
                }
                
                Text(
                    text = if (useTls) "wss://${host.ifEmpty { "host" }}:${port.ifEmpty { "port" }}/ws"
                           else "ws://${host.ifEmpty { "host" }}:${port.ifEmpty { "port" }}/ws",
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
                            port = port.toIntOrNull() ?: 18789,
                            useTls = useTls
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

/**
 * 关于对话框
 */
@Composable
private fun AboutDialog(
    onDismiss: () -> Unit,
    onShowPairing: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.Info,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = { Text("ClawChat") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("OpenClaw Android 客户端")
                Text("版本 1.0.0")
                Divider()
                Text(
                    "ClawChat 是 OpenClaw 生态系统的官方 Android 客户端，提供与 OpenClaw Gateway 的实时通信。",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onShowPairing) {
                Text("设备配对")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}
