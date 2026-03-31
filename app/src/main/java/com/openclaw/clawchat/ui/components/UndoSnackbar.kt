package com.openclaw.clawchat.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * 撤销 Snackbar
 * 显示删除操作后提供撤销选项
 */
@Composable
fun UndoSnackbar(
    visible: Boolean,
    message: String = "已删除",
    undoText: String = "撤销",
    onUndo: () -> Unit,
    onDismiss: () -> Unit,
    durationMs: Long = 5000,  // 5秒后自动消失
    modifier: Modifier = Modifier
) {
    // 自动消失
    LaunchedEffect(visible) {
        if (visible) {
            delay(durationMs)
            onDismiss()
        }
    }
    
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(
            initialOffsetY = { it }
        ),
        exit = slideOutVertically(
            targetOffsetY = { it }
        ),
        modifier = modifier
    ) {
        Snackbar(
            modifier = Modifier.padding(16.dp),
            action = {
                TextButton(onClick = onUndo) {
                    Text(
                        text = undoText,
                        color = MaterialTheme.colorScheme.inversePrimary
                    )
                }
            }
        ) {
            Text(
                text = message,
                color = MaterialTheme.colorScheme.inverseSurface
            )
        }
    }
}

/**
 * 撤销队列管理器
 * 存储最近删除的项目，支持撤销操作
 */
class UndoQueue<T>(
    private val maxSize: Int = 10,
    private val expiryMs: Long = 5000  // 5秒过期
) {
    private val queue = mutableListOf<UndoItem<T>>()
    
    /**
     * 添加删除项目到队列
     */
    fun push(item: T, timestamp: Long = System.currentTimeMillis()) {
        // 移除过期项目
        queue.removeAll { System.currentTimeMillis() - it.timestamp > expiryMs }
        
        // 添加新项目
        queue.add(UndoItem(item, timestamp))
        
        // 保持队列大小限制
        if (queue.size > maxSize) {
            queue.removeAt(0)
        }
    }
    
    /**
     * 撤销最近删除的项目
     */
    fun pop(): T? {
        return if (queue.isNotEmpty()) {
            queue.removeAt(queue.size - 1).item
        } else {
            null
        }
    }
    
    /**
     * 获取最近删除的项目（不移除）
     */
    fun peek(): T? {
        return queue.lastOrNull()?.item
    }
    
    /**
     * 清空队列
     */
    fun clear() {
        queue.clear()
    }
    
    /**
     * 队列是否为空
     */
    fun isEmpty(): Boolean = queue.isEmpty()
    
    /**
     * 队列大小
     */
    fun size(): Int = queue.size
}

/**
 * 撤销项目
 */
data class UndoItem<T>(
    val item: T,
    val timestamp: Long
)

/**
 * 删除会话数据
 */
data class DeletedSession(
    val sessionId: String,
    val sessionName: String,
    val deletedAt: Long = System.currentTimeMillis()
)

/**
 * 删除消息数据
 */
data class DeletedMessage(
    val sessionId: String,
    val messageId: String,
    val content: String,
    val deletedAt: Long = System.currentTimeMillis()
)