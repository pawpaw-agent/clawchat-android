package ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.BreakStrategy
import androidx.compose.ui.unit.dp
import ui.theme.MessageBubbleAssistant
import ui.theme.MessageBubbleSystem
import ui.theme.MessageBubbleUser
import ui.theme.TextPrimary
import ui.theme.TextSecondary

/**
 * 消息数据模型
 */
data class MessageUi(
    val id: String,
    val content: String,
    val role: MessageRole,
    val timestamp: Long,
    val isLoading: Boolean = false
)

enum class MessageRole {
    USER,
    ASSISTANT,
    SYSTEM
}

/**
 * 消息列表组件
 * 显示聊天会话中的消息历史
 */
@Composable
fun MessageList(
    messages: List<MessageUi>,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    onMessageClick: ((MessageUi) -> Unit)? = null,
    showTimestamp: Boolean = true
) {
    LazyColumn(
        modifier = modifier,
        state = listState,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
    ) {
        items(
            items = messages,
            key = { it.id }
        ) { message ->
            MessageItem(
                message = message,
                showTimestamp = showTimestamp,
                onClick = onMessageClick
            )
        }
    }
}

/**
 * 单条消息组件
 */
@Composable
private fun MessageItem(
    message: MessageUi,
    showTimestamp: Boolean,
    onClick: ((MessageUi) -> Unit)?
) {
    val isUser = message.role == MessageRole.USER
    val isSystem = message.role == MessageRole.SYSTEM
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isUser) {
                    Modifier.padding(start = 64.dp)
                } else {
                    Modifier.padding(end = 64.dp)
                }
            ),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        if (!isSystem) {
            Box(
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        when (message.role) {
                            MessageRole.USER -> MessageBubbleUser
                            MessageRole.ASSISTANT -> MessageBubbleAssistant
                            MessageRole.SYSTEM -> MessageBubbleSystem
                        }
                    )
                    .padding(12.dp)
                    .then(
                        if (onClick != null) {
                            Modifier.clickable { onClick(message) } else Modifier
                        }
                    )
            ) {
                Column {
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary,
                        breakStrategy = BreakStrategy.HighQuality
                    )
                    
                    if (message.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .size(16.dp),
                            strokeWidth = 2.dp,
                            color = TextSecondary
                        )
                    }
                }
            }
        } else {
            // 系统消息样式
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MessageBubbleSystem)
                    .padding(8.dp)
            ) {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
        
        if (showTimestamp && !message.isLoading) {
            Text(
                text = formatTimestamp(message.timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                modifier = Modifier.padding(
                    start = if (isUser) 0.dp else 8.dp,
                    end = if (isUser) 8.dp else 0.dp,
                    top = 4.dp
                )
            )
        }
    }
}

/**
 * 时间戳格式化
 */
private fun formatTimestamp(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    
    return when {
        diff < 60_000 -> "刚刚"
        diff < 3600_000 -> "${diff / 60_000}分钟前"
        diff < 86400_000 -> "${diff / 3600_000}小时前"
        else -> {
            val date = java.util.Date(timestamp)
            java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
                .format(date)
        }
    }
}

/**
 * 加载指示器
 */
@Composable
fun LoadingIndicator(
    modifier: Modifier = Modifier,
    message: String = "加载中..."
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(24.dp),
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
    }
}

/**
 * 空状态提示
 */
@Composable
fun EmptyMessageList(
    modifier: Modifier = Modifier,
    message: String = "暂无消息"
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary
        )
    }
}
