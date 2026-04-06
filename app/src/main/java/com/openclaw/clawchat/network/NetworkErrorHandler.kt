package com.openclaw.clawchat.network

import com.openclaw.clawchat.R
import com.openclaw.clawchat.util.AppLog
import com.openclaw.clawchat.util.StringResourceProvider
import kotlinx.coroutines.delay
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Network error types
 */
sealed class NetworkError {
    /** Timeout */
    data object Timeout : NetworkError()

    /** No network connection */
    data object NoConnection : NetworkError()

    /** Unauthorized */
    data object Unauthorized : NetworkError()

    /** Server error */
    data object ServerError : NetworkError()

    /** WebSocket connection closed */
    data class ConnectionClosed(val code: Int, val reason: String) : NetworkError()

    /** Unknown error */
    data class Unknown(val throwable: Throwable) : NetworkError()

    /**
     * Get error description (for logging/debugging)
     * Note: For user-facing messages, use toLocalizedDescription() or toLocalizedMessage()
     */
    fun description(): String {
        return when (this) {
            is Timeout -> "Request timed out"
            is NoConnection -> "No network connection"
            is Unauthorized -> "Authentication failed, please check device pairing"
            is ServerError -> "Server error"
            is ConnectionClosed -> "Connection closed: $code - $reason"
            is Unknown -> "Unknown error: ${throwable.message}"
        }
    }

    /**
     * Get localized description using StringResourceProvider
     */
    fun toLocalizedDescription(strings: StringResourceProvider): String {
        return when (this) {
            is Timeout -> strings.getString(R.string.network_error_description_timeout)
            is NoConnection -> strings.getString(R.string.network_error_description_no_connection)
            is Unauthorized -> strings.getString(R.string.network_error_description_unauthorized)
            is ServerError -> strings.getString(R.string.network_error_description_server)
            is ConnectionClosed -> strings.getString(R.string.network_error_description_connection_closed, code, reason)
            is Unknown -> strings.getString(R.string.network_error_description_unknown, throwable.message ?: "")
        }
    }

    /**
     * Get localized user-facing message using StringResourceProvider
     */
    fun toLocalizedMessage(strings: StringResourceProvider): String {
        return when (this) {
            is Timeout -> strings.getString(R.string.network_error_timeout)
            is NoConnection -> strings.getString(R.string.network_error_no_connection)
            is Unauthorized -> strings.getString(R.string.network_error_unauthorized)
            is ServerError -> strings.getString(R.string.network_error_server)
            is ConnectionClosed -> strings.getString(R.string.network_error_connection_closed, reason)
            is Unknown -> strings.getString(R.string.network_error_generic, throwable.message ?: strings.getString(R.string.error_unknown))
        }
    }
}

/**
 * Convert exception to network error
 */
fun Throwable.toNetworkError(): NetworkError {
    return when (this) {
        is SocketTimeoutException -> {
            AppLog.w("NetworkError", "Timeout: ${message}")
            NetworkError.Timeout
        }
        is UnknownHostException -> {
            AppLog.w("NetworkError", "No connection: ${message}")
            NetworkError.NoConnection
        }
        is IOException -> {
            AppLog.w("NetworkError", "IO error: ${message}")
            NetworkError.NoConnection
        }
        else -> {
            // Other errors
            AppLog.e("NetworkError", "Unknown error: ${message}", this)
            NetworkError.Unknown(this)
        }
    }
}

/**
 * Retry with exponential backoff
 *
 * @param maxRetries Maximum retry attempts
 * @param initialDelay Initial delay in milliseconds
 * @param maxDelay Maximum delay in milliseconds
 * @param factor Backoff factor
 * @param block Operation to execute
 * @return Operation result
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

        // Check if should retry
        val exception = lastException
        if (exception != null && !shouldRetry(exception)) {
            AppLog.d("RetryWithBackoff", "Not retrying: $exception")
            return Result.failure(exception)
        }

        // Wait before retry if not the last attempt
        if (attempt < maxRetries - 1) {
            AppLog.d("RetryWithBackoff", "Retry attempt ${attempt + 1}/${maxRetries} after ${currentDelay}ms")
            delay(currentDelay)
            currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelay)
        }
    }

    // Return last result or exception
    return lastResult ?: Result.failure(lastException ?: Exception("Unknown error"))
}

/**
 * Check if error is recoverable (can retry)
 */
fun NetworkError.isRecoverable(): Boolean {
    return when (this) {
        is NetworkError.Timeout -> true
        is NetworkError.NoConnection -> true
        is NetworkError.ServerError -> true
        is NetworkError.ConnectionClosed -> code >= 1000 && code < 1002 // Normal close not recoverable
        is NetworkError.Unauthorized -> false // Auth error requires re-pairing
        is NetworkError.Unknown -> {
            // Some unknown errors may be recoverable
            throwable !is java.security.cert.CertificateException
        }
    }
}
