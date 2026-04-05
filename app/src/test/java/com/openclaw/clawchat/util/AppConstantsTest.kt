package com.openclaw.clawchat.util

import org.junit.Assert.*
import org.junit.Test

/**
 * AppConstants 单元测试
 */
class AppConstantsTest {

    @Test
    fun `app name is ClawChat`() {
        assertEquals("ClawChat", AppConstants.APP_NAME)
    }

    @Test
    fun `app version is defined`() {
        assertTrue(AppConstants.APP_VERSION.isNotEmpty())
        assertTrue(AppConstants.APP_VERSION_CODE > 0)
    }

    // ─────────────────────────────────────────────────────────────
    // 时间常量测试
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `time constants are correctly calculated`() {
        assertEquals(1000L, AppConstants.SECOND)
        assertEquals(60 * 1000L, AppConstants.MINUTE)
        assertEquals(60 * 60 * 1000L, AppConstants.HOUR)
        assertEquals(24 * 60 * 60 * 1000L, AppConstants.DAY)
        assertEquals(7 * 24 * 60 * 60 * 1000L, AppConstants.WEEK)
    }

    // ─────────────────────────────────────────────────────────────
    // 缓存限制测试
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `cache limits are positive`() {
        assertTrue(AppConstants.MAX_MESSAGES_PER_SESSION > 0)
        assertTrue(AppConstants.MAX_ATTACHMENTS_PER_MESSAGE > 0)
        assertTrue(AppConstants.MAX_ATTACHMENT_SIZE_MB > 0)
        assertTrue(AppConstants.MAX_PROMPT_HISTORY > 0)
        assertTrue(AppConstants.MAX_DRAFT_AGE_MS > 0)
    }

    @Test
    fun `draft age is 24 hours`() {
        assertEquals(24 * AppConstants.HOUR, AppConstants.MAX_DRAFT_AGE_MS)
    }

    // ─────────────────────────────────────────────────────────────
    // UI 常量测试
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `ui constants are reasonable`() {
        assertTrue(AppConstants.MIN_TOUCH_TARGET_DP >= 48) // Material Design guideline
        assertTrue(AppConstants.DEFAULT_PADDING_DP > 0)
        assertTrue(AppConstants.DEFAULT_CORNER_RADIUS_DP >= 0)
    }

    @Test
    fun `animation durations are ascending`() {
        assertTrue(AppConstants.ANIMATION_DURATION_SHORT < AppConstants.ANIMATION_DURATION_MEDIUM)
        assertTrue(AppConstants.ANIMATION_DURATION_MEDIUM < AppConstants.ANIMATION_DURATION_LONG)
    }

    // ─────────────────────────────────────────────────────────────
    // 分页常量测试
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `pagination constants are valid`() {
        assertTrue(AppConstants.DEFAULT_PAGE_SIZE > 0)
        assertEquals(0, AppConstants.INITIAL_PAGE)
    }

    // ─────────────────────────────────────────────────────────────
    // 文件常量测试
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `file constants are valid`() {
        assertTrue(AppConstants.MAX_LOG_LENGTH > 0)
        assertTrue(AppConstants.MAX_FILE_NAME_LENGTH > 0)
    }

    @Test
    fun `max log length is reasonable for logging`() {
        // Android logcat has a limit around 4000 chars
        assertTrue(AppConstants.MAX_LOG_LENGTH <= 5000)
    }
}