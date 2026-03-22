package com.openclaw.clawchat.ui.screens.session

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openclaw.clawchat.data.FontSize
import com.openclaw.clawchat.ui.components.MarkdownText
import com.openclaw.clawchat.ui.state.*
import com.openclaw.clawchat.ui.theme.DesignTokens
import kotlinx.serialization.json.jsonPrimitive

/**
 * 消息内容卡片
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageContentCard(
    message: MessageUi,
    isUser: Boolean,
    isLastInGroup: Boolean,
    messageFontSize: FontSize = FontSize.MEDIUM,
    onCopy: (String) -> Unit = {},
    onDelete: () -> Unit = {},
    onRegenerate: () -> Unit = {}
) {
    val textContent = message.getTextContent()
    val images = message.content.filterIsInstance<MessageContentItem.Image>()
    
    if (textContent.isBlank() && images.isEmpty()) return
    
    val textSize = when (messageFontSize) {
        FontSize.SMALL -> 10.sp
        FontSize.MEDIUM -> 13.sp
        FontSize.LARGE -> 16.sp
    }
    
    var showMenu by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 渲染图片
        images.forEach { image ->
            MessageImageContent(image = image)
        }
        
        // 渲染文本
        if (textContent.isNotBlank()) {
            Box(
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .clip(RoundedCornerShape(DesignTokens.radiusLg))
                    .background(
                        if (isUser) DesignTokens.accentSubtle else DesignTokens.card
                    )
                    .combinedClickable(
                        onClick = {},
                        onLongClick = { showMenu = true }
                    )
                    .padding(
                        horizontal = DesignTokens.space3,
                        vertical = DesignTokens.space2
                    )
            ) {
                MarkdownText(
                    content = textContent,
                    modifier = Modifier.fillMaxWidth(),
                    fontSize = textSize,
                    textColor = if (isUser) DesignTokens.text else DesignTokens.text
                )
                
                if (showMenu) {
                    MessageActionDropdownMenu(
                        isUser = isUser,
                        onCopy = {
                            clipboardManager.setText(AnnotatedString(formatMessageAsMarkdown(message)))
                            showMenu = false
                        },
                        onDelete = {
                            onDelete()
                            showMenu = false
                        },
                        onRegenerate = {
                            onRegenerate()
                            showMenu = false
                        },
                        onDismiss = { showMenu = false }
                    )
                }
            }
        }
    }
}

/**
 * 消息操作下拉菜单
 */
@Composable
fun MessageActionDropdownMenu(
    isUser: Boolean,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    onRegenerate: () -> Unit,
    onDismiss: () -> Unit
) {
    DropdownMenu(
        expanded = true,
        onDismissRequest = onDismiss
    ) {
        DropdownMenuItem(
            text = { Text("复制") },
            onClick = onCopy
        )
        DropdownMenuItem(
            text = { Text("复制为 Markdown") },
            onClick = onCopy
        )
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text("删除", color = DesignTokens.danger) },
            onClick = onDelete
        )
        if (!isUser) {
            DropdownMenuItem(
                text = { Text("重新生成") },
                onClick = onRegenerate
            )
        }
    }
}

/**
 * 消息图片内容
 */
@Composable
fun MessageImageContent(image: MessageContentItem.Image) {
    val bitmap = remember(image.base64) {
        try {
            val base64Data = image.base64 ?: return@remember null
            val bytes = Base64.decode(base64Data, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            null
        }
    }
    
    Box(
        modifier = Modifier
            .widthIn(max = 280.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(DesignTokens.bgHover)
    ) {
        if (bitmap != null) {
            androidx.compose.foundation.Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "图片",
                modifier = Modifier.fillMaxWidth(),
                contentScale = ContentScale.FillWidth
            )
        }
    }
}

/**
 * 系统消息项
 */
@Composable
fun SystemMessageItem(message: MessageUi) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = DesignTokens.bgHover
        ),
        shape = RoundedCornerShape(DesignTokens.radiusMd)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = DesignTokens.accent
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = message.getTextContent(),
                style = MaterialTheme.typography.bodySmall,
                color = DesignTokens.muted
            )
        }
    }
}

/**
 * 工具详情卡片
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ToolDetailCard(toolCard: ToolCard) {
    var expanded by remember { mutableStateOf(false) }
    val hasContent = toolCard.args?.isNotBlank() == true || toolCard.result?.isNotBlank() == true
    
    val backgroundColor = when {
        toolCard.isError -> DesignTokens.dangerSubtle
        toolCard.kind == ToolCardKind.CALL -> Color(0x1AE53935)
        else -> DesignTokens.bgHover
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                enabled = hasContent,
                onClick = { expanded = !expanded }
            ),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        shape = RoundedCornerShape(DesignTokens.radiusMd)
    ) {
        Column(modifier = Modifier.padding(DesignTokens.space3)) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = when {
                        toolCard.isError -> Icons.Default.ErrorOutline
                        toolCard.kind == ToolCardKind.CALL -> Icons.Default.Bolt
                        else -> Icons.Default.CheckCircle
                    },
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = when {
                        toolCard.isError -> DesignTokens.danger
                        toolCard.kind == ToolCardKind.CALL -> DesignTokens.accent
                        else -> DesignTokens.accent2
                    }
                )
                Spacer(modifier = Modifier.width(DesignTokens.space2))
                Text(
                    text = toolCard.name.replaceFirstChar { it.uppercase() },
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.Bold,
                    color = DesignTokens.text
                )
                if (hasContent) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = DesignTokens.muted
                    )
                }
            }
            
            if (expanded && hasContent) {
                Spacer(modifier = Modifier.height(DesignTokens.space2))
                SelectionContainer {
                    Column {
                        toolCard.args?.takeIf { it.isNotBlank() }?.let { args ->
                            Text(
                                text = args,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                fontSize = DesignTokens.textXs,
                                color = DesignTokens.muted
                            )
                        }
                        toolCard.result?.takeIf { it.isNotBlank() }?.let { result ->
                            if (toolCard.args?.isNotBlank() == true) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = DesignTokens.space1),
                                    color = DesignTokens.border
                                )
                            }
                            Text(
                                text = result,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                fontSize = DesignTokens.textXs,
                                color = if (toolCard.isError) DesignTokens.danger else DesignTokens.text
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 配对工具卡片
 */
fun pairToolCards(message: MessageUi): List<ToolCard> {
    val calls = message.getToolCalls()
    val results = message.getToolResults()
    
    if (calls.isEmpty() && results.isEmpty()) {
        val textContent = message.getTextContent()
        return if (textContent.isNotBlank()) {
            listOf(ToolCard(
                kind = ToolCardKind.RESULT,
                name = "output",
                args = null,
                result = textContent,
                isError = false,
                callId = null
            ))
        } else {
            emptyList()
        }
    }
    
    return calls.map { call ->
        val matchingResult = results.find { it.toolCallId == call.id }
        val displayArgs = if (call.name == "exec" && call.args != null) {
            call.args?.get("command")?.jsonPrimitive?.content ?: call.args.toString()
        } else {
            call.args?.toString()
        }
        
        ToolCard(
            kind = if (matchingResult != null) ToolCardKind.RESULT else ToolCardKind.CALL,
            name = call.name,
            args = displayArgs,
            result = matchingResult?.text,
            isError = matchingResult?.isError ?: false,
            callId = call.id
        )
    }
}

/**
 * 工具标签行
 */
@Composable
fun ToolTagsRow(toolCards: List<ToolCard>) {
    var expandedIndex by remember { mutableStateOf(-1) }
    
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            toolCards.forEachIndexed { index, card ->
                ToolTag(
                    name = when (card.kind) {
                        ToolCardKind.CALL -> card.name
                        ToolCardKind.RESULT -> "output"
                    },
                    isError = card.isError,
                    isExpanded = expandedIndex == index,
                    onClick = { 
                        expandedIndex = if (expandedIndex == index) -1 else index 
                    }
                )
            }
        }
        
        if (expandedIndex >= 0 && expandedIndex < toolCards.size) {
            val card = toolCards[expandedIndex]
            ToolDetailCard(toolCard = card)
        }
    }
}

/**
 * 单个工具标签
 * 对应 WebChat chat-tool-tag
 */
@Composable
fun ToolTag(
    name: String,
    isError: Boolean,
    isExpanded: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(DesignTokens.radiusSm),
        color = if (isError) {
            DesignTokens.dangerSubtle
        } else {
            DesignTokens.bgHover
        },
        modifier = Modifier.height(DesignTokens.space6)  // 24dp
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = DesignTokens.space2,  // 8dp
                vertical = DesignTokens.space1     // 4dp
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.space1)
        ) {
            Icon(
                imageVector = if (isError) Icons.Default.ErrorOutline else Icons.Default.Terminal,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = if (isError) {
                    DesignTokens.danger
                } else {
                    DesignTokens.accent
                }
            )
            Text(
                text = name,
                style = MaterialTheme.typography.labelSmall,
                fontSize = DesignTokens.textSm,
                color = if (isError) {
                    DesignTokens.danger
                } else {
                    DesignTokens.text
                }
            )
            Icon(
                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = DesignTokens.muted
            )
        }
    }
}