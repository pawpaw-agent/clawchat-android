package com.openclaw.clawchat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.openclaw.clawchat.ui.screens.PairingScreen
import com.openclaw.clawchat.ui.screens.MainScreen
import com.openclaw.clawchat.ui.screens.SessionScreen
import com.openclaw.clawchat.ui.theme.ClawChatTheme
import com.openclaw.clawchat.ui.state.MainViewModel
import com.openclaw.clawchat.ui.state.PairingViewModel
import dagger.hilt.android.AndroidEntryPoint

/**
 * ClawChat 主 Activity
 * 
 * 应用入口点，负责：
 * - 初始化 Compose UI
 * - 设置导航结构
 * - 管理应用生命周期
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // 安装启动屏
        val splashScreen = installSplashScreen()
        
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            ClawChatTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ClawChatNavHost()
                }
            }
        }
    }
}

/**
 * ClawChat 导航主机
 * 
 * 定义应用的导航结构：
 * - pairing: 设备配对屏幕（首次启动）
 * - main: 主界面（配对成功后）
 * - session/{sessionId}: 会话详情界面
 */
@androidx.compose.runtime.Composable
fun ClawChatNavHost(
    mainViewModel: MainViewModel = hiltViewModel(),
    pairingViewModel: PairingViewModel = hiltViewModel()
) {
    val navController = rememberNavController()
    
    // 检查是否已配对，决定初始目的地
    val isPairedState = mainViewModel.isPaired.collectAsStateWithLifecycle(initialValue = false)
    val startDestination = if (isPairedState.value) "main" else "pairing"

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        // 配对屏幕
        composable("pairing") {
            PairingScreen(
                viewModel = pairingViewModel,
                onPairingSuccess = {
                    mainViewModel.refreshPairedState()
                    navController.navigate("main") {
                        popUpTo("pairing") { inclusive = true }
                    }
                }
            )
        }

        // 主屏幕
        composable("main") {
            MainScreen(
                viewModel = mainViewModel,
                onNavigateToSession = { sessionId ->
                    navController.navigate("session/$sessionId")
                },
                onDisconnect = {
                    navController.navigate("pairing") {
                        popUpTo("main") { inclusive = true }
                    }
                }
            )
        }

        // 会话详情屏幕
        composable(
            route = "session/{sessionId}",
            arguments = listOf(
                navArgument("sessionId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getString("sessionId") ?: return@composable
            
            SessionScreen(
                viewModel = hiltViewModel(),
                sessionId = sessionId,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
