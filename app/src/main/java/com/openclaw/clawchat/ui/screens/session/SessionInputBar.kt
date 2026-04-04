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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.openclaw.clawchat.ui.components.SLASH_COMMANDS
import com.openclaw.clawchat.ui.components.SlashCommandDef
import com.openclaw.clawchat.ui.components.SlashMenuState
import com.openclaw.clawchat.ui.components.getSlashCommandCompletions
import com.openclaw.clawchat.ui.components.SlashCommandMenuItem
import com.openclaw.clawchat.ui.components.SlashCommandArgItem
import com.openclaw.clawchat.ui.state.AttachmentUi
import com.openclaw.clawchat.util.FileUtils
import java.util.*

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
    val context = LocalContext.current

    // 封装的斜杠菜单状态
    var slashMenu by remember { mutableStateOf(SlashMenuState()) }
    val haptic = LocalHapticFeedback.current

    // 根据输入更新菜单状态
    LaunchedEffect(value) {
        val trimmed = value.trim()

        // 匹配参数模式: /cmd arg...
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
                    slashMenu = SlashMenuState(
                        isOpen = true,
                        mode = "args",
                        command = cmd,
                        argItems = filtered
                    )
                    return@LaunchedEffect
                }
            }
            slashMenu = SlashMenuState()
            return@LaunchedEffect
        }

        // 匹配命令模式: /cmd
        val commandMatch = Regex("^/(\\S*)$").find(trimmed)
        if (commandMatch != null) {
            val filter = commandMatch.groupValues[1]
            val items = getSlashCommandCompletions(filter)
            slashMenu = SlashMenuState(
                isOpen = items.isNotEmpty(),
                mode = "command",
                items = items
            )
        } else {
            slashMenu = SlashMenuState()
        }
    }

    val imagePickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        uri?.let {
            onAddAttachment(com.openclaw.clawchat.ui.screens.session.createAttachmentFromUri(context, uri))
        }
    }

    Surface(
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (slashMenu.isOpen) {
                SlashCommandMenu(
                    items = if (slashMenu.mode == "command") slashMenu.items else emptyList(),
                    argItems = if (slashMenu.mode == "args") slashMenu.argItems else emptyList(),
                    command = slashMenu.command,
                    selectedIndex = slashMenu.selectedIndex,
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
                        slashMenu = SlashMenuState()
                    },
                    onSelectArg = { arg ->
                        val cmd = slashMenu.command ?: return@SlashCommandMenu
                        onValueChange("/${cmd.name} $arg ")
                        slashMenu = SlashMenuState()
                    },
                    onDismiss = { slashMenu = SlashMenuState() }
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
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSend()
                    },
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