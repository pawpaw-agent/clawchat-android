package com.openclaw.clawchat.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.snipme.highlights.Highlights
import dev.snipme.highlights.model.SyntaxThemes
import io.github.dsheirer.mmarkdown.MarkdownParser
import io.github.dsheirer.mmarkdown.model.Element

/**
 * Markdown 文本渲染组件（支持表格）
 */
@Composable
fun MarkdownText(
    content: String,
    modifier: Modifier = Modifier,
    fontSize: androidx.compose.ui.unit.TextUnit = 13.sp,
    textColor: Color = Color.Unspecified
) {
    // 检查是否包含表格（更严格的检测）
    val hasTable = remember(content) {
        val lines = content.lines()
        val pipeLines = lines.count { it.trim().startsWith("|") && it.trim().endsWith("|") }
        pipeLines >= 2 // 至少有表头和一行数据
    }
    
    if (hasTable) {
        // 使用表格渲染
        MarkdownTableContent(
            content = content,
            fontSize = fontSize,
            textColor = textColor,
            modifier = modifier
        )
    } else {
        // 普通文本渲染
        MarkdownRegularContent(
            content = content,
            fontSize = fontSize,
            textColor = textColor,
            modifier = modifier
        )
    }
}

/**
 * 普通文本渲染
 */
@Composable
private fun MarkdownRegularContent(
    content: String,
    fontSize: androidx.compose.ui.unit.TextUnit,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    val elements = remember(content) {
        try {
            MarkdownParser.parse(content)
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    Column(modifier = modifier) {
        elements.forEach { element ->
            when (element) {
                is Element.Text -> {
                    Text(
                        text = element.value,
                        fontSize = fontSize,
                        color = textColor
                    )
                }
                is Element.Bold -> {
                    Text(
                        text = element.value,
                        fontSize = fontSize,
                        color = textColor,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                }
                is Element.Code -> {
                    Text(
                        text = element.value,
                        fontSize = (fontSize.value - 1).sp,
                        color = Color(0xFF60A5FA),
                        fontFamily = FontFamily.Monospace
                    )
                }
                is Element.Link -> {
                    Text(
                        text = element.value,
                        fontSize = fontSize,
                        color = Color(0xFF3B82F6)
                    )
                }
                else -> {
                    Text(
                        text = when (element) {
                            is Element.Heading -> element.value
                            is Element.Paragraph -> element.value
                            else -> ""
                        },
                        fontSize = fontSize,
                        color = textColor
                    )
                }
            }
        }
    }
}

/**
 * Markdown 表格渲染
 */
@Composable
private fun MarkdownTableContent(
    content: String,
    fontSize: androidx.compose.ui.unit.TextUnit,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    val lines = content.lines()
    val tableLines = mutableListOf<String>()
    val beforeTable = mutableListOf<String>()
    val afterTable = mutableListOf<String>()
    
    var inTable = false
    var tableEnded = false
    
    // 分离表格和非表格内容
    lines.forEach { line ->
        if (line.trim().startsWith("|") && line.trim().endsWith("|")) {
            if (!tableEnded) {
                inTable = true
                tableLines.add(line)
            } else {
                afterTable.add(line)
            }
        } else {
            if (inTable) {
                tableEnded = true
            }
            if (!inTable && !tableEnded) {
                beforeTable.add(line)
            } else {
                afterTable.add(line)
            }
        }
    }
    
    Column(modifier = modifier) {
        // 表格前的内容
        if (beforeTable.isNotEmpty()) {
            MarkdownRegularContent(
                content = beforeTable.joinToString("\n"),
                fontSize = fontSize,
                textColor = textColor
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        
        // 表格渲染
        if (tableLines.isNotEmpty()) {
            RenderTable(
                tableLines = tableLines,
                fontSize = fontSize,
                textColor = textColor
            )
        }
        
        // 表格后的内容
        if (afterTable.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            MarkdownRegularContent(
                content = afterTable.joinToString("\n"),
                fontSize = fontSize,
                textColor = textColor
            )
        }
    }
}

/**
 * 渲染表格
 */
@Composable
private fun RenderTable(
    tableLines: List<String>,
    fontSize: androidx.compose.ui.unit.TextUnit,
    textColor: Color
) {
    val headerLine = tableLines.firstOrNull() ?: return
    val dataLines = tableLines.drop(1).filter { !it.contains("---") && !it.contains(":--") }
    
    val headers = parseTableRow(headerLine)
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(4.dp)
    ) {
        Column(modifier = Modifier.padding(1.dp)) {
            // 表头
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                headers.forEach { header ->
                    Text(
                        text = header,
                        fontSize = (fontSize.value - 1).sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            
            // 数据行
            dataLines.forEachIndexed { index, line ->
                val cells = parseTableRow(line)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (index % 2 == 0) Color.Transparent 
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    cells.forEach { cell ->
                        Text(
                            text = cell,
                            fontSize = (fontSize.value - 1).sp,
                            color = textColor,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * 解析表格行
 */
private fun parseTableRow(line: String): List<String> {
    return line
        .trim()
        .trim('|')
        .split("|")
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.all { c -> c == '-' || c == ':' || c == ' ' } }
}

/**
 * 格式化 Markdown 消息
 */
fun formatMessageAsMarkdown(message: com.openclaw.clawchat.ui.state.MessageUi): String {
    val sb = StringBuilder()
    
    message.content.forEach { item ->
        when (item) {
            is com.openclaw.clawchat.ui.state.MessageContentItem.Text -> {
                sb.append(item.text)
            }
            is com.openclaw.clawchat.ui.state.MessageContentItem.Image -> {
                sb.append("[图片]")
            }
            is com.openclaw.clawchat.ui.state.MessageContentItem.ToolCall -> {
                sb.append("\n```\n工具调用: ${item.name}\n```")
            }
            is com.openclaw.clawchat.ui.state.MessageContentItem.ToolResult -> {
                sb.append("\n```\n工具结果: ${item.name}\n```")
            }
            else -> {}
        }
    }
    
    return sb.toString()
}