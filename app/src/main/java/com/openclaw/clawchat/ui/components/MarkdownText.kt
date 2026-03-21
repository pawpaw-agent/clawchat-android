package com.openclaw.clawchat.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Markdown 渲染组件
 */
@Composable
fun MarkdownText(
    content: String,
    modifier: Modifier = Modifier
) {
    // 长文本限制：超过 10000 字符截断
    val truncatedContent = remember(content) {
        if (content.length > 10000) {
            content.take(10000) + "\n\n... (内容过长，已截断)"
        } else {
            content
        }
    }
    
    // 安全解析
    val nodes = remember(truncatedContent) {
        try {
            parseMarkdown(truncatedContent)
        } catch (e: Exception) {
            // 解析失败，返回纯文本节点
            listOf(MarkdownNode.Text(truncatedContent))
        }
    }
    
    // 限制渲染节点数量
    val limitedNodes = remember(nodes) {
        if (nodes.size > 100) nodes.take(100) else nodes
    }
    
    if (limitedNodes.isEmpty()) {
        Text(text = truncatedContent, fontSize = 14.sp, modifier = modifier)
    } else {
        MarkdownRenderer(nodes = limitedNodes, modifier = modifier.fillMaxWidth())
    }
}