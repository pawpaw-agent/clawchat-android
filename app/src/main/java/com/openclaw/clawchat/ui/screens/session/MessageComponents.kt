package com.openclaw.clawchat.ui.screens.session

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.openclaw.clawchat.ui.components.MarkdownText
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.core.app.ShareCompat
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openclaw.clawchat.data.FontSize
import com.openclaw.clawchat.ui.components.MarkdownText
import com.openclaw.clawchat.ui.state.*
import com.openclaw.clawchat.ui.theme.DesignTokens
import com.openclaw.clawchat.ui.theme.TerminalColors
import com.openclaw.clawchat.ui.theme.LightTerminalColors
import com.openclaw.clawchat.ui.theme.ChatTokens
import kotlinx.serialization.json.jsonPrimitive

/**
 * 消息内容卡片
 * 支持：文本/图片渲染、长按菜单、流式脉冲动画
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageContentCard(
    message: MessageUi,
    isUser: Boolean,
    isLastInGroup: Boolean,
    messageFontSize: FontSize = FontSize.MEDIUM,
    onCopy: (String) -> Unit = {},
    onDelete: () -> Unit = {},
    onRegenerate: () -> Unit = {},
    isStreaming: Boolean = false
) {
    val textContent = message.getTextContent()
    val images = message.content.filterIsInstance<MessageContentItem.Image>()
    
    if (textContent.isBlank() && images.isEmpty()) return
    
    val textSize = when (messageFontSize) {
        FontSize.SMALL -> 10.sp
        FontSize.MEDIUM -> 13.sp
        FontSize.LARGE -> 16.sp
    }
    
    var showMenu by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    
    // 流式输出脉冲动画
    val infiniteTransition = rememberInfiniteTransition(label = "streaming")
    val borderColor by infiniteTransition.animateColor(
        initialValue = MaterialTheme.colorScheme.outline,
        targetValue = MaterialTheme.colorScheme.primary,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "borderColor"
    )
    
    Column(
        verticalArrangement = Arrangement.spacedBy(DesignTokens.space2)
    ) {
        // 渲染图片
        images.forEach { image ->
            MessageImageContent(image = image)
        }
        
        // 渲染文本
        if (textContent.isNotBlank()) {
            Box(
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .clip(RoundedCornerShape(DesignTokens.radiusLg))
                    .background(
                        if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                    )
                    .then(
                        // 流式输出时添加脉冲边框
                        if (isStreaming) {
                            Modifier.border(
                                width = 1.dp,
                                color = borderColor,
                                shape = RoundedCornerShape(DesignTokens.radiusLg)
                            )
                        } else {
                            Modifier
                        }
                    )
                    .combinedClickable(
                        onClick = {},
                        onLongClick = { showMenu = true }
                    )
                    .padding(
                        horizontal = DesignTokens.space3,
                        vertical = DesignTokens.space2
                    )
            ) {
                MarkdownText(
                    content = if (isUser) "$ " + textContent else textContent,
                    modifier = Modifier.fillMaxWidth(),
                    fontSize = textSize,
                    textColor = MaterialTheme.colorScheme.onBackground
                )
                
                // 流式输出时显示闪烁光标
                if (isStreaming) {
                    BlinkingCursor(
                        modifier = Modifier.padding(start = 2.dp)
                    )
                }
                
                if (showMenu) {
                    val messageText = formatMessageAsMarkdown(message)
                    MessageActionDropdownMenu(
                        isUser = isUser,
                        onCopy = {
                            clipboardManager.setText(AnnotatedString(messageText))
                            showMenu = false
                        },
                        onShare = {
                            val shareIntent = ShareCompat.IntentBuilder(context)
                                .setType("text/plain")
                                .setText(messageText)
                                .intent
                            context.startActivity(shareIntent)
                            showMenu = false
                        },
                        onDelete = {
                            onDelete()
                            showMenu = false
                        },
                        onRegenerate = {
                            onRegenerate()
                            showMenu = false
                        },
                        onDismiss = { showMenu = false }
                    )
                }
            }
        }
        
        // 用户消息发送状态图标
        if (isUser && isLastInGroup) {
            MessageStatusIndicator(
                status = message.status,
                modifier = Modifier.align(Alignment.End)
            )
        }
    }
}

/**
 * 消息发送状态指示器
 */
@Composable
private fun MessageStatusIndicator(
    status: MessageStatus,
    modifier: Modifier = Modifier
) {
    val (icon, color, contentDesc) = when (status) {
        MessageStatus.SENDING -> Triple(Icons.Default.Schedule, MaterialTheme.colorScheme.onSurfaceVariant, "发送中")
        MessageStatus.SENT -> Triple(Icons.Default.Check, MaterialTheme.colorScheme.primary, "已发送")
        MessageStatus.DELIVERED -> Triple(Icons.Default.DoneAll, MaterialTheme.colorScheme.primary, "已送达")
        MessageStatus.FAILED -> Triple(Icons.Default.Error, MaterialTheme.colorScheme.error, "发送失败")
    }
    
    Icon(
        imageVector = icon,
        contentDescription = contentDesc,
        tint = color,
        modifier = modifier.size(14.dp)
    )
}

/**
 * 消息操作下拉菜单
 */
@Composable
fun MessageActionDropdownMenu(
    isUser: Boolean,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onRegenerate: () -> Unit,
    onDismiss: () -> Unit
) {
    DropdownMenu(
        expanded = true,
        onDismissRequest = onDismiss
    ) {
        DropdownMenuItem(
            text = { Text("复制") },
            onClick = onCopy,
            leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp)) }
        )
        DropdownMenuItem(
            text = { Text("分享") },
            onClick = onShare,
            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp)) }
        )
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text("删除", color = MaterialTheme.colorScheme.error) },
            onClick = onDelete,
            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp)) }
        )
        if (!isUser) {
            DropdownMenuItem(
                text = { Text("重新生成") },
                onClick = onRegenerate,
                leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
        }
    }
}

/**
 * 消息图片内容
 */
@Composable
fun MessageImageContent(image: MessageContentItem.Image) {
    val bitmap = remember(image.base64) {
        try {
            val base64Data = image.base64 ?: return@remember null
            val bytes = Base64.decode(base64Data, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            null
        }
    }
    
    Box(
        modifier = Modifier
            .widthIn(max = 280.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
    ) {
        if (bitmap != null) {
            androidx.compose.foundation.Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "图片",
                modifier = Modifier.fillMaxWidth(),
                contentScale = ContentScale.FillWidth
            )
        }
    }
}

/**
 * 系统消息项
 */
@Composable
fun SystemMessageItem(message: MessageUi) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(DesignTokens.radiusMd)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = message.getTextContent(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 工具详情卡片
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ToolDetailCard(toolCard: ToolCard) {
    var expanded by remember { mutableStateOf(false) }
    val hasArgs = toolCard.args?.isNotBlank() == true
    val hasResult = toolCard.result?.isNotBlank() == true
    val isRunning = toolCard.kind == ToolCardKind.CALL && !hasResult
    val hasContent = hasArgs || hasResult
    
    val backgroundColor = when {
        toolCard.isError -> MaterialTheme.colorScheme.errorContainer
        toolCard.kind == ToolCardKind.CALL && !hasResult -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        toolCard.kind == ToolCardKind.CALL -> Color(0x1AE53935)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                enabled = hasContent,
                onClick = { expanded = !expanded }
            ),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        shape = RoundedCornerShape(DesignTokens.radiusMd)
    ) {
        Column(modifier = Modifier.padding(DesignTokens.space3)) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 运行中显示加载指示器
                if (isRunning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Icon(
                        imageVector = when {
                            toolCard.isError -> Icons.Default.ErrorOutline
                            toolCard.kind == ToolCardKind.CALL -> Icons.Default.Bolt
                            else -> Icons.Default.CheckCircle
                        },
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = when {
                            toolCard.isError -> MaterialTheme.colorScheme.error
                            toolCard.kind == ToolCardKind.CALL -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.tertiary
                        }
                    )
                }
                Spacer(modifier = Modifier.width(DesignTokens.space2))
                Text(
                    text = toolCard.name.replaceFirstChar { it.uppercase() },
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                // 运行中显示状态标签
                if (isRunning) {
                    Text(
                        text = "执行中...",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(DesignTokens.space2))
                }
                if (hasContent) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // 运行中时默认展开显示参数
            AnimatedVisibility(
                visible = (expanded || isRunning) && hasArgs,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(DesignTokens.space2))
                    SelectionContainer {
                        Text(
                            text = toolCard.args!!,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            // 结果（完成或错误时显示）
            AnimatedVisibility(
                visible = expanded && hasResult,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column {
                    if (hasArgs) {
                        Spacer(modifier = Modifier.height(DesignTokens.space2))
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = DesignTokens.space1),
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    SelectionContainer {
                        Text(
                            text = toolCard.result!!,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = if (toolCard.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }
        }
    }
}

/**
 * 工具标签行
 */
@Composable
fun ToolTagsRow(toolCards: List<ToolCard>) {
    var expandedIndex by remember { mutableStateOf(-1) }
    
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            toolCards.forEachIndexed { index, card ->
                ToolTagExpanding(
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
        
        if (expandedIndex >= 0 && expandedIndex < toolCards.size) {
            val card = toolCards[expandedIndex]
            ToolDetailCard(toolCard = card)
        }
    }
}

/**
 * 单个工具标签（可展开版本）
 * 对应 WebChat chat-tool-tag
 */
@Composable
fun ToolTagExpanding(
    name: String,
    isError: Boolean,
    isExpanded: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(DesignTokens.radiusSm),
        color = if (isError) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        modifier = Modifier.height(DesignTokens.space6)  // 24dp
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = DesignTokens.space2,  // 8dp
                vertical = DesignTokens.space1     // 4dp
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.space1)
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
                fontSize = 11.sp,
                color = if (isError) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onBackground
                }
            )
            Icon(
                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 消息淡入动画包装器
 * 对应 WebChat rise animation
 */
@Composable
fun AnimatedMessageItem(
    visible: Boolean,
    content: @Composable AnimatedVisibilityScope.() -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(
            animationSpec = tween(
                durationMillis = DesignTokens.durationNormal,
                easing = EaseOut
            )
        ) + slideInVertically(
            animationSpec = tween(
                durationMillis = DesignTokens.durationNormal,
                easing = EaseOut
            ),
            initialOffsetY = { it / 4 }  // 从底部向上滑入
        ),
        content = content
    )
}

/**
 * 打字指示器（三个跳动的点）
 * 对应 WebChat typing indicator
 */
@Composable
fun TypingIndicator(
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "typing")
    
    Row(
        modifier = modifier
            .background(
                MaterialTheme.colorScheme.surface,
                RoundedCornerShape(DesignTokens.radiusLg)
            )
            .padding(horizontal = DesignTokens.space3, vertical = DesignTokens.space2),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { index ->
            val delay = index * 150
            val scale by infiniteTransition.animateFloat(
                initialValue = 0.5f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(400, delayMillis = delay, easing = EaseInOut),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dot_$index"
            )
            
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .scale(scale)
                    .background(
                        MaterialTheme.colorScheme.primary,
                        CircleShape
                    )
            )
        }
    }
}

/**
 * 流式输出加载指示器
 */
@Composable
fun StreamingIndicator(
    text: String = "思考中",
    modifier: Modifier = Modifier
) {
    var dots by remember { mutableStateOf(0) }
    val infiniteTransition = rememberInfiniteTransition(label = "streaming")
    
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(300)
            dots = (dots + 1) % 4
        }
    }
    
    Row(
        modifier = modifier
            .background(
                MaterialTheme.colorScheme.surface,
                RoundedCornerShape(DesignTokens.radiusLg)
            )
            .padding(horizontal = DesignTokens.space3, vertical = DesignTokens.space2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2)
    ) {
        // 脉冲圆点
        val pulseAlpha by infiniteTransition.animateFloat(
            initialValue = 0.4f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(600),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulse"
        )
        
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(
                    MaterialTheme.colorScheme.primary.copy(alpha = pulseAlpha),
                    CircleShape
                )
        )
        
        Text(
            text = "$text${".".repeat(dots)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 闪烁光标（流式输出时显示）
 */
@Composable
private fun BlinkingCursor(
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "cursor")
    
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(530, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cursor_alpha"
    )
    
    Box(
        modifier = modifier
            .width(2.dp)
            .height(14.dp)
            .background(
                MaterialTheme.colorScheme.primary.copy(alpha = alpha),
                RoundedCornerShape(1.dp)
            )
    )
}