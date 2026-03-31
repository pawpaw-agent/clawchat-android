package com.openclaw.clawchat.util

import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

/**
 * DateTimeUtils 单元测试
 */
class DateTimeUtilsTest {
    
    // ─────────────────────────────────────────────────────────────
    // 相对时间测试
    // ─────────────────────────────────────────────────────────────
    
    @Test
    fun `formatRelativeTime returns 刚刚 for less than 1 minute`() {
        val now = System.currentTimeMillis()
        val result = DateTimeUtils.formatRelativeTime(now - 30_000, now)
        assertEquals("刚刚", result)
    }
    
    @Test
    fun `formatRelativeTime returns X 分钟前 for less than 1 hour`() {
        val now = System.currentTimeMillis()
        val result = DateTimeUtils.formatRelativeTime(now - 5 * 60_000, now)
        assertEquals("5 分钟前", result)
    }
    
    @Test
    fun `formatRelativeTime returns X 小时前 for less than 1 day`() {
        val now = System.currentTimeMillis()
        val result = DateTimeUtils.formatRelativeTime(now - 3 * 3_600_000, now)
        assertEquals("3 小时前", result)
    }
    
    @Test
    fun `formatRelativeTime returns X 天前 for less than 1 week`() {
        val now = System.currentTimeMillis()
        val result = DateTimeUtils.formatRelativeTime(now - 2 * 86_400_000, now)
        assertEquals("2 天前", result)
    }
    
    @Test
    fun `formatRelativeTime returns date for more than 1 week`() {
        val now = System.currentTimeMillis()
        val result = DateTimeUtils.formatRelativeTime(now - 8 * 86_400_000, now)
        assertTrue(result.matches(Regex("\\d{2}-\\d{2}")))
    }
    
    // ─────────────────────────────────────────────────────────────
    // 会话分组测试
    // ─────────────────────────────────────────────────────────────
    
    @Test
    fun `formatSessionGroupLabel returns 今天 for today`() {
        val now = System.currentTimeMillis()
        val result = DateTimeUtils.formatSessionGroupLabel(now)
        assertEquals("今天", result)
    }
    
    @Test
    fun `formatSessionGroupLabel returns 昨天 for yesterday`() {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -1)
        val result = DateTimeUtils.formatSessionGroupLabel(cal.timeInMillis)
        assertEquals("昨天", result)
    }
    
    // ─────────────────────────────────────────────────────────────
    // 格式化测试
    // ─────────────────────────────────────────────────────────────
    
    @Test
    fun `formatTime returns HH:mm format`() {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 14)
            set(Calendar.MINUTE, 30)
        }
        val result = DateTimeUtils.formatTime(cal.timeInMillis)
        assertEquals("14:30", result)
    }
    
    @Test
    fun `formatDate returns MM-dd format`() {
        val cal = Calendar.getInstance().apply {
            set(Calendar.MONTH, Calendar.MARCH)
            set(Calendar.DAY_OF_MONTH, 15)
        }
        val result = DateTimeUtils.formatDate(cal.timeInMillis)
        assertEquals("03-15", result)
    }
    
    @Test
    fun `formatFullDateTime returns full format`() {
        val timestamp = System.currentTimeMillis()
        val result = DateTimeUtils.formatFullDateTime(timestamp)
        assertTrue(result.matches(Regex("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}")))
    }
}