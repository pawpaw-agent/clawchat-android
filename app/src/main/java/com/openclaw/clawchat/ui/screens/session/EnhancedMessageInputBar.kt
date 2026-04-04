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
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.openclaw.clawchat.ui.components.*
import com.openclaw.clawchat.ui.state.AttachmentUi
import com.openclaw.clawchat.util.FileUtils
import com.openclaw.clawchat.util.isNewSessionShortcut
import com.openclaw.clawchat.util.isSearchShortcut
import com.openclaw.clawchat.util.isUndoShortcut
import com.openclaw.clawchat.util.isSaveDraftShortcut
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

    // 键盘快捷键处理
    var currentKeyEvent by remember { mutableStateOf<KeyEvent?>(null) }
    val scope = rememberCoroutineScope()

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

    // 处理键盘快捷键
    LaunchedEffect(currentKeyEvent) {
        currentKeyEvent?.let { event ->
            if (event.type == KeyEventType.KeyUp) {
                when {
                    event.isNewSessionShortcut() -> {
                        // 新建会话快捷键 - 执行相应命令
                        val newSessionCmd = SLASH_COMMANDS.find { it.name == "new" }
                        if (newSessionCmd != null) {
                            onExecuteCommand(newSessionCmd, "")
                        }
                    }
                    event.isSearchShortcut() -> {
                        // 搜索快捷键
                        val searchCmd = SLASH_COMMANDS.find { it.name == "search" }
                        if (searchCmd != null) {
                            onExecuteCommand(searchCmd, "")
                        }
                    }
                    event.isUndoShortcut() -> {
                        // 撤销快捷键
                        val undoCmd = SLASH_COMMANDS.find { it.name == "undo" }
                        if (undoCmd != null) {
                            onExecuteCommand(undoCmd, "")
                        }
                    }
                    event.isSaveDraftShortcut() -> {
                        // 保存草稿快捷键
                        val saveCmd = SLASH_COMMANDS.find { it.name == "save" }
                        if (saveCmd != null) {
                            onExecuteCommand(saveCmd, "")
                        }
                    }
                }
            }
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

            // 附件预览行
            if (attachments.isNotEmpty()) {
                AttachmentPreviews(
                    attachments = attachments,
                    onRemove = onRemoveAttachment,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
                    .onPreviewKeyEvent { event ->
                        currentKeyEvent = event
                        false
                    },
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                // 附加功能按钮
                IconButton(
                    onClick = {
                        imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
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

                // 输入框
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    placeholder = { Text("输入消息或使用 / 命令...") },
                    enabled = enabled,
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = { onSend() },
                        onDone = { focusRequester.freeFocus() }
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        disabledBorderColor = Color.Transparent
                    ),
                    supportingText = {
                        // 显示字符计数
                        val charCount = value.length
                        val warningThreshold = 2000 // 警告阈值
                        val isWarning = charCount > warningThreshold

                        Text(
                            text = "$charCount",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isWarning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )

                // 发送按钮
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
 * 附件预览组件
 */
@Composable
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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier.heightIn(max = 240.dp),
            contentPadding = PaddingValues(vertical = 4.dp)
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
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
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