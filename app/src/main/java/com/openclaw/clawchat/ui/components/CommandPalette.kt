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
import android.content.Context
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
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search)) },
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
    abstract val contentDescription: String?

    data class SessionItem(
        override val id: String,
        override val title: String,
        override val icon: ImageVector = Icons.Default.Chat,
        override val contentDescription: String? = null,
        val lastMessage: String? = null,
        val timestamp: Long? = null
    ) : CommandPaletteItem()

    data class CommandItem(
        override val id: String,
        override val title: String,
        override val icon: ImageVector = Icons.Default.Terminal,
        override val contentDescription: String? = null,
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
                contentDescription = item.contentDescription,
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
fun getDefaultCommands(context: Context): List<CommandPaletteItem.CommandItem> = listOf(
    CommandPaletteItem.CommandItem(
        id = "new-session",
        title = context.getString(R.string.session_new),
        icon = Icons.Default.Add,
        description = context.getString(R.string.command_new_session_desc)
    ),
    CommandPaletteItem.CommandItem(
        id = "settings",
        title = context.getString(R.string.settings_title),
        icon = Icons.Default.Settings,
        description = context.getString(R.string.command_settings_desc)
    ),
    CommandPaletteItem.CommandItem(
        id = "clear-chat",
        title = context.getString(R.string.command_clear_chat),
        icon = Icons.Default.DeleteSweep,
        description = context.getString(R.string.command_clear_chat_desc)
    ),
    CommandPaletteItem.CommandItem(
        id = "export",
        title = context.getString(R.string.command_export_session),
        icon = Icons.Default.Download,
        description = context.getString(R.string.command_export_session_desc)
    ),
    CommandPaletteItem.CommandItem(
        id = "debug",
        title = context.getString(R.string.command_debug_info),
        icon = Icons.Default.BugReport,
        description = context.getString(R.string.command_debug_info_desc)
    )
)