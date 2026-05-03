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
    primary = MinimalTokens.Legacy.primary,
    onPrimary = MinimalTokens.Legacy.onPrimary,
    primaryContainer = MinimalTokens.Legacy.primarySubtle,
    onPrimaryContainer = MinimalTokens.Legacy.textPrimary,

    secondary = MinimalTokens.Dark.surfaceVariant,
    onSecondary = MinimalTokens.Dark.textPrimary,
    secondaryContainer = MinimalTokens.Dark.surfaceVariant,
    onSecondaryContainer = MinimalTokens.Dark.textPrimary,

    tertiary = MinimalTokens.Legacy.primary,
    onTertiary = MinimalTokens.Legacy.onPrimary,
    tertiaryContainer = MinimalTokens.Legacy.primarySubtle,
    onTertiaryContainer = MinimalTokens.Legacy.primary,

    background = MinimalTokens.Legacy.background,
    onBackground = MinimalTokens.Legacy.textPrimary,

    surface = MinimalTokens.Dark.surface,
    onSurface = MinimalTokens.Dark.text,
    surfaceVariant = MinimalTokens.Dark.surfaceStrong,
    onSurfaceVariant = MinimalTokens.Dark.textSecondary,

    error = MinimalTokens.Legacy.error,
    onError = Color.White,
    errorContainer = MinimalTokens.Dark.dangerSoft,
    onErrorContainer = MinimalTokens.Dark.danger,

    outline = MinimalTokens.Dark.border,
    outlineVariant = MinimalTokens.Dark.borderStrong,

    inverseSurface = MinimalTokens.Light.background,
    inverseOnSurface = MinimalTokens.Light.textPrimary,
    inversePrimary = MinimalTokens.Light.primary,

    scrim = Color.Black.copy(alpha = 0.32f)
)

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
    onSurface = MinimalTokens.Light.text,
    surfaceVariant = MinimalTokens.Light.surfaceStrong,
    onSurfaceVariant = MinimalTokens.Light.textSecondary,

    error = MinimalTokens.Legacy.error,
    onError = Color.White,
    errorContainer = MinimalTokens.Light.dangerSoft,
    onErrorContainer = MinimalTokens.Light.danger,

    outline = MinimalTokens.Light.border,
    outlineVariant = MinimalTokens.Light.borderStrong,

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