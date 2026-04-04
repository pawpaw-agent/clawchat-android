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
import com.openclaw.clawchat.ui.components.LoadingSkeleton
import com.openclaw.clawchat.ui.components.SkeletonType
import com.openclaw.clawchat.ui.components.ContextNotice
import com.openclaw.clawchat.ui.components.CompactionIndicator
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

    // 搜索状态
    var isSearchMode by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    // 会话切换时重置状态
    LaunchedEffect(sessionId) {
        if (currentSessionId != sessionId) {
            currentSessionId = sessionId
            viewModel.setSessionId(sessionId)
            focusRequester.requestFocus()
            // 切换会话时重置搜索
            isSearchMode = false
            searchQuery = ""
        }
    }

    // 进入会话时直接显示最新消息
    // 使用 scrollToItem 不带动画，瞬间定位避免视觉跳跃
    // 注意：MessageGroupList 也有滚动逻辑，这里只处理初始加载
    var hasInitializedScroll by remember(sessionId) { mutableStateOf(false) }
    
    // 当消息加载完成时，立即滚动到底部
    LaunchedEffect(sessionId) {
        // 等待消息加载完成（isLoading=false 且有消息）
        snapshotFlow { state.isLoading to state.chatMessages.size }
            .collect { (loading, size) ->
                if (!loading && size > 0 && !hasInitializedScroll) {
                    // 直接定位，不等待布局完成，避免视觉跳跃
                    // reverseLayout=true: scrollToItem(0) 滚动到最新消息
                    listState.scrollToItem(0)
                    hasInitializedScroll = true
                    return@collect  // 只执行一次
                }
            }
    }

    // P1-3: 使用 derivedStateOf 优化消息分组计算，避免不必要的重组
    val messageGroups by remember { derivedStateOf { groupMessages(state.chatMessages) } }

    // 搜索过滤消息
    val filteredMessages by remember(state.chatMessages, searchQuery) {
        derivedStateOf {
            if (searchQuery.isBlank()) {
                state.chatMessages
            } else {
                state.chatMessages.filter { message ->
                    message.content.any { content ->
                        when (content) {
                            is com.openclaw.clawchat.ui.state.MessageContentItem.Text ->
                                content.text.contains(searchQuery, ignoreCase = true)
                            else -> false
                        }
                    }
                }
            }
        }
    }
    val filteredGroups by remember { derivedStateOf { groupMessages(filteredMessages) } }

    Scaffold(
        modifier = Modifier.background(MaterialTheme.colorScheme.background),
        topBar = {
            SessionTopAppBar(
                connectionStatus = state.connectionStatus,
                onNavigateBack = onNavigateBack,
                isSearchMode = isSearchMode,
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                onToggleSearch = {
                    isSearchMode = !isSearchMode
                    if (!isSearchMode) {
                        searchQuery = ""
                    }
                }
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
                    // Context 用量警告和 Compaction 指示器（顶部）
                    Column(
                        modifier = Modifier.align(Alignment.TopCenter)
                    ) {
                        // Compaction 指示器
                        CompactionIndicator(
                            active = state.compactionActive,
                            completedAt = state.compactionCompletedAt
                        )
                        
                        // Context 用量警告（>= 85%）
                        val totalTokens = state.totalTokens
                        val contextTokensLimit = state.contextTokensLimit
                        if (totalTokens != null && contextTokensLimit != null && state.totalTokensFresh) {
                            val ratio = totalTokens.toFloat() / contextTokensLimit.toFloat()
                            if (ratio >= 0.85f) {
                                ContextNotice(
                                    totalTokens = totalTokens,
                                    contextTokensLimit = contextTokensLimit
                                )
                            }
                        }
                    }
                    
                    if (state.isLoading && state.chatMessages.isEmpty()) {
                        // 加载骨架屏
                        LoadingSkeleton(
                            type = SkeletonType.MESSAGE,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else if (state.chatMessages.isEmpty()) {
                        EmptySessionContent(connectionStatus = state.connectionStatus)
                    } else if (filteredGroups.isNotEmpty()) {
                        MessageGroupList(
                            groups = filteredGroups,
                            listState = listState,
                            streamSegments = state.chatStreamSegments,
                            toolMessages = state.chatToolMessages,
                            chatStream = state.chatStream,
                            messageFontSize = messageFontSize,
                            // 滚动状态（参考 webchat app-scroll.ts）
                            chatUserNearBottom = state.chatUserNearBottom,
                            chatHasAutoScrolled = state.chatHasAutoScrolled,
                            chatNewMessagesBelow = state.chatNewMessagesBelow,
                            onUpdateUserNearBottom = { viewModel.updateUserNearBottom(it) },
                            onMarkAutoScrolled = { viewModel.markAutoScrolled() },
                            onSetNewMessagesBelow = { viewModel.setNewMessagesBelow() },
                            onDeleteMessage = { viewModel.deleteMessage(it) },
                            onRegenerate = { viewModel.regenerateLastMessage() },
                            onRetryMessage = { viewModel.retryMessage(it) },
                            onContinueGeneration = { viewModel.continueGeneration() }
                        )
                    } else if (isSearchMode && searchQuery.isNotBlank()) {
                        // 搜索无结果
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Spacer(modifier = Modifier.height(48.dp))
                            Icon(
                                imageVector = Icons.Default.SearchOff,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "未找到 \"$searchQuery\"",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (state.isLoading) {
                        LoadingOverlay()
                    }
                    
                    // "新消息"按钮：点击滚动到最底部（最新消息）
                    // 显示条件：用户向上滚动 OR 有新消息在下方
                    if (showNewMessagesButton || state.chatNewMessagesBelow) {
                        NewMessagesIndicator(
                            modifier = Modifier.align(Alignment.BottomCenter),
                            onClick = {
                                scope.launch {
                                    // reverseLayout 模式下，index 0 是最新消息
                                    listState.animateScrollToItem(0)
                                }
                                viewModel.clearNewMessagesBelow()
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