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
            val strictModeClass = Class.forName("android.os.StrictMode")
            val threadPolicyBuilderClass = Class.forName("android.os.StrictMode\$ThreadPolicy\$Builder")
            
            val threadPolicyBuilder = threadPolicyBuilderClass.getDeclaredConstructor().newInstance()
            threadPolicyBuilderClass.getMethod("detectAll").invoke(threadPolicyBuilder)
            threadPolicyBuilderClass.getMethod("penaltyLog").invoke(threadPolicyBuilder)
            
            val threadPolicy = threadPolicyBuilderClass.getMethod("build").invoke(threadPolicyBuilder)
            strictModeClass.getMethod("setThreadPolicy", threadPolicyBuilderClass).invoke(null, threadPolicy)
            
            val vmPolicyBuilder = threadPolicyBuilderClass.getDeclaredConstructor().newInstance()
            vmPolicyBuilderClass.getMethod("detectAll").invoke(vmPolicyBuilder)
            vmPolicyBuilderClass.getMethod("penaltyLog").invoke(vmPolicyBuilder)
            
            val vmPolicy = vmPolicyBuilderClass.getMethod("build").invoke(vmPolicyBuilder)
            strictModeClass.getMethod("setVmPolicy", vmPolicyBuilderClass).invoke(null, vmPolicy)
        } catch (e: Exception) {
            // StrictMode not available, ignore
        }
    }
}
