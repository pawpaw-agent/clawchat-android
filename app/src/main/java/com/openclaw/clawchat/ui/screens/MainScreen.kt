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
import com.openclaw.clawchat.ui.state.ConnectionStatus
import com.openclaw.clawchat.ui.state.MainViewModel
import com.openclaw.clawchat.ui.state.SessionStatus
import com.openclaw.clawchat.ui.state.SessionUi
import com.openclaw.clawchat.ui.state.UiEvent
import com.openclaw.clawchat.ui.screens.settings.SettingsScreen

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
    onNavigateToDebug: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showSettings by remember { mutableStateOf(false) }
    var showSessionOptions by remember { mutableStateOf<SessionUi?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var showCommandPalette by remember { mutableStateOf(false) }
    var commandPaletteQuery by remember { mutableStateOf("") }
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    
    // 过滤会话列表
    val filteredSessions = remember(state.sessions, searchQuery) {
        if (searchQuery.isBlank()) {
            state.sessions
        } else {
            state.sessions.filter { session ->
                session.getDisplayName().contains(searchQuery, ignoreCase = true) ||
                session.lastMessage?.contains(searchQuery, ignoreCase = true) == true
            }
        }
    }

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
        },
        floatingActionButton = {}
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
                        state = state.copy(sessions = filteredSessions),
                        searchQuery = searchQuery,
                        onSearchQueryChange = { searchQuery = it },
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
        }
    }

    // 设置页面
    if (showSettings) {
        SettingsScreen(
            onNavigateBack = { showSettings = false },
            onNavigateToDebug = onNavigateToDebug
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
                    contentDescription = "设置"
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