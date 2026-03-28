package com.openclaw.clawchat.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
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
    
    // 是否显示"新消息"按钮：只要没滑到底部就显示
    val showNewMessagesButton by remember { derivedStateOf { listState.canScrollForward } }

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

    // 监听消息变化：在底部时自动滚动到最新
    val messageCount = state.chatMessages.size
    var lastMessageCount by remember { mutableStateOf(0) }
    
    LaunchedEffect(messageCount) {
        // 只在新消息增加时自动滚动（排除加载旧消息的情况）
        if (messageCount > lastMessageCount && messageCount > 0) {
            lastMessageCount = messageCount
            // 用户在底部，自动滚动到最新消息
            if (!listState.canScrollForward) {
                val lastIndex = listState.layoutInfo.totalItemsCount - 1
                if (lastIndex >= 0) {
                    listState.scrollToItem(lastIndex)
                }
            }
        }
    }
    
    // 监听流式响应：平滑滚动到底部
    // 流式过程中用 scrollToItem（无动画，避免卡顿）
    // 流式结束时用 animateScrollToItem（平滑过渡）
    val chatStream = state.chatStream
    var wasStreaming by remember { mutableStateOf(false) }
    val isStreaming = chatStream != null && chatStream.isNotBlank()
    
    // 流式响应内容更新：无动画滚动（高频更新，避免卡顿）
    LaunchedEffect(chatStream) {
        if (isStreaming) {
            // 流式过程中直接跳转，无动画
            val lastIndex = listState.layoutInfo.totalItemsCount - 1
            if (lastIndex >= 0) {
                listState.scrollToItem(lastIndex)
            }
            wasStreaming = true
        }
    }
    
    // 流式响应结束：平滑动画滚动（低频，视觉效果好）
    LaunchedEffect(isStreaming) {
        if (!isStreaming && wasStreaming) {
            // 流式结束，平滑滚动到底部确认
            scope.launch {
                val lastIndex = listState.layoutInfo.totalItemsCount - 1
                if (lastIndex >= 0) {
                    listState.animateScrollToItem(lastIndex)
                }
            }
            wasStreaming = false
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

                    if (state.isLoading) {
                        LoadingOverlay()
                    }
                    
                    // "新消息"按钮：点击滚动到最底部
                    if (showNewMessagesButton) {
                        NewMessagesIndicator(
                            modifier = Modifier.align(Alignment.BottomCenter),
                            onClick = {
                                scope.launch {
                                    // 使用 animateScrollToItem 滚动到最后
                                    listState.animateScrollToItem(Int.MAX_VALUE, scrollOffset = 0)
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