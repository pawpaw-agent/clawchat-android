package com.openclaw.clawchat.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.openclaw.clawchat.ui.state.ConnectionStatus
import com.openclaw.clawchat.ui.state.MainUiState
import com.openclaw.clawchat.ui.state.SessionUi
import java.text.SimpleDateFormat
import java.util.*

/**
 * 会话列表内容
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionListContent(
    state: MainUiState,
    onSelectSession: (String) -> Unit,
    onSessionLongPress: (SessionUi?) -> Unit,
    onCreateSession: () -> Unit,
    onRefresh: () -> Unit
) {
    var isRefreshing by remember { mutableStateOf(false) }
    
    Column(modifier = Modifier.fillMaxSize()) {
        // 连接状态提示条（未连接时显示）
        if (state.connectionStatus !is ConnectionStatus.Connected) {
            ConnectionStatusBar(connectionStatus = state.connectionStatus)
        }
        
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                isRefreshing = true
                onRefresh()
                isRefreshing = false
            },
            modifier = Modifier.weight(1f)
        ) {
            if (state.sessions.isEmpty()) {
                EmptySessionList(onCreateSession = onCreateSession)
            } else {
                SessionList(
                    sessions = state.sessions,
                    currentSession = state.currentSession,
                    onSelectSession = onSelectSession,
                    onSessionLongPress = onSessionLongPress
                )
            }
        }
    }
}

/**
 * 会话列表
 */
@Composable
private fun SessionList(
    sessions: List<SessionUi>,
    currentSession: SessionUi?,
    onSelectSession: (String) -> Unit,
    onSessionLongPress: (SessionUi?) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(sessions, key = { it.id }) { session ->
            SessionItem(
                session = session,
                isSelected = currentSession?.id == session.id,
                onSelect = { onSelectSession(session.id) },
                onSessionLongPress = { onSessionLongPress(session) }
            )
        }
    }
}

/**
 * 会话列表项
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SessionItem(
    session: SessionUi,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onSessionLongPress: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .let {
                if (onSessionLongPress != null) {
                    it.combinedClickable(
                        onClick = onSelect,
                        onLongClick = onSessionLongPress
                    )
                } else {
                    it.clickable(onClick = onSelect)
                }
            },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // 会话名称
            Text(
                text = session.getDisplayName(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1
            )
            
            // 最后一条消息
            if (session.lastMessage != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = session.lastMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            
            // 时间
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = formatTimeAgo(session.lastActivityAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 空会话列表
 */
@Composable
private fun EmptySessionList(onCreateSession: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Chat,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "暂无会话",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "点击下方按钮创建新会话",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        FilledIconButton(onClick = onCreateSession) {
            Icon(Icons.Default.Add, contentDescription = "创建会话")
        }
    }
}

/**
 * 连接状态提示条
 */
@Composable
private fun ConnectionStatusBar(connectionStatus: ConnectionStatus) {
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
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
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
 * 格式化时间为"多久以前"
 */
private fun formatTimeAgo(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    
    return when {
        diff < 60_000 -> "刚刚"
        diff < 3_600_000 -> "${diff / 60_000} 分钟前"
        diff < 86_400_000 -> "${diff / 3_600_000} 小时前"
        diff < 604_800_000 -> "${diff / 86_400_000} 天前"
        else -> SimpleDateFormat("MM-dd", Locale.getDefault()).format(Date(timestamp))
    }
}