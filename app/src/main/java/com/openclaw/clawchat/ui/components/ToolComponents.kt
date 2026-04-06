package com.openclaw.clawchat.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.openclaw.clawchat.ui.state.ToolCard
import com.openclaw.clawchat.ui.state.ToolCardKind

/**
 * 工具图标映射
 */
private val toolIconMap = mapOf(
    // 文件操作
    "read_file" to Icons.Outlined.Description,
    "write_file" to Icons.Outlined.Edit,
    "edit_file" to Icons.Outlined.EditDocument,
    "list_directory" to Icons.Outlined.Folder,
    "create_directory" to Icons.Outlined.CreateNewFolder,
    "delete_file" to Icons.Outlined.Delete,
    "move_file" to Icons.Outlined.DriveFileMove,
    "copy_file" to Icons.Outlined.FileCopy,
    "search_files" to Icons.Outlined.Search,

    // 代码执行
    "execute_command" to Icons.Outlined.Terminal,
    "run_script" to Icons.Outlined.PlayArrow,
    "bash" to Icons.Outlined.Terminal,
    "shell" to Icons.Outlined.Terminal,

    // 网络
    "http_request" to Icons.Outlined.Http,
    "fetch" to Icons.Outlined.CloudDownload,
    "web_search" to Icons.Outlined.TravelExplore,
    "browse" to Icons.Outlined.OpenInBrowser,

    // 数据处理
    "json_parse" to Icons.Outlined.DataObject,
    "xml_parse" to Icons.Outlined.Code,
    "csv_parse" to Icons.Outlined.TableChart,

    // AI 相关
    "think" to Icons.Outlined.Psychology,
    "analyze" to Icons.Outlined.Analytics,
    "summarize" to Icons.Outlined.Summarize,

    // 通用
    "unknown" to Icons.Outlined.Build
)

/**
 * 获取工具图标
 */
private fun getToolIcon(toolName: String): ImageVector {
    // 首先尝试精确匹配
    toolIconMap[toolName.lowercase()]?.let { return it }

    // 然后尝试模糊匹配
    val lowerName = toolName.lowercase()
    return when {
        lowerName.contains("file") || lowerName.contains("fs") -> Icons.Outlined.Description
        lowerName.contains("dir") || lowerName.contains("folder") -> Icons.Outlined.Folder
        lowerName.contains("exec") || lowerName.contains("bash") || lowerName.contains("shell") -> Icons.Outlined.Terminal
        lowerName.contains("http") || lowerName.contains("fetch") || lowerName.contains("request") -> Icons.Outlined.Http
        lowerName.contains("search") || lowerName.contains("find") -> Icons.Outlined.Search
        lowerName.contains("web") || lowerName.contains("browse") -> Icons.Outlined.TravelExplore
        lowerName.contains("code") || lowerName.contains("script") -> Icons.Outlined.Code
        lowerName.contains("data") || lowerName.contains("json") -> Icons.Outlined.DataObject
        lowerName.contains("think") || lowerName.contains("analy") -> Icons.Outlined.Psychology
        else -> Icons.Outlined.Build
    }
}

/**
 * 工具状态颜色
 */
private object ToolColors {
    @Composable
    fun running() = MaterialTheme.colorScheme.primary
    @Composable
    fun success() = MaterialTheme.colorScheme.tertiary
    @Composable
    fun error() = MaterialTheme.colorScheme.error
    @Composable
    fun pending() = MaterialTheme.colorScheme.outline
}

/**
 * 工具卡片组件 - 实现 webchat 风格的工具调用显示
 */
@Composable
fun ToolDetailCard(
    toolCard: ToolCard,
    modifier: Modifier = Modifier
) {
    val isRunning = toolCard.phase != "result"
    val hasError = toolCard.isError

    // 根据状态选择颜色
    val statusColor = when {
        hasError -> ToolColors.error()
        isRunning -> ToolColors.running()
        else -> ToolColors.success()
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                hasError -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                isRunning -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            }
        ),
        border = if (isRunning && !hasError) {
            androidx.compose.foundation.BorderStroke(1.dp, statusColor.copy(alpha = 0.3f))
        } else null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // 工具头部
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 工具图标（带动画）
                    ToolIconWithStatus(
                        toolName = toolCard.name,
                        isRunning = isRunning,
                        hasError = hasError,
                        statusColor = statusColor
                    )

                    Column {
                        Text(
                            text = formatToolName(toolCard.name),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        // 状态标签
                        ToolStatusBadge(
                            phase = toolCard.phase,
                            isError = hasError
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 工具参数
            if (toolCard.args != null) {
                ExpansionPanel(
                    title = "参数",
                    icon = Icons.Outlined.Input,
                    content = {
                        CodeBlock(text = toolCard.args)
                    }
                )
                Spacer(modifier = Modifier.height(6.dp))
            }

            // 工具结果
            if (toolCard.result != null) {
                ExpansionPanel(
                    title = if (toolCard.isError) "错误" else "结果",
                    icon = if (toolCard.isError) Icons.Outlined.ErrorOutline else Icons.Outlined.CheckCircle,
                    content = {
                        when {
                            toolCard.isError -> {
                                Text(
                                    text = toolCard.result!!,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            else -> {
                                if (isJsonString(toolCard.result!!)) {
                                    CodeBlock(text = toolCard.result!!)
                                } else {
                                    Text(
                                        text = toolCard.result!!,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    },
                    defaultExpanded = toolCard.isError // 错误时默认展开
                )
            }
        }
    }
}

/**
 * 工具图标（带状态动画）
 */
@Composable
private fun ToolIconWithStatus(
    toolName: String,
    isRunning: Boolean,
    hasError: Boolean,
    statusColor: Color
) {
    val icon = getToolIcon(toolName)

    // 运行中旋转动画
    val infiniteTransition = rememberInfiniteTransition(label = "tool-icon")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    // 脉冲动画（运行中）
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                statusColor.copy(alpha = 0.15f)
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = statusColor,
            modifier = Modifier
                .size(20.dp)
                .then(
                    if (isRunning && !hasError) {
                        Modifier
                            .rotate(rotation)
                            .graphicsLayer { scaleX = scale; scaleY = scale }
                    } else {
                        Modifier
                    }
                )
        )

        // 运行中指示点
        if (isRunning && !hasError) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(8.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(statusColor)
            )
        }
    }
}

/**
 * 工具状态徽章
 */
@Composable
private fun ToolStatusBadge(
    phase: String?,
    isError: Boolean
) {
    val (text, color) = when {
        isError -> "错误" to MaterialTheme.colorScheme.error
        phase == "result" -> "完成" to MaterialTheme.colorScheme.tertiary
        phase == "update" -> "执行中" to MaterialTheme.colorScheme.primary
        else -> "启动中" to MaterialTheme.colorScheme.primary
    }

    Surface(
        shape = RoundedCornerShape(4.dp),
        color = color.copy(alpha = 0.15f)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

/**
 * 格式化工具名称
 */
private fun formatToolName(name: String): String {
    // 将 snake_case 或 camelCase 转换为可读格式
    return name
        .replace("_", " ")
        .replace(Regex("([a-z])([A-Z])")) { "${it.groupValues[1]} ${it.groupValues[2]}" }
        .split(" ")
        .joinToString(" ") { word ->
            word.lowercase().replaceFirstChar { it.uppercase() }
        }
}

/**
 * 可展开面板组件
 */
@Composable
fun ExpansionPanel(
    title: String,
    icon: ImageVector = Icons.Outlined.Expand,
    content: @Composable () -> Unit,
    defaultExpanded: Boolean = false
) {
    var expanded by remember { mutableStateOf(defaultExpanded) }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(8.dp)
                )
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                .clickable { expanded = !expanded }
                .padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) "收起" else "展开",
                modifier = Modifier.size(18.dp)
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                content()
            }
        }
    }
}

/**
 * 代码块显示组件
 */
@Composable
fun CodeBlock(
    text: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            androidx.compose.foundation.text.selection.SelectionContainer {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 检查字符串是否为JSON格式
 */
fun isJsonString(str: String): Boolean {
    return try {
        val trimmed = str.trim()
        (trimmed.startsWith("{") && trimmed.endsWith("}")) ||
        (trimmed.startsWith("[") && trimmed.endsWith("]"))
    } catch (e: Exception) {
        false
    }
}

/**
 * 流式工具结果显示组件
 */
@Composable
fun StreamingToolResult(
    toolCard: ToolCard,
    modifier: Modifier = Modifier
) {
    var resultText by remember { mutableStateOf(toolCard.result ?: "") }

    // 模拟流式更新（在真实场景中，这会来自流数据）
    LaunchedEffect(toolCard.result) {
        if (!toolCard.result.isNullOrBlank()) {
            resultText += toolCard.result
        }
    }

    ToolDetailCard(
        toolCard = toolCard.copy(result = resultText),
        modifier = modifier
    )
}

/**
 * 工具流式占位符（等待执行）
 */
@Composable
fun ToolPlaceholderCard(
    toolName: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 占位图标动画
            val infiniteTransition = rememberInfiniteTransition(label = "placeholder")
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 0.7f,
                animationSpec = infiniteRepeatable(
                    animation = tween(800, easing = EaseInOut),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "alpha"
            )

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = alpha))
            )

            Column {
                Text(
                    text = formatToolName(toolName),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "准备执行...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}