package com.openclaw.clawchat.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * ClawChat 设计系统
 * 对应 WebChat base.css 设计 tokens
 */
object DesignTokens {
    // ─────────────────────────────────────────────────────────────
    // 颜色 - 深色主题（对应 WebChat dark theme）
    // ─────────────────────────────────────────────────────────────
    
    // 背景层次
    val bg = Color(0xFF0E1015)
    val bgAccent = Color(0xFF13151B)
    val bgElevated = Color(0xFF191C24)
    val bgHover = Color(0xFF1F2330)
    val bgMuted = Color(0xFF1F2330)
    
    // 卡片/表面
    val card = Color(0xFF161920)
    val cardForeground = Color(0xFFF0F0F2)
    val panel = Color(0xFF0E1015)
    val panelStrong = Color(0xFF191C24)
    
    // 文本
    val text = Color(0xFFD4D4D8)
    val textStrong = Color(0xFFF4F4F5)
    val muted = Color(0xFF838387)
    val mutedStrong = Color(0xFF62626A)
    
    // 边框
    val border = Color(0xFF1E2028)
    val borderStrong = Color(0xFF2E3040)
    val borderHover = Color(0xFF3E4050)
    
    // 强调色 - 红色
    val accent = Color(0xFFFF5C5C)
    val accentHover = Color(0xFFFF7070)
    val accentSubtle = Color(0x1AFF5C5C)  // 10% opacity
    val accentForeground = Color(0xFFFAFAFA)
    
    // 次要强调色 - 青色
    val accent2 = Color(0xFF14B8A6)
    val accent2Subtle = Color(0x1A14B8A6)
    
    // 语义色
    val ok = Color(0xFF22C55E)
    val okSubtle = Color(0x1422C55E)
    val warn = Color(0xFFF59E0B)
    val warnSubtle = Color(0x14F59E0B)
    val danger = Color(0xFFEF4444)
    val dangerSubtle = Color(0x14EF4444)
    val info = Color(0xFF3B82F6)
    
    // ─────────────────────────────────────────────────────────────
    // 颜色 - 浅色主题（对应 WebChat light theme）
    // ─────────────────────────────────────────────────────────────
    
    object Light {
        val bg = Color(0xFFF8F9FA)
        val bgAccent = Color(0xFFF1F3F5)
        val bgElevated = Color(0xFFFFFFFF)
        val bgHover = Color(0xFFECEEF0)
        
        val card = Color(0xFFFFFFFF)
        val cardForeground = Color(0xFF1A1A1E)
        val panel = Color(0xFFF8F9FA)
        val panelStrong = Color(0xFFF1F3F5)
        
        val text = Color(0xFF3C3C43)
        val textStrong = Color(0xFF1A1A1E)
        val muted = Color(0xFF6E6E73)
        
        val border = Color(0xFFE5E5EA)
        val borderStrong = Color(0xFFD1D1D6)
        
        val accent = Color(0xFFDC2626)
        val accentHover = Color(0xFFEF4444)
        val accentSubtle = Color(0x14DC2626)
    }
    
    // ─────────────────────────────────────────────────────────────
    // 圆角（对应 WebChat --radius-*）
    // ─────────────────────────────────────────────────────────────
    
    val radiusSm = 6.dp
    val radiusMd = 10.dp
    val radiusLg = 14.dp
    val radiusXl = 20.dp
    val radiusFull = 9999.dp
    
    // ─────────────────────────────────────────────────────────────
    // 间距
    // ─────────────────────────────────────────────────────────────
    
    val space0 = 0.dp
    val space1 = 4.dp
    val space2 = 8.dp
    val space3 = 12.dp
    val space4 = 16.dp
    val space5 = 20.dp
    val space6 = 24.dp
    val space8 = 32.dp
    val space10 = 40.dp
    val space12 = 48.dp
    
    // ─────────────────────────────────────────────────────────────
    // 字体大小
    // ─────────────────────────────────────────────────────────────
    
    val textXs = 11.sp
    val textSm = 12.sp
    val textBase = 13.5.sp
    val textMd = 14.sp
    val textLg = 16.sp
    val textXl = 18.sp
    val text2xl = 20.sp
    val text3xl = 24.sp
    
    // ─────────────────────────────────────────────────────────────
    // 阴影（对应 WebChat --shadow-*）
    // ─────────────────────────────────────────────────────────────
    
    val elevationSm = 1.dp
    val elevationMd = 4.dp
    val elevationLg = 12.dp
    val elevationXl = 24.dp
    
    // ─────────────────────────────────────────────────────────────
    // 动画时长（对应 WebChat --duration-*）
    // ─────────────────────────────────────────────────────────────
    
    const val durationFast = 100
    const val durationNormal = 180
    const val durationSlow = 300
}

/**
 * 消息气泡样式（对应 WebChat chat/grouped.css）
 */
object ChatTokens {
    // 头像
    val avatarSize = 36.dp
    val avatarRadius = 10.dp
    
    // 气泡
    val bubblePaddingH = 14.dp
    val bubblePaddingV = 10.dp
    val bubbleRadius = 14.dp
    val bubbleMaxWidth = 900.dp
    
    // 用户气泡
    val userBubbleBg = DesignTokens.accentSubtle
    val userBubbleBorder = Color(0x33FF5C5C)  // 20% accent
    
    // 助手气泡
    val assistantBubbleBg = DesignTokens.card
    val assistantBubbleBorder = DesignTokens.border
    
    // 消息组间距
    val groupGap = 14.dp
    val messageGap = 2.dp
    val footerGap = 6.dp
    
    // 时间戳
    val timestampSize = 11.sp
    
    // 工具卡片
    val toolCardRadius = 8.dp
    val toolCardPadding = 12.dp
    val toolTagRadius = 999.dp
    val toolTagPaddingH = 8.dp
    val toolTagPaddingV = 4.dp
}