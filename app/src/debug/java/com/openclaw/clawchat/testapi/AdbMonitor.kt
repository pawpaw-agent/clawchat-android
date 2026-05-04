package com.openclaw.clawchat.testapi

import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.FileReader

/**
 * Monitors ADB reverse connection and controls TestApiServer lifecycle.
 * Polls /proc/net/tcp6 for port 18792, auto-starts server when detected, auto-stops after idle timeout.
 * Debug builds only.
 */
class AdbMonitor(
    private val server: TestApiServer,
    private val scope: CoroutineScope
) {
    private var monitorJob: Job? = null
    private var idleCheckJob: Job? = null

    // Port 18792 in hex = 4960
    private val targetPortHex = "4960".uppercase()

    fun start() {
        if (monitorJob?.isActive == true) return

        monitorJob = scope.launch(Dispatchers.IO) {
            if (isPortReachable()) startServerIfNeeded()
            while (isActive) {
                val reachable = isPortReachable()
                if (reachable && !server.isRunning) startServerIfNeeded()
                else if (!reachable && server.isRunning && server.shouldStop()) server.stop()
                delay(POLL_INTERVAL_MS)
            }
        }

        idleCheckJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                if (server.isRunning && server.shouldStop()) server.stop()
                delay(IDLE_CHECK_INTERVAL_MS)
            }
        }
    }

    fun stop() {
        monitorJob?.cancel()
        idleCheckJob?.cancel()
        if (server.isRunning) server.stop()
    }

    private fun startServerIfNeeded() {
        if (!server.isRunning) server.start()
    }

    private fun isPortReachable(): Boolean {
        return try {
            BufferedReader(FileReader("/proc/net/tcp6")).use { br ->
                var line: String?
                while (br.readLine().also { line = it } != null) {
                    val trimmed = line ?: continue
                    if (trimmed.startsWith("sl")) continue
                    if (trimmed.contains(targetPortHex, ignoreCase = true)) return true
                }
                false
            }
        } catch (e: Exception) { false }
    }

    companion object {
        private const val POLL_INTERVAL_MS = 3_000L
        private const val IDLE_CHECK_INTERVAL_MS = 30_000L
    }
}
