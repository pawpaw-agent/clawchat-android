package com.openclaw.clawchat.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.markdown.compose.LocalMarkdownColors
import com.mikepenz.markdown.compose.LocalMarkdownDimens
import com.mikepenz.markdown.compose.LocalMarkdownPadding
import com.mikepenz.markdown.compose.LocalMarkdownTypography
import com.mikepenz.markdown.compose.elements.MarkdownCodeBackground
import com.mikepenz.markdown.compose.elements.MarkdownCodeBlock
import com.mikepenz.markdown.compose.elements.MarkdownCodeFence
import com.mikepenz.markdown.compose.elements.material.MarkdownBasicText
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.model.MarkdownTypography
import dev.snipme.highlights.Highlights
import dev.snipme.highlights.model.BoldHighlight
import dev.snipme.highlights.model.ColorHighlight
import dev.snipme.highlights.model.SyntaxLanguage

/**
 * Markdown 渲染组件
 * 使用 multiplatform-markdown-renderer 库
 *
 * 特性：
 * - GFM 支持（代码块、表格、列表等）
 * - Material 3 样式
 * - 代码块语法高亮（Highlights 库）
 * - 代码块语言标签 + 复制按钮
 * - JSON 自动折叠（> 5 行）
 */
@Composable
fun MarkdownText(
    content: String,
    modifier: Modifier = Modifier,
    fontSize: androidx.compose.ui.unit.TextUnit = 16.sp
) {
    // 长文本限制
    val truncatedContent = if (content.length > 50000) {
        content.take(50000) + "\n\n... (内容过长，已截断)"
    } else {
        content
    }

    // 检测深色模式
    val isDarkTheme = isSystemInDarkTheme()

    // 创建语法高亮构建器
    val highlightsBuilder = remember(isDarkTheme) {
        Highlights.Builder()
    }

    // 自定义颜色配置
    val colors = markdownColor(
        text = MaterialTheme.colorScheme.onSurface,
        codeText = MaterialTheme.colorScheme.onSurface,
        codeBackground = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        dividerColor = MaterialTheme.colorScheme.outlineVariant
    )

    // 使用 LocalMarkdownTypography 设置字体大小
    val customTypography = remember(fontSize) {
        DefaultMarkdownTypography(fontSize)
    }

    CompositionLocalProvider(LocalMarkdownTypography provides customTypography) {
        Markdown(
            content = truncatedContent,
            modifier = modifier.fillMaxWidth(),
            colors = colors,
            components = markdownComponents(
                codeFence = { model ->
                    CustomCodeFence(
                        content = model.content,
                        node = model.node,
                        highlightsBuilder = highlightsBuilder
                    )
                },
                codeBlock = { model ->
                    CustomCodeBlock(
                        content = model.content,
                        node = model.node,
                        highlightsBuilder = highlightsBuilder
                    )
                }
            )
        )
    }
}

/**
 * 自定义代码围栏组件
 */
@Composable
private fun CustomCodeFence(
    content: String,
    node: org.intellij.markdown.ast.ASTNode,
    highlightsBuilder: Highlights.Builder
) {
    MarkdownCodeFence(content, node) { code, language ->
        CustomCodeContent(
            code = code,
            language = language,
            highlightsBuilder = highlightsBuilder
        )
    }
}

/**
 * 自定义代码块组件
 */
@Composable
private fun CustomCodeBlock(
    content: String,
    node: org.intellij.markdown.ast.ASTNode,
    highlightsBuilder: Highlights.Builder
) {
    MarkdownCodeBlock(content, node) { code, language ->
        CustomCodeContent(
            code = code,
            language = language,
            highlightsBuilder = highlightsBuilder
        )
    }
}

/**
 * 自定义代码内容渲染
 * - 检测 JSON 并折叠
 * - 显示语言标签 + 复制按钮
 * - 语法高亮
 */
@Composable
private fun CustomCodeContent(
    code: String,
    language: String?,
    highlightsBuilder: Highlights.Builder
) {
    val trimmedCode = code.trim()
    val isJson = language?.equals("json", ignoreCase = true) == true ||
        (language == null &&
         (trimmedCode.startsWith("{") && trimmedCode.endsWith("}") ||
          trimmedCode.startsWith("[") && trimmedCode.endsWith("]")))

    val lineCount = code.lines().size

    if (isJson && lineCount > 5) {
        JsonCollapsibleCodeBlock(
            code = code,
            highlightsBuilder = highlightsBuilder
        )
    } else {
        HighlightedCodeBlock(
            code = code,
            language = language,
            highlightsBuilder = highlightsBuilder
        )
    }
}

/**
 * JSON 可折叠代码块（> 5 行自动折叠）
 */
@Composable
private fun JsonCollapsibleCodeBlock(
    code: String,
    highlightsBuilder: Highlights.Builder
) {
    val lineCount = code.lines().size
    var isExpanded by remember { mutableStateOf(false) }

    val backgroundCodeColor = LocalMarkdownColors.current.codeBackground
    val codeBackgroundCornerSize = LocalMarkdownDimens.current.codeBackgroundCornerSize
    val textColor = LocalMarkdownColors.current.text
    val dividerColor = LocalMarkdownColors.current.dividerColor

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(codeBackgroundCornerSize))
            .background(backgroundCodeColor)
    ) {
        // 折叠头部
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "JSON · $lineCount lines",
                style = MaterialTheme.typography.labelMedium,
                color = textColor,
                fontFamily = FontFamily.Monospace
            )
            Icon(
                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (isExpanded) "Collapse" else "Expand",
                tint = textColor,
                modifier = Modifier.size(20.dp)
            )
        }

        // 分隔线
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(dividerColor.copy(alpha = 0.3f))
        )

        // 代码内容（折叠时隐藏）
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            HighlightedCodeContent(
                code = code,
                language = "json",
                highlightsBuilder = highlightsBuilder,
                showHeader = false
            )
        }
    }
}

/**
 * 带语言标签和复制按钮的高亮代码块
 */
@Composable
private fun HighlightedCodeBlock(
    code: String,
    language: String?,
    highlightsBuilder: Highlights.Builder
) {
    HighlightedCodeContent(
        code = code,
        language = language,
        highlightsBuilder = highlightsBuilder,
        showHeader = true
    )
}

/**
 * 高亮代码内容
 */
@Composable
private fun HighlightedCodeContent(
    code: String,
    language: String?,
    highlightsBuilder: Highlights.Builder,
    fontSize: androidx.compose.ui.unit.TextUnit = 14.sp,
    showHeader: Boolean = true
) {
    val backgroundCodeColor = LocalMarkdownColors.current.codeBackground
    val codeBackgroundCornerSize = LocalMarkdownDimens.current.codeBackgroundCornerSize
    val codeBlockPadding = LocalMarkdownPadding.current.codeBlock
    val codeTextColor = LocalMarkdownColors.current.codeText
    val textColor = LocalMarkdownColors.current.text
    val dividerColor = LocalMarkdownColors.current.dividerColor
    // 使用传入的 fontSize，不访问 LocalMarkdownTypography
    val style = TextStyle(fontSize = fontSize * 0.9f, fontFamily = FontFamily.Monospace)

    val clipboardManager = LocalClipboardManager.current

    // 语法高亮
    val syntaxLanguage = remember(language) { language?.let { SyntaxLanguage.getByName(it) } }
    val codeHighlights by remember(code, syntaxLanguage) {
        derivedStateOf {
            highlightsBuilder
                .code(code)
                .let { if (syntaxLanguage != null) it.language(syntaxLanguage) else it }
                .build()
        }
    }

    val annotatedString = buildAnnotatedString {
        append(codeHighlights.getCode())
        codeHighlights.getHighlights()
            .filterIsInstance<ColorHighlight>()
            .forEach {
                addStyle(
                    SpanStyle(color = Color(it.rgb).copy(alpha = 1f)),
                    start = it.location.start,
                    end = it.location.end
                )
            }
        codeHighlights.getHighlights()
            .filterIsInstance<BoldHighlight>()
            .forEach {
                addStyle(
                    SpanStyle(fontWeight = FontWeight.Bold),
                    start = it.location.start,
                    end = it.location.end
                )
            }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(codeBackgroundCornerSize))
            .background(backgroundCodeColor)
    ) {
        // 头部：语言标签 + 复制按钮
        if (showHeader) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 语言标签
                if (!language.isNullOrEmpty()) {
                    Text(
                        text = language.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .background(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                } else {
                    Box(modifier = Modifier.size(1.dp))
                }

                // 复制按钮
                IconButton(
                    onClick = { clipboardManager.setText(AnnotatedString(code)) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy code",
                        tint = textColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // 分隔线
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(dividerColor.copy(alpha = 0.3f))
            )
        }

        // 代码内容
        MarkdownBasicText(
            text = annotatedString,
            color = codeTextColor,
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(codeBlockPadding),
            style = style
        )
    }
}

/**
 * 默认 MarkdownTypography 实现，支持自定义字体大小
 */
private data class DefaultMarkdownTypography(
    private val fontSize: androidx.compose.ui.unit.TextUnit
) : MarkdownTypography {
    override val text = TextStyle(fontSize = fontSize, fontFamily = FontFamily.Default)
    override val h1 = TextStyle(fontSize = fontSize * 1.5f, fontWeight = FontWeight.Bold)
    override val h2 = TextStyle(fontSize = fontSize * 1.3f, fontWeight = FontWeight.Bold)
    override val h3 = TextStyle(fontSize = fontSize * 1.1f, fontWeight = FontWeight.Bold)
    override val h4 = TextStyle(fontSize = fontSize * 1.0f, fontWeight = FontWeight.Bold)
    override val h5 = TextStyle(fontSize = fontSize * 0.9f, fontWeight = FontWeight.Bold)
    override val h6 = TextStyle(fontSize = fontSize * 0.8f, fontWeight = FontWeight.Bold)
    override val code = TextStyle(fontSize = fontSize * 0.9f, fontFamily = FontFamily.Monospace)
    override val inlineCode = TextStyle(fontSize = fontSize * 0.9f, fontFamily = FontFamily.Monospace)
    override val bullet = TextStyle(fontSize = fontSize)
    override val ordered = TextStyle(fontSize = fontSize)
    override val list = TextStyle(fontSize = fontSize)
    override val quote = TextStyle(fontSize = fontSize)
    override val paragraph = TextStyle(fontSize = fontSize)
    override val link = TextStyle(fontSize = fontSize)
}