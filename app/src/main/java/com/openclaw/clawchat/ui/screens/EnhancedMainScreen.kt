package com.openclaw.clawchat.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.openclaw.clawchat.ui.state.MainViewModel
import com.openclaw.clawchat.ui.components.SessionList
import com.openclaw.clawchat.ui.components.BatchSessionOperations
import com.openclaw.clawchat.ui.state.SessionUi

/**
 * 主屏幕 - 实现完整的 webchat 功能对等
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onNavigateToSession: (String) -> Unit,
    onNavigateToDebug: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // 选择状态
    var selectedSessions by remember { mutableStateOf(setOf<String>()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ClawChat") },
                actions = {
                    IconButton(onClick = { onNavigateToDebug() }) {
                        Icon(Icons.Default.BugReport, contentDescription = "调试")
                    }
                    IconButton(onClick = { /* TODO: 添加搜索功能 */ }) {
                        Icon(Icons.Default.Search, contentDescription = "搜索")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.createNewSession() },
                icon = { Icon(Icons.Default.Add, contentDescription = "新建会话") },
                text = { Text("新建会话") }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column {
                // 批量操作工具栏
                BatchSessionOperations(
                    selectedSessions = selectedSessions,
                    onSelectAll = { /* 实现全选逻辑 */ },
                    onDeselectAll = { selectedSessions = emptySet() },
                    onDeleteSelected = {
                        // 删除选中的会话
                        selectedSessions.forEach { sessionId ->
                            viewModel.deleteSession(sessionId)
                        }
                        selectedSessions = emptySet()
                    }
                )

                // 会话列表
                SessionList(
                    sessions = state.sessions,
                    onSessionClick = { session ->
                        if (selectedSessions.isNotEmpty()) {
                            // 如果处于选择模式，切换选中状态
                            selectedSessions = if (selectedSessions.contains(session.id)) {
                                selectedSessions - session.id
                            } else {
                                selectedSessions + session.id
                            }
                        } else {
                            // 否则导航到会话
                            onNavigateToSession(session.id)
                        }
                    },
                    onSessionRename = { session ->
                        // TODO: 实现重命名功能
                    },
                    onSessionDelete = { session ->
                        viewModel.deleteSession(session.id)
                    }
                )
            }
        }
    }
}