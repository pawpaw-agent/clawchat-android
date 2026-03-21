package com.openclaw.clawchat.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * Markdown 渲染组件
 * 
 * 使用自定义解析器和渲染器实现与 webchat 一致的效果
 * 
 * 特性：
 * - 代码块：语言标签 + 复制按钮 + JSON 折叠
 * - 行内代码：背景色 + 等宽字体
 * - 链接：下划线 + 可点击
 * - 引用块：左边框
 * - 列表：有序/无序
 * - 标题：h1-h6
 * 
 * @param content Markdown 内容
 * @param modifier 修饰符
 * @param color 文本颜色（可选）
 */
@Composable
fun MarkdownText(
    content: String,
    modifier: Modifier = Modifier,
    color: Color? = null
) {
    // 解析 Markdown 为 AST
    val nodes = remember(content) { MarkdownParser.parse(content) }
    
    // 渲染 AST
    MarkdownRenderer(
        nodes = nodes,
        modifier = modifier.fillMaxWidth()
    )
}