package com.openclaw.clawchat.util

/**
 * 应用常量
 */
object AppConstants {
    
    // ─────────────────────────────────────────────────────────────
    // 应用信息
    // ─────────────────────────────────────────────────────────────
    
    const val APP_NAME = "ClawChat"
    const val APP_VERSION = "1.2.0"
    const val APP_VERSION_CODE = 6
    
    // ─────────────────────────────────────────────────────────────
    // 时间常量
    // ─────────────────────────────────────────────────────────────
    
    const val SECOND = 1000L
    const val MINUTE = 60 * SECOND
    const val HOUR = 60 * MINUTE
    const val DAY = 24 * HOUR
    const val WEEK = 7 * DAY
    
    // ─────────────────────────────────────────────────────────────
    // 缓存限制
    // ─────────────────────────────────────────────────────────────
    
    const val MAX_MESSAGES_PER_SESSION = 500
    const val MAX_ATTACHMENTS_PER_MESSAGE = 5
    const val MAX_ATTACHMENT_SIZE_MB = 10
    const val MAX_PROMPT_HISTORY = 20
    const val MAX_DRAFT_AGE_MS = 24 * HOUR
    
    // ─────────────────────────────────────────────────────────────
    // UI 常量
    // ─────────────────────────────────────────────────────────────
    
    const val MIN_TOUCH_TARGET_DP = 48
    const val DEFAULT_PADDING_DP = 16
    const val DEFAULT_CORNER_RADIUS_DP = 8
    const val ANIMATION_DURATION_SHORT = 150
    const val ANIMATION_DURATION_MEDIUM = 300
    const val ANIMATION_DURATION_LONG = 500
    
    // ─────────────────────────────────────────────────────────────
    // 分页
    // ─────────────────────────────────────────────────────────────
    
    const val DEFAULT_PAGE_SIZE = 20
    const val INITIAL_PAGE = 0
    
    // ─────────────────────────────────────────────────────────────
    // 文件
    // ─────────────────────────────────────────────────────────────
    
    const val MAX_LOG_LENGTH = 4000
    const val MAX_FILE_NAME_LENGTH = 255
}