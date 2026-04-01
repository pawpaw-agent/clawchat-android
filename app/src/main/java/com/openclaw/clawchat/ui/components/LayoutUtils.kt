package com.openclaw.clawchat.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.window.layout.DisplayFeature
import androidx.window.layout.FoldingFeature

/**
 * 响应式布局工具
 * 支持横屏、折叠屏、大屏幕适配
 */
object LayoutUtils {
    
    // ─────────────────────────────────────────────────────────────
    // 屏幕尺寸阈值
    // ─────────────────────────────────────────────────────────────
    
    val COMPACT_MAX_WIDTH = 600.dp   // 手机竖屏
    val MEDIUM_MAX_WIDTH = 840.dp    // 手机横屏/小平板
    val EXPANDED_MIN_WIDTH = 840.dp  // 大平板/折叠屏展开
    
    // ─────────────────────────────────────────────────────────────
    // 窗口大小类
    // ─────────────────────────────────────────────────────────────
    
    enum class WindowSizeClass {
        COMPACT,   // < 600dp（手机）
        MEDIUM,    // 600-840dp（手机横屏/小平板）
        EXPANDED   // > 840dp（大平板）
    }
    
    /**
     * 计算窗口大小类
     */
    @Composable
    fun calculateWindowSizeClass(width: Dp): WindowSizeClass {
        return when {
            width < COMPACT_MAX_WIDTH -> WindowSizeClass.COMPACT
            width < MEDIUM_MAX_WIDTH -> WindowSizeClass.MEDIUM
            else -> WindowSizeClass.EXPANDED
        }
    }
    
    // ─────────────────────────────────────────────────────────────
    // 响应式布局配置
    // ─────────────────────────────────────────────────────────────
    
    /**
     * 布局配置
     */
    data class LayoutConfig(
        val isCompact: Boolean,
        val isMedium: Boolean,
        val isExpanded: Boolean,
        val isLandscape: Boolean,
        val isFolding: Boolean,
        val showSidebar: Boolean,
        val showPreviewPane: Boolean,
        val messageColumns: Int,
        val maxMessagesPerRow: Int
    )
    
    /**
     * 获取布局配置
     */
    @Composable
    fun getLayoutConfig(
        width: Dp,
        height: Dp,
        foldingFeature: FoldingFeature? = null
    ): LayoutConfig {
        val sizeClass = calculateWindowSizeClass(width)
        val isLandscape = width > height
        
        val isFolding = foldingFeature?.state == androidx.window.layout.FoldingFeature.State.FLAT
        val foldingOrientation = foldingFeature?.orientation
        
        return LayoutConfig(
            isCompact = sizeClass == WindowSizeClass.COMPACT,
            isMedium = sizeClass == WindowSizeClass.MEDIUM,
            isExpanded = sizeClass == WindowSizeClass.EXPANDED,
            isLandscape = isLandscape,
            isFolding = isFolding,
            showSidebar = sizeClass == WindowSizeClass.EXPANDED && !isLandscape,
            showPreviewPane = sizeClass == WindowSizeClass.EXPANDED && isLandscape,
            messageColumns = when {
                sizeClass == WindowSizeClass.EXPANDED && isLandscape -> 2
                sizeClass == WindowSizeClass.MEDIUM -> 1
                else -> 1
            },
            maxMessagesPerRow = when {
                sizeClass == WindowSizeClass.EXPANDED -> 3
                sizeClass == WindowSizeClass.MEDIUM -> 2
                else -> 1
            }
        )
    }
    
    /**
     * 获取会话列表布局配置
     */
    @Composable
    fun getSessionListLayout(sizeClass: WindowSizeClass): SessionListLayout {
        return when (sizeClass) {
            WindowSizeClass.COMPACT -> SessionListLayout(
                widthFraction = 1f,
                showPreview = false,
                showDetails = false
            )
            WindowSizeClass.MEDIUM -> SessionListLayout(
                widthFraction = 0.4f,
                showPreview = true,
                showDetails = false
            )
            WindowSizeClass.EXPANDED -> SessionListLayout(
                widthFraction = 0.33f,
                showPreview = true,
                showDetails = true
            )
        }
    }
    
    /**
     * 会话列表布局
     */
    data class SessionListLayout(
        val widthFraction: Float,
        val showPreview: Boolean,
        val showDetails: Boolean
    )
    
    /**
     * 获取消息列表布局配置
     */
    @Composable
    fun getMessageListLayout(sizeClass: WindowSizeClass): MessageListLayout {
        return when (sizeClass) {
            WindowSizeClass.COMPACT -> MessageListLayout(
                padding = 8.dp,
                messageSpacing = 4.dp,
                showAvatars = true,
                maxWidth = 600.dp
            )
            WindowSizeClass.MEDIUM -> MessageListLayout(
                padding = 16.dp,
                messageSpacing = 8.dp,
                showAvatars = true,
                maxWidth = 800.dp
            )
            WindowSizeClass.EXPANDED -> MessageListLayout(
                padding = 24.dp,
                messageSpacing = 12.dp,
                showAvatars = true,
                maxWidth = null // 无限制
            )
        }
    }
    
    /**
     * 消息列表布局
     */
    data class MessageListLayout(
        val padding: Dp,
        val messageSpacing: Dp,
        val showAvatars: Boolean,
        val maxWidth: Dp?
    )
}