package com.openclaw.clawchat.ui.components

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * 颜色工具类
 * 提供主题色相关功能
 */
object ColorUtils {
    
    // ─────────────────────────────────────────────────────────────
    // 预设主题色（8 种）
    // ─────────────────────────────────────────────────────────────
    
    val PRESET_COLORS = listOf(
        // Blue (Material3 默认)
        ColorPreset(
            name = "蓝色",
            primary = Color(0xFF6750A4),
            secondary = Color(0xFF625B71),
            accent = Color(0xFF7D5260)
        ),
        // Purple
        ColorPreset(
            name = "紫色",
            primary = Color(0xFF9C27B0),
            secondary = Color(0xFFBA68C8),
            accent = Color(0xFFE1BEE7)
        ),
        // Teal
        ColorPreset(
            name = "青色",
            primary = Color(0xFF009688),
            secondary = Color(0xFF4DB6AC),
            accent = Color(0xFFB2DFDB)
        ),
        // Orange
        ColorPreset(
            name = "橙色",
            primary = Color(0xFFFF9800),
            secondary = Color(0xFFFFB74D),
            accent = Color(0xFFFFE0B2)
        ),
        // Pink
        ColorPreset(
            name = "粉色",
            primary = Color(0xFFE91E63),
            secondary = Color(0xFFF06292),
            accent = Color(0xFFF8BBD0)
        ),
        // Green
        ColorPreset(
            name = "绿色",
            primary = Color(0xFF4CAF50),
            secondary = Color(0xFF81C784),
            accent = Color(0xFFC8E6C9)
        ),
        // Red
        ColorPreset(
            name = "红色",
            primary = Color(0xFFF44336),
            secondary = Color(0xFFE57373),
            accent = Color(0xFFFFCDD2)
        ),
        // Indigo
        ColorPreset(
            name = "靛蓝",
            primary = Color(0xFF3F51B5),
            secondary = Color(0xFF7986CB),
            accent = Color(0xFFC5CAE9)
        )
    )
    
    /**
     * 获取当前主题的主色
     */
    @Composable
    fun getCurrentPrimaryColor(): Color {
        return MaterialTheme.colorScheme.primary
    }
    
    /**
     * 获取当前主题的强调色
     */
    @Composable
    fun getCurrentAccentColor(): Color {
        return MaterialTheme.colorScheme.tertiary
    }
    
    /**
     * 计算对比色（用于文本）
     */
    fun getContrastColor(backgroundColor: Color): Color {
        val luminance = (0.299 * backgroundColor.red + 
                        0.587 * backgroundColor.green + 
                        0.114 * backgroundColor.blue)
        return if (luminance > 0.5f) Color.Black else Color.White
    }
    
    /**
     * 生成渐变色
     */
    fun createGradientColors(baseColor: Color): Pair<Color, Color> {
        val darker = baseColor.copy(
            red = (baseColor.red * 0.8f).coerceIn(0f, 1f),
            green = (baseColor.green * 0.8f).coerceIn(0f, 1f),
            blue = (baseColor.blue * 0.8f).coerceIn(0f, 1f)
        )
        return Pair(baseColor, darker)
    }
}

/**
 * 预设颜色配置
 */
data class ColorPreset(
    val name: String,
    val primary: Color,
    val secondary: Color,
    val accent: Color,
    val id: Int = 0
)

/**
 * 主题管理工具
 */
object ThemeUtils {
    
    /**
     * 应用预设主题色到 ColorScheme
     */
    fun applyColorPreset(
        preset: ColorPreset,
        isDark: Boolean
    ): ColorScheme {
        return if (isDark) {
            createDarkColorScheme(preset)
        } else {
            createLightColorScheme(preset)
        }
    }
    
    private fun createLightColorScheme(preset: ColorPreset): ColorScheme {
        return lightColorScheme(
            primary = preset.primary,
            onPrimary = ColorUtils.getContrastColor(preset.primary),
            primaryContainer = preset.primary.copy(alpha = 0.2f),
            onPrimaryContainer = preset.primary,
            secondary = preset.secondary,
            onSecondary = ColorUtils.getContrastColor(preset.secondary),
            secondaryContainer = preset.secondary.copy(alpha = 0.2f),
            onSecondaryContainer = preset.secondary,
            tertiary = preset.accent,
            onTertiary = ColorUtils.getContrastColor(preset.accent),
            tertiaryContainer = preset.accent.copy(alpha = 0.2f),
            onTertiaryContainer = preset.accent,
            background = Color.White,
            onBackground = Color.Black,
            surface = Color.White,
            onSurface = Color.Black,
            surfaceVariant = preset.primary.copy(alpha = 0.1f),
            onSurfaceVariant = preset.primary,
            outline = preset.primary.copy(alpha = 0.5f),
            outlineVariant = preset.primary.copy(alpha = 0.3f),
            inverseSurface = Color.DarkGray,
            inverseOnSurface = Color.White,
            inversePrimary = preset.primary,
            scrim = Color.Black.copy(alpha = 0.3f)
        )
    }
    
    private fun createDarkColorScheme(preset: ColorPreset): ColorScheme {
        return darkColorScheme(
            primary = preset.primary,
            onPrimary = ColorUtils.getContrastColor(preset.primary),
            primaryContainer = preset.primary.copy(alpha = 0.3f),
            onPrimaryContainer = preset.primary,
            secondary = preset.secondary,
            onSecondary = ColorUtils.getContrastColor(preset.secondary),
            secondaryContainer = preset.secondary.copy(alpha = 0.3f),
            onSecondaryContainer = preset.secondary,
            tertiary = preset.accent,
            onTertiary = ColorUtils.getContrastColor(preset.accent),
            tertiaryContainer = preset.accent.copy(alpha = 0.3f),
            onTertiaryContainer = preset.accent,
            background = Color(0xFF1C1B1F),
            onBackground = Color(0xFFE6E1E5),
            surface = Color(0xFF1C1B1F),
            onSurface = Color(0xFFE6E1E5),
            surfaceVariant = preset.primary.copy(alpha = 0.2f),
            onSurfaceVariant = preset.primary,
            outline = preset.primary.copy(alpha = 0.7f),
            outlineVariant = preset.primary.copy(alpha = 0.5f),
            inverseSurface = Color.LightGray,
            inverseOnSurface = Color.Black,
            inversePrimary = preset.primary,
            scrim = Color.Black.copy(alpha = 0.5f)
        )
    }
}