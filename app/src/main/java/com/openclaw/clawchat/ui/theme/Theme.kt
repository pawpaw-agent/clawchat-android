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

// 深色主题配色方案
private val DarkColorScheme = darkColorScheme(
    primary = ClawBlue,
    onPrimary = Color.White,
    primaryContainer = ClawBlueDark,
    onPrimaryContainer = ClawBlueLight,
    
    secondary = ClawBlueLight,
    onSecondary = Color.White,
    secondaryContainer = BackgroundTertiary,
    onSecondaryContainer = TextPrimary,
    
    tertiary = Success,
    onTertiary = Color.White,
    
    background = BackgroundPrimary,
    onBackground = TextPrimary,
    
    surface = SurfacePrimary,
    onSurface = TextPrimary,
    surfaceVariant = BackgroundSecondary,
    onSurfaceVariant = TextSecondary,
    
    error = Error,
    onError = Color.White,
    
    outline = BackgroundTertiary,
    outlineVariant = TextTertiary
)

// 浅色主题配色方案（备用）
private val LightColorScheme = lightColorScheme(
    primary = ClawBlueDark,
    onPrimary = Color.White,
    primaryContainer = ClawBlueLight,
    onPrimaryContainer = ClawBlue,
    
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF0F172A),
    
    surface = Color.White,
    onSurface = Color(0xFF0F172A)
)

@Composable
fun ClawChatTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // 默认禁用动态取色，保持品牌一致性
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }
    
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

// Typography 实例
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
