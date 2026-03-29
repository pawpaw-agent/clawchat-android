package com.openclaw.clawchat.ui.screens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.openclaw.clawchat.ui.state.ConnectionStatus
import com.openclaw.clawchat.ui.state.MainUiState
import com.openclaw.clawchat.ui.state.SessionUi
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 会话列表内容
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionListContent(
    state: MainUiState,
    searchQuery: String = "",
    onSearchQueryChange: (String) -> Unit = {},
    onSelectSession: (String) -> Unit,
    onSessionLongPress: (SessionUi?) -> Unit,
    onCreateSession: () -> Unit,
    onRefresh: () -> Unit,
    onDeleteSession: (String) -> Unit = {}
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 搜索框
            if (state.sessions.isNotEmpty() || searchQuery.isNotBlank()) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text("搜索会话...") },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null)
                    },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { onSearchQueryChange("") }) {
                                Icon(Icons.Default.Close, contentDescription = "清除")
                            }
                        }
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )
            }
            
            // 列表内容
            if (state.isLoading && state.sessions.isEmpty()) {
                // 加载骨架屏
                com.openclaw.clawchat.ui.components.LoadingSkeleton(
                    type = com.openclaw.clawchat.ui.components.SkeletonType.SESSION,
                    modifier = Modifier.fillMaxSize()
                )
            } else if (state.sessions.isEmpty()) {
                if (searchQuery.isBlank()) {
                    EmptySessionList(onCreateSession = onCreateSession)
                } else {
                    // 搜索无结果
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(modifier = Modifier.height(48.dp))
                        Icon(
                            imageVector = Icons.Default.SearchOff,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "未找到 \"$searchQuery\"",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                SessionList(
                    sessions = state.sessions,
                    currentSession = state.currentSession,
                    onSelectSession = onSelectSession,
                    onSessionLongPress = onSessionLongPress,
                    onDeleteSession = onDeleteSession
                )
            }
        }
        
        // 连接状态提示条（未连接时显示）- 放在最上层
        if (state.connectionStatus !is ConnectionStatus.Connected) {
            ConnectionStatusBar(connectionStatus = state.connectionStatus)
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
    onSessionLongPress: (SessionUi?) -> Unit,
    onDeleteSession: (String) -> Unit = {}
) {
    // 按日期分组
    val groupedSessions = remember(sessions) {
        groupSessionsByDate(sessions)
    }
    
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        groupedSessions.forEach { (dateLabel, sessionsInGroup) ->
            // 日期标题
            item {
                Text(
                    text = dateLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            
            // 该日期下的会话
            items(
                items = sessionsInGroup,
                key = { session -> session.id }
            ) { session ->
                SessionItem(
                    session = session,
                    isSelected = currentSession?.id == session.id,
                    onSelect = { onSelectSession(session.id) },
                    onSessionLongPress = { onSessionLongPress(session) },
                    onDelete = { id -> onDeleteSession(id) },
                    modifier = Modifier.animateItem(
                        fadeInSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        placementSpec = spring(stiffness = Spring.StiffnessMediumLow)
                    )
                )
            }
        }
    }
}

/**
 * 按日期分组会话
 */
private fun groupSessionsByDate(sessions: List<SessionUi>): List<Pair<String, List<SessionUi>>> {
    val now = System.currentTimeMillis()
    val today = Calendar.getInstance().apply {
        timeInMillis = now
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    
    val yesterday = today - 86_400_000 // 24 hours in ms
    val weekAgo = today - 7 * 86_400_000
    
    val groups = mutableMapOf<String, MutableList<SessionUi>>()
    
    sessions.forEach { session ->
        val label = when {
            session.lastActivityAt >= today -> "今天"
            session.lastActivityAt >= yesterday -> "昨天"
            session.lastActivityAt >= weekAgo -> {
                val cal = Calendar.getInstance()
                cal.timeInMillis = session.lastActivityAt
                SimpleDateFormat("EEEE", Locale.getDefault()).format(cal.time)
            }
            else -> "更早"
        }
        
        groups.getOrPut(label) { mutableListOf() }.add(session)
    }
    
    // 保持顺序：今天 -> 昨天 -> 本周 -> 更早
    return listOf("今天", "昨天", "本周", "更早")
        .filter { groups.containsKey(it) }
        .mapNotNull { label ->
            // 处理 "本周" 的特殊情况
            if (label == "本周") {
                val weekSessions = groups.filterKeys { it != "今天" && it != "昨天" && it != "更早" }
                    .values
                    .flatten()
                if (weekSessions.isNotEmpty()) {
                    label to weekSessions
                } else null
            } else {
                groups[label]?.let { label to it }
            }
        }
}

/**
 * 会话列表项（支持长按删除）
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SessionItem(
    session: SessionUi,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onSessionLongPress: (() -> Unit)? = null,
    onDelete: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    var showDeleteConfirm by remember { mutableStateOf(false) }
    
    // 删除确认对话框
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除会话") },
            text = { Text("确定要删除「${session.getDisplayName()}」吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(session.id)
                        showDeleteConfirm = false
                    }
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .then(
                if (onSessionLongPress != null) {
                    Modifier.combinedClickable(
                        onClick = onSelect,
                        onLongClick = {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            showDeleteConfirm = true
                        }
                    )
                } else {
                    Modifier.clickable(onClick = onSelect)
                }
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 会话头像
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (session.title.contains("GPT", ignoreCase = true) || 
                                     session.title.contains("Chat", ignoreCase = true)) {
                        Icons.Default.SmartToy
                    } else {
                        Icons.Default.Chat
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                // 会话名称
                Text(
                    text = session.getDisplayName(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
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
                
                // 时间和消息数
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatTimeAgo(session.lastActivityAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (session.messageCount > 0) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "· ${session.messageCount} 条消息",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
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
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.ChatBubbleOutline,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
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
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(24.dp))
        FilledIconButton(onClick = onCreateSession) {
            Icon(Icons.Default.Add, contentDescription = "创建会话")
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