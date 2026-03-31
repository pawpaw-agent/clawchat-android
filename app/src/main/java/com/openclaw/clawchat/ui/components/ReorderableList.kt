package com.openclaw.clawchat.ui.components

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 可重排列表工具类
 * 提供 LazyColumn 拖拽排序支持
 */
object ReorderableList {
    
    /**
     * 计算拖拽后的新位置
     */
    fun calculateNewPosition(
        listState: LazyListState,
        draggedItem: LazyListItemInfo,
        dragOffset: Float
    ): Int {
        val startOffset = draggedItem.offset + dragOffset
        val endOffset = startOffset + draggedItem.size
        
        val items = listState.layoutInfo.visibleItemsInfo
        
        // 找到重叠最多的项目
        var newIndex = draggedItem.index
        
        items.forEach { item ->
            if (item.index != draggedItem.index) {
                val itemStart = item.offset.toFloat()
                val itemEnd = itemStart + item.size
                
                // 检查是否重叠
                if (startOffset >= itemStart && startOffset <= itemEnd ||
                    endOffset >= itemStart && endOffset <= itemEnd) {
                    newIndex = if (dragOffset > 0) {
                        maxOf(newIndex, item.index)
                    } else {
                        minOf(newIndex, item.index)
                    }
                }
            }
        }
        
        return newIndex.coerceIn(0, listState.layoutInfo.totalItemsCount - 1)
    }
    
    /**
     * 拖拽修饰符
     */
    fun Modifier.dragReorderModifier(
        key: Any,
        isDragging: Boolean,
        onDragStart: (Any) -> Unit,
        onDragEnd: () -> Unit,
        onDragCancel: () -> Unit,
        onDrag: (Offset) -> Unit
    ): Modifier = this.pointerInput(key) {
        detectDragGesturesAfterLongPress(
            onDragStart = { onDragStart(key) },
            onDragEnd = onDragEnd,
            onDragCancel = onDragCancel,
            onDrag = { change, dragAmount ->
                change.consume()
                onDrag(dragAmount)
            }
        )
    }
    
    /**
     * 拖拽偏移计算
     */
    fun calculateDragOffset(
        draggedItem: LazyListItemInfo?,
        dragAmount: Offset,
        currentDragOffset: Float
    ): Float {
        if (draggedItem == null) return 0f
        
        val newOffset = currentDragOffset + dragAmount.y
        
        // 限制在可见范围内
        val maxOffset = draggedItem.size.toFloat()
        return newOffset.coerceIn(-maxOffset, maxOffset)
    }
    
    /**
     * 项目位置动画偏移
     */
    fun calculateItemOffset(
        itemIndex: Int,
        draggedIndex: Int?,
        draggedOffset: Float,
        draggedItemHeight: Int
    ): IntOffset {
        if (draggedIndex == null) return IntOffset.Zero
        
        return when {
            itemIndex < draggedIndex && itemIndex >= draggedIndex + (draggedOffset / draggedItemHeight).roundToInt() -> {
                IntOffset(0, draggedItemHeight)
            }
            itemIndex > draggedIndex && itemIndex <= draggedIndex + (draggedOffset / draggedItemHeight).roundToInt() -> {
                IntOffset(0, -draggedItemHeight)
            }
            else -> IntOffset.Zero
        }
    }
}

/**
 * 拖拽状态
 */
data class DragState(
    val isDragging: Boolean = false,
    val draggedKey: Any? = null,
    val draggedIndex: Int? = null,
    val dragOffset: Float = 0f,
    val draggedItemHeight: Int = 0
) {
    fun startDrag(key: Any, index: Int, height: Int): DragState {
        return copy(
            isDragging = true,
            draggedKey = key,
            draggedIndex = index,
            draggedItemHeight = height,
            dragOffset = 0f
        )
    }
    
    fun updateOffset(offset: Float): DragState {
        return copy(dragOffset = offset)
    }
    
    fun endDrag(): DragState {
        return copy(
            isDragging = false,
            draggedKey = null,
            draggedIndex = null,
            dragOffset = 0f
        )
    }
}

/**
 * 拖拽回调接口
 */
interface DragCallbacks {
    fun onDragStart(key: Any, index: Int)
    fun onDragEnd(fromIndex: Int, toIndex: Int)
    fun onDragCancel()
    fun onDrag(offset: Offset)
}