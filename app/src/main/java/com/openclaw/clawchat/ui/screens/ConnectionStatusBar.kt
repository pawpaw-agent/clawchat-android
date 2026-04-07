package com.openclaw.clawchat.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.openclaw.clawchat.R
import com.openclaw.clawchat.ui.state.ConnectionStatus

/**
 * 连接状态提示条
 */
@Composable
fun ConnectionStatusBar(connectionStatus: ConnectionStatus) {
    val (icon, color, text) = when (connectionStatus) {
        is ConnectionStatus.Connecting -> Triple(Icons.Default.Sync, MaterialTheme.colorScheme.primary, stringResource(R.string.status_connecting))
        is ConnectionStatus.Disconnected -> Triple(Icons.Default.CloudOff, MaterialTheme.colorScheme.outline, stringResource(R.string.status_disconnected))
        is ConnectionStatus.Error -> Triple(Icons.Default.Error, MaterialTheme.colorScheme.error, stringResource(R.string.status_error))
        else -> Triple(Icons.Default.CheckCircle, MaterialTheme.colorScheme.primary, stringResource(R.string.status_connected))
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
                    text = stringResource(R.string.network_connection_failed),
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
                Text(stringResource(R.string.retry))
            }

            IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.close),
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
                is ConnectionStatus.Disconnected -> stringResource(R.string.session_not_connected)
                is ConnectionStatus.Connecting -> stringResource(R.string.status_connecting)
                is ConnectionStatus.Disconnecting -> stringResource(R.string.status_disconnecting)
                is ConnectionStatus.Error -> stringResource(R.string.error_connection_exception, connectionStatus.message)
                is ConnectionStatus.Connected -> stringResource(R.string.status_connected)
                else -> stringResource(R.string.error_unknown)
            },
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (connectionStatus is ConnectionStatus.Disconnected || connectionStatus is ConnectionStatus.Error) {
            Text(
                text = stringResource(R.string.network_configure_gateway),
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
                Text(stringResource(R.string.go_to_settings))
            }
        }
    }
}