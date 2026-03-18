package com.openclaw.clawchat.network.protocol

import android.util.Log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 序列号管理器
 * 
 * 管理事件序列号，用于：
 * 1. 检测丢失的事件
 * 2. 事件去重
 * 3. 事件顺序保证
 * 
 * 使用示例:
 * ```kotlin
 * val manager = SequenceManager()
 * 
 * // 收到事件
 * val event = EventFrame(seq = 5, ...)
 * 
 * // 检查序列号
 * when (manager.checkSequence(event.seq)) {
 *     is SequenceResult.Ok -> 处理事件
 *     is SequenceResult.Duplicate -> 忽略（已处理）
 *     is SequenceResult.Gap -> 检测到丢失的事件
 *     is SequenceResult.Old -> 忽略（过时事件）
 * }
 * 
 * // 确认事件已处理
 * manager.acknowledge(event.seq)
 * ```
 */
class SequenceManager(
    private val maxGapSize: Int = 100
) {
    companion object {
        private const val TAG = "SequenceManager"
    }
    
    // 当前序列号
    private var currentSeq: Int = 0
    
    // 已确认的序列号集合（用于去重）
    private val acknowledgedSeqs = mutableSetOf<Int>()
    
    // 锁
    private val mutex = Mutex()
    
    // 监听器
    private val listeners = mutableListOf<SequenceListener>()
    
    /**
     * 序列号检查结果
     */
    sealed class SequenceResult {
        /** 序列号正常 */
        data object Ok : SequenceResult()
        
        /** 重复的序列号 */
        data object Duplicate : SequenceResult()
        
        /** 序列号有间隙（丢失事件） */
        data class Gap(val expected: Int, val received: Int, val missing: List<Int>) : SequenceResult()
        
        /** 过时的序列号 */
        data class Old(val received: Int, val current: Int) : SequenceResult()
    }
    
    /**
     * 序列号监听器
     */
    interface SequenceListener {
        fun onSequenceGap(expected: Int, received: Int, missing: List<Int>)
        fun onSequenceReset(newSeq: Int)
    }
    
    /**
     * 检查序列号
     * 
     * @param seq 收到的序列号
     * @return 检查结果
     */
    suspend fun checkSequence(seq: Int?): SequenceResult = mutex.withLock {
        // 没有序列号，跳过检查
        if (seq == null) {
            return SequenceResult.Ok
        }
        
        // 检查是否重复
        if (acknowledgedSeqs.contains(seq)) {
            Log.d(TAG, "重复的序列号：$seq")
            return SequenceResult.Duplicate
        }
        
        // 检查是否过时
        if (seq < currentSeq) {
            Log.d(TAG, "过时的序列号：$seq (current: $currentSeq)")
            return SequenceResult.Old(seq, currentSeq)
        }
        
        // 检查是否有间隙
        if (seq > currentSeq + 1) {
            val missing = (currentSeq + 1 until seq).toList()
            
            if (missing.size <= maxGapSize) {
                Log.w(TAG, "序列号间隙：期望 ${currentSeq + 1}, 收到 $seq, 丢失 $missing")
                
                // 通知监听器
                listeners.forEach { 
                    it.onSequenceGap(currentSeq + 1, seq, missing) 
                }
                
                return SequenceResult.Gap(currentSeq + 1, seq, missing)
            } else {
                // 间隙太大，重置序列号
                Log.w(TAG, "序列号间隙过大，重置：$currentSeq -> $seq")
                listeners.forEach { it.onSequenceReset(seq) }
                currentSeq = seq
                return SequenceResult.Ok
            }
        }
        
        // 序列号正常
        return SequenceResult.Ok
    }
    
    /**
     * 确认序列号已处理
     * 
     * @param seq 序列号
     */
    suspend fun acknowledge(seq: Int?) = mutex.withLock {
        if (seq == null) return@withLock
        
        // 更新当前序列号
        if (seq > currentSeq) {
            currentSeq = seq
        }
        
        // 添加到已确认集合
        acknowledgedSeqs.add(seq)
        
        // 清理旧的序列号（保留最近的 1000 个）
        if (acknowledgedSeqs.size > 1000) {
            val minSeq = currentSeq - 1000
            acknowledgedSeqs.removeAll { it < minSeq }
        }
        
        Log.d(TAG, "确认序列号：$seq (current: $currentSeq)")
    }
    
    /**
     * 获取当前序列号
     */
    suspend fun getCurrentSeq(): Int = mutex.withLock {
        currentSeq
    }
    
    /**
     * 重置序列号
     */
    suspend fun reset(newSeq: Int = 0) = mutex.withLock {
        Log.d(TAG, "重置序列号：$currentSeq -> $newSeq")
        currentSeq = newSeq
        acknowledgedSeqs.clear()
        listeners.forEach { it.onSequenceReset(newSeq) }
    }
    
    /**
     * 添加监听器
     */
    fun addListener(listener: SequenceListener) {
        listeners.add(listener)
    }
    
    /**
     * 移除监听器
     */
    fun removeListener(listener: SequenceListener) {
        listeners.remove(listener)
    }
    
    /**
     * 清除所有监听器
     */
    fun clearListeners() {
        listeners.clear()
    }
}

/**
 * 简单的序列号监听器实现
 */
class SimpleSequenceListener(
    private val onGap: (Int, Int, List<Int>) -> Unit = { _, _, _ -> },
    private val onReset: (Int) -> Unit = {}
) : SequenceManager.SequenceListener {
    override fun onSequenceGap(expected: Int, received: Int, missing: List<Int>) {
        onGap(expected, received, missing)
    }
    
    override fun onSequenceReset(newSeq: Int) {
        onReset(newSeq)
    }
}

/**
 * 事件去重器
 * 
 * 基于事件 ID 或序列号进行去重
 */
class EventDeduplicator(
    private val maxHistorySize: Int = 1000
) {
    private val seenEventIds = mutableSetOf<String>()
    private val seenSeqs = mutableSetOf<Int>()
    private val mutex = Mutex()
    
    /**
     * 检查事件是否重复
     * 
     * @param eventId 事件 ID（如果有）
     * @param seq 序列号（如果有）
     * @return true 如果是新事件，false 如果是重复事件
     */
    suspend fun isDuplicate(eventId: String?, seq: Int?): Boolean = mutex.withLock {
        // 检查事件 ID
        if (eventId != null) {
            if (seenEventIds.contains(eventId)) {
                return true
            }
            seenEventIds.add(eventId)
        }
        
        // 检查序列号
        if (seq != null) {
            if (seenSeqs.contains(seq)) {
                return true
            }
            seenSeqs.add(seq)
        }
        
        // 清理历史记录
        if (seenEventIds.size > maxHistorySize) {
            val toRemove = seenEventIds.take(maxHistorySize / 2)
            seenEventIds.removeAll(toRemove)
        }
        
        if (seenSeqs.size > maxHistorySize) {
            val toRemove = seenSeqs.take(maxHistorySize / 2)
            seenSeqs.removeAll(toRemove)
        }
        
        false
    }
    
    /**
     * 清除历史记录
     */
    suspend fun clear() = mutex.withLock {
        seenEventIds.clear()
        seenSeqs.clear()
    }
}

/**
 * 事件缓冲区
 * 
 * 缓冲等待序列号的事件，用于处理乱序到达
 */
class EventBuffer(
    private val maxSize: Int = 100,
    private val maxWaitTimeMs: Long = 5000L
) {
    private val buffer = mutableMapOf<Int, EventFrame>()
    private val mutex = Mutex()
    
    /**
     * 添加事件到缓冲区
     */
    suspend fun add(event: EventFrame): List<EventFrame> = mutex.withLock {
        val seq = event.seq ?: return@withLock emptyList()
        
        buffer[seq] = event
        
        // 如果缓冲区太大，返回所有事件
        if (buffer.size >= maxSize) {
            return flush()
        }
        
        emptyList()
    }
    
    /**
     * 刷新缓冲区，返回连续的事件
     * 
     * @param expectedSeq 期望的序列号
     * @return 连续的事件列表
     */
    suspend fun flush(expectedSeq: Int = 0): List<EventFrame> = mutex.withLock {
        val result = mutableListOf<EventFrame>()
        var seq = expectedSeq
        
        while (buffer.containsKey(seq)) {
            val event = buffer.remove(seq)
            if (event != null) {
                result.add(event)
                seq++
            } else {
                break
            }
        }
        
        result
    }
    
    /**
     * 获取缓冲区大小
     */
    suspend fun size(): Int = mutex.withLock {
        buffer.size
    }
    
    /**
     * 清除缓冲区
     */
    suspend fun clear() = mutex.withLock {
        buffer.clear()
    }
}
