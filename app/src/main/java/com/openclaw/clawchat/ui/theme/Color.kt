package com.openclaw.clawchat.ui.theme

import androidx.compose.ui.graphics.Color

// ==================== 品牌色 ====================
// 主色调 - 基于 OpenClaw 品牌色
val ClawBlue = Color(0xFF3B82F6)
val ClawBlueDark = Color(0xFF2563EB)
val ClawBlueLight = Color(0xFF60A5FA)

// ==================== 深色主题颜色 ====================
// 背景色（深色）
val DarkBackgroundPrimary = Color(0xFF0F172A)
val DarkBackgroundSecondary = Color(0xFF1E293B)
val DarkBackgroundTertiary = Color(0xFF334155)

// 表面色（深色）
val DarkSurfacePrimary = Color(0xFF1E293B)
val DarkSurfaceSecondary = Color(0xFF334155)

// 文本色（深色）
val DarkTextPrimary = Color(0xFFF8FAFC)
val DarkTextSecondary = Color(0xFF94A3B8)
val DarkTextTertiary = Color(0xFF64748B)

// ==================== 浅色主题颜色 ====================
// 背景色（浅色）
val LightBackgroundPrimary = Color(0xFFF8FAFC)
val LightBackgroundSecondary = Color(0xFFF1F5F9)
val LightBackgroundTertiary = Color(0xFFE2E8F0)

// 表面色（浅色）
val LightSurfacePrimary = Color(0xFFFFFFFF)
val LightSurfaceSecondary = Color(0xFFF8FAFC)

// 文本色（浅色）
val LightTextPrimary = Color(0xFF0F172A)
val LightTextSecondary = Color(0xFF475569)
val LightTextTertiary = Color(0xFF94A3B8)

// ==================== 状态色（通用） ====================
val Success = Color(0xFF22C55E)
val Warning = Color(0xFFF59E0B)
val Error = Color(0xFFEF4444)
val Info = Color(0xFF3B82F6)

// 连接状态色
val ConnectedColor = Color(0xFF22C55E)
val ConnectingColor = Color(0xFFF59E0B)
val DisconnectedColor = Color(0xFF64748B)

// 消息气泡（深色）
val DarkMessageBubbleUser = Color(0xFF3B82F6)
val DarkMessageBubbleAssistant = Color(0xFF334155)
val DarkMessageBubbleSystem = Color(0xFF1E293B)

// 消息气泡（浅色）
val LightMessageBubbleUser = Color(0xFF3B82F6)
val LightMessageBubbleAssistant = Color(0xFFF1F5F9)
val LightMessageBubbleSystem = Color(0xFFE2E8F0)

// ==================== TerminalFlow 主题颜色 ====================
/**
 * TerminalFlow 颜色系统
 * 琥珀色终端美学配色
 */
object TerminalColors {
    // 背景层级
    val TerminalBlack = Color(0xFF0A0A0B)      // 最深背景
    val TerminalBg = Color(0xFF0E1015)         // 主背景
    val TerminalSurface = Color(0xFF161920)    // 表面
    val TerminalElevated = Color(0xFF1E2028)   // 浮层
    
    // 强调色 (琥珀色系)
    val PulseAmber = Color(0xFFF59E0B)         // 主强调色
    val PulseAmberGlow = Color(0x33F59E0B)     // 发光效果
    val PulseAmberMuted = Color(0xFF92400E)    // 暗淡
    val PulseAmberBright = Color(0xFFFBBF24)   // 亮色
    
    // 文字颜色
    val TextPrimary = Color(0xFFF4F4F5)        // 主文字
    val TextSecondary = Color(0xFFA1A1AA)      // 次要文字
    val TextMuted = Color(0xFF71717A)          // 暗淡文字
    val TextCode = Color(0xFF22D3EE)           // 代码文字
    
    // 状态颜色
    val StatusActive = Color(0xFF22C55E)
    val StatusWarning = Color(0xFFEAB308)
    val StatusError = Color(0xFFEF4444)
    val StatusIdle = Color(0xFF6B7280)
    
    // 消息气泡颜色
    val BubbleUser = Color(0xFF1E3A5F)
    val BubbleUserBorder = Color(0xFF2563EB)
    val BubbleAssistant = Color(0xFF1A1D24)
    val BubbleAssistantBorder = Color(0xFF2D3748)
    val BubbleSystem = Color(0xFF1F2937)
    
    // 工具调用颜色
    val ToolCardBg = Color(0xFF1A1F2E)
    val ToolCardBorder = Color(0xFF2D3748)
    val ToolRunning = Color(0xFFF59E0B)
    val ToolSuccess = Color(0xFF22C55E)
    val ToolError = Color(0xFFEF4444)
    
    // 边框
    val Border = Color(0xFF1E2028)
    val BorderStrong = Color(0xFF2E3040)
    val Divider = Color(0xFF1A1D24)
    
    // 其他
    val Overlay = Color(0x80000000)
    val Ripple = Color(0x1AF59E0B)
}

/**
 * TerminalFlow 浅色主题颜色
 * 温暖白色调，保留琥珀色强调
 */
object LightTerminalColors {
    // 背景层级
    val TerminalLight = Color(0xFFFBFAF8)      // 主背景 - 温暖白
    val TerminalBg = Color(0xFFF5F4F1)         // 次级背景 - 米灰
    val TerminalSurface = Color(0xFFFFFFFF)    // 表面 - 纯白卡片
    val TerminalElevated = Color(0xFFFFFFFF)   // 浮层
    
    // 强调色 (琥珀色系 - 深色版本)
    val PulseAmber = Color(0xFFD97706)         // 主强调 - 琥珀 600
    val PulseAmberGlow = Color(0x33D97706)     // 发光效果
    val PulseAmberMuted = Color(0xFFFDE68A)    // 浅色背景
    val PulseAmberBright = Color(0xFFB45309)   // 悬停 - 琥珀 700
    
    // 文字颜色
    val TextPrimary = Color(0xFF1C1917)        // 近黑 - 主文字
    val TextSecondary = Color(0xFF57534E)      // 深灰 - 次要文字
    val TextMuted = Color(0xFFA8A29E)          // 浅灰 - 暗淡文字
    val TextCode = Color(0xFF0891B2)           // 青色 - 代码文字
    
    // 状态颜色
    val StatusActive = Color(0xFF16A34A)       // 成功 - 更深绿
    val StatusWarning = Color(0xFFCA8A04)      // 警告 - 更深黄
    val StatusError = Color(0xFFDC2626)        // 错误
    val StatusIdle = Color(0xFF9CA3AF)         // 空闲 - 中灰
    
    // 消息气泡颜色
    val BubbleUser = Color(0xFFFDE68A)         // 琥珀 200 背景
    val BubbleUserBorder = Color(0xFFFBBF24)   // 琥珀 400 边框
    val BubbleUserText = Color(0xFF78350F)     // 琥珀 900 文字
    val BubbleAssistant = Color(0xFFF5F4F1)    // 米灰背景
    val BubbleAssistantBorder = Color(0xFFE7E5E4) // 细边框
    val BubbleSystem = Color(0xFFE7E5E4)       // 浅灰背景
    
    // 工具调用颜色
    val ToolCardBg = Color(0xFFF5F4F1)         // 浅灰背景
    val ToolCardBorder = Color(0xFFD6D3D1)     // 边框
    val ToolRunning = Color(0xFFD97706)        // 运行中 - 琥珀
    val ToolSuccess = Color(0xFF16A34A)        // 成功
    val ToolError = Color(0xFFDC2626)          // 错误
    
    // 边框
    val Border = Color(0xFFE7E5E4)             // 默认边框
    val BorderStrong = Color(0xFFD6D3D1)       // 强调边框
    val Divider = Color(0xFFF5F4F1)            // 分割线
    
    // 其他
    val Overlay = Color(0x80000000)            // 遮罩
    val Ripple = Color(0x1AD97706)             // 涟漪效果
}
