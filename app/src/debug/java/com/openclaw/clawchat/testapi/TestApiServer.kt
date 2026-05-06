package com.openclaw.clawchat.testapi

import com.openclaw.clawchat.network.protocol.GatewayConnection
import com.openclaw.clawchat.ui.state.MainViewModel
import com.openclaw.clawchat.ui.state.SessionViewModel
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.routing.*
import kotlinx.coroutines.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Embedded Ktor HTTP server for test API (debug builds only).
 * Starts on ADB reverse detect, stops after idle timeout.
 */
@Singleton
class TestApiServer @Inject constructor(
    @JvmField internal val mainViewModel: MainViewModel,
    @JvmField internal val sessionViewModel: SessionViewModel,
    @JvmField internal val gatewayConnection: GatewayConnection
) {
    private var engine: NettyApplicationEngine? = null
    private var lastRequestTime = System.currentTimeMillis()
    private val idleTimeoutMs = IDLE_TIMEOUT_MS

    val isRunning: Boolean
        get() = engine != null

    val serverPort: Int
        get() = PORT

    fun start() {
        if (engine != null) return

        engine = embeddedServer(Netty) {
            routing {
                installTestApiRoutes(mainViewModel, sessionViewModel, gatewayConnection, this@TestApiServer)
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
        const val IDLE_TIMEOUT_MS = 5 * 60 * 1000L
    }
}
