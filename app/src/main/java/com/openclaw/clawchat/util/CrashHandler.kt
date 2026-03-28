package com.openclaw.clawchat.util

import android.content.Context
import android.os.Build
import android.os.Process
import com.openclaw.clawchat.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 崩溃处理器
 *
 * 功能：
 * - 捕获未处理异常
 * - 保存崩溃日志到文件
 * - 包含设备信息、应用版本、堆栈跟踪
 */
class CrashHandler(private val context: Context) : Thread.UncaughtExceptionHandler {

    companion object {
        private const val TAG = "CrashHandler"
        private const val CRASH_FILE_NAME = "crash_report.txt"
        private const val CRASH_HISTORY_FILE = "crash_history.txt"
        private const val MAX_HISTORY_SIZE = 10

        @Volatile
        private var instance: CrashHandler? = null

        fun init(context: Context): CrashHandler {
            return instance ?: synchronized(this) {
                instance ?: CrashHandler(context.applicationContext).also {
                    instance = it
                    Thread.setDefaultUncaughtExceptionHandler(it)
                }
            }
        }

        fun getCrashReport(context: Context): CrashReport? {
            val file = File(context.filesDir, CRASH_FILE_NAME)
            if (!file.exists()) return null

            val content = file.readText()
            return parseCrashReport(content)
        }

        fun getCrashHistory(context: Context): List<CrashReport> {
            val file = File(context.filesDir, CRASH_HISTORY_FILE)
            if (!file.exists()) return emptyList()

            return file.readLines()
                .filter { it.isNotBlank() }
                .mapNotNull { parseCrashReport(it) }
        }

        fun clearCrashReport(context: Context) {
            val file = File(context.filesDir, CRASH_FILE_NAME)
            file.delete()
        }

        fun clearCrashHistory(context: Context) {
            val file = File(context.filesDir, CRASH_HISTORY_FILE)
            file.delete()
        }

        private fun parseCrashReport(content: String): CrashReport? {
            if (content.isBlank()) return null

            val lines = content.lines()
            var timestamp: Long? = null
            var message: String? = null
            var stackTrace: String? = null

            for (line in lines) {
                when {
                    line.startsWith("Timestamp:") -> {
                        timestamp = line.substringAfter("Timestamp:").trim().toLongOrNull()
                    }
                    line.startsWith("Exception:") -> {
                        message = line.substringAfter("Exception:").trim()
                    }
                    line.startsWith("Stack Trace:") -> {
                        stackTrace = content.substringAfter("Stack Trace:\n")
                    }
                }
            }

            return CrashReport(
                timestamp = timestamp ?: System.currentTimeMillis(),
                message = message ?: "Unknown error",
                stackTrace = stackTrace ?: content,
                rawContent = content
            )
        }
    }

    private val defaultHandler: Thread.UncaughtExceptionHandler? = 
        Thread.getDefaultUncaughtExceptionHandler()

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        val crashReport = buildCrashReport(throwable)

        // 保存当前崩溃报告
        saveCrashReport(crashReport)

        // 添加到历史记录
        appendToHistory(crashReport)

        // 调用默认处理器（会导致应用崩溃）
        defaultHandler?.uncaughtException(thread, throwable) ?: run {
            Process.killProcess(Process.myPid())
            System.exit(1)
        }
    }

    private fun buildCrashReport(throwable: Throwable): String {
        val sb = StringBuilder()

        // 时间戳
        sb.appendLine("Timestamp: ${System.currentTimeMillis()}")
        sb.appendLine("Date: ${dateFormat.format(Date())}")
        sb.appendLine()

        // 应用信息
        sb.appendLine("=== Application Info ===")
        sb.appendLine("Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        sb.appendLine("Build Type: ${BuildConfig.BUILD_TYPE}")
        sb.appendLine("Debug: ${BuildConfig.DEBUG}")
        sb.appendLine()

        // 设备信息
        sb.appendLine("=== Device Info ===")
        sb.appendLine("Brand: ${Build.BRAND}")
        sb.appendLine("Device: ${Build.DEVICE}")
        sb.appendLine("Model: ${Build.MODEL}")
        sb.appendLine("Manufacturer: ${Build.MANUFACTURER}")
        sb.appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
        sb.appendLine()

        // 异常信息
        sb.appendLine("=== Exception Info ===")
        sb.appendLine("Thread: ${Thread.currentThread().name}")
        sb.appendLine("Exception: ${throwable.javaClass.name}")
        sb.appendLine("Message: ${throwable.message}")
        sb.appendLine()

        // 堆栈跟踪
        sb.appendLine("=== Stack Trace ===")
        val stringWriter = StringWriter()
        val printWriter = PrintWriter(stringWriter)
        throwable.printStackTrace(printWriter)
        sb.appendLine(stringWriter.toString())

        return sb.toString()
    }

    private fun saveCrashReport(report: String) {
        try {
            val file = File(context.filesDir, CRASH_FILE_NAME)
            file.writeText(report)
            AppLog.i(TAG, "Crash report saved to ${file.absolutePath}")
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to save crash report", e)
        }
    }

    private fun appendToHistory(report: String) {
        try {
            val file = File(context.filesDir, CRASH_HISTORY_FILE)

            // 读取现有历史
            val history = if (file.exists()) {
                file.readLines().toMutableList()
            } else {
                mutableListOf()
            }

            // 添加新记录（压缩版）
            val summary = buildSummary(report)
            history.add(0, summary)

            // 限制历史记录数量
            while (history.size > MAX_HISTORY_SIZE) {
                history.removeAt(history.size - 1)
            }

            file.writeText(history.joinToString("\n"))
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to append crash history", e)
        }
    }

    private fun buildSummary(report: String): String {
        val lines = report.lines()
        val timestamp = lines.find { it.startsWith("Timestamp:") } ?: ""
        val exception = lines.find { it.startsWith("Exception:") } ?: ""
        val message = lines.find { it.startsWith("Message:") } ?: ""
        return "$timestamp | $exception | $message"
    }
}

/**
 * 崩溃报告数据类
 */
data class CrashReport(
    val timestamp: Long,
    val message: String,
    val stackTrace: String,
    val rawContent: String
) {
    val formattedTime: String
        get() = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            .format(Date(timestamp))
}