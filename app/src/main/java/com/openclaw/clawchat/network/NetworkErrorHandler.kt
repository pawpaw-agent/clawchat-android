package com.openclaw.clawchat.network

import android.util.Log
import com.openclaw.clawchat.util.AppLog
import kotlinx.coroutines.delay
import com.openclaw.clawchat.util.AppLog
import java.io.IOException
import com.openclaw.clawchat.util.AppLog
import java.net.SocketTimeoutException
import com.openclaw.clawchat.util.AppLog
import java.net.UnknownHostException
import com.openclaw.clawchat.util.AppLog

/**
 * 网络错误类型
 */
sealed class NetworkError {
    /** 超时 */
    data object Timeout : NetworkError()
    
    /** 无网络连接 */
    data object NoConnection : NetworkError()
    
    /** 未授权 */
    data object Unauthorized : NetworkError()
    
    /** 服务器错误 */
    data object ServerError : NetworkError()
    
    /** WebSocket 连接关闭 */
    data class ConnectionClosed(val code: Int, val reason: String) : NetworkError()
    
    /** 未知错误 */
    data class Unknown(val throwable: Throwable) : NetworkError()
    
    /** 获取错误描述 */
    fun description(): String {
        return when (this) {
            is Timeout -> "请求超时"
            is NoConnection -> "无网络连接"
            is Unauthorized -> "认证失败，请检查设备配对"
            is ServerError -> "服务器错误"
            is ConnectionClosed -> "连接已关闭：$code - $reason"
            is Unknown -> "未知错误：${throwable.message}"
        }
    }
}

/**
 * 将异常转换为网络错误
 */
fun Throwable.toNetworkError(): NetworkError {
    return when (this) {
        is SocketTimeoutException -> {
            Log.w("NetworkError", "Timeout: ${message}")
            NetworkError.Timeout
        }
        is UnknownHostException -> {
            Log.w("NetworkError", "No connection: ${message}")
            NetworkError.NoConnection
        }
        is IOException -> {
            Log.w("NetworkError", "IO error: ${message}")
            NetworkError.NoConnection
        }
        else -> {
            // 其他错误
            Log.e("NetworkError", "Unknown error: ${message}", this)
            NetworkError.Unknown(this)
        }
    }
}

/**
 * 带指数退避的重试函数
 * 
 * @param maxRetries 最大重试次数
 * @param initialDelay 初始延迟（毫秒）
 * @param maxDelay 最大延迟（毫秒）
 * @param factor 退避因子
 * @param block 要执行的操作
 * @return 操作结果
 */
suspend fun <T> retryWithBackoff(
    maxRetries: Int = 3,
    initialDelay: Long = 1000,
    maxDelay: Long = 10000,
    factor: Double = 2.0,
    shouldRetry: (Throwable) -> Boolean = { true },
    block: suspend () -> Result<T>
): Result<T> {
    var currentDelay = initialDelay
    var lastResult: Result<T>? = null
    var lastException: Throwable? = null
    
    repeat(maxRetries) { attempt ->
        try {
            val result = block()
            if (result.isSuccess) {
                return result
            }
            lastResult = result
            lastException = result.exceptionOrNull()
        } catch (e: Exception) {
            lastException = e
        }
        
        // 检查是否应该重试
        val exception = lastException
        if (exception != null && !shouldRetry(exception)) {
            AppLog.d("RetryWithBackoff", "Not retrying: $exception")
            return Result.failure(exception)
        }
        
        // 如果不是最后一次尝试，则等待后重试
        if (attempt < maxRetries - 1) {
            AppLog.d("RetryWithBackoff", "Retry attempt ${attempt + 1}/${maxRetries} after ${currentDelay}ms")
            delay(currentDelay)
            currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelay)
        }
    }
    
    // 返回最后一次结果或异常
    return lastResult ?: Result.failure(lastException ?: Exception("Unknown error"))
}

/**
 * 检查错误是否可恢复（可重试）
 */
fun NetworkError.isRecoverable(): Boolean {
    return when (this) {
        is NetworkError.Timeout -> true
        is NetworkError.NoConnection -> true
        is NetworkError.ServerError -> true
        is NetworkError.ConnectionClosed -> code >= 1000 && code < 1002 // 正常关闭不可恢复
        is NetworkError.Unauthorized -> false // 认证错误需要重新配对
        is NetworkError.Unknown -> {
            // 某些未知错误可能可恢复
            throwable !is java.security.cert.CertificateException
        }
    }
}

/**
 * 网络错误扩展：转换为人类可读的消息
 */
fun NetworkError.toUserMessage(): String {
    return when (this) {
        is NetworkError.Timeout -> "连接超时，请检查网络后重试"
        is NetworkError.NoConnection -> "无法连接到网络，请检查网络连接"
        is NetworkError.Unauthorized -> "设备未授权，请重新配对设备"
        is NetworkError.ServerError -> "服务器暂时不可用，请稍后重试"
        is NetworkError.ConnectionClosed -> "连接已断开：$reason"
        is NetworkError.Unknown -> "发生错误：${throwable.message ?: "未知错误"}"
    }
}
