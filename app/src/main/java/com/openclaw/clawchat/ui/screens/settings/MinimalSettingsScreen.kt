package com.openclaw.clawchat.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
        verticalArrangement = Arrangement.spacedBy(MinimalTokens.space4)
    ) {
        // Connection Section
        MinimalSettingsSection(title = "Connection") {
            MinimalSettingsCard {
                MinimalSettingsItem(
                    icon = Icons.Default.Link,
                    title = "Gateway",
                    subtitle = state.currentGateway?.host ?: "Not connected"
                )
            }
        }

        // Appearance Section
        MinimalSettingsSection(title = "Appearance") {
            MinimalSettingsCard {
                MinimalSettingsItem(
                    icon = Icons.Default.DarkMode,
                    title = "Dark Mode",
                    trailing = {
                        Switch(
                            checked = isDarkMode,
                            onCheckedChange = { viewModel.setThemeMode(if (it) ThemeMode.DARK else ThemeMode.LIGHT) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.primary,
                                checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }
                )
            }
        }

        // Theme Color Section
        MinimalSettingsSection(title = "Theme") {
            MinimalSettingsCard {
                Column(
                    modifier = Modifier.padding(MinimalTokens.space3),
                    verticalArrangement = Arrangement.spacedBy(MinimalTokens.space2)
                ) {
                    Text(
                        text = "Accent Color",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ThemeColorOption(
                            color = Color(0xFFff5c5c),
                            label = "Coral",
                            isSelected = state.themeColorIndex == 0,
                            onClick = { viewModel.setThemeColor(0) }
                        )
                        ThemeColorOption(
                            color = Color(0xFF14b8a6),
                            label = "Teal",
                            isSelected = state.themeColorIndex == 1,
                            onClick = { viewModel.setThemeColor(1) }
                        )
                        ThemeColorOption(
                            color = Color(0xFF3b82f6),
                            label = "Blue",
                            isSelected = state.themeColorIndex == 2,
                            onClick = { viewModel.setThemeColor(2) }
                        )
                        ThemeColorOption(
                            color = Color(0xFFa855f7),
                            label = "Purple",
                            isSelected = state.themeColorIndex == 3,
                            onClick = { viewModel.setThemeColor(3) }
                        )
                    }
                }
            }
        }

        // About Section
        MinimalSettingsSection(title = "About") {
            MinimalSettingsCard {
                MinimalSettingsItem(
                    icon = Icons.Default.Info,
                    title = "Version",
                    subtitle = state.appVersion
                )
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(vertical = MinimalTokens.space2),
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        )

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
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.5.sp
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.padding(bottom = MinimalTokens.space1, start = MinimalTokens.space1)
        )
        content()
    }
}

@Composable
private fun MinimalSettingsCard(
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(MinimalTokens.radiusLg))
            .border(
                BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                RoundedCornerShape(MinimalTokens.radiusLg)
            )
            .background(MaterialTheme.colorScheme.surface)
            .padding(vertical = MinimalTokens.space1),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        content()
    }
}

@Composable
private fun ThemeColorOption(
    color: Color,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(color)
                .then(
                    if (isSelected) {
                        Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                    } else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onPrimary)
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = if (isSelected) color else MaterialTheme.colorScheme.onSurfaceVariant
        )
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