package com.openclaw.clawchat.ui.components

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import java.util.regex.Pattern

/**
 * Markdown 解析器
 * 
 * 提供解析函数：
 * - parseMarkdownSegments
 * - parseMarkdownToAnnotatedString
 * - highlightSyntax
 * - parseTableRow
 */

/**
 * Markdown 内容片段
 */
sealed class MarkdownSegment {
    data class Text(val content: String) : MarkdownSegment()
    data class CodeBlock(val language: String, val code: String) : MarkdownSegment()
}

/**
 * 解析 Markdown 内容为片段列表
 */
fun parseMarkdownSegments(content: String): List<MarkdownSegment> {
    val segments = mutableListOf<MarkdownSegment>()
    val codeBlockPattern = Pattern.compile("```(\\w*)\\s*\\n?(.*?)```", Pattern.DOTALL)
    val matcher = codeBlockPattern.matcher(content)
    
    var lastEnd = 0
    while (matcher.find()) {
        // 添加代码块之前的文本
        if (matcher.start() > lastEnd) {
            val text = content.substring(lastEnd, matcher.start()).trim()
            if (text.isNotEmpty()) {
                segments.add(MarkdownSegment.Text(text))
            }
        }
        
        // 添加代码块
        val language = matcher.group(1) ?: ""
        val code = matcher.group(2) ?: ""
        segments.add(MarkdownSegment.CodeBlock(language.trim(), code.trim()))
        
        lastEnd = matcher.end()
    }
    
    // 添加最后的文本
    if (lastEnd < content.length) {
        val text = content.substring(lastEnd).trim()
        if (text.isNotEmpty()) {
            segments.add(MarkdownSegment.Text(text))
        }
    }
    
    return segments
}

/**
 * 解析 Markdown 为 AnnotatedString
 */
fun parseMarkdownToAnnotatedString(content: String): AnnotatedString = buildAnnotatedString {
    var remaining = content
    
    while (remaining.isNotEmpty()) {
        when {
            // 粗体
            remaining.startsWith("**") -> {
                val endIndex = remaining.indexOf("**", 2)
                if (endIndex > 0) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(remaining.substring(2, endIndex))
                    }
                    remaining = remaining.substring(endIndex + 2)
                } else {
                    append("**")
                    remaining = remaining.substring(2)
                }
            }
            // 斜体
            remaining.startsWith("*") && !remaining.startsWith("**") -> {
                val endIndex = remaining.indexOf("*", 1)
                if (endIndex > 0) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(remaining.substring(1, endIndex))
                    }
                    remaining = remaining.substring(endIndex + 1)
                } else {
                    append("*")
                    remaining = remaining.substring(1)
                }
            }
            // 行内代码
            remaining.startsWith("`") && !remaining.startsWith("```") -> {
                val endIndex = remaining.indexOf("`", 1)
                if (endIndex > 0) {
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp)) {
                        append(remaining.substring(1, endIndex))
                    }
                    remaining = remaining.substring(endIndex + 1)
                } else {
                    append("`")
                    remaining = remaining.substring(1)
                }
            }
            // 普通字符
            else -> {
                append(remaining[0])
                remaining = remaining.substring(1)
            }
        }
    }
}

/**
 * 语法高亮
 */
fun highlightSyntax(code: String, language: String): AnnotatedString = buildAnnotatedString {
    val keywords = when (language.lowercase()) {
        "kotlin", "java" -> listOf(
            "fun", "val", "var", "class", "interface", "object", "if", "else", "when",
            "for", "while", "do", "return", "break", "continue", "import", "package",
            "private", "public", "protected", "internal", "open", "override", "abstract",
            "sealed", "data", "enum", "companion", "this", "super", "null", "true", "false",
            "suspend", "inline", "reified", "crossinline", "noinline", "lateinit",
            "by", "lazy", "init", "constructor", "get", "set", "where", "typealias",
            "as", "is", "in", "out", "typeof", "void", "static", "final", "extends",
            "implements", "throws", "try", "catch", "finally", "throw", "new", "instanceof"
        )
        "python" -> listOf(
            "def", "class", "if", "elif", "else", "for", "while", "return", "import",
            "from", "as", "try", "except", "finally", "raise", "with", "lambda", "yield",
            "global", "nonlocal", "pass", "break", "continue", "True", "False", "None",
            "and", "or", "not", "in", "is", "async", "await"
        )
        "javascript", "js", "typescript", "ts" -> listOf(
            "function", "const", "let", "var", "class", "if", "else", "for", "while",
            "return", "import", "export", "from", "as", "try", "catch", "finally",
            "throw", "new", "this", "super", "extends", "static", "get", "set",
            "async", "await", "yield", "true", "false", "null", "undefined",
            "typeof", "instanceof", "void", "delete", "in", "of"
        )
        "json" -> listOf("true", "false", "null")
        else -> emptyList()
    }
    
    val stringColor = SpanStyle(color = androidx.compose.ui.graphics.Color(0xFF6A9955))
    val keywordColor = SpanStyle(color = androidx.compose.ui.graphics.Color(0xFF569CD6))
    val numberColor = SpanStyle(color = androidx.compose.ui.graphics.Color(0xFFB5CEA8))
    val commentColor = SpanStyle(color = androidx.compose.ui.graphics.Color(0xFF6A9955))
    
    val lines = code.lines()
    lines.forEachIndexed { index, line ->
        var inString = false
        var stringStart = 0
        var i = 0
        
        while (i < line.length) {
            val char = line[i]
            
            when {
                // 字符串
                char == '"' || char == '\'' -> {
                    if (!inString) {
                        inString = true
                        stringStart = i
                    } else {
                        // 结束字符串
                        withStyle(stringColor) {
                            append(line.substring(stringStart, i + 1))
                        }
                        inString = false
                    }
                    i++
                }
                // 注释
                !inString && (char == '/' && i + 1 < line.length && line[i + 1] == '/') ||
                (char == '#' && language.lowercase() in listOf("python", "shell", "bash", "yaml")) -> {
                    withStyle(commentColor) {
                        append(line.substring(i))
                    }
                    break
                }
                // 关键字
                !inString && keywords.any { kw ->
                    line.substring(i).startsWith(kw) &&
                    (i + kw.length >= line.length || !line[i + kw.length].isLetterOrDigit())
                } -> {
                    val keyword = keywords.first { kw ->
                        line.substring(i).startsWith(kw) &&
                        (i + kw.length >= line.length || !line[i + kw.length].isLetterOrDigit())
                    }
                    withStyle(keywordColor) {
                        append(keyword)
                    }
                    i += keyword.length
                }
                // 数字
                !inString && char.isDigit() -> {
                    var j = i
                    while (j < line.length && (line[j].isDigit() || line[j] == '.')) {
                        j++
                    }
                    withStyle(numberColor) {
                        append(line.substring(i, j))
                    }
                    i = j
                }
                else -> {
                    if (!inString) {
                        append(char)
                    }
                    i++
                }
            }
        }
        
        if (inString) {
            // 未闭合的字符串
            withStyle(stringColor) {
                append(line.substring(stringStart))
            }
        }
        
        if (index < lines.size - 1) {
            append("\n")
        }
    }
}

/**
 * 解析表格行
 */
fun parseTableRow(line: String): List<String> {
    return line.trim()
        .removePrefix("|")
        .removeSuffix("|")
        .split("|")
        .map { it.trim() }
}