package com.openclaw.clawchat.ui.screens.session

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.*
import androidx.compose.animation.animateColor
import androidx.compose.animation.animateColorAsState
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
import com.openclaw.clawchat.ui.components.LoadingSkeleton
import com.openclaw.clawchat.ui.components.SkeletonType
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.core.app.ShareCompat
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openclaw.clawchat.R
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
    onEdit: () -> Unit = {},           // 编辑回调
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

    // 性能优化：流式边框动画 - 使用 AnimatedVisibility 或条件渲染
    // 只在流式时启动无限动画，非流式使用透明边框
    val staticBorderColor = MaterialTheme.colorScheme.outline
    val borderColor by animateColorAsState(
        targetValue = if (isStreaming) MaterialTheme.colorScheme.primary else staticBorderColor,
        animationSpec = if (isStreaming) {
            infiniteRepeatable(
                animation = tween(1500, easing = EaseInOut),
                repeatMode = RepeatMode.Reverse
            )
        } else {
            tween(0)  // 非流式时无动画
        },
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

                // 流式输出光标动画
                if (isStreaming) {
                    StreamingCursor(
                        modifier = Modifier.align(Alignment.BottomEnd)
                    )
                }

                if (showCopiedToast) {
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter),
                color = MaterialTheme.colorScheme.inverseSurface,
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = stringResource(R.string.action_copied),
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
                        onEdit = {
                            onEdit()
                            showMenu = false
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
                title = { Text(stringResource(R.string.action_delete_message)) },
                text = { Text(stringResource(R.string.action_delete_confirm)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDeleteConfirm = false
                            onDelete()
                        }
                    ) {
                        Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = false }) {
                        Text(stringResource(R.string.action_cancel))
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
                contentDescription = stringResource(R.string.status_sending),
                tint = MaterialTheme.colorScheme.primary,
                modifier = modifier
                    .size(14.dp)
                    .graphicsLayer { rotationZ = rotation }
            )
        }
        MessageStatus.SENT -> {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = stringResource(R.string.status_sent),
                tint = MaterialTheme.colorScheme.primary,
                modifier = modifier.size(14.dp)
            )
        }
        MessageStatus.DELIVERED -> {
            Icon(
                imageVector = Icons.Default.DoneAll,
                contentDescription = stringResource(R.string.status_delivered),
                tint = MaterialTheme.colorScheme.primary,
                modifier = modifier.size(14.dp)
            )
        }
        MessageStatus.FAILED -> {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = stringResource(R.string.status_failed),
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
    val copiedText = stringResource(R.string.action_copied)
    val copyText = stringResource(R.string.action_copy)
    Crossfade(
        targetState = copied,
        animationSpec = tween(150, easing = FastOutSlowInEasing),
        label = "copy_icon"
    ) { isCopied ->
        Icon(
            imageVector = if (isCopied) Icons.Default.Check else Icons.Default.ContentCopy,
            contentDescription = if (isCopied) copiedText else copyText,
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
    onEdit: () -> Unit = {},  // 编辑回调
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
        val copiedStr = stringResource(R.string.action_copied)
        val copyStr = stringResource(R.string.action_copy)
        DropdownMenuItem(
            text = { Text(if (showCopied) copiedStr else copyStr) },
            onClick = {
                onCopy()
                showCopied = true
            },
            leadingIcon = {
                AnimatedCopyIcon(copied = showCopied)
            }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_copy_markdown)) },
            onClick = {
                onCopyMarkdown()
                onDismiss()
            },
            leadingIcon = { Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(18.dp)) }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_share)) },
            onClick = onShare,
            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp)) }
        )
        HorizontalDivider()
        // 用户消息显示编辑选项
        if (isUser) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_edit)) },
                onClick = {
                    onEdit()
                    onDismiss()
                },
                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
        }
        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) },
            onClick = onDelete,
            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error) }
        )
        if (isUser) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_retry)) },
                onClick = onRetry,
                leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
        }
        if (!isUser) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_regenerate)) },
                onClick = onRegenerate,
                leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
        }
    }
}

/**
 * 消息图片内容（带加载占位）
 */
@Composable
fun MessageImageContent(image: MessageContentItem.Image) {
    var isLoading by remember { mutableStateOf(true) }
    var bitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

    // 异步加载图片
    LaunchedEffect(image.base64) {
        isLoading = true
        bitmap = try {
            val base64Data = image.base64
            if (base64Data.isNullOrBlank()) {
                null
            } else {
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
            }
        } catch (e: Exception) {
            null
        }
        isLoading = false
    }

    Box(
        modifier = Modifier
            .widthIn(max = 280.dp)
            .heightIn(min = 100.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        if (isLoading) {
            // 加载占位（闪烁效果）
            LoadingSkeleton(
                type = SkeletonType.THUMBNAIL,
                modifier = Modifier.fillMaxWidth().height(150.dp)
            )
        } else if (bitmap != null) {
            androidx.compose.foundation.Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = stringResource(R.string.action_image),
                modifier = Modifier.fillMaxWidth(),
                contentScale = ContentScale.FillWidth
            )
        } else {
            // 加载失败占位
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.BrokenImage,
                    contentDescription = stringResource(R.string.action_load_failed),
                    tint = MaterialTheme.colorScheme.error
                )
            }
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
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(DesignTokens.radiusMd)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(DesignTokens.space3),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2)
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = message.getTextContent(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 流式输出光标动画
 * 参考 gpt_mobile: text + "●"
 */
@Composable
private fun StreamingCursor(
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "cursor")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Text(
        text = "●",
        modifier = modifier.padding(start = 2.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
        style = MaterialTheme.typography.bodyMedium
    )
}


