package com.openclaw.clawchat.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
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
        nodes.forEach { node -> RenderNode(node) }
    }
}

@Composable
private fun RenderNode(node: MarkdownNode) {
    when (node) {
        is MarkdownNode.Paragraph -> ParagraphRenderer(node)
        is MarkdownNode.Heading -> HeadingRenderer(node)
        is MarkdownNode.CodeBlock -> CodeBlockRenderer(node)
        is MarkdownNode.BlockQuote -> BlockQuoteRenderer(node)
        is MarkdownNode.BulletList -> BulletListRenderer(node)
        is MarkdownNode.OrderedList -> OrderedListRenderer(node)
        is MarkdownNode.HorizontalRule -> HorizontalRuleRenderer()
        is MarkdownNode.LineBreak -> Spacer(modifier = Modifier.height(4.dp))
        is MarkdownNode.SoftLineBreak -> Spacer(modifier = Modifier.height(2.dp))
        is MarkdownNode.Text -> Text(text = node.text, fontSize = 14.sp, lineHeight = 20.sp)
        is MarkdownNode.Bold -> Text(text = node.text, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        is MarkdownNode.Italic -> Text(text = node.text, fontStyle = FontStyle.Italic, fontSize = 14.sp)
        is MarkdownNode.InlineCode -> InlineCodeRenderer(node)
        is MarkdownNode.Link -> LinkRenderer(node)
        is MarkdownNode.Strikethrough -> Text(text = node.text, textDecoration = TextDecoration.LineThrough, fontSize = 14.sp)
        is MarkdownNode.Image -> Text(text = "[${node.alt}]", fontSize = 14.sp, color = Color.Gray)
        is MarkdownNode.ListItem -> node.children.forEach { child -> RenderNode(child) }
        is MarkdownNode.Table -> TableRenderer(node)
        is MarkdownNode.TableRow -> {} // 表格行在 TableRenderer 中处理
    }
}

@Composable
private fun ParagraphRenderer(node: MarkdownNode.Paragraph) {
    if (node.children.isEmpty()) return
    val annotatedString = buildAnnotatedString {
        node.children.forEach { child ->
            when (child) {
                is MarkdownNode.Text -> append(child.text)
                is MarkdownNode.Bold -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(child.text) }
                is MarkdownNode.Italic -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(child.text) }
                is MarkdownNode.InlineCode -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp)) { append(child.code) }
                is MarkdownNode.Link -> withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline)) { append(child.text) }
                is MarkdownNode.Strikethrough -> withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { append(child.text) }
                else -> {}
            }
        }
    }
    Text(text = annotatedString, fontSize = 14.sp, lineHeight = 20.sp)
}

@Composable
private fun HeadingRenderer(node: MarkdownNode.Heading) {
    val fontSize = when (node.level) { 1 -> 24.sp; 2 -> 20.sp; 3 -> 18.sp; 4 -> 16.sp; 5 -> 14.sp; else -> 13.sp }
    Text(text = node.text, fontSize = fontSize, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
}

@Composable
private fun CodeBlockRenderer(node: MarkdownNode.CodeBlock) {
    val clipboardManager = LocalClipboardManager.current
    var showCopied by remember { mutableStateOf(false) }
    val isJson = node.language == "json" || (node.language == null && (node.code.trim().startsWith("{") || node.code.trim().startsWith("[")))
    val lineCount = node.code.lines().size
    
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(6.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))) {
        Column {
            Row(modifier = Modifier.fillMaxWidth().background(Color(0x40000000)).padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                val langLabel = if (isJson && lineCount > 1) "JSON · $lineCount lines" else node.language?.uppercase() ?: "CODE"
                Text(text = langLabel, fontSize = 11.sp, color = Color(0xFF888888), fontFamily = FontFamily.Monospace)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clip(RoundedCornerShape(4.dp)).clickable { clipboardManager.setText(AnnotatedString(node.code)); showCopied = true }.padding(horizontal = 8.dp, vertical = 2.dp)) {
                    if (showCopied) { Text(text = "Copied!", fontSize = 11.sp, color = Color(0xFF22C55E)); LaunchedEffect(showCopied) { kotlinx.coroutines.delay(1500); showCopied = false } }
                    else { Text(text = "Copy", fontSize = 11.sp, color = Color(0xFF888888)) }
                }
            }
            SelectionContainer { Text(text = node.code, modifier = Modifier.fillMaxWidth().padding(10.dp), fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = Color(0xFFE0E0E0), lineHeight = 18.sp) }
        }
    }
}

@Composable
private fun InlineCodeRenderer(node: MarkdownNode.InlineCode) {
    Text(text = node.code, modifier = Modifier.background(Color(0x25000000), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 1.dp), fontFamily = FontFamily.Monospace, fontSize = 13.sp)
}

@Composable
private fun LinkRenderer(node: MarkdownNode.Link) {
    val context = LocalContext.current
    Text(text = node.text, color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline, fontSize = 14.sp, modifier = Modifier.clickable { try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(node.url))) } catch (e: Exception) {} })
}

@Composable
private fun BlockQuoteRenderer(node: MarkdownNode.BlockQuote) {
    Row(modifier = Modifier.fillMaxWidth().background(Color(0x05000000), RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp))) {
        Box(modifier = Modifier.width(3.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outline))
        Column(modifier = Modifier.padding(12.dp)) { node.children.forEach { child -> RenderNode(child) } }
    }
}

@Composable
private fun BulletListRenderer(node: MarkdownNode.BulletList) {
    Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        node.items.forEach { item ->
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(text = "•", fontSize = 14.sp, modifier = Modifier.padding(end = 8.dp))
                Column(modifier = Modifier.weight(1f)) { when (item) { is MarkdownNode.ListItem -> item.children.forEach { child -> RenderNode(child) }; else -> RenderNode(item) } }
            }
        }
    }
}

@Composable
private fun OrderedListRenderer(node: MarkdownNode.OrderedList) {
    Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        node.items.forEachIndexed { index, item ->
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(text = "${node.start + index}.", fontSize = 14.sp, modifier = Modifier.padding(end = 8.dp))
                Column(modifier = Modifier.weight(1f)) { when (item) { is MarkdownNode.ListItem -> item.children.forEach { child -> RenderNode(child) }; else -> RenderNode(item) } }
            }
        }
    }
}

@Composable
private fun HorizontalRuleRenderer() {
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF404040)))
}

/**
 * 表格渲染器
 * 对标 webchat: 边框 + 表头背景 + 水平滚动
 */
@Composable
private fun TableRenderer(node: MarkdownNode.Table) {
    val scrollState = rememberScrollState()
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0x15000000), RoundedCornerShape(6.dp))
    ) {
        Row(
            modifier = Modifier
                .horizontalScroll(scrollState)
                .padding(1.dp)
        ) {
            Column {
                // 表头
                Row(
                    modifier = Modifier
                        .background(Color(0x25000000))
                ) {
                    node.header.cells.forEach { cell ->
                        Text(
                            text = cell,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color(0xFFE0E0E0),
                            modifier = Modifier
                                .defaultMinSize(minWidth = 80.dp)
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                        // 分隔线
                        if (cell != node.header.cells.last()) {
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(20.dp)
                                    .background(Color(0x40000000))
                                    .align(Alignment.CenterVertically)
                            )
                        }
                    }
                }
                
                // 数据行
                node.rows.forEachIndexed { index, row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (index % 2 == 0) Color.Transparent 
                                else Color(0x08000000)
                            )
                    ) {
                        row.cells.forEach { cell ->
                            Text(
                                text = cell,
                                fontSize = 13.sp,
                                color = Color(0xFFE0E0E0),
                                modifier = Modifier
                                    .defaultMinSize(minWidth = 80.dp)
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                            // 分隔线
                            if (cell != row.cells.last()) {
                                Box(
                                    modifier = Modifier
                                        .width(1.dp)
                                        .height(18.dp)
                                        .background(Color(0x30000000))
                                        .align(Alignment.CenterVertically)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}