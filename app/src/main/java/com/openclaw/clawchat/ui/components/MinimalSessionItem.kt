package com.openclaw.clawchat.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openclaw.clawchat.ui.state.SessionUi
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
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = backgroundColor,
                shape = MaterialTheme.shapes.medium
            )
            .padding(horizontal = MinimalTokens.space3, vertical = MinimalTokens.space3)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar with thinking indicator
            Box {
                MinimalAvatar(
                    emoji = session.agentEmoji,
                    icon = Icons.Default.Chat,
                    size = MinimalTokens.avatarSizeMd
                )
                if (session.thinking) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .align(Alignment.BottomEnd)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
            }

            Spacer(modifier = Modifier.width(MinimalTokens.space2))

            // Content
            Column(modifier = Modifier.weight(1f)) {
                // Row 1: Display name + kind badge + timestamp
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = session.computeDisplayName(),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    // Kind badge (OpenClaw style)
                    if (session.kind != "unknown") {
                        KindBadge(kind = session.kind)
                        Spacer(modifier = Modifier.width(6.dp))
                    }

                    Text(
                        text = formatTimeAgo(session.lastActivityAt),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                // Row 2: Model + tokens (OpenClaw style)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!session.model.isNullOrBlank()) {
                        Text(
                            text = session.model,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (session.totalTokens != null) {
                        Text(
                            text = "${session.totalTokens} tok",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (session.messageCount > 0) {
                        Text(
                            text = "${session.messageCount} msg",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }

                // Row 3: Last message preview
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
        }
    }
}

@Composable
private fun KindBadge(kind: String, modifier: Modifier = Modifier) {
    val (bgColor, textColor) = when (kind) {
        "direct" -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.primary
        "group" -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.secondary
        "global" -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = modifier,
        color = bgColor,
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = kind.lowercase(),
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            ),
            color = textColor,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
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