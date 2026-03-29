package com.openclaw.clawchat.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
 * - 关于页面
 * - Debug 调试
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToDebug: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showGatewayDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showPairingSheet by remember { mutableStateOf(false) }
    var debugClickCount by remember { mutableStateOf(0) }

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

            // 关于区域
            SettingsSection(title = "关于") {
                ClickableSettingItem(
                    icon = Icons.Outlined.Info,
                    title = "ClawChat",
                    subtitle = "版本 ${state.appVersion}",
                    onClick = { 
                        showAboutDialog = true
                        // 连续点击 7 次进入 Debug
                        debugClickCount++
                        if (debugClickCount >= 7) {
                            debugClickCount = 0
                            onNavigateToDebug()
                        }
                    }
                )
                
                ClickableSettingItem(
                    icon = Icons.Outlined.Description,
                    title = "开源许可",
                    subtitle = "查看第三方库许可",
                    onClick = { /* 尚未实现 */ }
                )
                
                // Debug 入口（开发版显示）
                if (com.openclaw.clawchat.BuildConfig.DEBUG) {
                    ClickableSettingItem(
                        icon = Icons.Outlined.BugReport,
                        title = "Debug",
                        subtitle = "调试和诊断工具",
                        onClick = onNavigateToDebug
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // Gateway 配置对话框
    if (showGatewayDialog) {
        GatewayConfigDialog(
            currentConfig = state.gatewayConfigInput,
            isPaired = state.isPaired,
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
}