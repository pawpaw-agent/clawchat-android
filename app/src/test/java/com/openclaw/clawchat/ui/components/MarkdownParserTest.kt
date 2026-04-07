package com.openclaw.clawchat.ui.components

import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.*
import org.junit.Test

/**
 * MarkdownParser 单元测试
 */
class MarkdownParserTest {

    // ─────────────────────────────────────────────────────────────
    // parseMarkdownSegments 测试
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `parseMarkdownSegments parses plain text`() {
        val content = "This is plain text"
        val segments = parseMarkdownSegments(content)

        assertEquals(1, segments.size)
        assertTrue(segments[0] is MarkdownSegment.Text)
        assertEquals("This is plain text", (segments[0] as MarkdownSegment.Text).text)
    }

    @Test
    fun `parseMarkdownSegments parses code block`() {
        val content = "```kotlin\nval x = 1\n```"
        val segments = parseMarkdownSegments(content)

        assertEquals(1, segments.size)
        assertTrue(segments[0] is MarkdownSegment.CodeBlock)
        val codeBlock = segments[0] as MarkdownSegment.CodeBlock
        assertEquals("kotlin", codeBlock.language)
        assertTrue(codeBlock.code.contains("val x = 1"))
    }

    @Test
    fun `parseMarkdownSegments parses mixed content`() {
        val content = "Some text\n```python\nprint('hello')\n```\nMore text"
        val segments = parseMarkdownSegments(content)

        assertEquals(3, segments.size)
        assertTrue(segments[0] is MarkdownSegment.Text)
        assertTrue(segments[1] is MarkdownSegment.CodeBlock)
        assertTrue(segments[2] is MarkdownSegment.Text)
    }

    @Test
    fun `parseMarkdownSegments handles empty code block`() {
        val content = "```json\n```"
        val segments = parseMarkdownSegments(content)

        assertEquals(1, segments.size)
        assertTrue(segments[0] is MarkdownSegment.CodeBlock)
        assertEquals("json", (segments[0] as MarkdownSegment.CodeBlock).language)
    }

    @Test
    fun `parseMarkdownSegments handles multiple code blocks`() {
        val content = "```kotlin\ncode1\n```\n```python\ncode2\n```"
        val segments = parseMarkdownSegments(content)

        assertEquals(2, segments.size)
        assertEquals("kotlin", (segments[0] as MarkdownSegment.CodeBlock).language)
        assertEquals("python", (segments[1] as MarkdownSegment.CodeBlock).language)
    }

    @Test
    fun `parseMarkdownSegments handles code block without language`() {
        val content = "```\ncode\n```"
        val segments = parseMarkdownSegments(content)

        assertEquals(1, segments.size)
        assertEquals("", (segments[0] as MarkdownSegment.CodeBlock).language)
    }

    // ─────────────────────────────────────────────────────────────
    // parseMarkdownToAnnotatedString 测试
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `parseMarkdownToAnnotatedString handles plain text`() {
        val content = "Hello World"
        val result = parseMarkdownToAnnotatedString(content)

        assertEquals("Hello World", result.text)
    }

    @Test
    fun `parseMarkdownToAnnotatedString handles bold text`() {
        val content = "**bold text**"
        val result = parseMarkdownToAnnotatedString(content)

        assertEquals("bold text", result.text)
        // 验证 SpanStyle 存在
        assertTrue(result.spanStyles.isNotEmpty())
    }

    @Test
    fun `parseMarkdownToAnnotatedString handles italic text`() {
        val content = "*italic text*"
        val result = parseMarkdownToAnnotatedString(content)

        assertEquals("italic text", result.text)
        assertTrue(result.spanStyles.isNotEmpty())
    }

    @Test
    fun `parseMarkdownToAnnotatedString handles underscore italic`() {
        val content = "_italic text_"
        val result = parseMarkdownToAnnotatedString(content)

        assertEquals("italic text", result.text)
        assertTrue(result.spanStyles.isNotEmpty())
    }

    @Test
    fun `parseMarkdownToAnnotatedString handles inline code`() {
        val content = "`code snippet`"
        val result = parseMarkdownToAnnotatedString(content)

        assertEquals("code snippet", result.text)
        assertTrue(result.spanStyles.isNotEmpty())
    }

    @Test
    fun `parseMarkdownToAnnotatedString handles link`() {
        val content = "[link text](https://example.com)"
        val result = parseMarkdownToAnnotatedString(content)

        assertEquals("link text", result.text)
        // 验证 URL annotation 存在
        val urlAnnotations = result.getStringAnnotations("URL", 0, result.length)
        assertTrue(urlAnnotations.isNotEmpty())
        assertEquals("https://example.com", urlAnnotations.first().item)
    }

    @Test
    fun `parseMarkdownToAnnotatedString handles auto link`() {
        val content = "https://example.com"
        val result = parseMarkdownToAnnotatedString(content)

        assertEquals("https://example.com", result.text)
        val urlAnnotations = result.getStringAnnotations("URL", 0, result.length)
        assertTrue(urlAnnotations.isNotEmpty())
    }

    @Test
    fun `parseMarkdownToAnnotatedString handles complex markdown`() {
        val content = "**bold** and *italic* and `code`"
        val result = parseMarkdownToAnnotatedString(content)

        assertTrue(result.text.contains("bold"))
        assertTrue(result.text.contains("italic"))
        assertTrue(result.text.contains("code"))
        assertTrue(result.spanStyles.size >= 3)
    }

    @Test
    fun `parseMarkdownToAnnotatedString handles nested formatting`() {
        val content = "**bold with *italic* inside**"
        val result = parseMarkdownToAnnotatedString(content)

        assertTrue(result.spanStyles.isNotEmpty())
    }

    // ─────────────────────────────────────────────────────────────
    // highlightSyntax 测试
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `highlightSyntax handles kotlin keywords`() {
        val code = "fun main() { val x = 1 }"
        val result = highlightSyntax(code, "kotlin")

        assertTrue(result.text.contains("fun"))
        assertTrue(result.text.contains("val"))
        assertTrue(result.spanStyles.isNotEmpty())
    }

    @Test
    fun `highlightSyntax handles python keywords`() {
        val code = "def test(): return True"
        val result = highlightSyntax(code, "python")

        assertTrue(result.text.contains("def"))
        assertTrue(result.text.contains("return"))
        assertTrue(result.spanStyles.isNotEmpty())
    }

    @Test
    fun `highlightSyntax handles json`() {
        val json = "{\"key\": \"value\", \"num\": 42}"
        val result = highlightSyntax(json, "json")

        assertTrue(result.spanStyles.isNotEmpty())
    }

    @Test
    fun `highlightSyntax handles json with null`() {
        val json = "{\"key\": null, \"bool\": true}"
        val result = highlightSyntax(json, "json")

        assertTrue(result.spanStyles.isNotEmpty())
    }

    @Test
    fun `highlightSyntax handles numbers`() {
        val code = "val x = 123.45"
        val result = highlightSyntax(code, "kotlin")

        assertTrue(result.text.contains("123.45"))
        assertTrue(result.spanStyles.isNotEmpty())
    }

    @Test
    fun `highlightSyntax handles comments`() {
        val code = "// This is a comment\nval x = 1"
        val result = highlightSyntax(code, "kotlin")

        assertTrue(result.spanStyles.isNotEmpty())
    }

    @Test
    fun `highlightSyntax handles strings`() {
        val code = "val s = \"hello world\""
        val result = highlightSyntax(code, "kotlin")

        assertTrue(result.spanStyles.isNotEmpty())
    }

    @Test
    fun `highlightSyntax handles function calls`() {
        val code = "println(\"test\")"
        val result = highlightSyntax(code, "kotlin")

        assertTrue(result.spanStyles.isNotEmpty())
    }

    @Test
    fun `highlightSyntax handles rust code`() {
        val code = "fn main() { let x = 1; }"
        val result = highlightSyntax(code, "rust")

        assertTrue(result.spanStyles.isNotEmpty())
    }

    @Test
    fun `highlightSyntax handles javascript code`() {
        val code = "function test() { return true; }"
        val result = highlightSyntax(code, "javascript")

        assertTrue(result.spanStyles.isNotEmpty())
    }

    @Test
    fun `highlightSyntax handles unknown language`() {
        val code = "if condition then return"
        val result = highlightSyntax(code, "unknown")

        assertTrue(result.text.isNotEmpty())
    }

    @Test
    fun `highlightSyntax handles scala code`() {
        val code = "def hello(): String = \"world\""
        val result = highlightSyntax(code, "scala")

        assertTrue(result.spanStyles.isNotEmpty())
    }

    @Test
    fun `highlightSyntax handles lua code`() {
        val code = "function test() return true end"
        val result = highlightSyntax(code, "lua")

        assertTrue(result.spanStyles.isNotEmpty())
    }

    @Test
    fun `highlightSyntax handles perl code`() {
        val code = "sub test { my $x = 1; return $x; }"
        val result = highlightSyntax(code, "perl")

        assertTrue(result.spanStyles.isNotEmpty())
    }

    @Test
    fun `highlightSyntax handles r code`() {
        val code = "library(dplyr)\ndata <- data.frame(x = 1:10)"
        val result = highlightSyntax(code, "r")

        assertTrue(result.spanStyles.isNotEmpty())
    }

    @Test
    fun `highlightSyntax handles gradle code`() {
        val code = "plugins { id(\"com.android.application\") }"
        val result = highlightSyntax(code, "gradle")

        assertTrue(result.spanStyles.isNotEmpty())
    }

    @Test
    fun `highlightSyntax handles dockerfile`() {
        val code = "FROM ubuntu:latest\nRUN apt-get update"
        val result = highlightSyntax(code, "dockerfile")

        assertTrue(result.spanStyles.isNotEmpty())
    }

    @Test
    fun `highlightSyntax handles makefile`() {
        val code = "all: build\n\t$(CC) -o app main.c"
        val result = highlightSyntax(code, "makefile")

        assertTrue(result.text.isNotEmpty())
    }

    // ─────────────────────────────────────────────────────────────
    // parseTableRow 测试
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `parseTableRow parses simple row`() {
        val line = "| col1 | col2 | col3 |"
        val result = parseTableRow(line)

        assertEquals(3, result.size)
        assertEquals("col1", result[0])
        assertEquals("col2", result[1])
        assertEquals("col3", result[2])
    }

    @Test
    fun `parseTableRow handles extra spaces`() {
        val line = "|  col1  |  col2  |"
        val result = parseTableRow(line)

        assertEquals(2, result.size)
        assertEquals("col1", result[0])
        assertEquals("col2", result[1])
    }

    @Test
    fun `parseTableRow handles row without outer pipes`() {
        val line = "col1 | col2 | col3"
        val result = parseTableRow(line)

        assertEquals(3, result.size)
    }

    @Test
    fun `parseTableRow handles empty cells`() {
        val line = "| | col2 | |"
        val result = parseTableRow(line)

        assertEquals(3, result.size)
        assertEquals("", result[0])
        assertEquals("col2", result[1])
        assertEquals("", result[2])
    }
}