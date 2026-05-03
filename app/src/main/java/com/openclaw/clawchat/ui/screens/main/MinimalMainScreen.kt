package com.openclaw.clawchat.ui.screens.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.openclaw.clawchat.ui.components.MinimalSessionItem
import com.openclaw.clawchat.ui.state.MainUiState
import com.openclaw.clawchat.ui.state.MainViewModel
import com.openclaw.clawchat.ui.theme.MinimalTokens

@Composable
fun MinimalMainScreen(
    onNavigateToSession: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MainViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

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
        if (uiState.isLoading && uiState.sessions.isEmpty()) {
            MinimalLoadingState(modifier = Modifier.padding(padding))
        } else if (uiState.sessions.isEmpty()) {
            MinimalEmptyState(modifier = Modifier.padding(padding))
        } else {
            MinimalSessionList(
                sessions = uiState,
                onSessionClick = onNavigateToSession,
                modifier = Modifier.padding(padding)
            )
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
            vertical = MinimalTokens.space4
        ),
        verticalArrangement = Arrangement.spacedBy(MinimalTokens.space2)
    ) {
        items(
            items = sessions.sessions,
            key = { it.id }
        ) { session ->
            MinimalSessionItem(
                session = session,
                onClick = { onSessionClick(session.id) }
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
private fun MinimalEmptyState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "No sessions",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Tap + to start a new conversation",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = MinimalTokens.space2)
            )
        }
    }
}