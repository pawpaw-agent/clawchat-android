package com.openclaw.clawchat.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

/**
 * Markdown 渲染组件
 */
@Composable
fun MarkdownText(
    content: String,
    modifier: Modifier = Modifier
) {
    val nodes = remember(content) { parseMarkdown(content) }
    MarkdownRenderer(nodes = nodes, modifier = modifier.fillMaxWidth())
}