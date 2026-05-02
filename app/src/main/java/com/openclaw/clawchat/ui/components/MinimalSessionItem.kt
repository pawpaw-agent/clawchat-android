package com.openclaw.clawchat.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.openclaw.clawchat.ui.state.SessionUi
import com.openclaw.clawchat.ui.theme.MinimalColors
import com.openclaw.clawchat.ui.theme.MinimalTokens
import com.openclaw.clawchat.ui.components.minimal.MinimalAvatar

@Composable
fun MinimalSessionItem(
    session: SessionUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false
) {
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.surfaceVariant
    } else {
        MaterialTheme.colorScheme.surface
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = backgroundColor,
        shape = RoundedCornerShape(MinimalTokens.radiusMd),
        shadowElevation = MinimalTokens.elevationNone
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MinimalTokens.space3, vertical = MinimalTokens.space2),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar
            MinimalAvatar(
                emoji = session.agentEmoji,
                icon = Icons.Default.Chat,
                size = MinimalTokens.avatarSizeSm
            )

            Spacer(modifier = Modifier.width(MinimalTokens.space2))

            // Content
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session.getDisplayName(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (!session.lastMessage.isNullOrBlank()) {
                    Text(
                        text = session.lastMessage,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(MinimalTokens.space1))

            // Timestamp
            Text(
                text = formatTimeAgo(session.lastActivityAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Unread indicator
            if (session.thinking) {
                Box(
                    modifier = Modifier
                        .padding(start = MinimalTokens.space1)
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}

private fun formatTimeAgo(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp

    return when {
        diff < 60_000 -> "now"
        diff < 3600_000 -> "${diff / 60_000}m"
        diff < 86400_000 -> "${diff / 3600_000}h"
        diff < 604800_000 -> "${diff / 86400_000}d"
        else -> {
            val formatter = java.text.SimpleDateFormat("MM/dd", java.util.Locale.getDefault())
            formatter.format(java.util.Date(timestamp))
        }
    }
}