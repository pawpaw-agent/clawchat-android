package com.openclaw.clawchat.util

import android.util.Log
import com.openclaw.clawchat.BuildConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 统一日志工具
 *
 * 功能：
 * - 日志级别控制
 * - 文件持久化
 * - 超长日志分割
 * - Debug 构建自动启用详细日志
 */
object AppLog {

    private const val MAX_LOG_LENGTH = 4000
    private const val MAX_LOG_FILE_SIZE = 5 * 1024 * 1024 // 5MB
    private const val MAX_LOG_FILES = 5

    /**
     * 日志级别
     */
    enum class Level(val priority: Int) {
        VERBOSE(0),
        DEBUG(1),
        INFO(2),
        WARNING(3),
        ERROR(4),
        NONE(5)
    }

    /**
     * 当前日志级别
     * Debug 构建默认 VERBOSE，Release 构建默认 INFO
     */
    @Volatile
    var logLevel: Level = if (BuildConfig.DEBUG) Level.VERBOSE else Level.INFO
        set(value) {
            field = value
            i("AppLog", "Log level changed to: $value")
        }

    /**
     * 是否启用文件持久化
     */
    @Volatile
    var enableFileLogging: Boolean = false
        set(value) {
            field = value
            if (value) {
                startFileLogger()
            } else {
                stopFileLogger()
            }
        }

    /**
     * 日志文件目录
     */
    var logDirectory: File? = null

    private val logQueue = ConcurrentLinkedQueue<String>()
    private val executor = Executors.newSingleThreadScheduledExecutor()
    private var isFileLoggerRunning = false

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    private val fileDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    // ─────────────────────────────────────────────────────────────
    // 日志方法
    // ─────────────────────────────────────────────────────────────

    /**
     * Verbose 日志
     */
    fun v(tag: String, message: String) {
        if (logLevel.priority <= Level.VERBOSE.priority) {
            logToFile("V", tag, message)
            if (message.length > MAX_LOG_LENGTH) {
                Log.v(tag, message.substring(0, MAX_LOG_LENGTH))
                v(tag, message.substring(MAX_LOG_LENGTH))
            } else {
                Log.v(tag, message)
            }
        }
    }

    /**
     * Debug 日志
     */
    fun d(tag: String, message: String) {
        if (logLevel.priority <= Level.DEBUG.priority) {
            logToFile("D", tag, message)
            if (message.length > MAX_LOG_LENGTH) {
                Log.d(tag, message.substring(0, MAX_LOG_LENGTH))
                d(tag, message.substring(MAX_LOG_LENGTH))
            } else {
                Log.d(tag, message)
            }
        }
    }

    /**
     * Info 日志
     */
    fun i(tag: String, message: String) {
        if (logLevel.priority <= Level.INFO.priority) {
            logToFile("I", tag, message)
            Log.i(tag, message)
        }
    }

    /**
     * Warning 日志
     */
    fun w(tag: String, message: String) {
        if (logLevel.priority <= Level.WARNING.priority) {
            logToFile("W", tag, message)
            Log.w(tag, message)
        }
    }

    /**
     * Warning 日志（带异常）
     */
    fun w(tag: String, message: String, throwable: Throwable) {
        if (logLevel.priority <= Level.WARNING.priority) {
            val fullMessage = "$message\n${Log.getStackTraceString(throwable)}"
            logToFile("W", tag, fullMessage)
            Log.w(tag, message, throwable)
        }
    }

    /**
     * Error 日志
     */
    fun e(tag: String, message: String) {
        if (logLevel.priority <= Level.ERROR.priority) {
            logToFile("E", tag, message)
            Log.e(tag, message)
        }
    }

    /**
     * Error 日志（带异常）
     */
    fun e(tag: String, message: String, throwable: Throwable) {
        if (logLevel.priority <= Level.ERROR.priority) {
            val fullMessage = "$message\n${Log.getStackTraceString(throwable)}"
            logToFile("E", tag, fullMessage)
            Log.e(tag, message, throwable)
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 文件持久化
    // ─────────────────────────────────────────────────────────────

    /**
     * 初始化文件日志
     */
    fun initFileLogging(directory: File) {
        logDirectory = directory
        if (!directory.exists()) {
            directory.mkdirs()
        }
        cleanOldLogFiles()
    }

    private fun logToFile(level: String, tag: String, message: String) {
        if (!enableFileLogging || logDirectory == null) return

        val timestamp = dateFormat.format(Date())
        val logEntry = "$timestamp $level/$tag: $message"
        logQueue.offer(logEntry)
    }

    private fun startFileLogger() {
        if (isFileLoggerRunning) return
        isFileLoggerRunning = true

        executor.scheduleWithFixedDelay({
            try {
                flushLogs()
            } catch (e: Exception) {
                Log.e("AppLog", "Failed to flush logs", e)
            }
        }, 0, 1, TimeUnit.SECONDS)
    }

    private fun stopFileLogger() {
        isFileLoggerRunning = false
        executor.shutdown()
        flushLogs()
    }

    private fun flushLogs() {
        val dir = logDirectory ?: return
        if (!dir.exists()) return

        val today = fileDateFormat.format(Date())
        val logFile = File(dir, "log_$today.txt")

        // Check file size and rotate if needed
        if (logFile.exists() && logFile.length() > MAX_LOG_FILE_SIZE) {
            val rotatedFile = File(dir, "log_${today}_${System.currentTimeMillis()}.txt")
            logFile.renameTo(rotatedFile)
            cleanOldLogFiles()
        }

        // Write queued logs
        val logs = mutableListOf<String>()
        while (logQueue.isNotEmpty()) {
            logQueue.poll()?.let { logs.add(it) }
        }

        if (logs.isNotEmpty()) {
            logFile.appendText(logs.joinToString("\n") + "\n")
        }
    }

    private fun cleanOldLogFiles() {
        val dir = logDirectory ?: return
        if (!dir.exists()) return

        val logFiles = dir.listFiles { file ->
            file.name.startsWith("log_") && file.name.endsWith(".txt")
        }?.sortedByDescending { it.lastModified() } ?: return

        // Keep only the most recent log files
        if (logFiles.size > MAX_LOG_FILES) {
            logFiles.drop(MAX_LOG_FILES).forEach { it.delete() }
        }
    }

    /**
     * 获取日志文件列表
     */
    fun getLogFiles(): List<File> {
        val dir = logDirectory ?: return emptyList()
        if (!dir.exists()) return emptyList()

        return dir.listFiles { file ->
            file.name.startsWith("log_") && file.name.endsWith(".txt")
        }?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    /**
     * 清除所有日志文件
     */
    fun clearLogFiles() {
        val dir = logDirectory ?: return
        if (!dir.exists()) return

        dir.listFiles { file ->
            file.name.startsWith("log_") && file.name.endsWith(".txt")
        }?.forEach { it.delete() }
    }
}