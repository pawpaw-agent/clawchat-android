package com.openclaw.clawchat.ui.screens.session

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import com.openclaw.clawchat.ui.state.ConnectionStatus

/**
 * 会话顶部导航栏
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionTopAppBar(
    connectionStatus: ConnectionStatus,
    onNavigateBack: () -> Unit,
    onSettingsClick: () -> Unit = {}
) {
    TopAppBar(
        title = { Text("会话") },
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "返回"
                )
            }
        },
        actions = {
            // 设置按钮
            IconButton(onClick = onSettingsClick) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "会话设置"
                )
            }
            // 连接状态图标
            ConnectionStatusIcon(connectionStatus)
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            actionIconContentColor = MaterialTheme.colorScheme.onBackground
        )
    )
}

/**
 * 连接状态图标
 */
@Composable
private fun ConnectionStatusIcon(status: ConnectionStatus) {
    val (icon, color) = when (status) {
        is ConnectionStatus.Connected -> Icons.Default.CheckCircle to MaterialTheme.colorScheme.tertiary
        is ConnectionStatus.Connecting, is ConnectionStatus.Disconnecting -> Icons.Default.Sync to MaterialTheme.colorScheme.error
        is ConnectionStatus.Disconnected -> Icons.Default.CloudOff to MaterialTheme.colorScheme.onSurfaceVariant
        is ConnectionStatus.Error -> Icons.Default.Error to MaterialTheme.colorScheme.error
        else -> Icons.Default.Help to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Icon(
        imageVector = icon,
        contentDescription = "连接状态",
        tint = color
    )
}