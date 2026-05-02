package com.openclaw.clawchat.ui.screens.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.openclaw.clawchat.ui.components.MinimalInputBar
import com.openclaw.clawchat.ui.components.MinimalMessageBubble
import com.openclaw.clawchat.ui.state.SessionUiState
import com.openclaw.clawchat.ui.state.SessionViewModel
import com.openclaw.clawchat.ui.theme.MinimalTokens

@Composable
fun MinimalSessionScreen(
    sessionId: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SessionViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(sessionId) {
        viewModel.loadSession(sessionId)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(bottom = MinimalTokens.inputBarHeight)
    ) {
        if (state.isLoading && state.messageGroups.isEmpty()) {
            MinimalSessionLoadingState()
        } else if (state.messageGroups.isEmpty()) {
            MinimalSessionEmptyState()
        } else {
            MinimalMessageList(
                messageGroups = state.messageGroups,
                modifier = Modifier.weight(1f)
            )
        }

        MinimalInputBar(
            value = state.inputText,
            onValueChange = { viewModel.updateInputText(it) },
            onSend = { viewModel.sendMessage() },
            enabled = state.isConnected && !state.isSending,
            placeholder = "Type a message..."
        )
    }
}

@Composable
private fun MinimalMessageList(
    messageGroups: List<com.openclaw.clawchat.ui.state.MessageGroup>,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        state = listState,
        contentPadding = PaddingValues(
            horizontal = MinimalTokens.space4,
            vertical = MinimalTokens.space4
        ),
        verticalArrangement = Arrangement.spacedBy(MinimalTokens.space2)
    ) {
        items(
            items = messageGroups,
            key = { it.timestamp }
        ) { group ->
            group.messages.forEach { message ->
                MinimalMessageBubble(
                    message = message,
                    showTimestamp = true
                )
            }
        }
    }

    LaunchedEffect(messageGroups.size) {
        if (messageGroups.isNotEmpty()) {
            listState.animateScrollToItem(messageGroups.size - 1)
        }
    }
}

@Composable
private fun MinimalSessionLoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Loading messages...",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MinimalSessionEmptyState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "No messages yet",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Start the conversation",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = MinimalTokens.space2)
            )
        }
    }
}