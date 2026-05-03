package com.openclaw.clawchat.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Minimal Design Tokens - OpenClaw v2026.4.29 Aligned
 *
 * Aligned with OpenClaw MobileUiTokens patterns:
 * - Semantic color system with light/dark variants
 * - Manrope-inspired typography scale
 * - Mobile-first spacing and radius
 */
object MinimalTokens {
    // ── Typography Scale (Manrope-inspired) ──────────────────────────
    // Using system font as fallback since Manrope font not bundled
    val fontFamily = FontFamily.SansSerif

    val display = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.8).sp
    )

    val title1 = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.5).sp
    )

    val title2 = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.3).sp
    )

    val headline = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = (-0.1).sp
    )

    val body = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 22.sp
    )

    val callout = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp
    )

    val caption1 = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.2.sp
    )

    val caption2 = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.4.sp
    )

    // ── Dark Theme Colors (OpenClaw claw theme aligned) ────────────
    // Colors match openclaw-429 CSS: --bg, --bg-accent, --bg-elevated, --card, --accent
    object Dark {
        // Surface colors
        val surface = Color(0xFF0e1015)       // openclaw --bg: deepest canvas
        val surfaceAccent = Color(0xFF13151b) // openclaw --bg-accent: slightly elevated
        val surfaceElevated = Color(0xFF191c24) // openclaw --bg-elevated: cards/elevated
        val cardSurface = Color(0xFF161920)    // openclaw --card: card surface
        val hover = Color(0xFF1f2330)          // openclaw --bg-hover

        // Borders
        val border = Color(0xFF1e2028)         // openclaw --border: whisper-thin
        val borderStrong = Color(0xFF2e3040)     // openclaw --border-strong

        // Text
        val text = Color(0xFFd4d4d8)           // openclaw --text: body
        val textStrong = Color(0xFFf4f4f5)     // openclaw --text-strong
        val muted = Color(0xFF838387)          // openclaw --muted

        // Accent — OpenClaw signature coral red
        val accent = Color(0xFFff5c5c)
        val accentHover = Color(0xFFff7070)
        val accentSoft = Color(0xFF1a1414)       // subtle accent fill (10% opacity)
        val accentForeground = Color(0xFFfafafa)
        val accentGlow = Color(0x33ff5c5c)       // 20% opacity glow

        // Secondary accent — teal
        val accent2 = Color(0xFF14b8a6)

        // Semantic
        val success = Color(0xFF22c55e)
        val successSoft = Color(0xFF0f1f16)
        val warning = Color(0xFFf59e0b)
        val warningSoft = Color(0xFF2e2212)
        val danger = Color(0xFFef4444)
        val dangerSoft = Color(0xFF2e1616)
        val info = Color(0xFF3b82f6)

        // Code
        val codeBg = Color(0xFF111317)
        val codeText = Color(0xFFe8eaee)
        val codeBorder = Color(0xFF2b2e35)
        val codeAccent = Color(0xFF3fc97a)
    }

    // ── Light Theme Colors ─────────────────────────────────────────
    object Light {
        val surface = Color(0xFFf8f9fa)
        val surfaceAccent = Color(0xFFffffff)
        val surfaceElevated = Color(0xFFf1f3f5)
        val cardSurface = Color(0xFFffffff)
        val hover = Color(0xFFe8eaed)

        val border = Color(0xFFd1d5db)
        val borderStrong = Color(0xFF9ca3af)

        val text = Color(0xFF17181c)
        val textStrong = Color(0xFF0a0a0b)
        val muted = Color(0xFF6b7280)

        // Accent — coral red (darker on light for visibility)
        val accent = Color(0xFFdc2626)
        val accentHover = Color(0xFFef4444)
        val accentSoft = Color(0xFFfef2f2)
        val accentForeground = Color(0xFFffffff)
        val accentGlow = Color(0x33dc2626)

        val accent2 = Color(0xFF0d9488)

        val success = Color(0xFF16a34a)
        val successSoft = Color(0xFFf0fdf4)
        val warning = Color(0xFFd97706)
        val warningSoft = Color(0xFFfffbeb)
        val danger = Color(0xFFdc2626)
        val dangerSoft = Color(0xFFfef2f2)
        val info = Color(0xFF2563eb)

        val codeBg = Color(0xFF1f2937)
        val codeText = Color(0xFFf9fafb)
        val codeBorder = Color(0xFF374151)
        val codeAccent = Color(0xFF34d399)
    }

    // ── Legacy Support ───────────────────────────────────────────────
    // These map to the new semantic colors for backward compatibility
    object Legacy {
        val background = Color(0xFF0F0F0F)
        val surface = Color(0xFF1A1A1A)
        val surfaceVariant = Color(0xFF262626)
        val surfaceElevated = Color(0xFF2A2A2A)

        val primary = Color(0xFF2563EB)
        val primarySubtle = Color(0x1A2563EB)
        val onPrimary = Color(0xFFFFFFFF)

        val textPrimary = Color(0xFFFAFAFA)
        val textSecondary = Color(0xFF71717A)
        val textTertiary = Color(0xFF52525B)

        val border = Color(0xFF27272A)
        val borderSubtle = Color(0xFF1F1F23)

        val success = Color(0xFF22C55E)
        val warning = Color(0xFFF59E0B)
        val error = Color(0xFFEF4444)
    }

    // ── Spacing ──────────────────────────────────────────────────────
    val space1 = 4.dp
    val space2 = 6.dp
    val space3 = 8.dp
    val space4 = 12.dp
    val space5 = 16.dp
    val space6 = 20.dp
    val space8 = 24.dp

    // ── Radius (OpenClaw mobile style) ──────────────────────────────
    val radiusSm = 6.dp
    val radiusMd = 8.dp
    val radiusLg = 12.dp
    val radiusXl = 14.dp  // OpenClaw uses 14dp for chips/selectors
    val radiusFull = 9999.dp

    // ── Typography ────────────────────────────────────────────────────
    val textXs = 11.sp
    val textSm = 13.sp
    val textBase = 15.sp
    val textLg = 17.sp
    val textXl = 20.sp
    val text2xl = 24.sp

    // ── Elevation ────────────────────────────────────────────────────
    val elevationNone = 0.dp
    val elevationSm = 2.dp
    val elevationMd = 4.dp
    val elevationLg = 8.dp

    // ── Animation ────────────────────────────────────────────────────
    const val durationFast = 150
    const val durationNormal = 200
    const val durationSlow = 300

    // ── Component Sizes ──────────────────────────────────────────────
    val bottomNavHeight = 56.dp
    val inputBarHeight = 52.dp
    val avatarSizeSm = 28.dp
    val avatarSizeMd = 36.dp
    val avatarSizeLg = 44.dp
    val iconSizeSm = 16.dp
    val iconSizeMd = 20.dp
    val iconSizeLg = 28.dp
}