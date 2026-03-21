package com.openclaw.clawchat.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.jeziellago.compose.markdowntext.MarkdownText

/**
 * Markdown 渲染组件
 * 使用 compose-markdown 库
 * 
 * 特性：
 * - GFM 支持（表格、列表、标题等）
 * - 代码块
 * - 纯 Compose 实现，性能好
 */
@Composable
fun MarkdownText(
    content: String,
    modifier: Modifier = Modifier
) {
    MarkdownText(
        markdown = content,
        modifier = modifier.fillMaxWidth()
    )
}