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
    val lifecycleOwner = LocalLifecycleOwner.current
    
    val messageFontSize by viewModel.messageFontSize.collectAsState(initial = FontSize.MEDIUM)

    // IME 状态检测
    val density = LocalDensity.current
    val imeHeightPx = WindowInsets.ime.getBottom(density)
    
    // 滚动状态追踪
    var currentSessionId by remember { mutableStateOf<String?>(null) }
    var hasScrolledOnEnter by remember { mutableStateOf(false) }
    var lastImeHeightPx by remember { mutableStateOf(0) }
    
    // 判断用户是否在底部附近
    val isNearBottom by remember { derivedStateOf { !listState.canScrollForward } }

    AppLog.d("SessionScreen", "=== Render: sessionId=$sessionId, messages=${state.chatMessages.size}, imeHeight=$imeHeightPx, isNearBottom=$isNearBottom")

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

    // 进入会话 - 重置状态
    LaunchedEffect(sessionId) {
        if (currentSessionId != sessionId) {
            AppLog.d("SessionScreen", "=== Session changed: $sessionId")
            currentSessionId = sessionId
            hasScrolledOnEnter = false
            lastImeHeightPx = 0
            viewModel.setSessionId(sessionId)
            focusRequester.requestFocus()
        }
    }

    // 场景1: 进入会话后滚动到底部
    LaunchedEffect(state.chatMessages.size, currentSessionId) {
        if (state.chatMessages.isNotEmpty() && !hasScrolledOnEnter && currentSessionId == sessionId) {
            AppLog.d("SessionScreen", "=== Try scroll on enter: messages=${state.chatMessages.size}")
            // 等待列表渲染完成
            delay(200)
            scrollToBottom(listState, imeHeightPx, "session enter")
            hasScrolledOnEnter = true
        }
    }

    // 场景2: 新消息到达时滚动
    LaunchedEffect(state.chatMessages.size) {
        if (state.chatMessages.isNotEmpty() && hasScrolledOnEnter) {
            AppLog.d("SessionScreen", "=== Messages changed: ${state.chatMessages.size}, isNearBottom=$isNearBottom")
            if (isNearBottom || imeHeightPx > 0) {
                delay(50)
                scrollToBottom(listState, imeHeightPx, "new message")
            }
        }
    }

    // 场景3: IME 状态变化时滚动
    LaunchedEffect(imeHeightPx) {
        AppLog.d("SessionScreen", "=== IME changed: $lastImeHeightPx -> $imeHeightPx")
        if (imeHeightPx > 0 && lastImeHeightPx == 0) {
            // IME 弹出
            if (state.chatMessages.isNotEmpty()) {
                delay(300)  // 等待 IME 动画
                scrollToBottom(listState, imeHeightPx, "IME shown")
            }
        } else if (imeHeightPx == 0 && lastImeHeightPx > 0) {
            // IME 收起
            if (state.chatMessages.isNotEmpty() && isNearBottom) {
                scrollToBottom(listState, 0, "IME hidden")
            }
        }
        lastImeHeightPx = imeHeightPx
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
 * @param imeHeightPx IME 高度（像素）
 * @param reason 日志原因
 */
private suspend fun scrollToBottom(
    listState: LazyListState,
    imeHeightPx: Int,
    reason: String
) {
    val totalItems = listState.layoutInfo.totalItemsCount
    if (totalItems <= 0) {
        AppLog.d("SessionScreen", "=== scrollToBottom: no items, skipping")
        return
    }
    
    val lastIndex = totalItems - 1
    
    // scrollOffset: 负值表示向上额外滚动
    // 让最后一个 item 的底部在 IME 上方可见
    val scrollOffset = if (imeHeightPx > 0) -imeHeightPx else 0
    
    AppLog.d("SessionScreen", "=== scrollToBottom: reason=$reason, totalItems=$totalItems, lastIndex=$lastIndex, scrollOffset=$scrollOffset")
    
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