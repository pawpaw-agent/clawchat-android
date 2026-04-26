package com.openclaw.clawchat.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.openclaw.clawchat.R
import com.openclaw.clawchat.ui.state.ToolCard

/**
 * 工具图标映射
 */
private val toolIconMap = mapOf(
    "read" to Icons.Outlined.Description,
    "write" to Icons.Outlined.Edit,
    "bash" to Icons.Outlined.Terminal,
    "shell" to Icons.Outlined.Terminal,
    "exec" to Icons.Outlined.Terminal,
    "search" to Icons.Outlined.Search,
    "think" to Icons.Outlined.Psychology
)

/**
 * 获取工具图标
 */
private fun getToolIcon(toolName: String): ImageVector {
    toolIconMap[toolName.lowercase()]?.let { return it }
    val lowerName = toolName.lowercase()
    return when {
        lowerName.contains("file") || lowerName.contains("read") || lowerName.contains("write") -> Icons.Outlined.Description
        lowerName.contains("exec") || lowerName.contains("bash") || lowerName.contains("shell") -> Icons.Outlined.Terminal
        lowerName.contains("search") || lowerName.contains("find") -> Icons.Outlined.Search
        lowerName.contains("think") -> Icons.Outlined.Psychology
        else -> Icons.Outlined.Build
    }
}

/**
 * 工具卡片列表 - 每个卡片占据整行
 */
@Composable
fun ToolCardRow(
    toolCards: List<ToolCard>,
    modifier: Modifier = Modifier
) {
    if (toolCards.isEmpty()) return

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        toolCards.forEach { card ->
            CompactToolCard(toolCard = card, modifier = Modifier.fillMaxWidth())
        }
    }
}

/**
 * 紧凑型工具卡片 - 占据整行，可点击展开
 */
@Composable
fun CompactToolCard(
    toolCard: ToolCard,
    modifier: Modifier = Modifier
) {
    val isRunning = toolCard.phase != "result"
    val hasError = toolCard.isError
    val hasContent = !toolCard.args.isNullOrBlank() || !toolCard.result.isNullOrBlank()

    // 运行中默认展开，完成后折叠
    var expanded by remember(toolCard.callId, isRunning) {
        mutableStateOf(isRunning)
    }

    // 状态颜色：运行中红色，完成蓝色，错误保持红色
    // 使用主题感知颜色
    val statusColor = when {
        hasError -> MaterialTheme.colorScheme.error
        isRunning -> MaterialTheme.colorScheme.error // 运行中用错误色调的红色
        else -> MaterialTheme.colorScheme.primary // 完成用主题主色
    }

    // 背景：使用主题感知颜色，深色/浅色模式自动适配
    val bgColor = when {
        hasError -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
        isRunning -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f) // 运行中用淡红色背景
        else -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) // 完成用淡主色背景
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = hasContent) { expanded = !expanded },
        color = bgColor,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // 左侧状态指示条
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(statusColor)
            )

            Column(modifier = Modifier
                .weight(1f)
                .padding(8.dp)) {
            // 头部：图标 + 名称 + 状态（箭头在最右边）
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // 图标
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(statusColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = getToolIcon(toolCard.name),
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = statusColor
                    )
                    // 运行中指示点
                    if (isRunning && !hasError) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(6.dp)
                                .clip(RoundedCornerShape(50))
                                .background(statusColor)
                        )
                    }
                }

                // 名称
                Text(
                    text = toolCard.name.replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                // 状态/展开图标（最右边）
                if (hasContent) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (isRunning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 1.5.dp,
                        color = statusColor
                    )
                } else if (hasError) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = statusColor
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.CheckCircleOutline,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = statusColor
                    )
                }
            }

            // 运行中的进度条动画
            if (isRunning && !hasError) {
                Spacer(modifier = Modifier.height(4.dp))
                RunningProgressBar(color = statusColor)
            }

            // 展开内容 - 仅显示分割线
            AnimatedVisibility(
                visible = expanded && hasContent,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 6.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
            }
        } // Column 结束
        } // Row 结束
    } // Surface 结束
}

/**
 * 兼容旧接口的工具卡片组件
 */
@Composable
fun ToolDetailCard(
    toolCard: ToolCard,
    modifier: Modifier = Modifier
) {
    CompactToolCard(toolCard = toolCard, modifier = modifier)
}

/**
 * 检查字符串是否为JSON格式
 */
fun isJsonString(str: String): Boolean {
    val trimmed = str.trim()
    return (trimmed.startsWith("{") && trimmed.endsWith("}")) ||
           (trimmed.startsWith("[") && trimmed.endsWith("]"))
}

/**
 * 格式化工具结果，提取关键内容
 */
private fun formatToolResult(toolName: String, result: String): String {
    val json = result.trim()
    if (!json.startsWith("{")) {
        return result.take(300) + if (result.length > 300) "..." else ""
    }

    // 尝试提取关键内容
    return try {
        when (toolName) {
            "web_search" -> {
                // 提取 results 数组或 externalContent
                val resultsMatch = Regex("\"results\"\\s*:\\s*\\[").find(json)
                val contentMatch = Regex("\"externalContent\"\\s*:").find(json)
                if (resultsMatch != null || contentMatch != null) {
                    // 找到 results 开始位置，截取显示
                    val startIdx = resultsMatch?.range?.first ?: contentMatch!!.range.first
                    json.drop(startIdx).take(300) + "..."
                } else {
                    json.take(300) + if (json.length > 300) "..." else ""
                }
            }
            "web_fetch" -> {
                // 提取 content 或 text 字段
                val contentMatch = Regex("\"content\"\\s*:\\s*\"").find(json)
                if (contentMatch != null) {
                    val startIdx = contentMatch.range.last
                    json.drop(startIdx).take(300).removeSuffix("\"").removeSuffix("}").trim() + "..."
                } else {
                    json.take(300) + if (json.length > 300) "..." else ""
                }
            }
            else -> json.take(300) + if (json.length > 300) "..." else ""
        }
    } catch (e: Exception) {
        result.take(300) + if (result.length > 300) "..." else ""
    }
}

/**
 * 格式化工具名称
 */
private fun formatToolName(name: String): String {
    return name
        .replace("_", " ")
        .replace(Regex("([a-z])([A-Z])")) { "${it.groupValues[1]} ${it.groupValues[2]}" }
        .split(" ")
        .joinToString(" ") { word ->
            word.lowercase().replaceFirstChar { it.uppercase() }
        }
}

/**
 * 运行中的进度条动画
 */
@Composable
private fun RunningProgressBar(
    color: Color,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "progress")
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "progressAnim"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(2.dp)
            .clip(RoundedCornerShape(1.dp))
            .background(color.copy(alpha = 0.2f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0.1f, 0.9f))
                .fillMaxHeight()
                .background(color)
        )
    }
}