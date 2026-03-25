package com.openclaw.clawchat.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openclaw.clawchat.ui.state.MessageContentItem
import com.openclaw.clawchat.ui.state.MessageUi

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
    val annotatedString = remember(content) {
        parseMarkdownToAnnotatedString(content)
    }
    
    SelectionContainer {
        Text(
            text = annotatedString,
            fontSize = fontSize,
            color = textColor,
            modifier = modifier
        )
    }
}

/**
 * 解析 Markdown 为 AnnotatedString
 */
private fun parseMarkdownToAnnotatedString(content: String): AnnotatedString {
    return buildAnnotatedString {
        val lines = content.lines()
        var isFirst = true
        
        for (line in lines) {
            if (!isFirst) append("\n")
            isFirst = false
            
            when {
                // 标题
                line.startsWith("### ") -> {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 14.sp)) {
                        append(line.removePrefix("### "))
                    }
                }
                line.startsWith("## ") -> {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp)) {
                        append(line.removePrefix("## "))
                    }
                }
                line.startsWith("# ") -> {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 18.sp)) {
                        append(line.removePrefix("# "))
                    }
                }
                // 粗体 **text**
                line.contains("**") -> {
                    var text = line
                    var inBold = false
                    var result = StringBuilder()
                    var i = 0
                    while (i < text.length) {
                        if (i + 1 < text.length && text[i] == '*' && text[i + 1] == '*') {
                            if (inBold) {
                                result.append("</b>")
                            } else {
                                result.append("<b>")
                            }
                            inBold = !inBold
                            i += 2
                        } else {
                            result.append(text[i])
                            i++
                        }
                    }
                    // 简化处理：直接显示原始文本
                    append(line.replace("**", ""))
                }
                // 代码块
                line.startsWith("```") -> {
                    // 跳过代码块标记
                }
                // 行内代码 `code`
                line.contains("`") -> {
                    val parts = line.split("`")
                    for ((index, part) in parts.withIndex()) {
                        if (index % 2 == 1) {
                            withStyle(SpanStyle(fontFamily = FontFamily.Monospace, color = Color(0xFF60A5FA))) {
                                append(part)
                            }
                        } else {
                            append(part)
                        }
                    }
                }
                // 列表
                line.trim().startsWith("- ") || line.trim().startsWith("* ") -> {
                    append("• ")
                    append(line.trim().removePrefix("- ").removePrefix("* "))
                }
                // 普通文本
                else -> {
                    append(line)
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
 * 渲染表格（带边框和网格线）
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
    val columnCount = headers.size
    
    // 表格颜色
    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    val headerBgColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
    val evenRowBgColor = MaterialTheme.colorScheme.surface
    val oddRowBgColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .border(1.dp, borderColor, RoundedCornerShape(4.dp))
    ) {
        // 表头行
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(headerBgColor)
        ) {
            headers.forEachIndexed { index, header ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .border(
                            width = 1.dp,
                            color = borderColor,
                            shape = if (index == 0) RoundedCornerShape(topStart = 4.dp)
                                   else if (index == columnCount - 1) RoundedCornerShape(topEnd = 4.dp)
                                   else RoundedCornerShape(0.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = header,
                        fontSize = (fontSize.value - 1).sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        
        // 数据行
        dataLines.forEachIndexed { rowIndex, line ->
            val cells = parseTableRow(line)
            // 确保单元格数量与表头一致
            val paddedCells = cells + List(maxOf(0, columnCount - cells.size)) { "" }
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (rowIndex % 2 == 0) evenRowBgColor else oddRowBgColor)
            ) {
                paddedCells.take(columnCount).forEachIndexed { colIndex, cell ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .border(1.dp, borderColor)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = cell,
                            fontSize = (fontSize.value - 1).sp,
                            color = textColor
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
fun formatMessageAsMarkdown(message: MessageUi): String {
    val sb = StringBuilder()
    
    message.content.forEach { item ->
        when (item) {
            is MessageContentItem.Text -> {
                sb.append(item.text)
            }
            is MessageContentItem.Image -> {
                sb.append("[图片]")
            }
            is MessageContentItem.ToolCall -> {
                sb.append("\n```\n工具调用: ${item.name}\n```")
            }
            is MessageContentItem.ToolResult -> {
                sb.append("\n```\n工具结果: ${item.name}\n```")
            }
            else -> {}
        }
    }
    
    return sb.toString()
}