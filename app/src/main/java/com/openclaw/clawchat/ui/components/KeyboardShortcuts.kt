package com.openclaw.clawchat.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.input.key.*

/**
 * 键盘快捷键组件 - 实现 webchat 的键盘快捷键功能
 *
 * 支持以下快捷键：
 * - Ctrl+N: 新建会话
 * - Ctrl+F: 搜索
 * - Ctrl+Z: 撤销删除
 * - Ctrl+S: 保存草稿
 */
@Composable
fun KeyboardShortcuts(
    onNewSession: () -> Unit = {},
    onToggleSearch: () -> Unit = {},
    onUndo: () -> Unit = {},
    onSaveDraft: () -> Unit = {}
) {
    // 这个组件只是用于演示目的，实际的键盘处理会在各个组件中实现
    Column {
        Text(
            text = "键盘快捷键",
            style = MaterialTheme.typography.headlineSmall
        )
    }
}

/**
 * 检查键盘事件是否匹配快捷键组合
 */
fun KeyEvent.isNewSessionShortcut(): Boolean {
    return isCtrlPressed && code == Key.N && type == KeyEventType.KeyUp
}

fun KeyEvent.isSearchShortcut(): Boolean {
    return isCtrlPressed && code == Key.F && type == KeyEventType.KeyUp
}

fun KeyEvent.isUndoShortcut(): Boolean {
    return isCtrlPressed && code == Key.Z && type == KeyEventType.KeyUp
}

fun KeyEvent.isSaveDraftShortcut(): Boolean {
    return isCtrlPressed && code == Key.S && type == KeyEventType.KeyUp
}