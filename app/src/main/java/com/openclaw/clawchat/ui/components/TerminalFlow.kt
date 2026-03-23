package com.openclaw.clawchat.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.openclaw.clawchat.ui.theme.TerminalColors

/**
 * 脉冲状态
 */
enum class PulseState {
    Idle,       // 静止
    Active,     // 活跃
    Thinking,   // 思考中
    Streaming   // 输出中
}

/**
 * 会话脉搏指示器
 */
@Composable
fun PulseIndicator(
    state: PulseState,
    modifier: Modifier = Modifier,
    color: Color = TerminalColors.PulseAmber,
    width: androidx.compose.ui.unit.Dp = 3.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    
    val (animDuration, targetScale, targetAlpha) = when (state) {
        PulseState.Idle -> Triple(0, 1f, 0.3f)
        PulseState.Active -> Triple(1500, 1f, 1f)
        PulseState.Thinking -> Triple(800, 1.2f, 1f)
        PulseState.Streaming -> Triple(500, 1.3f, 1f)
    }
    
    val height by if (state == PulseState.Idle) {
        remember { mutableStateOf(32.dp) }
    } else {
        infiniteTransition.animateDp(
            initialValue = 24.dp,
            targetValue = 48.dp * targetScale,
            animationSpec = infiniteRepeatable(
                animation = tween(animDuration, easing = EaseInOutSine),
                repeatMode = RepeatMode.Reverse
            ),
            label = "height"
        )
    }
    
    val alpha by if (state == PulseState.Idle) {
        remember { mutableStateOf(0.3f) }
    } else {
        infiniteTransition.animateFloat(
            initialValue = 0.6f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(animDuration / 2, easing = EaseInOutSine),
                repeatMode = RepeatMode.Reverse
            ),
            label = "alpha"
        )
    }
    
    val glowColor = color.copy(alpha = alpha * 0.5f)
    
    Box(
        modifier = modifier
            .width(width)
            .height(height)
            .clip(RoundedCornerShape(2.dp))
            .drawBehind {
                if (state != PulseState.Idle) {
                    drawRect(
                        color = glowColor,
                        size = size.copy(width = size.width * 3),
                        topLeft = androidx.compose.ui.geometry.Offset(
                            x = -size.width,
                            y = 0f
                        )
                    )
                }
            }
            .background(color.copy(alpha = alpha))
    )
}

/**
 * 流式光标
 */
@Composable
fun StreamingCursor(
    modifier: Modifier = Modifier,
    color: Color = TerminalColors.PulseAmber,
    fontSize: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.sp(14)
) {
    val infiniteTransition = rememberInfiniteTransition(label = "cursor")
    
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cursor_alpha"
    )
    
    androidx.compose.material3.Text(
        text = "▌",
        color = color.copy(alpha = alpha),
        fontSize = fontSize,
        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
        modifier = modifier
    )
}

/**
 * 思考中指示器
 */
@Composable
fun ThinkingIndicator(
    modifier: Modifier = Modifier,
    color: Color = TerminalColors.PulseAmber
) {
    val infiniteTransition = rememberInfiniteTransition(label = "thinking")
    
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        repeat(3) { index ->
            val delay = index * 150
            val scale by infiniteTransition.animateFloat(
                initialValue = 0.5f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(400, delayMillis = delay, easing = EaseInOutSine),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dot_$index"
            )
            
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .alpha(scale)
                    .background(color, RoundedCornerShape(4.dp))
            )
        }
    }
}