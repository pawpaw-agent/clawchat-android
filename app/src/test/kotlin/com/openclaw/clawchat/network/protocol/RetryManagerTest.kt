package com.openclaw.clawchat.network.protocol

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/**
 * RetryManager 单元测试
 * 
 * 测试重试逻辑、指数退避、错误判断
 */
class RetryManagerTest {
    
    // ==================== 基本重试测试 ====================
    
    @Test
    fun testRetrySuccessOnFirstAttempt() = runBlocking {
        val retryManager = RetryManager(maxRetries = 3)
        var attempts = 0
        
        val result = retryManager.executeWithRetry({
            attempts++
            "success"
        })
        
        assertTrue("应该成功", result is RetryManager.RetryResult.Success)
        assertEquals("应该只尝试 1 次", 1, attempts)
        assertEquals("结果应该是 success", "success", (result as RetryManager.RetryResult.Success).value)
        assertEquals("尝试次数应该是 1", 1, result.attempts)
    }
    
    @Test
    fun testRetrySuccessAfterFailures() = runBlocking {
        val retryManager = RetryManager(
            initialDelayMs = 10L, // 快速重试用于测试
            maxRetries = 3
        )
        var attempts = 0
        
        val result = retryManager.executeWithRetry({
            attempts++
            if (attempts < 3) {
                throw Exception("Temporary failure $attempts")
            }
            "success"
        })
        
        assertTrue("应该成功", result is RetryManager.RetryResult.Success)
        assertEquals("应该尝试 3 次", 3, attempts)
    }
    
    @Test
    fun testRetryExhausted() = runBlocking {
        val retryManager = RetryManager(
            initialDelayMs = 10L,
            maxRetries = 2
        )
        var attempts = 0
        
        val result = retryManager.executeWithRetry({
            attempts++
            throw Exception("Always fails")
        })
        
        assertTrue("应该失败", result is RetryManager.RetryResult.Failure)
        assertEquals("应该尝试 3 次 (1 + 2 retries)", 3, attempts)
    }
    
    // ==================== 条件重试测试 ====================
    
    @Test
    fun testRetryWithCondition() = runBlocking {
        val retryManager = RetryManager(
            initialDelayMs = 10L,
            maxRetries = 3
        )
        var attempts = 0
        
        // 只在特定条件下重试
        val result = retryManager.executeWithRetry(
            operation = {
                attempts++
                throw NonRetryableException("Non-retryable error")
            },
            shouldRetry = { it !is NonRetryableException }
        )
        
        assertTrue("应该失败", result is RetryManager.RetryResult.Failure)
        assertEquals("应该只尝试 1 次 (不重试)", 1, attempts)
    }
    
    class NonRetryableException(message: String) : Exception(message)
    
    // ==================== 指数退避测试 ====================
    
    @Test
    fun testExponentialBackoff() = runBlocking {
        val retryManager = RetryManager(
            initialDelayMs = 10L,
            maxDelayMs = 100L,
            backoffFactor = 2.0,
            maxRetries = 3
        )
        
        val delays = mutableListOf<Long>()
        var lastTime = System.currentTimeMillis()
        
        retryManager.executeWithRetry(
            operation = {
                val now = System.currentTimeMillis()
                if (delays.isNotEmpty()) {
                    delays.add(now - lastTime)
                }
                lastTime = now
                throw Exception("Fail")
            },
            maxRetries = 3
        )
        
        // 验证延迟递增
        assertEquals("应该有 3 次延迟", 3, delays.size)
        
        // 验证指数增长（允许一定误差）
        if (delays.size >= 2) {
            assertTrue("延迟应该递增", delays[1] >= delays[0] * 1.5)
        }
    }
    
    // ==================== 统计测试 ====================
    
    @Test
    fun testRetryStats() = runBlocking {
        val retryManager = RetryManager(
            initialDelayMs = 10L,
            maxRetries = 2
        )
        
        // 成功的操作
        retryManager.executeWithRetry { "success" }
        
        // 失败的操作
        retryManager.executeWithRetry { throw Exception("Fail") }
        
        // 重试后成功
        var attempts = 0
        retryManager.executeWithRetry {
            attempts++
            if (attempts < 2) throw Exception("Temporary")
            "success"
        }
        
        val stats = retryManager.getStats()
        
        assertTrue("总重试数应该 > 0", stats.totalRetries > 0)
        assertTrue("总失败数应该 >= 1", stats.totalFailures >= 1)
    }
    
    @Test
    fun testResetStats() = runBlocking {
        val retryManager = RetryManager(maxRetries = 1)
        
        // 执行一些操作
        retryManager.executeWithRetry { throw Exception("Fail") }
        
        // 重置统计
        retryManager.resetStats()
        
        val stats = retryManager.getStats()
        
        assertEquals("重试数应该重置为 0", 0, stats.totalRetries)
        assertEquals("失败数应该重置为 0", 0, stats.totalFailures)
    }
    
    // ==================== 监听器测试 ====================
    
    @Test
    fun testRetryListener() = runBlocking {
        val retryManager = RetryManager(
            initialDelayMs = 10L,
            maxRetries = 2
        )
        
        val retryEvents = mutableListOf<Triple<Int, Int, Long>>()
        var successCount = 0
        var failureCount = 0
        
        retryManager.addListener(object : RetryManager.RetryListener {
            override fun onRetry(attempt: Int, maxRetries: Int, delay: Long, error: Throwable) {
                retryEvents.add(Triple(attempt, maxRetries, delay))
            }
            
            override fun onSuccess(attempts: Int) {
                successCount++
            }
            
            override fun onFailure(attempts: Int, error: Throwable) {
                failureCount++
            }
        })
        
        // 执行会重试的操作
        var attempts = 0
        retryManager.executeWithRetry({
            attempts++
            if (attempts < 2) throw Exception("Temporary")
            "success"
        })
        
        assertTrue("应该有重试事件", retryEvents.isNotEmpty())
        assertEquals("应该有 1 次成功", 1, successCount)
        assertEquals("应该没有失败", 0, failureCount)
    }
    
    // ==================== 可重试错误测试 ====================
    
    @Test
    fun testRetryableErrorDetection() {
        // 可重试的错误
        assertTrue(java.net.SocketTimeoutException().isRetryable())
        assertTrue(java.net.SocketException().isRetryable())
        assertTrue(java.net.UnknownHostException().isRetryable())
        assertTrue(java.io.IOException().isRetryable())
        
        // 不可重试的错误
        assertFalse(RetryableError.ClientError("Client error").isRetryable())
    }
    
    @Test
    fun testRetryableErrorTypes() {
        val networkError = RetryableError.NetworkError("Network error")
        val timeoutError = RetryableError.TimeoutError("Timeout")
        val serverError = RetryableError.ServerError("Server error")
        val clientError = RetryableError.ClientError("Client error")
        
        assertTrue("NetworkError 应该可重试", networkError.recoverable)
        assertTrue("TimeoutError 应该可重试", timeoutError.recoverable)
        assertTrue("ServerError 应该可重试", serverError.recoverable)
        assertFalse("ClientError 不应该可重试", clientError.recoverable)
    }
    
    // ==================== 重试配置测试 ====================
    
    @Test
    fun testRetryConfigBuilder() {
        val config = retryConfig {
            initialDelayMs = 500L
            maxDelayMs = 10000L
            maxRetries = 5
        }
        
        assertEquals(500L, config.initialDelayMs)
        assertEquals(10000L, config.maxDelayMs)
        assertEquals(5, config.maxRetries)
    }
    
    @Test
    fun testPredefinedRetryConfigs() {
        // 快速配置
        val quick = RetryConfigs.QUICK
        assertEquals(500L, quick.initialDelayMs)
        assertEquals(2, quick.maxRetries)
        
        // 标准配置
        val standard = RetryConfigs.STANDARD
        assertEquals(1000L, standard.initialDelayMs)
        assertEquals(3, standard.maxRetries)
        
        // 激进配置
        val aggressive = RetryConfigs.AGGRESSIVE
        assertEquals(2000L, aggressive.initialDelayMs)
        assertEquals(5, aggressive.maxRetries)
    }
    
    // ==================== Result API 测试 ====================
    
    @Test
    fun testExecuteWithRetryResult() = runBlocking {
        val retryManager = RetryManager(
            initialDelayMs = 10L,
            maxRetries = 2
        )
        
        // 成功的 Result
        var attempts = 0
        val successResult = retryManager.executeWithRetryResult({
            attempts++
            Result.success("success")
        })
        
        assertTrue("应该成功", successResult.isSuccess)
        assertEquals("结果应该是 success", "success", successResult.getOrNull())
        
        // 失败的 Result
        attempts = 0
        val failureResult = retryManager.executeWithRetryResult({
            attempts++
            Result.failure<Unit>(Exception("Fail"))
        }, maxRetries = 1)
        
        assertTrue("应该失败", failureResult.isFailure)
    }
}
