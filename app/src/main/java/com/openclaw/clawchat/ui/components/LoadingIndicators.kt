package com.openclaw.clawchat.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import com.openclaw.clawchat.ui.theme.DesignTokens

/**
 * 打字指示器（三个跳动的点）
 * 对应 WebChat typing indicator
 */
@Composable
fun TypingIndicator(
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "typing")
    
    Row(
        modifier = modifier
            .background(
                MaterialTheme.colorScheme.surface,
                androidx.compose.foundation.shape.RoundedCornerShape(DesignTokens.radiusLg)
            )
            .padding(horizontal = DesignTokens.space3, vertical = DesignTokens.space2),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { index ->
            val delay = index * 150
            val scale by infiniteTransition.animateFloat(
                initialValue = 0.5f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(400, delayMillis = delay, easing = EaseInOut),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dot_$index"
            )
            
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .scale(scale)
                    .background(
                        MaterialTheme.colorScheme.primary,
                        CircleShape
                    )
            )
        }
    }
}

/**
 * 流式输出加载指示器 - 优化动画效果
 */
@Composable
fun StreamingIndicator(
    text: String = "思考中",
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "streaming")
    
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 跳动的点
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(3) { index ->
                val delay = index * 150
                val scale by infiniteTransition.animateFloat(
                    initialValue = 0.5f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(400, delayMillis = delay, easing = EaseInOut),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "stream_dot_$index"
                )
                
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .scale(scale)
                        .background(
                            MaterialTheme.colorScheme.primary,
                            CircleShape
                        )
                )
            }
        }
        
        // 文字
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}