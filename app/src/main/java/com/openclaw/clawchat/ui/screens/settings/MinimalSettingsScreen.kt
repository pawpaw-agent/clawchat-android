package com.openclaw.clawchat.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.openclaw.clawchat.ui.components.minimal.MinimalCard
import com.openclaw.clawchat.ui.theme.MinimalTokens

@Composable
fun MinimalSettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(MinimalTokens.space4),
        verticalArrangement = Arrangement.spacedBy(MinimalTokens.space4)
    ) {
        // Connection Section
        MinimalSettingsSection(title = "Connection") {
            MinimalSettingsItem(
                icon = Icons.Default.Link,
                title = "Gateway",
                subtitle = state.gatewayUrl ?: "Not connected",
                onClick = { /* TODO */ }
            )
        }

        // Appearance Section
        MinimalSettingsSection(title = "Appearance") {
            MinimalSettingsItem(
                icon = Icons.Default.DarkMode,
                title = "Dark Mode",
                trailing = {
                    Switch(
                        checked = state.isDarkMode,
                        onCheckedChange = { viewModel.setDarkMode(it) }
                    )
                }
            )
        }

        // Notifications Section
        MinimalSettingsSection(title = "Notifications") {
            MinimalSettingsItem(
                icon = Icons.Default.Notifications,
                title = "Push Notifications",
                trailing = {
                    Switch(
                        checked = state.notificationsEnabled,
                        onCheckedChange = { viewModel.setNotifications(it) }
                    )
                }
            )
        }

        // About Section
        MinimalSettingsSection(title = "About") {
            MinimalSettingsItem(
                icon = Icons.Default.Info,
                title = "Version",
                subtitle = "1.0.0",
                onClick = { }
            )
        }

        // Disconnect
        MinimalSettingsItem(
            icon = Icons.AutoMirrored.Filled.ExitToApp,
            title = "Disconnect",
            onClick = { viewModel.disconnect() }
        )
    }
}

@Composable
private fun MinimalSettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = MinimalTokens.space2)
        )
        MinimalCard {
            content()
        }
    }
}

@Composable
private fun MinimalSettingsItem(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    subtitle: String? = null,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = MinimalTokens.space3),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = MinimalTokens.space3)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (trailing != null) {
            trailing()
        } else if (onClick != null) {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}