package com.openclaw.clawchat.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.openclaw.clawchat.MainActivity
import com.openclaw.clawchat.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 通知管理器
 * 
 * 负责：
 * - 创建通知渠道
 * - 显示消息通知
 * - 显示系统通知
 * - 管理通知权限
 */
@Singleton
class ClawChatNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val CHANNEL_MESSAGES = "messages"
        private const val CHANNEL_SYSTEM = "system"
        
        private const val NOTIFICATION_MESSAGE = 1
        private const val NOTIFICATION_SYSTEM = 2
        
        private const val REQUEST_CODE_MAIN_ACTIVITY = 100
    }
    
    init {
        createNotificationChannels()
    }
    
    /**
     * 创建通知渠道（Android 8.0+）
     */
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // 消息通知渠道（高优先级）
            val messagesChannel = NotificationChannel(
                CHANNEL_MESSAGES,
                "消息通知",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "新消息到达时的通知"
                enableLights(true)
                lightColor = context.getColor(R.color.primary)
                enableVibration(true)
                vibrationPattern = longArrayOf(100, 200, 100, 200)
            }
            
            // 系统通知渠道（低优先级）
            val systemChannel = NotificationChannel(
                CHANNEL_SYSTEM,
                "系统通知",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "连接状态、配对结果等系统通知"
                enableLights(false)
                enableVibration(false)
            }
            
            notificationManager.createNotificationChannel(messagesChannel)
            notificationManager.createNotificationChannel(systemChannel)
        }
    }
    
    /**
     * 检查通知权限（Android 13+）
     */
    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Android 12 及以下不需要权限
        }
    }
    
    /**
     * 显示消息通知
     * 
     * @param sessionId 会话 ID
     * @param messageContent 消息内容
     * @param senderName 发送者名称
     */
    fun showMessageNotification(
        sessionId: String,
        messageContent: String,
        senderName: String = "OpenClaw"
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("navigate_to_session", sessionId)
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            REQUEST_CODE_MAIN_ACTIVITY,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle(senderName)
            .setContentText(truncateMessage(messageContent))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        
        notify(NOTIFICATION_MESSAGE, notification)
    }
    
    /**
     * 显示系统通知
     * 
     * @param title 通知标题
     * @param content 通知内容
     * @param priority 优先级
     */
    fun showSystemNotification(
        title: String,
        content: String,
        priority: Int = NotificationCompat.PRIORITY_DEFAULT
    ) {
        val notification = NotificationCompat.Builder(context, CHANNEL_SYSTEM)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(priority)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .build()
        
        notify(NOTIFICATION_SYSTEM, notification)
    }
    
    /**
     * 显示配对成功通知
     */
    fun showPairingSuccessNotification() {
        showSystemNotification(
            title = "配对成功",
            content = "设备已成功连接到 OpenClaw Gateway",
            priority = NotificationCompat.PRIORITY_HIGH
        )
    }
    
    /**
     * 显示配对失败通知
     */
    fun showPairingFailedNotification(reason: String) {
        showSystemNotification(
            title = "配对失败",
            content = reason,
            priority = NotificationCompat.PRIORITY_HIGH
        )
    }
    
    /**
     * 显示连接状态通知
     */
    fun showConnectionStatusNotification(isConnected: Boolean) {
        if (isConnected) {
            showSystemNotification(
                title = "已连接",
                content = "已连接到 OpenClaw Gateway",
                priority = NotificationCompat.PRIORITY_LOW
            )
        } else {
            showSystemNotification(
                title = "已断开",
                content = "与 OpenClaw Gateway 的连接已断开",
                priority = NotificationCompat.PRIORITY_DEFAULT
            )
        }
    }
    
    /**
     * 取消所有通知
     */
    fun cancelAllNotifications() {
        NotificationManagerCompat.from(context).cancelAll()
    }
    
    /**
     * 取消指定通知
     */
    fun cancelNotification(notificationId: Int) {
        NotificationManagerCompat.from(context).cancel(notificationId)
    }
    
    /**
     * 发送通知（内部方法）
     */
    private fun notify(notificationId: Int, notification: android.app.Notification) {
        if (hasNotificationPermission()) {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        }
    }
    
    /**
     * 截断消息内容（显示预览）
     */
    private fun truncateMessage(content: String, maxLength: Int = 100): String {
        return if (content.length > maxLength) {
            content.take(maxLength) + "..."
        } else {
            content
        }
    }
}
