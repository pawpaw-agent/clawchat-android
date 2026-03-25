package com.openclaw.clawchat.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openclaw.clawchat.ui.state.MessageContentItem
import com.openclaw.clawchat.ui.state.MessageUi

/**
 * Markdown 文本渲染组件
 * 
 * 支持的语法：
 * - 标题 (# ## ###)
 * - 粗体 (**text**)
 * - 斜体 (*text* 或 _text_)
 * - 行内代码 (`code`)
 * - 代码块 (```lang ... ```)
 * - 列表 (- item 或 * item 或 1. item)
 * - 链接 [text](url)
 * - 表格
 */
@Composable
fun MarkdownText(
    content: String,
    modifier: Modifier = Modifier,
    fontSize: androidx.compose.ui.unit.TextUnit = 13.sp,
    textColor: Color = Color.Unspecified
) {
    // 检测是否有代码块或表格
    val hasCodeBlock = content.contains("```")
    val hasTable = remember(content) {
        val lines = content.lines()
        val pipeLines = lines.count { it.trim().startsWith("|") && it.trim().endsWith("|") }
        pipeLines >= 2
    }
    
    when {
        hasCodeBlock -> MarkdownWithCodeBlocks(content, fontSize, textColor, modifier)
        hasTable -> MarkdownTableContent(content, fontSize, textColor, modifier)
        else -> MarkdownRegularContent(content, fontSize, textColor, modifier)
    }
}

/**
 * 带代码块的 Markdown 渲染
 */
@Composable
private fun MarkdownWithCodeBlocks(
    content: String,
    fontSize: androidx.compose.ui.unit.TextUnit,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    val segments = remember(content) { parseMarkdownSegments(content) }
    
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        segments.forEach { segment ->
            when (segment) {
                is MarkdownSegment.CodeBlock -> {
                    CodeBlockContent(
                        code = segment.code,
                        language = segment.language,
                        fontSize = fontSize
                    )
                }
                is MarkdownSegment.Text -> {
                    if (segment.text.isNotBlank()) {
                        val hasTable = segment.text.lines()
                            .count { it.trim().startsWith("|") && it.trim().endsWith("|") } >= 2
                        
                        if (hasTable) {
                            MarkdownTableContent(segment.text, fontSize, textColor, Modifier)
                        } else {
                            MarkdownRegularContent(segment.text, fontSize, textColor, Modifier)
                        }
                    }
                }
            }
        }
    }
}

/**
 * 代码块内容
 */
@Composable
private fun CodeBlockContent(
    code: String,
    language: String,
    fontSize: androidx.compose.ui.unit.TextUnit
) {
    val scrollState = rememberScrollState()
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 语言标签
            if (language.isNotBlank()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                ) {
                    Text(
                        text = language,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            
            // 代码内容
            SelectionContainer {
                Text(
                    text = code.trimEnd(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(scrollState)
                        .padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    fontSize = (fontSize.value - 2).sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 解析 Markdown 段落
 */
private sealed class MarkdownSegment {
    data class Text(val text: String) : MarkdownSegment()
    data class CodeBlock(val code: String, val language: String) : MarkdownSegment()
}

private fun parseMarkdownSegments(content: String): List<MarkdownSegment> {
    val segments = mutableListOf<MarkdownSegment>()
    val lines = content.lines()
    val currentText = StringBuilder()
    var inCodeBlock = false
    var codeBlockContent = StringBuilder()
    var codeBlockLanguage = ""
    
    for (line in lines) {
        if (line.trim().startsWith("```")) {
            if (inCodeBlock) {
                // 结束代码块
                segments.add(MarkdownSegment.CodeBlock(
                    code = codeBlockContent.toString(),
                    language = codeBlockLanguage
                ))
                codeBlockContent = StringBuilder()
                codeBlockLanguage = ""
                inCodeBlock = false
            } else {
                // 开始代码块
                if (currentText.isNotBlank()) {
                    segments.add(MarkdownSegment.Text(currentText.toString().trimEnd()))
                    currentText.clear()
                }
                codeBlockLanguage = line.trim().removePrefix("```").trim()
                inCodeBlock = true
            }
        } else if (inCodeBlock) {
            codeBlockContent.appendLine(line)
        } else {
            currentText.appendLine(line)
        }
    }
    
    // 处理未结束的代码块
    if (inCodeBlock && codeBlockContent.isNotBlank()) {
        segments.add(MarkdownSegment.CodeBlock(
            code = codeBlockContent.toString(),
            language = codeBlockLanguage
        ))
    } else if (currentText.isNotBlank()) {
        segments.add(MarkdownSegment.Text(currentText.toString().trimEnd()))
    }
    
    return segments
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
        var inList = false
        
        for (line in lines) {
            if (!isFirst) append("\n")
            isFirst = false
            
            val trimmedLine = line.trim()
            
            when {
                // 标题
                trimmedLine.startsWith("### ") -> {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 14.sp)) {
                        append(trimmedLine.removePrefix("### "))
                    }
                }
                trimmedLine.startsWith("## ") -> {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp)) {
                        append(trimmedLine.removePrefix("## "))
                    }
                }
                trimmedLine.startsWith("# ") -> {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 18.sp)) {
                        append(trimmedLine.removePrefix("# "))
                    }
                }
                // 有序列表
                trimmedLine.matches(Regex("^\\d+\\.\\s+.*")) -> {
                    val match = Regex("^(\\d+)\\.\\s+(.*)$").find(trimmedLine)
                    if (match != null) {
                        val number = match.groupValues[1]
                        val text = match.groupValues[2]
                        append("$number. ")
                        appendStyledText(text)
                        inList = true
                    }
                }
                // 无序列表
                trimmedLine.startsWith("- ") || trimmedLine.startsWith("* ") -> {
                    append("• ")
                    appendStyledText(trimmedLine.removePrefix("- ").removePrefix("* "))
                    inList = true
                }
                // 空行
                trimmedLine.isEmpty() -> {
                    if (inList) {
                        inList = false
                    }
                }
                // 普通文本（处理行内样式）
                else -> {
                    appendStyledText(line)
                }
            }
        }
    }
}

/**
 * 处理行内样式（粗体、斜体、行内代码）
 */
private fun AnnotatedString.Builder.appendStyledText(text: String) {
    var i = 0
    while (i < text.length) {
        when {
            // 粗体 **text**
            i + 1 < text.length && text[i] == '*' && text[i + 1] == '*' -> {
                val end = text.indexOf("**", i + 2)
                if (end != -1) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(text.substring(i + 2, end))
                    }
                    i = end + 2
                } else {
                    append(text.substring(i, i + 1))
                    i++
                }
            }
            // 斜体 *text* 或 _text_
            text[i] == '*' && (i == 0 || text[i - 1] != '*') -> {
                val end = text.indexOf('*', i + 1)
                if (end != -1 && end > i + 1) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                } else {
                    append(text.substring(i, i + 1))
                    i++
                }
            }
            text[i] == '_' && i + 1 < text.length -> {
                val end = text.indexOf('_', i + 1)
                if (end != -1 && end > i + 1) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                } else {
                    append(text.substring(i, i + 1))
                    i++
                }
            }
            // 行内代码 `code`
            text[i] == '`' -> {
                val end = text.indexOf('`', i + 1)
                if (end != -1 && end > i + 1) {
                    withStyle(SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF60A5FA),
                        background = Color(0x1A60A5FA)
                    )) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                } else {
                    append(text.substring(i, i + 1))
                    i++
                }
            }
            // 链接 [text](url)
            text[i] == '[' -> {
                val textEnd = text.indexOf(']', i + 1)
                if (textEnd != -1 && textEnd + 1 < text.length && text[textEnd + 1] == '(') {
                    val urlEnd = text.indexOf(')', textEnd + 2)
                    if (urlEnd != -1) {
                        val linkText = text.substring(i + 1, textEnd)
                        withStyle(SpanStyle(
                            color = Color(0xFF2196F3),
                            fontWeight = FontWeight.Medium
                        )) {
                            append(linkText)
                        }
                        i = urlEnd + 1
                    } else {
                        append(text.substring(i, i + 1))
                        i++
                    }
                } else {
                    append(text.substring(i, i + 1))
                    i++
                }
            }
            else -> {
                append(text.substring(i, i + 1))
                i++
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
        if (beforeTable.isNotEmpty()) {
            MarkdownRegularContent(
                content = beforeTable.joinToString("\n"),
                fontSize = fontSize,
                textColor = textColor
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        
        if (tableLines.isNotEmpty()) {
            RenderTable(
                tableLines = tableLines,
                fontSize = fontSize,
                textColor = textColor
            )
        }
        
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
        
        dataLines.forEachIndexed { rowIndex, line ->
            val cells = parseTableRow(line)
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