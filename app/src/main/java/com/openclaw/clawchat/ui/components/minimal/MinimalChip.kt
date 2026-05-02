package com.openclaw.clawchat.ui.components.minimal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.openclaw.clawchat.ui.theme.MinimalTokens

@Composable
fun MinimalChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false
) {
    val backgroundColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    val textColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Text(
        text = text,
        modifier = modifier
            .clip(RoundedCornerShape(MinimalTokens.radiusSm))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = MinimalTokens.space3, vertical = MinimalTokens.space2),
        style = MaterialTheme.typography.labelMedium,
        color = textColor
    )
}