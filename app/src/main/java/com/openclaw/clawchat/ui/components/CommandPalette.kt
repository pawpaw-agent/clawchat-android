package com.openclaw.clawchat.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.openclaw.clawchat.R

/**
 * 命令面板 - 类似 ⌘K 快速搜索
 * 
 * 功能：
 * - 快速搜索会话
 * - 快速执行命令
 * - 快捷键触发
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommandPalette(
    query: String,
    onQueryChange: (String) -> Unit,
    sessions: List<CommandPaletteItem.SessionItem>,
    commands: List<CommandPaletteItem.CommandItem>,
    onSessionSelect: (String) -> Unit,
    onCommandExecute: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    
    // 过滤结果
    val filteredSessions = remember(query, sessions) {
        if (query.isBlank()) sessions.take(5)
        else sessions.filter { it.title.contains(query, ignoreCase = true) }
    }
    
    val filteredCommands = remember(query, commands) {
        if (query.isBlank()) commands
        else commands.filter { 
            it.title.contains(query, ignoreCase = true) ||
            it.description?.contains(query, ignoreCase = true) == true
        }
    }
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.7f),
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 6.dp
        ) {
            Column {
                // 搜索框
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .focusRequester(focusRequester),
                    placeholder = { Text(stringResource(R.string.command_palette_search_placeholder)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotBlank()) {
                            IconButton(onClick = { onQueryChange("") }) {
                                Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.clear))
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        // 执行第一个结果
                        if (filteredSessions.isNotEmpty()) {
                            onSessionSelect(filteredSessions.first().id)
                        } else if (filteredCommands.isNotEmpty()) {
                            onCommandExecute(filteredCommands.first().id)
                        }
                    }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    )
                )
                
                Divider()
                
                // 结果列表
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(8.dp)
                ) {
                    // 会话结果
                    if (filteredSessions.isNotEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.command_palette_sessions),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        items(filteredSessions, key = { "session-${it.id}" }) { session ->
                            CommandPaletteItemRow(
                                item = session,
                                onClick = { onSessionSelect(session.id) }
                            )
                        }
                    }
                    
                    // 命令结果
                    if (filteredCommands.isNotEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.command_palette_commands),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        items(filteredCommands, key = { "command-${it.id}" }) { command ->
                            CommandPaletteItemRow(
                                item = command,
                                onClick = { onCommandExecute(command.id) }
                            )
                        }
                    }
                    
                    // 无结果
                    if (filteredSessions.isEmpty() && filteredCommands.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SearchOff,
                                        contentDescription = null,
                                        modifier = Modifier.size(48.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = stringResource(R.string.command_palette_no_results),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    // 自动聚焦
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

/**
 * 命令面板项
 */
sealed class CommandPaletteItem {
    abstract val id: String
    abstract val title: String
    abstract val icon: ImageVector
    
    data class SessionItem(
        override val id: String,
        override val title: String,
        override val icon: ImageVector = Icons.Default.Chat,
        val lastMessage: String? = null,
        val timestamp: Long? = null
    ) : CommandPaletteItem()
    
    data class CommandItem(
        override val id: String,
        override val title: String,
        override val icon: ImageVector = Icons.Default.Terminal,
        val description: String? = null
    ) : CommandPaletteItem()
}

/**
 * 命令面板项行
 */
@Composable
private fun CommandPaletteItemRow(
    item: CommandPaletteItem,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1
                )
                when (item) {
                    is CommandPaletteItem.SessionItem -> {
                        item.lastMessage?.let { msg ->
                            Text(
                                text = msg.take(50) + if (msg.length > 50) "..." else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                    is CommandPaletteItem.CommandItem -> {
                        item.description?.let { desc ->
                            Text(
                                text = desc,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * 默认命令列表
 */
fun getDefaultCommands(): List<CommandPaletteItem.CommandItem> = listOf(
    CommandPaletteItem.CommandItem(
        id = "new-session",
        title = "新建会话",
        icon = Icons.Default.Add,
        description = "创建新的对话会话"
    ),
    CommandPaletteItem.CommandItem(
        id = "settings",
        title = "设置",
        icon = Icons.Default.Settings,
        description = "打开应用设置"
    ),
    CommandPaletteItem.CommandItem(
        id = "clear-chat",
        title = "清除当前会话",
        icon = Icons.Default.DeleteSweep,
        description = "清除当前会话的所有消息"
    ),
    CommandPaletteItem.CommandItem(
        id = "export",
        title = "导出会话",
        icon = Icons.Default.Download,
        description = "导出当前会话为文件"
    ),
    CommandPaletteItem.CommandItem(
        id = "debug",
        title = "调试信息",
        icon = Icons.Default.BugReport,
        description = "查看连接状态和日志"
    )
)