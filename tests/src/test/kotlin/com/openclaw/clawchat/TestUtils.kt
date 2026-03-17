package com.openclaw.clawchat

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

/**
 * 测试工具类
 * 
 * 提供通用的测试辅助函数和常量
 */
object TestUtils {

    /**
     * 设置测试调度器
     * 在 @BeforeEach 中调用
     */
    fun setupTestDispatcher(): TestDispatcher {
        val testDispatcher = StandardTestDispatcher()
        Dispatchers.setMain(testDispatcher)
        return testDispatcher
    }

    /**
     * 清理测试调度器
     * 在 @AfterEach 中调用
     */
    fun cleanupTestDispatcher() {
        Dispatchers.resetMain()
    }

    /**
     * 常见的测试用网关 URL
     */
    object TestUrls {
        const val LOCAL_GATEWAY = "ws://localhost:8080"
        const val LOCAL_GATEWAY_SECURE = "wss://localhost:8443"
        const val REMOTE_GATEWAY = "wss://gateway.example.com"
        const val INVALID_URL = "ws://invalid-url-that-does-not-exist"
    }

    /**
     * 常见的测试用会话 ID
     */
    object TestSessionIds {
        const val SESSION_1 = "session_001"
        const val SESSION_2 = "session_002"
        const val SESSION_3 = "session_003"
        const val INVALID_SESSION = "invalid_session"
    }

    /**
     * 常见的测试用消息 ID
     */
    object TestMessageIds {
        const val MSG_1 = "msg_001"
        const val MSG_2 = "msg_002"
        const val MSG_3 = "msg_003"
    }

    /**
     * 常见的测试用挑战数据
     */
    object TestChallenges {
        const val CHALLENGE_STRING = "test_challenge_12345"
        val CHALLENGE_BYTES = "test_challenge_bytes".toByteArray()
        const val SERVER_NONCE = "server_nonce_abcdef"
    }

    /**
     * 常见的测试用密钥别名
     */
    object TestKeyAliases {
        const val DEFAULT_ALIAS = "clawchat_device_key"
        const val TEST_ALIAS = "test_clawchat_key"
        const val INVALID_ALIAS = "invalid_key_alias"
    }

    /**
     * 测试用时间戳（2024-01-01 00:00:00 UTC）
     */
    const val TEST_TIMESTAMP = 1704067200000L

    /**
     * 测试用延迟时间（毫秒）
     */
    const val TEST_DELAY_MS = 100L

    /**
     * 测试用超时时间（毫秒）
     */
    const val TEST_TIMEOUT_MS = 5000L
}

/**
 * 运行测试块，自动处理调度器设置和清理
 */
inline fun runTestWithDispatcher(crossinline block: suspend TestDispatcher.() -> Unit) {
    val testDispatcher = TestUtils.setupTestDispatcher()
    try {
        kotlinx.coroutines.test.runTest {
            block(testDispatcher)
        }
    } finally {
        TestUtils.cleanupTestDispatcher()
    }
}

/**
 * 断言辅助函数
 */
object AssertUtils {

    /**
     * 断言结果成功并返回值
     */
    inline fun <T> Result<T>.assertSuccess(): T {
        return getOrThrow()
    }

    /**
     * 断言结果失败并返回异常
     */
    inline fun <T> Result<T>.assertFailure(): Throwable {
        return exceptionOrNull()!!
    }

    /**
     * 断言集合大小
     */
    fun <T> Collection<T>.assertSize(expected: Int) {
        assert(size == expected) { "Expected size $expected but got $size" }
    }

    /**
     * 断言集合包含某个元素
     */
    fun <T> Collection<T>.assertContains(element: T) {
        assert(contains(element)) { "Collection does not contain expected element: $element" }
    }

    /**
     * 断言集合为空
     */
    fun <T> Collection<T>.assertEmpty() {
        assert(isEmpty()) { "Expected empty collection but got: $this" }
    }
}
