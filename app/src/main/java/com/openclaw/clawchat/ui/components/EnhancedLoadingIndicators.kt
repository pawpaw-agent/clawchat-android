package com.openclaw.clawchat.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 波浪加载动画 - 类似 webchat 风格的加载效果
 */
@Composable
fun WaveLoadingIndicator(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    waveCount: Int = 3,
    amplitude: Dp = 8.dp,
    frequency: Float = 0.02f,
    speed: Float = 2f
) {
    val infiniteTransition = rememberInfiniteTransition(label = "wave_transition")
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = (1000 / speed).toInt(), easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_progress"
    )

    Canvas(modifier = modifier.fillMaxWidth()) {
        val width = size.width
        val height = size.height
        val centerY = height / 2

        for (i in 0 until waveCount) {
            val offsetX = (width / waveCount) * i
            val path = Path()
            path.moveTo(0f, centerY)
            for (x in 0..width.toInt() step 10) {
                val waveY = centerY + amplitude.toPx() * kotlin.math.sin(
                    frequency * x + progress + (i * PI / waveCount)
                ).toFloat()
                path.lineTo(x.toFloat(), waveY)
            }
            path.lineTo(width, height)
            path.lineTo(0f, height)
            path.close()

            drawPath(
                path = path,
                color = color.copy(alpha = 0.6f)
            )
        }
    }
}

/**
 * 脉冲圆点加载动画
 */
@Composable
fun PulseDotLoadingIndicator(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    dotCount: Int = 3
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until dotCount) {
            PulseDot(
                index = i,
                color = color
            )
        }
    }
}

@Composable
private fun PulseDot(
    index: Int,
    color: Color
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_transition")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1000
                0.6f at 0
                1f at 300 + index * 100
                0.6f at 600 + index * 100
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "scale"
    )

    Canvas(
        modifier = Modifier
            .size(8.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    ) {
        drawCircle(
            color = color,
            radius = 4.dp.toPx(),
            center = Offset(size.width / 2, size.height / 2)
        )
    }
}

/**
 * 圆形脉冲加载指示器
 */
@Composable
fun CircularPulseIndicator(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    size: Dp = 40.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_circle_transition")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "circle_scale"
    )

    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "circle_alpha"
    )

    Box(
        modifier = Modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .alpha(alpha)
            .background(color, shape = CircleShape)
    )
}