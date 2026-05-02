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

private val DarkColorScheme = darkColorScheme(
    primary = MinimalTokens.Dark.primary,
    onPrimary = MinimalTokens.Dark.onPrimary,
    primaryContainer = MinimalTokens.Dark.primarySubtle,
    onPrimaryContainer = MinimalTokens.Dark.textPrimary,

    secondary = MinimalTokens.Dark.surfaceVariant,
    onSecondary = MinimalTokens.Dark.textPrimary,
    secondaryContainer = MinimalTokens.Dark.surfaceVariant,
    onSecondaryContainer = MinimalTokens.Dark.textPrimary,

    tertiary = MinimalTokens.Dark.primary,
    onTertiary = MinimalTokens.Dark.onPrimary,
    tertiaryContainer = MinimalTokens.Dark.primarySubtle,
    onTertiaryContainer = MinimalTokens.Dark.primary,

    background = MinimalTokens.Dark.background,
    onBackground = MinimalTokens.Dark.textPrimary,

    surface = MinimalTokens.Dark.surface,
    onSurface = MinimalTokens.Dark.textPrimary,
    surfaceVariant = MinimalTokens.Dark.surfaceVariant,
    onSurfaceVariant = MinimalTokens.Dark.textSecondary,

    error = MinimalTokens.Dark.error,
    onError = Color.White,
    errorContainer = MinimalTokens.Dark.error.copy(alpha = 0.1f),
    onErrorContainer = MinimalTokens.Dark.error,

    outline = MinimalTokens.Dark.border,
    outlineVariant = MinimalTokens.Dark.borderSubtle,

    inverseSurface = MinimalTokens.Light.background,
    inverseOnSurface = MinimalTokens.Light.textPrimary,
    inversePrimary = MinimalTokens.Light.primary,

    scrim = Color.Black.copy(alpha = 0.32f)
)

private val LightColorScheme = lightColorScheme(
    primary = MinimalTokens.Light.primary,
    onPrimary = MinimalTokens.Light.onPrimary,
    primaryContainer = MinimalTokens.Light.primarySubtle,
    onPrimaryContainer = MinimalTokens.Light.primary,

    secondary = MinimalTokens.Light.surfaceVariant,
    onSecondary = MinimalTokens.Light.textPrimary,
    secondaryContainer = MinimalTokens.Light.surfaceVariant,
    onSecondaryContainer = MinimalTokens.Light.textPrimary,

    tertiary = MinimalTokens.Light.primary,
    onTertiary = MinimalTokens.Light.onPrimary,
    tertiaryContainer = MinimalTokens.Light.primarySubtle,
    onTertiaryContainer = MinimalTokens.Light.primary,

    background = MinimalTokens.Light.background,
    onBackground = MinimalTokens.Light.textPrimary,

    surface = MinimalTokens.Light.surface,
    onSurface = MinimalTokens.Light.textPrimary,
    surfaceVariant = MinimalTokens.Light.surfaceVariant,
    onSurfaceVariant = MinimalTokens.Light.textSecondary,

    error = MinimalTokens.Light.error,
    onError = Color.White,
    errorContainer = MinimalTokens.Light.error.copy(alpha = 0.1f),
    onErrorContainer = MinimalTokens.Light.error,

    outline = MinimalTokens.Light.border,
    outlineVariant = MinimalTokens.Light.borderSubtle,

    inverseSurface = MinimalTokens.Dark.background,
    inverseOnSurface = MinimalTokens.Dark.textPrimary,
    inversePrimary = MinimalTokens.Dark.primary,

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