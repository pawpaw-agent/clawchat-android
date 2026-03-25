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
 * 1:1 复刻 webchat base.css 深色主题
 */
private val DarkColorScheme = darkColorScheme(
    // 主色 - 红色强调
    primary = DesignTokens.accent,           // #ff5c5c
    onPrimary = DesignTokens.accentForeground,
    primaryContainer = DesignTokens.accentContainer,
    onPrimaryContainer = DesignTokens.accentForeground,
    
    // 次级色
    secondary = DesignTokens.secondary,
    onSecondary = DesignTokens.secondaryForeground,
    secondaryContainer = DesignTokens.bgHover,
    onSecondaryContainer = DesignTokens.textStrong,
    
    // 第三色
    tertiary = DesignTokens.accent2,
    onTertiary = Color.White,
    tertiaryContainer = DesignTokens.accent2Subtle,
    onTertiaryContainer = DesignTokens.accent2,
    
    // 背景
    background = DesignTokens.bg,            // #0e1015
    onBackground = DesignTokens.text,        // #d4d4d8
    
    // 表面
    surface = DesignTokens.card,             // #161920
    onSurface = DesignTokens.text,
    surfaceVariant = DesignTokens.bgAccent,  // #13151b
    onSurfaceVariant = DesignTokens.muted,
    surfaceTint = DesignTokens.accent,
    
    // 错误
    error = DesignTokens.danger,             // #ef4444
    onError = Color.White,
    errorContainer = DesignTokens.dangerSubtle,
    onErrorContainer = DesignTokens.danger,
    
    // 边框/分割线
    outline = DesignTokens.border,           // #1e2028
    outlineVariant = DesignTokens.borderStrong,
    
    // 反色
    inverseSurface = DesignTokens.Light.bg,
    inverseOnSurface = DesignTokens.Light.text,
    inversePrimary = DesignTokens.Light.accent,
    
    // 遮罩
    scrim = Color.Black.copy(alpha = 0.52f)
)

/**
 * 浅色主题配色方案
 * 
 * 1:1 复刻 webchat base.css 浅色主题
 */
private val LightColorScheme = lightColorScheme(
    // 主色 - 深红强调
    primary = DesignTokens.Light.accent,     // #dc2626
    onPrimary = DesignTokens.Light.accentForeground,
    primaryContainer = DesignTokens.Light.accentSubtle,
    onPrimaryContainer = DesignTokens.Light.accent,
    
    // 次级色
    secondary = DesignTokens.Light.secondary,
    onSecondary = DesignTokens.Light.secondaryForeground,
    secondaryContainer = DesignTokens.Light.bgHover,
    onSecondaryContainer = DesignTokens.Light.text,
    
    // 第三色
    tertiary = DesignTokens.Light.accent2,
    onTertiary = Color.White,
    tertiaryContainer = DesignTokens.Light.accent2Subtle,
    onTertiaryContainer = DesignTokens.Light.accent2,
    
    // 背景
    background = DesignTokens.Light.bg,      // #f8f9fa
    onBackground = DesignTokens.Light.text,  // #3c3c43
    
    // 表面
    surface = DesignTokens.Light.card,       // #ffffff
    onSurface = DesignTokens.Light.text,
    surfaceVariant = DesignTokens.Light.bgAccent,
    onSurfaceVariant = DesignTokens.Light.muted,
    surfaceTint = DesignTokens.Light.accent,
    
    // 错误
    error = DesignTokens.Light.danger,
    onError = Color.White,
    errorContainer = DesignTokens.Light.dangerSubtle,
    onErrorContainer = DesignTokens.Light.danger,
    
    // 边框/分割线
    outline = DesignTokens.Light.border,     // #e5e5ea
    outlineVariant = DesignTokens.Light.borderStrong,
    
    // 反色
    inverseSurface = DesignTokens.bg,
    inverseOnSurface = DesignTokens.text,
    inversePrimary = DesignTokens.accent,
    
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

/**
 * TerminalFlow 深色主题配色
 * 主色：灰色 #6B7280
 */
private val TerminalDarkColorScheme = darkColorScheme(
    primary = Color(0xFF6B7280),           // 灰色
    onPrimary = Color.White,
    primaryContainer = Color(0xFF374151),
    onPrimaryContainer = Color(0xFFE5E7EB),
    
    secondary = TerminalColors.TextCode,
    onSecondary = TerminalColors.TerminalBlack,
    secondaryContainer = TerminalColors.BubbleUser,
    onSecondaryContainer = TerminalColors.TextPrimary,
    
    tertiary = TerminalColors.StatusActive,
    onTertiary = TerminalColors.TerminalBlack,
    
    background = TerminalColors.TerminalBg,
    onBackground = TerminalColors.TextPrimary,
    surface = TerminalColors.TerminalSurface,
    onSurface = TerminalColors.TextPrimary,
    surfaceVariant = TerminalColors.TerminalElevated,
    onSurfaceVariant = TerminalColors.TextSecondary,
    
    error = TerminalColors.StatusError,
    onError = Color.White,
    errorContainer = TerminalColors.StatusError.copy(alpha = 0.2f),
    onErrorContainer = TerminalColors.StatusError,
    
    outline = TerminalColors.Border,
    outlineVariant = TerminalColors.BorderStrong
)

/**
 * TerminalFlow 浅色主题配色
 * 主色：浅蓝色 #3B82F6
 */
private val TerminalLightColorScheme = lightColorScheme(
    primary = Color(0xFF3B82F6),           // 浅蓝色
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDBEAFE),  // 浅蓝背景
    onPrimaryContainer = Color(0xFF1E40AF),
    
    secondary = Color(0xFF64748B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF1F5F9),
    onSecondaryContainer = Color(0xFF334155),
    
    tertiary = Color(0xFF10B981),
    onTertiary = Color.White,
    
    background = LightTerminalColors.TerminalLight,
    onBackground = LightTerminalColors.TextPrimary,
    surface = LightTerminalColors.TerminalSurface,
    onSurface = LightTerminalColors.TextPrimary,
    surfaceVariant = LightTerminalColors.TerminalBg,
    onSurfaceVariant = LightTerminalColors.TextSecondary,
    
    error = Color(0xFFEF4444),
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF991B1B),
    
    outline = Color(0xFFE2E8F0),
    outlineVariant = Color(0xFFCBD5E1)
)

/**
 * TerminalFlow 主题
 * 终端美学 + 琥珀色强调
 * 支持深色和浅色两种模式
 */
@Composable
fun TerminalFlowTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) {
        TerminalDarkColorScheme
    } else {
        TerminalLightColorScheme
    }
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = if (darkTheme) {
                TerminalColors.TerminalBg.toArgb()
            } else {
                LightTerminalColors.TerminalLight.toArgb()
            }
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }
    
    MaterialTheme(
        colorScheme = colorScheme,
        typography = androidx.compose.material3.Typography(),
        content = content
    )
}