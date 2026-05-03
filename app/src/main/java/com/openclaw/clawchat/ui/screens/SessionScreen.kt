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
import kotlinx.coroutines.isActive
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.openclaw.clawchat.R
import com.openclaw.clawchat.data.FontSize
import com.openclaw.clawchat.ui.components.LoadingSkeleton
import com.openclaw.clawchat.ui.components.SkeletonType
import com.openclaw.clawchat.ui.components.ContextNotice
import com.openclaw.clawchat.ui.components.CompactionIndicator
import com.openclaw.clawchat.ui.components.FallbackIndicator
import com.openclaw.clawchat.ui.components.NetworkStatusBanner
import com.openclaw.clawchat.ui.state.ConnectionStatus
import com.openclaw.clawchat.ui.state.MainViewModel
import com.openclaw.clawchat.ui.state.SessionViewModel
import com.openclaw.clawchat.ui.theme.DesignTokens
import com.openclaw.clawchat.ui.theme.ChatTokens
import com.openclaw.clawchat.ui.screens.session.*
import com.openclaw.clawchat.util.AppLog
import kotlinx.coroutines.launch

/**
 * 会话界面屏幕
 *
 * 滚动逻辑：
 * 1. 新消息按钮点击 → 滚动到最底部
 * 2. 键盘弹出 → 自动滚动到底部
 * 3. 系统布局调整（adjustResize）
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SessionScreen(
    viewModel: SessionViewModel,
    sessionId: String,
    onNavigateBack: () -> Unit,
    mainViewModel: MainViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val mainState by mainViewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    val messageFontSize by viewModel.messageFontSize.collectAsState(initial = FontSize.MEDIUM)

    // 当前会话 ID（从 ViewModel 状态获取，避免进程死亡后丢失）
    val stateSessionId = state.sessionId

    // 检测键盘是否可见（用于其他用途）
    val imeVisible = WindowInsets.ime.getBottom(density) > 0

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

    // 从 MainViewModel 获取当前会话数据（包含 token 信息）
    val currentSession = remember(sessionId, mainState.sessions) {
        mainState.sessions.find { it.key == sessionId }
    }

    // 会话切换时更新 ViewModel
    // 使用 ViewModel 的 sessionId 状态来判断是否需要切换，避免进程死亡后重复清除
    LaunchedEffect(sessionId, stateSessionId, currentSession) {
        // 只在导航传入的 sessionId 与 ViewModel 状态不同时才调用 setSessionId
        // 进程死亡后 ViewModel 会从 SavedStateHandle 恢复，此时 stateSessionId == sessionId
        if (stateSessionId != sessionId) {
            AppLog.d("SessionScreen", "=== sessionId changed: $stateSessionId -> $sessionId")
            viewModel.setSessionId(sessionId)
            focusRequester.requestFocus()
            // 切换会话时重置搜索
            isSearchMode = false
            searchQuery = ""
        }

        // 更新 session 数据（包含 token 信息）
        currentSession?.let { viewModel.setSession(it) }
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
                },
                // 会话信息
                sessionLabel = state.session?.label,
                agentId = state.session?.agentId,
                agentName = state.session?.agentName,
                agentEmoji = state.session?.agentEmoji,
                currentModel = state.session?.model,
                thinkingLevel = state.session?.thinkingLevel,
                startedAt = state.session?.startedAt,
                messageCount = state.chatMessages.size
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
                        // 网络状态横幅（连接断开/错误时显示）
                        NetworkStatusBanner(
                            status = state.connectionStatus,
                            onRetry = { viewModel.retryConnection() }
                        )

                        // Compaction 指示器
                        CompactionIndicator(
                            compactionStatus = state.compactionStatus
                        )

                        // Fallback 指示器（模型切换通知）
                        state.fallbackStatus?.let { fallback ->
                            FallbackIndicator(
                                phase = fallback.phase,
                                selected = fallback.selected,
                                active = fallback.active,
                                previous = fallback.previous,
                                reason = fallback.reason,
                                attempts = fallback.attempts,
                                occurredAt = fallback.occurredAt
                            )
                        }

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
                        // 空会话内容（带 Welcome suggestions）
                        EmptySessionContent(
                            connectionStatus = state.connectionStatus,
                            assistantName = state.session?.agentName,
                            assistantEmoji = state.session?.agentEmoji,
                            onSuggestionClick = { suggestion ->
                                viewModel.sendMessage(suggestion)
                            }
                        )
                    } else if (filteredGroups.isNotEmpty()) {
                        MessageGroupList(
                            groups = filteredGroups,
                            listState = listState,
                            streamSegments = state.chatStreamSegments,
                            toolMessages = state.chatToolMessages,
                            toolStreamById = state.toolStreamById,  // 实时工具流状态
                            chatStream = state.chatStream,
                            messageFontSize = messageFontSize,
                            // 滚动状态（参考 webchat app-scroll.ts 和 Stream SDK）
                            chatUserNearBottom = state.chatUserNearBottom,
                            chatHasAutoScrolled = state.chatHasAutoScrolled,
                            chatNewMessagesBelow = state.chatNewMessagesBelow,
                            onUpdateUserNearBottom = { viewModel.updateUserNearBottom(it) },
                            onMarkAutoScrolled = { viewModel.markAutoScrolled() },
                            onSetNewMessagesBelow = { viewModel.setNewMessagesBelow() },
                            onUserScrolledAway = { viewModel.setUserScrolledAway() },
                            onDeleteMessage = { viewModel.deleteMessage(it) },
                            onEditMessage = { viewModel.startEditMessage(it) },
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
                                text = stringResource(R.string.session_search_no_results, searchQuery),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (state.isLoading) {
                        LoadingOverlay()
                    }
                    
                    // "新消息"按钮：点击滚动到最底部（最新消息）
                    // 显示条件：有新消息在下方（由 ViewModel 统一管理）
                    if (state.chatNewMessagesBelow) {
                        NewMessagesIndicator(
                            modifier = Modifier.align(Alignment.BottomCenter),
                            count = state.unreadMessageCount,
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

                EnhancedMessageInputBar(
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

            // 编辑消息对话框
            state.editingMessageId?.let { messageId ->
                EditMessageDialog(
                    initialText = state.editingMessageText ?: "",
                    onDismiss = { viewModel.cancelEdit() },
                    onConfirm = { newText ->
                        viewModel.editMessage(messageId, newText)
                    }
                )
            }
        }
    }
}

/**
 * 新消息提示按钮
 * 参考 Stream SDK: ScrollToBottomButton
 */
@Composable
private fun NewMessagesIndicator(
    modifier: Modifier = Modifier,
    count: Int = 0,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.padding(bottom = DesignTokens.space4),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = RoundedCornerShape(DesignTokens.radiusFull),
        shadowElevation = DesignTokens.elevationSm,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = DesignTokens.space4,
                vertical = DesignTokens.space2
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.space1)
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = if (count > 0) stringResource(R.string.session_new_messages_count, count)
                       else stringResource(R.string.session_new_messages),
                style = MaterialTheme.typography.labelMedium
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
    onDismiss: () -> Unit,
    onRetry: (() -> Unit)? = null
) {
    // 自动关闭
    LaunchedEffect(message) {
        kotlinx.coroutines.delay(8000)
        if (isActive) {
            onDismiss()
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(DesignTokens.space4),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = RoundedCornerShape(DesignTokens.radiusMd),
        shadowElevation = DesignTokens.elevationSm
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(DesignTokens.space3),
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
                maxLines = 3
            )

            // 重试按钮
            if (onRetry != null) {
                TextButton(
                    onClick = {
                        onDismiss()
                        onRetry()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Text(stringResource(R.string.retry))
                }
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.close),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * 编辑消息对话框
 */
@Composable
private fun EditMessageDialog(
    initialText: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(initialText) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.session_edit_message)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 100.dp),
                placeholder = { Text(stringResource(R.string.session_edit_placeholder)) },
                maxLines = 10
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (text.isNotBlank()) {
                        onConfirm(text)
                    }
                }
            ) {
                Text(stringResource(R.string.session_edit_send))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.session_edit_cancel))
            }
        }
    )
}