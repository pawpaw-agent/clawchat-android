package com.openclaw.clawchat.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openclaw.clawchat.R
import com.openclaw.clawchat.ui.state.ConnectionStatus
import com.openclaw.clawchat.ui.state.MainViewModel
import com.openclaw.clawchat.ui.state.SessionStatus
import com.openclaw.clawchat.ui.state.SessionUi
import com.openclaw.clawchat.ui.state.UiEvent
import com.openclaw.clawchat.ui.state.UpdateInfo
import com.openclaw.clawchat.ui.screens.settings.SettingsScreen
import com.openclaw.clawchat.ui.theme.DesignTokens

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
    onNavigateToDebug: () -> Unit = {},
    onNavigateToCron: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showSettings by remember { mutableStateOf(false) }
    var showSessionOptions by remember { mutableStateOf<SessionUi?>(null) }
    var showCommandPalette by remember { mutableStateOf(false) }
    var commandPaletteQuery by remember { mutableStateOf("") }
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current

    // 监听生命周期，onResume 时检查并重连
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.checkAndReconnectIfNeeded()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // 监听事件
    LaunchedEffect(viewModel.events) {
        viewModel.events.collect { event ->
            when (event) {
                is UiEvent.NavigateToSession -> {
                    onNavigateToSession(event.sessionId)
                }
                else -> {}
            }
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
                if (session.status == SessionStatus.RUNNING) {
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
                onSettingsClick = { showSettings = true },
                onCommandPaletteClick = { showCommandPalette = true }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                state.connectionStatus !is ConnectionStatus.Connected && state.sessions.isEmpty() -> {
                    // 未连接且无会话列表（首次使用）
                    NotConnectedContent(
                        connectionStatus = state.connectionStatus,
                        onOpenSettings = { showSettings = true }
                    )
                }
                else -> {
                    // 显示会话列表（即使未连接也显示缓存的会话）
                    SessionListContent(
                        state = state,
                        onSelectSession = { viewModel.selectSession(it) },
                        onSessionLongPress = { showSessionOptions = it },
                        onCreateSession = { viewModel.showCreateSessionDialog() },
                        onRefresh = { viewModel.refreshSessions() },
                        onDeleteSession = { viewModel.deleteSession(it) },
                        onSteerSession = { sessionKey, text -> viewModel.steerSession(sessionKey, text) },
                        onRenameSession = { sessionKey, newLabel -> viewModel.renameSession(sessionKey, newLabel) },
                        onTogglePinSession = { sessionKey, currentPinned -> viewModel.toggleSessionPin(sessionKey, currentPinned) }
                    )
                }
            }
            
            // 连接错误 Banner - 放在 Box 最上层，确保始终可见
            if (state.connectionError != null) {
                ConnectionErrorBanner(
                    error = state.connectionError!!,
                    onRetry = { viewModel.retryConnection() },
                    onDismiss = { viewModel.clearConnectionError() }
                )
            }

            // 更新通知 Banner
            state.updateAvailable?.let { updateInfo ->
                UpdateNotificationBanner(
                    updateInfo = updateInfo,
                    onUpdate = { viewModel.runUpdate() },
                    onDismiss = { viewModel.dismissUpdate() }
                )
            }
        }
    }

    // 设置页面
    if (showSettings) {
        SettingsScreen(
            onNavigateBack = { showSettings = false },
            onNavigateToDebug = onNavigateToDebug,
            onNavigateToCron = onNavigateToCron
        )
    }
    
    // 命令面板 (⌘K)
    if (showCommandPalette) {
        val sessionItems = state.sessions.map { session ->
            com.openclaw.clawchat.ui.components.CommandPaletteItem.SessionItem(
                id = session.id,
                title = session.getDisplayName(),
                lastMessage = session.lastMessage,
                timestamp = session.lastActivityAt
            )
        }

        com.openclaw.clawchat.ui.components.CommandPalette(
            query = commandPaletteQuery,
            onQueryChange = { commandPaletteQuery = it },
            sessions = sessionItems,
            commands = com.openclaw.clawchat.ui.components.getDefaultCommands(context),
            onSessionSelect = { sessionId ->
                showCommandPalette = false
                commandPaletteQuery = ""
                viewModel.selectSession(sessionId)
                onNavigateToSession(sessionId)
            },
            onCommandExecute = { commandId ->
                showCommandPalette = false
                commandPaletteQuery = ""
                when (commandId) {
                    "new-session" -> viewModel.showCreateSessionDialog()
                    "settings" -> showSettings = true
                    "clear-chat" -> viewModel.clearCurrentSession()
                    "debug" -> onNavigateToDebug()
                }
            },
            onDismiss = {
                showCommandPalette = false
                commandPaletteQuery = ""
            }
        )
    }

    // 创建会话对话框
    if (state.showCreateDialog) {
        com.openclaw.clawchat.ui.components.CreateSessionDialog(
            agents = state.agents,
            models = state.models,
            onDismiss = { viewModel.hideCreateSessionDialog() },
            onCreate = { agentId, model, initialMessage, label ->
                viewModel.createSessionWithAgentModel(agentId, model, initialMessage, label)
            },
            isLoading = state.isLoadingAgentsModels
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
    onSettingsClick: () -> Unit,
    onCommandPaletteClick: () -> Unit = {}
) {
    TopAppBar(
        title = {
            Column {
                Text(stringResource(R.string.app_name))
                if (latency != null) {
                    Text(
                        text = "${latency}ms",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        actions = {
            // 搜索按钮（命令面板）
            IconButton(onClick = onCommandPaletteClick) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = stringResource(R.string.debug_search)
                )
            }
            
            // 连接状态指示器
            ConnectionStatusIcon(connectionStatus)
            
            IconButton(onClick = onSettingsClick) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = stringResource(R.string.settings_label)
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            actionIconContentColor = MaterialTheme.colorScheme.onSurface
        )
    )
}

/**
 * 更新通知提示条
 */
@Composable
private fun UpdateNotificationBanner(
    updateInfo: UpdateInfo,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(DesignTokens.space2),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = RoundedCornerShape(DesignTokens.radiusMd),
        shadowElevation = DesignTokens.elevationSm
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(DesignTokens.space3),
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.SystemUpdate,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.update_available_title),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (updateInfo.version.isNotEmpty()) stringResource(R.string.update_available_version, updateInfo.version)
                    else stringResource(R.string.update_available_message),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            TextButton(onClick = onUpdate) {
                Text(stringResource(R.string.update_run))
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.close),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}