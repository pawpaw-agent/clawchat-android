package com.openclaw.clawchat.util

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Modifier 扩展函数
 */

/**
 * 条件性应用 Modifier
 */
inline fun Modifier.thenIf(condition: Boolean, modifier: Modifier.() -> Modifier): Modifier {
    return if (condition) then(modifier()) else this
}

/**
 * 无涟漪点击
 */
fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier = composed {
    clickable(
        indication = null,
        interactionSource = remember { MutableInteractionSource() }
    ) { onClick() }
}

/**
 * 条件性点击
 */
fun Modifier.conditionalClickable(
    enabled: Boolean,
    onClick: () -> Unit
): Modifier = composed {
    thenIf(enabled) {
        clickable(onClick = onClick)
    }
}

/**
 * 动画边框
 */
fun Modifier.animatedBorder(
    show: Boolean,
    color: androidx.compose.ui.graphics.Color,
    width: Dp = 2.dp,
    animationSpec: AnimationSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessLow
    )
): Modifier = composed {
    if (show) {
        // 简单实现，实际需要更复杂的动画逻辑
        this
    } else {
        this
    }
}

/**
 * 裁剪边缘
 */
fun Modifier.clipEdges(
    top: Boolean = false,
    bottom: Boolean = false,
    start: Boolean = false,
    end: Boolean = false
): Modifier = drawWithContent {
    val clipTop = if (top) 0f else Float.MIN_VALUE
    val clipBottom = if (bottom) size.height else Float.MAX_VALUE
    val clipStart = if (start) 0f else Float.MIN_VALUE
    val clipEnd = if (end) size.width else Float.MAX_VALUE
    
    clipRect(
        left = clipStart,
        top = clipTop,
        right = clipEnd,
        bottom = clipBottom
    ) {
        this@drawWithContent.drawContent()
    }
}

/**
 * 防抖点击
 */
fun Modifier.debounceClickable(
    debounceTime: Long = 500,
    onClick: () -> Unit
): Modifier = composed {
    var lastClickTime = remember { 0L }
    
    clickable {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastClickTime >= debounceTime) {
            lastClickTime = currentTime
            onClick()
        }
    }
}

/**
 * 双击检测
 */
fun Modifier.doubleClick(
    onDoubleClick: () -> Unit,
    onClick: () -> Unit = {}
): Modifier = composed {
    var lastClickTime = remember { 0L }
    
    clickable {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastClickTime < 300) {
            onDoubleClick()
            lastClickTime = 0L
        } else {
            onClick()
            lastClickTime = currentTime
        }
    }
}