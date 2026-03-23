package com.openclaw.clawchat.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * ClawChat 设计系统
 * 基于 DESIGN-SYSTEM.md 规范
 * 源文件：WebChat base.css
 */
object DesignTokens {
    // ─────────────────────────────────────────────────────────────
    // 颜色 - 深色主题 (默认)
    // ─────────────────────────────────────────────────────────────
    
    // 背景层次
    val bg = Color(0xFF0E1015)           // 主背景
    val bgAccent = Color(0xFF13151B)     // 次级背景
    val bgElevated = Color(0xFF191C24)   // 浮层背景
    val bgHover = Color(0xFF1F2330)      // 悬停状态
    val bgMuted = Color(0xFF1F2330)      // 静音背景
    
    // 卡片/表面
    val card = Color(0xFF161920)         // 卡片背景
    val cardForeground = Color(0xFFF0F0F2)
    val popover = Color(0xFF191C24)      // 弹出菜单
    val panel = Color(0xFF0E1015)        // 面板背景
    val panelStrong = Color(0xFF191C24)
    
    // 文本
    val text = Color(0xFFD4D4D8)         // 正文文字
    val textStrong = Color(0xFFF4F4F5)   // 强调文字
    val muted = Color(0xFF838387)        // 次要文字
    val mutedStrong = Color(0xFF62626A)
    
    // 边框
    val border = Color(0xFF1E2028)       // 默认边框
    val borderStrong = Color(0xFF2E3040) // 强调边框
    val borderHover = Color(0xFF3E4050)  // 悬停边框
    
    // 强调色 - 红色 (openclaw 主题)
    val accent = Color(0xFFFF5C5C)       // 主按钮、链接、重点
    val accentHover = Color(0xFFFF7070)  // 悬停状态
    val accentSubtle = Color(0x1AFF5C5C) // 10% opacity - 强调背景
    val accentGlow = Color(0x33FF5C5C)   // 20% opacity - 光晕效果
    val accentForeground = Color(0xFFFAFAFA)
    val accentContainer = Color(0xFF2A2A35) // 按钮容器背景
    
    // 次要
    val secondary = Color(0xFF161920)
    val secondaryForeground = Color(0xFFF0F0F2)
    
    // 次要强调色 - 青色
    val accent2 = Color(0xFF14B8A6)
    val accent2Subtle = Color(0x1A14B8A6)
    
    // 语义色
    val ok = Color(0xFF22C55E)           // 成功
    val okSubtle = Color(0x1422C55E)     // 8% opacity
    val warn = Color(0xFFF59E0B)         // 警告
    val warnSubtle = Color(0x14F59E0B)
    val danger = Color(0xFFEF4444)       // 错误
    val dangerSubtle = Color(0x14EF4444)
    val info = Color(0xFF3B82F6)         // 信息
    
    // ─────────────────────────────────────────────────────────────
    // 颜色 - 浅色主题
    // ─────────────────────────────────────────────────────────────
    
    object Light {
        val bg = Color(0xFFF8F9FA)
        val bgAccent = Color(0xFFF1F3F5)
        val bgElevated = Color(0xFFFFFFFF)
        val bgHover = Color(0xFFECEEF0)
        
        val card = Color(0xFFFFFFFF)
        val cardForeground = Color(0xFF1A1A1E)
        val popover = Color(0xFFFFFFFF)
        val panel = Color(0xFFF8F9FA)
        val panelStrong = Color(0xFFF1F3F5)
        
        val text = Color(0xFF3C3C43)
        val textStrong = Color(0xFF1A1A1E)
        val muted = Color(0xFF6E6E73)
        val mutedStrong = Color(0xFF545458)
        
        val border = Color(0xFFE5E5EA)
        val borderStrong = Color(0xFFD1D1D6)
        val borderHover = Color(0xFFC7C7CC)
        
        val secondary = Color(0xFFF1F3F5)
        val secondaryForeground = Color(0xFF3C3C43)
        
        val accent = Color(0xFFDC2626)       // 亮色模式用深红
        val accentHover = Color(0xFFEF4444)
        val accentSubtle = Color(0x14DC2626)
        val accentGlow = Color(0x22DC2626)
        val accentForeground = Color(0xFFFFFFFF)
        
        val accent2 = Color(0xFF0D9488)
        val accent2Subtle = Color(0x140D9488)
        
        val ok = Color(0xFF15803D)
        val okSubtle = Color(0x1415803D)
        val warn = Color(0xFFB45309)
        val warnSubtle = Color(0x14B45309)
        val danger = Color(0xFFDC2626)
        val dangerSubtle = Color(0x14DC2626)
        val info = Color(0xFF2563EB)
        
        // 用户气泡特殊色（亮色模式）
        val userBubbleBg = Color(0x1EFB923C) // rgba(251, 146, 60, 0.12)
        val userBubbleBorder = Color(0x33EA580C) // rgba(234, 88, 12, 0.2)
    }
    
    // ─────────────────────────────────────────────────────────────
    // 圆角
    // ─────────────────────────────────────────────────────────────
    
    val radiusSm = 6.dp      // 小元素、标签
    val radiusMd = 10.dp     // 按钮、输入框、卡片内元素
    val radiusLg = 14.dp     // 卡片、模态框
    val radiusXl = 20.dp     // 大卡片
    val radiusFull = 9999.dp // 圆形、药丸按钮
    
    // ─────────────────────────────────────────────────────────────
    // 间距 (基础单位 4dp)
    // ─────────────────────────────────────────────────────────────
    
    val space0 = 0.dp
    val space1 = 4.dp       // xs
    val space2 = 8.dp       // sm
    val space3 = 12.dp      // md
    val space4 = 16.dp      // lg
    val space5 = 20.dp      // 
    val space6 = 24.dp      // xl
    val space8 = 32.dp      // xxl
    val space10 = 40.dp     // xxxl
    val space12 = 48.dp     // huge
    
    // ─────────────────────────────────────────────────────────────
    // 字体大小
    // ─────────────────────────────────────────────────────────────
    
    val textXs = 11.sp       // 标签、时间戳
    val textSm = 12.sp       // 次要文字、标签
    val textBase = 13.5.sp   // 正文
    val textMd = 14.sp       // 聊天文字
    val textLg = 15.sp       // 卡片标题
    val textXl = 22.sp       // 大标题
    val textStat = 24.sp     // 统计数字
    
    // ─────────────────────────────────────────────────────────────
    // 阴影
    // ─────────────────────────────────────────────────────────────
    
    val elevationSm = 1.dp      // 轻微提升
    val elevationMd = 4.dp      // 卡片、下拉
    val elevationLg = 12.dp     // 模态框
    val elevationXl = 24.dp     // 大模态框
    
    // ─────────────────────────────────────────────────────────────
    // 动画时长
    // ─────────────────────────────────────────────────────────────
    
    const val durationFast = 100    // 悬停、颜色变化
    const val durationNormal = 180  // 状态过渡、展开
    const val durationSlow = 300    // 页面切换、大动画
}

/**
 * 聊天组件样式 (对应 WebChat chat/grouped.css)
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
    
    // 用户气泡 (深色主题)
    val userBubbleBg = DesignTokens.accentSubtle      // rgba(255, 92, 92, 0.1)
    val userBubbleBorder = Color(0x00FF5C5C)          // transparent
    
    // 助手气泡 (深色主题)
    val assistantBubbleBg = DesignTokens.card         // #161920
    val assistantBubbleBorder = Color(0x001E2028)     // transparent
    
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
    val toolTagPaddingH = 11.dp     // 5px 11px per spec
    val toolTagPaddingV = 5.dp
    val toolTagHeight = 24.dp
}