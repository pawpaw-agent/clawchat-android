package com.openclaw.clawchat.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 日期时间工具类
 * 提供统一的日期时间格式化方法
 */
object DateTimeUtils {
    
    // ─────────────────────────────────────────────────────────────
    // 格式常量
    // ─────────────────────────────────────────────────────────────
    
    private const val FORMAT_TIME = "HH:mm"
    private const val FORMAT_DATE = "MM-dd"
    private const val FORMAT_DATETIME = "MM-dd HH:mm"
    private const val FORMAT_FULL = "yyyy-MM-dd HH:mm:ss"
    private const val FORMAT_WEEKDAY = "EEEE"
    
    // ─────────────────────────────────────────────────────────────
    // 相对时间
    // ─────────────────────────────────────────────────────────────
    
    /**
     * 格式化相对时间
     * @param timestamp 时间戳（毫秒）
     * @param now 当前时间戳（毫秒），默认为当前时间
     * @return 相对时间字符串
     */
    fun formatRelativeTime(timestamp: Long, now: Long = System.currentTimeMillis()): String {
        val diff = now - timestamp
        
        return when {
            diff < 60_000 -> "刚刚"
            diff < 3_600_000 -> "${diff / 60_000} 分钟前"
            diff < 86_400_000 -> "${diff / 3_600_000} 小时前"
            diff < 604_800_000 -> "${diff / 86_400_000} 天前"
            else -> formatDate(timestamp)
        }
    }
    
    /**
     * 格式化会话时间分组标签
     * @param timestamp 时间戳（毫秒）
     * @return 分组标签（今天、昨天、本周、更早）
     */
    fun formatSessionGroupLabel(timestamp: Long): String {
        val cal = Calendar.getInstance()
        cal.timeInMillis = timestamp
        
        val today = Calendar.getInstance()
        
        return when {
            isSameDay(cal, today) -> "今天"
            isYesterday(cal, today) -> "昨天"
            isSameWeek(cal, today) -> "本周"
            else -> "更早"
        }
    }
    
    // ─────────────────────────────────────────────────────────────
    // 格式化方法
    // ─────────────────────────────────────────────────────────────
    
    /**
     * 格式化时间（HH:mm）
     */
    fun formatTime(timestamp: Long): String {
        return SimpleDateFormat(FORMAT_TIME, Locale.getDefault()).format(Date(timestamp))
    }
    
    /**
     * 格式化日期（MM-dd）
     */
    fun formatDate(timestamp: Long): String {
        return SimpleDateFormat(FORMAT_DATE, Locale.getDefault()).format(Date(timestamp))
    }
    
    /**
     * 格式化日期时间（MM-dd HH:mm）
     */
    fun formatDateTime(timestamp: Long): String {
        return SimpleDateFormat(FORMAT_DATETIME, Locale.getDefault()).format(Date(timestamp))
    }
    
    /**
     * 格式化完整日期时间（yyyy-MM-dd HH:mm:ss）
     */
    fun formatFullDateTime(timestamp: Long): String {
        return SimpleDateFormat(FORMAT_FULL, Locale.getDefault()).format(Date(timestamp))
    }
    
    /**
     * 格式化星期几
     */
    fun formatWeekday(timestamp: Long): String {
        return SimpleDateFormat(FORMAT_WEEKDAY, Locale.getDefault()).format(Date(timestamp))
    }
    
    // ─────────────────────────────────────────────────────────────
    // 辅助方法
    // ─────────────────────────────────────────────────────────────
    
    private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
               cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }
    
    private fun isYesterday(cal1: Calendar, cal2: Calendar): Boolean {
        cal2.add(Calendar.DAY_OF_YEAR, -1)
        return isSameDay(cal1, cal2).also { cal2.add(Calendar.DAY_OF_YEAR, 1) }
    }
    
    private fun isSameWeek(cal1: Calendar, cal2: Calendar): Boolean {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
               cal1.get(Calendar.WEEK_OF_YEAR) == cal2.get(Calendar.WEEK_OF_YEAR)
    }
}