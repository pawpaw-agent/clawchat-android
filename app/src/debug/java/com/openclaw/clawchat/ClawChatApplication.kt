package com.openclaw.clawchat

import android.app.Application
import com.openclaw.clawchat.network.ApplicationScope
import com.openclaw.clawchat.testapi.AdbMonitor
import com.openclaw.clawchat.testapi.TestApiServer
import com.openclaw.clawchat.util.CrashHandler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject

/**
 * ClawChat Application (debug build)
 *
 * This class is compiled instead of the main ClawChatApplication for debug builds.
 * Starts the AdbMonitor which auto-starts the test API server on ADB reverse detect.
 */
@HiltAndroidApp
class ClawChatApplication : Application() {

    @Inject
    lateinit var testApiServer: TestApiServer

    @Inject
    @field:ApplicationScope
    lateinit var applicationScope: CoroutineScope

    internal var testApiMonitorRef: AdbMonitor? = null

    override fun onCreate() {
        super.onCreate()
        // Initialize crash handler
        CrashHandler.init(this)

        // Start Test API server monitor (ADB-connected only)
        val monitor = AdbMonitor(testApiServer, applicationScope)
        testApiMonitorRef = monitor
        monitor.start()
    }
}
