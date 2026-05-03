package com.openclaw.clawchat.ui.screens.session

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.openclaw.clawchat.ui.components.MinimalMessageBubble
import com.openclaw.clawchat.ui.state.MessageRole
import com.openclaw.clawchat.ui.state.MessageUi
import com.openclaw.clawchat.ui.state.SessionUi
import com.openclaw.clawchat.ui.state.SessionViewModel
import com.openclaw.clawchat.ui.theme.MinimalTokens

/**
 * OpenClaw v2026.4.29 Style Chat Session Screen
 *
 * Key patterns:
 * - Session thread selector row at top (horizontal scroll)
 * - Message grouping by role (user/assistant/tool)
 * - Role labels: "You" (user), "OpenClaw" (assistant), tool labels
 * - Streaming assistant text shown separately at top
 * - Thinking indicator + pending tool calls shown before messages
 * - Chat composer with thinking level selector
 */
@Composable
fun MinimalSessionScreen(
    sessionKey: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SessionViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val availableSessions = state.availableSessions

    LaunchedEffect(sessionKey) {
        viewModel.setSessionKey(sessionKey)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .windowInsetsPadding(WindowInsets.ime)
    ) {
        // Session thread selector (OpenClaw style)
        if (availableSessions.isNotEmpty()) {
            SessionThreadSelector(
                currentKey = sessionKey,
                sessions = availableSessions,
                onSelectSession = { viewModel.switchSession(it) }
            )
        }

        // Error rail
        if (!state.error.isNullOrBlank()) {
            ErrorRail(errorText = state.error!!)
        }

        // Message list (OpenClaw style: newest at bottom)
        MinimalMessageList(
            messages = state.chatMessages,
            streamingText = state.chatStream,
            pendingToolCount = state.pendingToolCalls.size,
            pendingRunCount = state.pendingRunCount,
            isLoading = state.isLoading,
            modifier = Modifier.weight(1f)
        )

        // Chat composer (OpenClaw style)
        ChatComposer(
            inputText = state.inputText,
            onInputChange = { viewModel.updateInputText(it) },
            thinkingLevel = state.thinkingLevel,
            onThinkingLevelChange = { viewModel.setThinkingLevel(it) },
            pendingRunCount = state.pendingRunCount,
            onSend = { viewModel.sendMessage(state.inputText) },
            onAbort = { viewModel.abortChat() },
            onRefresh = { viewModel.refreshMessages() },
            enabled = state.connectionStatus.isConnected,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Session thread selector row (OpenClaw v2026.4.29 style)
 */
@Composable
private fun SessionThreadSelector(
    currentKey: String,
    sessions: List<SessionUi>,
    onSelectSession: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = MinimalTokens.space3, vertical = MinimalTokens.space2),
        horizontalArrangement = Arrangement.spacedBy(MinimalTokens.space2)
    ) {
        sessions.take(5).forEach { session ->
            val isActive = session.key == currentKey
            Surface(
                onClick = { onSelectSession(session.key) },
                shape = RoundedCornerShape(14.dp),
                color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                border = BorderStroke(
                    1.dp,
                    if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                )
            ) {
                Text(
                    text = session.getDisplayName().take(12),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium
                    ),
                    color = if (isActive) Color.White else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * Error rail (OpenClaw v2026.4.29 style)
 */
@Composable
private fun ErrorRail(
    errorText: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = MinimalTokens.space3, vertical = MinimalTokens.space1),
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = "CHAT ERROR",
                style = MaterialTheme.typography.labelSmall.copy(
                    letterSpacing = 0.6.sp,
                    fontSize = 10.sp
                ),
                color = MaterialTheme.colorScheme.error
            )
            Text(
                text = errorText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

/**
 * Message list (OpenClaw v2026.4.29 style)
 *
 * Layout: newest at bottom (reverseLayout = false for LazyColumn)
 * Items at top: streaming bubble → pending tools → typing indicator
 */
@Composable
private fun MinimalMessageList(
    messages: List<MessageUi>,
    streamingText: String?,
    pendingToolCount: Int,
    pendingRunCount: Int,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    // Auto-scroll when new items arrive
    LaunchedEffect(messages.size, pendingRunCount, pendingToolCount) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // Scroll to top on streaming
    LaunchedEffect(streamingText) {
        if (!streamingText.isNullOrBlank()) {
            listState.animateScrollToItem(0)
        }
    }

    Box(modifier = modifier.fillMaxWidth()) {
        if (messages.isEmpty() && pendingRunCount == 0 && pendingToolCount == 0 && streamingText.isNullOrBlank()) {
            // Empty state
            MinimalSessionEmptyState(
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(
                    horizontal = MinimalTokens.space4,
                    vertical = MinimalTokens.space2
                ),
                verticalArrangement = Arrangement.spacedBy(MinimalTokens.space2)
            ) {
                // OpenClaw style: streaming text at top
                if (!streamingText.isNullOrBlank()) {
                    item(key = "stream") {
                        MinimalStreamingBubble(text = streamingText)
                    }
                }

                // Pending tools indicator
                if (pendingToolCount > 0) {
                    item(key = "tools") {
                        MinimalPendingToolsBubble(count = pendingToolCount)
                    }
                }

                // Thinking indicator
                if (pendingRunCount > 0) {
                    item(key = "typing") {
                        MinimalTypingIndicatorBubble()
                    }
                }

                // Message items
                items(
                    items = messages,
                    key = { it.id }
                ) { message ->
                    MinimalMessageBubble(
                        message = message,
                        showRoleLabel = true,
                        showTimestamp = true
                    )
                }
            }
        }
    }
}

/**
 * Streaming assistant bubble (OpenClaw v2026.4.29 style)
 */
@Composable
private fun MinimalStreamingBubble(
    text: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(0.90f),
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = "OpenClaw · Live",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.6.sp
                ),
                color = MaterialTheme.colorScheme.primary
            )
            // Streaming text with markdown-like handling
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

/**
 * Pending tools bubble (OpenClaw v2026.4.29 style)
 */
@Composable
private fun MinimalPendingToolsBubble(
    count: Int,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(0.85f),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Loading dots
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(3) { index ->
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .padding(0.dp)
                                .clip(RoundedCornerShape(50))
                                .padding(0.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Running tools...",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (count > 0) {
                Text(
                    text = "... +$count tool${if (count > 1) "s" else ""}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

/**
 * Typing indicator bubble (OpenClaw v2026.4.29 style)
 */
@Composable
private fun MinimalTypingIndicatorBubble(
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(0.85f),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Dot pulse animation (simplified - dots at different opacity)
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(RoundedCornerShape(50))
                    .padding(0.dp)
            )
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(RoundedCornerShape(50))
                    .padding(0.dp)
            )
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(RoundedCornerShape(50))
                    .padding(0.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Thinking...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Chat composer (OpenClaw v2026.4.29 style)
 *
 * Features:
 * - Thinking level selector (Off/Low/Medium/High)
 * - Attach button
 * - Refresh button
 * - Abort button (when pending)
 * - Send button with loading state
 */
@Composable
private fun ChatComposer(
    inputText: String,
    onInputChange: (String) -> Unit,
    thinkingLevel: String,
    onThinkingLevelChange: (String) -> Unit,
    pendingRunCount: Int,
    onSend: () -> Unit,
    onAbort: () -> Unit,
    onRefresh: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = MinimalTokens.elevationSm
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MinimalTokens.space3, vertical = MinimalTokens.space2),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Input field
            androidx.compose.material3.OutlinedTextField(
                value = inputText,
                onValueChange = onInputChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        text = if (enabled) "Type a message..." else "Connect gateway first",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                enabled = enabled,
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(14.dp),
                maxLines = 5,
                minLines = 2
            )

            // Action row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Thinking level selector
                MinimalThinkingSelector(
                    level = thinkingLevel,
                    onLevelChange = onThinkingLevelChange,
                    enabled = enabled
                )

                // Attach button
                MinimalIconButton(
                    icon = null, // Use attach icon
                    label = "Attach",
                    enabled = enabled,
                    onClick = { /* TODO: attachment picker */ }
                )

                // Refresh button
                MinimalIconButton(
                    icon = Icons.Default.Refresh,
                    label = null,
                    enabled = enabled,
                    onClick = onRefresh
                )

                // Abort button (when pending)
                if (pendingRunCount > 0) {
                    MinimalIconButton(
                        icon = Icons.Default.Stop,
                        label = "Abort",
                        enabled = true,
                        onClick = onAbort
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // Send button
                androidx.compose.material3.Button(
                    onClick = onSend,
                    enabled = enabled && (inputText.isNotBlank() || pendingRunCount > 0),
                    modifier = Modifier.height(44.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White,
                        disabledContainerColor = MaterialTheme.colorScheme.outline,
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    border = BorderStroke(
                        1.dp,
                        if (inputText.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                    )
                ) {
                    if (pendingRunCount > 0) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Send",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}

/**
 * Thinking level selector (OpenClaw v2026.4.29 style)
 */
@Composable
private fun MinimalThinkingSelector(
    level: String,
    onLevelChange: (String) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Surface(
        onClick = { if (enabled) expanded = true },
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = thinkingLabel(level),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Icon(
                imageVector = Icons.Default.Refresh, // arrow drop down
                contentDescription = "Select thinking level",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }

    androidx.compose.material3.DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false },
        shape = RoundedCornerShape(16.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        listOf("off", "low", "medium", "high").forEach { value ->
            androidx.compose.material3.DropdownMenuItem(
                text = {
                    Text(
                        text = thinkingLabel(value),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                onClick = {
                    onLevelChange(value)
                    expanded = false
                },
                trailingIcon = {
                    if (value == level.lowercase()) {
                        Text("✓", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            )
        }
    }
}

/**
 * Minimal icon button (OpenClaw v2026.4.29 style)
 */
@Composable
private fun MinimalIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    label: String?,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    androidx.compose.material3.Button(
        onClick = onClick,
        enabled = enabled,
        modifier = if (label == null) Modifier.size(44.dp) else Modifier.height(44.dp),
        shape = RoundedCornerShape(14.dp),
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        contentPadding = if (label == null) PaddingValues(0.dp) else androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp)
    ) {
        icon?.let {
            Icon(
                imageVector = it,
                contentDescription = label,
                modifier = Modifier.size(14.dp)
            )
            if (label != null) {
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } ?: run {
            // Attach icon placeholder
            Text("+", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/**
 * OpenClaw v2026.4.29 style empty state
 */
@Composable
private fun MinimalSessionEmptyState(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "No messages yet",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Send the first prompt to start this session",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun thinkingLabel(raw: String): String =
    when (raw.lowercase()) {
        "low" -> "Low"
        "medium" -> "Medium"
        "high" -> "High"
        else -> "Off"
    }