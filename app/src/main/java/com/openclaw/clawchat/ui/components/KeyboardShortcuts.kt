package com.openclaw.clawchat.ui.components

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.key

/**
 * 键盘快捷键工具类
 * 处理全局快捷键事件
 */
object KeyboardShortcuts {
    
    /**
     * 处理快捷键事件
     * 
     * @param event 键盘事件
     * @param onNewSession Ctrl+N: 新建会话
     * @param onSearch Ctrl+F: 打开搜索
     * @param onUndo Ctrl+Z: 撤销（可选）
     * @param onSaveDraft Ctrl+S: 保存草稿（可选）
     * @return 是否处理了事件
     */
    fun handleKeyEvent(
        event: KeyEvent,
        onNewSession: () -> Unit = {},
        onSearch: () -> Unit = {},
        onUndo: () -> Unit = {},
        onSaveDraft: () -> Unit = {}
    ): Boolean {
        // 只处理 KeyDown 事件
        if (event.type != KeyEventType.KeyDown) return false
        
        // Ctrl+N: 新建会话
        if (event.isCtrlPressed && event.key == Key.N) {
            onNewSession()
            return true
        }
        
        // Ctrl+F: 打开搜索
        if (event.isCtrlPressed && event.key == Key.F) {
            onSearch()
            return true
        }
        
        // Ctrl+Z: 撤销删除
        if (event.isCtrlPressed && event.key == Key.Z) {
            onUndo()
            return true
        }
        
        // Ctrl+S: 保存草稿
        if (event.isCtrlPressed && event.key == Key.S) {
            onSaveDraft()
            return true
        }
        
        return false
    }
    
    /**
     * 快捷键列表（用于提示对话框）
     */
    val SHORTCUT_LIST = listOf(
        ShortcutInfo("Ctrl+N", "新建会话"),
        ShortcutInfo("Ctrl+F", "打开搜索"),
        ShortcutInfo("Ctrl+Z", "撤销删除"),
        ShortcutInfo("Ctrl+S", "保存草稿"),
        ShortcutInfo("Enter", "发送消息"),
        ShortcutInfo("Shift+Enter", "换行")
    )
}

/**
 * 快捷键信息
 */
data class ShortcutInfo(
    val keyCombo: String,
    val description: String
)