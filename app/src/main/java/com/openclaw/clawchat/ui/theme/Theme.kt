package com.openclaw.clawchat.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * 深色主题配色方案
 * 
 * 基于 OpenClaw 品牌色，采用深蓝色系
 */
private val DarkColorScheme = darkColorScheme(
    // 主色
    primary = ClawBlue,
    onPrimary = Color.White,
    primaryContainer = ClawBlueDark,
    onPrimaryContainer = ClawBlueLight,
    
    // 次级色
    secondary = ClawBlueLight,
    onSecondary = Color.White,
    secondaryContainer = DarkBackgroundTertiary,
    onSecondaryContainer = DarkTextPrimary,
    
    // 第三色
    tertiary = Success,
    onTertiary = Color.White,
    tertiaryContainer = Success.copy(alpha = 0.2f),
    onTertiaryContainer = Success,
    
    // 背景
    background = DarkBackgroundPrimary,
    onBackground = DarkTextPrimary,
    
    // 表面
    surface = DarkSurfacePrimary,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkBackgroundSecondary,
    onSurfaceVariant = DarkTextSecondary,
    surfaceTint = ClawBlue,
    
    // 错误
    error = Error,
    onError = Color.White,
    errorContainer = Error.copy(alpha = 0.2f),
    onErrorContainer = Error,
    
    // 边框/分割线
    outline = DarkBackgroundTertiary,
    outlineVariant = DarkTextTertiary,
    
    // 反色
    inverseSurface = LightSurfacePrimary,
    inverseOnSurface = LightTextPrimary,
    inversePrimary = ClawBlueDark,
    
    // 遮罩
    scrim = Color.Black.copy(alpha = 0.32f)
)

/**
 * 浅色主题配色方案
 * 
 * 采用清爽的浅蓝灰色系
 */
private val LightColorScheme = lightColorScheme(
    // 主色
    primary = ClawBlueDark,
    onPrimary = Color.White,
    primaryContainer = ClawBlueLight,
    onPrimaryContainer = Color.White,
    
    // 次级色
    secondary = ClawBlue,
    onSecondary = Color.White,
    secondaryContainer = LightBackgroundTertiary,
    onSecondaryContainer = LightTextPrimary,
    
    // 第三色
    tertiary = Success,
    onTertiary = Color.White,
    tertiaryContainer = Success.copy(alpha = 0.1f),
    onTertiaryContainer = Success,
    
    // 背景
    background = LightBackgroundPrimary,
    onBackground = LightTextPrimary,
    
    // 表面
    surface = LightSurfacePrimary,
    onSurface = LightTextPrimary,
    surfaceVariant = LightBackgroundSecondary,
    onSurfaceVariant = LightTextSecondary,
    surfaceTint = ClawBlueDark,
    
    // 错误
    error = Error,
    onError = Color.White,
    errorContainer = Error.copy(alpha = 0.1f),
    onErrorContainer = Error,
    
    // 边框/分割线
    outline = LightBackgroundTertiary,
    outlineVariant = LightTextTertiary,
    
    // 反色
    inverseSurface = DarkSurfacePrimary,
    inverseOnSurface = DarkTextPrimary,
    inversePrimary = ClawBlueLight,
    
    // 遮罩
    scrim = Color.Black.copy(alpha = 0.32f)
)

/**
 * ClawChat 主题
 * 
 * @param darkTheme 是否使用深色主题，默认跟随系统
 * @param dynamicColor 是否使用动态取色（Android 12+），默认关闭以保持品牌一致性
 * @param content 内容
 */
@Composable
fun ClawChatTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        // Android 12+ 支持动态取色
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        // 深色主题
        darkTheme -> DarkColorScheme
        // 浅色主题
        else -> LightColorScheme
    }
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // 设置状态栏颜色
            window.statusBarColor = colorScheme.background.toArgb()
            // 根据主题设置状态栏图标颜色（深色主题用浅色图标）
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }
    
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

/**
 * 排版样式
 */
val Typography = androidx.compose.material3.Typography(
    displayLarge = HeadingLarge,
    displayMedium = HeadingMedium,
    displaySmall = HeadingSmall,
    headlineLarge = HeadingLarge,
    headlineMedium = HeadingMedium,
    headlineSmall = HeadingSmall,
    titleLarge = HeadingLarge,
    titleMedium = HeadingMedium,
    titleSmall = HeadingSmall,
    bodyLarge = BodyLarge,
    bodyMedium = BodyMedium,
    bodySmall = BodySmall,
    labelLarge = LabelLarge,
    labelMedium = LabelMedium,
    labelSmall = LabelSmall
)
