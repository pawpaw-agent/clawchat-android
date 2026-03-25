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

    // IME 状态检测 - 使用 withDensity 确保正确的像素值
    val density = LocalDensity.current
    val imeHeight = WindowInsets.ime.getBottom(density)
    val imeVisible = imeHeight > 0
    
    // 滚动状态追踪
    var lastSessionId by remember { mutableStateOf<String?>(null) }
    var lastMessageCount by remember { mutableStateOf(0) }
    var wasImeVisible by remember { mutableStateOf(false) }
    
    // 判断用户是否在底部附近
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

    // 进入会话
    LaunchedEffect(sessionId) {
        if (lastSessionId != sessionId) {
            AppLog.d("SessionScreen", "Session changed: $sessionId")
            viewModel.setSessionId(sessionId)
            focusRequester.requestFocus()
            lastSessionId = sessionId
            lastMessageCount = 0
        }
    }
    
    // 进入会话后滚动到底部
    LaunchedEffect(state.chatMessages.isNotEmpty(), sessionId) {
        if (state.chatMessages.isNotEmpty() && lastSessionId == sessionId && lastMessageCount == 0) {
            delay(100)
            scrollToBottom(listState, imeHeight, "session enter")
            lastMessageCount = state.chatMessages.size
        }
    }

    // 新消息到达
    LaunchedEffect(state.chatMessages.size) {
        val currentCount = state.chatMessages.size
        if (currentCount > lastMessageCount && lastMessageCount > 0) {
            if (isNearBottom || imeVisible) {
                scrollToBottom(listState, imeHeight, "new message")
            }
        }
        lastMessageCount = currentCount
    }

    // IME 状态变化 - 关键修复
    LaunchedEffect(imeVisible) {
        if (imeVisible && !wasImeVisible) {
            // IME 弹出：等待动画完成后滚动
            if (state.chatMessages.isNotEmpty()) {
                delay(150)  // 等待 IME 动画
                // 使用之前获取的 imeHeight（在 Composable 上下文中）
                AppLog.d("SessionScreen", "IME shown: height=$imeHeight")
                scrollToBottom(listState, imeHeight, "IME shown")
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
                    if (isNearBottom || imeVisible) {
                        scrollToBottom(listState, imeHeight, "message received")
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
 * 
 * @param imeHeight IME 高度（像素）
 * @param reason 日志原因
 */
private suspend fun scrollToBottom(
    listState: LazyListState,
    imeHeight: Int,
    reason: String
) {
    // 获取实际的 item 总数
    val totalItems = listState.layoutInfo.totalItemsCount
    if (totalItems <= 0) return
    
    val lastIndex = totalItems - 1
    
    // scrollOffset: 负值表示向上额外滚动
    // IME 高度需要作为负偏移，让消息在键盘上方可见
    val scrollOffset = if (imeHeight > 0) -imeHeight else 0
    
    AppLog.d("SessionScreen", "scrollToBottom: reason=$reason, lastIndex=$lastIndex, imeHeight=$imeHeight, scrollOffset=$scrollOffset")
    
    // 使用 scrollToItem 而不是 animateScrollToItem 以获得精确控制
    listState.scrollToItem(
        index = lastIndex,
        scrollOffset = scrollOffset
    )
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