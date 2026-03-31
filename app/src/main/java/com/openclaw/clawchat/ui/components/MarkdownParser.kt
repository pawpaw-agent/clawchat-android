package com.openclaw.clawchat.ui.components

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight

/**
 * Markdown 解析器
 * 
 * 提取自 MarkdownText.kt 的解析相关函数：
 * - MarkdownSegment 数据类
 * - parseMarkdownSegments
 * - parseMarkdownToAnnotatedString
 * - highlightSyntax
 * - parseTableRow
 */

/**
 * 解析 Markdown 段落
 */
internal sealed class MarkdownSegment {
    data class Text(val text: String) : MarkdownSegment()
    data class CodeBlock(val code: String, val language: String) : MarkdownSegment()
}

/**
 * 解析 Markdown 内容为段落列表
 */
internal fun parseMarkdownSegments(content: String): List<MarkdownSegment> {
    val segments = mutableListOf<MarkdownSegment>()
    val lines = content.lines()
    val currentText = StringBuilder()
    var inCodeBlock = false
    var codeBlockContent = StringBuilder()
    var codeBlockLanguage = ""
    
    for (line in lines) {
        if (line.trim().startsWith("```")) {
            if (inCodeBlock) {
                // 结束代码块
                segments.add(MarkdownSegment.CodeBlock(
                    code = codeBlockContent.toString(),
                    language = codeBlockLanguage
                ))
                codeBlockContent = StringBuilder()
                codeBlockLanguage = ""
                inCodeBlock = false
            } else {
                // 开始代码块
                if (currentText.isNotBlank()) {
                    segments.add(MarkdownSegment.Text(currentText.toString().trimEnd()))
                    currentText.clear()
                }
                codeBlockLanguage = line.trim().removePrefix("```").trim()
                inCodeBlock = true
            }
        } else if (inCodeBlock) {
            codeBlockContent.appendLine(line)
        } else {
            currentText.appendLine(line)
        }
    }
    
    // 处理未结束的代码块
    if (inCodeBlock && codeBlockContent.isNotBlank()) {
        segments.add(MarkdownSegment.CodeBlock(
            code = codeBlockContent.toString(),
            language = codeBlockLanguage
        ))
    } else if (currentText.isNotBlank()) {
        segments.add(MarkdownSegment.Text(currentText.toString().trimEnd()))
    }
    
    return segments
}

/**
 * 解析 Markdown 为 AnnotatedString
 */
internal fun parseMarkdownToAnnotatedString(content: String): AnnotatedString {
    return buildAnnotatedString {
        var i = 0
        while (i < content.length) {
            when {
                // 粗体 **text**
                content.startsWith("**", i) -> {
                    val end = content.indexOf("**", i + 2)
                    if (end != -1) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(content.substring(i + 2, end))
                        }
                        i = end + 2
                    } else {
                        append(content[i])
                        i++
                    }
                }
                // 斜体 *text* 或 _text_
                (content.startsWith("*", i) && !content.startsWith("**", i)) || content.startsWith("_", i) -> {
                    val marker = if (content.startsWith("*", i)) "*" else "_"
                    val end = content.indexOf(marker, i + 1)
                    if (end != -1 && end > i + 1) {
                        withStyle(SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)) {
                            append(content.substring(i + 1, end))
                        }
                        i = end + 1
                    } else {
                        append(content[i])
                        i++
                    }
                }
                // 行内代码 `code`
                content.startsWith("`", i) && !content.startsWith("```", i) -> {
                    val end = content.indexOf("`", i + 1)
                    if (end != -1) {
                        withStyle(SpanStyle(
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            background = Color(0xFF2D2D2D),
                            color = Color(0xFFCE9178)
                        )) {
                            append(content.substring(i + 1, end))
                        }
                        i = end + 1
                    } else {
                        append(content[i])
                        i++
                    }
                }
                // 普通文本
                else -> {
                    append(content[i])
                    i++
                }
            }
        }
    }
}

/**
 * 语法高亮
 */
internal fun highlightSyntax(code: String, language: String): AnnotatedString {
    return buildAnnotatedString {
        val keywords = when (language.lowercase()) {
            "kotlin", "java" -> setOf("fun", "val", "var", "class", "interface", "object", "if", "else", "when", "for", "while", "return", "import", "package", "private", "public", "protected", "suspend", "inline", "data", "sealed", "enum", "companion", "override", "abstract", "open", "lateinit", "by", "is", "in", "as", "true", "false", "null")
            "python" -> setOf("def", "class", "if", "elif", "else", "for", "while", "return", "import", "from", "as", "with", "try", "except", "finally", "raise", "lambda", "yield", "True", "False", "None", "and", "or", "not", "in", "is")
            "javascript", "typescript", "js", "ts" -> setOf("function", "const", "let", "var", "class", "if", "else", "for", "while", "return", "import", "export", "from", "async", "await", "try", "catch", "throw", "new", "this", "super", "extends", "implements", "interface", "type", "enum", "true", "false", "null", "undefined")
            "rust" -> setOf("fn", "let", "mut", "pub", "struct", "enum", "impl", "trait", "mod", "use", "crate", "self", "super", "where", "match", "if", "else", "loop", "while", "for", "in", "return", "move", "ref", "true", "false", "Some", "None", "Ok", "Err")
            "go" -> setOf("func", "var", "const", "type", "struct", "interface", "map", "chan", "package", "import", "if", "else", "for", "range", "switch", "case", "default", "return", "go", "defer", "select", "true", "false", "nil")
            "c", "cpp", "c++" -> setOf("int", "char", "float", "double", "void", "struct", "union", "enum", "typedef", "const", "static", "extern", "if", "else", "for", "while", "do", "switch", "case", "default", "return", "break", "continue", "goto", "sizeof", "nullptr", "true", "false")
            else -> setOf("if", "else", "for", "while", "return", "function", "class", "const", "let", "var", "true", "false", "null")
        }
        
        val lines = code.lines()
        lines.forEachIndexed { index, line ->
            if (index > 0) append("\n")
            
            var i = 0
            while (i < line.length) {
                // 检查注释
                if (line.substring(i).startsWith("//") || line.substring(i).startsWith("#")) {
                    withStyle(SpanStyle(color = Color(0xFF6A9955))) {
                        append(line.substring(i))
                    }
                    break
                }
                
                // 检查字符串
                if (line[i] == '"' || line[i] == '\'') {
                    val quote = line[i]
                    val end = line.indexOf(quote, i + 1)
                    if (end != -1) {
                        withStyle(SpanStyle(color = Color(0xFFCE9178))) {
                            append(line.substring(i, end + 1))
                        }
                        i = end + 1
                        continue
                    }
                }
                
                // 检查关键字
                val wordEnd = line.indexOfAny(charArrayOf(' ', '(', ')', '{', '}', '[', ']', ',', ';', ':', '.', '=', '<', '>', '+', '-', '*', '/', '!', '&', '|', '?'), i)
                val endPos = if (wordEnd == -1) line.length else wordEnd
                val word = line.substring(i, endPos)
                
                if (word in keywords) {
                    withStyle(SpanStyle(color = Color(0xFF569CD6), fontWeight = FontWeight.Medium)) {
                        append(word)
                    }
                } else if (word.matches(Regex("\\d+(\\.\\d+)?"))) {
                    withStyle(SpanStyle(color = Color(0xFFB5CEA8))) {
                        append(word)
                    }
                } else {
                    append(word)
                }
                
                i = endPos
                if (i < line.length) {
                    append(line[i])
                    i++
                }
            }
        }
    }
}

/**
 * 解析表格行
 */
internal fun parseTableRow(line: String): List<String> {
    return line.trim()
        .removePrefix("|")
        .removeSuffix("|")
        .split("|")
        .map { it.trim() }
}