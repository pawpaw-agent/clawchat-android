package com.openclaw.clawchat

import android.os.Bundle
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
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.openclaw.clawchat.data.ThemeMode
import com.openclaw.clawchat.ui.screens.PairingScreen
import com.openclaw.clawchat.ui.screens.MainScreen
import com.openclaw.clawchat.ui.screens.SessionScreen
import com.openclaw.clawchat.ui.theme.TerminalFlowTheme
import com.openclaw.clawchat.ui.state.MainViewModel
import com.openclaw.clawchat.ui.state.PairingViewModel
import com.openclaw.clawchat.ui.state.ThemeViewModel
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
            // 主题 ViewModel
            val themeViewModel: ThemeViewModel = hiltViewModel()
            val themeMode by themeViewModel.themeMode.collectAsStateWithLifecycle()
            val systemDarkTheme = isSystemInDarkTheme()
            
            // 计算实际使用的暗色主题值
            val darkTheme = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> systemDarkTheme
            }
            
            TerminalFlowTheme(darkTheme = darkTheme) {
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
    // 使用 null 作为初始值，等待真实值加载
    val isPairedState = mainViewModel.isPaired.collectAsStateWithLifecycle(initialValue = null)
    
    // 在加载完成前显示空白（splash screen 会处理）
    val startDestination = when (isPairedState.value) {
        true -> "main"
        false -> "pairing"
        null -> "main" // 默认进入 main，让 ViewModel 处理连接状态
    }

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