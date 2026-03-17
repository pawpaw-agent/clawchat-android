package com.openclaw.clawchat

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * ClawChat Application
 * 
 * Main application class for the ClawChat Android client.
 * Initializes Hilt dependency injection.
 */
@HiltAndroidApp
class ClawChatApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // Application initialization
    }
}
