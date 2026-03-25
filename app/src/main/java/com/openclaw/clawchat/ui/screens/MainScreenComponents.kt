package com.openclaw.clawchat.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.openclaw.clawchat.ui.state.SessionStatus
import com.openclaw.clawchat.ui.state.SessionUi

/**
 * 会话选项对话框
 */
@Composable
fun SessionOptionsDialog(
    session: SessionUi,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
    onPauseResume: () -> Unit,
    onTerminate: () -> Unit,
    onDelete: () -> Unit
) {
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf(session.label ?: session.getDisplayName()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("会话选项") },
        text = {
            Column {
                Text(
                    text = session.getDisplayName(),
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "状态：${session.status.name}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "消息数：${session.messageCount}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Column {
                Button(
                    onClick = onPauseResume,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Text(
                        if (session.status == SessionStatus.RUNNING) {
                            "暂停会话"
                        } else {
                            "恢复会话"
                        }
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { showRenameDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("重命名")
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onTerminate,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("终止会话")
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("删除会话")
                }
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )

    // 删除确认对话框
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除会话") },
            text = { Text("确定要删除会话「${session.getDisplayName()}」吗？此操作不可撤销。") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }

    // 重命名对话框
    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("重命名会话") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("会话名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = { onRename(newName) },
                    enabled = newName.isNotBlank()
                ) {
                    Text("确认")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showRenameDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

/**
 * 连接状态图标
 */
@Composable
fun ConnectionStatusIcon(status: com.openclaw.clawchat.ui.state.ConnectionStatus) {
    val (icon, color) = when (status) {
        is com.openclaw.clawchat.ui.state.ConnectionStatus.Connected -> Icons.Default.CheckCircle to MaterialTheme.colorScheme.primary
        is com.openclaw.clawchat.ui.state.ConnectionStatus.Connecting, is com.openclaw.clawchat.ui.state.ConnectionStatus.Disconnecting -> Icons.Default.Sync to MaterialTheme.colorScheme.error
        is com.openclaw.clawchat.ui.state.ConnectionStatus.Disconnected -> Icons.Default.CloudOff to MaterialTheme.colorScheme.onSurfaceVariant
        is com.openclaw.clawchat.ui.state.ConnectionStatus.Error -> Icons.Default.Error to MaterialTheme.colorScheme.error
        else -> Icons.Default.Help to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Icon(
        imageVector = icon,
        contentDescription = "连接状态",
        tint = color
    )
}