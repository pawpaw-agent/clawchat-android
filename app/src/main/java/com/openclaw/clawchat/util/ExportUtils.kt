package com.openclaw.clawchat.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import com.openclaw.clawchat.util.AppLog
import com.openclaw.clawchat.ui.state.MessageContentItem
import com.openclaw.clawchat.ui.state.MessageUi
import com.openclaw.clawchat.ui.state.MessageRole
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream

/**
 * 导出工具类
 * 支持消息导出为多种格式
 */
object ExportUtils {
    
    /**
     * 导出消息为 JSON
     */
    fun exportMessagesToJson(messages: List<MessageUi>): String {
        val exportData = messages.map { message ->
            mapOf(
                "id" to message.id,
                "role" to message.role.name,
                "timestamp" to message.timestamp,
                "content" to message.content.map { item ->
                    when (item) {
                        is MessageContentItem.Text -> mapOf("type" to "text", "text" to item.text)
                        is MessageContentItem.Image -> mapOf("type" to "image", "base64" to item.base64.take(50) + "...")
                        else -> mapOf("type" to "unknown")
                    }
                }
            )
        }
        return Json { prettyPrint = true }.encodeToString(exportData)
    }
    
    /**
     * 导出消息为纯文本
     */
    fun exportMessagesToText(messages: List<MessageUi>): String {
        return messages.joinToString("\n\n") { message ->
            val role = when (message.role) {
                MessageRole.USER -> "👤 用户"
                MessageRole.ASSISTANT -> "🤖 助手"
                MessageRole.SYSTEM -> "⚙️ 系统"
                MessageRole.TOOL -> "🔧 工具"
            }
            val time = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                .format(java.util.Date(message.timestamp))
            
            val textContent = message.content.filterIsInstance<MessageContentItem.Text>()
                .joinToString("\n") { it.text }
            
            "[$time] $role\n$textContent"
        }
    }
    
    /**
     * 导出消息为图片
     */
    fun exportMessagesToBitmap(
        messages: List<MessageUi>,
        width: Int = 800,
        context: Context
    ): Bitmap? {
        return try {
            val padding = 32f
            val lineHeight = 60f
            val textSize = 36f
            
            // 计算高度
            var totalHeight = padding.toInt()
            messages.forEach { message ->
                val text = message.content.filterIsInstance<MessageContentItem.Text>()
                    .joinToString("\n") { it.text }
                totalHeight += (text.split("\n").size + 2) * lineHeight.toInt()
            }
            
            // 创建 Bitmap
            val bitmap = Bitmap.createBitmap(width, totalHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            
            // 背景
            canvas.drawColor(android.graphics.Color.WHITE)
            
            // 绘制消息
            val paint = Paint().apply {
                this.textSize = textSize
                isAntiAlias = true
            }
            
            var y = padding
            messages.forEach { message ->
                val role = when (message.role) {
                    MessageRole.USER -> "用户"
                    MessageRole.ASSISTANT -> "助手"
                    else -> "系统"
                }
                
                // 角色名
                paint.color = if (message.role == MessageRole.USER) {
                    android.graphics.Color.parseColor("#2196F3")
                } else {
                    android.graphics.Color.parseColor("#4CAF50")
                }
                canvas.drawText("[$role]", padding, y + textSize, paint)
                y += lineHeight.toInt()
                
                // 消息内容
                paint.color = android.graphics.Color.BLACK
                val text = message.content.filterIsInstance<MessageContentItem.Text>()
                    .joinToString("\n") { it.text }
                
                text.split("\n").forEach { line ->
                    canvas.drawText(line, padding, y + textSize, paint)
                    y += lineHeight.toInt()
                }
                
                y += lineHeight.toInt() // 空行
            }
            
            bitmap
        } catch (e: Exception) {
            AppLog.e("ExportUtils", "Failed to export messages to bitmap: ${e.message}")
            null
        }
    }
    
    /**
     * 保存文件到外部存储
     */
    fun saveToFile(context: Context, content: String, fileName: String): Uri? {
        return try {
            val file = File(context.cacheDir, fileName)
            FileOutputStream(file).use { output ->
                output.write(content.toByteArray())
            }
            androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        } catch (e: Exception) {
            AppLog.e("ExportUtils", "Failed to save file: ${e.message}")
            null
        }
    }
    
    /**
     * 分享文本
     */
    fun shareText(context: Context, text: String, title: String = "分享对话") {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            putExtra(Intent.EXTRA_SUBJECT, title)
        }
        context.startActivity(Intent.createChooser(intent, title))
    }
    
    /**
     * 分享文件
     */
    fun shareFile(context: Context, uri: Uri, mimeType: String = "text/plain") {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "分享文件"))
    }
}