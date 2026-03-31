package com.openclaw.clawchat.util

import android.app.ActivityManager
import android.content.Context
import android.os.Debug

/**
 * 内存分析工具
 * 用于检测内存使用和泄漏
 */
object MemoryProfiler {
    
    /**
     * 内存快照
     */
    data class MemorySnapshot(
        val totalMemory: Long,
        val freeMemory: Long,
        val usedMemory: Long,
        val maxMemory: Long,
        val nativeHeapSize: Long,
        val nativeHeapAllocated: Long,
        val nativeHeapFree: Long,
        val timestamp: Long = System.currentTimeMillis()
    ) {
        val usedMB: Double get() = usedMemory / (1024.0 * 1024.0)
        val freeMB: Double get() = freeMemory / (1024.0 * 1024.0)
        val totalMB: Double get() = totalMemory / (1024.0 * 1024.0)
        val maxMB: Double get() = maxMemory / (1024.0 * 1024.0)
        
        fun printReport() {
            println("""
                === Memory Snapshot ===
                Used: ${String.format("%.2f", usedMB)} MB
                Free: ${String.format("%.2f", freeMB)} MB
                Total: ${String.format("%.2f", totalMB)} MB
                Max: ${String.format("%.2f", maxMB)} MB
            """.trimIndent())
        }
    }
    
    /**
     * 获取当前内存快照
     */
    fun takeSnapshot(): MemorySnapshot {
        val runtime = Runtime.getRuntime()
        val memoryInfo = Debug.MemoryInfo()
        Debug.getMemoryInfo(memoryInfo)
        
        return MemorySnapshot(
            totalMemory = runtime.totalMemory(),
            freeMemory = runtime.freeMemory(),
            usedMemory = runtime.totalMemory() - runtime.freeMemory(),
            maxMemory = runtime.maxMemory(),
            nativeHeapSize = memoryInfo.totalPrivateDirty.toLong() * 1024,
            nativeHeapAllocated = memoryInfo.totalPrivateDirty.toLong() * 1024,
            nativeHeapFree = 0L
        )
    }
    
    /**
     * 测量内存增量
     */
    inline fun <T> measureMemoryDelta(block: () -> T): MemoryDelta<T> {
        val before = takeSnapshot()
        val result = block()
        val after = takeSnapshot()
        
        return MemoryDelta(
            result = result,
            before = before,
            after = after,
            deltaBytes = after.usedMemory - before.usedMemory
        )
    }
    
    /**
     * 检测潜在内存泄漏
     */
    fun detectLeak(
        before: MemorySnapshot,
        after: MemorySnapshot,
        thresholdMB: Double = 1.0
    ): LeakDetectionResult {
        val deltaMB = (after.usedMemory - before.usedMemory) / (1024.0 * 1024.0)
        val potentialLeak = deltaMB > thresholdMB
        
        return LeakDetectionResult(
            deltaMB = deltaMB,
            thresholdMB = thresholdMB,
            potentialLeak = potentialLeak
        )
    }
    
    /**
     * 触发 GC 并测量回收效果
     */
    fun measureGCCollection(): GCCollectionResult {
        val before = takeSnapshot()
        System.gc()
        Thread.sleep(100) // 等待 GC 完成
        val after = takeSnapshot()
        
        val reclaimedBytes = before.usedMemory - after.usedMemory
        val reclaimedMB = reclaimedBytes / (1024.0 * 1024.0)
        
        return GCCollectionResult(
            before = before,
            after = after,
            reclaimedBytes = reclaimedBytes,
            reclaimedMB = reclaimedMB
        )
    }
}

/**
 * 内存增量结果
 */
data class MemoryDelta<T>(
    val result: T,
    val before: MemoryProfiler.MemorySnapshot,
    val after: MemoryProfiler.MemorySnapshot,
    val deltaBytes: Long
) {
    val deltaMB: Double get() = deltaBytes / (1024.0 * 1024.0)
}

/**
 * 泄漏检测结果
 */
data class LeakDetectionResult(
    val deltaMB: Double,
    val thresholdMB: Double,
    val potentialLeak: Boolean
)

/**
 * GC 回收结果
 */
data class GCCollectionResult(
    val before: MemoryProfiler.MemorySnapshot,
    val after: MemoryProfiler.MemorySnapshot,
    val reclaimedBytes: Long,
    val reclaimedMB: Double
)