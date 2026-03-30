package com.openclaw.clawchat.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openclaw.clawchat.ui.theme.DesignTokens
import com.openclaw.clawchat.ui.state.ToolCard
import com.openclaw.clawchat.ui.state.ToolCardKind

/**
 * 工具卡片组件
 * 对应 WebChat .chat-tool-card
 */
@Composable
fun ToolCardCompact(
    toolCard: ToolCard,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val backgroundColor by animateColorAsState(
        targetValue = when {
            toolCard.isError -> DesignTokens.dangerSubtle
            toolCard.kind == ToolCardKind.CALL -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
            else -> DesignTokens.bgHover
        },
        label = "tool_card_bg"
    )
    
    val iconColor = when {
        toolCard.isError -> DesignTokens.danger
        toolCard.kind == ToolCardKind.CALL -> MaterialTheme.colorScheme.tertiary
        else -> DesignTokens.accent2
    }
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(DesignTokens.radiusMd),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // 图标
            Icon(
                imageVector = when {
                    toolCard.isError -> Icons.Default.ErrorOutline
                    toolCard.kind == ToolCardKind.CALL -> Icons.Default.Bolt
                    else -> Icons.Default.CheckCircle
                },
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(12.dp)
            )
            
            // 工具名称
            Text(
                text = toolCard.name.replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = DesignTokens.text,
                modifier = Modifier.weight(1f)
            )
            
            // 状态标签
            if (toolCard.kind == ToolCardKind.CALL) {
                Text(
                    text = "运行中",
                    style = MaterialTheme.typography.labelSmall,
                    color = DesignTokens.muted,
                    fontSize = 10.sp
                )
            } else if (toolCard.result != null) {
                Text(
                    text = if (toolCard.isError) "错误" else "完成",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (toolCard.isError) DesignTokens.danger else DesignTokens.ok,
                    fontSize = 10.sp
                )
            }
        }
    }
}

/**
 * 工具卡片详情（可展开）
 * 对应 WebChat ToolDetailCard
 */
@Composable
fun ToolCardExpanded(
    toolCard: ToolCard,
    initiallyExpanded: Boolean = false,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    val hasContent = when (toolCard.kind) {
        ToolCardKind.CALL -> toolCard.args != null && toolCard.args.isNotBlank()
        ToolCardKind.RESULT -> (toolCard.args != null && toolCard.args.isNotBlank()) ||
                (toolCard.result != null && toolCard.result.isNotBlank())
    }
    
    val backgroundColor = when {
        toolCard.isError -> DesignTokens.dangerSubtle
        toolCard.kind == ToolCardKind.CALL -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
        else -> DesignTokens.bgHover
    }
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = hasContent) { expanded = !expanded },
        shape = RoundedCornerShape(DesignTokens.radiusMd),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            // 头部
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = when {
                        toolCard.isError -> Icons.Default.ErrorOutline
                        toolCard.kind == ToolCardKind.CALL -> Icons.Default.Bolt
                        else -> Icons.Default.CheckCircle
                    },
                    contentDescription = null,
                    tint = when {
                        toolCard.isError -> DesignTokens.danger
                        toolCard.kind == ToolCardKind.CALL -> MaterialTheme.colorScheme.tertiary
                        else -> DesignTokens.accent2
                    },
                    modifier = Modifier.size(12.dp)
                )
                
                Text(
                    text = toolCard.name.replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = DesignTokens.text,
                    modifier = Modifier.weight(1f)
                )
                
                if (hasContent) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = DesignTokens.muted,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
            
            // 展开内容
            if (expanded && hasContent) {
                Spacer(modifier = Modifier.height(4.dp))
                
                SelectionContainer {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        // 参数
                        if (toolCard.args != null && toolCard.args.isNotBlank()) {
                            Text(
                                text = toolCard.args,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = DesignTokens.muted,
                                fontSize = 11.sp
                            )
                        }
                        
                        // 结果
                        if (toolCard.result != null && toolCard.result.isNotBlank()) {
                            if (toolCard.args != null && toolCard.args.isNotBlank()) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 4.dp),
                                    color = DesignTokens.border
                                )
                            }
                            Text(
                                text = toolCard.result,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = if (toolCard.isError) DesignTokens.danger else DesignTokens.text,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 工具标签（紧凑显示）
 * 对应 WebChat .chat-tool-tag
 */
@Composable
fun ToolTag(
    name: String,
    isError: Boolean = false,
    isCall: Boolean = false,
    isActive: Boolean = false,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val backgroundColor = when {
        isError -> DesignTokens.dangerSubtle
        isCall -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
        isActive -> DesignTokens.accentSubtle
        else -> DesignTokens.bgHover
    }
    
    val textColor = when {
        isError -> DesignTokens.danger
        isCall -> MaterialTheme.colorScheme.tertiary
        isActive -> DesignTokens.accent
        else -> DesignTokens.muted
    }
    
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = backgroundColor,
        border = null
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Icon(
                imageVector = when {
                    isError -> Icons.Default.ErrorOutline
                    isCall -> Icons.Default.Bolt
                    else -> Icons.Default.CheckCircle
                },
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(8.dp)
            )
            Text(
                text = name,
                style = MaterialTheme.typography.labelSmall,
                color = textColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}