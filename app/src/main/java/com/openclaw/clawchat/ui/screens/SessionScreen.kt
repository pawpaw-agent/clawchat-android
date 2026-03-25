package com.openclaw.clawchat.ui.screens

import androidx.compose.foundation.background
import com.openclaw.clawchat.util.AppLog
import androidx.compose.foundation.layout.*
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
import kotlinx.coroutines.launch

/**
 * 会话界面屏幕（1:1 复刻 webchat）
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

    // 统一的滚动状态管理
    var lastSessionId by remember { mutableStateOf<String?>(null) }
    var lastMessageCount by remember { mutableStateOf(0) }

    // IME 高度检测（需要在滚动逻辑之前）
    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)
    val imeVisible = imeBottom > 0
    var wasImeVisible by remember { mutableStateOf(false) }

    // 监听生命周期，onResume 时刷新消息
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                AppLog.d("SessionScreen", "onResume: refreshing messages")
                viewModel.refreshMessages()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // 设置会话 ID
    LaunchedEffect(sessionId) {
        AppLog.d("SessionScreen", "LaunchedEffect: sessionId=$sessionId")
        viewModel.setSessionId(sessionId)
        focusRequester.requestFocus()
        lastSessionId = sessionId
    }

    // 统一的滚动逻辑：首次加载、消息增加、sessionId 变化
    // 使用 imeBottom 作为偏移量，确保滚动到 IME 上方
    LaunchedEffect(state.chatMessages.size, sessionId, imeBottom) {
        val isFirstLoad = lastSessionId != sessionId
        val hasNewMessages = state.chatMessages.size > lastMessageCount
        
        if (state.chatMessages.isNotEmpty() && (isFirstLoad || hasNewMessages || (imeVisible && !wasImeVisible))) {
            // 等待列表渲染
            kotlinx.coroutines.delay(50)
            // 计算 scrollOffset：如果 IME 可见，使用负值向上额外滚动
            val scrollOffset = if (imeBottom > 0) -imeBottom else 0
            listState.scrollToItem(state.chatMessages.lastIndex, scrollOffset)
            AppLog.d("SessionScreen", "Scrolled to bottom: isFirstLoad=$isFirstLoad, hasNewMessages=$hasNewMessages, imeOffset=$scrollOffset")
        }
        
        lastMessageCount = state.chatMessages.size
        lastSessionId = sessionId
    }

    // 更新 IME 状态
    LaunchedEffect(imeVisible) {
        wasImeVisible = imeVisible
    }

    // 监听事件
    LaunchedEffect(viewModel.events) {
        viewModel.events.collect { event ->
            when (event) {
                is SessionEvent.Error -> { }
                is SessionEvent.MessageReceived -> {
                    if (listState.canScrollForward) {
                        val scrollOffset = if (imeBottom > 0) -imeBottom else 0
                        listState.animateScrollToItem(state.chatMessages.lastIndex, scrollOffset)
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