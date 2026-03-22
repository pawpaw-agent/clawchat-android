package com.openclaw.clawchat.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openclaw.clawchat.ui.state.ConnectionStatus
import com.openclaw.clawchat.ui.state.MainViewModel
import com.openclaw.clawchat.ui.state.SessionUi
import com.openclaw.clawchat.ui.state.UiEvent
import com.openclaw.clawchat.ui.screens.settings.SettingsScreen
import com.openclaw.clawchat.ui.theme.DesignTokens
import java.text.SimpleDateFormat
import java.util.*

/**
 * 主界面屏幕
 * 
 * 显示：
 * - 连接状态
 * - 会话列表
 * - 创建新会话
 * - 导航到设置
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onNavigateToSession: (String) -> Unit,
    onDisconnect: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showSettings by remember { mutableStateOf(false) }
    var showSessionOptions by remember { mutableStateOf<SessionUi?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    // 监听事件
    LaunchedEffect(viewModel.events) {
        viewModel.events.collect { event ->
            when (event) {
                is UiEvent.ConnectionLost -> {
                    onDisconnect()
                }
                is UiEvent.NavigateToSession -> {
                    onNavigateToSession(event.sessionId)
                }
                else -> {}
            }
            viewModel.consumeEvent()
        }
    }

    // 会话选项对话框
    showSessionOptions?.let { session ->
        SessionOptionsDialog(
            session = session,
            onDismiss = { showSessionOptions = null },
            onRename = { newName ->
                viewModel.renameSession(session.id, newName)
                showSessionOptions = null
            },
            onPauseResume = {
                if (session.status == com.openclaw.clawchat.ui.state.SessionStatus.RUNNING) {
                    viewModel.pauseSession(session.id)
                } else {
                    viewModel.resumeSession(session.id)
                }
                showSessionOptions = null
            },
            onTerminate = {
                viewModel.terminateSession(session.id)
                showSessionOptions = null
            },
            onDelete = {
                viewModel.deleteSession(session.id)
                showSessionOptions = null
            }
        )
    }

    Scaffold(
        topBar = {
            ClawTopAppBar(
                connectionStatus = state.connectionStatus,
                latency = state.latency,
                onSettingsClick = { showSettings = true }
            )
        },
        floatingActionButton = {
            if (state.connectionStatus.isConnected) {
                ExtendedFloatingActionButton(
                    onClick = { viewModel.createSession() },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("新会话") }
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                state.connectionStatus !is ConnectionStatus.Connected -> {
                    // 未连接状态
                    NotConnectedContent(
                        connectionStatus = state.connectionStatus,
                        onDisconnect = onDisconnect
                    )
                }
                state.sessions.isEmpty() -> {
                    // 空会话列表
                    EmptySessionList(
                        onCreateSession = { viewModel.createSession() }
                    )
                }
                else -> {
                    // 会话列表
                    Column {
                        // 搜索栏
                        if (state.sessions.isNotEmpty()) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                placeholder = { Text("搜索会话...") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = null
                                    )
                                },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = DesignTokens.accent,
                                    unfocusedBorderColor = DesignTokens.border
                                )
                            )
                        }

                        // 会话列表
                        SessionList(
                            sessions = filterSessions(state.sessions, searchQuery),
                            currentSession = state.currentSession,
                            onSelectSession = { viewModel.selectSession(it) },
                            onSessionLongPress = { showSessionOptions = it }
                        )
                    }
                }
            }
        }
    }

    // 设置页面
    if (showSettings) {
        SettingsScreen(
            onNavigateBack = { showSettings = false },
            onShowPairing = { 
                showSettings = false
                onDisconnect()
            }
        )
    }
}

/**
 * 顶部导航栏
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClawTopAppBar(
    connectionStatus: ConnectionStatus,
    latency: Long?,
    onSettingsClick: () -> Unit
) {
    TopAppBar(
        title = {
            Column {
                Text("ClawChat")
                if (latency != null) {
                    Text(
                        text = "${latency}ms",
                        style = MaterialTheme.typography.bodySmall,
                        color = DesignTokens.muted
                    )
                }
            }
        },
        actions = {
            // 连接状态指示器
            ConnectionStatusIcon(connectionStatus)
            
            IconButton(onClick = onSettingsClick) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "设置"
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = DesignTokens.accentContainer,
            titleContentColor = DesignTokens.textStrong,
            actionIconContentColor = DesignTokens.textStrong
        )
    )
}

/**
 * 连接状态图标
 */
@Composable
private fun ConnectionStatusIcon(status: ConnectionStatus) {
    val (icon, color) = when (status) {
        is ConnectionStatus.Connected -> Icons.Default.CheckCircle to DesignTokens.accent
        is ConnectionStatus.Connecting, is ConnectionStatus.Disconnecting -> Icons.Default.Sync to DesignTokens.warn
        is ConnectionStatus.Disconnected -> Icons.Default.CloudOff to DesignTokens.muted
        is ConnectionStatus.Error -> Icons.Default.Error to DesignTokens.danger
        else -> Icons.Default.Help to DesignTokens.muted
    }

    Icon(
        imageVector = icon,
        contentDescription = "连接状态",
        tint = color
    )
}

/**
 * 未连接状态内容
 */
@Composable
private fun NotConnectedContent(
    connectionStatus: ConnectionStatus,
    onDisconnect: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.CloudOff,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = DesignTokens.muted
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = when (connectionStatus) {
                is ConnectionStatus.Disconnected -> "未连接到网关"
                is ConnectionStatus.Connecting -> "正在连接..."
                is ConnectionStatus.Disconnecting -> "正在断开..."
                is ConnectionStatus.Error -> "连接错误：${connectionStatus.message}"
                is ConnectionStatus.Connected -> "已连接"
                else -> "未知状态"
            },
            style = MaterialTheme.typography.titleMedium,
            color = DesignTokens.text
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        if (connectionStatus is ConnectionStatus.Disconnected || connectionStatus is ConnectionStatus.Error) {
            Button(onClick = onDisconnect) {
                Text("返回配对")
            }
        }
    }
}

/**
 * 空会话列表
 */
@Composable
private fun EmptySessionList(
    onCreateSession: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.ChatBubbleOutline,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = DesignTokens.muted
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "暂无会话",
            style = MaterialTheme.typography.titleMedium,
            color = DesignTokens.text
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "点击 + 创建新会话",
            style = MaterialTheme.typography.bodyMedium,
            color = DesignTokens.muted
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(onClick = onCreateSession) {
            Text("创建会话")
        }
    }
}

/**
 * 会话列表
 */
@Composable
private fun SessionList(
    sessions: List<SessionUi>,
    currentSession: SessionUi?,
    onSelectSession: (String) -> Unit,
    onSessionLongPress: (SessionUi) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(sessions, key = { it.id }) { session ->
            SessionItem(
                session = session,
                isSelected = session.id == currentSession?.id,
                onSelect = { onSelectSession(session.id) },
                onSessionLongPress = { onSessionLongPress(session) }
            )
        }
    }
}

/**
 * 会话列表项
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SessionItem(
    session: SessionUi,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onSessionLongPress: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .let {
                if (onSessionLongPress != null) {
                    it.combinedClickable(
                        onClick = onSelect,
                        onLongClick = onSessionLongPress
                    )
                } else {
                    it.clickable(onClick = onSelect)
                }
            },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                DesignTokens.accentContainer
            } else {
                DesignTokens.bg
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // 会话名称（显示 agent 名称）
            Text(
                text = session.getDisplayName(),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            // 时间和消息数
            Row {
                Text(
                    text = formatTimeAgo(session.lastActivityAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = DesignTokens.muted
                )
                
                if (session.messageCount > 0) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "• ${session.messageCount} 条消息",
                        style = MaterialTheme.typography.bodySmall,
                        color = DesignTokens.muted
                    )
                }
            }
            
            // 最后一条消息
            if (session.lastMessage != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = session.lastMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = DesignTokens.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * 格式化时间为"多久以前"
 */
private fun formatTimeAgo(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    
    return when {
        diff < 60_000 -> "刚刚"
        diff < 3600_000 -> "${diff / 60_000} 分钟前"
        diff < 86400_000 -> "${diff / 3600_000} 小时前"
        diff < 604800_000 -> "${diff / 86400_000} 天前"
        else -> SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(java.util.Date(timestamp))
    }
}

/**
 * 过滤会话列表
 */
private fun filterSessions(sessions: List<SessionUi>, query: String): List<SessionUi> {
    if (query.isBlank()) {
        return sessions
    }
    val lowerQuery = query.lowercase()
    return sessions.filter { session ->
        session.getDisplayName().lowercase().contains(lowerQuery) ||
        session.label?.lowercase()?.contains(lowerQuery) == true ||
        session.lastMessage?.lowercase()?.contains(lowerQuery) == true
    }
}

/**
 * 会话选项对话框
 */
@Composable
private fun SessionOptionsDialog(
    session: SessionUi,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
    onPauseResume: () -> Unit,
    onTerminate: () -> Unit,
    onDelete: () -> Unit
) {
    var showRenameDialog by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf(session.label ?: session.getDisplayName()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("会话选项") },
        text = {
            Column {
                Text(
                    text = session.getDisplayName(),
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "状态：${session.status.name}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = DesignTokens.muted
                )
                Text(
                    text = "消息数：${session.messageCount}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = DesignTokens.muted
                )
            }
        },
        confirmButton = {
            Column {
                Button(
                    onClick = onPauseResume,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DesignTokens.card
                    )
                ) {
                    Text(
                        if (session.status == com.openclaw.clawchat.ui.state.SessionStatus.RUNNING) {
                            "暂停会话"
                        } else {
                            "恢复会话"
                        }
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { showRenameDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("重命名")
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onTerminate,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = DesignTokens.danger
                    )
                ) {
                    Text("终止会话")
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = DesignTokens.danger
                    )
                ) {
                    Text("删除会话")
                }
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )

    // 重命名对话框
    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("重命名会话") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("会话名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = { onRename(newName) },
                    enabled = newName.isNotBlank()
                ) {
                    Text("确认")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showRenameDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}
