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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.openclaw.clawchat.ui.components.PulseIndicator
import com.openclaw.clawchat.ui.components.PulseState
import com.openclaw.clawchat.ui.state.ConnectionStatus
import com.openclaw.clawchat.ui.state.MainUiState
import com.openclaw.clawchat.ui.state.SessionStatus
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
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
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
            Column {
                // 搜索栏
                if (state.sessions.isNotEmpty()) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        placeholder = { Text("搜索会话...") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null
                            )
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )
                }

                // 会话列表或空状态
                if (state.sessions.isEmpty()) {
                    EmptySessionList(onCreateSession = onCreateSession)
                } else {
                    SessionList(
                        sessions = filterSessions(state.sessions, searchQuery),
                        currentSession = state.currentSession,
                        onSelectSession = onSelectSession,
                        onSessionLongPress = onSessionLongPress
                    )
                }
            }
        }
    }
}

/**
 * 空会话列表
 */
@Composable
private fun EmptySessionList(
    onCreateSession: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.ChatBubbleOutline,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "暂无会话",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "点击 + 创建新会话",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(onClick = onCreateSession) {
            Text("创建会话")
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
        contentPadding = PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(sessions, key = { it.id }) { session ->
            SessionItem(
                session = session,
                isSelected = session.id == currentSession?.id,
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
    // 根据会话状态确定脉冲状态
    val pulseState = remember(session.id, session.status, session.lastActivityAt) {
        when (session.status) {
            SessionStatus.RUNNING -> {
                // 运行中：最近5分钟活跃
                val fiveMinutesAgo = System.currentTimeMillis() - 5 * 60 * 1000
                if (session.lastActivityAt > fiveMinutesAgo) {
                    PulseState.Streaming
                } else {
                    PulseState.Active
                }
            }
            SessionStatus.PAUSED -> PulseState.Thinking
            SessionStatus.TERMINATED -> PulseState.Idle
        }
    }
    
    // 根据状态确定颜色
    val pulseColor = when (session.status) {
        SessionStatus.RUNNING -> if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
        SessionStatus.PAUSED -> MaterialTheme.colorScheme.outline
        SessionStatus.TERMINATED -> MaterialTheme.colorScheme.outlineVariant
    }
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左侧脉冲指示器
        PulseIndicator(
            state = pulseState,
            modifier = Modifier.padding(end = 8.dp),
            color = pulseColor
        )
        
        Card(
            modifier = Modifier
                .weight(1f)
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
                // 会话名称（显示 agent 名称）
                Text(
                    text = session.getDisplayName(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            
                Spacer(modifier = Modifier.height(4.dp))
            
                // 时间和消息数
                Row {
                    Text(
                        text = formatTimeAgo(session.lastActivityAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    if (session.messageCount > 0) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "• ${session.messageCount} 条消息",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            
                // 最后一条消息
                if (session.lastMessage != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = session.lastMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
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
        diff < 3600_000 -> "${diff / 60_000} 分钟前"
        diff < 86400_000 -> "${diff / 3600_000} 小时前"
        diff < 604800_000 -> "${diff / 86400_000} 天前"
        else -> SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))
    }
}

/**
 * 过滤会话列表
 */
private fun filterSessions(sessions: List<SessionUi>, query: String): List<SessionUi> {
    if (query.isBlank()) {
        return sessions
    }
    val lowerQuery = query.lowercase()
    return sessions.filter { session ->
        session.getDisplayName().lowercase().contains(lowerQuery) ||
        session.label?.lowercase()?.contains(lowerQuery) == true ||
        session.lastMessage?.lowercase()?.contains(lowerQuery) == true
    }
}