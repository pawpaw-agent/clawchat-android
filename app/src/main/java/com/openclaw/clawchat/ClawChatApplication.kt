package com.openclaw.clawchat

import android.app.Application
import com.openclaw.clawchat.util.CrashHandler
import dagger.hilt.android.HiltAndroidApp

/**
 * ClawChat Application
 * 
 * Main application class for the ClawChat Android client.
 * Initializes Hilt dependency injection and crash handler.
 */
@HiltAndroidApp
class ClawChatApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // Initialize crash handler
        CrashHandler.init(this)
    }
}
