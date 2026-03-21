package com.openclaw.clawchat.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.halilibo.richtext.commonmark.CommonmarkAstNodeParser
import com.halilibo.richtext.commonmark.MarkdownParseOptions
import com.halilibo.richtext.markdown.BasicMarkdown
import com.halilibo.richtext.ui.RichTextStyle
import com.halilibo.richtext.ui.material3.RichText

/**
 * 原生 Markdown 渲染组件
 * 使用 richtext-commonmark 实现高性能 Markdown 渲染
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
    val parser = remember { CommonmarkAstNodeParser(MarkdownParseOptions.Default) }
    val astNode = remember(content) { parser.parse(content) }
    
    RichText(
        modifier = modifier.fillMaxWidth(),
        style = RichTextStyle()
    ) {
        BasicMarkdown(astNode)
    }
}