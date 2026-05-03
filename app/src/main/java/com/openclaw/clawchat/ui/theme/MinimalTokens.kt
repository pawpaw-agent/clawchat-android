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

    // ── Dark Theme Colors (OpenClaw MobileColors aligned) ────────────
    object Dark {
        val surface = Color(0xFF1A1C20)
        val surfaceStrong = Color(0xFF24262B)
        val cardSurface = Color(0xFF1E2024)
        val border = Color(0xFF2E3038)
        val borderStrong = Color(0xFF3A3D46)

        val text = Color(0xFFE4E5EA)
        val textSecondary = Color(0xFFA0A6B4)
        val textTertiary = Color(0xFF6B7280)

        val accent = Color(0xFF6EA8FF)
        val accentSoft = Color(0xFF1A2A44)
        val accentBorderStrong = Color(0xFF5B93E8)

        val success = Color(0xFF5FBB85)
        val successSoft = Color(0xFF152E22)
        val warning = Color(0xFFE8A844)
        val warningSoft = Color(0xFF2E2212)
        val danger = Color(0xFFE87070)
        val dangerSoft = Color(0xFF2E1616)

        val codeBg = Color(0xFF111317)
        val codeText = Color(0xFFE8EAEE)
        val codeBorder = Color(0xFF2B2E35)
        val codeAccent = Color(0xFF3FC97A)
    }

    // ── Light Theme Colors (OpenClaw MobileColors aligned) ───────────
    object Light {
        val surface = Color(0xFFF6F7FA)
        val surfaceStrong = Color(0xFFECEEF3)
        val cardSurface = Color(0xFFFFFFFF)
        val border = Color(0xFFE5E7EC)
        val borderStrong = Color(0xFFD6DAE2)

        val text = Color(0xFF17181C)
        val textSecondary = Color(0xFF5D6472)
        val textTertiary = Color(0xFF99A0AE)

        val accent = Color(0xFF1D5DD8)
        val accentSoft = Color(0xFFECF3FF)
        val accentBorderStrong = Color(0xFF184DAF)

        val success = Color(0xFF2F8C5A)
        val successSoft = Color(0xFFEEF9F3)
        val warning = Color(0xFFC8841A)
        val warningSoft = Color(0xFFFFF8EC)
        val danger = Color(0xFFD04B4B)
        val dangerSoft = Color(0xFFFFF2F2)

        val codeBg = Color(0xFF15171B)
        val codeText = Color(0xFFE8EAEE)
        val codeBorder = Color(0xFF2B2E35)
        val codeAccent = Color(0xFF3FC97A)
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