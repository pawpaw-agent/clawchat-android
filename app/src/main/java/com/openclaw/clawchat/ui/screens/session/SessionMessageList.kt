package com.openclaw.clawchat.ui.screens.session

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.openclaw.clawchat.data.FontSize
import com.openclaw.clawchat.ui.components.MarkdownText
import com.openclaw.clawchat.ui.components.ToolDetailCard
import com.openclaw.clawchat.ui.state.*
import com.openclaw.clawchat.ui.theme.DesignTokens
import com.openclaw.clawchat.ui.theme.ChatTokens

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
            .padding(DesignTokens.space4),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.ChatBubbleOutline,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(DesignTokens.space4))

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

        Spacer(modifier = Modifier.height(DesignTokens.space2))

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

    // 新消息/流式输出到达时自动滚动
    // 参考 Stream SDK: 区分自己发的消息和 AI 响应
    // - 用户消息：始终滚动
    // - AI 响应：用户在底部时才滚动
    val streamKey = chatStream?.hashCode() ?: 0
    LaunchedEffect(groups.size, streamKey, toolMessages.size, streamSegments.size) {
        if (groups.isEmpty()) return@LaunchedEffect
        // 只有已经完成初始滚动时才处理
        if (!chatHasAutoScrolled) return@LaunchedEffect

        // 检查最新消息是否是用户消息
        val lastGroup = groups.lastOrNull()
        val isUserMessage = lastGroup?.role == MessageRole.USER

        if (isUserMessage) {
            // 用户消息：始终滚动到底部
            listState.scrollToItem(0)
        } else if (chatUserNearBottom) {
            // AI 响应 + 用户在底部：自动跟随
            listState.scrollToItem(0)
        } else {
            // AI 响应 + 用户不在底部：增加未读计数
            onSetNewMessagesBelow()
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
                    Text("继续生成")
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
                    group.messages.forEach { RenderToolCardsFromMessage(it) }
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
                            RenderToolCardsFromMessage(message)
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

                            // 工具调用显示（使用 remember 缓存计算结果）
                            val toolCards = remember(message) { pairToolCards(message) }
                            toolCards.forEach { toolCard ->
                                Spacer(modifier = Modifier.height(DesignTokens.space1))
                                ToolDetailCard(toolCard = toolCard)
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
 * 从消息提取并渲染工具卡片（已优化：使用 remember 缓存）
 */
@Composable
private fun RenderToolCardsFromMessage(message: MessageUi) {
    // 用户消息不应该显示为工具卡片
    if (message.role == MessageRole.USER) {
        return
    }

    // 使用 remember 缓存计算结果
    val toolCards = remember(message) { pairToolCards(message) }

    toolCards.forEach { toolCard ->
        ToolDetailCard(toolCard = toolCard)
    }
}

/**
 * 工具消息卡片（已优化：使用 remember 缓存）
 * @param message 工具消息（来自 toolStream，只显示工具名+状态）
 * @param historyGroups 历史消息分组（不再合并 toolResult）
 */
@Composable
fun ToolMessageCard(message: MessageUi, historyGroups: List<MessageGroup> = emptyList()) {
    // 使用 remember 缓存计算结果
    val toolCards = remember(message) { pairToolCards(message) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(DesignTokens.space2)
    ) {
        toolCards.forEach { toolCard ->
            ToolDetailCard(toolCard = toolCard)
        }

        if (toolCards.isEmpty()) {
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
}




