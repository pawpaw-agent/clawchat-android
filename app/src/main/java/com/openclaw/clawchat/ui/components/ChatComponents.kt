package com.openclaw.clawchat.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.openclaw.clawchat.ui.theme.ChatTokens
import com.openclaw.clawchat.ui.theme.DesignTokens
import com.openclaw.clawchat.ui.state.MessageRole

/**
 * 聊天头像组件
 * 对应 WebChat .chat-avatar
 */
@Composable
fun ChatAvatar(
    role: MessageRole,
    name: String? = null,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(ChatTokens.avatarSize)
            .clip(RoundedCornerShape(ChatTokens.avatarRadius))
            .background(
                when (role) {
                    MessageRole.USER -> DesignTokens.accentSubtle
                    MessageRole.ASSISTANT -> DesignTokens.panelStrong
                    MessageRole.SYSTEM -> DesignTokens.panelStrong
                    MessageRole.TOOL -> DesignTokens.panelStrong
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        when (role) {
            MessageRole.USER -> {
                Text(
                    text = name?.firstOrNull()?.uppercase() ?: "U",
                    style = MaterialTheme.typography.labelMedium,
                    color = DesignTokens.accent,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                )
            }
            MessageRole.ASSISTANT -> {
                Text(
                    text = "🤖",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            MessageRole.SYSTEM -> {
                Text(
                    text = "⚙️",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            MessageRole.TOOL -> {
                Text(
                    text = "🔧",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

/**
 * 消息气泡组件
 * 对应 WebChat .chat-bubble
 */
@Composable
fun ChatBubble(
    isUser: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val backgroundColor = if (isUser) {
        ChatTokens.userBubbleBg
    } else {
        ChatTokens.assistantBubbleBg
    }
    
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(ChatTokens.bubbleRadius))
            .background(backgroundColor)
            .padding(
                horizontal = ChatTokens.bubblePaddingH,
                vertical = ChatTokens.bubblePaddingV
            )
    ) {
        Column(content = content)
    }
}

/**
 * 消息时间戳
 * 对应 WebChat .chat-group-timestamp
 */
@Composable
fun ChatTimestamp(
    timestamp: Long,
    modifier: Modifier = Modifier
) {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    
    val text = when {
        diff < 60_000 -> "刚刚"
        diff < 3600_000 -> "${diff / 60_000}分钟前"
        diff < 86400_000 -> "${diff / 3600_000}小时前"
        else -> {
            val sdf = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
            sdf.format(java.util.Date(timestamp))
        }
    }
    
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = DesignTokens.muted,
        fontSize = ChatTokens.timestampSize,
        modifier = modifier
    )
}