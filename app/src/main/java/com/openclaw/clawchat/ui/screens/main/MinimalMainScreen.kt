package com.openclaw.clawchat.ui.screens.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.openclaw.clawchat.R
import com.openclaw.clawchat.ui.components.MinimalSessionItem
import com.openclaw.clawchat.ui.state.MainUiState
import com.openclaw.clawchat.ui.state.MainViewModel
import com.openclaw.clawchat.ui.theme.MinimalTokens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MinimalMainScreen(
    onNavigateToSession: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MainViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var isSearchMode by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier
            .windowInsetsPadding(WindowInsets.navigationBars),
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    viewModel.createSession()
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "New session"
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // Search bar
            if (isSearchMode) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = MinimalTokens.space4, vertical = MinimalTokens.space2),
                    placeholder = { Text("Search sessions...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    )
                )
            }

            // Main content
            if (uiState.isLoading && uiState.sessions.isEmpty()) {
                MinimalLoadingState()
            } else if (uiState.sessions.isEmpty()) {
                MinimalEmptyState(
                    onCreateSession = { viewModel.createSession() },
                    onEnterSearchMode = { isSearchMode = true }
                )
            } else {
                MinimalSessionList(
                    sessions = uiState,
                    onSessionClick = onNavigateToSession,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun MinimalSessionList(
    sessions: MainUiState,
    onSessionClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            horizontal = MinimalTokens.space4,
            vertical = MinimalTokens.space3
        ),
        verticalArrangement = Arrangement.spacedBy(MinimalTokens.space2)
    ) {
        items(
            items = sessions.sessions,
            key = { it.key }
        ) { session ->
            MinimalSessionItem(
                session = session,
                onClick = { onSessionClick(session.key) }
            )
        }
    }
}

@Composable
private fun MinimalLoadingState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Loading...",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MinimalEmptyState(
    onCreateSession: () -> Unit,
    onEnterSearchMode: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Large chat icon
            Icon(
                imageVector = Icons.Default.Chat,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            )

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "No conversations yet",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Start a new chat to get started",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            // Action buttons
            androidx.compose.material3.Button(
                onClick = onCreateSession,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("New Chat")
            }

            // Search link
            androidx.compose.material3.TextButton(onClick = onEnterSearchMode) {
                Text(
                    text = "Search history",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}