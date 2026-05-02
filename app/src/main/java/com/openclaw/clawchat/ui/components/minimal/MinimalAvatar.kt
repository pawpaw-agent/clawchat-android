package com.openclaw.clawchat.ui.components.minimal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.openclaw.clawchat.ui.theme.MinimalTokens

@Composable
fun MinimalAvatar(
    modifier: Modifier = Modifier,
    emoji: String? = null,
    icon: ImageVector? = null,
    size: Dp = MinimalTokens.avatarSizeMd,
    backgroundColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surfaceVariant,
    contentColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        when {
            emoji != null -> {
                Text(
                    text = emoji,
                    style = when {
                        size > 40.dp -> MaterialTheme.typography.titleMedium
                        else -> MaterialTheme.typography.labelMedium
                    }
                )
            }
            icon != null -> {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(size * 0.6f),
                    tint = contentColor
                )
            }
            else -> {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.size(size * 0.6f),
                    tint = contentColor
                )
            }
        }
    }
}