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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openclaw.clawchat.ui.components.MarkdownText
import com.openclaw.clawchat.ui.state.ConnectionStatus
import com.openclaw.clawchat.ui.state.MessageContentItem
import com.openclaw.clawchat.ui.state.MessageGroup
import com.openclaw.clawchat.ui.state.MessageRole
import com.openclaw.clawchat.ui.state.MessageUi
import com.openclaw.clawchat.ui.state.SessionEvent
import com.openclaw.clawchat.ui.state.SessionViewModel
import kotlinx.serialization.json.JsonObject
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * 会话界面屏幕
 * 
 * 显示：
 * - 消息列表（用户/助手/系统消息，分组显示）
 * - 消息输入框
 * - 发送按钮
 * - 连接状态指示
 * - 滚动到底部按钮
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

    // 初始化会话 ID
    LaunchedEffect(sessionId) {
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
                        listState.animateScrollToItem(state.messages.lastIndex)
                    }
                }
                else -> {}
            }
            viewModel.consumeEvent()
        }
    }

    // 分组消息
    val messageGroups = remember(state.messages) { groupMessages(state.messages) }

    // 消息变化时滚动到底部
    var lastMessageCount by remember { mutableStateOf(0) }
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty() && state.messages.size > lastMessageCount) {
            // 新消息到达，立即滚动到底部
            listState.scrollToItem(state.messages.lastIndex)
        }
        lastMessageCount = state.messages.size
    }

    // 是否显示滚动到底部按钮
    val showScrollToBottom by remember {
        derivedStateOf {
            listState.canScrollForward && state.messages.isNotEmpty()
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
                    if (state.messages.isEmpty()) {
                        // 空状态
                        EmptySessionContent(
                            connectionStatus = state.connectionStatus
                        )
                    } else {
                        // 消息列表（分组显示）
                        MessageGroupList(
                            groups = messageGroups,
                            listState = listState
                        )
                    }

                    // 滚动到底部按钮
                    if (showScrollToBottom) {
                        ScrollToBottomButton(
                            onClick = {
                                if (state.messages.isNotEmpty()) {
                                    scope.launch {
                                        listState.animateScrollToItem(state.messages.lastIndex)
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
        if (message.role != currentRole) {
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
            // 同角色，追加到当前分组
            currentGroup?.add(message)
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
 * 消息分组列表
 */
@Composable
private fun MessageGroupList(
    groups: List<MessageGroup>,
    listState: androidx.compose.foundation.lazy.LazyListState
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(groups, key = { it.messages.first().id }) { group ->
            MessageGroupItem(group = group)
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
        // 工具消息组：折叠显示
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalAlignment = Alignment.Start
        ) {
            group.messages.forEach { message ->
                ToolMessageCard(message = message)
            }
        }
    } else {
        // 用户/助手消息组
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            // 分组内所有消息
            group.messages.forEachIndexed { index, message ->
                // 显示消息内容
                MessageContentCard(
                    message = message,
                    isUser = isUser,
                    isLastInGroup = index == group.messages.lastIndex
                )
                
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
    isLastInGroup: Boolean
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 合并工具调用和结果
        val toolCards = pairToolCards(message)
        toolCards.forEach { toolCard ->
            MergedToolCard(toolCard = toolCard)
        }
        
        // 文本内容
        val textContent = message.getTextContent()
        if (textContent.isNotBlank()) {
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
                    .padding(12.dp)
            ) {
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
        }
    }
}

/**
 * 配对工具调用和结果
 */
private fun pairToolCards(message: MessageUi): List<ToolCard> {
    val calls = message.getToolCalls()
    val results = message.getToolResults()
    val cards = mutableListOf<ToolCard>()
    
    // 按 name 配对
    val resultsByName = results.groupBy { it.name ?: "tool" }
    val matchedResults = mutableSetOf<Int>()
    
    calls.forEach { call ->
        val result = resultsByName[call.name]?.firstOrNull { 
            results.indexOf(it) !in matchedResults 
        }
        if (result != null) {
            matchedResults.add(results.indexOf(result))
        }
        cards.add(ToolCard(
            name = call.name,
            args = call.args,
            result = result?.text,
            isError = result?.isError ?: false,
            callId = call.id
        ))
    }
    
    // 添加未匹配的结果
    results.forEachIndexed { index, result ->
        if (index !in matchedResults) {
            cards.add(ToolCard(
                name = result.name ?: "tool",
                args = null,
                result = result.text,
                isError = result.isError,
                callId = null
            ))
        }
    }
    
    return cards
}

/**
 * 合并的工具卡片数据
 */
private data class ToolCard(
    val name: String,
    val args: JsonObject?,
    val result: String?,
    val isError: Boolean,
    val callId: String?
)

/**
 * 合并的工具卡片组件
 */
@Composable
private fun MergedToolCard(toolCard: ToolCard) {
    var expanded by remember { mutableStateOf(false) }
    val hasDetails = (toolCard.args != null && toolCard.args.toString().isNotBlank()) || 
                     (toolCard.result != null && toolCard.result.isNotBlank())
    
    Card(
        modifier = Modifier
            .widthIn(max = 320.dp)
            .clickable(enabled = hasDetails) { expanded = !expanded },
        colors = CardDefaults.cardColors(
            containerColor = if (toolCard.isError) {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f)
            }
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column {
            // 标题栏：图标 + 名称 + 状态 + 展开按钮
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (toolCard.isError) Icons.Default.ErrorOutline else Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = if (toolCard.isError) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = toolCard.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (toolCard.isError) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        MaterialTheme.colorScheme.onTertiaryContainer
                    },
                    modifier = Modifier.weight(1f)
                )
                // 展开按钮
                if (hasDetails) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "收起" else "展开",
                        tint = if (toolCard.isError) {
                            MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                        } else {
                            MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                        },
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            
            // 展开内容
            if (expanded && hasDetails) {
                // 参数
                if (toolCard.args != null && toolCard.args.toString().isNotBlank()) {
                    SelectionContainer {
                        Text(
                            text = toolCard.args.toString(),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (toolCard.isError) {
                                MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                            } else {
                                MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 12.dp, end = 12.dp, bottom = 8.dp)
                        )
                    }
                }
                
                // 结果
                if (toolCard.result != null && toolCard.result.isNotBlank()) {
                    SelectionContainer {
                        Text(
                            text = toolCard.result,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (toolCard.isError) {
                                MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                            } else {
                                MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
                        )
                    }
                }
            }
        }
    }
}

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
 * 工具消息卡片（折叠显示）
 */
@Composable
private fun ToolMessageCard(message: MessageUi) {
    var isExpanded by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 标题行：工具图标 + 名称 + 展开/折叠按钮
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Build,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "工具消息",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    // 显示工具调用数量
                    val toolCallCount = message.getToolCalls().size
                    val toolResultCount = message.getToolResults().size
                    if (toolCallCount > 0 || toolResultCount > 0) {
                        Text(
                            text = "(${toolCallCount + toolResultCount})",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                // 展开/折叠按钮
                IconButton(
                    onClick = { isExpanded = !isExpanded },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "折叠" else "展开",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            
            // 折叠内容
            if (isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 显示工具卡片（合并）
                    val toolCards = pairToolCards(message)
                    toolCards.forEach { toolCard ->
                        MergedToolCard(toolCard = toolCard)
                    }
                    
                    // 文本内容
                    val textContent = message.getTextContent()
                    if (textContent.isNotBlank()) {
                        Text(
                            text = textContent,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 10,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
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
                Column {
                    // 渲染工具卡片（合并）
                    val toolCards = pairToolCards(message)
                    toolCards.forEach { toolCard ->
                        MergedToolCard(toolCard = toolCard)
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
