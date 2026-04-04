package com.openclaw.clawchat.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 加载指示器组件集合 - 丰富 webchat 风格的加载状态
 */

@Composable
fun LoadingIndicators() {
    // 这个组件用于组织各种加载指示器
}

/**
 * 波浪加载动画
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
        targetValue = 2 * kotlin.math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = (1000 / speed).roundToInt(), easing = LinearEasing)
        ),
        label = "wave_progress"
    )

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val centerY = height / 2

        for (i in 0 until waveCount) {
            val offsetX = (width / waveCount) * i
            drawPath(
                path = android.graphics.Path().asComposePath().apply {
                    moveTo(0f, centerY)
                    for (x in 0..width.toInt() step 10) {
                        val waveY = centerY + amplitude.toPx() * kotlin.math.sin(
                            frequency * x + progress + (i * kotlin.math.PI / waveCount)
                        ).toFloat()
                        lineTo(x.toFloat(), waveY)
                    }
                    lineTo(width, height)
                    lineTo(0f, height)
                    close()
                },
                brush = Brush.verticalGradient(
                    listOf(
                        color.copy(alpha = 0.6f),
                        color.copy(alpha = 0.2f)
                    )
                )
            )
        }
    }
}

/**
 * 脉冲圆点加载动画
 */
@Composable
fun PulseDotLoading(
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
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .size(size)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    this.alpha = alpha
                }
                .background(color, CircleShape)
        )
    }
}

/**
 * 滑动条加载指示器
 */
@Composable
fun ShimmerLoadingIndicator(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    shimmerColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
) {
    val transition = rememberInfiniteTransition(label = "shimmer_transition")
    val offset by transition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_offset"
    )

    val brush = Brush.horizontalGradient(
        colors = listOf(
            shimmerColor,
            color,
            shimmerColor
        ),
        startX = (-size.width.value * offset).dp.value,
        endX = (size.width.value * (1 - offset)).dp.value
    )

    LinearProgressIndicator(
        modifier = modifier,
        color = color,
        trackColor = shimmerColor,
        strokeCap = StrokeCap.Round
    )
}

/**
 * 组合加载指示器
 */
@Composable
fun EnhancedLoadingIndicator(
    modifier: Modifier = Modifier,
    loadingText: String = "加载中...",
    showText: Boolean = true
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(32.dp),
            strokeWidth = 3.dp,
            color = MaterialTheme.colorScheme.primary
        )

        if (showText) {
            androidx.compose.foundation.text.selection.SelectionContainer {
                androidx.compose.material3.Text(
                    text = loadingText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}