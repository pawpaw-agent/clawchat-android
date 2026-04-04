package com.openclaw.clawchat.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*

/**
 * 动画工具类 - 用于实现平滑的 UI 过渡（类似 webchat 的动画效果）
 */
object AnimationUtils {

    /**
     * 默认弹簧动画参数
     */
    val DefaultSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium
    )

    /**
     * 缓入缓出动画曲线
     */
    val EaseInOutQuad = CubicBezierEasing(0.45f, 0.05f, 0.55f, 0.95f)

    /**
     * 快速动画参数
     */
    val QuickDecay = exponentialDecay<Float>(
        frictionMultiplier = 0.8f,
        absVelocityThreshold = 0.5f
    )
}

/**
 * 扩展函数：检测滚动是否接近底部
 */
fun LazyListState.isNearBottom(threshold: Int = 50): Boolean {
    val totalItems = layoutInfo.totalItemsCount
    val lastItemIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
    val offset = layoutInfo.visibleItemsInfo.lastOrNull()?.offset ?: 0

    // 检查是否在底部附近
    return totalItems > 0 && (totalItems - lastItemIndex) <= threshold && offset >= -threshold
}

/**
 * 扩展函数：检测滚动是否接近顶部
 */
fun LazyListState.isNearTop(threshold: Int = 50): Boolean {
    val firstItemIndex = layoutInfo.visibleItemsInfo.firstOrNull()?.index ?: 0
    val offset = layoutInfo.visibleItemsInfo.firstOrNull()?.offset ?: 0

    // 检查是否在顶部附近
    return firstItemIndex <= threshold && offset >= -threshold
}

/**
 * 滚动状态管理器
 */
@Composable
fun rememberScrollState(): ScrollStateManager {
    val scrollState by remember { mutableStateOf(ScrollStateManager()) }
    return scrollState
}

class ScrollStateManager {
    var isUserScrolling by mutableStateOf(false)
        private set
    var isAutoScrolling by mutableStateOf(false)
        private set
    var lastScrollPosition by mutableStateOf(0)
        private set

    fun startUserScroll(position: Int) {
        isUserScrolling = true
        lastScrollPosition = position
    }

    fun startAutoScroll(position: Int) {
        isAutoScrolling = true
        lastScrollPosition = position
    }

    fun endScroll() {
        isUserScrolling = false
        isAutoScrolling = false
    }
}