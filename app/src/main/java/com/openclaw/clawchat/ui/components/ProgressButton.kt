package com.openclaw.clawchat.ui.components

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp

/**
 * 带进度条的按钮
 * 显示加载状态和进度
 */
@Composable
fun ProgressButton(
    text: String,
    onClick: () -> Unit,
    loading: Boolean,
    progress: Float? = null,  // null 表示不确定进度
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier
    ) {
        Box(
            contentAlignment = Alignment.Center
        ) {
            // 进度指示器
            if (loading) {
                if (progress != null) {
                    CircularProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                        strokeCap = StrokeCap.Round
                    )
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                        strokeCap = StrokeCap.Round
                    )
                }
            }
            
            // 按钮文本（加载时隐藏）
            Text(
                text = text,
                modifier = Modifier.alpha(if (loading) 0f else 1f)
            )
        }
    }
}

/**
 * 带进度的发送按钮
 * 用于消息发送场景
 */
@Composable
fun SendProgressButton(
    onClick: () -> Unit,
    sending: Boolean,
    progress: Float? = null,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    ProgressButton(
        text = "发送",
        onClick = onClick,
        loading = sending,
        progress = progress,
        enabled = enabled,
        modifier = modifier
    )
}