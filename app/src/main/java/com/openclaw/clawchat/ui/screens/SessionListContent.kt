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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.openclaw.clawchat.R
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
    onSelectSession: (String) -> Unit,
    onSessionLongPress: (SessionUi?) -> Unit,
    onCreateSession: () -> Unit,
    onRefresh: () -> Unit,
    onDeleteSession: (String) -> Unit = {},
    onSteerSession: ((String, String) -> Unit)? = null,  // sessionKey, steerText
    onRenameSession: ((String, String) -> Unit)? = null,  // sessionKey, newLabel
    onTogglePinSession: ((String, Boolean) -> Unit)? = null  // sessionKey, currentPinned
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 列表内容
            if (state.isLoading && state.sessions.isEmpty()) {
                // 加载骨架屏
                com.openclaw.clawchat.ui.components.LoadingSkeleton(
                    type = com.openclaw.clawchat.ui.components.SkeletonType.SESSION,
                    modifier = Modifier.fillMaxSize()
                )
            } else if (state.sessions.isEmpty()) {
                EmptySessionList(onCreateSession = onCreateSession)
            } else {
                // 下拉刷新
                val refreshState = rememberPullToRefreshState()

                PullToRefreshBox(
                    state = refreshState,
                    isRefreshing = state.isLoading && state.sessions.isNotEmpty(),
                    onRefresh = onRefresh,
                    modifier = Modifier.fillMaxSize()
                ) {
                    SessionList(
                        sessions = state.sessions,
                        currentSession = state.currentSession,
                        onSelectSession = onSelectSession,
                        onSessionLongPress = onSessionLongPress,
                        onDeleteSession = onDeleteSession,
                        onSteerSession = onSteerSession,
                        onRenameSession = onRenameSession,
                        onTogglePinSession = onTogglePinSession
                    )
                }
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
    onDeleteSession: (String) -> Unit = {},
    onSteerSession: ((String, String) -> Unit)? = null,
    onRenameSession: ((String, String) -> Unit)? = null,
    onTogglePinSession: ((String, Boolean) -> Unit)? = null
) {
    // Localized date labels
    val todayLabel = stringResource(R.string.session_today)
    val yesterdayLabel = stringResource(R.string.session_yesterday)
    val thisWeekLabel = stringResource(R.string.session_this_week)
    val earlierLabel = stringResource(R.string.session_earlier)

    // 按日期分组
    val groupedSessions = remember(sessions, todayLabel, yesterdayLabel, thisWeekLabel, earlierLabel) {
        groupSessionsByDate(sessions, todayLabel, yesterdayLabel, thisWeekLabel, earlierLabel)
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
                key = { session -> session.key }
            ) { session ->
                SessionItem(
                    session = session,
                    isSelected = currentSession?.key == session.key,
                    onSelect = { onSelectSession(session.key) },
                    onSessionLongPress = { onSessionLongPress(session) },
                    onDelete = { id -> onDeleteSession(id) },
                    onSteer = onSteerSession,
                    onRename = onRenameSession,
                    onTogglePin = onTogglePinSession,
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
 * 按日期分组会话（支持置顶和归档）
 */
private fun groupSessionsByDate(
    sessions: List<SessionUi>,
    todayLabel: String,
    yesterdayLabel: String,
    thisWeekLabel: String,
    earlierLabel: String
): List<Pair<String, List<SessionUi>>> {
    // 先按置顶/归档排序，再按日期分组
    val sortedSessions = sessions.sortedWith(
        compareByDescending<SessionUi> { it.isPinned }
            .thenBy { it.isArchived }
            .thenByDescending { it.lastActivityAt }
    )

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

    sortedSessions.forEach { session ->
        val label = when {
            session.lastActivityAt >= today -> todayLabel
            session.lastActivityAt >= yesterday -> yesterdayLabel
            session.lastActivityAt >= weekAgo -> {
                val cal = Calendar.getInstance()
                cal.timeInMillis = session.lastActivityAt
                SimpleDateFormat("EEEE", Locale.getDefault()).format(cal.time)
            }
            else -> earlierLabel
        }

        groups.getOrPut(label) { mutableListOf() }.add(session)
    }

    // 保持顺序：今天 -> 昨天 -> 本周 -> 更早
    return listOf(todayLabel, yesterdayLabel, thisWeekLabel, earlierLabel)
        .filter { groups.containsKey(it) }
        .mapNotNull { label ->
            // 处理 "本周" 的特殊情况
            if (label == thisWeekLabel) {
                val weekSessions = groups.filterKeys { it != todayLabel && it != yesterdayLabel && it != earlierLabel }
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
 * 会话列表项（支持长按删除和引导）
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SessionItem(
    session: SessionUi,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onSessionLongPress: (() -> Unit)? = null,
    onDelete: (String) -> Unit = {},
    onSteer: ((String, String) -> Unit)? = null,
    onRename: ((String, String) -> Unit)? = null,
    onTogglePin: ((String, Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showSteerDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var steerText by remember { mutableStateOf("") }
    var renameText by remember { mutableStateOf(session.label ?: "") }
    var showMenu by remember { mutableStateOf(false) }
    
    // 引导对话框
    if (showSteerDialog) {
        AlertDialog(
            onDismissRequest = { showSteerDialog = false },
            title = { Text(stringResource(R.string.session_steer_title)) },
            text = {
                OutlinedTextField(
                    value = steerText,
                    onValueChange = { steerText = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.session_steer_placeholder)) },
                    singleLine = false,
                    maxLines = 3
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (steerText.isNotBlank() && onSteer != null) {
                            onSteer(session.key, steerText)
                            steerText = ""
                        }
                        showSteerDialog = false
                    }
                ) {
                    Text(stringResource(R.string.session_steer_send))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSteerDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
    
    // 重命名对话框
    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text(stringResource(R.string.session_rename_title)) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.session_enter_new_name)) },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (renameText.isNotBlank() && onRename != null) {
                            onRename(session.key, renameText)
                        }
                        showRenameDialog = false
                    }
                ) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
    
    // 删除确认对话框
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.session_delete_title)) },
            text = { Text(stringResource(R.string.session_delete_message, session.getDisplayName())) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(session.key)
                        showDeleteConfirm = false
                    }
                ) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .then(
                if (onSessionLongPress != null || onSteer != null) {
                    Modifier.combinedClickable(
                        onClick = onSelect,
                        onLongClick = {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            showMenu = true
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
            // 会话头像（根据 Agent 或 Model 显示不同图标）
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            session.agentId != null -> MaterialTheme.colorScheme.tertiaryContainer
                            session.model?.contains("claude", ignoreCase = true) == true -> MaterialTheme.colorScheme.primaryContainer
                            session.model?.contains("gpt", ignoreCase = true) == true -> MaterialTheme.colorScheme.secondaryContainer
                            else -> MaterialTheme.colorScheme.primaryContainer
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                // Agent emoji 或图标
                if (session.agentEmoji != null) {
                    Text(
                        text = session.agentEmoji,
                        style = MaterialTheme.typography.titleMedium
                    )
                } else if (session.agentId != null) {
                    Icon(
                        imageVector = Icons.Default.SmartToy,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Icon(
                        imageVector = when {
                            session.model?.contains("claude", ignoreCase = true) == true -> Icons.Default.SmartToy
                            session.model?.contains("gpt", ignoreCase = true) == true -> Icons.Default.Psychology
                            session.model?.contains("gemini", ignoreCase = true) == true -> Icons.Default.AutoAwesome
                            session.label?.contains("GPT", ignoreCase = true) == true -> Icons.Default.Psychology
                            session.label?.contains("Chat", ignoreCase = true) == true -> Icons.Default.SmartToy
                            else -> Icons.Default.Chat
                        },
                        contentDescription = null,
                        tint = when {
                            session.model?.contains("claude", ignoreCase = true) == true -> MaterialTheme.colorScheme.onPrimaryContainer
                            session.model?.contains("gpt", ignoreCase = true) == true -> MaterialTheme.colorScheme.onSecondaryContainer
                            else -> MaterialTheme.colorScheme.onPrimaryContainer
                        },
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                // 会话名称 + Agent/Model 标签
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = session.getDisplayName(),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    // Agent 标签
                    if (session.agentId != null) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f)
                        ) {
                            Text(
                                text = stringResource(R.string.selector_agent_label),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    // Model 标签
                    if (session.model != null && session.agentId == null) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                        ) {
                            Text(
                                text = formatModelName(session.model),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
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
                            text = "· ${stringResource(R.string.session_message_count, session.messageCount)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        
        // 长按菜单
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            // 重命名选项
            DropdownMenuItem(
                text = { Text(stringResource(R.string.session_rename)) },
                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                onClick = {
                    showMenu = false
                    renameText = session.label ?: ""
                    showRenameDialog = true
                }
            )

            // 置顶选项
            DropdownMenuItem(
                text = { Text(if (session.isPinned) stringResource(R.string.session_unpin) else stringResource(R.string.session_pin)) },
                leadingIcon = {
                    Icon(
                        if (session.isPinned) Icons.Default.PushPin else Icons.Default.PushPin,
                        contentDescription = null
                    )
                },
                onClick = {
                    showMenu = false
                    onTogglePin?.invoke(session.key, session.isPinned)
                }
            )

            // 引导选项
            if (onSteer != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.session_steer_title)) },
                    leadingIcon = { Icon(Icons.Default.Navigation, contentDescription = null) },
                    onClick = {
                        showMenu = false
                        showSteerDialog = true
                    }
                )
            }

            Divider()
            
            // 删除选项
            DropdownMenuItem(
                text = { Text(stringResource(R.string.session_delete_button), color = MaterialTheme.colorScheme.error) },
                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                onClick = {
                    showMenu = false
                    showDeleteConfirm = true
                }
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
            text = stringResource(R.string.session_empty_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.session_empty_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(24.dp))
        FilledIconButton(onClick = onCreateSession) {
            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.session_create_button))
        }
    }
}

/**
 * 格式化时间为"多久以前"
 */
@Composable
private fun formatTimeAgo(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp

    return when {
        diff < 60_000 -> stringResource(R.string.session_time_just_now)
        diff < 3_600_000 -> stringResource(R.string.session_time_minutes_ago, diff / 60_000)
        diff < 86_400_000 -> stringResource(R.string.session_time_hours_ago, diff / 3_600_000)
        diff < 604_800_000 -> stringResource(R.string.session_time_days_ago, diff / 86_400_000)
        else -> SimpleDateFormat("MM-dd", Locale.getDefault()).format(Date(timestamp))
    }
}

/**
 * 格式化模型名称（简化显示）
 */
private fun formatModelName(model: String): String {
    return when {
        // Claude 模型
        model.contains("claude-3-5-sonnet", ignoreCase = true) -> "Claude 3.5"
        model.contains("claude-3-opus", ignoreCase = true) -> "Claude 3 Opus"
        model.contains("claude-3-sonnet", ignoreCase = true) -> "Claude 3"
        model.contains("claude-3-haiku", ignoreCase = true) -> "Claude 3 Haiku"
        model.contains("claude", ignoreCase = true) -> "Claude"

        // GPT 模型
        model.contains("gpt-4o", ignoreCase = true) -> "GPT-4o"
        model.contains("gpt-4-turbo", ignoreCase = true) -> "GPT-4 Turbo"
        model.contains("gpt-4", ignoreCase = true) -> "GPT-4"
        model.contains("gpt-3.5", ignoreCase = true) -> "GPT-3.5"
        model.contains("gpt", ignoreCase = true) -> "GPT"

        // Gemini 模型
        model.contains("gemini-1.5-pro", ignoreCase = true) -> "Gemini 1.5 Pro"
        model.contains("gemini-1.5-flash", ignoreCase = true) -> "Gemini 1.5 Flash"
        model.contains("gemini-pro", ignoreCase = true) -> "Gemini Pro"
        model.contains("gemini", ignoreCase = true) -> "Gemini"

        // Llama 模型
        model.contains("llama-3", ignoreCase = true) -> "Llama 3"
        model.contains("llama", ignoreCase = true) -> "Llama"

        // 其他
        else -> model.take(12)
    }
}