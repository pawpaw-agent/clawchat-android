package com.openclaw.clawchat.util

/**
 * 配置常量
 */
object ConfigConstants {
    
    // ─────────────────────────────────────────────────────────────
    // 默认配置
    // ─────────────────────────────────────────────────────────────
    
    const val DEFAULT_GATEWAY_PORT = 18789
    const val DEFAULT_THEME_COLOR_INDEX = 0  // Blue
    const val DEFAULT_FONT_SIZE = "MEDIUM"
    
    // ─────────────────────────────────────────────────────────────
    // DataStore 文件名
    // ─────────────────────────────────────────────────────────────
    
    const val USER_PREFERENCES_FILE = "user_preferences"
    const val THEME_PREFERENCES_FILE = "theme_preferences"
    const val RECENT_PROMPTS_FILE = "recent_prompts"
    const val MESSAGE_DRAFTS_FILE = "message_drafts"
    
    // ─────────────────────────────────────────────────────────────
    // 加密存储
    // ─────────────────────────────────────────────────────────────
    
    const val ENCRYPTED_PREFS_FILE = "clawchat_secure"
    const val KEYSTORE_ALIAS = "clawchat_device_key"
    
    // ─────────────────────────────────────────────────────────────
    // 主题配置
    // ─────────────────────────────────────────────────────────────
    
    object Theme {
        const val LIGHT = "light"
        const val DARK = "dark"
        const val SYSTEM = "system"
    }
    
    // ─────────────────────────────────────────────────────────────
    // 字体大小
    // ─────────────────────────────────────────────────────────────
    
    object FontSize {
        const val SMALL = "SMALL"
        const val MEDIUM = "MEDIUM"
        const val LARGE = "LARGE"
    }
    
    // ─────────────────────────────────────────────────────────────
    // 主题色
    // ─────────────────────────────────────────────────────────────
    
    object ThemeColor {
        const val BLUE = 0
        const val PURPLE = 1
        const val TEAL = 2
        const val ORANGE = 3
        const val PINK = 4
        const val GREEN = 5
        const val RED = 6
        const val INDIGO = 7
    }
    
    // ─────────────────────────────────────────────────────────────
    // 通知配置
    // ─────────────────────────────────────────────────────────────
    
    object Notification {
        const val CHANNEL_ID_MESSAGES = "messages"
        const val CHANNEL_ID_ERRORS = "errors"
        const val CHANNEL_NAME_MESSAGES = "消息通知"
        const val CHANNEL_NAME_ERRORS = "错误通知"
    }
    
    // ─────────────────────────────────────────────────────────────
    // 日志配置
    // ─────────────────────────────────────────────────────────────
    
    object Log {
        const val TAG_APP = "ClawChat"
        const val TAG_NETWORK = "Network"
        const val TAG_SECURITY = "Security"
        const val TAG_UI = "UI"
    }
}