package com.openclaw.clawchat.ui.screens.session

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.openclaw.clawchat.ui.components.AgentItem
import com.openclaw.clawchat.ui.components.ModelItem
import com.openclaw.clawchat.ui.state.ConnectionStatus

/**
 * 会话顶部导航栏
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionTopAppBar(
    connectionStatus: ConnectionStatus,
    onNavigateBack: () -> Unit,
    isSearchMode: Boolean = false,
    searchQuery: String = "",
    onSearchQueryChange: (String) -> Unit = {},
    onToggleSearch: () -> Unit = {},
    // 会话信息
    sessionLabel: String? = null,
    agentId: String? = null,
    currentModel: String? = null,
    // Model 切换
    models: List<ModelItem> = emptyList(),
    onModelChange: ((String) -> Unit)? = null,
    isLoadingModels: Boolean = false
) {
    var showModelMenu by remember { mutableStateOf(false) }
    var showSessionMenu by remember { mutableStateOf(false) }

    // 显示名称优先级：Agent ID > Model > 默认
    val displayName = when {
        !agentId.isNullOrBlank() -> formatAgentName(agentId)
        !currentModel.isNullOrBlank() -> formatModelName(currentModel)
        !sessionLabel.isNullOrBlank() -> sessionLabel
        else -> "会话"
    }

    // 是否可以切换 Model
    val canSwitchModel = onModelChange != null && models.isNotEmpty()

    if (isSearchMode) {
        // 搜索模式
        TopAppBar(
            title = {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("搜索消息...") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { }),
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchQueryChange("") }) {
                                Icon(Icons.Default.Close, "清除")
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )
            },
            navigationIcon = {
                IconButton(onClick = onToggleSearch) {
                    Icon(Icons.Default.ArrowBack, "关闭搜索")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
    } else {
        // 正常模式
        TopAppBar(
            title = {
                Column {
                    Text(
                        text = displayName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    // Model 副标题
                    if (!currentModel.isNullOrBlank() && agentId.isNullOrBlank()) {
                        Text(
                            text = formatModelName(currentModel),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
            },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "返回"
                    )
                }
            },
            actions = {
                // Model 切换按钮（如果有可用的 models）
                if (canSwitchModel) {
                    Box {
                        IconButton(onClick = { showModelMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "切换模型"
                            )
                        }

                        DropdownMenu(
                            expanded = showModelMenu,
                            onDismissRequest = { showModelMenu = false }
                        ) {
                            Text(
                                text = "切换模型",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )

                            if (isLoadingModels) {
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(16.dp),
                                                strokeWidth = 2.dp
                                            )
                                            Text("加载中...")
                                        }
                                    },
                                    onClick = { }
                                )
                            } else {
                                models.forEach { model ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(
                                                    text = model.name,
                                                    style = MaterialTheme.typography.bodyMedium
                                                )
                                                if (model.provider != null) {
                                                    Text(
                                                        text = model.provider,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = getModelIcon(model.id),
                                                contentDescription = null,
                                                tint = if (model.id == currentModel) {
                                                    MaterialTheme.colorScheme.primary
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                }
                                            )
                                        },
                                        trailingIcon = {
                                            if (model.id == currentModel) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        },
                                        onClick = {
                                            onModelChange(model.id)
                                            showModelMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // 搜索按钮
                IconButton(onClick = onToggleSearch) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "搜索消息"
                    )
                }

                // 连接状态
                ConnectionStatusIcon(connectionStatus)
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                titleContentColor = MaterialTheme.colorScheme.onSurface
            )
        )
    }
}

/**
 * 连接状态图标
 */
@Composable
private fun ConnectionStatusIcon(status: ConnectionStatus) {
    val (icon, color) = when (status) {
        is ConnectionStatus.Connected -> Icons.Default.CheckCircle to MaterialTheme.colorScheme.tertiary
        is ConnectionStatus.Connecting, is ConnectionStatus.Disconnecting -> Icons.Default.Sync to MaterialTheme.colorScheme.error
        is ConnectionStatus.Disconnected -> Icons.Default.CloudOff to MaterialTheme.colorScheme.onSurfaceVariant
        is ConnectionStatus.Error -> Icons.Default.Error to MaterialTheme.colorScheme.error
        else -> Icons.Default.Help to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Icon(
        imageVector = icon,
        contentDescription = "连接状态",
        tint = color
    )
}

/**
 * 获取模型图标
 */
private fun getModelIcon(modelId: String) = when {
    modelId.contains("claude", ignoreCase = true) -> Icons.Outlined.SmartToy
    modelId.contains("gpt", ignoreCase = true) -> Icons.Outlined.Psychology
    modelId.contains("gemini", ignoreCase = true) -> Icons.Outlined.AutoAwesome
    modelId.contains("llama", ignoreCase = true) -> Icons.Outlined.Pets
    else -> Icons.Outlined.Memory
}

/**
 * 格式化模型名称
 */
private fun formatModelName(model: String): String {
    return when {
        // Claude 模型
        model.contains("claude-3-5-sonnet", ignoreCase = true) -> "Claude 3.5 Sonnet"
        model.contains("claude-3-opus", ignoreCase = true) -> "Claude 3 Opus"
        model.contains("claude-3-sonnet", ignoreCase = true) -> "Claude 3 Sonnet"
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
        else -> model.takeBefore("-").ifEmpty { model.take(15) }
    }
}

/**
 * 格式化 Agent 名称
 */
private fun formatAgentName(agentId: String): String {
    // 从 agent session key 提取名称
    // 格式可能是 "agent:{agentId}:{sessionId}" 或直接是 agentId
    val name = agentId
        .removePrefix("agent:")
        .substringBefore(":")
        .ifEmpty { agentId }

    return name
        .replace("-", " ")
        .replace("_", " ")
        .split(" ")
        .joinToString(" ") { word ->
            word.lowercase().replaceFirstChar { it.uppercase() }
        }
}

/**
 * String 扩展：取分隔符前的内容
 */
private fun String.takeBefore(delimiter: String): String {
    val index = indexOf(delimiter)
    return if (index > 0) substring(0, index) else this
}