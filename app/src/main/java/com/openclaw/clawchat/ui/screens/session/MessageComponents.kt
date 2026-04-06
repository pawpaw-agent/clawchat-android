package com.openclaw.clawchat.ui.screens.session

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.*
import androidx.compose.animation.animateColor
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
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
import com.openclaw.clawchat.ui.state.*
import com.openclaw.clawchat.ui.theme.DesignTokens

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
    onContinueGeneration: () -> Unit = {}, // 继续生成回调
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
    val hasResult = toolCard.result?.isNotBlank() == true
    // 使用 phase 判断完成状态：phase=result 表示已完成
    val isComplete = toolCard.phase == "result"
    val isRunning = !isComplete && toolCard.kind == ToolCardKind.CALL
    // 完成时显示 result，运行时不显示（避免闪烁）
    val hasContent = hasArgs || (hasResult && isComplete)

    val backgroundColor = when {
        toolCard.isError -> MaterialTheme.colorScheme.errorContainer
        isRunning -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)  // 执行中
        toolCard.kind == ToolCardKind.CALL -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)  // 已完成
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    // 工具特定图标和颜色
    val toolIcon = getToolIcon(toolCard.name)
    val toolColor = getToolColor(toolCard.name)

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
                        color = toolColor
                    )
                } else {
                    Icon(
                        imageVector = when {
                            toolCard.isError -> Icons.Default.ErrorOutline
                            isComplete -> Icons.Default.CheckCircle
                            else -> toolIcon
                        },
                        contentDescription = null,
                        modifier = Modifier.size(10.dp),
                        tint = when {
                            toolCard.isError -> MaterialTheme.colorScheme.error
                            isComplete -> MaterialTheme.colorScheme.tertiary
                            else -> toolColor
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
            
            // 结果（完成时显示）
            AnimatedVisibility(
                visible = expanded && hasResult && isComplete,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column {
                    if (hasArgs) {
                        Spacer(modifier = Modifier.height(3.dp))
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 2.dp),
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    SelectionContainer {
                        Text(
                            text = toolCard.result!!,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            color = if (toolCard.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }
        }
    }
}

/**
 * 获取工具特定图标
 */
private fun getToolIcon(toolName: String): androidx.compose.ui.graphics.vector.ImageVector {
    return when (toolName.lowercase()) {
        // 文件操作
        "read_file", "write_file", "edit_file", "read", "write" -> Icons.Default.Description
        "list_files", "list_directory", "ls", "glob" -> Icons.Default.FolderOpen

        // 代码执行
        "bash", "shell", "exec", "execute", "run" -> Icons.Default.Terminal
        "python", "python3" -> Icons.Default.Code

        // 搜索
        "search", "grep", "find" -> Icons.Default.Search
        "search_files" -> Icons.Default.Search

        // 网络请求
        "curl", "wget", "http", "fetch", "request" -> Icons.Default.Cloud
        "web_search", "websearch" -> Icons.Default.Public

        // 数据处理
        "json", "parse", "format" -> Icons.Default.DataObject
        "sql", "database", "query" -> Icons.Default.Storage

        // AI 相关
        "think", "reason", "analyze" -> Icons.Default.Psychology
        "image", "vision", "ocr" -> Icons.Default.Image

        // 其他
        else -> Icons.Default.Bolt
    }
}

/**
 * 获取工具特定颜色
 */
@Composable
private fun getToolColor(toolName: String): Color {
    return when (toolName.lowercase()) {
        // 文件操作 - 蓝色
        "read_file", "write_file", "edit_file", "read", "write",
        "list_files", "list_directory", "ls", "glob" -> MaterialTheme.colorScheme.primary

        // 代码执行 - 深色
        "bash", "shell", "exec", "execute", "run",
        "python", "python3" -> MaterialTheme.colorScheme.secondary

        // 搜索 - 橙色
        "search", "grep", "find", "search_files" -> MaterialTheme.colorScheme.tertiary

        // 网络请求 - 绿色
        "curl", "wget", "http", "fetch", "request",
        "web_search", "websearch" -> MaterialTheme.colorScheme.primary

        // 数据处理 - 紫色
        "json", "parse", "format", "sql", "database", "query" -> MaterialTheme.colorScheme.secondary

        // AI 相关 - 特殊颜色
        "think", "reason", "analyze" -> MaterialTheme.colorScheme.tertiary
        "image", "vision", "ocr" -> MaterialTheme.colorScheme.primary

        // 其他 - 默认
        else -> MaterialTheme.colorScheme.primary
    }
}


