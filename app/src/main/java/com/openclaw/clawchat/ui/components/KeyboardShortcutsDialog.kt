package com.openclaw.clawchat.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.openclaw.clawchat.R
import com.openclaw.clawchat.ui.theme.DesignTokens

/**
 * 键盘快捷键项
 */
data class KeyboardShortcut(
    val keys: String,
    val descriptionResId: Int
)

/**
 * 可用快捷键列表
 */
val KEYBOARD_SHORTCUTS = listOf(
    KeyboardShortcut("Ctrl + N", R.string.keyboard_shortcut_new_session),
    KeyboardShortcut("Ctrl + F", R.string.keyboard_shortcut_search),
    KeyboardShortcut("Ctrl + Z", R.string.keyboard_shortcut_undo),
    KeyboardShortcut("Ctrl + S", R.string.keyboard_shortcut_save_draft),
    KeyboardShortcut("Enter", R.string.keyboard_shortcut_send),
    KeyboardShortcut("Shift + Enter", R.string.keyboard_shortcut_newline)
)

/**
 * 键盘快捷键帮助对话框
 */
@Composable
fun KeyboardShortcutsDialog(
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.keyboard_shortcuts_title)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(DesignTokens.space2)
            ) {
                KEYBOARD_SHORTCUTS.forEach { shortcut ->
                    ShortcutRow(shortcut = shortcut)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.ok))
            }
        }
    )
}

/**
 * 快捷键行
 */
@Composable
private fun ShortcutRow(
    shortcut: KeyboardShortcut
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(DesignTokens.radiusSm)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = DesignTokens.space3, vertical = DesignTokens.space2),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(shortcut.descriptionResId),
                style = MaterialTheme.typography.bodyMedium
            )

            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(DesignTokens.radiusSm)
            ) {
                Text(
                    text = shortcut.keys,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = DesignTokens.space2, vertical = DesignTokens.space1)
                )
            }
        }
    }
}