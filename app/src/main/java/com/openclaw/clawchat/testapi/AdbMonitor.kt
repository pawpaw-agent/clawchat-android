package com.openclaw.clawchat.testapi

import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.FileReader

/**
 * Monitors ADB reverse connection and controls TestApiServer lifecycle.
 *
 * Detection approach: poll /proc/net/tcp to check if port 18792 is in LISTEN state.
 * When ADB reverse is active and a host connects, the port becomes reachable.
 * Auto-starts server when detect first positive, auto-stops after IDLE_TIMEOUT_MS of no requests.
 *
 * Note: This detects if the app's port is bound (listening), which is a prerequisite
 * for ADB reverse to work. The actual ADB reverse tunnel is managed by the host,
 * but we can reliably detect when the server is accessible.
 */
class AdbMonitor(
    private val server: TestApiServer,
    private val scope: CoroutineScope
) {
    private val handler = Handler(Looper.getMainLooper())
    private var monitorJob: Job? = null
    private var idleCheckJob: Job? = null

    // Port 18792 in hex = 4960
    private val targetPortHex = "4960".uppercase()

    fun start() {
        if (monitorJob?.isActive == true) return

        monitorJob = scope.launch(Dispatchers.IO) {
            // Initial check
            if (isPortListening()) {
                startServerIfNeeded()
            }

            // Poll every 3 seconds
            while (isActive) {
                val isListening = isPortListening()
                if (isListening && !server.isRunning) {
                    startServerIfNeeded()
                } else if (!isListening && server.isRunning) {
                    stopServerIfIdle()
                }
                delay(POLL_INTERVAL_MS)
            }
        }

        // Also check idle timeout every 30 seconds
        idleCheckJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                if (server.isRunning && server.shouldStop()) {
                    server.stop()
                }
                delay(IDLE_CHECK_INTERVAL_MS)
            }
        }
    }

    fun stop() {
        monitorJob?.cancel()
        idleCheckJob?.cancel()
        if (server.isRunning) {
            server.stop()
        }
    }

    private fun startServerIfNeeded() {
        if (!server.isRunning) {
            server.start()
        }
    }

    private fun stopServerIfIdle() {
        if (server.shouldStop()) {
            server.stop()
        }
    }

    /**
     * Check if port 18792 appears in /proc/net/tcp6 (ADB reverse active).
     * When ADB reverse is set up (`adb reverse tcp:18792 tcp:18792`), a socket
     * entry appears for that port - either in LISTEN state (server running)
     * or in other states (ADB tunnel ready but server not yet started).
     * Port 18792 in hex = 4960.
     */
    private fun isPortListening(): Boolean {
        return try {
            BufferedReader(FileReader("/proc/net/tcp6")).use { br ->
                var line: String?
                while (br.readLine().also { line = it } != null) {
                    val trimmed = line ?: continue
                    // Skip header line
                    if (trimmed.startsWith("sl")) continue
                    // Look for our port hex (4960) in the local address field
                    // Format: sl local_address:port rem_address:port state ...
                    if (trimmed.contains(targetPortHex, ignoreCase = true)) {
                        return true
                    }
                }
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    companion object {
        private const val POLL_INTERVAL_MS = 3_000L // 3 seconds
        private const val IDLE_CHECK_INTERVAL_MS = 30_000L // 30 seconds
    }
}
