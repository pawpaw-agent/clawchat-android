package com.openclaw.clawchat.ui.state

import android.os.Build
import android.os.Process
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openclaw.clawchat.BuildConfig
import com.openclaw.clawchat.network.WebSocketConnectionState
import com.openclaw.clawchat.network.protocol.GatewayConnection
import com.openclaw.clawchat.util.AppLog
import com.openclaw.clawchat.util.CrashHandler
import com.openclaw.clawchat.util.CrashReport
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject

/**
 * Debug 状态
 */
data class DebugUiState(
    // 连接诊断
    val connectionState: String = "Disconnected",
    val connectionLatency: Long? = null,
    val reconnectAttempts: Int = 0,
    val gatewayUrl: String? = null,
    val lastConnectedTime: Long? = null,
    val connectionErrors: List<String> = emptyList(),

    // 日志查看器
    val logLines: List<LogLine> = emptyList(),
    val logLevel: LogLevel = LogLevel.VERBOSE,
    val logSearchQuery: String = "",
    val autoScroll: Boolean = true,
    val isLogStreaming: Boolean = false,

    // 性能指标
    val usedMemory: Long = 0,
    val maxMemory: Long = 0,
    val cpuUsage: Double = 0.0,
    val fps: Int = 0,
    val networkRequests: Int = 0,
    val networkSuccess: Int = 0,
    val appUptime: Long = 0,

    // 消息详情
    val recentMessages: List<MessageDebugInfo> = emptyList(),
    val selectedMessageJson: String? = null,

    // 崩溃报告
    val lastCrashReport: CrashReport? = null,
    val crashHistory: List<CrashReport> = emptyList(),

    // 当前 Tab
    val currentTab: Int = 0
)

data class LogLine(
    val timestamp: String,
    val level: LogLevel,
    val tag: String,
    val message: String
)

enum class LogLevel(val priority: Int, val label: String) {
    VERBOSE(2, "V"),
    DEBUG(3, "D"),
    INFO(4, "I"),
    WARN(5, "W"),
    ERROR(6, "E")
}

data class MessageDebugInfo(
    val id: String,
    val timestamp: Long,
    val status: String,
    val size: Int,
    val latency: Long? = null
)

/**
 * Debug ViewModel
 */
@HiltViewModel
class DebugViewModel @Inject constructor(
    private val gateway: GatewayConnection
) : ViewModel() {

    private val _state = MutableStateFlow(DebugUiState())
    val state: StateFlow<DebugUiState> = _state.asStateFlow()

    private var logcatJob: Job? = null
    private var metricsJob: Job? = null
    private val startTime = System.currentTimeMillis()

    private val connectionErrors = mutableListOf<String>()

    init {
        observeConnectionState()
        startMetricsCollection()
        loadCrashReport()
    }

    override fun onCleared() {
        super.onCleared()
        stopLogcatStream()
        metricsJob?.cancel()
    }

    private fun observeConnectionState() {
        viewModelScope.launch {
            gateway.connectionState.collect { state ->
                val stateName = when (state) {
                    is WebSocketConnectionState.Connected -> "Connected"
                    is WebSocketConnectionState.Connecting -> "Connecting"
                    is WebSocketConnectionState.Disconnecting -> "Disconnecting"
                    is WebSocketConnectionState.Disconnected -> "Disconnected"
                    is WebSocketConnectionState.Error -> "Error: ${state.throwable.message}"
                }

                val url = gateway.helloOkPayload?.let { 
                    gateway.defaultSessionKey 
                }.toString()

                _state.update { 
                    it.copy(
                        connectionState = stateName,
                        gatewayUrl = url
                    )
                }

                if (state is WebSocketConnectionState.Error) {
                    addConnectionError(state.throwable.message ?: "Unknown error")
                }
            }
        }
    }

    private fun startMetricsCollection() {
        metricsJob = viewModelScope.launch {
            while (isActive) {
                updateMemoryMetrics()
                updateUptime()
                delay(1000)
            }
        }
    }

    private fun updateMemoryMetrics() {
        val runtime = Runtime.getRuntime()
        val usedMemory = runtime.totalMemory() - runtime.freeMemory()
        val maxMemory = runtime.maxMemory()

        _state.update { 
            it.copy(
                usedMemory = usedMemory / 1024 / 1024,
                maxMemory = maxMemory / 1024 / 1024
            )
        }
    }

    private fun updateUptime() {
        val uptime = System.currentTimeMillis() - startTime
        _state.update { it.copy(appUptime = uptime / 1000) }
    }

    // === 日志查看器 ===

    fun startLogcatStream() {
        if (logcatJob?.isActive == true) return

        _state.update { it.copy(isLogStreaming = true) }

        logcatJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val process = Runtime.getRuntime().exec(
                    "logcat -v time *:${_state.value.logLevel.label}"
                )
                val reader = BufferedReader(InputStreamReader(process.inputStream))

                var line: String?
                while (reader.readLine().also { line = it } != null && isActive) {
                    val logLine = parseLogLine(line ?: continue) ?: continue

                    // 应用搜索过滤
                    val query = _state.value.logSearchQuery
                    if (query.isNotBlank() && 
                        !logLine.message.contains(query, ignoreCase = true) &&
                        !logLine.tag.contains(query, ignoreCase = true)) {
                        continue
                    }

                    withContext(Dispatchers.Main) {
                        _state.update { state ->
                            val newLines = (state.logLines + logLine).takeLast(500)
                            state.copy(logLines = newLines)
                        }
                    }
                }
            } catch (e: Exception) {
                AppLog.e("DebugViewModel", "Logcat stream error", e)
            }
        }
    }

    fun stopLogcatStream() {
        logcatJob?.cancel()
        logcatJob = null
        _state.update { it.copy(isLogStreaming = false) }
    }

    fun clearLogs() {
        _state.update { it.copy(logLines = emptyList()) }
    }

    fun setLogLevel(level: LogLevel) {
        _state.update { it.copy(logLevel = level) }
        if (_state.value.isLogStreaming) {
            stopLogcatStream()
            startLogcatStream()
        }
    }

    fun setLogSearchQuery(query: String) {
        _state.update { it.copy(logSearchQuery = query) }
    }

    fun toggleAutoScroll() {
        _state.update { it.copy(autoScroll = !it.autoScroll) }
    }

    private fun parseLogLine(line: String): LogLine? {
        // 解析格式: MM-DD HH:MM:SS.mmm PID-TID LEVEL/TAG: MESSAGE
        val regex = Regex("""(\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3})\s+\d+-\d+\s+([VDIWE])/(.+?):\s*(.*)""")
        val match = regex.find(line) ?: return null

        val timestamp = match.groupValues[1]
        val levelChar = match.groupValues[2]
        val tag = match.groupValues[3]
        val message = match.groupValues[4]

        val level = when (levelChar) {
            "V" -> LogLevel.VERBOSE
            "D" -> LogLevel.DEBUG
            "I" -> LogLevel.INFO
            "W" -> LogLevel.WARN
            "E" -> LogLevel.ERROR
            else -> return null
        }

        return LogLine(timestamp, level, tag, message)
    }

    // === 连接诊断 ===

    fun addConnectionError(error: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date())
        connectionErrors.add(0, "[$timestamp] $error")
        if (connectionErrors.size > 50) {
            connectionErrors.removeAt(connectionErrors.size - 1)
        }
        _state.update { it.copy(connectionErrors = connectionErrors.toList()) }
    }

    suspend fun measureLatency(): Long? {
        return gateway.measureLatency()?.also { latency ->
            _state.update { it.copy(connectionLatency = latency) }
        }
    }

    // === 崩溃报告 ===

    fun loadCrashReport() {
        viewModelScope.launch {
            val report = CrashHandler.getCrashReport(gateway.helloOkPayload?.let { 
                kotlinx.coroutines.Dispatchers.Default 
            }?.let { 
                android.app.Application().applicationContext 
            } ?: return@launch)
            _state.update { it.copy(lastCrashReport = report) }
        }
    }

    fun clearCrashReport() {
        // Note: 需要应用 context
        _state.update { it.copy(lastCrashReport = null) }
    }

    // === Tab 切换 ===

    fun setCurrentTab(tab: Int) {
        _state.update { it.copy(currentTab = tab) }
    }
}