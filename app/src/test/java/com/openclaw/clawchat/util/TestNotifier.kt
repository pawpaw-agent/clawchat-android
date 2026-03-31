package com.openclaw.clawchat.util

import java.util.concurrent.atomic.AtomicInteger

/**
 * 测试用通知器
 * 用于验证通知行为
 */
class TestNotifier {
    private val notifications = mutableListOf<NotificationRecord>()
    private val notificationCount = AtomicInteger(0)
    
    /**
     * 通知记录
     */
    data class NotificationRecord(
        val title: String,
        val message: String,
        val type: String,
        val timestamp: Long = System.currentTimeMillis()
    )
    
    /**
     * 发送通知
     */
    fun notify(title: String, message: String, type: String = "info") {
        notifications.add(NotificationRecord(title, message, type))
        notificationCount.incrementAndGet()
    }
    
    /**
     * 发送错误通知
     */
    fun notifyError(title: String, message: String) {
        notify(title, message, "error")
    }
    
    /**
     * 发送成功通知
     */
    fun notifySuccess(title: String, message: String) {
        notify(title, message, "success")
    }
    
    /**
     * 发送警告通知
     */
    fun notifyWarning(title: String, message: String) {
        notify(title, message, "warning")
    }
    
    /**
     * 获取所有通知
     */
    fun getNotifications(): List<NotificationRecord> {
        return notifications.toList()
    }
    
    /**
     * 获取通知数量
     */
    fun getNotificationCount(): Int {
        return notificationCount.get()
    }
    
    /**
     * 获取最后一次通知
     */
    fun getLastNotification(): NotificationRecord? {
        return notifications.lastOrNull()
    }
    
    /**
     * 清除所有通知
     */
    fun clear() {
        notifications.clear()
        notificationCount.set(0)
    }
    
    /**
     * 验证是否收到通知
     */
    fun hasNotification(title: String): Boolean {
        return notifications.any { it.title == title }
    }
    
    /**
     * 验证是否收到特定类型通知
     */
    fun hasNotificationOfType(type: String): Boolean {
        return notifications.any { it.type == type }
    }
    
    /**
     * 获取特定类型的通知
     */
    fun getNotificationsOfType(type: String): List<NotificationRecord> {
        return notifications.filter { it.type == type }
    }
}