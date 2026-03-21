package com.openclaw.clawchat.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

/**
 * Markdown 渲染组件
 * 自定义渲染器，对标 webchat 效果
 * 
 * 特性：
 * - GFM 支持（代码块、表格、列表等）
 * - 代码块：语言标签 + 复制按钮 + 圆角背景
 * - 行内代码：半透明背景 + 圆角
 * - 链接：下划线 + accent 颜色
 * - 引用块：左边框 + 半透明背景
 */
@Composable
fun MarkdownText(
    content: String,
    modifier: Modifier = Modifier
) {
    val nodes = remember(content) { parseMarkdown(content) }
    MarkdownRenderer(
        nodes = nodes,
        modifier = modifier.fillMaxWidth()
    )
}