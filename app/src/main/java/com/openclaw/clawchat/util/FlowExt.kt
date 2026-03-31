package com.openclaw.clawchat.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*

/**
 * Flow 扩展函数
 */

/**
 * 防抖
 * 在指定时间内只发送最后一个值
 */
@OptIn(FlowPreview::class)
fun <T> Flow<T>.debounce(timeoutMillis: Long): Flow<T> {
    return debounce(timeoutMillis)
}

/**
 * 节流
 * 在指定时间内只发送第一个值
 */
@OptIn(FlowPreview::class)
fun <T> Flow<T>.throttleFirst(timeoutMillis: Long): Flow<T> {
    return flow {
        var lastEmitTime = 0L
        collect { value ->
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastEmitTime >= timeoutMillis) {
                lastEmitTime = currentTime
                emit(value)
            }
        }
    }
}

/**
 * 重试
 */
fun <T> Flow<T>.retryWithDelay(
    retries: Int = 3,
    delayMillis: Long = 1000
): Flow<T> {
    return retryWhen { cause, attempt ->
        if (attempt < retries) {
            kotlinx.coroutines.delay(delayMillis)
            true
        } else {
            false
        }
    }
}

/**
 * 缓存最新值
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun <T> Flow<T>.cache(scope: CoroutineScope): Flow<T> {
    return this.stateIn(scope, SharingStarted.WhileSubscribed(5000), null)
        .filterNotNull()
}

/**
 * 比较并发出变化
 */
fun <T> Flow<T>.distinctUntilChanged(): Flow<T> {
    return distinctUntilChanged()
}

/**
 * 转换为热流
 */
fun <T> Flow<T>.asHotFlow(scope: CoroutineScope, initialValue: T): StateFlow<T> {
    return stateIn(scope, SharingStarted.WhileSubscribed(5000), initialValue)
}

/**
 * 收集并处理错误
 */
suspend fun <T> Flow<T>.collectCatching(
    onError: (Throwable) -> Unit = {},
    onCollect: (T) -> Unit
) {
    catch { e -> onError(e) }.collect(onCollect)
}

/**
 * 合并多个 Flow
 */
fun <T1, T2, R> combine(
    flow1: Flow<T1>,
    flow2: Flow<T2>,
    transform: (T1, T2) -> R
): Flow<R> {
    return combine(flow1, flow2, transform)
}