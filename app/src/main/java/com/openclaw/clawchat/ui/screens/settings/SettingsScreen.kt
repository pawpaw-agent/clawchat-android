package com.openclaw.clawchat.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openclaw.clawchat.data.FontSize
import com.openclaw.clawchat.data.ThemeMode

/**
 * 设置页面屏幕
 * 
 * 功能：
 * - Gateway 配置管理
 * - 配对功能
 * - 通知设置
 * - 安全设置
 * - 关于页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showGatewayDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showPairingSheet by remember { mutableStateOf(false) }
    var showDeviceInfo by remember { mutableStateOf(false) }

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
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
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
                
                // 配对/连接按钮
                if (!state.connectionStatus.isConnected) {
                    PairingItem(
                        isPaired = state.isPaired,
                        onClick = { showPairingSheet = true }
                    )
                } else {
                    DisconnectItem(
                        onDisconnect = { viewModel.disconnect() }
                    )
                }
            }

            // 显示设置区域
            SettingsSection(title = "显示") {
                ThemeModeSettingItem(
                    title = "主题模式",
                    subtitle = "选择应用主题外观",
                    currentMode = state.themeMode,
                    onModeChange = { viewModel.setThemeMode(it) }
                )

                FontSizeSettingItem(
                    title = "消息字体大小",
                    subtitle = "调整所有消息的字体大小",
                    currentSize = state.messageFontSize,
                    onSizeChange = { viewModel.setMessageFontSize(it) }
                )
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
                    onClick = { showDeviceInfo = true }
                )
                
                ClickableSettingItem(
                    icon = Icons.Outlined.Fingerprint,
                    title = "生物识别",
                    subtitle = "使用指纹/面部解锁应用",
                    onClick = { /* 功能开发中 */ }
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
            onDismiss = { showAboutDialog = false }
        )
    }
    
    // 配对底部抽屉
    if (showPairingSheet) {
        PairingBottomSheet(
            onDismiss = { showPairingSheet = false },
            onPairingSuccess = {
                showPairingSheet = false
                viewModel.refreshConnectionState()
            }
        )
    }
    
    // 设备信息对话框
    if (showDeviceInfo) {
        DeviceInfoDialog(
            isPaired = state.isPaired,
            onDismiss = { showDeviceInfo = false }
        )
    }
}

/**
 * 设备信息对话框
 */
@Composable
private fun DeviceInfoDialog(
    isPaired: Boolean,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.Devices,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = { Text("设备信息") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("配对状态", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        if (isPaired) "已配对" else "未配对",
                        color = if (isPaired) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }
                
                HorizontalDivider()
                
                Text(
                    "此设备已与 OpenClaw Gateway 配对，可以接收和发送消息。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}