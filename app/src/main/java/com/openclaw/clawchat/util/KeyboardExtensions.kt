package com.openclaw.clawchat.util

import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key

/**
 * 键盘快捷键扩展函数
 */

/**
 * 检查是否是新建会话快捷键 (Ctrl+N)
 */
fun KeyEvent.isNewSessionShortcut(): Boolean {
    return isCtrlPressed && key == Key.N
}

/**
 * 检查是否是搜索快捷键 (Ctrl+F)
 */
fun KeyEvent.isSearchShortcut(): Boolean {
    return isCtrlPressed && key == Key.F
}

/**
 * 检查是否是撤销快捷键 (Ctrl+Z)
 */
fun KeyEvent.isUndoShortcut(): Boolean {
    return isCtrlPressed && key == Key.Z
}

/**
 * 检查是否是保存草稿快捷键 (Ctrl+S)
 */
fun KeyEvent.isSaveDraftShortcut(): Boolean {
    return isCtrlPressed && key == Key.S
}