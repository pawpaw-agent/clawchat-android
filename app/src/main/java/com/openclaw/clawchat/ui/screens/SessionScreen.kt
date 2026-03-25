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
 * 
 * 滚动逻辑严格参考 webchat app-scroll.ts 实现
 * 
 * Webchat 关键常量和状态：
 * - NEAR_BOTTOM_THRESHOLD = 450px
 * - chatHasAutoScrolled: 首次加载后设为 true
 * - chatUserNearBottom: 用户是否在底部附近
 * - chatNewMessagesBelow: 有新消息在下方（用户滚动到中间时）
 * 
 * 滚动条件：
 * - distanceFromBottom = scrollHeight - scrollTop - clientHeight
 * - effectiveForce = force && !chatHasAutoScrolled
 * - shouldStick = effectiveForce || chatUserNearBottom || distanceFromBottom < THRESHOLD
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

    // === 参考 webchat app-scroll.ts 的状态管理 ===
    // chatHasAutoScrolled: 首次加载后设为 true
    var chatHasAutoScrolled by remember { mutableStateOf(false) }
    // chatUserNearBottom: 用户是否在底部附近
    var chatUserNearBottom by remember { mutableStateOf(true) }
    // chatNewMessagesBelow: 有新消息在下方
    var chatNewMessagesBelow by remember { mutableStateOf(false) }
    // 当前会话 ID
    var currentSessionId by remember { mutableStateOf<String?>(null) }
    
    // IME 状态
    val density = LocalDensity.current
    val imeHeightPx = WindowInsets.ime.getBottom(density)
    var lastImeHeightPx by remember { mutableStateOf(0) }

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

    // 参考 webchat: resetChatScroll() - 在会话切换时重置
    LaunchedEffect(sessionId) {
        if (currentSessionId != sessionId) {
            AppLog.d("SessionScreen", "Session changed: $sessionId, reset scroll state")
            currentSessionId = sessionId
            // resetChatScroll(host)
            chatHasAutoScrolled = false
            chatUserNearBottom = true
            chatNewMessagesBelow = false
            viewModel.setSessionId(sessionId)
            focusRequester.requestFocus()
        }
    }

    // 参考 webchat: handleUpdated() - 监听消息变化触发滚动
    // scheduleChatScroll(host, forcedByTab || forcedByLoad || streamJustStarted || !host.chatHasAutoScrolled)
    val messageCount = state.chatMessages.size
    val isLoading = state.isLoading
    val hasStream = state.chatStream != null
    
    LaunchedEffect(messageCount, currentSessionId, isLoading, hasStream) {
        if (messageCount == 0) return@LaunchedEffect
        
        // 计算 force 条件
        // webchat: forcedByTab || forcedByLoad || streamJustStarted || !host.chatHasAutoScrolled
        val forcedByLoad = !isLoading && !chatHasAutoScrolled
        val streamJustStarted = hasStream && !chatHasAutoScrolled
        val force = forcedByLoad || streamJustStarted || !chatHasAutoScrolled
        
        AppLog.d("SessionScreen", "scheduleChatScroll trigger: messageCount=$messageCount, force=$force, chatHasAutoScrolled=$chatHasAutoScrolled")
        
        scheduleChatScroll(
            listState = listState,
            force = force,
            chatHasAutoScrolled = { chatHasAutoScrolled },
            setHasAutoScrolled = { chatHasAutoScrolled = it },
            chatUserNearBottom = { chatUserNearBottom },
            setUserNearBottom = { chatUserNearBottom = it },
            setNewMessagesBelow = { chatNewMessagesBelow = it },
            reason = "messageCount=$messageCount, force=$force"
        )
    }

    // 参考 webchat: handleChatScroll() - 监听滚动事件更新 chatUserNearBottom
    // Compose 使用 derivedStateOf 代替滚动事件
    val layoutInfo = listState.layoutInfo
    LaunchedEffect(layoutInfo) {
        // 计算 distanceFromBottom
        // webchat: distanceFromBottom = scrollHeight - scrollTop - clientHeight
        // Compose: !canScrollForward 表示在底部
        val isAtBottom = !listState.canScrollForward
        chatUserNearBottom = isAtBottom
        
        // Clear the "new messages below" indicator when user scrolls back to bottom.
        if (chatUserNearBottom) {
            chatNewMessagesBelow = false
        }
    }

    // IME 弹出时滚动到底部
    LaunchedEffect(imeHeightPx) {
        if (imeHeightPx > 0 && lastImeHeightPx == 0) {
            // IME 弹出
            AppLog.d("SessionScreen", "IME shown: height=$imeHeightPx")
            if (state.chatMessages.isNotEmpty()) {
                // 等待 IME 动画
                delay(250)
                scheduleChatScroll(
                    listState = listState,
                    force = true,
                    chatHasAutoScrolled = { chatHasAutoScrolled },
                    setHasAutoScrolled = { chatHasAutoScrolled = it },
                    chatUserNearBottom = { chatUserNearBottom },
                    setUserNearBottom = { chatUserNearBottom = it },
                    setNewMessagesBelow = { chatNewMessagesBelow = it },
                    reason = "IME shown"
                )
            }
        } else if (imeHeightPx == 0 && lastImeHeightPx > 0) {
            // IME 收起
            if (state.chatMessages.isNotEmpty() && chatUserNearBottom) {
                scheduleChatScroll(
                    listState = listState,
                    force = false,
                    chatHasAutoScrolled = { chatHasAutoScrolled },
                    setHasAutoScrolled = { chatHasAutoScrolled = it },
                    chatUserNearBottom = { chatUserNearBottom },
                    setUserNearBottom = { chatUserNearBottom = it },
                    setNewMessagesBelow = { chatNewMessagesBelow = it },
                    reason = "IME hidden"
                )
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
                    
                    // 显示"新消息"提示（当用户滚动到中间时）
                    if (chatNewMessagesBelow) {
                        NewMessagesIndicator(
                            modifier = Modifier.align(Alignment.BottomCenter),
                            onClick = {
                                chatNewMessagesBelow = false
                                chatUserNearBottom = true
                                chatHasAutoScrolled = false
                            }
                        )
                    }
                }

                MessageInputBar(
                    value = state.inputText,
                    onValueChange = { viewModel.updateInputText(it) },
                    onSend = { 
                        // 参考 webchat: 发送前 resetChatScroll
                        chatHasAutoScrolled = false
                        chatUserNearBottom = true
                        chatNewMessagesBelow = false
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
 * 参考 webchat scheduleChatScroll 实现
 * 
 * 核心逻辑：
 * 1. force=true 且未自动滚动过 → 强制滚动
 * 2. 用户在底部附近 → 滚动
 * 3. 滚动后延迟重试（webchat: 120-150ms）
 */
private suspend fun scheduleChatScroll(
    listState: LazyListState,
    force: Boolean,
    chatHasAutoScrolled: () -> Boolean,
    setHasAutoScrolled: (Boolean) -> Unit,
    chatUserNearBottom: () -> Boolean,
    setUserNearBottom: (Boolean) -> Unit,
    setNewMessagesBelow: (Boolean) -> Unit,
    reason: String
) {
    val totalItems = listState.layoutInfo.totalItemsCount
    if (totalItems <= 0) {
        AppLog.d("SessionScreen", "scheduleChatScroll: no items, skip")
        return
    }
    
    // 等待渲染完成（类似 webchat 的 updateComplete.then）
    delay(50)
    
    // 参考 webchat: effectiveForce = force && !chatHasAutoScrolled
    val effectiveForce = force && !chatHasAutoScrolled()
    
    // 参考 webchat: distanceFromBottom = scrollHeight - scrollTop - clientHeight
    // Compose: canScrollForward 表示可以继续向下滚动
    val isAtBottom = !listState.canScrollForward
    
    // 参考 webchat: shouldStick = effectiveForce || chatUserNearBottom || distanceFromBottom < NEAR_BOTTOM_THRESHOLD
    // Compose: 使用 isAtBottom 代替 distanceFromBottom < THRESHOLD
    val shouldStick = effectiveForce || chatUserNearBottom() || isAtBottom
    
    AppLog.d("SessionScreen", "scheduleChatScroll: reason=$reason, totalItems=$totalItems, effectiveForce=$effectiveForce, userNearBottom=${chatUserNearBottom()}, isAtBottom=$isAtBottom, shouldStick=$shouldStick")
    
    if (!shouldStick) {
        // User is scrolled up — flag that new content arrived below.
        setNewMessagesBelow(true)
        AppLog.d("SessionScreen", "scheduleChatScroll: user scrolled up, set newMessagesBelow=true")
        return
    }
    
    // 标记已自动滚动
    if (effectiveForce) {
        setHasAutoScrolled(true)
        AppLog.d("SessionScreen", "scheduleChatScroll: set hasAutoScrolled=true")
    }
    
    // 滚动到底部
    val lastIndex = totalItems - 1
    listState.scrollToItem(index = lastIndex)
    setUserNearBottom(true)
    setNewMessagesBelow(false)
    AppLog.d("SessionScreen", "scheduleChatScroll: scrolled to $lastIndex")
    
    // 参考 webchat: 延迟重试
    // const retryDelay = effectiveForce ? 150 : 120;
    val retryDelay = if (effectiveForce) 150L else 120L
    delay(retryDelay)
    
    // 重试滚动
    val newTotalItems = listState.layoutInfo.totalItemsCount
    if (newTotalItems > 0) {
        val newIsAtBottom = !listState.canScrollForward
        val shouldStickRetry = effectiveForce || chatUserNearBottom() || newIsAtBottom
        if (shouldStickRetry) {
            listState.scrollToItem(index = newTotalItems - 1)
            setUserNearBottom(true)
            AppLog.d("SessionScreen", "scheduleChatScroll: retry scrolled to ${newTotalItems - 1}")
        }
    }
}

/**
 * 新消息提示组件（点击滚动到底部）
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