package com.openclaw.clawchat.ui.components

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

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
 * 获取主题感知的链接颜色
 */
@Composable
internal fun getLinkColor(): Color = MaterialTheme.colorScheme.primary

/**
 * 获取主题感知的行内代码颜色
 */
@Composable
internal fun getInlineCodeColors(): Pair<Color, Color> {
    val textColor = MaterialTheme.colorScheme.tertiary
    val bgColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
    return Pair(textColor, bgColor)
}

/**
 * 解析 Markdown 段落
 */
/**
 * Markdown 段落类型（块级元素）
 */
internal sealed class MarkdownBlock {
    data class Text(val text: String) : MarkdownBlock()
    data class CodeBlock(val code: String, val language: String) : MarkdownBlock()
    data class Table(val headers: List<String>, val rows: List<List<String>>) : MarkdownBlock()
    data class Blockquote(val text: String) : MarkdownBlock()
    data class TaskItem(val checked: Boolean, val text: String) : MarkdownBlock()
    object HorizontalRule : MarkdownBlock()
    data class Image(val alt: String, val url: String) : MarkdownBlock()
    data class Heading(val level: Int, val text: String) : MarkdownBlock()
    data class UnorderedListItem(val text: String) : MarkdownBlock()
    data class OrderListItem(val number: Int, val text: String) : MarkdownBlock()
}

/**
 * 解析 Markdown 段落（块级元素）
 * 支持：代码块、普通文本、表格、块引用、任务列表、分割线、图片、标题、列表
 */
internal fun parseMarkdownBlocks(content: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    val lines = content.lines()
    var i = 0

    while (i < lines.size) {
        val line = lines[i]

        // 代码块
        if (line.trim().startsWith("```")) {
            val lang = line.trim().removePrefix("```").trim()
            val codeLines = mutableListOf<String>()
            i++
            while (i < lines.size && !lines[i].trim().startsWith("```")) {
                codeLines.add(lines[i])
                i++
            }
            blocks.add(MarkdownBlock.CodeBlock(codeLines.joinToString("\n"), lang))
            i++ // skip closing ```
            continue
        }

        // 标题
        val headingMatch = Regex("^(#{1,6})\\s+(.*)").find(line)
        if (headingMatch != null) {
            blocks.add(MarkdownBlock.Heading(headingMatch.groupValues[1].length, headingMatch.groupValues[2]))
            i++
            continue
        }

        // 水平分割线
        if (line.trim().matches(Regex("^(-{3,}|\\*{3,}|_{3,})$"))) {
            blocks.add(MarkdownBlock.HorizontalRule)
            i++
            continue
        }

        // 块引用
        if (line.startsWith("> ")) {
            val quoteLines = mutableListOf<String>()
            while (i < lines.size && lines[i].startsWith("> ")) {
                quoteLines.add(lines[i].removePrefix("> "))
                i++
            }
            blocks.add(MarkdownBlock.Blockquote(quoteLines.joinToString(" ")))
            continue
        }

        // 任务列表
        val taskMatch = Regex("^\\s*[-*+]\\s+\\[([ xX])\\]\\s+(.*)").find(line)
        if (taskMatch != null) {
            val checked = taskMatch.groupValues[1].lowercase() == "x"
            blocks.add(MarkdownBlock.TaskItem(checked, taskMatch.groupValues[2]))
            i++
            continue
        }

        // 无序列表项
        val listMatch = Regex("^\\s*[-*+]\\s+(.*)").find(line)
        if (listMatch != null) {
            blocks.add(MarkdownBlock.UnorderedListItem(listMatch.groupValues[1]))
            i++
            continue
        }

        // 有序列表项
        val orderedMatch = Regex("^\\s*(\\d+)\\.\\s+(.*)").find(line)
        if (orderedMatch != null) {
            blocks.add(MarkdownBlock.OrderListItem(orderedMatch.groupValues[1].toInt(), orderedMatch.groupValues[2]))
            i++
            continue
        }

        // 图片 ![alt](url)
        val imageMatch = Regex("^!\\[([^\\]]*)\\]\\(([^)]+)\\)").find(line)
        if (imageMatch != null) {
            blocks.add(MarkdownBlock.Image(imageMatch.groupValues[1], imageMatch.groupValues[2]))
            i++
            continue
        }

        // 表格检测
        if (line.contains("|") && i + 1 < lines.size && lines[i + 1].contains("|") &&
            Regex("^\\s*\\|?\\s*:?-+:?\\s*(\\|\\s*:?-+:?\\s*)*\\|?\\s*$").matches(lines[i + 1])) {
            val headerLine = line.trim().removePrefix("|").removeSuffix("|")
            val headers = headerLine.split("|").map { it.trim() }
            i += 2 // skip header and separator
            val rows = mutableListOf<List<String>>()
            while (i < lines.size && lines[i].contains("|")) {
                val rowLine = lines[i].trim().removePrefix("|").removeSuffix("|")
                rows.add(rowLine.split("|").map { it.trim() })
                i++
            }
            blocks.add(MarkdownBlock.Table(headers, rows))
            continue
        }

        // 空行
        if (line.isBlank()) {
            i++
            continue
        }

        // 普通文本：收集连续的非空行
        val textLines = mutableListOf<String>()
        while (i < lines.size && lines[i].isNotBlank() && !lines[i].trim().startsWith("```") &&
               !Regex("^(#{1,6})\\s+").matches(lines[i]) &&
               !lines[i].trim().matches(Regex("^(-{3,}|\\*{3,}|_{3,})$")) &&
               !lines[i].startsWith("> ") &&
               !Regex("^\\s*[-*+]\\s+").matches(lines[i]) &&
               !Regex("^\\s*\\d+\\.\\s+").matches(lines[i]) &&
               !Regex("^!\\[").matches(lines[i]) &&
               !(lines[i].contains("|") && i + 1 < lines.size && lines[i + 1].contains("|"))) {
            textLines.add(lines[i])
            i++
        }
        if (textLines.isNotEmpty()) {
            blocks.add(MarkdownBlock.Text(textLines.joinToString(" ")))
        }
    }

    return blocks
}

/**
 * 解析 Markdown 段落
 * @deprecated Use parseMarkdownBlocks instead
 */
internal sealed class MarkdownSegment {
    data class Text(val text: String) : MarkdownSegment()
    data class CodeBlock(val code: String, val language: String) : MarkdownSegment()
}

/**
 * @deprecated Use parseMarkdownBlocks instead
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
 * @param linkColor 链接颜色（主题感知）
 * @param codeColor 行内代码颜色（主题感知）
 * @param codeBgColor 行内代码背景色（主题感知）
 */
internal fun parseMarkdownToAnnotatedString(
    content: String,
    linkColor: Color = Color(0xFF58A6FF),
    codeColor: Color = Color(0xFFCE9178),
    codeBgColor: Color = Color(0xFF2D2D2D)
): AnnotatedString {
    return buildAnnotatedString {
        var i = 0
        while (i < content.length) {
            when {
                // Markdown 链接 [text](url)
                content.startsWith("[", i) -> {
                    val textEnd = content.indexOf("]", i)
                    if (textEnd != -1 && textEnd + 1 < content.length && content[textEnd + 1] == '(') {
                        val urlEnd = content.indexOf(")", textEnd + 2)
                        if (urlEnd != -1) {
                            val linkText = content.substring(i + 1, textEnd)
                            val url = content.substring(textEnd + 2, urlEnd)
                            // 添加 URL annotation
                            pushStringAnnotation(tag = "URL", annotation = url)
                            // 添加链接样式（使用传入的颜色）
                            withStyle(SpanStyle(color = linkColor)) {
                                append(linkText)
                            }
                            // 弹出 annotation
                            pop()
                            i = urlEnd + 1
                        } else {
                            append(content[i])
                            i++
                        }
                    } else {
                        append(content[i])
                        i++
                    }
                }
                // 自动链接 http:// 或 https://
                content.startsWith("http://", i) || content.startsWith("https://", i) -> {
                    val urlEnd = content.indexOfAny(charArrayOf(' ', '\n', '\t', ')', '*', '_', '`', '[', ']'), i)
                    val endPos = if (urlEnd == -1) content.length else urlEnd
                    val url = content.substring(i, endPos)
                    // 添加 URL annotation
                    pushStringAnnotation(tag = "URL", annotation = url)
                    // 添加链接样式（使用传入的颜色）
                    withStyle(SpanStyle(color = linkColor)) {
                        append(url)
                    }
                    // 弹出 annotation
                    pop()
                    i = endPos
                }
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
                            background = codeBgColor,
                            color = codeColor
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
 * 语法高亮颜色方案
 */
private object SyntaxColors {
    // 关键字
    val KEYWORD = Color(0xFF569CD6)
    // 字符串
    val STRING = Color(0xFFCE9178)
    // 注释
    val COMMENT = Color(0xFF6A9955)
    // 数字
    val NUMBER = Color(0xFFB5CEA8)
    // 函数
    val FUNCTION = Color(0xFFDCDCAA)
    // 类型
    val TYPE = Color(0xFF4EC9B0)
    // 变量
    val VARIABLE = Color(0xFF9CDCFE)
    // 操作符
    val OPERATOR = Color(0xFFD4D4D4)
    // JSON 键
    val JSON_KEY = Color(0xFF9CDCFE)
    // JSON 值字符串
    val JSON_STRING = Color(0xFFCE9178)
    // JSON 值数字
    val JSON_NUMBER = Color(0xFFB5CEA8)
    // JSON 值布尔
    val JSON_BOOL = Color(0xFF569CD6)
    // JSON null
    val JSON_NULL = Color(0xFF569CD6)
}

/**
 * 语法高亮
 */
internal fun highlightSyntax(code: String, language: String): AnnotatedString {
    return buildAnnotatedString {
        val lang = language.lowercase()

        // JSON 特殊处理
        if (lang == "json") {
            appendJsonHighlight(code)
            return@buildAnnotatedString
        }

        val keywords = when (lang) {
            "kotlin" -> setOf(
                "fun", "val", "var", "class", "interface", "object", "if", "else", "when",
                "for", "while", "return", "import", "package", "private", "public", "protected",
                "suspend", "inline", "data", "sealed", "enum", "companion", "override",
                "abstract", "open", "lateinit", "by", "is", "in", "as", "true", "false", "null",
                "typealias", "reified", "crossinline", "noinline", "external", "tailrec",
                "inner", "annotation", "const", "vararg"
            )
            "java" -> setOf(
                "class", "interface", "enum", "extends", "implements", "public", "private",
                "protected", "static", "final", "abstract", "synchronized", "volatile",
                "transient", "native", "strictfp", "if", "else", "for", "while", "do",
                "switch", "case", "default", "return", "break", "continue", "new", "this",
                "super", "instanceof", "try", "catch", "finally", "throw", "throws",
                "true", "false", "null", "void", "int", "long", "short", "byte", "float",
                "double", "boolean", "char", "package", "import"
            )
            "python" -> setOf(
                "def", "class", "if", "elif", "else", "for", "while", "return", "import",
                "from", "as", "with", "try", "except", "finally", "raise", "lambda", "yield",
                "True", "False", "None", "and", "or", "not", "in", "is", "pass", "break",
                "continue", "global", "nonlocal", "assert", "del", "async", "await"
            )
            "javascript", "typescript", "js", "ts" -> setOf(
                "function", "const", "let", "var", "class", "if", "else", "for", "while",
                "return", "import", "export", "from", "async", "await", "try", "catch",
                "throw", "new", "this", "super", "extends", "implements", "interface",
                "type", "enum", "true", "false", "null", "undefined", "typeof", "instanceof",
                "switch", "case", "default", "break", "continue", "do", "yield", "static",
                "readonly", "public", "private", "protected", "abstract", "override", "namespace"
            )
            "rust" -> setOf(
                "fn", "let", "mut", "pub", "struct", "enum", "impl", "trait", "mod", "use",
                "crate", "self", "super", "where", "match", "if", "else", "loop", "while",
                "for", "in", "return", "move", "ref", "true", "false", "Some", "None",
                "Ok", "Err", "const", "static", "type", "as", "unsafe", "extern", "macro"
            )
            "go" -> setOf(
                "func", "var", "const", "type", "struct", "interface", "map", "chan",
                "package", "import", "if", "else", "for", "range", "switch", "case",
                "default", "return", "go", "defer", "select", "true", "false", "nil",
                "break", "continue", "goto", "fallthrough"
            )
            "c", "cpp", "c++" -> setOf(
                "int", "char", "float", "double", "void", "struct", "union", "enum",
                "typedef", "const", "static", "extern", "if", "else", "for", "while",
                "do", "switch", "case", "default", "return", "break", "continue", "goto",
                "sizeof", "nullptr", "true", "false", "auto", "register", "volatile",
                "inline", "class", "public", "private", "protected", "virtual", "override",
                "final", "new", "delete", "template", "typename", "namespace", "using"
            )
            "swift" -> setOf(
                "func", "var", "let", "class", "struct", "enum", "protocol", "extension",
                "if", "else", "for", "while", "switch", "case", "default", "return",
                "break", "continue", "import", "public", "private", "fileprivate",
                "internal", "open", "static", "override", "final", "mutating", "nonmutating",
                "lazy", "weak", "unowned", "guard", "defer", "inout", "where", "as", "is"
            )
            "sql" -> setOf(
                "SELECT", "FROM", "WHERE", "INSERT", "UPDATE", "DELETE", "CREATE", "DROP",
                "ALTER", "TABLE", "INDEX", "VIEW", "JOIN", "LEFT", "RIGHT", "INNER", "OUTER",
                "ON", "AND", "OR", "NOT", "NULL", "TRUE", "FALSE", "ORDER", "BY", "GROUP",
                "HAVING", "LIMIT", "OFFSET", "AS", "DISTINCT", "COUNT", "SUM", "AVG", "MAX", "MIN"
            )
            "shell", "bash", "sh" -> setOf(
                "if", "then", "else", "elif", "fi", "for", "while", "do", "done", "case",
                "esac", "function", "return", "exit", "break", "continue", "local", "export",
                "source", "alias", "unset", "readonly", "declare", "echo", "printf", "read",
                "true", "false", "test", "shift", "set", "trap"
            )
            "ruby", "rb" -> setOf(
                "def", "class", "module", "end", "if", "else", "elsif", "unless", "case",
                "when", "while", "for", "do", "begin", "rescue", "ensure", "raise", "return",
                "yield", "lambda", "proc", "true", "false", "nil", "self", "super", "require",
                "include", "extend", "attr_reader", "attr_writer", "attr_accessor", "private",
                "protected", "public", "puts", "print", "p", "gets", "chomp", "to_s", "to_i"
            )
            "php" -> setOf(
                "function", "class", "interface", "trait", "extends", "implements", "public",
                "private", "protected", "static", "final", "abstract", "const", "var", "new",
                "if", "else", "elseif", "switch", "case", "default", "break", "continue",
                "for", "foreach", "while", "do", "try", "catch", "finally", "throw", "use",
                "namespace", "return", "echo", "print", "true", "false", "null", "array",
                "isset", "empty", "die", "exit", "include", "require", "include_once", "require_once"
            )
            "yaml", "yml" -> setOf(
                "true", "false", "null", "yes", "no", "on", "off"
            )
            "toml" -> setOf(
                "true", "false"
            )
            // Scala support
            "scala" -> setOf(
                "def", "val", "var", "class", "object", "trait", "extends", "with",
                "if", "else", "match", "case", "for", "while", "do", "yield",
                "return", "throw", "try", "catch", "finally", "import", "package",
                "private", "protected", "public", "override", "abstract", "final",
                "sealed", "implicit", "lazy", "new", "this", "super", "true", "false", "null"
            )
            // Lua support
            "lua" -> setOf(
                "and", "break", "do", "else", "elseif", "end", "false", "for",
                "function", "if", "in", "local", "nil", "not", "or", "repeat",
                "return", "then", "true", "until", "while"
            )
            // Perl support
            "perl", "pl" -> setOf(
                "sub", "my", "local", "our", "if", "else", "elsif", "unless",
                "for", "foreach", "while", "do", "return", "last", "next", "redo",
                "package", "use", "require", "import", "print", "say", "true", "false"
            )
            // R support
            "r" -> setOf(
                "if", "else", "repeat", "while", "function", "for", "in", "next",
                "break", "return", "TRUE", "FALSE", "NULL", "NA", "Inf", "NaN",
                "library", "require", "source"
            )
            // Kotlin DSL / Gradle support
            "gradle", "gradle.kts" -> setOf(
                "plugins", "dependencies", "repositories", "tasks", "android",
                "implementation", "api", "compileOnly", "runtimeOnly", "testImplementation",
                "apply", "from", "version", "group", "id"
            )
            // Dockerfile support
            "dockerfile", "docker" -> setOf(
                "FROM", "RUN", "CMD", "LABEL", "MAINTAINER", "EXPOSE", "ENV",
                "ADD", "COPY", "ENTRYPOINT", "VOLUME", "USER", "WORKDIR", "ARG",
                "ONBUILD", "STOPSIGNAL", "HEALTHCHECK", "SHELL"
            )
            // Makefile support
            "makefile", "make" -> setOf(
                "include", "define", "endef", "ifdef", "ifndef", "ifeq", "ifneq",
                "else", "endif", "override", "export", "unexport", "vpath"
            )
            else -> setOf(
                "if", "else", "for", "while", "return", "function", "class", "const",
                "let", "var", "true", "false", "null", "import", "export", "public",
                "private", "static", "void", "int", "string", "bool"
            )
        }

        val types = when (lang) {
            "kotlin" -> setOf("String", "Int", "Long", "Float", "Double", "Boolean", "Unit", "Any", "Nothing", "List", "Map", "Set", "Pair", "Triple")
            "java" -> setOf("String", "Integer", "Long", "Float", "Double", "Boolean", "Object", "Class", "List", "Map", "Set", "ArrayList", "HashMap", "HashSet")
            "python" -> setOf("str", "int", "float", "bool", "list", "dict", "set", "tuple", "None", "Exception", "BaseException")
            "typescript", "ts" -> setOf("string", "number", "boolean", "object", "any", "void", "null", "undefined", "never", "unknown", "Promise", "Array")
            "ruby", "rb" -> setOf("String", "Integer", "Float", "Array", "Hash", "Symbol", "NilClass", "TrueClass", "FalseClass")
            "php" -> setOf("string", "int", "float", "bool", "array", "object", "null", "mixed", "void", "callable")
            "scala" -> setOf("String", "Int", "Long", "Float", "Double", "Boolean", "Unit", "Any", "Nothing", "List", "Map", "Set", "Option", "Either")
            "lua" -> setOf("string", "number", "boolean", "table", "function", "thread", "userdata")
            "perl", "pl" -> setOf("scalar", "array", "hash", "sub", "ref")
            "r" -> setOf("character", "numeric", "integer", "logical", "complex", "list", "data.frame", "matrix", "vector")
            else -> emptySet()
        }

        val lines = code.lines()
        lines.forEachIndexed { index, line ->
            if (index > 0) append("\n")

            var i = 0
            while (i < line.length) {
                // 检查注释
                if (line.substring(i).startsWith("//") ||
                    line.substring(i).startsWith("#") ||
                    (lang == "python" && line.substring(i).startsWith("#"))) {
                    withStyle(SpanStyle(color = SyntaxColors.COMMENT)) {
                        append(line.substring(i))
                    }
                    break
                }

                // 检查块注释开始
                if (line.substring(i).startsWith("/*")) {
                    val end = line.indexOf("*/", i + 2)
                    if (end != -1) {
                        withStyle(SpanStyle(color = SyntaxColors.COMMENT)) {
                            append(line.substring(i, end + 2))
                        }
                        i = end + 2
                        continue
                    } else {
                        withStyle(SpanStyle(color = SyntaxColors.COMMENT)) {
                            append(line.substring(i))
                        }
                        break
                    }
                }

                // 检查字符串
                if (line[i] == '"' || line[i] == '\'') {
                    val quote = line[i]
                    var end = line.indexOf(quote, i + 1)
                    // 处理转义
                    while (end > 0 && line[end - 1] == '\\') {
                        end = line.indexOf(quote, end + 1)
                    }
                    if (end != -1) {
                        withStyle(SpanStyle(color = SyntaxColors.STRING)) {
                            append(line.substring(i, end + 1))
                        }
                        i = end + 1
                        continue
                    }
                }

                // 检查模板字符串 (反引号)
                if (line[i] == '`') {
                    val end = line.indexOf('`', i + 1)
                    if (end != -1) {
                        withStyle(SpanStyle(color = SyntaxColors.STRING)) {
                            append(line.substring(i, end + 1))
                        }
                        i = end + 1
                        continue
                    }
                }

                // 检查关键字、类型、数字
                val wordEnd = line.indexOfAny(charArrayOf(' ', '(', ')', '{', '}', '[', ']', ',', ';', ':', '.', '=', '<', '>', '+', '-', '*', '/', '!', '&', '|', '?', '@', '#', '$'), i)
                val endPos = if (wordEnd == -1) line.length else wordEnd
                val word = line.substring(i, endPos)

                when {
                    word in keywords -> {
                        withStyle(SpanStyle(color = SyntaxColors.KEYWORD, fontWeight = FontWeight.Medium)) {
                            append(word)
                        }
                    }
                    word in types -> {
                        withStyle(SpanStyle(color = SyntaxColors.TYPE)) {
                            append(word)
                        }
                    }
                    word.matches(Regex("\\d+(\\.\\d+)?([eE][+-]?\\d+)?[fFlLdD]?")) -> {
                        withStyle(SpanStyle(color = SyntaxColors.NUMBER)) {
                            append(word)
                        }
                    }
                    // 函数调用检测 (后跟括号)
                    endPos < line.length && line[endPos] == '(' && word.firstOrNull()?.isLetter() == true -> {
                        withStyle(SpanStyle(color = SyntaxColors.FUNCTION)) {
                            append(word)
                        }
                    }
                    else -> {
                        append(word)
                    }
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
 * JSON 语法高亮
 */
private fun AnnotatedString.Builder.appendJsonHighlight(json: String) {
    var i = 0
    var inString = false
    var stringStart = 0
    var isKey = true // 下一个字符串是否是键

    while (i < json.length) {
        when {
            // 字符串
            json[i] == '"' && !inString -> {
                inString = true
                stringStart = i
                append(json[i])
                i++
            }
            json[i] == '"' && inString -> {
                inString = false
                val stringContent = json.substring(stringStart, i + 1)
                withStyle(SpanStyle(
                    color = if (isKey) SyntaxColors.JSON_KEY else SyntaxColors.JSON_STRING
                )) {
                    append(stringContent)
                }
                isKey = false // 字符串后不是键，直到遇到逗号或结束
                i++
            }
            inString -> {
                append(json[i])
                i++
            }
            // 数字
            json[i].isDigit() || (json[i] == '-' && i + 1 < json.length && json[i + 1].isDigit()) -> {
                var end = i
                while (end < json.length && (json[end].isDigit() || json[end] == '.' || json[end] == 'e' || json[end] == 'E' || json[end] == '+' || json[end] == '-' || json[end] == 'f' || json[end] == 'F')) {
                    end++
                }
                withStyle(SpanStyle(color = SyntaxColors.JSON_NUMBER)) {
                    append(json.substring(i, end))
                }
                i = end
            }
            // 布尔值
            json.substring(i).startsWith("true") -> {
                withStyle(SpanStyle(color = SyntaxColors.JSON_BOOL)) {
                    append("true")
                }
                i += 4
            }
            json.substring(i).startsWith("false") -> {
                withStyle(SpanStyle(color = SyntaxColors.JSON_BOOL)) {
                    append("false")
                }
                i += 5
            }
            // null
            json.substring(i).startsWith("null") -> {
                withStyle(SpanStyle(color = SyntaxColors.JSON_NULL)) {
                    append("null")
                }
                i += 4
            }
            // 逗号后下一个字符串是键
            json[i] == ',' -> {
                append(json[i])
                isKey = true
                i++
            }
            // 冒号
            json[i] == ':' -> {
                append(json[i])
                isKey = false // 冒号后是值
                i++
            }
            else -> {
                append(json[i])
                i++
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