package com.openclaw.clawchat.testapi

import android.app.Application
import com.openclaw.clawchat.network.ApplicationScope
import com.openclaw.clawchat.util.CrashHandler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject

/**
 * ClawChat Application (debug build variant).
 * Starts the Test API server monitor on app launch.
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
        CrashHandler.init(this)
        val monitor = AdbMonitor(testApiServer, applicationScope)
        testApiMonitorRef = monitor
        monitor.start()
    }
}
