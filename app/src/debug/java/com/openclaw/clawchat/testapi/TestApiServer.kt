package com.openclaw.clawchat.testapi

import com.openclaw.clawchat.network.protocol.GatewayConnection
import com.openclaw.clawchat.ui.state.MainViewModel
import com.openclaw.clawchat.ui.state.SessionViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.routing.*
import kotlinx.coroutines.*
import javax.inject.Inject

/**
 * Embedded Ktor HTTP server for test API.
 * Starts on ADB reverse detect, stops after idle timeout.
 * Hilt-managed singleton so it can access ViewModels and GatewayConnection.
 */
@javax.inject.Singleton
class TestApiServer @Inject constructor(
    private val mainViewModel: MainViewModel,
    private val sessionViewModel: SessionViewModel,
    private val gatewayConnection: GatewayConnection
) {
    private var engine: NettyApplicationEngine? = null
    private var lastRequestTime = System.currentTimeMillis()
    private val idleTimeoutMs = 5 * 60 * 1000L // 5 minutes

    val mainViewModel get() = _mainVm
    val sessionViewModel get() = _sessionVm
    val gatewayConnection get() = _gateway
    private val _mainVm = mainViewModel
    private val _sessionVm = sessionViewModel
    private val _gateway = gatewayConnection

    val isRunning: Boolean
        get() = engine != null

    val serverPort: Int
        get() = PORT

    fun start() {
        if (engine != null) return

        engine = embeddedServer(Netty) {
            routing {
                testApiRoutes(this@TestApiServer)
            }
        }.also {
            it.start(wait = false)
            lastRequestTime = System.currentTimeMillis()
        }
    }

    fun stop() {
        engine?.stop(gracePeriodMillis = 1000, timeoutMillis = 5000)
        engine = null
    }

    fun recordRequest() {
        lastRequestTime = System.currentTimeMillis()
    }

    fun shouldStop(): Boolean {
        return isRunning && (System.currentTimeMillis() - lastRequestTime > idleTimeoutMs)
    }

    companion object {
        const val PORT = 18792
        const val IDLE_TIMEOUT_MS = 5 * 60 * 1000L // 5 minutes
    }
}
