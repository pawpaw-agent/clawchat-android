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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    val tabs = listOf("连接", "日志", "性能", "消息", "崩溃")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Debug") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
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
                        state.connectionState == "Connected" -> MaterialTheme.colorScheme.primaryContainer
                        state.connectionState == "Connecting" -> MaterialTheme.colorScheme.secondaryContainer
                        else -> MaterialTheme.colorScheme.errorContainer
                    }
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "状态: ${state.connectionState}",
                        style = MaterialTheme.typography.titleMedium
                    )
                    state.connectionLatency?.let { latency ->
                        Text(
                            text = "延迟: ${latency}ms",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Text(
                        text = "重连次数: ${state.reconnectAttempts}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        item {
            Text(
                text = "Gateway",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }

        item {
            Text(
                text = state.gatewayUrl ?: "未配置",
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace
            )
        }

        if (state.lastConnectedTime != null) {
            item {
                Text(
                    text = "最后连接: ${java.text.SimpleDateFormat("HH:mm:ss").format(java.util.Date(state.lastConnectedTime))}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        if (state.connectionErrors.isNotEmpty()) {
            item {
                Text(
                    text = "错误历史",
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
                placeholder = { Text("搜索") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )

            // 自动滚动开关
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("自动滚动", style = MaterialTheme.typography.bodySmall)
                Switch(
                    checked = state.autoScroll,
                    onCheckedChange = { viewModel.toggleAutoScroll() }
                )
            }
        }

        // 日志列表
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
        ) {
            items(
                items = state.logLines.filter {
                    state.logSearchQuery.isBlank() || 
                    it.message.contains(state.logSearchQuery, ignoreCase = true)
                }
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
                    Text("内存", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    val usedMB = state.usedMemory / 1024 / 1024
                    val maxMB = state.maxMemory / 1024 / 1024
                    val percent = if (state.maxMemory > 0) {
                        (state.usedMemory * 100 / state.maxMemory).toInt()
                    } else 0
                    
                    Text("已用: ${usedMB}MB / ${maxMB}MB ($percent%)")
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
                    Text("帧率", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("${state.fps} FPS")
                }
            }
        }

        // 应用信息
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("应用", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("运行时间: ${state.appUptime}秒")
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
                text = "最近消息",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }

        if (state.recentMessages.isEmpty()) {
            item {
                Text("暂无消息", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            items(state.recentMessages, key = { it.id }) { msg ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = msg.timestamp,
                            style = MaterialTheme.typography.labelSmall
                        )
                        Text(
                            text = "状态: ${msg.status}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "大小: ${msg.size} bytes",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

/**
 * 崩溃报告 Tab
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
                        Text("没有崩溃记录", style = MaterialTheme.typography.titleMedium)
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
                                "上次崩溃",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                crashReport!!.formattedTime,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            crashReport!!.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            item {
                Text(
                    text = "堆栈跟踪",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = crashReport!!.stackTrace,
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
                    Text("清除崩溃记录")
                }
            }
        }
    }
}