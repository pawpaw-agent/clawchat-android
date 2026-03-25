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
 * 滚动逻辑参考 webchat app-scroll.ts 实现
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
    // NEAR_BOTTOM_THRESHOLD = 450 (webchat)
    // 但 Compose 使用 canScrollForward 更简单
    
    // chatHasAutoScrolled: 首次加载后设为 true
    var chatHasAutoScrolled by remember { mutableStateOf(false) }
    // chatUserNearBottom: 用户是否在底部附近
    var chatUserNearBottom by remember { mutableStateOf(true) }
    // 当前会话 ID
    var currentSessionId by remember { mutableStateOf<String?>(null) }
    
    // 使用 derivedStateOf 判断是否在底部
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

    // 参考 webchat: resetChatScroll() - 在会话切换时重置
    LaunchedEffect(sessionId) {
        if (currentSessionId != sessionId) {
            AppLog.d("SessionScreen", "Session changed: $sessionId, reset scroll state")
            currentSessionId = sessionId
            chatHasAutoScrolled = false
            chatUserNearBottom = true
            viewModel.setSessionId(sessionId)
            focusRequester.requestFocus()
        }
    }

    // 参考 webchat: handleUpdated() - 监听消息变化触发滚动
    // scheduleChatScroll(host, forcedByTab || forcedByLoad || streamJustStarted || !host.chatHasAutoScrolled)
    LaunchedEffect(
        state.chatMessages,
        state.chatToolMessages,
        state.chatStream,
        state.isLoading,
        currentSessionId
    ) {
        if (state.chatMessages.isEmpty()) return@LaunchedEffect
        
        val forcedByLoad = !state.isLoading && chatHasAutoScrolled.not()
        val streamJustStarted = state.chatStream != null && !chatHasAutoScrolled
        val shouldForce = forcedByLoad || streamJustStarted || !chatHasAutoScrolled
        
        // 参考 webchat: 等待渲染完成
        delay(50)
        
        scheduleChatScroll(
            listState = listState,
            force = shouldForce,
            chatHasAutoScrolled = { chatHasAutoScrolled },
            setHasAutoScrolled = { chatHasAutoScrolled = it },
            userNearBottom = isNearBottom || chatUserNearBottom,
            reason = "messages changed, force=$shouldForce"
        )
    }

    // 参考 webchat: handleChatScroll() - 监听滚动事件更新 userNearBottom
    // Compose 没有 scroll event，使用 derivedStateOf 代替
    
    // 更新 chatUserNearBottom 状态
    LaunchedEffect(isNearBottom) {
        chatUserNearBottom = isNearBottom
        AppLog.d("SessionScreen", "chatUserNearBottom updated: $isNearBottom")
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
                    onSend = { 
                        // 参考 webchat: 发送前 resetChatScroll
                        chatHasAutoScrolled = false
                        chatUserNearBottom = true
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
    userNearBottom: Boolean,
    reason: String
) {
    val totalItems = listState.layoutInfo.totalItemsCount
    if (totalItems <= 0) {
        AppLog.d("SessionScreen", "scheduleChatScroll: no items, skip")
        return
    }
    
    // 参考 webchat: effectiveForce = force && !chatHasAutoScrolled
    val effectiveForce = force && !chatHasAutoScrolled()
    
    // 参考 webchat: shouldStick = effectiveForce || chatUserNearBottom || distanceFromBottom < THRESHOLD
    // Compose 使用 isNearBottom 代替 distanceFromBottom 计算
    val shouldStick = effectiveForce || userNearBottom
    
    AppLog.d("SessionScreen", "scheduleChatScroll: reason=$reason, force=$force, effectiveForce=$effectiveForce, userNearBottom=$userNearBottom, shouldStick=$shouldStick")
    
    if (!shouldStick) {
        // 用户滚动到中间，不自动滚动
        AppLog.d("SessionScreen", "scheduleChatScroll: user scrolled up, skip")
        return
    }
    
    // 标记已自动滚动
    if (effectiveForce) {
        setHasAutoScrolled(true)
    }
    
    // 滚动到底部
    val lastIndex = totalItems - 1
    listState.scrollToItem(index = lastIndex)
    AppLog.d("SessionScreen", "scheduleChatScroll: scrolled to index $lastIndex")
    
    // 参考 webchat: 延迟重试
    val retryDelay = if (effectiveForce) 150L else 120L
    delay(retryDelay)
    
    // 重试滚动（确保位置正确）
    val newTotalItems = listState.layoutInfo.totalItemsCount
    if (newTotalItems > 0) {
        listState.scrollToItem(index = newTotalItems - 1)
        AppLog.d("SessionScreen", "scheduleChatScroll: retry scrolled to index ${newTotalItems - 1}")
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