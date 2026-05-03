package com.openclaw.clawchat.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Dark theme - using Legacy colors for Material3 compatibility
private val DarkColorScheme = darkColorScheme(
    primary = MinimalTokens.Legacy.primary,
    onPrimary = MinimalTokens.Legacy.onPrimary,
    primaryContainer = MinimalTokens.Legacy.primarySubtle,
    onPrimaryContainer = MinimalTokens.Legacy.textPrimary,

    secondary = MinimalTokens.Legacy.surfaceVariant,
    onSecondary = MinimalTokens.Legacy.textPrimary,
    secondaryContainer = MinimalTokens.Legacy.surfaceVariant,
    onSecondaryContainer = MinimalTokens.Legacy.textPrimary,

    tertiary = MinimalTokens.Legacy.primary,
    onTertiary = MinimalTokens.Legacy.onPrimary,
    tertiaryContainer = MinimalTokens.Legacy.primarySubtle,
    onTertiaryContainer = MinimalTokens.Legacy.primary,

    background = MinimalTokens.Legacy.background,
    onBackground = MinimalTokens.Legacy.textPrimary,

    surface = MinimalTokens.Legacy.surface,
    onSurface = MinimalTokens.Legacy.textPrimary,
    surfaceVariant = MinimalTokens.Legacy.surfaceVariant,
    onSurfaceVariant = MinimalTokens.Legacy.textSecondary,

    error = MinimalTokens.Legacy.error,
    onError = Color.White,
    errorContainer = MinimalTokens.Legacy.error.copy(alpha = 0.1f),
    onErrorContainer = MinimalTokens.Legacy.error,

    outline = MinimalTokens.Legacy.border,
    outlineVariant = MinimalTokens.Legacy.borderSubtle,

    inverseSurface = MinimalTokens.Legacy.background,
    inverseOnSurface = MinimalTokens.Legacy.textPrimary,
    inversePrimary = MinimalTokens.Legacy.primary,

    scrim = Color.Black.copy(alpha = 0.32f)
)

// Light theme - using Legacy colors for Material3 compatibility
private val LightColorScheme = lightColorScheme(
    primary = MinimalTokens.Legacy.primary,
    onPrimary = MinimalTokens.Legacy.onPrimary,
    primaryContainer = MinimalTokens.Legacy.primarySubtle,
    onPrimaryContainer = MinimalTokens.Legacy.primary,

    secondary = MinimalTokens.Light.surfaceVariant,
    onSecondary = MinimalTokens.Light.textPrimary,
    secondaryContainer = MinimalTokens.Light.surfaceVariant,
    onSecondaryContainer = MinimalTokens.Light.textPrimary,

    tertiary = MinimalTokens.Legacy.primary,
    onTertiary = MinimalTokens.Legacy.onPrimary,
    tertiaryContainer = MinimalTokens.Legacy.primarySubtle,
    onTertiaryContainer = MinimalTokens.Legacy.primary,

    background = MinimalTokens.Legacy.background,
    onBackground = MinimalTokens.Legacy.textPrimary,

    surface = MinimalTokens.Light.surface,
    onSurface = MinimalTokens.Light.textPrimary,
    surfaceVariant = MinimalTokens.Light.surfaceVariant,
    onSurfaceVariant = MinimalTokens.Light.textSecondary,

    error = MinimalTokens.Legacy.error,
    onError = Color.White,
    errorContainer = MinimalTokens.Legacy.error.copy(alpha = 0.1f),
    onErrorContainer = MinimalTokens.Legacy.error,

    outline = MinimalTokens.Light.border,
    outlineVariant = MinimalTokens.Light.borderSubtle,

    inverseSurface = MinimalTokens.Legacy.surface,
    inverseOnSurface = MinimalTokens.Legacy.textPrimary,
    inversePrimary = MinimalTokens.Legacy.primary,

    scrim = Color.Black.copy(alpha = 0.32f)
)

@Composable
fun MinimalTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

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
        typography = MinimalTypography,
        content = content
    )
}