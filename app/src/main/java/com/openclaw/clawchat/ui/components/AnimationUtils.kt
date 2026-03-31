package com.openclaw.clawchat.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically

/**
 * 动画预设工具
 * 提供统一的动画效果
 */
object AnimationUtils {
    
    // 动画时长
    const val DURATION_SHORT = 150
    const val DURATION_MEDIUM = 300
    const val DURATION_LONG = 500
    
    // ─────────────────────────────────────────────────────────────
    // 入场动画
    // ─────────────────────────────────────────────────────────────
    
    /**
     * 页面切换动画（crossfade）
     */
    fun pageTransition(): Pair<EnterTransition, ExitTransition> {
        return fadeIn(
            animationSpec = tween(DURATION_MEDIUM, easing = FastOutSlowInEasing)
        ) to fadeOut(
            animationSpec = tween(DURATION_SHORT, easing = FastOutSlowInEasing)
        )
    }
    
    /**
     * 列表项入场动画（fadeIn + slideIn）
     */
    fun listItemEnter(): EnterTransition {
        return fadeIn(
            animationSpec = tween(DURATION_SHORT)
        ) + slideInVertically(
            animationSpec = spring(stiffness = Spring.StiffnessLow),
            initialOffsetY = { it / 4 }
        )
    }
    
    /**
     * 消息发送动画（scaleIn）
     */
    fun messageSendEnter(): EnterTransition {
        return scaleIn(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            ),
            initialScale = 0.8f
        ) + fadeIn(
            animationSpec = tween(DURATION_SHORT)
        )
    }
    
    /**
     * 卡片展开动画
     */
    fun cardExpand(): EnterTransition {
        return fadeIn(
            animationSpec = spring(stiffness = Spring.StiffnessMedium)
        ) + slideInVertically(
            animationSpec = spring(stiffness = Spring.StiffnessLow)
        )
    }
    
    // ─────────────────────────────────────────────────────────────
    // 出场动画
    // ─────────────────────────────────────────────────────────────
    
    /**
     * 列表项出场动画
     */
    fun listItemExit(): ExitTransition {
        return fadeOut(
            animationSpec = tween(DURATION_SHORT)
        ) + slideOutVertically(
            animationSpec = tween(DURATION_SHORT),
            targetOffsetY = { -it / 4 }
        )
    }
    
    /**
     * 删除动画（fadeOut + slideOut）
     */
    fun messageDeleteExit(): ExitTransition {
        return fadeOut(
            animationSpec = tween(DURATION_SHORT)
        ) + slideOutHorizontally(
            animationSpec = spring(stiffness = Spring.StiffnessMedium),
            targetOffsetX = { -it }
        ) + scaleOut(
            animationSpec = tween(DURATION_SHORT),
            targetScale = 0.5f
        )
    }
    
    /**
     * 卡片收起动画
     */
    fun cardCollapse(): ExitTransition {
        return fadeOut(
            animationSpec = tween(DURATION_SHORT)
        ) + slideOutVertically(
            animationSpec = tween(DURATION_SHORT)
        )
    }
    
    // ─────────────────────────────────────────────────────────────
    // 特殊动画
    // ─────────────────────────────────────────────────────────────
    
    /**
     * Toast 提示动画
     */
    fun toastEnter(): EnterTransition {
        return fadeIn(
            animationSpec = tween(DURATION_SHORT)
        ) + slideInVertically(
            animationSpec = spring(stiffness = Spring.StiffnessLow),
            initialOffsetY = { it }
        )
    }
    
    fun toastExit(): ExitTransition {
        return fadeOut(
            animationSpec = tween(DURATION_SHORT)
        ) + slideOutVertically(
            animationSpec = tween(DURATION_SHORT),
            targetOffsetY = { it }
        )
    }
    
    /**
     * 对话框动画
     */
    fun dialogEnter(): EnterTransition {
        return scaleIn(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            ),
            initialScale = 0.8f
        ) + fadeIn(
            animationSpec = tween(DURATION_SHORT)
        )
    }
    
    fun dialogExit(): ExitTransition {
        return scaleOut(
            animationSpec = tween(DURATION_SHORT),
            targetScale = 0.9f
        ) + fadeOut(
            animationSpec = tween(DURATION_SHORT)
        )
    }
}

/**
 * 动画规格类型
 */
sealed class AnimationSpecType {
    data class Tween(val duration: Int = AnimationUtils.DURATION_SHORT) : AnimationSpecType()
    data class Spring(val dampingRatio: Float = Spring.DampingRatioMediumBouncy) : AnimationSpecType()
}