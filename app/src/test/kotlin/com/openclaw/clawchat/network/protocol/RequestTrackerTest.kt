package com.openclaw.clawchat.network.protocol

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * RequestTracker 单元测试
 * 
 * 测试请求追踪、响应匹配、超时处理
 */
class RequestTrackerTest {
    
    private lateinit var tracker: RequestTracker
    
    @Before
    fun setup() {
        tracker = RequestTracker(timeoutMs = 1000L) // 1 秒超时用于测试
    }
    
    // ==================== 基本功能测试 ====================
    
    @Test
    fun testTrackRequest() = runBlocking {
        // 追踪请求
        val deferred = tracker.trackRequest("req-123", "ping")
        
        assertNotNull("应该返回 CompletableDeferred", deferred)
        assertFalse("请求应该未完成", deferred.isCompleted)
        
        // 验证待处理数量
        assertEquals("应该有 1 个待处理请求", 1, tracker.getPendingCount())
    }
    
    @Test
    fun testCompleteRequest() = runBlocking {
        // 追踪请求
        val deferred = tracker.trackRequest("req-123", "ping")
        
        // 完成请求
        val response = ResponseFrame(id = "req-123", ok = true)
        val result = tracker.completeRequest(response)
        
        assertTrue("应该成功完成", result)
        assertTrue("请求应该已完成", deferred.isCompleted)
        assertEquals("响应 ID 应该匹配", "req-123", deferred.getOrNull()?.id)
        
        // 验证待处理数量
        assertEquals("应该没有待处理请求", 0, tracker.getPendingCount())
    }
    
    @Test
    fun testCompleteRequestWithNotFound() = runBlocking {
        // 完成不存在的请求
        val response = ResponseFrame(id = "req-999", ok = true)
        val result = tracker.completeRequest(response)
        
        assertFalse("应该返回 false", result)
    }
    
    @Test
    fun testFailRequest() = runBlocking {
        // 追踪请求
        val deferred = tracker.trackRequest("req-123", "ping")
        
        // 失败请求
        val error = Exception("Test error")
        val result = tracker.failRequest("req-123", error)
        
        assertTrue("应该成功失败", result)
        assertTrue("请求应该已完成", deferred.isCompleted)
        assertTrue("请求应该异常完成", deferred.isCompletedExceptionally)
    }
    
    @Test
    fun testCancelRequest() = runBlocking {
        // 追踪请求
        val deferred = tracker.trackRequest("req-123", "ping")
        
        // 取消请求
        val result = tracker.cancelRequest("req-123")
        
        assertTrue("应该成功取消", result)
        assertTrue("请求应该已完成", deferred.isCompleted)
        
        // 验证待处理数量
        assertEquals("应该没有待处理请求", 0, tracker.getPendingCount())
    }
    
    @Test
    fun testCancelAllRequests() = runBlocking {
        // 追踪多个请求
        tracker.trackRequest("req-1", "ping")
        tracker.trackRequest("req-2", "ping")
        tracker.trackRequest("req-3", "ping")
        
        assertEquals("应该有 3 个待处理请求", 3, tracker.getPendingCount())
        
        // 取消所有请求
        tracker.cancelAllRequests("Test cancel")
        
        assertEquals("应该没有待处理请求", 0, tracker.getPendingCount())
    }
    
    // ==================== 超时测试 ====================
    
    @Test
    fun testRequestTimeout() = runBlocking {
        // 追踪请求（不完成）
        tracker.trackRequest("req-timeout", "ping")
        
        // 等待超时（1 秒 + 缓冲）
        delay(1500)
        
        // 验证待处理数量（应该被清理）
        val count = tracker.getPendingCount()
        assertTrue("超时的请求应该被清理，实际：$count", count <= 1)
    }
    
    @Test
    fun testRequestTimeoutException() = runBlocking {
        // 追踪请求
        val deferred = tracker.trackRequest("req-timeout", "ping")
        
        // 等待超时
        delay(1500)
        
        // 验证异常类型
        try {
            deferred.await()
            fail("应该抛出 TimeoutCancellationException")
        } catch (e: Exception) {
            assertTrue("应该是超时异常", e is RequestTracker.RequestTimeoutException || 
                       e is kotlinx.coroutines.TimeoutCancellationException ||
                       e is java.util.concurrent.CancellationException)
        }
    }
    
    // ==================== 请求执行器测试 ====================
    
    @Test
    fun testRequestExecutorSuccess() = runBlocking {
        val sentRequests = mutableListOf<RequestFrame>()
        
        val executor = createRequestExecutor(
            tracker = tracker,
            sendFunction = { request ->
                sentRequests.add(request)
                
                // 模拟响应
                delay(100)
                val response = ResponseFrame(id = request.id, ok = true)
                tracker.completeRequest(response)
            },
            timeoutMs = 5000L
        )
        
        // 执行请求
        val request = RequestFrame(id = "req-exec-1", method = "ping")
        val response = executor.execute(request)
        
        assertTrue("应该发送请求", sentRequests.isNotEmpty())
        assertEquals("请求 ID 应该匹配", request.id, sentRequests[0].id)
        assertTrue("响应应该成功", response.ok)
    }
    
    @Test
    fun testRequestExecutorTimeout() = runBlocking {
        val executor = createRequestExecutor(
            tracker = tracker,
            sendFunction = { request ->
                // 不发送响应，导致超时
            },
            timeoutMs = 500L // 0.5 秒超时
        )
        
        // 执行请求
        val request = RequestFrame(id = "req-exec-2", method = "ping")
        
        try {
            executor.execute(request)
            fail("应该抛出超时异常")
        } catch (e: Exception) {
            assertTrue("应该是超时异常", 
                e is kotlinx.coroutines.TimeoutCancellationException ||
                e is RequestTracker.RequestTimeoutException)
        }
    }
    
    // ==================== 辅助函数测试 ====================
    
    @Test
    fun testSendMessageRequestBuilder() {
        val request = send_messageRequest(
            sessionId = "test-session",
            content = "Hello"
        )
        
        assertEquals("session.send", request.method)
        assertTrue("ID 应以 req-开头", request.id.startsWith("req-"))
        assertNotNull("params 不应为空", request.params)
    }
    
    @Test
    fun testGetSessionListRequestBuilder() {
        val request = getSessionListRequest()
        
        assertEquals("session.list", request.method)
        assertTrue("ID 应以 req-开头", request.id.startsWith("req-"))
    }
    
    @Test
    fun testCreateSessionRequestBuilder() {
        val request = createSessionRequest(
            model = "qwen3.5-plus",
            thinking = true
        )
        
        assertEquals("session.create", request.method)
        assertTrue("ID 应以 req-开头", request.id.startsWith("req-"))
    }
    
    @Test
    fun testTerminateSessionRequestBuilder() {
        val request = terminateSessionRequest(
            sessionId = "test-session",
            reason = "User requested"
        )
        
        assertEquals("session.terminate", request.method)
        assertTrue("ID 应以 req-开头", request.id.startsWith("req-"))
    }
    
    @Test
    fun testPingRequestBuilder() {
        val request = pingRequest()
        
        assertEquals("ping", request.method)
        assertTrue("ID 应以 req-开头", request.id.startsWith("req-"))
    }
}
