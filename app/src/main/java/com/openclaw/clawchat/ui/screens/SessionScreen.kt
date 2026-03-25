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
    var currentSessionId by remember { mutableStateOf<String?>(null) }
    
    // 使用 derivedStateOf 判断是否在底部
    // webchat: NEAR_BOTTOM_THRESHOLD = 450px
    // Compose: canScrollForward = false 表示在底部
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
            viewModel.setSessionId(sessionId)
            focusRequester.requestFocus()
        }
    }

    // 参考 webchat: handleUpdated() - 监听消息变化触发滚动
    val messageCount = state.chatMessages.size
    LaunchedEffect(messageCount, currentSessionId) {
        if (messageCount == 0) return@LaunchedEffect
        
        AppLog.d("SessionScreen", "Messages changed: count=$messageCount, isNearBottom=$isNearBottom, hasAutoScrolled=$chatHasAutoScrolled")
        
        // 参考 webchat: 等待渲染完成
        delay(50)
        
        // 检查滚动条件
        // webchat: shouldStick = effectiveForce || chatUserNearBottom || distanceFromBottom < THRESHOLD
        val shouldScroll = scheduleChatScroll(
            listState = listState,
            hasAutoScrolled = chatHasAutoScrolled,
            setHasAutoScrolled = { chatHasAutoScrolled = it }
        )
        
        AppLog.d("SessionScreen", "Scroll result: shouldScroll=$shouldScroll")
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
 * 1. 检查列表是否为空
 * 2. 检查用户是否在底部附近 (canScrollForward)
 * 3. 如果在底部或首次加载，滚动
 * 4. 延迟重试
 */
private suspend fun scheduleChatScroll(
    listState: LazyListState,
    hasAutoScrolled: Boolean,
    setHasAutoScrolled: (Boolean) -> Unit
): Boolean {
    val totalItems = listState.layoutInfo.totalItemsCount
    if (totalItems <= 0) {
        AppLog.d("SessionScreen", "scheduleChatScroll: no items")
        return false
    }
    
    // 实时检查是否在底部
    // webchat: distanceFromBottom = scrollHeight - scrollTop - clientHeight
    // Compose: !canScrollForward 表示已滚动到底部
    val isAtBottom = !listState.canScrollForward
    
    // webchat: effectiveForce = force && !chatHasAutoScrolled
    // 这里 force = true（消息变化时总是尝试），所以：
    val effectiveForce = !hasAutoScrolled
    
    // webchat: shouldStick = effectiveForce || chatUserNearBottom || distanceFromBottom < THRESHOLD
    val shouldStick = effectiveForce || isAtBottom
    
    AppLog.d("SessionScreen", "scheduleChatScroll: totalItems=$totalItems, isAtBottom=$isAtBottom, hasAutoScrolled=$hasAutoScrolled, effectiveForce=$effectiveForce, shouldStick=$shouldStick")
    
    if (!shouldStick) {
        // 用户滚动到中间，不自动滚动
        AppLog.d("SessionScreen", "scheduleChatScroll: user scrolled up, skip")
        return false
    }
    
    // 标记已自动滚动
    if (effectiveForce) {
        setHasAutoScrolled(true)
        AppLog.d("SessionScreen", "scheduleChatScroll: set hasAutoScrolled=true")
    }
    
    // 滚动到底部
    val lastIndex = totalItems - 1
    listState.scrollToItem(index = lastIndex)
    AppLog.d("SessionScreen", "scheduleChatScroll: scrolled to $lastIndex")
    
    // 参考 webchat: 延迟重试 (120-150ms)
    delay(120)
    
    // 重试滚动
    val newTotalItems = listState.layoutInfo.totalItemsCount
    if (newTotalItems > 0) {
        listState.scrollToItem(index = newTotalItems - 1)
        AppLog.d("SessionScreen", "scheduleChatScroll: retry scrolled to ${newTotalItems - 1}")
    }
    
    return true
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