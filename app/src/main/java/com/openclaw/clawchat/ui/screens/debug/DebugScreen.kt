package com.openclaw.clawchat.ui.screens.debug

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openclaw.clawchat.R
import com.openclaw.clawchat.ui.state.DebugUiState
import com.openclaw.clawchat.ui.state.DebugViewModel
import com.openclaw.clawchat.ui.state.LogLine
import com.openclaw.clawchat.ui.state.LogLevel
import com.openclaw.clawchat.util.CrashHandler
import com.openclaw.clawchat.util.CrashReport

/**
 * Debug 调试页面
 *
 * 功能：
 * - 连接诊断
 * - 日志查看器
 * - 性能指标
 * - 消息详情
 * - 崩溃报告
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugScreen(
    onNavigateBack: () -> Unit,
    viewModel: DebugViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        stringResource(R.string.debug_tab_connection),
        stringResource(R.string.debug_tab_logs),
        stringResource(R.string.debug_tab_performance),
        stringResource(R.string.debug_tab_messages),
        stringResource(R.string.debug_tab_crashes)
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.debug_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.debug_back))
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Tab 栏
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }

            // Tab 内容
            when (selectedTab) {
                0 -> ConnectionDiagnosticsTab(state)
                1 -> LogViewerTab(state, viewModel)
                2 -> PerformanceTab(state)
                3 -> MessageDetailsTab(state)
                4 -> CrashReportTab(viewModel)
            }
        }
    }
}

/**
 * 连接诊断 Tab
 */
@Composable
private fun ConnectionDiagnosticsTab(state: DebugUiState) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = when {
                        state.connectionState == "Connected" || state.connectionState == stringResource(R.string.status_connected) -> MaterialTheme.colorScheme.primaryContainer
                        state.connectionState == "Connecting" || state.connectionState == stringResource(R.string.status_connecting) -> MaterialTheme.colorScheme.secondaryContainer
                        else -> MaterialTheme.colorScheme.errorContainer
                    }
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.debug_connection_status, state.connectionState),
                        style = MaterialTheme.typography.titleMedium
                    )
                    state.connectionLatency?.let { latency ->
                        Text(
                            text = stringResource(R.string.status_latency, latency),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Text(
                        text = stringResource(R.string.debug_reconnect_attempts, state.reconnectAttempts),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        item {
            Text(
                text = stringResource(R.string.debug_gateway),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }

        item {
            Text(
                text = state.gatewayUrl ?: stringResource(R.string.debug_not_configured),
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace
            )
        }

        if (state.lastConnectedTime != null) {
            item {
                val timeStr = java.text.SimpleDateFormat("HH:mm:ss").format(java.util.Date(state.lastConnectedTime))
                Text(
                    text = stringResource(R.string.debug_last_connection, timeStr),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        if (state.connectionErrors.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.debug_error_history),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error
                )
            }

            items(state.connectionErrors.takeLast(10)) { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

/**
 * 日志查看器 Tab
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LogViewerTab(
    state: DebugUiState,
    viewModel: DebugViewModel
) {
    val listState = rememberLazyListState()

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // 控制栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 日志级别选择
            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it },
                modifier = Modifier.width(120.dp)
            ) {
                OutlinedTextField(
                    value = state.logLevel.name,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    LogLevel.entries.forEach { level ->
                        DropdownMenuItem(
                            text = { Text(level.name) },
                            onClick = {
                                viewModel.setLogLevel(level)
                                expanded = false
                            }
                        )
                    }
                }
            }

            // 搜索框
            OutlinedTextField(
                value = state.logSearchQuery,
                onValueChange = { viewModel.setLogSearchQuery(it) },
                placeholder = { Text(stringResource(R.string.debug_search)) },
                modifier = Modifier.weight(1f),
                singleLine = true
            )

            // 自动滚动开关
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.debug_auto_scroll), style = MaterialTheme.typography.bodySmall)
                Switch(
                    checked = state.autoScroll,
                    onCheckedChange = { viewModel.toggleAutoScroll() }
                )
            }
        }

        // 日志列表
        val filteredLogs = remember(state.logLines, state.logSearchQuery) {
            state.logLines.filter {
                state.logSearchQuery.isBlank() ||
                it.message.contains(state.logSearchQuery, ignoreCase = true)
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
        ) {
            items(
                items = filteredLogs
            ) { logLine ->
                Text(
                    text = "${logLine.timestamp} ${logLine.level.name.first()} ${logLine.message}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = when (logLine.level) {
                        LogLevel.VERBOSE -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        LogLevel.DEBUG -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        LogLevel.INFO -> MaterialTheme.colorScheme.primary
                        LogLevel.WARN -> MaterialTheme.colorScheme.tertiary
                        LogLevel.ERROR -> MaterialTheme.colorScheme.error
                    },
                    modifier = Modifier.padding(vertical = 1.dp)
                )
            }
        }
    }
}

/**
 * 性能指标 Tab
 */
@Composable
private fun PerformanceTab(state: DebugUiState) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 内存
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.debug_memory), style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    val usedMB = state.usedMemory
                    val maxMB = state.maxMemory
                    val percent = if (state.maxMemory > 0) {
                        (state.usedMemory * 100 / state.maxMemory).toInt()
                    } else 0

                    Text(stringResource(R.string.debug_memory_used, usedMB.toInt(), maxMB.toInt(), percent))
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { percent / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // FPS
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.debug_fps_title), style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(stringResource(R.string.debug_fps, state.fps))
                }
            }
        }

        // 应用信息
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.debug_app), style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(stringResource(R.string.debug_app_uptime, state.appUptime))
                }
            }
        }
    }
}

/**
 * 消息详情 Tab
 */
@Composable
private fun MessageDetailsTab(state: DebugUiState) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                text = stringResource(R.string.debug_recent_messages),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }

        if (state.recentMessages.isEmpty()) {
            item {
                Text(stringResource(R.string.debug_no_messages), style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            items(
                items = state.recentMessages,
                key = { msg -> msg.id }
            ) { msg ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = msg.timestamp.toString(),
                            style = MaterialTheme.typography.labelSmall
                        )
                        Text(
                            text = stringResource(R.string.debug_status, msg.status),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = stringResource(R.string.debug_message_size, msg.size),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

/**
 * Crash Report Tab
 */
@Composable
private fun CrashReportTab(viewModel: DebugViewModel) {
    val context = LocalContext.current
    var crashReport by remember { mutableStateOf<CrashReport?>(null) }

    LaunchedEffect(Unit) {
        crashReport = CrashHandler.getCrashReport(context)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (crashReport == null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(stringResource(R.string.debug_no_crash_report), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        } else {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                stringResource(R.string.debug_last_crash),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                crashReport?.formattedTime ?: "",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            crashReport?.message ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            item {
                Text(
                    text = stringResource(R.string.debug_stack_trace),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = crashReport?.stackTrace ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            item {
                Button(
                    onClick = {
                        CrashHandler.clearCrashReport(context)
                        crashReport = null
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.debug_clear_crash))
                }
            }
        }
    }
}