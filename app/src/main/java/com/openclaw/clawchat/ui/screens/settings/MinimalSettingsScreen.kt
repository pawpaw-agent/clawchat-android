package com.openclaw.clawchat.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.openclaw.clawchat.data.ThemeMode
import com.openclaw.clawchat.ui.theme.MinimalTokens

@Composable
fun MinimalSettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    val isDarkMode = state.themeMode == ThemeMode.DARK

    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .verticalScroll(scrollState)
            .padding(horizontal = MinimalTokens.space4)
            .padding(top = MinimalTokens.space4, bottom = MinimalTokens.space8),
        verticalArrangement = Arrangement.spacedBy(MinimalTokens.space3)
    ) {
        // Connection Section
        MinimalSettingsSection(title = "Connection") {
            MinimalSettingsItem(
                icon = Icons.Default.Link,
                title = "Gateway",
                subtitle = state.currentGateway?.host ?: "Not connected"
            )
        }

        // Appearance Section
        MinimalSettingsSection(title = "Appearance") {
            MinimalSettingsItem(
                icon = Icons.Default.DarkMode,
                title = "Dark Mode",
                trailing = {
                    Switch(
                        checked = isDarkMode,
                        onCheckedChange = { viewModel.setThemeMode(if (it) ThemeMode.DARK else ThemeMode.LIGHT) }
                    )
                }
            )
        }

        // About Section
        MinimalSettingsSection(title = "About") {
            MinimalSettingsItem(
                icon = Icons.Default.Info,
                title = "Version",
                subtitle = state.appVersion
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = MinimalTokens.space2))

        // Disconnect
        MinimalSettingsItem(
            icon = Icons.AutoMirrored.Filled.ExitToApp,
            title = "Disconnect",
            titleColor = MaterialTheme.colorScheme.error,
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
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = MinimalTokens.space1, start = MinimalTokens.space1)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(MinimalTokens.radiusMd))
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = MinimalTokens.space2),
            verticalArrangement = Arrangement.spacedBy(MinimalTokens.space1)
        ) {
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
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = MinimalTokens.space2, horizontal = MinimalTokens.space2),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = MinimalTokens.space2)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = titleColor
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (trailing != null) {
            trailing()
        }
    }
}