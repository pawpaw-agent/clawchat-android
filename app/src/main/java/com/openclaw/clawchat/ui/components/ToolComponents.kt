package com.openclaw.clawchat.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.openclaw.clawchat.ui.state.ToolCard
import com.openclaw.clawchat.ui.state.ToolCardKind

/**
 * 工具卡片组件 - 实现 webchat 风格的工具调用显示
 */
@Composable
fun ToolDetailCard(
    toolCard: ToolCard,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (toolCard.kind) {
                ToolCardKind.CALL -> MaterialTheme.colorScheme.secondaryContainer
                ToolCardKind.RESULT -> if (toolCard.isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer
            }
        )
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
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = when (toolCard.kind) {
                            ToolCardKind.CALL -> Icons.Default.SettingsApplications
                            ToolCardKind.RESULT -> if (toolCard.isError) Icons.Default.Warning else Icons.Default.Done
                        },
                        contentDescription = null,
                        tint = when (toolCard.kind) {
                            ToolCardKind.CALL -> MaterialTheme.colorScheme.secondary
                            ToolCardKind.RESULT -> if (toolCard.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary
                        }
                    )
                    Text(
                        text = toolCard.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        color = when (toolCard.kind) {
                            ToolCardKind.CALL -> MaterialTheme.colorScheme.onSecondaryContainer
                            ToolCardKind.RESULT -> if (toolCard.isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onTertiaryContainer
                        }
                    )
                }

                if (toolCard.phase != null) {
                    Text(
                        text = toolCard.phase,
                        style = MaterialTheme.typography.labelSmall,
                        color = when (toolCard.kind) {
                            ToolCardKind.CALL -> MaterialTheme.colorScheme.onSecondaryContainer
                            ToolCardKind.RESULT -> if (toolCard.isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onTertiaryContainer
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 工具参数
            if (toolCard.args != null) {
                ExpansionPanel(
                    title = "参数",
                    content = {
                        CodeBlock(text = toolCard.args)
                    }
                )
            }

            // 工具结果
            if (toolCard.result != null) {
                ExpansionPanel(
                    title = if (toolCard.isError) "错误" else "结果",
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
                    }
                )
            }
        }
    }
}

/**
 * 可展开面板组件
 */
@Composable
fun ExpansionPanel(
    title: String,
    content: @Composable () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

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
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
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
            containerColor = MaterialTheme.colorScheme.surfaceVariant
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
        // 尝试简单判断是否为JSON格式
        str.trim().startsWith("{") && str.trim().endsWith("}") ||
        str.trim().startsWith("[") && str.trim().endsWith("]")
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