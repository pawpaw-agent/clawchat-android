package com.openclaw.clawchat.ui.components

import com.openclaw.clawchat.R
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
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
    textColor: Color = Color.Unspecified,
    isStreaming: Boolean = false  // 流式优化：标记是否在流式输出
) {
    // 流式优化：减少检测频率
    // 非流式：每次检测
    // 流式：内容变化超过 50 字符才重新检测
    val lastCheckedLength = remember { mutableStateOf(0) }
    val shouldRecheck = !isStreaming || content.length - lastCheckedLength.value > 50
    
    if (shouldRecheck) {
        lastCheckedLength.value = content.length
    }
    
    val hasCodeBlock = if (shouldRecheck) content.contains("```") else remember { false }
    val hasTable = if (shouldRecheck) {
        val lines = content.lines()
        val pipeLines = lines.count { it.trim().startsWith("|") && it.trim().endsWith("|") }
        pipeLines >= 2
    } else remember { false }
    
    when {
        hasCodeBlock -> MarkdownWithCodeBlocks(content, fontSize, textColor, modifier, isStreaming)
        hasTable -> MarkdownTableContent(content, fontSize, textColor, modifier)
        else -> MarkdownRegularContent(content, fontSize, textColor, modifier, isStreaming)
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
    modifier: Modifier = Modifier,
    isStreaming: Boolean = false
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
                            MarkdownRegularContent(segment.text, fontSize, textColor, Modifier, isStreaming)
                        }
                    }
                }
            }
        }
    }
}

/**
 * 代码块内容（带行号）
 */
@Composable
private fun CodeBlockContent(
    code: String,
    language: String,
    fontSize: androidx.compose.ui.unit.TextUnit
) {
    val scrollState = rememberScrollState()
    val annotatedCode = remember(code, language) {
        highlightSyntax(code, language)
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    val lines = remember(code) { code.lines() }
    val lineCount = lines.size

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 语言标签和复制按钮
            if (language.isNotBlank()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = language,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        // 复制按钮
                        androidx.compose.material3.TextButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(code))
                                android.widget.Toast.makeText(context, context.getString(R.string.message_copy_code), android.widget.Toast.LENGTH_SHORT).show()
                            },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            androidx.compose.material3.Text(
                                text = context.getString(R.string.message_copy_code),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }

            // 代码内容（带行号）
            SelectionContainer {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(scrollState)
                        .padding(12.dp)
                ) {
                    // 行号列
                    if (lineCount > 1) {
                        Column(
                            modifier = Modifier.padding(end = 12.dp)
                        ) {
                            repeat(lineCount) { index ->
                                Text(
                                    text = "${index + 1}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = (fontSize.value - 2).sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }

                    // 代码内容
                    Text(
                        text = annotatedCode,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        fontSize = (fontSize.value - 2).sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
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
    modifier: Modifier = Modifier,
    isStreaming: Boolean = false
) {
    val uriHandler = LocalUriHandler.current
    // 流式优化：减少解析频率
    // 非流式：每次解析
    // 流式：内容变化超过 50 字符才重新解析
    val lastParsedLength = remember { mutableStateOf(0) }
    val shouldParse = !isStreaming || content.length - lastParsedLength.value > 50

    if (shouldParse) {
        lastParsedLength.value = content.length
    }

    // 在 @Composable 上下文中获取颜色
    val linkColor = MaterialTheme.colorScheme.primary
    val codeColor = MaterialTheme.colorScheme.tertiary
    val codeBgColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)

    val cachedAnnotatedString = remember(lastParsedLength.value) {
        if (shouldParse) parseMarkdownToAnnotatedString(
            content,
            linkColor = linkColor,
            codeColor = codeColor,
            codeBgColor = codeBgColor
        ) else null
    }

    // 流式时使用缓存的解析结果，最后 50 字符用原始文本显示
    val annotatedString = if (isStreaming && cachedAnnotatedString != null && !shouldParse) {
        buildAnnotatedString {
            append(cachedAnnotatedString)
            append(content.substring(lastParsedLength.value))
        }
    } else {
        cachedAnnotatedString ?: parseMarkdownToAnnotatedString(
            content,
            linkColor = linkColor,
            codeColor = codeColor,
            codeBgColor = codeBgColor
        )
    }
    
    // 检查是否有链接
    val hasLinks = annotatedString.getStringAnnotations(0, annotatedString.length).any { it.tag == "URL" }
    
    if (hasLinks) {
        ClickableText(
            text = annotatedString,
            onClick = { offset ->
                annotatedString.getStringAnnotations(offset, offset)
                    .firstOrNull { it.tag == "URL" }
                    ?.let { annotation ->
                        try {
                            uriHandler.openUri(annotation.item)
                        } catch (e: Exception) {
                            // 忽略无效链接
                        }
                    }
            },
            style = androidx.compose.ui.text.TextStyle(
                fontSize = fontSize,
                color = textColor
            ),
            modifier = modifier
        )
    } else {
        SelectionContainer {
            Text(
                text = annotatedString,
                fontSize = fontSize,
                color = textColor,
                modifier = modifier
            )
        }
    }
}


/**
 * 处理行内样式（粗体、斜体、行内代码）
 * 注意：此函数未使用，保留作为备用
 */
@Suppress("unused")
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
                    // 使用固定颜色（此函数未使用，保留作为备用）
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
                        val url = text.substring(textEnd + 2, urlEnd)
                        pushStringAnnotation(tag = "URL", annotation = url)
                        withStyle(SpanStyle(
                            color = Color(0xFF2196F3),
                            fontWeight = FontWeight.Medium
                        )) {
                            append(linkText)
                        }
                        pop()
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
