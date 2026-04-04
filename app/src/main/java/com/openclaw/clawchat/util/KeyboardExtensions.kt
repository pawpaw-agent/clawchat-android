package com.openclaw.clawchat.util

import androidx.compose.ui.input.key.*

/**
 * 键盘快捷键扩展函数
 */

/**
 * 新建会话快捷键：Ctrl+N / Cmd+N
 */
fun KeyEvent.isNewSessionShortcut(): Boolean {
    return type == KeyEventType.KeyDown &&
            (key == Key.N) &&
            isMetaPressed
}

/**
 * 搜索快捷键：Ctrl+K / Cmd+K
 */
fun KeyEvent.isSearchShortcut(): Boolean {
    return type == KeyEventType.KeyDown &&
            (key == Key.K) &&
            isMetaPressed
}

/**
 * 撤销快捷键：Ctrl+Z / Cmd+Z
 */
fun KeyEvent.isUndoShortcut(): Boolean {
    return type == KeyEventType.KeyDown &&
            (key == Key.Z) &&
            isMetaPressed
}

/**
 * 保存草稿快捷键：Ctrl+S / Cmd+S
 */
fun KeyEvent.isSaveDraftShortcut(): Boolean {
    return type == KeyEventType.KeyDown &&
            (key == Key.S) &&
            isMetaPressed
}

/**
 * 全屏快捷键：F11
 */
fun KeyEvent.isFullscreenShortcut(): Boolean {
    return type == KeyEventType.KeyDown &&
            (key == Key.F11)
}

/**
 * 设置快捷键：Ctrl+, / Cmd+,
 */
fun KeyEvent.isSettingsShortcut(): Boolean {
    return type == KeyEventType.KeyDown &&
            (key == Key.Comma)
}

/**
 * 切换开发者工具：F12 或 Ctrl+Shift+I
 */
fun KeyEvent.isDevToolsShortcut(): Boolean {
    return type == KeyEventType.KeyDown &&
            ((key == Key.F12) ||
            (key == Key.I && isCtrlPressed && isShiftPressed))
}

/**
 * 历史记录后退：Alt+Left 或 Ctrl+[
 */
fun KeyEvent.isHistoryBackwardShortcut(): Boolean {
    return type == KeyEventType.KeyDown &&
            ((key == Key.DirectionLeft && isAltPressed) ||
            (key == Key.Backquote && isCtrlPressed)) // Using backquote as common alternative for [
}

/**
 * 历史记录前进：Alt+Right 或 Ctrl+]
 */
fun KeyEvent.isHistoryForwardShortcut(): Boolean {
    return type == KeyEventType.KeyDown &&
            ((key == Key.DirectionRight && isAltPressed) ||
            (key == Key.Minus && isCtrlPressed)) // Using minus as common alternative for ]
}