package com.openclaw.clawchat.ui.screens.session

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.openclaw.clawchat.ui.state.ConnectionStatus

/**
 * 会话顶部导航栏
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionTopAppBar(
    connectionStatus: ConnectionStatus,
    onNavigateBack: () -> Unit,
    isSearchMode: Boolean = false,
    searchQuery: String = "",
    onSearchQueryChange: (String) -> Unit = {},
    onToggleSearch: () -> Unit = {}
) {
    if (isSearchMode) {
        // 搜索模式
        TopAppBar(
            title = {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("搜索消息...") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { }),
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchQueryChange("") }) {
                                Icon(Icons.Default.Close, "清除")
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )
            },
            navigationIcon = {
                IconButton(onClick = onToggleSearch) {
                    Icon(Icons.Default.ArrowBack, "关闭搜索")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
    } else {
        // 正常模式
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
                IconButton(onClick = onToggleSearch) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "搜索消息"
                    )
                }
                ConnectionStatusIcon(connectionStatus)
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                actionIconContentColor = MaterialTheme.colorScheme.onBackground
            )
        )
    }
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