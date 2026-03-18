package com.openclaw.clawchat.network.protocol

import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 重试管理器
 * 
 * 实现智能重试逻辑：
 * 1. 指数退避
 * 2. 最大重试次数
 * 3. 可重试错误判断
 * 4. 重试统计
 * 
 * 使用示例:
 * ```kotlin
 * val retryManager = RetryManager()
 * 
 * // 执行可重试的操作
 * val result = retryManager.executeWithRetry(
 *     operation = { sendMessage(message) },
 *     shouldRetry = { error -> error.isRecoverable },
 *     maxRetries = 3
 * )
 * ```
 */
class RetryManager(
    private val initialDelayMs: Long = 1000L,
    private val maxDelayMs: Long = 30000L,
    private val backoffFactor: Double = 2.0,
    private val maxRetries: Int = 3
) {
    companion object {
        private const val TAG = "RetryManager"
    }
    
    // 重试统计
    private var totalRetries = 0
    private var totalFailures = 0
    private val mutex = Mutex()
    
    // 监听器
    private val listeners = mutableListOf<RetryListener>()
    
    /**
     * 重试结果
     */
    sealed class RetryResult<T> {
        data class Success<T>(val value: T, val attempts: Int) : RetryResult<T>()
        data class Failure<T>(val error: Throwable, val attempts: Int) : RetryResult<T>()
    }
    
    /**
     * 重试监听器
     */
    interface RetryListener {
        fun onRetry(attempt: Int, maxRetries: Int, delay: Long, error: Throwable)
        fun onSuccess(attempts: Int)
        fun onFailure(attempts: Int, error: Throwable)
    }
    
    /**
     * 执行带重试的操作
     * 
     * @param operation 要执行的操作
     * @param shouldRetry 判断是否应该重试
     * @param maxRetries 最大重试次数（覆盖默认值）
     * @return 重试结果
     */
    suspend fun <T> executeWithRetry(
        operation: suspend () -> T,
        shouldRetry: (Throwable) -> Boolean = { true },
        maxRetries: Int = this.maxRetries
    ): RetryResult<T> {
        var lastError: Throwable? = null
        var delayMs = initialDelayMs
        
        for (attempt in 1..maxRetries + 1) {
            try {
                // 执行操作
                val result = operation()
                
                // 成功
                mutex.withLock {
                    totalRetries += (attempt - 1)
                }
                listeners.forEach { it.onSuccess(attempt) }
                
                return RetryResult.Success(result, attempt)
                
            } catch (e: Exception) {
                lastError = e
                
                // 判断是否应该重试
                if (attempt > maxRetries || !shouldRetry(e)) {
                    Log.e(TAG, "操作失败：${e.message} (尝试 $attempt/${maxRetries + 1})")
                    
                    mutex.withLock {
                        totalFailures++
                    }
                    listeners.forEach { it.onFailure(attempt, e) }
                    
                    return RetryResult.Failure(e, attempt)
                }
                
                // 等待后重试
                Log.w(TAG, "重试操作：${e.message} (尝试 $attempt/${maxRetries + 1}, 延迟 ${delayMs}ms)")
                listeners.forEach { it.onRetry(attempt, maxRetries, delayMs, e) }
                
                delay(delayMs)
                
                // 指数退避
                delayMs = (delayMs * backoffFactor).toLong().coerceAtMost(maxDelayMs)
            }
        }
        
        // 不应该到这里，但为了编译通过
        return RetryResult.Failure(lastError ?: Exception("Unknown error"), maxRetries + 1)
    }
    
    /**
     * 执行带重试的 suspend 函数（返回 Result）
     */
    suspend fun <T> executeWithRetryResult(
        operation: suspend () -> Result<T>,
        shouldRetry: (Throwable) -> Boolean = { true },
        maxRetries: Int = this.maxRetries
    ): Result<T> {
        val result = executeWithRetry(operation, shouldRetry, maxRetries)
        
        return when (result) {
            is RetryResult.Success -> result.value // Already a Result<T>
            is RetryResult.Failure -> Result.failure(result.error)
        }
    }
    
    /**
     * 获取重试统计
     */
    suspend fun getStats(): RetryStats = mutex.withLock {
        RetryStats(
            totalRetries = totalRetries,
            totalFailures = totalFailures
        )
    }
    
    /**
     * 重置统计
     */
    suspend fun resetStats() = mutex.withLock {
        totalRetries = 0
        totalFailures = 0
    }
    
    /**
     * 添加监听器
     */
    fun addListener(listener: RetryListener) {
        listeners.add(listener)
    }
    
    /**
     * 移除监听器
     */
    fun removeListener(listener: RetryListener) {
        listeners.remove(listener)
    }
    
    /**
     * 重试统计
     */
    data class RetryStats(
        val totalRetries: Int,
        val totalFailures: Int
    ) {
        val successRate: Double
            get() = if (totalRetries + totalFailures == 0) 1.0 
                    else totalFailures.toDouble() / (totalRetries + totalFailures)
    }
}

/**
 * 简单的重试监听器实现
 */
class SimpleRetryListener(
    private val onRetryCallback: (Int, Int, Long, Throwable) -> Unit = { _, _, _, _ -> },
    private val onSuccessCallback: (Int) -> Unit = {},
    private val onFailureCallback: (Int, Throwable) -> Unit = { _, _ -> }
) : RetryManager.RetryListener {
    override fun onRetry(attempt: Int, maxRetries: Int, delay: Long, error: Throwable) {
        onRetryCallback(attempt, maxRetries, delay, error)
    }
    
    override fun onSuccess(attempts: Int) {
        onSuccessCallback(attempts)
    }
    
    override fun onFailure(attempts: Int, error: Throwable) {
        onFailureCallback(attempts, error)
    }
}

/**
 * 可重试的错误
 */
sealed class RetryableError(message: String, val recoverable: Boolean) : Exception(message) {
    class NetworkError(message: String, recoverable: Boolean = true) : RetryableError(message, recoverable)
    class TimeoutError(message: String, recoverable: Boolean = true) : RetryableError(message, recoverable)
    class ServerError(message: String, recoverable: Boolean = true) : RetryableError(message, recoverable)
    class ClientError(message: String) : RetryableError(message, false)
}

/**
 * 判断错误是否可重试
 */
fun Throwable.isRetryable(): Boolean {
    return when (this) {
        is RetryableError -> recoverable
        is java.net.SocketTimeoutException -> true
        is java.net.SocketException -> true
        is java.net.UnknownHostException -> true
        is java.io.IOException -> true
        is kotlinx.coroutines.TimeoutCancellationException -> true
        else -> false
    }
}

/**
 * 创建重试配置
 */
class RetryConfig(
    val initialDelayMs: Long = 1000L,
    val maxDelayMs: Long = 30000L,
    val backoffFactor: Double = 2.0,
    val maxRetries: Int = 3,
    val shouldRetry: (Throwable) -> Boolean = { it.isRetryable() }
) {
    fun toRetryManager(): RetryManager {
        return RetryManager(
            initialDelayMs = initialDelayMs,
            maxDelayMs = maxDelayMs,
            backoffFactor = backoffFactor,
            maxRetries = maxRetries
        )
    }
}

/**
 * 重试配置构建器
 */
class RetryConfigBuilder {
    var initialDelayMs: Long = 1000L
    var maxDelayMs: Long = 30000L
    var backoffFactor: Double = 2.0
    var maxRetries: Int = 3
    var shouldRetry: (Throwable) -> Boolean = { it.isRetryable() }
    
    fun build(): RetryConfig {
        return RetryConfig(
            initialDelayMs = initialDelayMs,
            maxDelayMs = maxDelayMs,
            backoffFactor = backoffFactor,
            maxRetries = maxRetries,
            shouldRetry = shouldRetry
        )
    }
}

/**
 * 创建重试配置
 */
fun retryConfig(block: RetryConfigBuilder.() -> Unit): RetryConfig {
    val builder = RetryConfigBuilder()
    builder.block()
    return builder.build()
}

/**
 * 常用重试配置
 */
object RetryConfigs {
    /** 快速重试（用于轻量操作） */
    val QUICK = retryConfig {
        initialDelayMs = 500L
        maxDelayMs = 5000L
        maxRetries = 2
    }
    
    /** 标准重试（默认配置） */
    val STANDARD = retryConfig {
        initialDelayMs = 1000L
        maxDelayMs = 30000L
        maxRetries = 3
    }
    
    /** 激进重试（用于重要操作） */
    val AGGRESSIVE = retryConfig {
        initialDelayMs = 2000L
        maxDelayMs = 60000L
        maxRetries = 5
    }
    
    /** 仅网络错误重试 */
    val NETWORK_ONLY = retryConfig {
        initialDelayMs = 1000L
        maxDelayMs = 30000L
        maxRetries = 3
        shouldRetry = { it is java.net.SocketTimeoutException || it is java.net.SocketException }
    }
}
