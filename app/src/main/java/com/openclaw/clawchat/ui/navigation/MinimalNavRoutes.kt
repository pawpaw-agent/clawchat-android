package com.openclaw.clawchat.ui.navigation

/**
 * Minimal Navigation Routes
 */
object MinimalNavRoutes {
    const val HOME = "home"
    const val SESSION = "session/{sessionKey}"
    const val SETTINGS = "settings"
    const val ONBOARDING = "onboarding"

    fun session(sessionKey: String) = "session/$sessionKey"
}