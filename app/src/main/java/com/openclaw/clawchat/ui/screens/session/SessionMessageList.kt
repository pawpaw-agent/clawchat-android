package com.openclaw.clawchat.ui.screens.session

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import com.openclaw.clawchat.util.AppLog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.openclaw.clawchat.data.FontSize
import com.openclaw.clawchat.ui.components.MarkdownText
import com.openclaw.clawchat.ui.state.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.snapshotFlow
import kotlinx.serialization.json.jsonPrimitive

/**
 * 空会话内容
 */
@Composable
fun EmptySessionContent(
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
            color = MaterialTheme.colorScheme.onBackground
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
 * 加载覆盖层
 */
@Composable
fun LoadingOverlay() {
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
 * 消息分组列表
 */
@Composable
fun MessageGroupList(
    groups: List<MessageGroup>,
    listState: LazyListState,
    streamSegments: List<StreamSegment> = emptyList(),
    toolMessages: List<MessageUi> = emptyList(),
    chatStream: String? = null,
    messageFontSize: FontSize = FontSize.MEDIUM,
    // 滚动状态（参考 webchat app-scroll.ts）
    chatUserNearBottom: Boolean = true,
    chatHasAutoScrolled: Boolean = false,
    chatNewMessagesBelow: Boolean = false,
    onUpdateUserNearBottom: (Boolean) -> Unit = {},
    onMarkAutoScrolled: () -> Unit = {},
    onSetNewMessagesBelow: () -> Unit = {},
    onDeleteMessage: (String) -> Unit = {},
    onRegenerate: () -> Unit = {},
    onRetryMessage: (String) -> Unit = {},
    onContinueGeneration: () -> Unit = {}
) {
    // 监听用户滚动，更新 chatUserNearBottom 状态
    // 参考 webchat: handleChatScroll
    LaunchedEffect(listState) {
        snapshotFlow {
            // reverseLayout=true 时，firstVisibleItemIndex=0 表示在底部
            // 计算距离"底部"（视觉上的底部，实际上是列表顶部）
            val layoutInfo = listState.layoutInfo
            val viewportHeight = layoutInfo.viewportSize.height
            
            // 检查是否在底部附近
            // reverseLayout=true: firstVisibleItemIndex=0 且 firstVisibleItemScrollOffset 小表示在底部
            val isAtBottom = listState.firstVisibleItemIndex == 0 && 
                             listState.firstVisibleItemScrollOffset < 450
            
            isAtBottom
        }.collect { isNearBottom ->
            onUpdateUserNearBottom(isNearBottom)
        }
    }
    
    // 滚动逻辑：参考 webchat scheduleChatScroll
    // 只在用户在底部附近时自动滚动，否则显示"新消息"提示
    LaunchedEffect(groups.size, chatStream) {
        if (groups.isEmpty()) return@LaunchedEffect
        
        // 计算距离底部
        val layoutInfo = listState.layoutInfo
        val viewportHeight = layoutInfo.viewportSize.height
        val distanceFromBottom = listState.firstVisibleItemScrollOffset
        
        // 判断是否应该自动滚动
        // force=true 只在未自动滚动过时生效（初始加载）
        val effectiveForce = !chatHasAutoScrolled
        val shouldStick = effectiveForce || chatUserNearBottom || distanceFromBottom < 450
        
        if (!shouldStick) {
            // 用户正在查看历史消息，显示"新消息"提示
            onSetNewMessagesBelow()
            return@LaunchedEffect
        }
        
        // 执行滚动
        if (effectiveForce) {
            onMarkAutoScrolled()
        }
        
        // 滚动到底部
        listState.scrollToItem(0)
        
        // 延迟 150ms 后再次检查（参考 webchat retry）
        delay(150)
        
        val latestDistance = listState.firstVisibleItemScrollOffset
        val shouldRetry = effectiveForce || chatUserNearBottom || latestDistance < 450
        
        if (shouldRetry) {
            listState.scrollToItem(0)
        }
    }
    
    // 使用 reverseLayout = true 实现最优雅的自动滚动
    // 新消息自动显示在底部，无需手动滚动逻辑
    // 参考：lambiengcode/compose-chatgpt-kotlin-android-chatbot 最佳实践
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        reverseLayout = true,  // 关键：反转布局，新消息在底部
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 16.dp,
            bottom = 16.dp
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 注意：reverseLayout = true 时，items 顺序也需要反转
        // 显示顺序（从下到上）：流式文本 → 工具消息 → 文本段 → 历史消息
        
        // 1. 当前流式文本（显示在最底部）
        if (!chatStream.isNullOrBlank()) {
            item(key = "stream_current") {
                MessageContentCard(
                    message = MessageUi(
                        id = "stream",
                        content = listOf(MessageContentItem.Text(chatStream)),
                        role = MessageRole.ASSISTANT,
                        timestamp = System.currentTimeMillis(),
                        isLoading = true
                    ),
                    isUser = false,
                    isLastInGroup = true,
                    messageFontSize = messageFontSize,
                    isStreaming = true
                )
            }
        }
        
        // 2. 工具消息
        if (toolMessages.isNotEmpty()) {
            AppLog.d("SessionMessageList", "Rendering toolMessages: size=${toolMessages.size}")
            items(
                items = toolMessages.reversed(),  // 反转以保持正确顺序
                key = { msg -> msg.toolCallId ?: msg.id },
                contentType = { "tool_message" }
            ) { toolMessage ->
                ToolMessageCard(message = toolMessage)
            }
        }
        
        // 3. 文本段（工具执行前提交的文本）
        if (streamSegments.isNotEmpty()) {
            items(
                items = streamSegments.reversed(), 
                key = { "segment_${it.ts}" },
                contentType = { "stream_segment" }
            ) { segment ->
                MessageContentCard(
                    message = MessageUi(
                        id = "segment_${segment.ts}",
                        content = listOf(MessageContentItem.Text(segment.text)),
                        role = MessageRole.ASSISTANT,
                        timestamp = segment.ts
                    ),
                    isUser = false,
                    isLastInGroup = true,
                    messageFontSize = messageFontSize
                )
            }
        }
        
        // 4. 历史消息（显示在最上方）
        items(
            items = groups.reversed(), 
            key = { it.messages.first().id },
            contentType = { "message_group" }
        ) { group ->
            MessageGroupItem(
                group = group, 
                messageFontSize = messageFontSize,
                onDeleteMessage = onDeleteMessage,
                onRegenerate = onRegenerate,
                modifier = Modifier.animateItem(
                    fadeInSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    placementSpec = spring(stiffness = Spring.StiffnessMediumLow)
                )
            )
        }
        
        // 继续生成按钮（显示在顶部，reverseLayout 时实际在底部）
        if (groups.isNotEmpty() && chatStream.isNullOrBlank() && toolMessages.isEmpty()) {
            item(key = "continue_generation") {
                TextButton(
                    onClick = onContinueGeneration,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("继续生成")
                }
            }
        }
    }
}

/**
 * 消息分组函数 - Slack 风格连续同角色消息合并
 */
fun groupMessages(messages: List<MessageUi>): List<MessageGroup> {
    if (messages.isEmpty()) return emptyList()
    
    val groups = mutableListOf<MessageGroup>()
    var currentGroup: MutableList<MessageUi>? = null
    var currentRole: MessageRole? = null
    
    for (message in messages) {
        val shouldMergeWithPrevious = message.role == MessageRole.TOOL && 
            currentRole == MessageRole.ASSISTANT &&
            currentGroup?.any { it.hasToolContent() } == true
        
        if (message.role != currentRole && !shouldMergeWithPrevious) {
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
            currentGroup?.add(message)
            if (!shouldMergeWithPrevious) {
                currentRole = message.role
            }
        }
    }
    
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
 * 消息分组项（Slack 风格）
 */
@Composable
fun MessageGroupItem(
    group: MessageGroup, 
    messageFontSize: FontSize = FontSize.MEDIUM,
    onDeleteMessage: (String) -> Unit = {},
    onRetryMessage: (String) -> Unit = {},
    onRegenerate: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isUser = group.role == MessageRole.USER
    val isSystem = group.role == MessageRole.SYSTEM
    val isTool = group.role == MessageRole.TOOL

    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        if (isSystem) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                group.messages.forEach { message ->
                    SystemMessageItem(message = message)
                }
            }
        } else if (isTool) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalAlignment = Alignment.Start
            ) {
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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
            ) {
                val allToolResults = group.messages.flatMap { it.getToolResults() }
                val shownToolCallIds = mutableSetOf<String?>()
                
                group.messages.forEachIndexed { index, message ->
                when (message.role) {
                    MessageRole.TOOL -> {
                        val calls = message.getToolCalls()
                        val results = message.getToolResults()
                        
                        if (calls.isEmpty() && results.isEmpty()) {
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
                        // 消息行
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
                        ) {
                            // 消息气泡
                            MessageContentCard(
                                message = message,
                                isUser = isUser,
                                isLastInGroup = index == group.messages.lastIndex,
                                messageFontSize = messageFontSize,
                                onDelete = { onDeleteMessage(message.id) },
                                onRegenerate = onRegenerate,
                                onRetry = { onRetryMessage(message.id) }
                            )
                        }
                        
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
                
                if (index < group.messages.lastIndex) {
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
            
            group.lastMessage?.let { msg ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatTimestamp(msg.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
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
}

/**
 * 工具消息卡片
 */
@Composable
fun ToolMessageCard(message: MessageUi) {
    val toolCards = pairToolCards(message)
    
    val finalToolCards = if (toolCards.isEmpty() && message.role == MessageRole.TOOL) {
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
    } else toolCards
    
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        finalToolCards.forEach { toolCard ->
            ToolDetailCard(toolCard = toolCard)
        }
        
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
                        androidx.compose.foundation.text.selection.SelectionContainer {
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
