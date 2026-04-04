package com.openclaw.clawchat.util

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * 文件处理工具类 - 用于附件处理（参考 webchat 文件上传逻辑）
 */
object FileUtils {

    /**
     * 从 URI 获取文件名
     */
    fun getFileNameFromUri(context: Context, uri: Uri): String? {
        return when (uri.scheme) {
            "content" -> getFileNameFromContentUri(context, uri)
            "file" -> uri.lastPathSegment
            else -> null
        }
    }

    /**
     * 从内容 URI 获取文件名
     */
    private fun getFileNameFromContentUri(context: Context, uri: Uri): String? {
        var fileName: String? = null
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0) {
                cursor.moveToFirst()
                fileName = cursor.getString(nameIndex)
            }
        }
        return fileName
    }

    /**
     * 将 URI 转换为 Base64 字符串
     */
    fun uriToBase64(context: Context, uri: Uri): String? {
        return try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            val outputStream = ByteArrayOutputStream()

            inputStream?.use { input ->
                outputStream.use { output ->
                    input.copyTo(output)
                }
            }

            val byteArray = outputStream.toByteArray()
            android.util.Base64.encodeToString(byteArray, android.util.Base64.NO_WRAP)
        } catch (e: Exception) {
            AppLog.e("FileUtils", "Failed to convert URI to base64", e)
            null
        }
    }

    /**
     * 获取文件大小
     */
    fun getFileSize(context: Context, uri: Uri): Long? {
        return try {
            var fileSize: Long? = null
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex >= 0) {
                    cursor.moveToFirst()
                    fileSize = cursor.getLong(sizeIndex)
                }
            }
            fileSize
        } catch (e: Exception) {
            AppLog.e("FileUtils", "Failed to get file size", e)
            null
        }
    }
}