package com.openclaw.clawchat.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.openclaw.clawchat.R
import com.openclaw.clawchat.data.FontSize
import com.openclaw.clawchat.data.ThemeMode
import com.openclaw.clawchat.security.RootDetector
import com.openclaw.clawchat.ui.components.ColorUtils

/**
 * 设置区域容器
 */
@Composable
fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ) {
            Column(content = content)
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
    }
}

/**
 * 安全状态项
 */
@Composable
fun SecurityStatusItem(
    isRooted: Boolean,
    rootRiskLevel: RootDetector.RootCheckResult.RiskLevel
) {
    ListItem(
        headlineContent = { Text(stringResource(R.string.security_status_title)) },
        supportingContent = {
            Text(
                when {
                    isRooted && rootRiskLevel == RootDetector.RootCheckResult.RiskLevel.HIGH ->
                        stringResource(R.string.security_root_high)
                    isRooted && rootRiskLevel == RootDetector.RootCheckResult.RiskLevel.MEDIUM ->
                        stringResource(R.string.security_root_medium)
                    isRooted -> stringResource(R.string.security_root_low)
                    else -> stringResource(R.string.security_safe)
                }
            )
        },
        leadingContent = {
            Icon(
                imageVector = if (isRooted) Icons.Outlined.Warning else Icons.Outlined.VerifiedUser,
                contentDescription = null,
                tint = when (rootRiskLevel) {
                    RootDetector.RootCheckResult.RiskLevel.HIGH -> MaterialTheme.colorScheme.error
                    RootDetector.RootCheckResult.RiskLevel.MEDIUM -> MaterialTheme.colorScheme.tertiary
                    RootDetector.RootCheckResult.RiskLevel.LOW -> MaterialTheme.colorScheme.secondary
                    RootDetector.RootCheckResult.RiskLevel.NONE -> MaterialTheme.colorScheme.primary
                }
            )
        },
        trailingContent = {
            Surface(
                color = when (rootRiskLevel) {
                    RootDetector.RootCheckResult.RiskLevel.HIGH -> MaterialTheme.colorScheme.errorContainer
                    RootDetector.RootCheckResult.RiskLevel.MEDIUM -> MaterialTheme.colorScheme.tertiaryContainer
                    RootDetector.RootCheckResult.RiskLevel.LOW -> MaterialTheme.colorScheme.secondaryContainer
                    RootDetector.RootCheckResult.RiskLevel.NONE -> MaterialTheme.colorScheme.primaryContainer
                },
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = when (rootRiskLevel) {
                        RootDetector.RootCheckResult.RiskLevel.HIGH -> stringResource(R.string.security_risk_high)
                        RootDetector.RootCheckResult.RiskLevel.MEDIUM -> stringResource(R.string.security_risk_medium)
                        RootDetector.RootCheckResult.RiskLevel.LOW -> stringResource(R.string.security_risk_low)
                        RootDetector.RootCheckResult.RiskLevel.NONE -> stringResource(R.string.security_risk_none)
                    },
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = when (rootRiskLevel) {
                        RootDetector.RootCheckResult.RiskLevel.HIGH -> MaterialTheme.colorScheme.onErrorContainer
                        RootDetector.RootCheckResult.RiskLevel.MEDIUM -> MaterialTheme.colorScheme.onTertiaryContainer
                        RootDetector.RootCheckResult.RiskLevel.LOW -> MaterialTheme.colorScheme.onSecondaryContainer
                        RootDetector.RootCheckResult.RiskLevel.NONE -> MaterialTheme.colorScheme.onPrimaryContainer
                    }
                )
            }
        },
        modifier = Modifier.fillMaxWidth()
    )
}

/**
 * 开关设置项
 */
@Composable
fun ToggleSettingItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        },
        modifier = Modifier.fillMaxWidth()
    )
}

/**
 * 可点击设置项
 */
@Composable
fun ClickableSettingItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingContent = {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = stringResource(R.string.navigate_forward),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    )
}

/**
 * 字体大小设置项
 */
@Composable
fun FontSizeSettingItem(
    title: String,
    subtitle: String,
    currentSize: FontSize,
    onSizeChange: (FontSize) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = {
            Icon(
                imageVector = Icons.Outlined.TextFields,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingContent = {
            Text(
                text = currentSize.displayName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showDialog = true }
    )
    
    if (showDialog) {
        FontSizeDialog(
            title = title,
            currentSize = currentSize,
            onDismiss = { showDialog = false },
            onConfirm = { size ->
                onSizeChange(size)
                showDialog = false
            }
        )
    }
}

/**
 * 字体大小选择对话框
 */
@Composable
fun FontSizeDialog(
    title: String,
    currentSize: FontSize,
    onDismiss: () -> Unit,
    onConfirm: (FontSize) -> Unit
) {
    var selectedSize by remember { mutableStateOf(currentSize) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                FontSize.values().forEach { size ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedSize = size }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedSize == size,
                            onClick = { selectedSize = size }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = size.displayName,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.font_sample_text),
                            style = when (size) {
                                FontSize.SMALL -> MaterialTheme.typography.bodySmall
                                FontSize.MEDIUM -> MaterialTheme.typography.bodyMedium
                                FontSize.LARGE -> MaterialTheme.typography.bodyLarge
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selectedSize) }) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

/**
 * 主题模式设置项
 */
@Composable
fun ThemeModeSettingItem(
    title: String,
    subtitle: String,
    currentMode: ThemeMode,
    onModeChange: (ThemeMode) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = {
            Icon(
                imageVector = when (currentMode) {
                    ThemeMode.LIGHT -> Icons.Outlined.LightMode
                    ThemeMode.DARK -> Icons.Outlined.DarkMode
                    ThemeMode.SYSTEM -> Icons.Outlined.SettingsBrightness
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingContent = {
            Text(
                text = currentMode.displayName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showDialog = true }
    )

    if (showDialog) {
        ThemeModeDialog(
            title = title,
            currentMode = currentMode,
            onDismiss = { showDialog = false },
            onConfirm = { mode ->
                onModeChange(mode)
                showDialog = false
            }
        )
    }
}

/**
 * 主题模式选择对话框
 */
@Composable
fun ThemeModeDialog(
    title: String,
    currentMode: ThemeMode,
    onDismiss: () -> Unit,
    onConfirm: (ThemeMode) -> Unit
) {
    var selectedMode by remember { mutableStateOf(currentMode) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                ThemeMode.values().forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedMode = mode }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedMode == mode,
                            onClick = { selectedMode = mode }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = when (mode) {
                                ThemeMode.LIGHT -> Icons.Outlined.LightMode
                                ThemeMode.DARK -> Icons.Outlined.DarkMode
                                ThemeMode.SYSTEM -> Icons.Outlined.SettingsBrightness
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = mode.displayName,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selectedMode) }) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

/**
 * 主题色选择项
 */
@Composable
fun ThemeColorSettingItem(
    title: String,
    subtitle: String,
    currentColorIndex: Int,
    onColorChange: (Int) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = {
            Icon(
                imageVector = Icons.Outlined.Palette,
                contentDescription = null,
                tint = ColorUtils.PRESET_COLORS[currentColorIndex].primary
            )
        },
        trailingContent = {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .padding(4.dp)
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = ColorUtils.PRESET_COLORS[currentColorIndex].primary,
                    shape = MaterialTheme.shapes.small
                ) {}
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showDialog = true }
    )

    if (showDialog) {
        ThemeColorDialog(
            title = title,
            currentColorIndex = currentColorIndex,
            onDismiss = { showDialog = false },
            onConfirm = { index ->
                onColorChange(index)
                showDialog = false
            }
        )
    }
}

/**
 * 主题色选择对话框
 */
@Composable
fun ThemeColorDialog(
    title: String,
    currentColorIndex: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var selectedIndex by remember { mutableStateOf(currentColorIndex) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                ColorUtils.PRESET_COLORS.forEachIndexed { index, preset ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedIndex = index }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedIndex == index,
                            onClick = { selectedIndex = index }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            modifier = Modifier.size(32.dp),
                            color = preset.primary,
                            shape = MaterialTheme.shapes.small
                        ) {}
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = preset.name,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selectedIndex) }) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}