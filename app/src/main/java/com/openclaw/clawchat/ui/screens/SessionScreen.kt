package com.openclaw.clawchat.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.openclaw.clawchat.data.FontSize
import com.openclaw.clawchat.ui.state.*
import com.openclaw.clawchat.ui.screens.session.*
import kotlinx.coroutines.launch

/**
 * 会话界面屏幕
 * 
 * 滚动逻辑：
 * 1. 新消息按钮点击 → 滚动到最底部
 * 2. 键盘弹出 → 系统自动调整布局（adjustResize）
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SessionScreen(
    viewModel: SessionViewModel,
    sessionId: String,
    onNavigateBack: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    
    val messageFontSize by viewModel.messageFontSize.collectAsState(initial = FontSize.MEDIUM)

    // 当前会话 ID
    var currentSessionId by remember { mutableStateOf<String?>(null) }
    
    // reverseLayout = true 后，滚动语义反转：
    // - canScrollForward: 可以向上滚动（查看历史）
    // - canScrollBackward: 可以向下滚动（查看新消息）
    // 显示"新消息"按钮当用户向上滚动查看历史时
    val showNewMessagesButton by remember { derivedStateOf { listState.canScrollBackward } }

    // 监听生命周期
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshMessages()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // 会话切换时重置状态
    LaunchedEffect(sessionId) {
        if (currentSessionId != sessionId) {
            currentSessionId = sessionId
            viewModel.setSessionId(sessionId)
            focusRequester.requestFocus()
        }
    }

    // 进入会话时滚动到最后一条消息
    // reverseLayout=true 模式下，index 0 是最新消息
    // 使用 sessionId 作为 key，确保每次进入会话都触发
    LaunchedEffect(sessionId, state.chatMessages.size) {
        if (state.chatMessages.isNotEmpty()) {
            // 等待 LazyColumn 完成布局
            kotlinx.coroutines.delay(100)
            // reverseLayout=true: index 0 是最新消息
            listState.scrollToItem(0)
            android.util.Log.d("SessionScreen", "Scrolled to bottom (index 0) for session $sessionId, messages=${state.chatMessages.size}")
        }
    }
    
    // 下拉刷新状态
    var isRefreshing by remember { mutableStateOf(false) }

    val messageGroups = remember(state.chatMessages) { groupMessages(state.chatMessages) }

    Scaffold(
        modifier = Modifier.background(MaterialTheme.colorScheme.background),
        topBar = {
            SessionTopAppBar(
                connectionStatus = state.connectionStatus,
                onNavigateBack = onNavigateBack
            )
        },
        contentWindowInsets = WindowInsets.systemBars
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    // 下拉刷新容器
                    PullToRefreshBox(
                        isRefreshing = isRefreshing,
                        onRefresh = {
                            isRefreshing = true
                            viewModel.loadEarlierMessages {
                                isRefreshing = false
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (state.chatMessages.isEmpty() && !state.isLoading) {
                            EmptySessionContent(connectionStatus = state.connectionStatus)
                        } else if (state.chatMessages.isNotEmpty()) {
                            MessageGroupList(
                                groups = messageGroups,
                                listState = listState,
                                streamSegments = state.chatStreamSegments,
                                toolMessages = state.chatToolMessages,
                                chatStream = state.chatStream,
                                messageFontSize = messageFontSize,
                                onDeleteMessage = { viewModel.deleteMessage(it) },
                                onRegenerate = { viewModel.regenerateLastMessage() },
                                onSpeak = { text ->
                                    com.openclaw.clawchat.util.MessageSpeaker.speak(text)
                                }
                            )
                        }
                    }

                    if (state.isLoading && !isRefreshing) {
                        LoadingOverlay()
                    }
                    
                    // "新消息"按钮：点击滚动到最底部（最新消息）
                    if (showNewMessagesButton) {
                        NewMessagesIndicator(
                            modifier = Modifier.align(Alignment.BottomCenter),
                            onClick = {
                                scope.launch {
                                    // reverseLayout 模式下，index 0 是最新消息
                                    listState.animateScrollToItem(0)
                                }
                            }
                        )
                    }
                }

                MessageInputBar(
                    value = state.inputText,
                    onValueChange = { viewModel.updateInputText(it) },
                    onSend = { 
                        viewModel.sendMessage(state.inputText) 
                    },
                    enabled = state.connectionStatus is ConnectionStatus.Connected && !state.isSending,
                    focusRequester = focusRequester,
                    attachments = state.attachments,
                    onAddAttachment = { viewModel.addAttachment(it) },
                    onRemoveAttachment = { viewModel.removeAttachment(it) },
                    onExecuteCommand = { cmd, args ->
                        viewModel.executeSlashCommand(cmd, args)
                    }
                )
                
                // 发送中指示器
                if (state.isSending) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                    )
                }
            }

            state.error?.let { error ->
                ErrorSnackbar(
                    message = error,
                    onDismiss = { viewModel.clearError() }
                )
            }
        }
    }
}

/**
 * 新消息提示按钮
 */
@Composable
private fun NewMessagesIndicator(
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.padding(bottom = 16.dp),
        color = MaterialTheme.colorScheme.primary,
        shape = MaterialTheme.shapes.small,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "新消息",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}

/**
 * 错误提示条
 */
@Composable
private fun ErrorSnackbar(
    message: String,
    onDismiss: () -> Unit
) {
    // 自动关闭
    LaunchedEffect(message) {
        kotlinx.coroutines.delay(5000)
        onDismiss()
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        shape = RoundedCornerShape(12.dp),
        onClick = onDismiss
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
                maxLines = 3
            )
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "关闭",
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}