package com.openclaw.clawchat.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Context 用量警告组件
 * 参考 webchat: renderContextNotice
 * 
 * 显示条件：used / limit >= 0.85
 * 颜色：amber (85%) → red (95%+) 渐变
 */
@Composable
fun ContextNotice(
    totalTokens: Int,
    contextTokensLimit: Int,
    modifier: Modifier = Modifier
) {
    val ratio = totalTokens.toFloat() / contextTokensLimit.toFloat()
    
    // 只在 >= 85% 时显示
    if (ratio < 0.85f) return
    
    val pct = min((ratio * 100).roundToInt(), 100)
    
    // 颜色渐变：amber (85%) → red (95%+)
    // t: 0 at 85%, 1 at 95%+
    val t = min(maxOf((ratio - 0.85f) / 0.1f, 0f), 1f)
    
    // Warn: Color(0xFFF59E0B) - amber
    // Danger: Color(0xFFEF4444) - red
    val warnColor = Color(0xFFF59E0B)
    val dangerColor = Color(0xFFEF4444)
    
    val color = lerpColor(warnColor, dangerColor, t)
    val bgColor = color.copy(alpha = 0.08f + 0.08f * t)
    
    AnimatedVisibility(
        visible = true,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            shape = RoundedCornerShape(8.dp),
            color = bgColor,
            contentColor = color
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = color
                )
                
                Text(
                    text = "$pct% context used",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = color
                )
                
                Text(
                    text = "(${formatTokens(totalTokens)} / ${formatTokens(contextTokensLimit)})",
                    style = MaterialTheme.typography.bodySmall,
                    color = color.copy(alpha = 0.7f)
                )
            }
        }
    }
}

/**
 * Compaction 指示器组件
 * 参考 webchat: renderCompactionIndicator
 * 
 * 显示状态：
 * - active: "Compacting context..." + loader
 * - complete: "Context compacted" + check icon, 2秒后消失
 */
@Composable
fun CompactionIndicator(
    active: Boolean,
    completedAt: Long? = null,
    modifier: Modifier = Modifier
) {
    // 完成后显示 2 秒
    var showComplete by remember { mutableStateOf(false) }
    
    LaunchedEffect(completedAt) {
        if (completedAt != null) {
            showComplete = true
            kotlinx.coroutines.delay(2000)
            showComplete = false
        }
    }
    
    AnimatedVisibility(
        visible = active || showComplete,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (active) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    Text(
                        text = "Compacting context...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else if (showComplete) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    
                    Text(
                        text = "Context compacted",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

// 辅助函数

/**
 * 线性插值两个颜色
 */
private fun lerpColor(start: Color, end: Color, fraction: Float): Color {
    return Color(
        red = start.red + (end.red - start.red) * fraction,
        green = start.green + (end.green - start.green) * fraction,
        blue = start.blue + (end.blue - start.blue) * fraction,
        alpha = start.alpha + (end.alpha - start.alpha) * fraction
    )
}

/**
 * 格式化 token 数量（参考 webchat formatTokensCompact）
 * 120k → "120k", 1500 → "1.5k"
 */
private fun formatTokens(tokens: Int): String {
    return when {
        tokens >= 1000 -> {
            val k = tokens / 1000.0
            if (k >= 100) {
                "${k.roundToInt()}k"
            } else {
                "${k}k"
            }
        }
        else -> tokens.toString()
    }
}