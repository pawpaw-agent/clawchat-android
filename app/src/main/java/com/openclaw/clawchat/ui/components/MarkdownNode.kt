package com.openclaw.clawchat.ui.components

/**
 * Markdown AST 节点
 * 
 * 表示解析后的 Markdown 结构，用于自定义渲染
 */
sealed class MarkdownNode {
    /**
     * 段落
     */
    data class Paragraph(val children: List<MarkdownNode>) : MarkdownNode()
    
    /**
     * 标题
     */
    data class Heading(val level: Int, val text: String) : MarkdownNode()
    
    /**
     * 代码块
     */
    data class CodeBlock(val language: String?, val code: String) : MarkdownNode()
    
    /**
     * 行内代码
     */
    data class InlineCode(val code: String) : MarkdownNode()
    
    /**
     * 链接
     */
    data class Link(val text: String, val url: String) : MarkdownNode()
    
    /**
     * 引用块
     */
    data class BlockQuote(val children: List<MarkdownNode>) : MarkdownNode()
    
    /**
     * 无序列表
     */
    data class UnorderedList(val items: List<MarkdownNode>) : MarkdownNode()
    
    /**
     * 有序列表
     */
    data class OrderedList(val items: List<MarkdownNode>, val start: Int = 1) : MarkdownNode()
    
    /**
     * 列表项
     */
    data class ListItem(val children: List<MarkdownNode>) : MarkdownNode()
    
    /**
     * 粗体文本
     */
    data class Bold(val text: String) : MarkdownNode()
    
    /**
     * 斜体文本
     */
    data class Italic(val text: String) : MarkdownNode()
    
    /**
     * 删除线
     */
    data class Strikethrough(val text: String) : MarkdownNode()
    
    /**
     * 纯文本
     */
    data class Text(val text: String) : MarkdownNode()
    
    /**
     * 换行
     */
    data object SoftLineBreak : MarkdownNode()
    
    /**
     * 水平线
     */
    data object HorizontalRule : MarkdownNode()
    
    /**
     * 图片
     */
    data class Image(val alt: String, val url: String) : MarkdownNode()
}