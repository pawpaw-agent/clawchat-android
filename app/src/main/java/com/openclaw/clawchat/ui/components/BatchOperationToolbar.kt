package com.openclaw.clawchat.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 批量操作 Toolbar
 * 显示在顶部，提供批量删除、复制等操作
 */
@Composable
fun BatchOperationToolbar(
    selectedCount: Int,
    onDelete: () -> Unit,
    onCopy: () -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer,
        tonalElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 已选择数量
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClearSelection) {
                    Icon(Icons.Default.Close, contentDescription = "取消选择")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "已选择 $selectedCount 条",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            
            // 操作按钮
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 全选
                IconButton(onClick = onSelectAll) {
                    Icon(
                        Icons.Default.SelectAll,
                        contentDescription = "全选",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                
                // 复制
                IconButton(onClick = onCopy) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = "复制",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                
                // 删除
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

/**
 * 选择模式状态
 */
data class SelectionState(
    val isSelectionMode: Boolean = false,
    val selectedIds: Set<String> = emptySet(),
    val totalCount: Int = 0
) {
    val selectedCount: Int = selectedIds.size
    val isAllSelected: Boolean = selectedIds.size == totalCount && totalCount > 0
    
    fun toggle(id: String): SelectionState {
        return if (selectedIds.contains(id)) {
            copy(selectedIds = selectedIds - id)
        } else {
            copy(selectedIds = selectedIds + id)
        }
    }
    
    fun selectAll(): SelectionState {
        return copy(selectedIds = (1..totalCount).map { it.toString() }.toSet())
    }
    
    fun clearSelection(): SelectionState {
        return copy(isSelectionMode = false, selectedIds = emptySet())
    }
    
    fun startSelection(total: Int): SelectionState {
        return copy(isSelectionMode = true, totalCount = total)
    }
}