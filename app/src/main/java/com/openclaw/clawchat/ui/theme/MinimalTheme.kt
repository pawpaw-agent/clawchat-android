package com.openclaw.clawchat.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// OpenClaw claw theme aligned color schemes
private val DarkColorScheme = darkColorScheme(
    // Primary — coral red accent
    primary = MinimalTokens.Dark.accent,
    onPrimary = MinimalTokens.Dark.accentForeground,
    primaryContainer = MinimalTokens.Dark.accentSoft,
    onPrimaryContainer = MinimalTokens.Dark.accent,

    // Secondary — teal accent2
    secondary = MinimalTokens.Dark.accent2,
    onSecondary = MinimalTokens.Dark.accentForeground,
    secondaryContainer = MinimalTokens.Dark.surfaceElevated,
    onSecondaryContainer = MinimalTokens.Dark.text,

    // Tertiary — same as primary (coral red)
    tertiary = MinimalTokens.Dark.accent,
    onTertiary = MinimalTokens.Dark.accentForeground,
    tertiaryContainer = MinimalTokens.Dark.accentSoft,
    onTertiaryContainer = MinimalTokens.Dark.accent,

    // Background / Surface
    background = MinimalTokens.Dark.surface,
    onBackground = MinimalTokens.Dark.text,

    surface = MinimalTokens.Dark.surface,
    onSurface = MinimalTokens.Dark.text,
    surfaceVariant = MinimalTokens.Dark.cardSurface,
    onSurfaceVariant = MinimalTokens.Dark.muted,

    // Error
    error = MinimalTokens.Dark.danger,
    onError = MinimalTokens.Dark.accentForeground,
    errorContainer = MinimalTokens.Dark.dangerSoft,
    onErrorContainer = MinimalTokens.Dark.danger,

    // Borders
    outline = MinimalTokens.Dark.border,
    outlineVariant = MinimalTokens.Dark.borderStrong,

    // Inverse (for dialogs on dark surfaces)
    inverseSurface = MinimalTokens.Dark.surfaceAccent,
    inverseOnSurface = MinimalTokens.Dark.text,
    inversePrimary = MinimalTokens.Dark.accent,

    scrim = MinimalTokens.Dark.surface.copy(alpha = 0.32f)
)

private val LightColorScheme = lightColorScheme(
    // Primary — coral red accent
    primary = MinimalTokens.Light.accent,
    onPrimary = MinimalTokens.Light.accentForeground,
    primaryContainer = MinimalTokens.Light.accentSoft,
    onPrimaryContainer = MinimalTokens.Light.accent,

    // Secondary — teal accent2
    secondary = MinimalTokens.Light.accent2,
    onSecondary = MinimalTokens.Light.accentForeground,
    secondaryContainer = MinimalTokens.Light.surfaceElevated,
    onSecondaryContainer = MinimalTokens.Light.text,

    // Tertiary — same as primary
    tertiary = MinimalTokens.Light.accent,
    onTertiary = MinimalTokens.Light.accentForeground,
    tertiaryContainer = MinimalTokens.Light.accentSoft,
    onTertiaryContainer = MinimalTokens.Light.accent,

    // Background / Surface
    background = MinimalTokens.Light.surface,
    onBackground = MinimalTokens.Light.text,

    surface = MinimalTokens.Light.surface,
    onSurface = MinimalTokens.Light.text,
    surfaceVariant = MinimalTokens.Light.cardSurface,
    onSurfaceVariant = MinimalTokens.Light.muted,

    // Error
    error = MinimalTokens.Light.danger,
    onError = MinimalTokens.Light.accentForeground,
    errorContainer = MinimalTokens.Light.dangerSoft,
    onErrorContainer = MinimalTokens.Light.danger,

    // Borders
    outline = MinimalTokens.Light.border,
    outlineVariant = MinimalTokens.Light.borderStrong,

    // Inverse
    inverseSurface = MinimalTokens.Light.surfaceAccent,
    inverseOnSurface = MinimalTokens.Light.text,
    inversePrimary = MinimalTokens.Light.accent,

    scrim = MinimalTokens.Light.surface.copy(alpha = 0.32f)
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