package com.openclaw.clawchat.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.openclaw.clawchat.network.WebSocketConnectionState

@Composable
fun MinimalStatusIndicator(
    status: WebSocketConnectionState,
    modifier: Modifier = Modifier,
    showLabel: Boolean = false,
    dotSize: Dp = 8.dp
) {
    val (color, label) = when (status) {
        WebSocketConnectionState.Connected -> {
            Color(0xFF22C55E) to "Connected"
        }
        WebSocketConnectionState.Connecting -> {
            Color(0xFFF59E0B) to "Connecting"
        }
        WebSocketConnectionState.Reconnecting -> {
            Color(0xFFF59E0B) to "Reconnecting"
        }
        WebSocketConnectionState.Disconnected -> {
            Color(0xFF71717A) to "Disconnected"
        }
        is WebSocketConnectionState.Error -> {
            Color(0xFFEF4444) to "Error"
        }
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Surface(
            modifier = Modifier.size(dotSize),
            shape = CircleShape,
            color = color
        ) {}

        if (showLabel) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = color
            )
        }
    }
}

@Composable
fun MinimalLatencyIndicator(
    latencyMs: Long?,
    modifier: Modifier = Modifier
) {
    if (latencyMs == null) return

    val color = when {
        latencyMs < 50 -> Color(0xFF22C55E)
        latencyMs < 150 -> Color(0xFFF59E0B)
        else -> Color(0xFFEF4444)
    }

    Text(
        text = "${latencyMs}ms",
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = modifier
    )
}