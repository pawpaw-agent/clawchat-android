package com.openclaw.clawchat.util

import kotlin.system.measureNanoTime
import kotlin.system.measureTimeMillis

/**
 * 性能基准测试工具
 * 提供性能测量和基准比较
 */
object BenchmarkHelper {
    
    /**
     * 测量执行时间（毫秒）
     */
    inline fun <T> measureTime(block: () -> T): BenchmarkResult<T> {
        var result: T
        val timeMillis = measureTimeMillis {
            result = block()
        }
        return BenchmarkResult(result, timeMillis.toDouble())
    }
    
    /**
     * 测量执行时间（纳秒）
     */
    inline fun <T> measureTimeNanos(block: () -> T): BenchmarkResultNanos<T> {
        var result: T
        val timeNanos = measureNanoTime {
            result = block()
        }
        return BenchmarkResultNanos(result, timeNanos.toDouble())
    }
    
    /**
     * 多次运行并取平均值
     */
    inline fun <T> benchmark(
        iterations: Int = 10,
        warmup: Int = 2,
        block: () -> T
    ): BenchmarkStats {
        // 预热
        repeat(warmup) { block() }
        
        // 正式测量
        val times = mutableListOf<Double>()
        repeat(iterations) {
            val time = measureTimeMillis { block() }
            times.add(time.toDouble())
        }
        
        return BenchmarkStats(
            iterations = iterations,
            averageMs = times.average(),
            minMs = times.minOrNull() ?: 0.0,
            maxMs = times.maxOrNull() ?: 0.0,
            medianMs = times.sorted()[times.size / 2],
            standardDeviation = calculateStdDev(times)
        )
    }
    
    /**
     * 计算标准差
     */
    private fun calculateStdDev(values: List<Double>): Double {
        val avg = values.average()
        val variance = values.map { (it - avg) * (it - avg) }.average()
        return kotlin.math.sqrt(variance)
    }
    
    /**
     * 比较两个实现的性能
     */
    inline fun compare(
        iterations: Int = 10,
        crossinline baseline: () -> Unit,
        crossinline optimized: () -> Unit
    ): ComparisonResult {
        val baselineStats = benchmark(iterations) { baseline() }
        val optimizedStats = benchmark(iterations) { optimized() }
        
        val improvement = ((baselineStats.averageMs - optimizedStats.averageMs) 
            / baselineStats.averageMs * 100)
        
        return ComparisonResult(
            baseline = baselineStats,
            optimized = optimizedStats,
            improvementPercent = improvement
        )
    }
}

/**
 * 基准测试结果（毫秒）
 */
data class BenchmarkResult<T>(
    val value: T,
    val timeMs: Double
)

/**
 * 基准测试结果（纳秒）
 */
data class BenchmarkResultNanos<T>(
    val value: T,
    val timeNanos: Double
)

/**
 * 基准测试统计
 */
data class BenchmarkStats(
    val iterations: Int,
    val averageMs: Double,
    val minMs: Double,
    val maxMs: Double,
    val medianMs: Double,
    val standardDeviation: Double
) {
    /**
     * 打印报告
     */
    fun printReport(name: String = "Benchmark") {
        println("""
            === $name ===
            Iterations: $iterations
            Average: ${String.format("%.2f", averageMs)} ms
            Min: ${String.format("%.2f", minMs)} ms
            Max: ${String.format("%.2f", maxMs)} ms
            Median: ${String.format("%.2f", medianMs)} ms
            Std Dev: ${String.format("%.2f", standardDeviation)} ms
        """.trimIndent())
    }
}

/**
 * 比较结果
 */
data class ComparisonResult(
    val baseline: BenchmarkStats,
    val optimized: BenchmarkStats,
    val improvementPercent: Double
) {
    fun isImprovement(): Boolean = improvementPercent > 0
    
    fun printReport() {
        println("""
            === Performance Comparison ===
            Baseline: ${String.format("%.2f", baseline.averageMs)} ms
            Optimized: ${String.format("%.2f", optimized.averageMs)} ms
            Improvement: ${String.format("%.2f", improvementPercent)}%
        """.trimIndent())
    }
}