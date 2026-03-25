package com.openclaw.clawchat.ui.screens

import androidx.compose.foundation.background
import com.openclaw.clawchat.util.AppLog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.openclaw.clawchat.data.FontSize
import com.openclaw.clawchat.ui.state.*
import com.openclaw.clawchat.ui.screens.session.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 会话界面屏幕
 * 
 * 滚动逻辑设计：
 * 1. 进入会话：滚动到底部
 * 2. 新消息到达：如果用户在底部附近，滚动；否则保持当前位置
 * 3. IME 弹出：滚动到底部 + IME 偏移
 * 4. IME 收起：滚动到底部
 * 5. 用户在中间位置：新消息不强制滚动（除非 IME 可见）
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
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    
    val messageFontSize by viewModel.messageFontSize.collectAsState(initial = FontSize.MEDIUM)

    // IME 状态检测
    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)
    val imeVisible = imeBottom > 0
    
    // 滚动状态追踪
    var lastSessionId by remember { mutableStateOf<String?>(null) }
    var lastMessageCount by remember { mutableStateOf(0) }
    var wasImeVisible by remember { mutableStateOf(false) }
    
    // 判断用户是否在底部附近（允许一定误差）
    val isNearBottom by remember { derivedStateOf { !listState.canScrollForward } }

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

    // 场景 1: 进入会话 - 设置 sessionId
    LaunchedEffect(sessionId) {
        if (lastSessionId != sessionId) {
            AppLog.d("SessionScreen", "Session changed: $sessionId")
            viewModel.setSessionId(sessionId)
            focusRequester.requestFocus()
            lastSessionId = sessionId
            lastMessageCount = 0
        }
    }
    
    // 进入会话后，消息加载完成时滚动到底部
    LaunchedEffect(state.chatMessages.isNotEmpty(), sessionId) {
        if (state.chatMessages.isNotEmpty() && lastSessionId == sessionId && lastMessageCount == 0) {
            delay(100)  // 等待列表渲染
            scrollToBottom(listState, imeBottom, "session enter")
            lastMessageCount = state.chatMessages.size
        }
    }

    // 场景 2: 新消息到达
    LaunchedEffect(state.chatMessages.size) {
        val currentCount = state.chatMessages.size
        if (currentCount > lastMessageCount && lastMessageCount > 0) {
            // 有新消息
            if (isNearBottom || imeVisible) {
                // 用户在底部附近或 IME 可见，滚动到底部
                scrollToBottom(listState, imeBottom, "new message")
            }
            // 否则保持当前位置（场景 5）
        }
        lastMessageCount = currentCount
    }

    // 场景 3 & 4: IME 状态变化
    LaunchedEffect(imeVisible, imeBottom) {
        if (imeVisible && !wasImeVisible) {
            // IME 弹出
            if (state.chatMessages.isNotEmpty()) {
                scrollToBottom(listState, imeBottom, "IME shown")
            }
        } else if (!imeVisible && wasImeVisible) {
            // IME 收起
            if (state.chatMessages.isNotEmpty() && isNearBottom) {
                scrollToBottom(listState, 0, "IME hidden")
            }
        }
        wasImeVisible = imeVisible
    }

    // 监听事件
    LaunchedEffect(viewModel.events) {
        viewModel.events.collect { event ->
            when (event) {
                is SessionEvent.MessageReceived -> {
                    // 收到消息事件，如果用户在底部附近则滚动
                    if (isNearBottom || imeVisible) {
                        scrollToBottom(listState, imeBottom, "message received event")
                    }
                }
                else -> {}
            }
        }
    }

    val messageGroups = remember(state.chatMessages) { groupMessages(state.chatMessages) }

    Scaffold(
        modifier = Modifier.background(MaterialTheme.colorScheme.background),
        topBar = {
            SessionTopAppBar(
                connectionStatus = state.connectionStatus,
                onNavigateBack = onNavigateBack
            )
        },
        contentWindowInsets = WindowInsets.systemBars.union(WindowInsets.ime)
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
                            onRegenerate = { viewModel.regenerateLastMessage() }
                        )
                    }

                    if (state.isLoading) {
                        LoadingOverlay()
                    }
                }

                MessageInputBar(
                    value = state.inputText,
                    onValueChange = { viewModel.updateInputText(it) },
                    onSend = { viewModel.sendMessage(state.inputText) },
                    enabled = state.connectionStatus is ConnectionStatus.Connected && !state.isSending,
                    focusRequester = focusRequester,
                    attachments = state.attachments,
                    onAddAttachment = { viewModel.addAttachment(it) },
                    onRemoveAttachment = { viewModel.removeAttachment(it) },
                    onExecuteCommand = { cmd, args ->
                        viewModel.executeSlashCommand(cmd, args)
                    }
                )
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
 * 滚动到底部
 * @param imeBottom IME 底部高度（用于偏移）
 * @param reason 日志原因
 */
private suspend fun scrollToBottom(
    listState: LazyListState,
    imeBottom: Int,
    reason: String
) {
    val lastIndex = listState.layoutInfo.totalItemsCount - 1
    if (lastIndex < 0) return
    
    // scrollOffset 为负值表示向上额外滚动（IME 偏移）
    val scrollOffset = if (imeBottom > 0) -imeBottom else 0
    
    listState.animateScrollToItem(
        index = lastIndex,
        scrollOffset = scrollOffset
    )
    AppLog.d("SessionScreen", "Scrolled to bottom: reason=$reason, imeOffset=$scrollOffset")
}

/**
 * 错误提示条
 */
@Composable
private fun ErrorSnackbar(
    message: String,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
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
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "关闭",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}