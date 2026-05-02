package com.openclaw.clawchat.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Minimal Design Tokens
 * Inspired by Telegram/Slack - clean, monochromatic, generous whitespace
 */
object MinimalTokens {
    // ── Dark Theme Colors ──────────────────────────────────────────
    object Dark {
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

    // ── Light Theme Colors ─────────────────────────────────────────
    object Light {
        val background = Color(0xFFFFFFFF)
        val surface = Color(0xFFF4F4F5)
        val surfaceVariant = Color(0xFFE4E4E7)
        val surfaceElevated = Color(0xFFFFFFFF)

        val primary = Color(0xFF2563EB)
        val primarySubtle = Color(0x1A2563EB)
        val onPrimary = Color(0xFFFFFFFF)

        val textPrimary = Color(0xFF18181B)
        val textSecondary = Color(0xFF71717A)
        val textTertiary = Color(0xFFA1A1AA)

        val border = Color(0xFFE4E4E7)
        val borderSubtle = Color(0xFFF4F4F5)

        val success = Color(0xFF16A34A)
        val warning = Color(0xFFD97706)
        val error = Color(0xFFDC2626)
    }

    // ── Spacing ─────────────────────────────────────────────────────
    val space1 = 4.dp
    val space2 = 6.dp
    val space3 = 8.dp
    val space4 = 12.dp
    val space5 = 16.dp
    val space6 = 20.dp
    val space8 = 24.dp

    // ── Radius ─────────────────────────────────────────────────────
    val radiusSm = 6.dp
    val radiusMd = 8.dp
    val radiusLg = 12.dp
    val radiusFull = 9999.dp

    // ── Typography ──────────────────────────────────────────────────
    val textXs = 11.sp
    val textSm = 13.sp
    val textBase = 15.sp
    val textLg = 17.sp
    val textXl = 20.sp
    val text2xl = 24.sp

    // ── Elevation ───────────────────────────────────────────────────
    val elevationNone = 0.dp
    val elevationSm = 2.dp
    val elevationMd = 4.dp
    val elevationLg = 8.dp

    // ── Animation ───────────────────────────────────────────────────
    const val durationFast = 150
    const val durationNormal = 200
    const val durationSlow = 300

    // ── Component Sizes ─────────────────────────────────────────────
    val bottomNavHeight = 56.dp
    val inputBarHeight = 52.dp
    val avatarSizeSm = 28.dp
    val avatarSizeMd = 36.dp
    val avatarSizeLg = 44.dp
    val iconSizeSm = 16.dp
    val iconSizeMd = 20.dp
    val iconSizeLg = 28.dp
}