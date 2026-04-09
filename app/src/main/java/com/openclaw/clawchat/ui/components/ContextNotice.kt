package com.openclaw.clawchat.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.openclaw.clawchat.R
import com.openclaw.clawchat.ui.state.CompactionStatus
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
                    text = stringResource(R.string.context_used, pct),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = color
                )

                Text(
                    text = stringResource(R.string.context_tokens_format, formatTokens(totalTokens), formatTokens(contextTokensLimit)),
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
 * v2026.4.8: 使用 phase 替代 active 布尔值
 *
 * 显示状态：
 * - active: "Compacting context..." + loader
 * - retrying: "Retrying after compaction..." + loader
 * - complete: "Context compacted" + check icon, 5秒后消失
 */
@Composable
fun CompactionIndicator(
    phase: String = "active",           // active, retrying, complete
    completedAt: Long? = null,
    modifier: Modifier = Modifier
) {
    // 完成后显示 5 秒
    var showComplete by remember { mutableStateOf(false) }

    LaunchedEffect(completedAt, phase) {
        if (phase == "complete" && completedAt != null) {
            showComplete = true
            kotlinx.coroutines.delay(5000)
            showComplete = false
        } else {
            showComplete = false
        }
    }

    val isVisible = phase == "active" || phase == "retrying" || showComplete

    AnimatedVisibility(
        visible = isVisible,
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
                when {
                    phase == "active" -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = stringResource(R.string.context_compacting),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    phase == "retrying" -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = stringResource(R.string.context_retrying),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    showComplete -> {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = stringResource(R.string.context_compacted),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

/**
 * Compaction 指示器组件（兼容旧 API）
 */
@Composable
fun CompactionIndicator(
    compactionStatus: CompactionStatus?,
    modifier: Modifier = Modifier
) {
    if (compactionStatus == null) return
    CompactionIndicator(
        phase = compactionStatus.phase,
        completedAt = compactionStatus.completedAt,
        modifier = modifier
    )
}

/**
 * Fallback 指示器组件
 * 参考 webchat: renderFallbackIndicator
 *
 * 显示状态：
 * - active: "Fallback active: {model}" + brain icon
 * - cleared: "Fallback cleared: {model}" + check icon, 8秒后消失
 */
@Composable
fun FallbackIndicator(
    phase: String = "active",           // active, cleared
    selected: String,                    // 当前选中的模型
    active: String = selected,           // fallback active 时的模型
    previous: String? = null,            // 之前的 fallback
    reason: String? = null,              // fallback 原因
    attempts: List<String> = emptyList(), // 尝试过的模型列表
    occurredAt: Long,                    // 发生时间
    modifier: Modifier = Modifier
) {
    // 完成后显示 8 秒
    var showCleared by remember { mutableStateOf(false) }

    LaunchedEffect(occurredAt, phase) {
        if (phase == "cleared") {
            showCleared = true
            kotlinx.coroutines.delay(8000)
            showCleared = false
        } else {
            showCleared = false
        }
    }

    // active 或 cleared (未过期) 时显示
    val elapsed = System.currentTimeMillis() - occurredAt
    val isVisible = phase == "active" || (showCleared && elapsed < 8000)

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            shape = RoundedCornerShape(8.dp),
            color = if (phase == "cleared")
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f),
            contentColor = if (phase == "cleared")
                MaterialTheme.colorScheme.primary
            else
                MaterialTheme.colorScheme.secondary
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (phase == "cleared") {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )

                    Text(
                        text = stringResource(R.string.fallback_cleared, selected),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Psychology,  // brain icon equivalent
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary
                    )

                    Text(
                        text = stringResource(R.string.fallback_active, active),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary
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