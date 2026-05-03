package com.openclaw.clawchat.ui.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.openclaw.clawchat.ui.components.minimal.MinimalIconButton
import com.openclaw.clawchat.ui.components.MinimalStatusIndicator
import com.openclaw.clawchat.ui.screens.main.MinimalMainScreen
import com.openclaw.clawchat.ui.screens.session.MinimalSessionScreen
import com.openclaw.clawchat.ui.screens.settings.MinimalSettingsScreen
import com.openclaw.clawchat.ui.theme.MinimalTokens
import com.openclaw.clawchat.network.WebSocketConnectionState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MinimalNavHost(
    navController: NavHostController = rememberNavController(),
    startDestination: String = MinimalNavRoutes.HOME,
    connectionState: WebSocketConnectionState = WebSocketConnectionState.Disconnected,
    latencyMs: Long? = null
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: startDestination
    val showBottomBar = currentRoute in listOf(MinimalNavRoutes.HOME, MinimalNavRoutes.SETTINGS)
    val isSessionScreen = currentRoute.startsWith("session")

    Scaffold(
        topBar = {
            MinimalTopBar(
                currentRoute = currentRoute,
                connectionState = connectionState,
                latencyMs = latencyMs,
                onBack = { navController.popBackStack() },
                onSettings = { navController.navigate(MinimalNavRoutes.SETTINGS) },
                isSessionScreen = isSessionScreen
            )
        },
        bottomBar = {
            if (showBottomBar) {
                MinimalBottomNavBar(
                    currentRoute = currentRoute,
                    onNavigate = { navController.navigate(it) }
                )
            }
        },
        contentWindowInsets = WindowInsets(0)  // Handle insets manually in screens
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .windowInsetsPadding(WindowInsets.navigationBars)
        ) {
            NavHost(
                navController = navController,
                startDestination = startDestination
            ) {
                composable(MinimalNavRoutes.HOME) {
                    MinimalMainScreen(
                        onNavigateToSession = { sessionId ->
                            navController.navigate(MinimalNavRoutes.session(sessionId))
                        }
                    )
                }

                composable(
                    route = MinimalNavRoutes.SESSION,
                    arguments = listOf(
                        navArgument("sessionId") { type = NavType.StringType }
                    )
                ) { backStackEntry ->
                    val sessionId = backStackEntry.arguments?.getString("sessionId") ?: return@composable
                    MinimalSessionScreen(
                        sessionId = sessionId,
                        onNavigateBack = { navController.popBackStack() }
                    )
                }

                composable(MinimalNavRoutes.SETTINGS) {
                    MinimalSettingsScreen(
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MinimalTopBar(
    currentRoute: String,
    connectionState: WebSocketConnectionState,
    latencyMs: Long?,
    onBack: () -> Unit,
    onSettings: () -> Unit,
    isSessionScreen: Boolean
) {
    val title = when {
        currentRoute == MinimalNavRoutes.HOME -> "ClawChat"
        currentRoute == MinimalNavRoutes.SETTINGS -> "Settings"
        currentRoute.startsWith("session") -> "Chat"
        else -> "ClawChat"
    }

    TopAppBar(
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
        },
        navigationIcon = {
            if (isSessionScreen) {
                MinimalIconButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    onClick = onBack
                )
            }
        },
        actions = {
            if (currentRoute == MinimalNavRoutes.HOME) {
                MinimalStatusIndicator(
                    status = connectionState,
                    dotSize = 6.dp
                )
                if (latencyMs != null) {
                    Text(
                        text = "${latencyMs}ms",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = MinimalTokens.space2)
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onBackground
        )
    )
}