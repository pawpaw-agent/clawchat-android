package com.openclaw.clawchat.util

import org.junit.Assert.*
import org.junit.Test

/**
 * CollectionUtils 单元测试
 */
class CollectionUtilsTest {
    
    // ─────────────────────────────────────────────────────────────
    // 空检查测试
    // ─────────────────────────────────────────────────────────────
    
    @Test
    fun `isEmpty returns true for null`() {
        assertTrue(CollectionUtils.isEmpty(null))
    }
    
    @Test
    fun `isEmpty returns true for empty list`() {
        assertTrue(CollectionUtils.isEmpty(emptyList()))
    }
    
    @Test
    fun `isEmpty returns false for non-empty list`() {
        assertFalse(CollectionUtils.isEmpty(listOf(1, 2, 3)))
    }
    
    @Test
    fun `isNotEmpty returns true for non-empty list`() {
        assertTrue(CollectionUtils.isNotEmpty(listOf(1, 2, 3)))
    }
    
    // ─────────────────────────────────────────────────────────────
    // 安全获取测试
    // ─────────────────────────────────────────────────────────────
    
    @Test
    fun `getOrNull returns element at valid index`() {
        val list = listOf("a", "b", "c")
        val result = CollectionUtils.getOrNull(list, 1)
        assertEquals("b", result)
    }
    
    @Test
    fun `getOrNull returns null for negative index`() {
        val list = listOf("a", "b", "c")
        val result = CollectionUtils.getOrNull(list, -1)
        assertNull(result)
    }
    
    @Test
    fun `getOrNull returns null for out of bounds index`() {
        val list = listOf("a", "b", "c")
        val result = CollectionUtils.getOrNull(list, 10)
        assertNull(result)
    }
    
    @Test
    fun `getOrNull returns default for null list`() {
        val result = CollectionUtils.getOrNull(null, 0, "default")
        assertEquals("default", result)
    }
    
    @Test
    fun `getOrNull returns default for out of bounds`() {
        val list = listOf("a", "b", "c")
        val result = CollectionUtils.getOrNull(list, 10, "default")
        assertEquals("default", result)
    }
    
    // ─────────────────────────────────────────────────────────────
    // 首尾元素测试
    // ─────────────────────────────────────────────────────────────
    
    @Test
    fun `firstOrNull returns first element`() {
        val list = listOf("a", "b", "c")
        val result = CollectionUtils.firstOrNull(list)
        assertEquals("a", result)
    }
    
    @Test
    fun `firstOrNull returns null for empty list`() {
        val result = CollectionUtils.firstOrNull(emptyList())
        assertNull(result)
    }
    
    @Test
    fun `lastOrNull returns last element`() {
        val list = listOf("a", "b", "c")
        val result = CollectionUtils.lastOrNull(list)
        assertEquals("c", result)
    }
    
    @Test
    fun `lastOrNull returns null for empty list`() {
        val result = CollectionUtils.lastOrNull(emptyList())
        assertNull(result)
    }
    
    // ─────────────────────────────────────────────────────────────
    // 分批测试
    // ─────────────────────────────────────────────────────────────
    
    @Test
    fun `chunked splits list into batches`() {
        val list = listOf(1, 2, 3, 4, 5, 6, 7)
        val result = CollectionUtils.chunked(list, 3)
        assertEquals(3, result.size)
        assertEquals(listOf(1, 2, 3), result[0])
        assertEquals(listOf(4, 5, 6), result[1])
        assertEquals(listOf(7), result[2])
    }
    
    @Test
    fun `chunked returns single batch for zero batchSize`() {
        val list = listOf(1, 2, 3)
        val result = CollectionUtils.chunked(list, 0)
        assertEquals(1, result.size)
        assertEquals(list, result[0])
    }
    
    // ─────────────────────────────────────────────────────────────
    // 去重测试
    // ─────────────────────────────────────────────────────────────
    
    @Test
    fun `distinct removes duplicates`() {
        val list = listOf(1, 2, 2, 3, 3, 3)
        val result = CollectionUtils.distinct(list)
        assertEquals(listOf(1, 2, 3), result)
    }
    
    @Test
    fun `distinctBy removes duplicates by key`() {
        data class Person(val name: String, val age: Int)
        val list = listOf(
            Person("Alice", 25),
            Person("Bob", 30),
            Person("Alice", 35)
        )
        val result = CollectionUtils.distinctBy(list) { it.name }
        assertEquals(2, result.size)
        assertEquals("Alice", result[0].name)
        assertEquals("Bob", result[1].name)
    }
    
    // ─────────────────────────────────────────────────────────────
    // 过滤测试
    // ─────────────────────────────────────────────────────────────
    
    @Test
    fun `filterNotNull removes nulls`() {
        val list = listOf("a", null, "b", null, "c")
        val result = CollectionUtils.filterNotNull(list)
        assertEquals(listOf("a", "b", "c"), result)
    }
    
    // ─────────────────────────────────────────────────────────────
    // 连接测试
    // ─────────────────────────────────────────────────────────────
    
    @Test
    fun `joinToString joins with separator`() {
        val list = listOf("a", "b", "c")
        val result = CollectionUtils.joinToString(list, ", ")
        assertEquals("a, b, c", result)
    }
    
    @Test
    fun `joinToString with prefix and postfix`() {
        val list = listOf("a", "b", "c")
        val result = CollectionUtils.joinToString(list, ", ", "[", "]")
        assertEquals("[a, b, c]", result)
    }
    
    @Test
    fun `joinToString with transform`() {
        val list = listOf(1, 2, 3)
        val result = CollectionUtils.joinToString(list, "-", transform = { "#$it" })
        assertEquals("#1-#2-#3", result)
    }
    
    @Test
    fun `joinToString returns empty for null`() {
        val result = CollectionUtils.joinToString(null)
        assertEquals("", result)
    }
}