package com.openclaw.clawchat.ui.screens.session

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.openclaw.clawchat.R
import com.openclaw.clawchat.data.FontSize
import com.openclaw.clawchat.ui.components.MarkdownText
import com.openclaw.clawchat.ui.components.ToolCardRow
import com.openclaw.clawchat.ui.components.ToolDetailCard
import com.openclaw.clawchat.ui.state.*
import com.openclaw.clawchat.ui.theme.DesignTokens
import com.openclaw.clawchat.ui.theme.ChatTokens
import com.openclaw.clawchat.util.AppLog

/**
 * 空会话内容（增强版）
 */
@Composable
fun EmptySessionContent(
    connectionStatus: ConnectionStatus
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(DesignTokens.space4),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // 动画图标
        Surface(
            modifier = Modifier.size(100.dp),
            shape = RoundedCornerShape(DesignTokens.radiusFull),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        ) {
            Box(
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (connectionStatus) {
                        is ConnectionStatus.Connected -> Icons.Default.ChatBubbleOutline
                        is ConnectionStatus.Connecting -> Icons.Default.Sync
                        is ConnectionStatus.Disconnected -> Icons.Default.CloudOff
                        else -> Icons.Default.CloudQueue
                    },
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = alpha)
                )
            }
        }

        Spacer(modifier = Modifier.height(DesignTokens.space4))

        Text(
            text = when (connectionStatus) {
                is ConnectionStatus.Connected -> stringResource(R.string.session_start_conversation_hint)
                is ConnectionStatus.Disconnected -> stringResource(R.string.session_not_connected)
                is ConnectionStatus.Connecting -> stringResource(R.string.status_connecting)
                else -> stringResource(R.string.loading)
            },
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(DesignTokens.space2))

        Text(
            text = if (connectionStatus is ConnectionStatus.Connected) {
                stringResource(R.string.session_input_hint)
            } else if (connectionStatus is ConnectionStatus.Connecting) {
                stringResource(R.string.session_connecting_hint)
            } else {
                stringResource(R.string.session_check_network)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // 提示卡片
        if (connectionStatus is ConnectionStatus.Connected) {
            Spacer(modifier = Modifier.height(DesignTokens.space6))

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(DesignTokens.radiusMd)
            ) {
                Column(
                    modifier = Modifier.padding(DesignTokens.space4),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = stringResource(R.string.session_tip_title),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(DesignTokens.space2))
                    Text(
                        text = stringResource(R.string.session_tip_content),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * 加载覆盖层（带动画打字指示器）
 */
@Composable
fun LoadingOverlay() {
    val infiniteTransition = rememberInfiniteTransition(label = "typing")

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(DesignTokens.space4),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
            shape = RoundedCornerShape(DesignTokens.radiusMd),
            shadowElevation = DesignTokens.elevationSm
        ) {
            Row(
                modifier = Modifier.padding(DesignTokens.space4),
                horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 动画打字指示器（三个点）
                TypingDots()
                Text(
                    text = stringResource(R.string.session_thinking),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 动画打字指示器（三个跳动的小点）
 */
@Composable
private fun TypingDots() {
    val infiniteTransition = rememberInfiniteTransition(label = "dots")

    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { index ->
            val offsetY by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = -6f,
                animationSpec = infiniteRepeatable(
                    animation = tween(300, delayMillis = index * 100, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dot_$index"
            )

            Box(
                modifier = Modifier
                    .size(6.dp)
                    .offset { IntOffset(0, offsetY.toInt()) }
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.primary)
            )
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
    // 滚动状态（参考 webchat app-scroll.ts 和 Stream SDK）
    chatUserNearBottom: Boolean = true,
    chatHasAutoScrolled: Boolean = false,
    chatNewMessagesBelow: Boolean = false,
    onUpdateUserNearBottom: (Boolean) -> Unit = {},
    onMarkAutoScrolled: () -> Unit = {},
    onSetNewMessagesBelow: () -> Unit = {},      // 新消息到达，增加未读计数
    onUserScrolledAway: () -> Unit = {},          // 用户滚动离开底部，不增加计数
    onDeleteMessage: (String) -> Unit = {},
    onEditMessage: (String) -> Unit = {},         // 编辑消息
    onRegenerate: () -> Unit = {},
    onRetryMessage: (String) -> Unit = {},
    onContinueGeneration: () -> Unit = {}
) {
    // 滚动优化：监听用户滚动位置
    // reverseLayout=true 时，firstVisibleItemIndex=0 表示在底部
    // 阈值 100dp：用户离开最新消息约 1 条消息高度时才认为"不在底部"
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                val isNearBottom = index == 0 && offset < 100
                // 用户离开底部时显示"新消息"按钮（不增加计数）
                if (!isNearBottom && chatHasAutoScrolled && !chatNewMessagesBelow) {
                    onUserScrolledAway()
                }
                onUpdateUserNearBottom(isNearBottom)
            }
    }

    // 新消息到达时自动滚动到底部
    // reverseLayout=true 时，新消息会出现在 index=0，只需要确保滚动到 0
    LaunchedEffect(groups.size, chatUserNearBottom, chatHasAutoScrolled) {
        // 初始加载未完成时不处理
        if (!chatHasAutoScrolled) return@LaunchedEffect
        // 用户在底部时自动滚动到最新消息
        if (chatUserNearBottom && groups.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    // 流式输出到达时自动滚动（仅处理流式，不处理初始加载）
    val streamKey = chatStream?.hashCode() ?: 0
    LaunchedEffect(streamKey, toolMessages.size, streamSegments.size) {
        // 仅处理流式更新，不处理初始加载
        if (!chatHasAutoScrolled) return@LaunchedEffect
        if (chatStream.isNullOrBlank() && toolMessages.isEmpty() && streamSegments.isEmpty()) return@LaunchedEffect

        // 用户在底部时自动跟随流式输出
        if (chatUserNearBottom) {
            listState.animateScrollToItem(0)
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
            start = DesignTokens.space4,
            end = DesignTokens.space4,
            top = DesignTokens.space4,
            bottom = DesignTokens.space4
        ),
        verticalArrangement = Arrangement.spacedBy(ChatTokens.groupGap)
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

        // 2. 工具消息（与历史消息中的 toolResult 合并）
        if (toolMessages.isNotEmpty()) {
            items(
                items = toolMessages.reversed(),  // 反转以保持正确顺序
                key = { msg -> msg.toolCallId ?: msg.id },
                contentType = { "tool_message" }
            ) { toolMessage ->
                ToolMessageCard(message = toolMessage, historyGroups = groups)
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
                onEditMessage = onEditMessage,
                onRegenerate = onRegenerate,
                onRetryMessage = onRetryMessage,
                onContinueGeneration = onContinueGeneration,
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
                    Text(stringResource(R.string.session_continue_generation))
                }
            }
        }
    }
}

/**
 * 消息分组函数 - 简化版
 */
fun groupMessages(messages: List<MessageUi>): List<MessageGroup> {
    if (messages.isEmpty()) return emptyList()

    return messages.fold(mutableListOf<MessageGroup>()) { groups, message ->
        val lastGroup = groups.lastOrNull()
        val shouldMerge = message.role == MessageRole.TOOL &&
            lastGroup?.role == MessageRole.ASSISTANT &&
            lastGroup.messages.any { it.hasToolContent() }

        if (lastGroup != null && (message.role == lastGroup.role || shouldMerge)) {
            // 合并到当前分组
            groups[groups.lastIndex] = lastGroup.copy(
                messages = lastGroup.messages + message,
                isStreaming = lastGroup.isStreaming || message.isLoading
            )
        } else {
            // 创建新分组
            groups.add(MessageGroup(
                role = message.role,
                messages = mutableListOf(message),
                timestamp = message.timestamp,
                isStreaming = message.isLoading
            ))
        }
        groups
    }
}

/**
 * 消息分组项 - 简化版
 */
@Composable
fun MessageGroupItem(
    group: MessageGroup,
    messageFontSize: FontSize = FontSize.MEDIUM,
    onDeleteMessage: (String) -> Unit = {},
    onEditMessage: (String) -> Unit = {},
    onRetryMessage: (String) -> Unit = {},
    onRegenerate: () -> Unit = {},
    onContinueGeneration: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isUser = group.role == MessageRole.USER
    val isSystem = group.role == MessageRole.SYSTEM

    Column(modifier = modifier.fillMaxWidth()) {
        when (group.role) {
            MessageRole.SYSTEM -> {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    group.messages.forEach { SystemMessageItem(message = it) }
                }
            }
            MessageRole.TOOL -> {
                // 纯 TOOL 分组：显示工具卡片
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = DesignTokens.space2),
                    horizontalAlignment = Alignment.Start
                ) {
                    group.messages.forEach { RenderToolCardsFromMessage(it, group.messages) }
                }
            }
            else -> {
                // USER/ASSISTANT 分组：消息气泡 + 工具卡片
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = DesignTokens.space2),
                    horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
                ) {
                    group.messages.forEachIndexed { index, message ->
                        if (message.role == MessageRole.TOOL) {
                            RenderToolCardsFromMessage(message, group.messages)
                        } else {
                            MessageContentCard(
                                message = message,
                                isUser = isUser,
                                isLastInGroup = index == group.messages.lastIndex,
                                messageFontSize = messageFontSize,
                                onDelete = { onDeleteMessage(message.id) },
                                onEdit = { onEditMessage(message.id) },
                                onRegenerate = onRegenerate,
                                onRetry = { onRetryMessage(message.id) },
                                onContinueGeneration = onContinueGeneration
                            )

                            // 工具调用显示（一行显示多个可折叠卡片）
                            // 传递分组中所有消息，以便查找 ToolResult
                            val toolCards = remember(message, group.messages) {
                                pairToolCards(message, group.messages)
                            }
                            if (toolCards.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(DesignTokens.space1))
                                ToolCardRow(toolCards = toolCards)
                            }
                        }

                        if (index < group.messages.lastIndex) {
                            Spacer(modifier = Modifier.height(DesignTokens.space1))
                        }
                    }

                    // 时间戳
                    group.lastMessage?.let { msg: MessageUi ->
                        Spacer(modifier = Modifier.height(DesignTokens.space1))
                        Text(
                            text = formatTimestamp(msg.timestamp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // 流式指示器
                    if (group.isStreaming) {
                        Spacer(modifier = Modifier.height(DesignTokens.space2))
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
}

/**
 * 从消息提取并渲染工具卡片（一行显示）
 */
@Composable
private fun RenderToolCardsFromMessage(message: MessageUi, allMessagesInGroup: List<MessageUi> = emptyList()) {
    // 用户消息不应该显示为工具卡片
    if (message.role == MessageRole.USER) {
        return
    }

    // 使用 remember 缓存计算结果
    val toolCards = remember(message, allMessagesInGroup) { pairToolCards(message, allMessagesInGroup) }
    ToolCardRow(toolCards = toolCards)
}

/**
 * 工具消息卡片（一行显示多个工具）
 */
@Composable
fun ToolMessageCard(message: MessageUi, historyGroups: List<MessageGroup> = emptyList()) {
    // 使用 remember 缓存计算结果
    val toolCards = remember(message) { pairToolCards(message) }

    if (toolCards.isNotEmpty()) {
        ToolCardRow(toolCards = toolCards)
    } else {
        val textContent = message.getTextContent()
        if (textContent.isNotBlank()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(ChatTokens.toolCardRadius),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ) {
                Column(modifier = Modifier.padding(ChatTokens.toolCardPadding)) {
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




