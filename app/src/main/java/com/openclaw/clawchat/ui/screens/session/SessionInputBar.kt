package com.openclaw.clawchat.ui.screens.session

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.openclaw.clawchat.ui.components.SLASH_COMMANDS
import com.openclaw.clawchat.ui.components.SlashCommandCategory
import com.openclaw.clawchat.ui.components.SlashCommandDef
import com.openclaw.clawchat.ui.components.getSlashCommandCompletions
import com.openclaw.clawchat.ui.state.AttachmentUi

/**
 * 消息输入框（支持附件和斜杠命令）
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MessageInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    enabled: Boolean,
    focusRequester: FocusRequester,
    attachments: List<AttachmentUi> = emptyList(),
    onAddAttachment: (AttachmentUi) -> Unit = {},
    onRemoveAttachment: (String) -> Unit = {},
    onExecuteCommand: (SlashCommandDef, String) -> Unit = { _, _ -> }
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    
    var slashMenuOpen by remember { mutableStateOf(false) }
    var slashMenuItems by remember { mutableStateOf<List<SlashCommandDef>>(emptyList()) }
    var slashMenuIndex by remember { mutableStateOf(0) }
    var slashMenuCommand by remember { mutableStateOf<SlashCommandDef?>(null) }
    var slashMenuArgItems by remember { mutableStateOf<List<String>>(emptyList()) }
    var slashMenuMode by remember { mutableStateOf("command") }
    
    LaunchedEffect(value) {
        val trimmed = value.trim()
        
        val argMatch = Regex("^/(\\S+)\\s+(.*)$").find(trimmed)
        if (argMatch != null) {
            val cmdName = argMatch.groupValues[1].lowercase()
            val argFilter = argMatch.groupValues[2].lowercase()
            val cmd = SLASH_COMMANDS.find { it.name == cmdName }
            val argOpts = cmd?.argOptions ?: emptyList()
            if (cmd != null && argOpts.isNotEmpty()) {
                val filtered = if (argFilter.isNotEmpty()) {
                    argOpts.filter { it.lowercase().startsWith(argFilter) }
                } else {
                    argOpts
                }
                if (filtered.isNotEmpty()) {
                    slashMenuMode = "args"
                    slashMenuCommand = cmd
                    slashMenuArgItems = filtered
                    slashMenuOpen = true
                    slashMenuIndex = 0
                    slashMenuItems = emptyList()
                    return@LaunchedEffect
                }
            }
            slashMenuOpen = false
            slashMenuMode = "command"
            slashMenuCommand = null
            slashMenuArgItems = emptyList()
            return@LaunchedEffect
        }
        
        val commandMatch = Regex("^/(\\S*)$").find(trimmed)
        if (commandMatch != null) {
            val filter = commandMatch.groupValues[1]
            val items = getSlashCommandCompletions(filter)
            slashMenuItems = items
            slashMenuOpen = items.isNotEmpty()
            slashMenuIndex = 0
            slashMenuMode = "command"
            slashMenuCommand = null
            slashMenuArgItems = emptyList()
        } else {
            slashMenuOpen = false
            slashMenuMode = "command"
            slashMenuCommand = null
            slashMenuArgItems = emptyList()
            slashMenuItems = emptyList()
        }
    }
    
    val imagePickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        uri?.let {
            onAddAttachment(createAttachmentFromUri(context, uri))
        }
    }
    
    Surface(
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            if (slashMenuOpen) {
                SlashCommandMenu(
                    items = if (slashMenuMode == "command") slashMenuItems else emptyList(),
                    argItems = if (slashMenuMode == "args") slashMenuArgItems else emptyList(),
                    command = slashMenuCommand,
                    selectedIndex = slashMenuIndex,
                    onSelect = { cmd ->
                        val argOpts = cmd.argOptions ?: emptyList()
                        if (argOpts.isNotEmpty()) {
                            onValueChange("/${cmd.name} ")
                        } else {
                            onValueChange("/${cmd.name}")
                            if (cmd.executeLocal) {
                                onExecuteCommand(cmd, "")
                            } else {
                                onSend()
                            }
                        }
                        slashMenuOpen = false
                    },
                    onSelectArg = { arg ->
                        val cmd = slashMenuCommand ?: return@SlashCommandMenu
                        onValueChange("/${cmd.name} $arg ")
                        slashMenuOpen = false
                    },
                    onDismiss = { slashMenuOpen = false }
                )
            }
            
            if (attachments.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    attachments.forEach { att ->
                        AttachmentPreview(
                            attachment = att,
                            onRemove = { onRemoveAttachment(att.id) }
                        )
                    }
                }
            }
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                IconButton(
                    onClick = { imagePickerLauncher.launch("image/*") },
                    enabled = enabled
                ) {
                    Icon(
                        imageVector = Icons.Default.AttachFile,
                        contentDescription = "添加附件",
                        tint = if (enabled) {
                            if (attachments.isNotEmpty()) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        }
                    )
                }
                
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    placeholder = { Text("输入消息...") },
                    enabled = enabled,
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { onSend() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        disabledBorderColor = Color.Transparent
                    )
                )

                IconButton(
                    onClick = onSend,
                    enabled = enabled && (value.isNotBlank() || attachments.isNotEmpty())
                ) {
                    Icon(
                        imageVector = if (value.isNotBlank() || attachments.isNotEmpty()) {
                            Icons.Default.Send
                        } else {
                            Icons.Default.NearMe
                        },
                        contentDescription = "发送",
                        tint = if (enabled && (value.isNotBlank() || attachments.isNotEmpty())) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }
    }
}

/**
 * 斜杠命令菜单
 */
@Composable
private fun SlashCommandMenu(
    items: List<SlashCommandDef>,
    argItems: List<String>,
    command: SlashCommandDef?,
    selectedIndex: Int,
    onSelect: (SlashCommandDef) -> Unit,
    onSelectArg: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        LazyColumn(
            modifier = Modifier.heightIn(max = 240.dp),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            if (items.isNotEmpty()) {
                items(items, key = { it.name }) { cmd ->
                    SlashCommandMenuItem(
                        command = cmd,
                        isSelected = items.indexOf(cmd) == selectedIndex,
                        onClick = { onSelect(cmd) }
                    )
                }
            }
            
            if (argItems.isNotEmpty() && command != null) {
                item {
                    Text(
                        text = command.description,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
                items(argItems, key = { it }) { arg ->
                    SlashCommandArgItem(
                        arg = arg,
                        isSelected = argItems.indexOf(arg) == selectedIndex,
                        onClick = { onSelectArg(arg) }
                    )
                }
            }
        }
    }
}

/**
 * 斜杠命令菜单项
 */
@Composable
private fun SlashCommandMenuItem(
    command: SlashCommandDef,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val categoryLabel = when (command.category) {
        SlashCommandCategory.SESSION -> "会话"
        SlashCommandCategory.MODEL -> "模型"
        SlashCommandCategory.AGENTS -> "Agents"
        SlashCommandCategory.TOOLS -> "工具"
    }
    
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
        } else {
            Color.Transparent
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "/${command.name}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(80.dp)
            )
            
            Text(
                text = command.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f)
            )
            
            Text(
                text = categoryLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 斜杠命令参数项
 */
@Composable
private fun SlashCommandArgItem(
    arg: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
        } else {
            Color.Transparent
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = arg,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}