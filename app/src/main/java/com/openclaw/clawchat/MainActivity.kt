package com.openclaw.clawchat

import android.content.ComponentCallbacks2
import android.os.Bundle
import com.openclaw.clawchat.util.AppLog
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openclaw.clawchat.data.ThemeMode
import com.openclaw.clawchat.ui.navigation.MinimalNavHost
import com.openclaw.clawchat.ui.state.MainViewModel
import com.openclaw.clawchat.ui.state.ThemeViewModel
import com.openclaw.clawchat.ui.theme.MinimalTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val themeViewModel: ThemeViewModel = hiltViewModel()
            val themeMode by themeViewModel.themeMode.collectAsStateWithLifecycle()
            val dynamicColor by themeViewModel.dynamicColor.collectAsStateWithLifecycle()
            val systemDarkTheme = isSystemInDarkTheme()

            val darkTheme = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> systemDarkTheme
            }

            MinimalTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MinimalNavHost()
                }
            }
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)

        when (level) {
            ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> {
                AppLog.d("MainActivity", "TRIM_MEMORY_UI_HIDDEN: Releasing UI resources")
            }
            ComponentCallbacks2.TRIM_MEMORY_MODERATE -> {
                AppLog.d("MainActivity", "TRIM_MEMORY_MODERATE: Moderate memory pressure")
            }
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> {
                AppLog.w("MainActivity", "TRIM_MEMORY_RUNNING_CRITICAL: Critical memory pressure")
            }
        }
    }
}