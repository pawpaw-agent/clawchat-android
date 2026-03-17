package com.openclaw.clawchat

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * ClawChat Application
 * 
 * Main application class for the ClawChat Android client.
 * Initializes Hilt dependency injection and global configurations.
 */
@HiltAndroidApp
class ClawChatApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        
        // Initialize global configurations here
        // Example: Crashlytics, Analytics, etc.
        
        // Enable strict mode for debug builds
        if (BuildConfig.DEBUG) {
            enableStrictMode()
        }
    }

    /**
     * Enable Android Strict Mode for debug builds
     * Helps detect accidental disk/network access on main thread
     */
    private fun enableStrictMode() {
        try {
            // Use Kotlin reflection-friendly approach
            val threadPolicy = android.os.StrictMode.ThreadPolicy.Builder()
                .detectAll()
                .penaltyLog()
                .build()
            android.os.StrictMode.setThreadPolicy(threadPolicy)
            
            val vmPolicy = android.os.StrictMode.VmPolicy.Builder()
                .detectAll()
                .penaltyLog()
                .build()
            android.os.StrictMode.setVmPolicy(vmPolicy)
        } catch (e: Exception) {
            // StrictMode not available, ignore
        }
    }
}
