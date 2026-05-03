package com.openclaw.clawchat.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openclaw.clawchat.ui.state.MessageContentItem
import com.openclaw.clawchat.ui.state.MessageRole
import com.openclaw.clawchat.ui.state.MessageUi
import com.openclaw.clawchat.ui.theme.MinimalTokens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * OpenClaw v2026.4.29 Style Message Bubble
 *
 * Key patterns:
 * - Role labels: "You" (user), "OpenClaw" (assistant), tool names (tool)
 * - User messages aligned right with accent color
 * - Assistant/tool messages aligned left with surface color
 * - Tool calls show phase indicator (start/result)
 * - Tool results expandable with truncation
 */
@Composable
fun MinimalMessageBubble(
    message: MessageUi,
    modifier: Modifier = Modifier,
    showRoleLabel: Boolean = false,
    showTimestamp: Boolean = false
) {
    var showDetails by remember { mutableStateOf(false) }
    val isUser = message.role == MessageRole.USER

    // OpenClaw v2026.4.29: bubble style based on role
    val bubbleStyle = bubbleStyleForRole(message.role)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable { showDetails = !showDetails },
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        AnimatedVisibility(
            visible = true,
            enter = fadeIn(animationSpec = tween(200)) + slideInVertically(
                animationSpec = tween(200),
                initialOffsetY = { it / 6 }
            )
        ) {
            Surface(
                color = bubbleStyle.containerColor,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, bubbleStyle.borderColor)
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 280.dp)
                        .padding(horizontal = 11.dp, vertical = 8.dp)
                ) {
                    // Role label (OpenClaw style)
                    if (showRoleLabel) {
                        Text(
                            text = roleLabelText(message.role),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.6.sp
                            ),
                            color = bubbleStyle.roleColor,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }

                    // Message content
                    message.content.forEach { item ->
                        when (item) {
                            is MessageContentItem.Text -> {
                                MarkdownBlockRenderer(
                                    content = item.text,
                                    fontSize = 14.sp,
                                    textColor = if (isUser) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                    isStreaming = false
                                )
                            }
                            is MessageContentItem.ToolCall -> {
                                MinimalToolCallContent(
                                    name = item.name,
                                    phase = item.phase,
                                    isStreaming = false
                                )
                            }
                            is MessageContentItem.ToolResult -> {
                                MinimalToolResultContent(
                                    text = item.text,
                                    isError = item.isError
                                )
                            }
                            is MessageContentItem.Image -> {
                                coil.compose.AsyncImage(
                                    model = item.url,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                )
                            }
                        }
                    }

                    // Loading indicator
                    if (message.isLoading) {
                        MinimalLoadingIndicator()
                    }
                }
            }
        }

        // Timestamp (shown on tap or when requested)
        if (showDetails || showTimestamp) {
            Text(
                text = formatTimestamp(message.timestamp),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 4.dp, start = if (isUser) 0.dp else 4.dp, end = if (isUser) 4.dp else 0.dp)
            )
        }
    }
}

/**
 * OpenClaw v2026.4.29 bubble style based on role
 */
private data class BubbleStyle(
    val containerColor: Color,
    val borderColor: Color,
    val roleColor: Color
)

@Composable
private fun bubbleStyleForRole(role: MessageRole): BubbleStyle {
    return when (role) {
        MessageRole.USER -> BubbleStyle(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f),
            borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
            roleColor = MaterialTheme.colorScheme.primary
        )
        MessageRole.SYSTEM -> BubbleStyle(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
            borderColor = MaterialTheme.colorScheme.error.copy(alpha = 0.4f),
            roleColor = MaterialTheme.colorScheme.error
        )
        else -> BubbleStyle(
            containerColor = MaterialTheme.colorScheme.surface,
            borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
            roleColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Role label text (OpenClaw v2026.4.29 style)
 */
private fun roleLabelText(role: MessageRole): String {
    return when (role) {
        MessageRole.USER -> "You"
        MessageRole.SYSTEM -> "System"
        MessageRole.ASSISTANT -> "OpenClaw"
        MessageRole.TOOL -> "Tool"
    }
}

/**
 * Text content — renders markdown via MarkdownText
 */
@Composable
private fun MinimalTextContent(
    text: String,
    textColor: Color
) {
    MarkdownBlockRenderer(
        content = text,
        fontSize = 14.sp,
        textColor = textColor
    )
}

/**
 * Tool call content (OpenClaw v2026.4.29 style)
 */
@Composable
private fun MinimalToolCallContent(
    name: String,
    phase: String,
    isStreaming: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.padding(vertical = 4.dp)
    ) {
        // Tool indicator dot
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
        )

        Text(
            text = name,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp)
        )

        // Phase indicator
        when (phase) {
            "start" -> {
                if (isStreaming) {
                    Text(
                        text = "...",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            "result" -> {
                Text(
                    text = " ✓",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            else -> {
                if (isStreaming) {
                    Text(
                        text = "...",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Tool result content (OpenClaw v2026.4.29 style)
 */
@Composable
private fun MinimalToolResultContent(
    text: String,
    isError: Boolean,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val isTruncated = text.length > 200
    val displayText = if (expanded || !isTruncated) text else text.take(200) + "..."

    Column(modifier = modifier.padding(top = 4.dp)) {
        // Markdown-rendered tool output
        MarkdownBlockRenderer(
            content = displayText,
            fontSize = 12.sp,
            textColor = if (isError) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )

        // Expand/collapse toggle
        if (isTruncated) {
            Text(
                text = if (expanded) "Show less" else "Show more",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .clickable { expanded = !expanded }
            )
        }
    }
}

/**
 * Loading indicator (OpenClaw v2026.4.29 style - animated dot pulse)
 */
@Composable
private fun MinimalLoadingIndicator(
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.padding(top = 8.dp)
    ) {
        // Animated dot pulse (OpenClaw style - staggered bounce)
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf(0, 150, 300).forEach { delay ->
                LoadingDot(delayMillis = delay)
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = "Sending...",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun LoadingDot(delayMillis: Int) {
    val infiniteTransition = rememberInfiniteTransition(label = "loadingDot")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = delayMillis),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "dotAlpha"
    )
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = delayMillis),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "dotScale"
    )
    Box(
        modifier = Modifier
            .size(5.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha))
    )
}

/**
 * OpenClaw v2026.4.29 style markdown text (basic - no complex rendering)
 */
@Composable
fun MinimalMarkdownText(
    text: String,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    // Basic implementation - just display text
    // OpenClaw v2026.4.29 ChatMarkdown handles:
    // - Code blocks with language labels
    // - Inline code
    // - Bold, italic
    // - Links
    // TODO: Add markdown parsing
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = textColor,
        modifier = modifier
    )
}

/**
 * OpenClaw v2026.4.29 style code block
 */
@Composable
fun MinimalCodeBlock(
    code: String,
    language: String?,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (!language.isNullOrBlank()) {
                Text(
                    text = language.uppercase(Locale.getDefault()),
                    style = MaterialTheme.typography.labelSmall.copy(
                        letterSpacing = 0.4.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
            Text(
                text = code.trimEnd(),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
    return formatter.format(Date(timestamp))
}