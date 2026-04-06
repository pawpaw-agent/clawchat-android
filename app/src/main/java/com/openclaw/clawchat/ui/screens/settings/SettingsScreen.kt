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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openclaw.clawchat.R
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
    onNavigateToDebug: () -> Unit = {},
    onNavigateToCron: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showGatewayDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showPairingSheet by remember { mutableStateOf(false) }
    var debugClickCount by remember { mutableStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, stringResource(R.string.settings_back))
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
            SettingsSection(title = stringResource(R.string.settings_section_connection)) {
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
            SettingsSection(title = stringResource(R.string.settings_section_display)) {
                // 动态颜色（Android 12+）
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    ToggleSettingItem(
                        icon = Icons.Outlined.Palette,
                        title = stringResource(R.string.settings_dynamic_color),
                        subtitle = stringResource(R.string.settings_dynamic_color_desc),
                        checked = state.dynamicColor,
                        onCheckedChange = { viewModel.setDynamicColor(it) }
                    )
                }

                ThemeModeSettingItem(
                    title = stringResource(R.string.settings_theme_mode),
                    subtitle = stringResource(R.string.settings_theme_mode_desc),
                    currentMode = state.themeMode,
                    onModeChange = { viewModel.setThemeMode(it) }
                )

                FontSizeSettingItem(
                    title = stringResource(R.string.settings_font_size),
                    subtitle = stringResource(R.string.settings_font_size_desc),
                    currentSize = state.messageFontSize,
                    onSizeChange = { viewModel.setMessageFontSize(it) }
                )

                ThemeColorSettingItem(
                    title = stringResource(R.string.settings_theme_color),
                    subtitle = stringResource(R.string.settings_theme_color_desc),
                    currentColorIndex = state.themeColorIndex,
                    onColorChange = { viewModel.setThemeColor(it) }
                )
            }

            // 通知设置区域
            SettingsSection(title = stringResource(R.string.settings_section_notifications)) {
                ToggleSettingItem(
                    icon = Icons.Outlined.Notifications,
                    title = stringResource(R.string.settings_push_notifications),
                    subtitle = stringResource(R.string.settings_push_notifications_desc),
                    checked = state.notificationsEnabled,
                    onCheckedChange = { viewModel.toggleNotifications(it) }
                )

                ToggleSettingItem(
                    icon = Icons.Outlined.DoNotDisturb,
                    title = stringResource(R.string.settings_dnd_mode),
                    subtitle = stringResource(R.string.settings_dnd_mode_desc),
                    checked = state.dndEnabled,
                    onCheckedChange = { viewModel.toggleDnd(it) }
                )
            }

            // 自动化区域
            SettingsSection(title = stringResource(R.string.settings_section_automation)) {
                ClickableSettingItem(
                    icon = Icons.Outlined.Schedule,
                    title = stringResource(R.string.settings_scheduled_tasks),
                    subtitle = stringResource(R.string.settings_scheduled_tasks_desc),
                    onClick = onNavigateToCron
                )
            }

            // 关于区域
            SettingsSection(title = stringResource(R.string.settings_section_security)) {
                SecurityStatusItem(
                    isRooted = state.isRooted,
                    rootRiskLevel = state.rootRiskLevel
                )
            }

            // 关于区域
            SettingsSection(title = stringResource(R.string.settings_section_about)) {
                ClickableSettingItem(
                    icon = Icons.Outlined.Info,
                    title = "ClawChat",
                    subtitle = stringResource(R.string.settings_version, state.appVersion),
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
                    title = stringResource(R.string.settings_open_source_licenses),
                    subtitle = stringResource(R.string.settings_open_source_licenses_desc),
                    onClick = { /* 尚未实现 */ }
                )

                // Debug 入口（开发版显示）
                if (com.openclaw.clawchat.BuildConfig.DEBUG) {
                    ClickableSettingItem(
                        icon = Icons.Outlined.BugReport,
                        title = "Debug",
                        subtitle = stringResource(R.string.settings_debug_subtitle),
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