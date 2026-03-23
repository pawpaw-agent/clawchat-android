package com.openclaw.clawchat.ui.components

import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.openclaw.clawchat.ui.theme.DesignTokens
import com.openclaw.clawchat.ui.state.AttachmentUi
import com.openclaw.clawchat.ui.components.SLASH_COMMANDS
import com.openclaw.clawchat.ui.components.SlashCommandCategory
import com.openclaw.clawchat.ui.components.SlashCommandDef
import com.openclaw.clawchat.ui.components.getSlashCommandCompletions

/**
 * 聊天输入栏
 * 对应 WebChat chat input area
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun ChatInputBar(
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
    // 斜杠命令菜单状态
    var slashMenuOpen by remember { mutableStateOf(false) }
    var slashMenuItems by remember { mutableStateOf<List<SlashCommandDef>>(emptyList()) }
    var slashMenuIndex by remember { mutableStateOf(0) }
    var slashMenuCommand by remember { mutableStateOf<SlashCommandDef?>(null) }
    var slashMenuArgItems by remember { mutableStateOf<List<String>>(emptyList()) }
    var slashMenuMode by remember { mutableStateOf("command") }
    
    // 更新斜杠菜单
    LaunchedEffect(value) {
        val trimmed = value.trim()
        
        // 参数模式: /command <partial-arg>
        val argMatch = Regex("^/(\\S+)\\s+(.*)$").find(trimmed)
        if (argMatch != null) {
            val cmdName = argMatch.groupValues[1].lowercase()
            val argFilter = argMatch.groupValues[2].lowercase()
            val cmd = SLASH_COMMANDS.find { it.name == cmdName }
            if (cmd != null && cmd.argOptions.isNotEmpty()) {
                val filtered = if (argFilter.isNotEmpty()) {
                    cmd.argOptions.filter { it.lowercase().startsWith(argFilter) }
                } else {
                    cmd.argOptions
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
        
        // 命令模式: /partial-command
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
    
    // 图片选择器
    val context = androidx.compose.ui.platform.LocalContext.current
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val mimeType = context.contentResolver.getType(uri) ?: "image/png"
                onAddAttachment(AttachmentUi(
                    id = "att-${System.currentTimeMillis()}-${(0..9999).random()}",
                    uri = uri,
                    mimeType = mimeType
                ))
            } catch (e: Exception) {
                android.util.Log.e("ChatInputBar", "Failed to read image: ${e.message}")
            }
        }
    }
    
    Surface(
        shadowElevation = 8.dp,
        color = DesignTokens.bgElevated
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 斜杠命令菜单
            if (slashMenuOpen) {
                SlashCommandMenu(
                    items = if (slashMenuMode == "command") slashMenuItems else emptyList(),
                    argItems = if (slashMenuMode == "args") slashMenuArgItems else emptyList(),
                    command = slashMenuCommand,
                    selectedIndex = slashMenuIndex,
                    onSelect = { cmd ->
                        if (cmd.argOptions.isNotEmpty()) {
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
            
            // 附件预览
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
            
            // 输入行
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                // 附件按钮
                IconButton(
                    onClick = { imagePickerLauncher.launch("image/*") },
                    enabled = enabled
                ) {
                    Icon(
                        imageVector = Icons.Default.AttachFile,
                        contentDescription = "添加附件",
                        tint = if (enabled) {
                            if (attachments.isNotEmpty()) DesignTokens.accent
                            else DesignTokens.muted
                        } else {
                            DesignTokens.muted.copy(alpha = 0.5f)
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
                    placeholder = { 
                        Text(
                            "输入消息...",
                            color = DesignTokens.muted
                        ) 
                    },
                    enabled = enabled,
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { onSend() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        disabledBorderColor = Color.Transparent,
                        focusedContainerColor = DesignTokens.bgHover,
                        unfocusedContainerColor = DesignTokens.bgAccent
                    ),
                    shape = RoundedCornerShape(DesignTokens.radiusLg)
                )
                
                // 发送按钮
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
                            DesignTokens.accent
                        } else {
                            DesignTokens.muted
                        }
                    )
                }
            }
        }
    }
}

/**
 * 附件预览
 */
@Composable
private fun AttachmentPreview(
    attachment: AttachmentUi,
    onRemove: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(DesignTokens.bgHover)
    ) {
        // 解码图片 - 优先从 dataUrl，否则从 uri
        val bitmap = remember(attachment.dataUrl, attachment.uri) {
            try {
                // 先尝试从 dataUrl 加载
                if (!attachment.dataUrl.isNullOrBlank()) {
                    val base64Match = Regex("base64,(.+)").find(attachment.dataUrl)
                    val base64 = base64Match?.groupValues?.get(1) ?: attachment.dataUrl
                    val bytes = Base64.decode(base64, Base64.DEFAULT)
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                } else {
                    // 从 uri 加载
                    val inputStream = context.contentResolver.openInputStream(attachment.uri)
                    val bytes = inputStream?.readBytes()
                    inputStream?.close()
                    if (bytes != null) {
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    } else {
                        null
                    }
                }
            } catch (e: Exception) {
                null
            }
        }
        
        if (bitmap != null) {
            androidx.compose.foundation.Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "附件预览",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Image,
                    contentDescription = null,
                    tint = DesignTokens.muted
                )
            }
        }
        
        // 删除按钮
        IconButton(
            onClick = onRemove,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(24.dp)
                .background(
                    color = DesignTokens.dangerSubtle,
                    shape = CircleShape
                )
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "移除",
                modifier = Modifier.size(14.dp),
                tint = DesignTokens.danger
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
            containerColor = DesignTokens.bgHover
        ),
        shape = RoundedCornerShape(DesignTokens.radiusMd)
    ) {
        LazyColumn(
            modifier = Modifier.heightIn(max = 240.dp),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            // 命令列表
            if (items.isNotEmpty()) {
                items(items, key = { it.name }) { cmd ->
                    SlashCommandMenuItem(
                        command = cmd,
                        isSelected = items.indexOf(cmd) == selectedIndex,
                        onClick = { onSelect(cmd) }
                    )
                }
            }
            
            // 参数列表
            if (argItems.isNotEmpty() && command != null) {
                item {
                    Text(
                        text = command.description,
                        style = MaterialTheme.typography.labelSmall,
                        color = DesignTokens.muted,
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
        color = if (isSelected) DesignTokens.accentSubtle else Color.Transparent
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
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                color = DesignTokens.accent,
                modifier = Modifier.width(80.dp)
            )
            
            Text(
                text = command.description,
                style = MaterialTheme.typography.bodySmall,
                color = DesignTokens.text,
                modifier = Modifier.weight(1f)
            )
            
            Text(
                text = categoryLabel,
                style = MaterialTheme.typography.labelSmall,
                color = DesignTokens.muted
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
        color = if (isSelected) DesignTokens.accentSubtle else Color.Transparent
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
                tint = DesignTokens.accent
            )
            Text(
                text = arg,
                style = MaterialTheme.typography.bodyMedium,
                color = DesignTokens.text
            )
        }
    }
}