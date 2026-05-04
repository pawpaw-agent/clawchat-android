package com.openclaw.clawchat.ui.screens.session

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.openclaw.clawchat.R
import com.openclaw.clawchat.ui.components.*
import com.openclaw.clawchat.ui.state.AttachmentUi
import com.openclaw.clawchat.util.FileUtils
import com.openclaw.clawchat.ui.theme.MinimalTokens
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import java.util.*


/**
 * 增强型消息输入栏 - 实现完整的 webchat 功能对等
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun EnhancedMessageInputBar(
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
    val haptic = LocalHapticFeedback.current

    // 斜杠菜单状态
    var slashMenu by remember { mutableStateOf(SlashMenuState()) }

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

    // 图片选择器
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri: android.net.Uri? ->
            uri?.let { selectedUri ->
                val fileName = FileUtils.getFileNameFromUri(context, selectedUri) ?: "image_${System.currentTimeMillis()}.jpg"
                val mimeType = context.contentResolver.getType(selectedUri) ?: "image/jpeg"

                // 将 URI 转换为 base64
                val base64String = FileUtils.uriToBase64(context, selectedUri)

                if (base64String != null) {
                    val attachment = AttachmentUi(
                        id = UUID.randomUUID().toString(),
                        uri = selectedUri,  // 传入 Uri 而不是 size
                        mimeType = mimeType,
                        fileName = fileName,
                        dataUrl = "data:$mimeType;base64,$base64String"
                    )
                    onAddAttachment(attachment)
                }
            }
        }
    )

    // 文件选择器（支持所有文件类型）
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri: android.net.Uri? ->
            uri?.let { selectedUri ->
                val fileName = FileUtils.getFileNameFromUri(context, selectedUri) ?: "file_${System.currentTimeMillis()}"
                val mimeType = context.contentResolver.getType(selectedUri) ?: "application/octet-stream"

                // 将 URI 转换为 base64
                val base64String = FileUtils.uriToBase64(context, selectedUri)

                if (base64String != null) {
                    val attachment = AttachmentUi(
                        id = UUID.randomUUID().toString(),
                        uri = selectedUri,
                        mimeType = mimeType,
                        fileName = fileName,
                        dataUrl = "data:$mimeType;base64,$base64String"
                    )
                    onAddAttachment(attachment)
                }
            }
        }
    )

    // 附件菜单状态
    var showAttachmentMenu by remember { mutableStateOf(false) }

    // 输入历史记录（参考 Web UI 的 input history 功能）
    var inputHistory by remember { mutableStateOf<List<String>>(emptyList()) }
    var historyIndex by remember { mutableIntStateOf(-1) }
    var savedDraft by remember { mutableStateOf("") }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(MinimalTokens.radiusMd),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
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

            // 附件预览行
            if (attachments.isNotEmpty()) {
                AttachmentPreviews(
                    attachments = attachments,
                    onRemove = onRemoveAttachment,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = MinimalTokens.space2, vertical = MinimalTokens.space1)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(MinimalTokens.space2),
                horizontalArrangement = Arrangement.spacedBy(MinimalTokens.space1),
                verticalAlignment = Alignment.Bottom
            ) {
                // 附加功能按钮（显示菜单）
                Box {
                    IconButton(
                        onClick = { showAttachmentMenu = true },
                        enabled = enabled
                    ) {
                        Icon(
                            imageVector = Icons.Default.AttachFile,
                            contentDescription = stringResource(R.string.action_add_attachment),
                            tint = if (enabled) {
                                if (attachments.isNotEmpty()) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            }
                        )
                    }

                    // 附件类型菜单
                    DropdownMenu(
                        expanded = showAttachmentMenu,
                        onDismissRequest = { showAttachmentMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_attach_image)) },
                            onClick = {
                                showAttachmentMenu = false
                                imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_attach_file)) },
                            onClick = {
                                showAttachmentMenu = false
                                filePicker.launch(arrayOf("*/*"))
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        )
                    }
                }

                // 输入框和 token 估算
                Column(modifier = Modifier.weight(1f)) {
                    OutlinedTextField(
                        value = value,
                        onValueChange = { newText ->
                            onValueChange(newText)
                            // 用户开始编辑时清除历史记录位置
                            if (historyIndex != -1 && newText != savedDraft) {
                                historyIndex = -1
                                savedDraft = ""
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                            .onKeyEvent { keyEvent ->
                                if (keyEvent.key == Key.DirectionUp &&
                                    keyEvent.type == KeyEventType.KeyDown) {
                                    // 上箭头：浏览历史
                                    if (inputHistory.isNotEmpty()) {
                                        if (historyIndex == -1) {
                                            // 第一次按上箭头：保存当前草稿
                                            savedDraft = value
                                            historyIndex = 0
                                        } else if (historyIndex < inputHistory.size - 1) {
                                            historyIndex++
                                        }
                                        if (historyIndex >= 0 && historyIndex < inputHistory.size) {
                                            onValueChange(inputHistory[historyIndex])
                                        }
                                        true // 有历史记录时消费事件
                                    } else {
                                        false // 无历史记录时不消费，交由系统处理（如光标移动）
                                    }
                                } else if (keyEvent.key == Key.DirectionDown &&
                                    keyEvent.type == KeyEventType.KeyDown) {
                                    // 下箭头：返回当前输入
                                    if (historyIndex != -1) {
                                        if (historyIndex > 0) {
                                            historyIndex--
                                            onValueChange(inputHistory[historyIndex])
                                        } else {
                                            historyIndex = -1
                                            onValueChange(savedDraft)
                                        }
                                        true
                                    } else {
                                        false
                                    }
                                } else {
                                    false
                                }
                            }
                            .heightIn(min = 48.dp, max = 120.dp),
                        placeholder = { Text(stringResource(R.string.input_placeholder)) },
                        enabled = enabled,
                        maxLines = 6,
                        minLines = 1,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(
                            onSend = { onSend() },
                            onDone = { focusRequester.freeFocus() }
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            disabledBorderColor = Color.Transparent
                        )
                    )

                    // Token 估算显示（参考 webchat tokenEstimate）
                    if (value.length >= 100) {
                        val tokenEstimate = "~${(value.length / 4) + 1} tokens"
                        Text(
                            text = tokenEstimate,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.padding(start = 16.dp, top = 2.dp)
                        )
                    }
                }

                // 发送按钮
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        // 保存输入到历史
                        if (value.isNotBlank()) {
                            inputHistory = (listOf(value) + inputHistory).take(50)
                        }
                        historyIndex = -1
                        savedDraft = ""
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
                        contentDescription = stringResource(R.string.action_send),
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
 * 附件预览组件
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun AttachmentPreviews(
    attachments: List<AttachmentUi>,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        attachments.forEach { attachment ->
            // 使用共享的 AttachmentPreview 组件
            com.openclaw.clawchat.ui.screens.session.AttachmentPreview(
                attachment = attachment,
                onRemove = { onRemove(attachment.id) }
            )
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
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MinimalTokens.space2, vertical = MinimalTokens.space1),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
        shape = RoundedCornerShape(MinimalTokens.radiusMd),
        shadowElevation = MinimalTokens.elevationSm
    ) {
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier.heightIn(max = 240.dp),
            contentPadding = PaddingValues(vertical = MinimalTokens.space1)
        ) {
            if (items.isNotEmpty()) {
                items(items.size) { index ->
                    val cmd = items[index]
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
                        modifier = Modifier.padding(
                            horizontal = MinimalTokens.space3,
                            vertical = MinimalTokens.space1
                        )
                    )
                }
                items(argItems.size) { index ->
                    val arg = argItems[index]
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