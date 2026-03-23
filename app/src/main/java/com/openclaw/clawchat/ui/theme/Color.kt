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
