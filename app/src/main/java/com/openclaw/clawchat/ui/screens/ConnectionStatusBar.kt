package com.openclaw.clawchat.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.openclaw.clawchat.ui.state.ConnectionStatus

/**
 * 连接状态提示条
 */
@Composable
fun ConnectionStatusBar(connectionStatus: ConnectionStatus) {
    val (icon, color, text) = when (connectionStatus) {
        is ConnectionStatus.Connecting -> Triple(Icons.Default.Sync, MaterialTheme.colorScheme.primary, "正在连接...")
        is ConnectionStatus.Disconnected -> Triple(Icons.Default.CloudOff, MaterialTheme.colorScheme.outline, "未连接")
        is ConnectionStatus.Error -> Triple(Icons.Default.Error, MaterialTheme.colorScheme.error, "连接错误")
        else -> Triple(Icons.Default.CheckCircle, MaterialTheme.colorScheme.primary, "已连接")
    }
    
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = color.copy(alpha = 0.1f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = color
            )
        }
    }
}

/**
 * 连接错误 Banner
 */
@Composable
fun ConnectionErrorBanner(
    error: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "连接失败",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f),
                    maxLines = 2
                )
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            TextButton(onClick = onRetry) {
                Text("重试")
            }
            
            IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "关闭",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * 未连接状态内容
 */
@Composable
fun NotConnectedContent(
    connectionStatus: ConnectionStatus,
    onOpenSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.CloudOff,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = when (connectionStatus) {
                is ConnectionStatus.Disconnected -> "未连接到 Gateway"
                is ConnectionStatus.Connecting -> "正在连接..."
                is ConnectionStatus.Disconnecting -> "正在断开..."
                is ConnectionStatus.Error -> "连接错误：${connectionStatus.message}"
                is ConnectionStatus.Connected -> "已连接"
                else -> "未知状态"
            },
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        if (connectionStatus is ConnectionStatus.Disconnected || connectionStatus is ConnectionStatus.Error) {
            Text(
                text = "请前往设置页面配置 Gateway 连接",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Button(onClick = onOpenSettings) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("去设置")
            }
        }
    }
}