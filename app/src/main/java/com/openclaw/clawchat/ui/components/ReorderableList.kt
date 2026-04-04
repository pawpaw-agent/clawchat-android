package com.openclaw.clawchat.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 可重排序列表 - 实现类似 webchat 的拖拽排序功能
 */
@Composable
fun <T> ReorderableList(
    items: List<T>,
    onMove: (fromIndex: Int, toIndex: Int) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (LazyItemScope.(item: T, index: Int, isDragging: Boolean) -> Unit)
) {
    val state = rememberReorderState<T>(items, onMove)

    androidx.compose.foundation.lazy.LazyColumn(
        state = state.lazyListState,
        modifier = modifier
    ) {
        items(items, key = { it.hashCode() }) { item ->
            val index = items.indexOf(item)
            val isDragging = state.draggingIndex == index

            content(this, item, index, isDragging)

            // 拖拽指示器
            if (isDragging) {
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

/**
 * 重排序状态管理器
 */
@Composable
fun <T> rememberReorderState(
    items: List<T>,
    onMove: (fromIndex: Int, toIndex: Int) -> Unit
): ReorderState<T> {
    val lazyListState = rememberLazyListState()
    val draggingIndex = remember { mutableIntStateOf(-1) }
    val offset = remember { mutableFloatStateOf(0f) }

    return remember(items) {
        ReorderState(
            items = items,
            lazyListState = lazyListState,
            draggingIndex = draggingIndex,
            offset = offset,
            onMove = onMove
        )
    }
}

class ReorderState<T>(
    val items: List<T>,
    val lazyListState: LazyListState,
    val draggingIndex: MutableIntState,
    val offset: MutableFloatState,
    val onMove: (fromIndex: Int, toIndex: Int) -> Unit
) {
    val isDragging: Boolean
        get() = draggingIndex.intValue != -1

    fun startDrag(index: Int) {
        draggingIndex.intValue = index
    }

    fun endDrag() {
        if (draggingIndex.intValue != -1) {
            draggingIndex.intValue = -1
            offset.floatValue = 0f
        }
    }
}

/**
 * 拖拽句柄 - 用于触发拖拽操作
 */
@Composable
fun DragHandle(
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    if (!enabled) {
        Icon(
            imageVector = Icons.Default.DragHandle,
            contentDescription = null,
            modifier = modifier,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    var rotation by remember { mutableFloatStateOf(0f) }
    val animatedRotation by animateFloatAsState(
        targetValue = rotation,
        animationSpec = androidx.compose.animation.core.tween(300)
    )

    Icon(
        imageVector = Icons.Default.DragHandle,
        contentDescription = "拖拽以重新排序",
        modifier = modifier
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = {
                        rotation = 45f
                        onDragStart()
                    },
                    onDragEnd = {
                        rotation = 0f
                        onDragEnd()
                    },
                    onDragCancel = {
                        rotation = 0f
                        onDragEnd()
                    },
                    onDrag = { change, dragAmount ->
                        onDrag(dragAmount.y)
                        change.consume()
                    }
                )
            }
            .graphicsLayer {
                rotationZ = animatedRotation
                zIndex = if (rotation != 0f) 1f else 0f
            },
        tint = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/**
 * 可拖拽列表项
 */
@Composable
fun <T> DraggableItem(
    reorderState: ReorderState<T>,
    index: Int,
    item: T,
    modifier: Modifier = Modifier,
    content: @Composable (isDragging: Boolean) -> Unit
) {
    val isDragging = reorderState.draggingIndex.intValue == index
    val animatedOffset by animateFloatAsState(
        targetValue = if (isDragging) reorderState.offset.floatValue else 0f,
        animationSpec = androidx.compose.animation.core.tween(150)
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                translationY = animatedOffset
                if (isDragging) {
                    elevation = 8f
                    zIndex = 1f
                }
            }
            .fillMaxWidth()
    ) {
        content(isDragging)
    }
}