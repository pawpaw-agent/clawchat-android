package com.openclaw.clawchat.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * 协程测试规则
 * 提供 Main 调度器替换
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TestCoroutinesRule(
    private val testDispatcher: TestDispatcher = UnconfinedTestDispatcher()
) : TestWatcher() {
    
    override fun starting(description: Description) {
        Dispatchers.setMain(testDispatcher)
    }
    
    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
    
    /**
     * 在测试调度器上运行
     */
    fun runTest(block: suspend TestScope.() -> Unit) {
        kotlinx.coroutines.test.runTest(testDispatcher, block)
    }
    
    /**
     * 获取测试调度器
     */
    fun getTestDispatcher(): TestDispatcher = testDispatcher
    
    /**
     * 推进时间
     */
    fun advanceTimeBy(delayTimeMillis: Long) {
        testDispatcher.scheduler.advanceTimeBy(delayTimeMillis)
    }
    
    /**
     * 运行待处理任务
     */
    fun runCurrent() {
        testDispatcher.scheduler.runCurrent()
    }
}

/**
 * 测试调度器提供者
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TestDispatcherProvider(
    private val testDispatcher: TestDispatcher = UnconfinedTestDispatcher()
) {
    fun io() = testDispatcher
    fun main() = testDispatcher
    fun default() = testDispatcher
    fun unconfined() = testDispatcher
}