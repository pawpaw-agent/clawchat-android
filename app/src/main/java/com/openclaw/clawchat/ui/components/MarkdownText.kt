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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.compose.elements.MarkdownCodeBackground
import com.mikepenz.markdown.compose.elements.MarkdownDivider
import com.mikepenz.markdown.compose.elements.material.MarkdownBasicText
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.model.MarkdownColors
import com.mikepenz.markdown.model.MarkdownDimens
import com.mikepenz.markdown.model.MarkdownPadding
import com.mikepenz.markdown.model.markdownColor
import com.mikepenz.markdown.model.markdownComponents
import dev.snipme.highlights.Highlights
import dev.snipme.highlights.model.BoldHighlight
import dev.snipme.highlights.model.ColorHighlight
import dev.snipme.highlights.model.SyntaxLanguage
import dev.snipme.highlights.model.SyntaxThemes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.findChildOfType
import org.intellij.markdown.ast.getTextInNode

/**
 * Markdown 渲染组件
 * 使用 multiplatform-markdown-renderer 库
 *
 * 特性：
 * - GFM 支持（代码块、表格、列表等）
 * - Material 3 样式
 * - 代码块语法高亮
 * - 代码块语言标签 + 复制按钮（通过 showHeader）
 * - JSON 自动折叠（> 5 行）
 */
@Composable
fun MarkdownText(
    content: String,
    modifier: Modifier = Modifier
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
            .theme(SyntaxThemes.atom(darkMode = isDarkTheme))
    }

    // 存储折叠状态
    val collapseStates = remember { mutableStateMapOf<String, Boolean>() }

    // 自定义颜色配置
    val colors = markdownColor(
        text = MaterialTheme.colorScheme.onSurface,
        codeBackground = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        dividerColor = MaterialTheme.colorScheme.outlineVariant
    )

    Markdown(
        content = truncatedContent,
        modifier = modifier.fillMaxWidth(),
        colors = colors,
        components = markdownComponents(
            codeBlock = { component ->
                JsonAwareCodeBlock(
                    content = component.content,
                    node = component.node,
                    style = component.typography.code,
                    highlightsBuilder = highlightsBuilder,
                    collapseStates = collapseStates,
                    colors = component.colors,
                    dimens = component.dimens,
                    padding = component.padding
                )
            },
            codeFence = { component ->
                JsonAwareCodeFence(
                    content = component.content,
                    node = component.node,
                    style = component.typography.code,
                    highlightsBuilder = highlightsBuilder,
                    collapseStates = collapseStates,
                    colors = component.colors,
                    dimens = component.dimens,
                    padding = component.padding
                )
            }
        )
    )
}

/**
 * 带JSON折叠功能的代码块组件
 */
@Composable
private fun JsonAwareCodeBlock(
    content: String,
    node: ASTNode,
    style: TextStyle,
    highlightsBuilder: Highlights.Builder,
    collapseStates: MutableMap<String, Boolean>,
    colors: MarkdownColors,
    dimens: MarkdownDimens,
    padding: MarkdownPadding
) {
    val codeInfo = remember(content, node) {
        extractCodeInfo(content, node)
    }

    if (codeInfo.isJson && codeInfo.lineCount > 5) {
        JsonCollapsibleCodeBlock(
            code = codeInfo.code,
            language = codeInfo.language,
            lineCount = codeInfo.lineCount,
            style = style,
            highlightsBuilder = highlightsBuilder,
            collapseKey = codeInfo.hashCode().toString(),
            collapseStates = collapseStates,
            colors = colors,
            dimens = dimens,
            padding = padding
        )
    } else {
        HighlightedCodeContent(
            code = codeInfo.code,
            language = codeInfo.language,
            style = style,
            highlightsBuilder = highlightsBuilder,
            colors = colors,
            dimens = dimens,
            padding = padding,
            showHeader = true
        )
    }
}

/**
 * 带JSON折叠功能的代码围栏组件
 */
@Composable
private fun JsonAwareCodeFence(
    content: String,
    node: ASTNode,
    style: TextStyle,
    highlightsBuilder: Highlights.Builder,
    collapseStates: MutableMap<String, Boolean>,
    colors: MarkdownColors,
    dimens: MarkdownDimens,
    padding: MarkdownPadding
) {
    val codeInfo = remember(content, node) {
        extractCodeInfo(content, node)
    }

    if (codeInfo.isJson && codeInfo.lineCount > 5) {
        JsonCollapsibleCodeBlock(
            code = codeInfo.code,
            language = codeInfo.language,
            lineCount = codeInfo.lineCount,
            style = style,
            highlightsBuilder = highlightsBuilder,
            collapseKey = codeInfo.hashCode().toString(),
            collapseStates = collapseStates,
            colors = colors,
            dimens = dimens,
            padding = padding
        )
    } else {
        HighlightedCodeContent(
            code = codeInfo.code,
            language = codeInfo.language,
            style = style,
            highlightsBuilder = highlightsBuilder,
            colors = colors,
            dimens = dimens,
            padding = padding,
            showHeader = true
        )
    }
}

/**
 * JSON 可折叠代码块
 */
@Composable
private fun JsonCollapsibleCodeBlock(
    code: String,
    language: String?,
    lineCount: Int,
    style: TextStyle,
    highlightsBuilder: Highlights.Builder,
    collapseKey: String,
    collapseStates: MutableMap<String, Boolean>,
    colors: MarkdownColors,
    dimens: MarkdownDimens,
    padding: MarkdownPadding
) {
    var isExpanded by remember { mutableStateOf(collapseStates[collapseKey] ?: false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(dimens.codeBackgroundCornerSize))
            .background(colors.codeBackground)
    ) {
        Column {
            // 折叠头部
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        isExpanded = !isExpanded
                        collapseStates[collapseKey] = isExpanded
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "JSON · $lineCount lines",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.text,
                    fontFamily = FontFamily.Monospace
                )
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = colors.text,
                    modifier = Modifier.size(20.dp)
                )
            }

            MarkdownDivider(
                color = colors.dividerColor.copy(alpha = 0.3f),
                thickness = 0.5.dp
            )

            // 代码内容
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                HighlightedCodeContent(
                    code = code,
                    language = "json",
                    style = style,
                    highlightsBuilder = highlightsBuilder,
                    colors = colors,
                    dimens = dimens,
                    padding = padding,
                    showHeader = false
                )
            }
        }
    }
}

/**
 * 高亮代码内容组件
 */
@Composable
private fun HighlightedCodeContent(
    code: String,
    language: String?,
    style: TextStyle,
    highlightsBuilder: Highlights.Builder,
    colors: MarkdownColors,
    dimens: MarkdownDimens,
    padding: MarkdownPadding,
    showHeader: Boolean
) {
    val codeHighlights: AnnotatedString by produceHighlightsState(
        code = code,
        language = language,
        highlightsBuilder = highlightsBuilder,
        immediate = LocalInspectionMode.current
    )

    MarkdownCodeBackground(
        color = colors.codeBackground,
        shape = RoundedCornerShape(dimens.codeBackgroundCornerSize),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        showHeader = showHeader,
        language = language,
        code = code
    ) {
        MarkdownBasicText(
            text = codeHighlights,
            style = style,
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(padding.codeBlock)
        )
    }
}

/**
 * 从 AST 节点提取代码信息
 */
private data class CodeInfo(
    val code: String,
    val language: String?,
    val isJson: Boolean,
    val lineCount: Int
)

private fun extractCodeInfo(content: String, node: ASTNode): CodeInfo {
    val language = node.findChildOfType(MarkdownTokenTypes.FENCE_LANG)?.getTextInNode(content)?.toString()

    val code = if (node.children.size >= 3) {
        val start = node.children[2].startOffset
        val minCodeFenceCount = if (language != null && node.children.size > 3) 3 else 2
        val end = node.children[(node.children.size - 2).coerceAtLeast(minCodeFenceCount)].endOffset
        content.subSequence(start, end).toString().replaceIndent()
    } else {
        ""
    }

    val trimmedCode = code.trim()
    val isJson = language?.equals("json", ignoreCase = true) == true ||
        (language.isNullOrEmpty() &&
         (trimmedCode.startsWith("{") && trimmedCode.endsWith("}") ||
          trimmedCode.startsWith("[") && trimmedCode.endsWith("]")))

    val lineCount = code.lines().size

    return CodeInfo(
        code = code,
        language = language,
        isJson = isJson,
        lineCount = lineCount
    )
}

/**
 * 生成高亮状态的 Composable
 */
@Composable
private fun produceHighlightsState(
    code: String,
    language: String?,
    highlightsBuilder: Highlights.Builder,
    immediate: Boolean
): State<AnnotatedString> {
    if (immediate) {
        val highlighted = remember(code) {
            buildHighlightedAnnotatedString(code, language, highlightsBuilder)
        }
        return rememberUpdatedState(highlighted)
    }

    return produceState(
        initialValue = AnnotatedString(text = code),
        key1 = code
    ) {
        val job = launch(Dispatchers.Default) {
            value = buildHighlightedAnnotatedString(code, language, highlightsBuilder)
        }
        awaitDispose {
            job.cancel()
        }
    }
}

/**
 * 构建高亮的 AnnotatedString
 */
private fun buildHighlightedAnnotatedString(
    code: String,
    language: String?,
    highlightsBuilder: Highlights.Builder
): AnnotatedString {
    val syntaxLanguage = language?.let { SyntaxLanguage.getByName(it) }
    val codeHighlights = highlightsBuilder
        .code(code)
        .let { if (syntaxLanguage != null) it.language(syntaxLanguage) else it }
        .build()
        .getHighlights()

    return buildAnnotatedString {
        append(code)
        codeHighlights.forEach {
            val spanStyle = when (it) {
                is ColorHighlight -> SpanStyle(color = Color(it.rgb).copy(alpha = 1f))
                is BoldHighlight -> SpanStyle(fontWeight = FontWeight.Bold)
            }
            addStyle(
                style = spanStyle,
                start = it.location.start,
                end = it.location.end
            )
        }
    }
}