package com.openclaw.clawchat.ui.components.minimal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.openclaw.clawchat.ui.theme.MinimalTokens

@Composable
fun MinimalCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = MinimalTokens.elevationNone),
        shape = RoundedCornerShape(MinimalTokens.radiusMd)
    ) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.padding(MinimalTokens.space4),
            content = content
        )
    }
}

@Composable
fun MinimalSurface(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(MinimalTokens.radiusMd))
            .background(MaterialTheme.colorScheme.surface)
            .padding(MinimalTokens.space4)
    ) {
        androidx.compose.foundation.layout.Column(content = content)
    }
}