package com.openclaw.clawchat.ui.components

import com.openclaw.clawchat.ui.components.MarkdownBlock.*
import coil.compose.AsyncImage
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
import androidx.compose.ui.text.style.TextDecoration
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
 *
 * 性能优化：
 * - 流式模式：减少内容检测频率，避免频繁重组
 * - 使用 remember 缓存解析结果
 */
@Composable
fun MarkdownText(
    content: String,
    modifier: Modifier = Modifier,
    fontSize: androidx.compose.ui.unit.TextUnit = 13.sp,
    textColor: Color = Color.Unspecified,
    isStreaming: Boolean = false  // 流式优化：标记是否在流式输出
) {
    // 性能优化：缓存内容类型检测结果
    // 流式模式：内容增长超过 100 字符才重新检测
    // 非流式模式：每次都检测
    var lastCheckedContent by remember { mutableStateOf("") }
    var cachedHasCodeBlock by remember { mutableStateOf(false) }
    var cachedHasTable by remember { mutableStateOf(false) }

    val shouldRecheck = if (isStreaming) {
        content.length - lastCheckedContent.length > 100
    } else {
        content != lastCheckedContent
    }

    if (shouldRecheck) {
        lastCheckedContent = content
        cachedHasCodeBlock = content.contains("```")
        cachedHasTable = if (!cachedHasCodeBlock) {
            val lines = content.lines()
            val pipeLines = lines.count { it.trim().startsWith("|") && it.trim().endsWith("|") }
            pipeLines >= 2
        } else false
    }

    when {
        cachedHasCodeBlock -> MarkdownWithCodeBlocks(content, fontSize, textColor, modifier, isStreaming)
        cachedHasTable -> MarkdownTableContent(content, fontSize, textColor, modifier)
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
 * 性能优化：使用 remember 缓存解析结果，减少重复解析
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

    // 性能优化：在 Composable 外获取颜色，避免每次重组都获取
    val linkColor = MaterialTheme.colorScheme.primary
    val codeColor = MaterialTheme.colorScheme.tertiary
    val codeBgColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)

    // 性能优化：使用 remember 缓存解析结果
    // 流式模式：只有内容增长超过阈值才重新解析
    // 非流式模式：每次内容变化都解析
    var lastParsedContent by remember { mutableStateOf("") }
    var lastParsedResult by remember { mutableStateOf<AnnotatedString?>(null) }

    val shouldReparse = if (isStreaming) {
        // 流式模式：内容增长超过 100 字符才重新解析
        content.length - lastParsedContent.length > 100
    } else {
        // 非流式模式：内容变化就重新解析
        content != lastParsedContent
    }

    val annotatedString = if (shouldReparse || lastParsedResult == null) {
        val result = parseMarkdownToAnnotatedString(
            content,
            linkColor = linkColor,
            codeColor = codeColor,
            codeBgColor = codeBgColor
        )
        lastParsedContent = content
        lastParsedResult = result
        result
    } else {
        lastParsedResult ?: parseMarkdownToAnnotatedString(content, linkColor, codeColor, codeBgColor)
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
 * 基于块级解析的完整 Markdown 渲染
 * 支持：代码块、表格、块引用、任务列表、分割线、图片、标题、列表
 */
@Composable
fun MarkdownBlockRenderer(
    content: String,
    modifier: Modifier = Modifier,
    fontSize: androidx.compose.ui.unit.TextUnit = 13.sp,
    textColor: Color = Color.Unspecified,
    isStreaming: Boolean = false,
    onImageClick: ((String) -> Unit)? = null
) {
    val blocks = remember(content) { parseMarkdownBlocks(content) }
    val linkColor = MaterialTheme.colorScheme.primary
    val codeColor = MaterialTheme.colorScheme.tertiary
    val codeBgColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        blocks.forEach { block ->
            when (block) {
                is CodeBlock -> {
                    CodeBlockContent(
                        code = block.code,
                        language = block.language,
                        fontSize = fontSize
                    )
                }
                is Text -> {
                    if (block.text.isNotBlank()) {
                        MarkdownRegularContent(
                            content = block.text,
                            fontSize = fontSize,
                            textColor = textColor,
                            isStreaming = isStreaming
                        )
                    }
                }
                is Table -> {
                    RenderStructuredTable(
                        headers = block.headers,
                        rows = block.rows,
                        fontSize = fontSize,
                        textColor = textColor
                    )
                }
                is Blockquote -> {
                    val quoteColor = MaterialTheme.colorScheme.onSurfaceVariant
                    val borderColor = MaterialTheme.colorScheme.primary
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .fillMaxHeight()
                                .background(borderColor, RoundedCornerShape(2.dp))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = block.text,
                            fontSize = fontSize,
                            color = quoteColor.copy(alpha = 0.85f),
                            fontStyle = FontStyle.Italic,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                is TaskItem -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = block.checked,
                            onCheckedChange = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = block.text,
                            fontSize = fontSize,
                            color = textColor,
                            textDecoration = if (block.checked) TextDecoration.LineThrough else null
                        )
                    }
                }
                is HorizontalRule -> {
                    Divider(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                is Image -> {
                    AsyncImage(
                        model = block.url,
                        contentDescription = block.alt,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit
                    )
                }
                is Heading -> {
                    val headingStyle = when (block.level) {
                        1 -> MaterialTheme.typography.headlineLarge
                        2 -> MaterialTheme.typography.headlineMedium
                        3 -> MaterialTheme.typography.headlineSmall
                        4 -> MaterialTheme.typography.titleLarge
                        5 -> MaterialTheme.typography.titleMedium
                        else -> MaterialTheme.typography.titleSmall
                    }
                    Text(
                        text = block.text,
                        style = headingStyle,
                        color = textColor
                    )
                }
                is UnorderedListItem -> {
                    Row(modifier = Modifier.fillMaxWidth().padding(start = 16.dp)) {
                        Text(
                            text = "\u2022 ",
                            fontSize = fontSize,
                            color = textColor,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = block.text,
                            fontSize = fontSize,
                            color = textColor
                        )
                    }
                }
                is OrderListItem -> {
                    Row(modifier = Modifier.fillMaxWidth().padding(start = 16.dp)) {
                        Text(
                            text = "${block.number}. ",
                            fontSize = fontSize,
                            color = textColor,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = block.text,
                            fontSize = fontSize,
                            color = textColor
                        )
                    }
                }
            }
        }
    }
}

/**
 * 渲染结构化的表格（从 MarkdownBlock.Table 数据）
 */
@Composable
private fun RenderStructuredTable(
    headers: List<String>,
    rows: List<List<String>>,
    fontSize: androidx.compose.ui.unit.TextUnit,
    textColor: Color
) {
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

        rows.forEachIndexed { rowIndex, cells ->
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
