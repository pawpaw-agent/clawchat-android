package com.openclaw.clawchat.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openclaw.clawchat.data.FontSize
import com.openclaw.clawchat.ui.components.MarkdownText
import com.openclaw.clawchat.ui.state.ConnectionStatus
import com.openclaw.clawchat.ui.state.MessageContentItem
import com.openclaw.clawchat.ui.state.MessageGroup
import com.openclaw.clawchat.ui.state.MessageRole
import com.openclaw.clawchat.ui.state.MessageUi
import com.openclaw.clawchat.ui.state.SessionEvent
import com.openclaw.clawchat.ui.state.StreamSegment
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import com.openclaw.clawchat.ui.state.SessionViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

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
    
    // 调试日志
    LaunchedEffect(state.chatMessages.size) {
        android.util.Log.d("SessionScreen", "=== chatMessages.size = ${state.chatMessages.size}")
    }
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    
    // 读取字体大小设置（从 ViewModel）
    val userMessageFontSize by viewModel.userMessageFontSize.collectAsState(initial = FontSize.MEDIUM)
    val aiMessageFontSize by viewModel.aiMessageFontSize.collectAsState(initial = FontSize.MEDIUM)

    // 初始化会话 ID
    LaunchedEffect(sessionId) {
        android.util.Log.d("SessionScreen", "=== LaunchedEffect: sessionId=$sessionId")
        viewModel.setSessionId(sessionId)
        focusRequester.requestFocus()
    }

    // 监听事件
    LaunchedEffect(viewModel.events) {
        viewModel.events.collect { event ->
            when (event) {
                is SessionEvent.Error -> {
                    // 显示错误提示
                }
                is SessionEvent.MessageReceived -> {
                    // 滚动到底部
                    if (listState.canScrollForward) {
                        listState.animateScrollToItem(state.chatMessages.lastIndex)
                    }
                }
                else -> {}
            }
        }
    }

    // 分组消息（使用 chatMessages）
    val messageGroups = remember(state.chatMessages) { groupMessages(state.chatMessages) }
    
    // 调试日志
    LaunchedEffect(state.chatMessages, state.chatStream, state.chatToolMessages) {
        android.util.Log.d("SessionScreen", "=== State: chatMessages.size=${state.chatMessages.size}, chatStream=${state.chatStream?.take(30)}, chatToolMessages.size=${state.chatToolMessages.size}, streamSegments.size=${state.chatStreamSegments.size}")
    }

    // 消息变化时滚动到底部
    var lastMessageCount by remember { mutableStateOf(0) }
    LaunchedEffect(state.chatMessages.size) {
        if (state.chatMessages.isNotEmpty() && state.chatMessages.size > lastMessageCount) {
            // 新消息到达，立即滚动到底部
            listState.scrollToItem(state.chatMessages.lastIndex)
        }
        lastMessageCount = state.chatMessages.size
    }

    // 是否显示滚动到底部按钮
    val showScrollToBottom by remember {
        derivedStateOf {
            listState.canScrollForward && state.chatMessages.isNotEmpty()
        }
    }

    Scaffold(
        topBar = {
            SessionTopAppBar(
                connectionStatus = state.connectionStatus,
                onNavigateBack = onNavigateBack
            )
        },
        contentWindowInsets = WindowInsets.ime
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // 消息列表区域
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    if (state.chatMessages.isEmpty()) {
                        // 空状态
                        EmptySessionContent(
                            connectionStatus = state.connectionStatus
                        )
                    } else {
                        // 消息列表（分组显示）
                        MessageGroupList(
                            groups = messageGroups,
                            listState = listState,
                            streamSegments = state.chatStreamSegments,
                            toolMessages = state.chatToolMessages,
                            chatStream = state.chatStream
                        )
                    }

                    // 滚动到底部按钮
                    if (showScrollToBottom) {
                        ScrollToBottomButton(
                            onClick = {
                                if (state.chatMessages.isNotEmpty()) {
                                    scope.launch {
                                        listState.animateScrollToItem(state.chatMessages.lastIndex)
                                    }
                                }
                            },
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(16.dp)
                        )
                    }

                    // 加载指示器
                    if (state.isLoading) {
                        LoadingOverlay()
                    }
                }

                // 消息输入框
                MessageInputBar(
                    value = state.inputText,
                    onValueChange = { viewModel.updateInputText(it) },
                    onSend = { viewModel.sendMessage(state.inputText) },
                    enabled = state.connectionStatus is ConnectionStatus.Connected && !state.isSending,
                    focusRequester = focusRequester
                )
            }

            // 错误提示
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
 * 消息分组函数 - Slack 风格连续同角色消息合并
 */
private fun groupMessages(messages: List<MessageUi>): List<MessageGroup> {
    if (messages.isEmpty()) return emptyList()
    
    val groups = mutableListOf<MessageGroup>()
    var currentGroup: MutableList<MessageUi>? = null
    var currentRole: MessageRole? = null
    
    for (message in messages) {
        // TOOL 消息应该和前面的 ASSISTANT 消息合并（如果前面有 ToolCall）
        val shouldMergeWithPrevious = message.role == MessageRole.TOOL && 
            currentRole == MessageRole.ASSISTANT &&
            currentGroup?.any { it.hasToolContent() } == true
        
        if (message.role != currentRole && !shouldMergeWithPrevious) {
            // 角色变化，创建新分组
            currentGroup?.let { msgs ->
                groups.add(MessageGroup(
                    role = currentRole!!,
                    messages = msgs,
                    timestamp = msgs.first().timestamp,
                    isStreaming = msgs.any { it.isLoading }
                ))
            }
            currentGroup = mutableListOf(message)
            currentRole = message.role
        } else {
            // 同角色或 TOOL 消息合并到 ASSISTANT，追加到当前分组
            currentGroup?.add(message)
            // 如果是 TOOL 消息，保持当前角色不变
            if (!shouldMergeWithPrevious) {
                currentRole = message.role
            }
        }
    }
    
    // 添加最后一个分组
    currentGroup?.let { msgs ->
        groups.add(MessageGroup(
            role = currentRole!!,
            messages = msgs,
            timestamp = msgs.first().timestamp,
            isStreaming = msgs.any { it.isLoading }
        ))
    }
    
    return groups
}

/**
 * 滚动到底部按钮
 */
@Composable
private fun ScrollToBottomButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    SmallFloatingActionButton(
        onClick = onClick,
        modifier = modifier.size(44.dp),
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = CircleShape
    ) {
        Icon(
            imageVector = Icons.Default.KeyboardArrowDown,
            contentDescription = "滚动到底部",
            modifier = Modifier.size(24.dp)
        )
    }
}

/**
 * 顶部导航栏
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionTopAppBar(
    connectionStatus: ConnectionStatus,
    onNavigateBack: () -> Unit
) {
    TopAppBar(
        title = { Text("会话") },
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "返回"
                )
            }
        },
        actions = {
            ConnectionStatusIcon(connectionStatus)
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    )
}

/**
 * 连接状态图标
 */
@Composable
private fun ConnectionStatusIcon(status: ConnectionStatus) {
    val (icon, color) = when (status) {
        is ConnectionStatus.Connected -> Icons.Default.CheckCircle to MaterialTheme.colorScheme.primary
        is ConnectionStatus.Connecting, is ConnectionStatus.Disconnecting -> Icons.Default.Sync to MaterialTheme.colorScheme.tertiary
        is ConnectionStatus.Disconnected -> Icons.Default.CloudOff to MaterialTheme.colorScheme.onSurfaceVariant
        is ConnectionStatus.Error -> Icons.Default.Error to MaterialTheme.colorScheme.error
        else -> Icons.Default.Help to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Icon(
        imageVector = icon,
        contentDescription = "连接状态",
        tint = color
    )
}

/**
 * 空会话内容
 */
@Composable
private fun EmptySessionContent(
    connectionStatus: ConnectionStatus
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.ChatBubbleOutline,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = when (connectionStatus) {
                is ConnectionStatus.Connected -> "开始对话吧"
                is ConnectionStatus.Disconnected -> "未连接到网关"
                is ConnectionStatus.Connecting -> "正在连接..."
                else -> "准备中..."
            },
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = if (connectionStatus is ConnectionStatus.Connected) {
                "输入消息并按发送键开始"
            } else {
                "请等待连接完成"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 消息分组列表（1:1 复刻 webchat）
 * 
 * 显示顺序：
 * 1. chatMessages（历史消息）
 * 2. chatStreamSegments（工具执行前的文本段）
 * 3. chatToolMessages（工具消息）
 * 4. chatStream（当前流式文本）
 */
@Composable
private fun MessageGroupList(
    groups: List<MessageGroup>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    streamSegments: List<com.openclaw.clawchat.ui.state.StreamSegment> = emptyList(),
    toolMessages: List<MessageUi> = emptyList(),
    chatStream: String? = null
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. 历史消息
        items(groups, key = { it.messages.first().id }) { group ->
            MessageGroupItem(group = group)
        }
        
        // 2. 文本段（工具执行前提交的文本）
        if (streamSegments.isNotEmpty()) {
            items(streamSegments, key = { "segment_${it.ts}" }) { segment ->
                MessageContentCard(
                    message = MessageUi(
                        id = "segment_${segment.ts}",
                        content = listOf(MessageContentItem.Text(segment.text)),
                        role = MessageRole.ASSISTANT,
                        timestamp = segment.ts
                    ),
                    isUser = false,
                    isLastInGroup = true
                )
            }
        }
        
        // 3. 工具消息
        if (toolMessages.isNotEmpty()) {
            android.util.Log.d("SessionScreen", "=== Rendering toolMessages: size=${toolMessages.size}")
            items(toolMessages, key = { "tool_${it.id}_${it.content.hashCode()}" }) { toolMessage ->
                ToolMessageCard(message = toolMessage)
            }
        }
        
        // 4. 当前流式文本（使用动态 key 确保每次更新都重新渲染）
        if (!chatStream.isNullOrBlank()) {
            item(key = "stream_${chatStream.length}") {
                // 添加调试日志
                android.util.Log.d("SessionScreen", "=== Rendering chatStream: len=${chatStream.length}, text=${chatStream.take(30)}...")
                MessageContentCard(
                    message = MessageUi(
                        id = "stream",
                        content = listOf(MessageContentItem.Text(chatStream)),
                        role = MessageRole.ASSISTANT,
                        timestamp = System.currentTimeMillis(),
                        isLoading = true
                    ),
                    isUser = false,
                    isLastInGroup = true
                )
            }
        }
    }
}

/**
 * 消息分组项（Slack 风格）
 */
@Composable
private fun MessageGroupItem(group: MessageGroup) {
    val isUser = group.role == MessageRole.USER
    val isSystem = group.role == MessageRole.SYSTEM
    val isTool = group.role == MessageRole.TOOL

    if (isSystem) {
        // 系统消息组
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            group.messages.forEach { message ->
                SystemMessageItem(message = message)
            }
        }
    } else if (isTool) {
        // 纯工具消息组（没有前面的 ASSISTANT 消息）
        // 这种情况不应该发生，因为 TOOL 消息已经合并到 ASSISTANT 分组
        // 但保留作为 fallback
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalAlignment = Alignment.Start
        ) {
            // 直接使用合并逻辑
            val mergedToolCards = group.messages.flatMap { message ->
                val calls = message.getToolCalls()
                val results = message.getToolResults()
                
                if (calls.isEmpty() && results.isEmpty()) {
                    val textContent = message.getTextContent()
                    if (textContent.isNotBlank()) {
                        listOf(ToolCard(
                            kind = ToolCardKind.RESULT,
                            name = "output",
                            args = null,
                            result = textContent,
                            isError = false,
                            callId = null
                        ))
                    } else emptyList()
                } else {
                    calls.map { call ->
                        val matchingResult = results.find { it.toolCallId == call.id }
                        ToolCard(
                            kind = if (matchingResult != null) ToolCardKind.RESULT else ToolCardKind.CALL,
                            name = call.name,
                            args = call.args?.toString(),
                            result = matchingResult?.text,
                            isError = matchingResult?.isError ?: false,
                            callId = call.id
                        )
                    }
                }
            }
            
            mergedToolCards.forEach { card ->
                ToolDetailCard(toolCard = card)
            }
        }
    } else {
        // 用户/助手消息组（可能包含合并的 TOOL 消息）
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            // 收集所有 ToolResult（用于匹配）
            val allToolResults = group.messages.flatMap { it.getToolResults() }
            
            // 调试：打印 ToolCall 和 ToolResult 的 ID
            group.messages.forEach { message ->
                val calls = message.getToolCalls()
                val results = message.getToolResults()
                if (calls.isNotEmpty() || results.isNotEmpty()) {
                    android.util.Log.d("SessionScreen", "=== message role=${message.role}, calls=${calls.map { "${it.name}:${it.id}" }}, results=${results.map { "${it.name}:${it.toolCallId}" }}")
                }
            }
            
            // 记录已经显示过的 ToolCall ID（避免重复显示）
            val shownToolCallIds = mutableSetOf<String?>()
            
            // 按消息顺序渲染
            group.messages.forEachIndexed { index, message ->
                when (message.role) {
                    MessageRole.TOOL -> {
                        // TOOL 消息：只显示未被 ASSISTANT 消息匹配的工具结果
                        val calls = message.getToolCalls()
                        val results = message.getToolResults()
                        
                        if (calls.isEmpty() && results.isEmpty()) {
                            // 纯文本工具结果（没有被前面的 ASSISTANT 消息匹配）
                            val textContent = message.getTextContent()
                            if (textContent.isNotBlank()) {
                                ToolDetailCard(toolCard = ToolCard(
                                    kind = ToolCardKind.RESULT,
                                    name = "output",
                                    args = null,
                                    result = textContent,
                                    isError = false,
                                    callId = null
                                ))
                            }
                        } else {
                            // 有 ToolCall 的 TOOL 消息 - 跳过已在 ASSISTANT 中显示的
                            calls.forEach { call ->
                                if (call.id !in shownToolCallIds) {
                                    val matchingResult = results.find { it.toolCallId == call.id }
                                        ?: allToolResults.find { it.toolCallId == call.id }
                                    
                                    val displayArgs = if (call.name == "exec" && call.args != null) {
                                        call.args?.get("command")?.jsonPrimitive?.content ?: call.args.toString()
                                    } else {
                                        call.args?.toString()
                                    }
                                    
                                    ToolDetailCard(toolCard = ToolCard(
                                        kind = if (matchingResult != null && matchingResult.text.isNotBlank()) ToolCardKind.RESULT else ToolCardKind.CALL,
                                        name = call.name,
                                        args = displayArgs,
                                        result = matchingResult?.text,
                                        isError = matchingResult?.isError ?: false,
                                        callId = call.id
                                    ))
                                    shownToolCallIds.add(call.id)
                                }
                            }
                        }
                    }
                    else -> {
                        // ASSISTANT/USER 消息
                        MessageContentCard(
                            message = message,
                            isUser = isUser,
                            isLastInGroup = index == group.messages.lastIndex
                        )
                        
                        // 如果这条消息有 ToolCall，立即显示工具卡片
                        val toolCalls = message.getToolCalls()
                        if (toolCalls.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            toolCalls.forEach { call ->
                                val matchingResult = allToolResults.find { it.toolCallId == call.id }
                                val displayArgs = if (call.name == "exec" && call.args != null) {
                                    call.args?.get("command")?.jsonPrimitive?.content ?: call.args.toString()
                                } else {
                                    call.args?.toString()
                                }
                                ToolDetailCard(toolCard = ToolCard(
                                    kind = if (matchingResult != null && matchingResult.text.isNotBlank()) ToolCardKind.RESULT else ToolCardKind.CALL,
                                    name = call.name,
                                    args = displayArgs,
                                    result = matchingResult?.text,
                                    isError = matchingResult?.isError ?: false,
                                    callId = call.id
                                ))
                                shownToolCallIds.add(call.id)
                            }
                        }
                    }
                }
                
                // 消息间分隔
                if (index < group.messages.lastIndex) {
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
            
            // 时间戳（只在最后一个显示）
            group.lastMessage?.let { msg ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatTimestamp(msg.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // 流式指示器
            if (group.isStreaming) {
                Spacer(modifier = Modifier.height(8.dp))
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/**
 * 消息内容卡片（支持文本和工具）
 */
@Composable
private fun MessageContentCard(
    message: MessageUi,
    isUser: Boolean,
    isLastInGroup: Boolean,
    userMessageFontSize: FontSize = FontSize.MEDIUM,
    aiMessageFontSize: FontSize = FontSize.MEDIUM
) {
    val textContent = message.getTextContent()
    
    // 只渲染文本内容，工具卡片在分组级别渲染
    if (textContent.isBlank()) return
    
    // 根据设置获取字体样式
    val userTextStyle = when (userMessageFontSize) {
        FontSize.SMALL -> MaterialTheme.typography.bodySmall
        FontSize.MEDIUM -> MaterialTheme.typography.bodyMedium
        FontSize.LARGE -> MaterialTheme.typography.bodyLarge
    }
    
    val aiTextSize = when (aiMessageFontSize) {
        FontSize.SMALL -> 14.sp
        FontSize.MEDIUM -> 16.sp
        FontSize.LARGE -> 18.sp
    }
    
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isUser) 16.dp else if (isLastInGroup) 4.dp else 4.dp,
                        bottomEnd = if (isUser) if (isLastInGroup) 4.dp else 4.dp else 16.dp
                    )
                )
                .background(
                    if (isUser) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.secondaryContainer
                    }
                )
                .padding(8.dp)
        ) {
            // 助手消息使用原生 Markdown 渲染
            if (!isUser) {
                MarkdownText(
                    content = textContent,
                    modifier = Modifier.fillMaxWidth(),
                    fontSize = aiTextSize
                )
            } else {
                Text(
                    text = textContent,
                    style = userTextStyle,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

/**
 * 工具标签行
 */
@Composable
private fun ToolTagsRow(toolCards: List<ToolCard>) {
    var expandedIndex by remember { mutableStateOf(-1) }
    
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // 标签行
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            toolCards.forEachIndexed { index, card ->
                ToolTag(
                    name = when (card.kind) {
                        ToolCardKind.CALL -> card.name
                        ToolCardKind.RESULT -> "output"
                    },
                    isError = card.isError,
                    isExpanded = expandedIndex == index,
                    onClick = { 
                        expandedIndex = if (expandedIndex == index) -1 else index 
                    }
                )
            }
        }
        
        // 展开的详情
        if (expandedIndex >= 0 && expandedIndex < toolCards.size) {
            val card = toolCards[expandedIndex]
            ToolDetailCard(toolCard = card)
        }
    }
}

/**
 * 单个工具标签
 */
@Composable
private fun ToolTag(
    name: String,
    isError: Boolean,
    isExpanded: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(4.dp),
        color = if (isError) {
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        },
        modifier = Modifier.height(24.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = if (isError) Icons.Default.ErrorOutline else Icons.Default.Terminal,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = if (isError) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                }
            )
            Text(
                text = name,
                style = MaterialTheme.typography.labelSmall,
                color = if (isError) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Icon(
                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = if (isError) {
                    MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                }
            )
        }
    }
}

/**
 * 工具详情卡片
 * 显示工具名称、参数和结果
 */
@Composable
private fun ToolDetailCard(toolCard: ToolCard) {
    // 默认折叠，点击展开
    var expanded by remember { mutableStateOf(false) }
    val hasContent = when (toolCard.kind) {
        ToolCardKind.CALL -> toolCard.args != null && toolCard.args.isNotBlank()
        ToolCardKind.RESULT -> (toolCard.args != null && toolCard.args.isNotBlank()) || 
                               (toolCard.result != null && toolCard.result.isNotBlank())
    }
    
    android.util.Log.d("SessionScreen", "=== ToolDetailCard: kind=${toolCard.kind}, hasContent=$hasContent, resultLen=${toolCard.result?.length}, argsLen=${toolCard.args?.length}")
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = hasContent) { expanded = !expanded },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                toolCard.isError -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                toolCard.kind == ToolCardKind.CALL -> Color(0xFFFFEBEE) // 浅红色背景
                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            }
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // 头部行
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = when {
                        toolCard.isError -> Icons.Default.ErrorOutline
                        toolCard.kind == ToolCardKind.CALL -> Icons.Default.Bolt // 红色闪电
                        else -> Icons.Default.CheckCircle
                    },
                    contentDescription = null,
                    tint = when {
                        toolCard.isError -> MaterialTheme.colorScheme.error
                        toolCard.kind == ToolCardKind.CALL -> Color(0xFFE53935) // 红色
                        else -> Color(0xFF2196F3) // 蓝色
                    },
                    modifier = Modifier.size(16.dp)
                )
                // 标题：显示工具名称（而不是 "Tool output"）
                Text(
                    text = toolCard.name.replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (toolCard.isError) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.weight(1f)
                )
                // 展开/折叠图标
                if (hasContent) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = when {
                            toolCard.kind == ToolCardKind.CALL -> Color(0xFFE53935)
                            else -> Color(0xFF2196F3)
                        },
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            
            // 展开内容
            if (expanded && hasContent) {
                Spacer(modifier = Modifier.height(8.dp))
                SelectionContainer {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // 如果有参数，显示参数
                        if (toolCard.args != null && toolCard.args.isNotBlank()) {
                            Text(
                                text = toolCard.args,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                        // 如果有结果，显示结果
                        if (toolCard.result != null && toolCard.result.isNotBlank()) {
                            if (toolCard.args != null && toolCard.args.isNotBlank()) {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            }
                            Text(
                                text = toolCard.result,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = if (toolCard.isError) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 配对工具调用和结果
 * 参考 webchat Fp(e)：消息包含 toolcall 和 toolresult 两个 content 项
 * - toolcall: 显示工具名称和参数（红色标签）
 * - toolresult: 显示工具输出（蓝色标签）
 */
private fun pairToolCards(message: MessageUi): List<ToolCard> {
    val calls = message.getToolCalls()
    val results = message.getToolResults()
    val cards = mutableListOf<ToolCard>()
    
    android.util.Log.d("SessionScreen", "=== pairToolCards: calls=${calls.size}, results=${results.size}")
    
    // 如果有 ToolCall，优先使用 ToolCall 的参数
    if (calls.isNotEmpty()) {
        calls.forEach { call ->
            // 查找匹配的 ToolResult
            val matchingResult = results.find { it.toolCallId == call.id }
            
            if (matchingResult != null && matchingResult.text.isNotBlank()) {
                // 有调用也有结果：合并显示
                val displayArgs = if (call.name == "exec" && call.args != null) {
                    call.args?.get("command")?.jsonPrimitive?.content ?: call.args.toString()
                } else {
                    call.args?.toString()
                }
                cards.add(ToolCard(
                    kind = ToolCardKind.RESULT,
                    name = call.name,
                    args = displayArgs,
                    result = matchingResult.text,
                    isError = matchingResult.isError,
                    callId = call.id
                ))
            } else {
                // 只有调用，没有结果：显示为 CALL
                val displayArgs = if (call.name == "exec" && call.args != null) {
                    call.args?.get("command")?.jsonPrimitive?.content ?: call.args.toString()
                } else {
                    call.args?.toString()
                }
                cards.add(ToolCard(
                    kind = ToolCardKind.CALL,
                    name = call.name,
                    args = displayArgs,
                    result = null,
                    isError = false,
                    callId = call.id
                ))
            }
        }
    } else {
        // 没有 ToolCall，只处理 ToolResult
        results.forEach { result ->
            val hasArgs = result.args != null && result.args.keys.isNotEmpty()
            val hasResult = result.text.isNotBlank()
            
            if (hasArgs && hasResult) {
                val displayArgs = if (result.name == "exec" && result.args != null) {
                    result.args?.get("command")?.jsonPrimitive?.content ?: result.args.toString()
                } else {
                    result.args?.toString()
                }
                cards.add(ToolCard(
                    kind = ToolCardKind.RESULT,
                    name = result.name ?: "tool",
                    args = displayArgs,
                    result = result.text,
                    isError = result.isError,
                    callId = result.toolCallId
                ))
            } else if (hasArgs) {
                val displayArgs = if (result.name == "exec" && result.args != null) {
                    result.args?.get("command")?.jsonPrimitive?.content ?: result.args.toString()
                } else {
                    result.args?.toString()
                }
                cards.add(ToolCard(
                    kind = ToolCardKind.CALL,
                    name = result.name ?: "tool",
                    args = displayArgs,
                    result = null,
                    isError = false,
                    callId = result.toolCallId
                ))
            } else {
                cards.add(ToolCard(
                    kind = ToolCardKind.RESULT,
                    name = result.name ?: "tool",
                    args = null,
                    result = result.text,
                    isError = result.isError,
                    callId = result.toolCallId
                ))
            }
        }
    }
    
    return cards
}

/**
 * 工具卡片类型
 */
private enum class ToolCardKind {
    CALL,    // tool_call: 显示工具名称 + 参数
    RESULT   // tool_result: 显示结果文本
}

/**
 * 合并的工具卡片数据
 */
private data class ToolCard(
    val kind: ToolCardKind,
    val name: String,
    val args: String?,
    val result: String?,
    val isError: Boolean,
    val callId: String?
)

/**
 * 系统消息项
 */
@Composable
private fun SystemMessageItem(message: MessageUi) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = message.getTextContent(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(8.dp)
        )
    }
}

/**
 * 工具消息卡片（对标 webchat 样式）
 * 显示 Tool output 折叠区域
 */
@Composable
private fun ToolMessageCard(message: MessageUi) {
    // 移除 remember 以确保每次 message 更新时重新计算
    val toolCards = pairToolCards(message)
    
    // 如果没有 ToolCard 但消息是 tool 类型，从文本创建 ToolCard
    val finalToolCards = if (toolCards.isEmpty() && message.role == MessageRole.TOOL) {
        val textContent = message.getTextContent()
        android.util.Log.d("SessionScreen", "=== ToolMessageCard: creating ToolCard from text, len=${textContent.length}")
        if (textContent.isNotBlank()) {
            listOf(ToolCard(
                kind = ToolCardKind.RESULT,
                name = "output",
                args = null,
                result = textContent,
                isError = false,
                callId = null
            ))
        } else emptyList()
    } else toolCards
    
    android.util.Log.d("SessionScreen", "=== ToolMessageCard: finalToolCards.size=${finalToolCards.size}")
    
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        finalToolCards.forEach { toolCard ->
            ToolDetailCard(toolCard = toolCard)
        }
        
        // 如果仍然没有 ToolCard，显示原始文本（fallback）
        if (finalToolCards.isEmpty()) {
            val textContent = message.getTextContent()
            if (textContent.isNotBlank()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        SelectionContainer {
                            Text(
                                text = textContent,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 行内工具卡片（对标 webchat chat-tool-card）
 */
@Composable
private fun InlineToolCard(toolCard: ToolCard) {
    var expanded by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier
            .widthIn(max = 320.dp)
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (toolCard.isError) {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            }
        )
    ) {
        Column {
            // 头部：图标 + 名称 + View 按钮
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 工具图标
                Icon(
                    imageVector = getToolIcon(toolCard.name),
                    contentDescription = null,
                    tint = if (toolCard.isError) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    modifier = Modifier.size(16.dp)
                )
                
                // 工具名称
                Text(
                    text = getToolDisplayName(toolCard.name),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                
                // View 按钮
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "View",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            
            // 展开的详情
            if (expanded) {
                Divider(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                )
                
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 参数 - 显示为 "with <args>"
                    val hasArgs = !toolCard.args.isNullOrBlank()
                    if (hasArgs) {
                        SelectionContainer {
                            Text(
                                text = "with ${toolCard.args}",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    // 结果
                    val hasResult = !toolCard.result.isNullOrBlank()
                    if (hasResult) {
                        if (hasArgs) {
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                        SelectionContainer {
                            Text(
                                text = toolCard.result.take(2000) + if (toolCard.result.length > 2000) "\n..." else "",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = if (toolCard.isError) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 获取工具图标
 */
private fun getToolIcon(toolName: String): androidx.compose.ui.graphics.vector.ImageVector {
    return when (toolName.lowercase()) {
        "read", "cat" -> Icons.Default.Description
        "edit", "write" -> Icons.Default.Edit
        "exec", "bash", "shell" -> Icons.Default.Terminal
        "search", "grep", "find" -> Icons.Default.Search
        "web", "fetch", "curl" -> Icons.Default.Language
        "list", "ls" -> Icons.Default.Folder
        "delete", "rm" -> Icons.Default.Delete
        else -> Icons.Default.Build
    }
}

/**
 * 获取工具显示名称
 */
private fun getToolDisplayName(toolName: String): String {
    return when (toolName.lowercase()) {
        "read" -> "Read file"
        "edit" -> "Edit file"
        "write" -> "Write file"
        "exec" -> "Execute command"
        "bash" -> "Bash"
        "shell" -> "Shell"
        "search" -> "Search"
        "grep" -> "Grep"
        "find" -> "Find"
        "web" -> "Web request"
        "fetch" -> "Fetch"
        "curl" -> "Curl"
        "list" -> "List directory"
        "ls" -> "List"
        "delete" -> "Delete"
        "rm" -> "Remove"
        else -> toolName.replaceFirstChar { it.uppercase() }
    }
}

/**
 * 工具调用卡片
 */
/**
 * 单条消息组件
 */
@Composable
private fun MessageItem(message: MessageUi) {
    val isUser = message.role == MessageRole.USER
    val isSystem = message.role == MessageRole.SYSTEM
    val isTool = message.role == MessageRole.TOOL

    if (isSystem) {
        // 系统消息
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Text(
                text = message.getTextContent(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(8.dp)
            )
        }
    } else if (isTool) {
        // 工具消息
        ToolMessageCard(message = message)
    } else {
        // 用户/助手消息
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .clip(
                        RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = if (isUser) 16.dp else 4.dp,
                            bottomEnd = if (isUser) 4.dp else 16.dp
                        )
                    )
                    .background(
                        if (isUser) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.secondaryContainer
                        }
                    )
                    .padding(12.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // 渲染工具标签
                    val toolCards = pairToolCards(message)
                    if (toolCards.isNotEmpty()) {
                        ToolTagsRow(toolCards = toolCards)
                    }
                    
                    // 渲染文本内容
                    val textContent = message.getTextContent()
                    if (textContent.isNotBlank()) {
                        // 助手消息使用原生 Markdown 渲染
                        if (!isUser) {
                            MarkdownText(
                                content = textContent,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            Text(
                                text = textContent,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }

                    if (message.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .size(16.dp),
                            strokeWidth = 2.dp,
                            color = if (isUser) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = formatTimestamp(message.timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 加载覆盖层
 */
@Composable
private fun LoadingOverlay() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
            )
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
                Text(
                    text = "助手正在思考...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 消息输入框
 */
@Composable
private fun MessageInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    enabled: Boolean,
    focusRequester: FocusRequester
) {
    Surface(
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                placeholder = { Text("输入消息...") },
                enabled = enabled,
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = { onSend() }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    disabledBorderColor = Color.Transparent
                )
            )

            IconButton(
                onClick = onSend,
                enabled = enabled && value.isNotBlank()
            ) {
                Icon(
                    imageVector = if (value.isNotBlank()) {
                        Icons.Default.Send
                    } else {
                        Icons.Default.NearMe
                    },
                    contentDescription = "发送",
                    tint = if (enabled && value.isNotBlank()) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
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
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f)
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

/**
 * 格式化时间戳
 */
private fun formatTimestamp(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp

    return when {
        diff < 60_000 -> "刚刚"
        diff < 3600_000 -> "${diff / 60_000}分钟前"
        diff < 86400_000 -> "${diff / 3600_000}小时前"
        else -> SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))
    }
}
