package com.openclaw.clawchat.util

/**
 * 断言工具
 * 提供丰富的测试断言方法
 */
object AssertionUtils {
    
    /**
     * 断言条件为真
     */
    fun assertTrue(condition: Boolean, message: String = "Assertion failed") {
        if (!condition) {
            throw AssertionError(message)
        }
    }
    
    /**
     * 断言条件为假
     */
    fun assertFalse(condition: Boolean, message: String = "Expected false") {
        if (condition) {
            throw AssertionError(message)
        }
    }
    
    /**
     * 断言相等
     */
    fun <T> assertEquals(expected: T, actual: T, message: String = "") {
        if (expected != actual) {
            throw AssertionError("$message\nExpected: $expected\nActual: $actual")
        }
    }
    
    /**
     * 断言不相等
     */
    fun <T> assertNotEquals(expected: T, actual: T, message: String = "") {
        if (expected == actual) {
            throw AssertionError("$message\nExpected not equal to: $expected")
        }
    }
    
    /**
     * 断言为空
     */
    fun assertNull(value: Any?, message: String = "Expected null") {
        if (value != null) {
            throw AssertionError("$message, but was: $value")
        }
    }
    
    /**
     * 断言不为空
     */
    fun assertNotNull(value: Any?, message: String = "Expected non-null") {
        if (value == null) {
            throw AssertionError(message)
        }
    }
    
    /**
     * 断言字符串包含子串
     */
    fun assertContains(text: String, substring: String, message: String = "") {
        if (!text.contains(substring)) {
            throw AssertionError("$message\n'$text' does not contain '$substring'")
        }
    }
    
    /**
     * 断言集合为空
     */
    fun <T> assertEmpty(collection: Collection<T>, message: String = "") {
        if (collection.isNotEmpty()) {
            throw AssertionError("$message\nExpected empty, but has ${collection.size} elements")
        }
    }
    
    /**
     * 断言集合不为空
     */
    fun <T> assertNotEmpty(collection: Collection<T>, message: String = "") {
        if (collection.isEmpty()) {
            throw AssertionError("$message\nExpected non-empty collection")
        }
    }
    
    /**
     * 断言集合大小
     */
    fun <T> assertSize(expected: Int, collection: Collection<T>, message: String = "") {
        if (collection.size != expected) {
            throw AssertionError("$message\nExpected size $expected, but was ${collection.size}")
        }
    }
    
    /**
     * 断言抛出异常
     */
    inline fun <reified T : Throwable> assertThrows(block: () -> Unit): T {
        try {
            block()
            throw AssertionError("Expected ${T::class.simpleName} to be thrown")
        } catch (e: Throwable) {
            if (e !is T) {
                throw AssertionError("Expected ${T::class.simpleName}, but got ${e::class.simpleName}")
            }
            return e
        }
    }
    
    /**
     * 断言在超时内完成
     */
    inline fun assertCompletesWithin(timeoutMs: Long, block: () -> Unit) {
        val start = System.currentTimeMillis()
        block()
        val elapsed = System.currentTimeMillis() - start
        
        if (elapsed > timeoutMs) {
            throw AssertionError("Operation took ${elapsed}ms, expected < ${timeoutMs}ms")
        }
    }
    
    /**
     * 断言范围
     */
    fun assertInRange(value: Double, min: Double, max: Double, message: String = "") {
        if (value < min || value > max) {
            throw AssertionError("$message\n$value is not in range [$min, $max]")
        }
    }
}