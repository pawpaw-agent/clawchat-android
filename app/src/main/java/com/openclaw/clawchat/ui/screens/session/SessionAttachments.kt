package com.openclaw.clawchat.ui.screens.session
import com.openclaw.clawchat.util.AppLog

import android.graphics.BitmapFactory
import android.util.Base64
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.openclaw.clawchat.R
import com.openclaw.clawchat.ui.state.AttachmentUi

/**
 * 计算图片采样率
 */
private fun calculateSampleSize(width: Int, height: Int, maxSize: Int): Int {
    var sampleSize = 1
    val halfWidth = width / 2
    val halfHeight = height / 2
    
    while (halfWidth / sampleSize >= maxSize || halfHeight / sampleSize >= maxSize) {
        sampleSize *= 2
    }
    
    return sampleSize
}

/**
 * 附件预览组件
 */
@Composable
fun AttachmentPreview(
    attachment: AttachmentUi,
    onRemove: () -> Unit
) {
    val context = LocalContext.current

    // 判断是否是图片
    val isImage = attachment.mimeType.startsWith("image/")

    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        if (isImage) {
            // 图片预览
            val bitmap = remember(attachment.dataUrl, attachment.uri) {
                try {
                    if (!attachment.dataUrl.isNullOrBlank()) {
                        val base64Match = Regex("base64,(.+)").find(attachment.dataUrl)
                        val base64 = base64Match?.groupValues?.get(1) ?: attachment.dataUrl
                        val bytes = Base64.decode(base64, Base64.DEFAULT)

                        // 采样解码
                        val options = BitmapFactory.Options().apply {
                            inJustDecodeBounds = true
                        }
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)

                        val maxSize = 256
                        val sampleSize = calculateSampleSize(options.outWidth, options.outHeight, maxSize)

                        val loadOptions = BitmapFactory.Options().apply {
                            inSampleSize = sampleSize
                        }
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, loadOptions)
                    } else {
                        val inputStream = context.contentResolver.openInputStream(attachment.uri)
                        val bytes = inputStream?.readBytes()
                        inputStream?.close()
                        if (bytes != null) {
                            // 采样解码
                            val options = BitmapFactory.Options().apply {
                                inJustDecodeBounds = true
                            }
                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)

                            val maxSize = 256
                            val sampleSize = calculateSampleSize(options.outWidth, options.outHeight, maxSize)

                            val loadOptions = BitmapFactory.Options().apply {
                                inSampleSize = sampleSize
                            }
                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, loadOptions)
                        } else {
                            null
                        }
                    }
                } catch (e: Exception) {
                    AppLog.e("AttachmentPreview", "Failed to decode image: ${e.message}")
                    null
                }
            }

            if (bitmap != null) {
                androidx.compose.foundation.Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = stringResource(R.string.attachment_preview),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                // 图片加载失败
                FilePreviewPlaceholder(attachment)
            }
        } else {
            // 非图片文件显示文件图标和名称
            FilePreviewPlaceholder(attachment)
        }

        IconButton(
            onClick = onRemove,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(24.dp)
                .background(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = CircleShape
                )
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.attachment_remove),
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}

/**
 * 文件预览占位符
 */
@Composable
private fun FilePreviewPlaceholder(attachment: AttachmentUi) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // 文件图标
        Icon(
            imageVector = getFileIcon(attachment.mimeType),
            contentDescription = null,
            modifier = Modifier.size(28.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(4.dp))
        // 文件名（截断显示）
        Text(
            text = attachment.fileName?.take(10)?.plus("...") ?: stringResource(R.string.attachment_file),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

/**
 * 根据文件类型获取图标
 */
private fun getFileIcon(mimeType: String): androidx.compose.ui.graphics.vector.ImageVector {
    return when {
        mimeType.startsWith("image/") -> Icons.Default.Image
        mimeType.startsWith("video/") -> Icons.Default.VideoFile
        mimeType.startsWith("audio/") -> Icons.Default.AudioFile
        mimeType == "application/pdf" -> Icons.Default.PictureAsPdf
        mimeType.contains("word") || mimeType.contains("document") -> Icons.Default.Description
        mimeType.contains("excel") || mimeType.contains("spreadsheet") -> Icons.Default.TableChart
        mimeType.contains("powerpoint") || mimeType.contains("presentation") -> Icons.Default.Slideshow
        mimeType.startsWith("text/") -> Icons.Default.TextSnippet
        mimeType.contains("zip") || mimeType.contains("rar") || mimeType.contains("archive") -> Icons.Default.FolderZip
        else -> Icons.Default.InsertDriveFile
    }
}

/**
 * 从 Uri 加载附件
 */
fun createAttachmentFromUri(
    context: android.content.Context,
    uri: Uri
): AttachmentUi {
    val mimeType = context.contentResolver.getType(uri) ?: "image/png"
    return AttachmentUi(
        id = "att-${System.currentTimeMillis()}-${(0..9999).random()}",
        uri = uri,
        mimeType = mimeType
    )
}