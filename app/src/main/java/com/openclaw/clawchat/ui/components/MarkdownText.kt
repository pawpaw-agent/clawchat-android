package com.openclaw.clawchat.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mikepenz.markdown.m3.Markdown

/**
 * Markdown 渲染组件
 * 使用 multiplatform-markdown-renderer 库
 */
@Composable
fun MarkdownText(
    content: String,
    modifier: Modifier = Modifier
) {
    val truncatedContent = if (content.length > 50000) {
        content.take(50000) + "\n\n... (内容过长，已截断)"
    } else {
        content
    }
    
    Markdown(
        content = truncatedContent,
        modifier = modifier.fillMaxWidth()
    )
}
