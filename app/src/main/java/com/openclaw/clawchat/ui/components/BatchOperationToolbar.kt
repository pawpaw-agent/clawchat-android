package com.openclaw.clawchat.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.openclaw.clawchat.ui.state.MessageUi

/**
 * 批量操作工具栏 - 实现 webchat 风格的多选操作
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchOperationToolbar(
    selectedItems: List<String>, // 选中的消息ID列表
    onDeselectAll: () -> Unit,
    onDeleteSelected: () -> Unit,
    onCopySelected: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = selectedItems.isNotEmpty(),
        enter = fadeIn(animationSpec = tween(200)),
        exit = fadeOut(animationSpec = tween(200))
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
            color = MaterialTheme.colorScheme.primary,
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 选中项数量
                Text(
                    text = "${selectedItems.size} 项已选择",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimary
                )

                // 操作按钮
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 复制按钮
                    IconButton(
                        onClick = onCopySelected,
                        enabled = selectedItems.isNotEmpty()
                    ) {
                        Icon(
                            imageVector = Icons.Default.CopyAll,
                            contentDescription = "复制选中项",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }

                    // 删除按钮
                    IconButton(
                        onClick = onDeleteSelected,
                        enabled = selectedItems.isNotEmpty()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "删除选中项",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }

                    // 全部取消选择
                    IconButton(
                        onClick = onDeselectAll
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "取消选择",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }
    }
}

/**
 * 选择状态管理器
 */
@Composable
fun rememberSelectionState(): SelectionState {
    val selectionState = remember { SelectionState() }
    return selectionState
}

class SelectionState {
    var selectedIds by mutableStateOf(setOf<String>())
        private set

    val isSelected: (String) -> Boolean = { id -> selectedIds.contains(id) }
    val selectionCount: Int get() = selectedIds.size
    val isEmpty: Boolean get() = selectedIds.isEmpty()
    val isNotEmpty: Boolean get() = selectedIds.isNotEmpty()

    fun toggleSelection(id: String) {
        selectedIds = if (selectedIds.contains(id)) {
            selectedIds - id
        } else {
            selectedIds + id
        }
    }

    fun select(id: String) {
        selectedIds = selectedIds + id
    }

    fun deselect(id: String) {
        selectedIds = selectedIds - id
    }

    fun selectAll(ids: List<String>) {
        selectedIds = selectedIds + ids
    }

    fun clearSelection() {
        selectedIds = emptySet()
    }

    fun addAll(ids: Collection<String>) {
        selectedIds = selectedIds + ids
    }
}