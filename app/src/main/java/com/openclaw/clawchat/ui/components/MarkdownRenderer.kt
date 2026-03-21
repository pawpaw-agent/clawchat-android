package com.openclaw.clawchat.ui.components

import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.platform.LocalContext
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
 * 
 * 使用 Compose 原生组件渲染 Markdown AST
 * 对标 webchat 样式
 */
@Composable
fun MarkdownRenderer(
    nodes: List<MarkdownNode>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        nodes.forEach { node ->
            RenderNode(node)
        }
    }
}

@Composable
private fun RenderNode(node: MarkdownNode) {
    when (node) {
        is MarkdownNode.Paragraph -> ParagraphRenderer(node)
        is MarkdownNode.Heading -> HeadingRenderer(node)
        is MarkdownNode.CodeBlock -> CodeBlockRenderer(node)
        is MarkdownNode.BlockQuote -> BlockQuoteRenderer(node)
        is MarkdownNode.UnorderedList -> UnorderedListRenderer(node)
        is MarkdownNode.OrderedList -> OrderedListRenderer(node)
        is MarkdownNode.HorizontalRule -> HorizontalRuleRenderer()
        is MarkdownNode.Text -> Text(text = node.text, fontSize = 14.sp, lineHeight = 20.sp)
        else -> {}
    }
}

/**
 * 段落渲染
 */
@Composable
private fun ParagraphRenderer(node: MarkdownNode.Paragraph) {
    if (node.children.isEmpty()) return
    
    val annotatedString = buildAnnotatedString {
        node.children.forEach { child ->
            when (child) {
                is MarkdownNode.Text -> append(child.text)
                is MarkdownNode.Bold -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(child.text)
                }
                is MarkdownNode.Italic -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    append(child.text)
                }
                is MarkdownNode.InlineCode -> withStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp
                    )
                ) {
                    append(child.code)
                }
                is MarkdownNode.Link -> {
                    pushStringAnnotation(tag = "URL", annotation = child.url)
                    withStyle(
                        SpanStyle(
                            color = MaterialTheme.colorScheme.primary,
                            textDecoration = TextDecoration.Underline
                        )
                    ) {
                        append(child.text)
                    }
                    pop()
                }
                else -> {}
            }
        }
    }
    
    Text(
        text = annotatedString,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        modifier = Modifier.fillMaxWidth()
    )
}

/**
 * 标题渲染
 */
@Composable
private fun HeadingRenderer(node: MarkdownNode.Heading) {
    val fontSize = when (node.level) {
        1 -> 24.sp
        2 -> 22.sp
        3 -> 20.sp
        4 -> 18.sp
        5 -> 16.sp
        else -> 14.sp
    }
    
    Text(
        text = node.text,
        fontSize = fontSize,
        fontWeight = FontWeight.Bold,
        lineHeight = fontSize * 1.3f,
        modifier = Modifier.fillMaxWidth()
    )
}

/**
 * 代码块渲染（对标 webchat code-block-wrapper）
 */
@Composable
private fun CodeBlockRenderer(node: MarkdownNode.CodeBlock) {
    val clipboardManager = LocalClipboardManager.current
    var isCopied by remember { mutableStateOf(false) }
    
    // 检测 JSON（webchat 会折叠大型 JSON）
    val isJson = remember(node.code, node.language) {
        val trimmed = node.code.trim()
        node.language?.lowercase() == "json" ||
            (trimmed.startsWith("{") && trimmed.endsWith("}")) ||
            (trimmed.startsWith("[") && trimmed.endsWith("]"))
    }
    
    // JSON 折叠状态（> 5 行自动折叠）
    var isExpanded by remember { 
        mutableStateOf(!isJson || node.code.lines().size <= 5) 
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0x25000000) // rgba(0,0,0,0.15)
        ),
        shape = RoundedCornerShape(6.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 头部：语言标签 + 复制按钮
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0x40000000), // rgba(0,0,0,0.25)
                shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 语言标签
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = node.language?.uppercase() ?: "CODE",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = FontFamily.Monospace
                        )
                        
                        val lineCount = node.code.lines().size
                        if (lineCount > 1) {
                            Text(
                                text = "· $lineCount lines",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                    
                    // 操作按钮
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // JSON 展开/折叠
                        if (isJson && node.code.lines().size > 5) {
                            TextButton(
                                onClick = { isExpanded = !isExpanded },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (isExpanded) "Collapse" else "Expand",
                                    fontSize = 11.sp
                                )
                            }
                        }
                        
                        // 复制按钮
                        TextButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(node.code))
                                isCopied = true
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (isCopied) "Copied!" else "Copy",
                                fontSize = 11.sp,
                                color = if (isCopied) MaterialTheme.colorScheme.primary 
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            
            // 代码内容
            if (isExpanded || !isJson) {
                SelectionContainer {
                    Text(
                        text = node.code,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
    
    // 自动重置复制状态
    LaunchedEffect(isCopied) {
        if (isCopied) {
            kotlinx.coroutines.delay(2000)
            isCopied = false
        }
    }
}

/**
 * 引用块渲染
 */
@Composable
private fun BlockQuoteRenderer(node: MarkdownNode.BlockQuote) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0x05000000))
    ) {
        // 左边框
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(IntrinsicSize.Max)
                .background(MaterialTheme.colorScheme.outline)
        )
        
        // 引用内容
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(12.dp)
        ) {
            node.children.forEach { child ->
                RenderNode(child)
            }
        }
    }
}

/**
 * 无序列表渲染
 */
@Composable
private fun UnorderedListRenderer(node: MarkdownNode.UnorderedList) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        node.items.forEach { item ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "•",
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    if (item is MarkdownNode.ListItem) {
                        item.children.forEach { child -> RenderNode(child) }
                    }
                }
            }
        }
    }
}

/**
 * 有序列表渲染
 */
@Composable
private fun OrderedListRenderer(node: MarkdownNode.OrderedList) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        node.items.forEachIndexed { index, item ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "${node.start + index}.",
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    if (item is MarkdownNode.ListItem) {
                        item.children.forEach { child -> RenderNode(child) }
                    }
                }
            }
        }
    }
}

/**
 * 水平线渲染
 */
@Composable
private fun HorizontalRuleRenderer() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    )
}