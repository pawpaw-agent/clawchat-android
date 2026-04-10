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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.openclaw.clawchat.security.EncryptedStorage
import com.openclaw.clawchat.ui.screens.MainScreen
import com.openclaw.clawchat.ui.screens.OnboardingScreen
import com.openclaw.clawchat.ui.screens.SessionScreen
import com.openclaw.clawchat.ui.screens.debug.DebugScreen
import com.openclaw.clawchat.ui.screens.settings.CronScreen
import com.openclaw.clawchat.ui.screens.settings.SettingsScreen
import com.openclaw.clawchat.ui.theme.TerminalFlowTheme
import com.openclaw.clawchat.ui.state.MainViewModel
import com.openclaw.clawchat.ui.state.ThemeViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

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
    
    @Inject
    lateinit var encryptedStorage: EncryptedStorage

    @Inject
    lateinit var gatewayConnection: com.openclaw.clawchat.network.protocol.GatewayConnection

    override fun onCreate(savedInstanceState: Bundle?) {
        // 安装启动屏
        val splashScreen = installSplashScreen()
        
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            // 主题 ViewModel
            val themeViewModel: ThemeViewModel = hiltViewModel()
            val themeMode by themeViewModel.themeMode.collectAsStateWithLifecycle()
            val dynamicColor by themeViewModel.dynamicColor.collectAsStateWithLifecycle()
            val systemDarkTheme = isSystemInDarkTheme()
            
            // 计算实际使用的暗色主题值
            val darkTheme = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> systemDarkTheme
            }
            
            TerminalFlowTheme(
                darkTheme = darkTheme,
                dynamicColor = dynamicColor
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ClawChatNavHost(
                        encryptedStorage = encryptedStorage,
                        gatewayConnection = gatewayConnection
                    )
                }
            }
        }
    }

    /**
     * 内存压力回调
     * 根据系统内存压力级别释放资源
     */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)

        when (level) {
            ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> {
                // UI 不可见，释放 UI 相关资源
                AppLog.d("MainActivity", "TRIM_MEMORY_UI_HIDDEN: Releasing UI resources")
            }
            ComponentCallbacks2.TRIM_MEMORY_MODERATE -> {
                // 中等内存压力
                AppLog.d("MainActivity", "TRIM_MEMORY_MODERATE: Moderate memory pressure")
            }
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> {
                // 严重内存压力
                AppLog.w("MainActivity", "TRIM_MEMORY_RUNNING_CRITICAL: Critical memory pressure")
            }
        }
    }
}

/**
 * ClawChat 导航主机
 * 
 * 定义应用的导航结构：
 * - onboarding: 首次使用引导页
 * - main: 主界面
 * - session/{sessionId}: 会话详情界面
 */
@androidx.compose.runtime.Composable
fun ClawChatNavHost(
    mainViewModel: MainViewModel = hiltViewModel(),
    encryptedStorage: EncryptedStorage,
    gatewayConnection: com.openclaw.clawchat.network.protocol.GatewayConnection
) {
    val navController = rememberNavController()
    
    // 使用 mutableStateOf 观察 isPaired 变化（配对成功后自动更新）
    var isPaired by remember { mutableStateOf(encryptedStorage.isPaired()) }
    
    // 监听配对状态变化
    LaunchedEffect(Unit) {
        // 当从 onboarding 配对成功返回时，重新检查配对状态
        navController.currentBackStackEntryFlow.collect { entry ->
            if (entry.destination.route == "main") {
                isPaired = encryptedStorage.isPaired()
            }
        }
    }
    
    val startDestination = if (isPaired) "main" else "onboarding"

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        // 首次使用引导页
        composable("onboarding") {
            OnboardingScreen(
                onPairingSuccess = {
                    navController.navigate("main") {
                        popUpTo("onboarding") { inclusive = true }
                    }
                }
            )
        }

        // 主屏幕
        composable("main") {
            MainScreen(
                viewModel = mainViewModel,
                onNavigateToSession = { sessionId: String ->
                    navController.navigate("session/$sessionId")
                },
                onNavigateToDebug = {
                    navController.navigate("debug")
                },
                onNavigateToCron = {
                    navController.navigate("cron")
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

        // 设置屏幕
        composable("settings") {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToDebug = { navController.navigate("debug") },
                onNavigateToCron = { navController.navigate("cron") }
            )
        }

        // Cron 屏幕
        composable("cron") {
            CronScreen(
                gateway = gatewayConnection,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // Debug 屏幕
        composable("debug") {
            DebugScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}