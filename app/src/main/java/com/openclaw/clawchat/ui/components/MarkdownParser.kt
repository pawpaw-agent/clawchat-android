package com.openclaw.clawchat.ui.components

import java.util.regex.Pattern

/**
 * Markdown 节点类型
 */
sealed class MarkdownNode {
    data class Paragraph(val children: List<MarkdownNode>) : MarkdownNode()
    data class Heading(val level: Int, val text: String) : MarkdownNode()
    data class CodeBlock(val language: String?, val code: String) : MarkdownNode()
    data class InlineCode(val code: String) : MarkdownNode()
    data class Link(val text: String, val url: String) : MarkdownNode()
    data class BlockQuote(val children: List<MarkdownNode>) : MarkdownNode()
    data class BulletList(val items: List<MarkdownNode>) : MarkdownNode()
    data class OrderedList(val items: List<MarkdownNode>, val start: Int) : MarkdownNode()
    data class ListItem(val children: List<MarkdownNode>) : MarkdownNode()
    data class Bold(val text: String) : MarkdownNode()
    data class Italic(val text: String) : MarkdownNode()
    data class Text(val text: String) : MarkdownNode()
    data class Strikethrough(val text: String) : MarkdownNode()
    data class Image(val alt: String, val url: String) : MarkdownNode()
    object HorizontalRule : MarkdownNode()
    object LineBreak : MarkdownNode()
    object SoftLineBreak : MarkdownNode()
}

/**
 * 解析 Markdown 为节点树
 */
fun parseMarkdown(markdown: String): List<MarkdownNode> {
    val nodes = mutableListOf<MarkdownNode>()
    val lines = markdown.lines()
    var i = 0
    
    while (i < lines.size) {
        val line = lines[i]
        
        // 代码块
        if (line.startsWith("```")) {
            val language = line.removePrefix("```").trim()
            val codeLines = mutableListOf<String>()
            i++
            while (i < lines.size && !lines[i].startsWith("```")) {
                codeLines.add(lines[i])
                i++
            }
            i++ // skip closing ```
            nodes.add(MarkdownNode.CodeBlock(language.ifEmpty { null }, codeLines.joinToString("\n")))
            continue
        }
        
        // 标题
        val headingMatch = Regex("^(#{1,6})\\s+(.+)$").find(line)
        if (headingMatch != null) {
            val level = headingMatch.groupValues[1].length
            val text = headingMatch.groupValues[2]
            nodes.add(MarkdownNode.Heading(level, text))
            i++
            continue
        }
        
        // 水平线
        if (line.matches(Regex("^(---+|\\*\\*\\*+|___+)$"))) {
            nodes.add(MarkdownNode.HorizontalRule)
            i++
            continue
        }
        
        // 引用块
        if (line.startsWith(">")) {
            val quoteLines = mutableListOf<String>()
            while (i < lines.size && lines[i].startsWith(">")) {
                quoteLines.add(lines[i].removePrefix(">").trim())
                i++
            }
            val quoteContent = quoteLines.joinToString("\n")
            nodes.add(MarkdownNode.BlockQuote(parseInlineNodes(quoteContent)))
            continue
        }
        
        // 无序列表
        if (line.matches(Regex("^[*+-]\\s+.+$"))) {
            val items = mutableListOf<MarkdownNode>()
            while (i < lines.size && lines[i].matches(Regex("^[*+-]\\s+.+$"))) {
                val itemText = lines[i].replaceFirst(Regex("^[*+-]\\s+"), "")
                items.add(MarkdownNode.ListItem(parseInlineNodes(itemText)))
                i++
            }
            nodes.add(MarkdownNode.BulletList(items))
            continue
        }
        
        // 有序列表
        val orderedMatch = Regex("^(\\d+)\\.\\s+(.+)$").find(line)
        if (orderedMatch != null) {
            val items = mutableListOf<MarkdownNode>()
            var start = orderedMatch.groupValues[1].toInt()
            while (i < lines.size) {
                val match = Regex("^(\\d+)\\.\\s+(.+)$").find(lines[i])
                if (match != null) {
                    items.add(MarkdownNode.ListItem(parseInlineNodes(match.groupValues[2])))
                    i++
                } else {
                    break
                }
            }
            nodes.add(MarkdownNode.OrderedList(items, start))
            continue
        }
        
        // 空行
        if (line.isBlank()) {
            i++
            continue
        }
        
        // 普通段落
        val paragraphLines = mutableListOf<String>()
        while (i < lines.size && lines[i].isNotBlank() && 
               !lines[i].startsWith("```") && 
               !lines[i].startsWith("#") &&
               !lines[i].startsWith(">") &&
               !lines[i].matches(Regex("^[*+-]\\s+.+$")) &&
               !lines[i].matches(Regex("^\\d+\\.\\s+.+$")) &&
               !lines[i].matches(Regex("^(---+|\\*\\*\\*+|___+)$"))) {
            paragraphLines.add(lines[i])
            i++
        }
        if (paragraphLines.isNotEmpty()) {
            val paragraphText = paragraphLines.joinToString("\n")
            nodes.add(MarkdownNode.Paragraph(parseInlineNodes(paragraphText)))
        }
    }
    
    return nodes
}

/**
 * 解析行内元素
 */
private fun parseInlineNodes(text: String): List<MarkdownNode> {
    val nodes = mutableListOf<MarkdownNode>()
    var remaining = text
    var safetyCounter = 0
    val maxIterations = text.length + 100 // 安全限制
    
    while (remaining.isNotEmpty() && safetyCounter < maxIterations) {
        safetyCounter++
        // 行内代码
        val inlineCodeMatch = Regex("`([^`]+)`").find(remaining)
        if (inlineCodeMatch != null && inlineCodeMatch.range.first == 0) {
            nodes.add(MarkdownNode.InlineCode(inlineCodeMatch.groupValues[1]))
            remaining = remaining.removeRange(inlineCodeMatch.range)
            continue
        }
        
        // 链接
        val linkMatch = Regex("\\[([^\\]]+)\\]\\(([^)]+)\\)").find(remaining)
        if (linkMatch != null && linkMatch.range.first == 0) {
            nodes.add(MarkdownNode.Link(linkMatch.groupValues[1], linkMatch.groupValues[2]))
            remaining = remaining.removeRange(linkMatch.range)
            continue
        }
        
        // 粗体
        val boldMatch = Regex("\\*\\*([^*]+)\\*\\*").find(remaining)
        if (boldMatch != null && boldMatch.range.first == 0) {
            nodes.add(MarkdownNode.Bold(boldMatch.groupValues[1]))
            remaining = remaining.removeRange(boldMatch.range)
            continue
        }
        
        // 斜体
        val italicMatch = Regex("\\*([^*]+)\\*").find(remaining)
        if (italicMatch != null && italicMatch.range.first == 0) {
            nodes.add(MarkdownNode.Italic(italicMatch.groupValues[1]))
            remaining = remaining.removeRange(italicMatch.range)
            continue
        }
        
        // 找到下一个特殊标记
        val nextSpecial = findNextSpecialIndex(remaining)
        if (nextSpecial > 0) {
            nodes.add(MarkdownNode.Text(remaining.substring(0, nextSpecial)))
            remaining = remaining.substring(nextSpecial)
        } else if (nextSpecial == -1) {
            nodes.add(MarkdownNode.Text(remaining))
            break
        } else {
            // nextSpecial == 0，但前面的匹配都失败了
            nodes.add(MarkdownNode.Text(remaining.take(1)))
            remaining = remaining.drop(1)
        }
    }
    
    return nodes
}

private fun findNextSpecialIndex(text: String): Int {
    val patterns = listOf(
        Regex("`[^`]+`"),
        Regex("\\[[^\\]]+\\]\\([^)]+\\)"),
        Regex("\\*\\*[^*]+\\*\\*"),
        Regex("\\*[^*]+\\*")
    )
    
    var minIndex = -1
    for (pattern in patterns) {
        val match = pattern.find(text)
        if (match != null) {
            if (minIndex == -1 || match.range.first < minIndex) {
                minIndex = match.range.first
            }
        }
    }
    
    return minIndex
}