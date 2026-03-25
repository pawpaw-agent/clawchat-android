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
    var chatHasAutoScrolled by remember { mutableStateOf(false) }
    var chatUserNearBottom by remember { mutableStateOf(true) }
    var currentSessionId by remember { mutableStateOf<String?>(null) }
    
    // 使用 derivedStateOf 判断是否在底部
    val isNearBottom by remember { derivedStateOf { !listState.canScrollForward } }

    AppLog.d("SessionScreen", "=== Render: sessionId=$sessionId, currentSessionId=$currentSessionId, messages=${state.chatMessages.size}, hasAutoScrolled=$chatHasAutoScrolled")

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
        AppLog.d("SessionScreen", "=== LaunchedEffect(sessionId): $sessionId")
        if (currentSessionId != sessionId) {
            AppLog.d("SessionScreen", "=== Session changed: $sessionId, reset scroll state")
            currentSessionId = sessionId
            chatHasAutoScrolled = false
            chatUserNearBottom = true
            viewModel.setSessionId(sessionId)
            focusRequester.requestFocus()
        }
    }

    // 参考 webchat: handleUpdated() - 监听消息变化触发滚动
    // 使用 size 作为 key 确保触发
    val messageCount = state.chatMessages.size
    LaunchedEffect(messageCount, currentSessionId, state.isLoading) {
        AppLog.d("SessionScreen", "=== LaunchedEffect triggered: messageCount=$messageCount, currentSessionId=$currentSessionId, isLoading=${state.isLoading}")
        
        if (messageCount == 0) {
            AppLog.d("SessionScreen", "=== No messages, skip scroll")
            return@LaunchedEffect
        }
        
        // 参考 webchat: forcedByLoad = !isLoading && !chatHasAutoScrolled
        val forcedByLoad = !state.isLoading && !chatHasAutoScrolled
        val streamJustStarted = state.chatStream != null && !chatHasAutoScrolled
        val shouldForce = forcedByLoad || streamJustStarted || !chatHasAutoScrolled
        
        AppLog.d("SessionScreen", "=== Scroll decision: forcedByLoad=$forcedByLoad, streamJustStarted=$streamJustStarted, shouldForce=$shouldForce")
        
        // 参考 webchat: 等待渲染完成
        delay(100)
        
        scheduleChatScroll(
            listState = listState,
            force = shouldForce,
            chatHasAutoScrolled = { chatHasAutoScrolled },
            setHasAutoScrolled = { chatHasAutoScrolled = it },
            userNearBottom = isNearBottom || chatUserNearBottom,
            reason = "messageCount=$messageCount, force=$shouldForce"
        )
    }

    // 更新 chatUserNearBottom 状态
    LaunchedEffect(isNearBottom) {
        chatUserNearBottom = isNearBottom
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
    AppLog.d("SessionScreen", "=== scheduleChatScroll: reason=$reason, totalItems=$totalItems, force=$force, hasAutoScrolled=${chatHasAutoScrolled()}")
    
    if (totalItems <= 0) {
        AppLog.d("SessionScreen", "=== scheduleChatScroll: no items, skip")
        return
    }
    
    // 参考 webchat: effectiveForce = force && !chatHasAutoScrolled
    val effectiveForce = force && !chatHasAutoScrolled()
    
    // 参考 webchat: shouldStick = effectiveForce || chatUserNearBottom
    val shouldStick = effectiveForce || userNearBottom
    
    AppLog.d("SessionScreen", "=== scheduleChatScroll: effectiveForce=$effectiveForce, userNearBottom=$userNearBottom, shouldStick=$shouldStick")
    
    if (!shouldStick) {
        AppLog.d("SessionScreen", "=== scheduleChatScroll: user scrolled up, skip")
        return
    }
    
    // 标记已自动滚动
    if (effectiveForce) {
        setHasAutoScrolled(true)
        AppLog.d("SessionScreen", "=== scheduleChatScroll: set hasAutoScrolled=true")
    }
    
    // 滚动到底部
    val lastIndex = totalItems - 1
    listState.scrollToItem(index = lastIndex)
    AppLog.d("SessionScreen", "=== scheduleChatScroll: scrolled to index $lastIndex")
    
    // 参考 webchat: 延迟重试
    val retryDelay = if (effectiveForce) 150L else 120L
    delay(retryDelay)
    
    // 重试滚动
    val newTotalItems = listState.layoutInfo.totalItemsCount
    if (newTotalItems > 0) {
        listState.scrollToItem(index = newTotalItems - 1)
        AppLog.d("SessionScreen", "=== scheduleChatScroll: retry scrolled to index ${newTotalItems - 1}")
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