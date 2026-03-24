package com.openclaw.clawchat.network.protocol

import android.util.Log
import com.openclaw.clawchat.util.AppLog
import kotlinx.coroutines.CompletableDeferred
import com.openclaw.clawchat.util.AppLog
import kotlinx.coroutines.CoroutineScope
import com.openclaw.clawchat.util.AppLog
import kotlinx.coroutines.Dispatchers
import com.openclaw.clawchat.util.AppLog
import kotlinx.coroutines.delay
import com.openclaw.clawchat.util.AppLog
import kotlinx.coroutines.launch
import com.openclaw.clawchat.util.AppLog
import kotlinx.coroutines.sync.Mutex
import com.openclaw.clawchat.util.AppLog
import kotlinx.coroutines.sync.withLock
import com.openclaw.clawchat.util.AppLog
import kotlinx.serialization.json.JsonElement
import com.openclaw.clawchat.util.AppLog

/**
 * 请求追踪器
 * 
 * 管理请求 - 响应匹配：
 * 1. 发送请求时创建 CompletableDeferred
 * 2. 收到响应时根据 ID 匹配并完成 Deferred
 * 3. 超时未收到响应则取消
 * 
 * 使用示例:
 * ```kotlin
 * val tracker = RequestTracker()
 * 
 * // 发送请求
 * val request = RequestFrame(id = "req-123", method = "ping")
 * val responseDeferred = tracker.trackRequest(request.id)
 * 
 * // 发送请求到服务器...
 * 
 * // 收到响应
 * val response = ResponseFrame(id = "req-123", ok = true, ...)
 * tracker.completeRequest(response)
 * 
 * // 等待响应
 * val result = responseDeferred.await()
 * ```
 */
class RequestTracker(
    private val timeoutMs: Long = 30000L,
    private val scope: CoroutineScope? = null
) {
    companion object {
        private const val TAG = "RequestTracker"
        private const val CLEANUP_INTERVAL_MS = 60000L
    }
    
    // 待处理的请求
    private val pendingRequests = mutableMapOf<String, PendingRequest>()
    private val mutex = Mutex()
    
    // 清理任务
    private var cleanupJob: kotlinx.coroutines.Job? = null
    
    /**
     * 待处理的请求
     */
    data class PendingRequest(
        val id: String,
        val method: String,
        val deferred: CompletableDeferred<ResponseFrame>,
        val createdAt: Long = System.currentTimeMillis()
    ) {
        val isExpired: Boolean
            get() = System.currentTimeMillis() - createdAt > 30000L
    }
    
    /**
     * 请求结果
     */
    sealed class RequestResult {
        data class Success(val response: ResponseFrame) : RequestResult()
        data class Error(val code: String, val message: String) : RequestResult()
        data class Timeout(val requestId: String) : RequestResult()
    }
    
    init {
        // Only start cleanup if an external scope is provided (avoids leaked coroutine)
        if (scope != null) {
            startCleanupTask(scope)
        }
    }
    
    /**
     * 启动定期清理任务（使用外部提供的 CoroutineScope）
     */
    private fun startCleanupTask(cleanupScope: CoroutineScope) {
        cleanupJob = cleanupScope.launch {
            while (true) {
                delay(CLEANUP_INTERVAL_MS)
                cleanupExpiredRequests()
            }
        }
    }
    
    /**
     * 清理超时的请求
     */
    private suspend fun cleanupExpiredRequests() = mutex.withLock {
        val now = System.currentTimeMillis()
        val expired = pendingRequests.entries.filter { 
            now - it.value.createdAt > timeoutMs 
        }
        
        for ((id, request) in expired) {
            Log.w(TAG, "请求超时：$id (${request.method})")
            request.deferred.cancel(
                RequestTimeoutException("Request timeout: ${request.method}", request.id)
            )
            pendingRequests.remove(id)
        }
        
        if (expired.isNotEmpty()) {
            AppLog.d(TAG, "清理了 ${expired.size} 个超时请求")
        }
    }
    
    /**
     * 追踪请求
     * 
     * @param requestId 请求 ID
     * @param method 方法名
     * @return CompletableDeferred 用于等待响应
     */
    suspend fun trackRequest(
        requestId: String,
        method: String
    ): CompletableDeferred<ResponseFrame> = mutex.withLock {
        val pendingRequest = PendingRequest(
            id = requestId,
            method = method,
            deferred = CompletableDeferred()
        )
        
        pendingRequests[requestId] = pendingRequest
        AppLog.d(TAG, "追踪请求：$requestId ($method)")
        
        pendingRequest.deferred
    }
    
    /**
     * 完成请求
     * 
     * @param response 响应帧
     * @return true 如果成功匹配，false 如果找不到对应的请求
     */
    suspend fun completeRequest(response: ResponseFrame): Boolean = mutex.withLock {
        val pendingRequest = pendingRequests.remove(response.id)
        
        if (pendingRequest != null) {
            if (!pendingRequest.deferred.isCompleted) {
                pendingRequest.deferred.complete(response)
                AppLog.d(TAG, "请求完成：${response.id} (ok=${response.ok})")
            }
            true
        } else {
            Log.w(TAG, "未找到对应的请求：${response.id}")
            false
        }
    }
    
    /**
     * 失败请求
     * 
     * @param requestId 请求 ID
     * @param error 错误信息
     * @return true 如果成功匹配，false 如果找不到对应的请求
     */
    suspend fun failRequest(
        requestId: String,
        error: Throwable
    ): Boolean = mutex.withLock {
        val pendingRequest = pendingRequests.remove(requestId)
        
        if (pendingRequest != null) {
            if (!pendingRequest.deferred.isCompleted) {
                pendingRequest.deferred.completeExceptionally(error)
                Log.e(TAG, "请求失败：$requestId - ${error.message}")
            }
            true
        } else {
            Log.w(TAG, "未找到对应的请求：$requestId")
            false
        }
    }
    
    /**
     * 取消请求
     * 
     * @param requestId 请求 ID
     * @return true 如果成功取消，false 如果找不到对应的请求
     */
    suspend fun cancelRequest(requestId: String): Boolean = mutex.withLock {
        val pendingRequest = pendingRequests.remove(requestId)
        
        if (pendingRequest != null) {
            if (!pendingRequest.deferred.isCompleted) {
                pendingRequest.deferred.cancel(CancellationException("Request cancelled"))
                AppLog.d(TAG, "请求取消：$requestId")
            }
            true
        } else {
            false
        }
    }
    
    /**
     * 取消所有请求
     */
    suspend fun cancelAllRequests(reason: String = "Cancelled") = mutex.withLock {
        for ((id, request) in pendingRequests) {
            if (!request.deferred.isCompleted) {
                request.deferred.cancel(CancellationException(reason))
            }
        }
        
        val count = pendingRequests.size
        pendingRequests.clear()
        AppLog.d(TAG, "取消了 $count 个请求：$reason")
    }
    
    /**
     * 获取待处理请求数量
     */
    suspend fun getPendingCount(): Int = mutex.withLock {
        pendingRequests.size
    }
    
    /**
     * 获取待处理请求列表
     */
    suspend fun getPendingRequests(): List<PendingRequest> = mutex.withLock {
        pendingRequests.values.toList()
    }
    
    /**
     * 停止清理任务
     */
    fun stop() {
        cleanupJob?.cancel()
        cleanupJob = null
    }
    
    /**
     * 请求超时异常
     */
    class RequestTimeoutException(message: String, val requestId: String) : java.util.concurrent.CancellationException(message)
    
    /**
     * 取消异常
     */
    class CancellationException(message: String) : java.util.concurrent.CancellationException(message)
}

/**
 * 请求执行器
 * 
 * 简化请求 - 响应流程：
 * 1. 创建请求
 * 2. 追踪请求
 * 3. 发送请求
 * 4. 等待响应
 * 5. 解析响应
 */
class RequestExecutor(
    private val tracker: RequestTracker,
    private val sendFunction: suspend (RequestFrame) -> Unit,
    private val timeoutMs: Long = 30000L
) {
    /**
     * 执行请求并等待响应
     */
    suspend fun execute(request: RequestFrame): ResponseFrame {
        // 追踪请求
        val deferred = tracker.trackRequest(request.id, request.method)
        
        try {
            // 发送请求
            sendFunction(request)
            
            // 等待响应（带超时）
            return kotlinx.coroutines.withTimeout(timeoutMs) {
                deferred.await()
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            tracker.failRequest(request.id, RequestTracker.RequestTimeoutException("Request timeout", request.id))
            throw e
        } catch (e: Exception) {
            tracker.failRequest(request.id, e)
            throw e
        }
    }
    
    /**
     * 执行请求并解析响应载荷
     */
    suspend inline fun <reified T> executeAndParse(
        request: RequestFrame,
        deserializer: kotlinx.serialization.DeserializationStrategy<T>
    ): T? {
        val response = execute(request)
        
        if (!response.isSuccess()) {
            throw RequestException("Request failed: ${response.error?.code} - ${response.error?.message}")
        }
        
        return response.parsePayload(deserializer)
    }
    
    /**
     * 请求异常
     */
    class RequestException(message: String) : Exception(message)
}

/**
 * 创建请求执行器的辅助函数
 */
fun createRequestExecutor(
    tracker: RequestTracker,
    sendFunction: suspend (RequestFrame) -> Unit,
    timeoutMs: Long = 30000L
): RequestExecutor {
    return RequestExecutor(tracker, sendFunction, timeoutMs)
}
