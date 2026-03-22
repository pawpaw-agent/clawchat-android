package com.openclaw.clawchat.ui.components

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import kotlinx.serialization.Serializable

/**
 * 附件支持 - 1:1 复刻 webchat attachment-support.ts
 * 
 * 支持的 MIME 类型：image/*
 */

private const val TAG = "AttachmentSupport"

/**
 * 附件数据模型
 */
@Serializable
data class ChatAttachment(
    val mimeType: String,
    val dataUrl: String,      // data:image/png;base64,xxx
    val fileName: String? = null,
    val fileSize: Long? = null
)

/**
 * API 附件格式（发送到 Gateway）
 */
@Serializable
data class ApiAttachment(
    val type: String,         // "image"
    val mimeType: String,
    val content: String       // base64 content only
)

/**
 * 检查 MIME 类型是否支持（仅支持图片）
 */
fun isSupportedChatAttachmentMimeType(mimeType: String?): Boolean {
    return !mimeType.isNullOrBlank() && mimeType.startsWith("image/")
}

/**
 * 从 Uri 加载图片并转换为附件
 * 
 * @param context Android Context
 * @param uri 图片 Uri
 * @param maxWidth 最大宽度（用于压缩）
 * @param quality JPEG 质量 (0-100)
 * @return ChatAttachment 或 null
 */
fun loadAttachmentFromUri(
    context: Context,
    uri: Uri,
    maxWidth: Int = 2048,
    quality: Int = 85
): ChatAttachment? {
    return try {
        val contentResolver = context.contentResolver
        val mimeType = contentResolver.getType(uri)
        
        if (!isSupportedChatAttachmentMimeType(mimeType)) {
            Log.w(TAG, "Unsupported MIME type: $mimeType")
            return null
        }
        
        // 获取文件名
        val fileName = getFileName(contentResolver, uri)
        
        // 加载并压缩图片
        val bitmap = loadCompressedBitmap(contentResolver, uri, maxWidth)
            ?: return null
        
        // 转换为 base64
        val outputStream = java.io.ByteArrayOutputStream()
        val compressFormat = when {
            mimeType?.contains("png", ignoreCase = true) == true -> Bitmap.CompressFormat.PNG
            mimeType?.contains("webp", ignoreCase = true) == true -> Bitmap.CompressFormat.WEBP
            else -> Bitmap.CompressFormat.JPEG
        }
        
        bitmap.compress(compressFormat, quality, outputStream)
        val bytes = outputStream.toByteArray()
        val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        
        // 构建 data URL
        val actualMimeType = mimeType ?: "image/jpeg"
        val dataUrl = "data:$actualMimeType;base64,$base64"
        
        Log.i(TAG, "Attachment loaded: $fileName, size=${bytes.size} bytes, mimeType=$actualMimeType")
        
        ChatAttachment(
            mimeType = actualMimeType,
            dataUrl = dataUrl,
            fileName = fileName,
            fileSize = bytes.size.toLong()
        )
    } catch (e: Exception) {
        Log.e(TAG, "Failed to load attachment from uri: $uri", e)
        null
    }
}

/**
 * 从 data URL 解析 base64 内容
 */
fun dataUrlToBase64(dataUrl: String): Pair<String, String>? {
    val match = Regex("^data:([^;]+);base64,(.+)$").find(dataUrl) ?: return null
    val mimeType = match.groupValues[1]
    val content = match.groupValues[2]
    return mimeType to content
}

/**
 * 转换附件为 API 格式
 */
fun toApiAttachment(attachment: ChatAttachment): ApiAttachment? {
    val (mimeType, content) = dataUrlToBase64(attachment.dataUrl) ?: return null
    return ApiAttachment(
        type = "image",
        mimeType = mimeType,
        content = content
    )
}

/**
 * 批量转换附件为 API 格式
 */
fun toApiAttachments(attachments: List<ChatAttachment>): List<ApiAttachment> {
    return attachments.mapNotNull { toApiAttachment(it) }
}

// ─────────────────────────────────────────────────────────────
// Internal helpers
// ─────────────────────────────────────────────────────────────

private fun getFileName(contentResolver: ContentResolver, uri: Uri): String? {
    var fileName: String? = null
    val cursor = contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
    cursor?.use {
        if (it.moveToFirst()) {
            val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0) {
                fileName = it.getString(nameIndex)
            }
        }
    }
    return fileName
}

private fun loadCompressedBitmap(contentResolver: ContentResolver, uri: Uri, maxWidth: Int): Bitmap? {
    // 首先获取图片尺寸
    val options = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
    }
    contentResolver.openInputStream(uri)?.use { 
        BitmapFactory.decodeStream(it, null, options)
    }
    
    val width = options.outWidth
    val height = options.outHeight
    
    // 计算采样率
    var sampleSize = 1
    if (width > maxWidth || height > maxWidth) {
        val halfWidth = width / 2
        val halfHeight = height / 2
        while ((halfWidth / sampleSize) >= maxWidth || (halfHeight / sampleSize) >= maxWidth) {
            sampleSize *= 2
        }
    }
    
    // 加载采样后的图片
    val loadOptions = BitmapFactory.Options().apply {
        inSampleSize = sampleSize
    }
    
    var bitmap = contentResolver.openInputStream(uri)?.use { 
        BitmapFactory.decodeStream(it, null, loadOptions)
    }
    
    // 如果仍然太大，进一步缩放
    if (bitmap != null && (bitmap.width > maxWidth || bitmap.height > maxWidth)) {
        val scale = maxWidth.toFloat() / maxOf(bitmap.width, bitmap.height)
        val newWidth = (bitmap.width * scale).toInt()
        val newHeight = (bitmap.height * scale).toInt()
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        if (scaledBitmap != bitmap) {
            bitmap.recycle()
        }
        bitmap = scaledBitmap
    }
    
    return bitmap
}