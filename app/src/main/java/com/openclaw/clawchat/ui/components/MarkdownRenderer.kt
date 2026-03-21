package com.openclaw.clawchat.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Markdown 渲染器
 * 对标 webchat 样式
 */
@Composable
fun MarkdownRenderer(
    nodes: List<MarkdownNode>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        nodes.forEach { node ->
            MarkdownNodeRenderer(node)
        }
    }
}

@Composable
private fun MarkdownNodeRenderer(node: MarkdownNode) {
    when (node) {
        is MarkdownNode.Paragraph -> ParagraphRenderer(node)
        is MarkdownNode.Heading -> HeadingRenderer(node)
        is MarkdownNode.CodeBlock -> CodeBlockRenderer(node)
        is MarkdownNode.InlineCode -> InlineCodeRenderer(node)
        is MarkdownNode.Link -> LinkRenderer(node)
        is MarkdownNode.BlockQuote -> BlockQuoteRenderer(node)
        is MarkdownNode.BulletList -> BulletListRenderer(node)
        is MarkdownNode.OrderedList -> OrderedListRenderer(node)
        is MarkdownNode.Bold -> BoldRenderer(node)
        is MarkdownNode.Italic -> ItalicRenderer(node)
        is MarkdownNode.Text -> TextRenderer(node)
        is MarkdownNode.HorizontalRule -> HorizontalRuleRenderer()
        is MarkdownNode.LineBreak -> Spacer(modifier = Modifier.height(4.dp))
    }
}

@Composable
private fun ParagraphRenderer(node: MarkdownNode.Paragraph) {
    Text(
        text = renderInlineNodes(node.children),
        fontSize = 14.sp,
        lineHeight = 20.sp
    )
}

@Composable
private fun HeadingRenderer(node: MarkdownNode.Heading) {
    val fontSize = when (node.level) {
        1 -> 24.sp
        2 -> 20.sp
        3 -> 18.sp
        4 -> 16.sp
        5 -> 14.sp
        else -> 13.sp
    }
    Text(
        text = node.text,
        fontSize = fontSize,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
    )
}

/**
 * 代码块渲染器
 * 对标 webchat: 圆角 + 头部(语言标签+复制按钮) + 代码内容
 */
@Composable
private fun CodeBlockRenderer(node: MarkdownNode.CodeBlock) {
    val clipboardManager = LocalClipboardManager.current
    var showCopied by remember { mutableStateOf(false) }
    
    // 判断是否是 JSON（需要折叠）
    val isJson = node.language == "json" || 
        (node.language == null && 
            (node.code.trim().startsWith("{") || node.code.trim().startsWith("[")))
    
    val lineCount = node.code.lines().size
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(6.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1E1E1E) // 深色背景
        )
    ) {
        Column {
            // 头部：语言标签 + 复制按钮
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0x40000000))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 语言标签
                val langLabel = if (isJson && lineCount > 1) {
                    "JSON · $lineCount lines"
                } else {
                    node.language?.uppercase() ?: "CODE"
                }
                Text(
                    text = langLabel,
                    fontSize = 11.sp,
                    color = Color(0xFF888888),
                    fontFamily = FontFamily.Monospace
                )
                
                // 复制按钮
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable {
                            clipboardManager.setText(AnnotatedString(node.code))
                            showCopied = true
                        }
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    if (showCopied) {
                        Text(
                            text = "Copied!",
                            fontSize = 11.sp,
                            color = Color(0xFF22C55E)
                        )
                        LaunchedEffect(showCopied) {
                            kotlinx.coroutines.delay(1500)
                            showCopied = false
                        }
                    } else {
                        Text(
                            text = "Copy",
                            fontSize = 11.sp,
                            color = Color(0xFF888888)
                        )
                    }
                }
            }
            
            // 代码内容
            SelectionContainer {
                Text(
                    text = node.code,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    color = Color(0xFFE0E0E0),
                    lineHeight = 18.sp
                )
            }
        }
    }
}

/**
 * 行内代码渲染器
 * 对标 webchat: 半透明背景 + 圆角
 */
@Composable
private fun InlineCodeRenderer(node: MarkdownNode.InlineCode) {
    Text(
        text = node.code,
        modifier = Modifier
            .background(
                Color(0x25000000),
                RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 4.dp, vertical = 1.dp),
        fontFamily = FontFamily.Monospace,
        fontSize = 13.sp
    )
}

/**
 * 链接渲染器
 * 对标 webchat: 下划线 + accent 颜色
 */
@Composable
private fun LinkRenderer(node: MarkdownNode.Link) {
    // 简化处理：只显示文本
    Text(
        text = node.text,
        color = MaterialTheme.colorScheme.primary,
        textDecoration = TextDecoration.Underline,
        fontSize = 14.sp
    )
}

/**
 * 引用块渲染器
 * 对标 webchat: 左边框 + 半透明背景
 */
@Composable
private fun BlockQuoteRenderer(node: MarkdownNode.BlockQuote) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Color(0x05000000),
                RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp)
            )
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.outline)
        )
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            node.children.forEach { child ->
                MarkdownNodeRenderer(child)
            }
        }
    }
}

@Composable
private fun BulletListRenderer(node: MarkdownNode.BulletList) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        node.items.forEach { item ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = "•",
                    modifier = Modifier.padding(end = 8.dp),
                    fontSize = 14.sp
                )
                Column(modifier = Modifier.weight(1f)) {
                    when (item) {
                        is MarkdownNode.ListItem -> item.children.forEach { child ->
                            MarkdownNodeRenderer(child)
                        }
                        else -> MarkdownNodeRenderer(item)
                    }
                }
            }
        }
    }
}

@Composable
private fun OrderedListRenderer(node: MarkdownNode.OrderedList) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        node.items.forEachIndexed { index, item ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = "${node.start + index}.",
                    modifier = Modifier.padding(end = 8.dp),
                    fontSize = 14.sp
                )
                Column(modifier = Modifier.weight(1f)) {
                    when (item) {
                        is MarkdownNode.ListItem -> item.children.forEach { child ->
                            MarkdownNodeRenderer(child)
                        }
                        else -> MarkdownNodeRenderer(item)
                    }
                }
            }
        }
    }
}

@Composable
private fun BoldRenderer(node: MarkdownNode.Bold) {
    Text(
        text = node.text,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp
    )
}

@Composable
private fun ItalicRenderer(node: MarkdownNode.Italic) {
    Text(
        text = node.text,
        fontStyle = FontStyle.Italic,
        fontSize = 14.sp
    )
}

@Composable
private fun TextRenderer(node: MarkdownNode.Text) {
    Text(
        text = node.text,
        fontSize = 14.sp,
        lineHeight = 20.sp
    )
}

@Composable
private fun HorizontalRuleRenderer() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Color(0xFF404040))
            .padding(vertical = 8.dp)
    )
}

/**
 * 渲染行内节点为 AnnotatedString
 */
@Composable
private fun renderInlineNodes(nodes: List<MarkdownNode>): AnnotatedString {
    return buildAnnotatedString {
        nodes.forEach { node ->
            when (node) {
                is MarkdownNode.Text -> append(node.text)
                is MarkdownNode.Bold -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(node.text)
                }
                is MarkdownNode.Italic -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    append(node.text)
                }
                is MarkdownNode.InlineCode -> {
                    append(node.code)
                }
                is MarkdownNode.Link -> {
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) {
                        append(node.text)
                    }
                }
                else -> append("")
            }
        }
    }
}