package com.openclaw.clawchat

import android.app.Application
import com.openclaw.clawchat.util.CrashHandler
import dagger.hilt.android.HiltAndroidApp

/**
 * ClawChat Application (main source set).
 * Debug variant is in app/src/debug/java/...
 */
@HiltAndroidApp
class ClawChatApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashHandler.init(this)
    }
}
