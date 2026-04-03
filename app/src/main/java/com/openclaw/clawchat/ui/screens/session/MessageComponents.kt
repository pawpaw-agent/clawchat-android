package com.openclaw.clawchat.ui.screens.session

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.*
import androidx.compose.animation.animateColor
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.core.app.ShareCompat
import android.widget.Toast
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openclaw.clawchat.data.FontSize
import com.openclaw.clawchat.ui.state.*
import com.openclaw.clawchat.ui.theme.DesignTokens
import com.openclaw.clawchat.ui.theme.TerminalColors
import com.openclaw.clawchat.ui.theme.LightTerminalColors
import com.openclaw.clawchat.ui.theme.ChatTokens
import kotlinx.serialization.json.jsonPrimitive

/**
 * 消息组件常量
 */
private const val MAX_IMAGE_SIZE = 1024  // 最大图片尺寸 (px)

/**
 * 消息内容卡片
 * 支持：文本/图片渲染、长按菜单、流式脉冲动画、反馈机制
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
    onRetry: () -> Unit = {},
    onShare: ((String) -> Unit)? = null,  // 分享回调
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
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    var showCopiedToast by remember { mutableStateOf(false) }
    
    // 双击复制提示
    if (showCopiedToast) {
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(1500)
            showCopiedToast = false
        }
    }
    
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
                        onLongClick = {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            showMenu = true
                        }
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
                    textColor = MaterialTheme.colorScheme.onBackground,
                    isStreaming = isStreaming  // 流式优化：传入流式状态
                )
                
                if (showCopiedToast) {
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter),
                color = MaterialTheme.colorScheme.inverseSurface,
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = "已复制",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
        
        if (showMenu) {
                    val messageText = formatMessageAsMarkdown(message)
                    MessageActionDropdownMenu(
                        isUser = isUser,
                        onCopy = {
                            clipboardManager.setText(AnnotatedString(messageText))
                            showMenu = false
                            // 不再显示 Toast，使用动画图标切换
                        },
                        onCopyMarkdown = {
                            clipboardManager.setText(AnnotatedString(messageText))
                            // 不再显示 Toast，使用动画图标切换
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
                            showMenu = false
                            showDeleteConfirm = true
                        },
                        onRegenerate = {
                            onRegenerate()
                            showMenu = false
                        },
                        onRetry = {
                            onRetry()
                            showMenu = false
                        },
                        onDismiss = { showMenu = false }
                    )
                }
            }
        }
        
        // 用户消息发送状态图标和时间
        if (isUser && isLastInGroup) {
            Row(
                modifier = Modifier.align(Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // 时间戳
                Text(
                    text = formatTimestamp(message.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
                MessageStatusIndicator(
                    status = message.status,
                    modifier = Modifier.size(14.dp)
                )
            }
        } else if (isLastInGroup) {
            // 助手消息时间戳 + 反馈按钮
            Row(
                modifier = Modifier.align(Alignment.Start),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = formatTimestamp(message.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
        
        // 删除确认对话框
        if (showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                title = { Text("删除消息") },
                text = { Text("确定要删除这条消息吗？") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDeleteConfirm = false
                            onDelete()
                        }
                    ) {
                        Text("删除", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = false }) {
                        Text("取消")
                    }
                }
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
    when (status) {
        MessageStatus.SENDING -> {
            // 发送中 - 旋转动画
            val infiniteTransition = rememberInfiniteTransition(label = "sending")
            val rotation by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "rotation"
            )
            
            Icon(
                imageVector = Icons.Default.Sync,
                contentDescription = "发送中",
                tint = MaterialTheme.colorScheme.primary,
                modifier = modifier
                    .size(14.dp)
                    .graphicsLayer { rotationZ = rotation }
            )
        }
        MessageStatus.SENT -> {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "已发送",
                tint = MaterialTheme.colorScheme.primary,
                modifier = modifier.size(14.dp)
            )
        }
        MessageStatus.DELIVERED -> {
            Icon(
                imageVector = Icons.Default.DoneAll,
                contentDescription = "已送达",
                tint = MaterialTheme.colorScheme.primary,
                modifier = modifier.size(14.dp)
            )
        }
        MessageStatus.FAILED -> {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = "发送失败",
                tint = MaterialTheme.colorScheme.error,
                modifier = modifier.size(14.dp)
            )
        }
    }
}

/**
 * 动画复制图标 - 点击后切换为勾号
 * 参考 webchat: 150ms 渐变动画
 */
@Composable
fun AnimatedCopyIcon(
    copied: Boolean,
    modifier: Modifier = Modifier
) {
    // 使用 Crossfade 实现 150ms 图标切换
    Crossfade(
        targetState = copied,
        animationSpec = tween(150, easing = FastOutSlowInEasing),
        label = "copy_icon"
    ) { isCopied ->
        Icon(
            imageVector = if (isCopied) Icons.Default.Check else Icons.Default.ContentCopy,
            contentDescription = if (isCopied) "已复制" else "复制",
            modifier = modifier.size(18.dp),
            tint = if (isCopied) 
                MaterialTheme.colorScheme.primary 
            else 
                MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * 消息操作下拉菜单
 */
@Composable
fun MessageActionDropdownMenu(
    isUser: Boolean,
    onCopy: () -> Unit,
    onCopyMarkdown: () -> Unit = {},
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onRegenerate: () -> Unit,
    onRetry: () -> Unit = {},
    onDismiss: () -> Unit
) {
    // 复制成功状态，用于图标切换动画
    var showCopied by remember { mutableStateOf(false) }
    
    // 复制成功后 1.5s 自动恢复图标
    LaunchedEffect(showCopied) {
        if (showCopied) {
            kotlinx.coroutines.delay(1500)
            showCopied = false
        }
    }
    
    DropdownMenu(
        expanded = true,
        onDismissRequest = onDismiss
    ) {
        DropdownMenuItem(
            text = { Text(if (showCopied) "已复制" else "复制") },
            onClick = {
                onCopy()
                showCopied = true
            },
            leadingIcon = { 
                AnimatedCopyIcon(copied = showCopied)
            }
        )
        DropdownMenuItem(
            text = { Text("复制 Markdown") },
            onClick = {
                onCopyMarkdown()
                onDismiss()
            },
            leadingIcon = { Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(18.dp)) }
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
        if (isUser) {
            DropdownMenuItem(
                text = { Text("重试") },
                onClick = onRetry,
                leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
        }
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
            
            // 先解码尺寸
            val options = android.graphics.BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            
            // 计算采样率，限制最大尺寸
            val maxSize = MAX_IMAGE_SIZE
            val sampleSize = calculateSampleSize(options.outWidth, options.outHeight, maxSize)
            
            // 用采样率解码
            val loadOptions = android.graphics.BitmapFactory.Options().apply {
                inSampleSize = sampleSize
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, loadOptions)
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
 * 计算图片采样率
 */
private fun calculateSampleSize(width: Int, height: Int, maxSize: Int): Int {
    var sampleSize = 1
    val halfWidth = width / 2
    val halfHeight = height / 2
    
    while (halfWidth / sampleSize >= maxSize || halfHeight / sampleSize >= maxSize) {
        sampleSize *= 2
    }
    
    return sampleSize
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
    // 不显示 result，只显示工具名+状态，提高 UI 平滑性
    // 使用 phase 判断完成状态：phase=result 表示已完成
    val isComplete = toolCard.phase == "result"
    val isRunning = !isComplete && toolCard.kind == ToolCardKind.CALL
    val hasContent = hasArgs
    
    val backgroundColor = when {
        toolCard.isError -> MaterialTheme.colorScheme.errorContainer
        isRunning -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)  // 执行中
        toolCard.kind == ToolCardKind.CALL -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)  // 已完成
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
        shape = RoundedCornerShape(6.dp)
    ) {
        Column(modifier = Modifier.padding(6.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 运行中显示加载指示器
                if (isRunning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(10.dp),
                        strokeWidth = 1.5.dp,
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
                        modifier = Modifier.size(10.dp),
                        tint = when {
                            toolCard.isError -> MaterialTheme.colorScheme.error
                            toolCard.kind == ToolCardKind.CALL -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.tertiary
                        }
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = toolCard.name.replaceFirstChar { it.uppercase() },
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
                // 运行中显示状态标签
                if (isRunning) {
                    Text(
                        text = "执行中...",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                if (hasContent) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(10.dp),
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
                    Spacer(modifier = Modifier.height(4.dp))
                    SelectionContainer {
                        Text(
                            text = toolCard.args!!,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            // 不显示 result，只显示工具名+状态，提高 UI 平滑性
        }
    }
}

/**
 * 工具标签行
 */
@Composable
fun ToolTagsRow(toolCards: List<ToolCard>) {
    var expandedIndex by remember { mutableStateOf(-1) }
    
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(3.dp),
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
        shape = RoundedCornerShape(4.dp),
        color = if (isError) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        modifier = Modifier.height(18.dp)
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = 5.dp,
                vertical = 2.dp
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Icon(
                imageVector = if (isError) Icons.Default.ErrorOutline else Icons.Default.Terminal,
                contentDescription = null,
                modifier = Modifier.size(8.dp),
                tint = if (isError) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                }
            )
            Text(
                text = name,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 9.sp,
                color = if (isError) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onBackground
                }
            )
            Icon(
                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                modifier = Modifier.size(8.dp),
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
        // 参考 webchat: translateY 上跳动画，1.2s ease-out
        repeat(3) { index ->
            val delay = index * 150
            val translateY by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = -3f,  // 上跳 3dp
                animationSpec = infiniteRepeatable(
                    animation = tween(1200, delayMillis = delay, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dot_$index"
            )
            
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .offset(y = translateY.dp)  // translateY 动画
                    .background(
                        MaterialTheme.colorScheme.primary,
                        CircleShape
                    )
            )
        }
    }
}

/**
 * 流式输出加载指示器 - 优化动画效果
 */
@Composable
fun StreamingIndicator(
    text: String = "思考中",
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "streaming")
    
    // 动态跳动的圆点
    val dotScale by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dotScale"
    )
    
    Row(
        modifier = modifier
            .background(
                MaterialTheme.colorScheme.surface,
                RoundedCornerShape(DesignTokens.radiusLg)
            )
            .padding(horizontal = DesignTokens.space3, vertical = DesignTokens.space2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        // 动态跳动的圆点
        repeat(3) { index ->
            val delay = index * 100
            val scale by infiniteTransition.animateFloat(
                initialValue = 0.6f,
                targetValue = 1.2f,
                animationSpec = infiniteRepeatable(
                    animation = tween(400, delayMillis = delay, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dot_$index"
            )
            
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .scale(scale)
                    .background(
                        MaterialTheme.colorScheme.primary,
                        CircleShape
                    )
            )
        }
        
        Spacer(modifier = Modifier.width(4.dp))
        
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

