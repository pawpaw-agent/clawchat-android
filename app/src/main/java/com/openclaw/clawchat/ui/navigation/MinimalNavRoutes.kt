package com.openclaw.clawchat.ui.navigation

/**
 * Minimal Navigation Routes
 */
object MinimalNavRoutes {
    const val HOME = "home"
    const val SESSION = "session/{sessionId}"
    const val SETTINGS = "settings"
    const val ONBOARDING = "onboarding"

    fun session(sessionId: String) = "session/$sessionId"
}