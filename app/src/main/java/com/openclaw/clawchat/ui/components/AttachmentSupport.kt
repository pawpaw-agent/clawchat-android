package com.openclaw.clawchat.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.openclaw.clawchat.ui.state.AttachmentUi
import com.openclaw.clawchat.util.FileUtils
import java.util.*

/**
 * 附件支持组件 - 1:1 复刻 webchat 附件功能
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachmentSupport(
    attachments: List<AttachmentUi>,
    onAddAttachment: (AttachmentUi) -> Unit,
    onRemoveAttachment: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // 图片选择器
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri: Uri? ->
            uri?.let { selectedUri ->
                val fileName = FileUtils.getFileNameFromUri(context, selectedUri) ?: "image_${System.currentTimeMillis()}.jpg"
                val mimeType = context.contentResolver.getType(selectedUri) ?: "image/jpeg"

                // 将 URI 转换为 base64
                val base64String = FileUtils.uriToBase64(context, selectedUri)

                if (base64String != null) {
                    val attachment = AttachmentUi(
                        id = UUID.randomUUID().toString(),
                        fileName = fileName,
                        mimeType = mimeType,
                        size = 0, // 由于是 URI，大小未知
                        dataUrl = "data:$mimeType;base64,$base64String"
                    )
                    onAddAttachment(attachment)
                }
            }
        }
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // 附件预览行
        if (attachments.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(attachments) { attachment ->
                    AttachmentPreview(
                        attachment = attachment,
                        onRemove = { onRemoveAttachment(attachment.id) }
                    )
                }
            }
        }

        // 添加附件按钮
        OutlinedButton(
            onClick = {
                imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            contentPadding = PaddingValues(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.AddPhotoAlternate,
                contentDescription = "添加图片",
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (attachments.isEmpty()) "添加图片" else "添加更多图片",
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@Composable
private fun AttachmentPreview(
    attachment: AttachmentUi,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier
            .size(80.dp)
            .clickable { onRemove() },
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = attachment.dataUrl,
                contentDescription = attachment.fileName,
                modifier = Modifier.fillMaxSize()
            )

            // 删除按钮
            IconButton(
                onClick = onRemove,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(24.dp)
                    .then(
                        // 使按钮背景透明
                        Modifier.background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "删除附件",
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}