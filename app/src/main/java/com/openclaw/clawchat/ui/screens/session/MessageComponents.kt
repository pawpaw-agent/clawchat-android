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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageContentCard(
    message: MessageUi, isUser: Boolean, isLastInGroup: Boolean,
    messageFontSize: FontSize = FontSize.MEDIUM, onCopy: (String) -> Unit = {},
    onDelete: () -> Unit = {}, onRegenerate: () -> Unit = {}
) {
    val textContent = message.getTextContent()
    val images = message.content.filterIsInstance<MessageContentItem.Image>()
    if (textContent.isBlank() && images.isEmpty()) return
    
    val textSize = when (messageFontSize) { FontSize.SMALL -> 10.sp; FontSize.MEDIUM -> 13.sp; FontSize.LARGE -> 16.sp }
    var showMenu by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        images.forEach { MessageImageContent(it) }
        if (textContent.isNotBlank()) {
            Box(Modifier.widthIn(max = 320.dp).clip(RoundedCornerShape(16.dp))
                .background(if (isUser) DesignTokens.accentSubtle else DesignTokens.bgHover)
                .combinedClickable(onClick = {}, onLongClick = { showMenu = true }).padding(8.dp)) {
                MarkdownText(textContent, Modifier.fillMaxWidth(), textSize, if (isUser) DesignTokens.text else DesignTokens.text)
                if (showMenu) MessageActionDropdownMenu(isUser, { clipboardManager.setText(AnnotatedString(formatMessageAsMarkdown(message))); showMenu = false },
                    { onDelete(); showMenu = false }, { onRegenerate(); showMenu = false }, { showMenu = false })
            }
        }
    }
}

@Composable
fun MessageActionDropdownMenu(isUser: Boolean, onCopy: () -> Unit, onDelete: () -> Unit, onRegenerate: () -> Unit, onDismiss: () -> Unit) {
    DropdownMenu(true, onDismiss) {
        DropdownMenuItem({ Text("复制") }, onCopy)
        DropdownMenuItem({ Text("复制为 Markdown") }, onCopy)
        HorizontalDivider()
        DropdownMenuItem({ Text("删除", color = DesignTokens.danger) }, onDelete)
        if (!isUser) DropdownMenuItem({ Text("重新生成") }, onRegenerate)
    }
}

@Composable
fun MessageImageContent(image: MessageContentItem.Image) {
    val bitmap = remember(image.base64) {
        try { Base64.decode(image.base64 ?: return@remember null, Base64.DEFAULT).let { BitmapFactory.decodeByteArray(it, 0, it.size) } } catch (e: Exception) { null }
    }
    Box(Modifier.widthIn(max = 280.dp).clip(RoundedCornerShape(12.dp)).background(DesignTokens.bgHover)) {
        if (bitmap != null) androidx.compose.foundation.Image(bitmap.asImageBitmap(), "图片", Modifier.fillMaxWidth(), ContentScale.FillWidth)
    }
}

@Composable
fun SystemMessageItem(message: MessageUi) {
    Card(colors = CardDefaults.cardColors(DesignTokens.bgHover), shape = RoundedCornerShape(DesignTokens.radiusMd)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), Alignment.CenterVertically) {
            Icon(Icons.Default.Info, null, Modifier.size(16.dp), DesignTokens.accent)
            Spacer(Modifier.width(8.dp))
            Text(message.getTextContent(), MaterialTheme.typography.bodySmall, color = DesignTokens.muted)
        }
    }
}

@Composable
fun ToolDetailCard(toolCard: ToolCard) {
    var expanded by remember { mutableStateOf(false) }
    val hasContent = toolCard.args?.isNotBlank() == true || toolCard.result?.isNotBlank() == true
    Card(Modifier.fillMaxWidth().combinedClickable(hasContent) { expanded = !expanded },
        colors = CardDefaults.cardColors(when { toolCard.isError -> DesignTokens.dangerSubtle; toolCard.kind == ToolCardKind.CALL -> Color(0x1AE53935); else -> DesignTokens.bgHover }),
        shape = RoundedCornerShape(DesignTokens.radiusMd)) {
        Column(Modifier.padding(12.dp)) {
            Row(Alignment.CenterVertically) {
                Icon(when { toolCard.isError -> Icons.Default.ErrorOutline; toolCard.kind == ToolCardKind.CALL -> Icons.Default.Bolt; else -> Icons.Default.CheckCircle },
                    null, Modifier.size(16.dp), when { toolCard.isError -> DesignTokens.danger; toolCard.kind == ToolCardKind.CALL -> Color(0xFFE53935); else -> DesignTokens.accent2 })
                Spacer(Modifier.width(8.dp))
                Text(toolCard.name.replaceFirstChar { it.uppercase() }, Modifier.weight(1f), fontWeight = FontWeight.Bold)
                if (hasContent) Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, Modifier.size(16.dp))
            }
            if (expanded && hasContent) { Spacer(Modifier.height(8.dp)); SelectionContainer { Column {
                toolCard.args?.takeIf { it.isNotBlank() }?.let { Text(it, MaterialTheme.typography.bodySmall, FontFamily.Monospace, fontSize = 11.sp, color = DesignTokens.muted) }
                toolCard.result?.takeIf { it.isNotBlank() }?.let {
                    if (toolCard.args?.isNotBlank() == true) HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    Text(it, MaterialTheme.typography.bodySmall, FontFamily.Monospace, 11.sp, if (toolCard.isError) DesignTokens.danger else DesignTokens.text)
                }
            }}}
        }
    }
}

fun pairToolCards(message: MessageUi): List<ToolCard> {
    val calls = message.getToolCalls(); val results = message.getToolResults()
    if (calls.isEmpty() && results.isEmpty()) return message.getTextContent().takeIf { it.isNotBlank() }?.let { listOf(ToolCard(ToolCardKind.RESULT, "output", null, it, false, null)) } ?: emptyList()
    return calls.map { call ->
        val result = results.find { it.toolCallId == call.id }
        val args = if (call.name == "exec") call.args?.get("command")?.jsonPrimitive?.content ?: call.args?.toString() else call.args?.toString()
        ToolCard(if (result != null) ToolCardKind.RESULT else ToolCardKind.CALL, call.name, args, result?.text, result?.isError ?: false, call.id)
    }
}