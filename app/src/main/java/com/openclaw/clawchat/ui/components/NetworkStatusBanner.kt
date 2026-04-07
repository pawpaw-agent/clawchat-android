package com.openclaw.clawchat.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.openclaw.clawchat.R
import com.openclaw.clawchat.ui.state.ConnectionStatus
import com.openclaw.clawchat.ui.theme.DesignTokens

/**
 * 网络状态横幅
 *
 * 显示在界面顶部，当连接断开或出错时提示用户
 * - Disconnected/Error：红色警告横幅
 * - Connecting：黄色提示横幅（带进度动画）
 */
@Composable
fun NetworkStatusBanner(
    status: ConnectionStatus,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null
) {
    // 仅在非连接状态显示
    val shouldShow = status is ConnectionStatus.Disconnected ||
                     status is ConnectionStatus.Error ||
                     status is ConnectionStatus.Connecting

    AnimatedVisibility(
        visible = shouldShow,
        enter = slideInVertically() + fadeIn(),
        exit = slideOutVertically() + fadeOut(),
        modifier = modifier
    ) {
        when (status) {
            is ConnectionStatus.Disconnected -> {
                DisconnectedBanner(onRetry = onRetry)
            }
            is ConnectionStatus.Error -> {
                ErrorBanner(message = status.message, onRetry = onRetry)
            }
            is ConnectionStatus.Connecting -> {
                ConnectingBanner()
            }
            else -> {}
        }
    }
}

/**
 * 断开连接横幅
 */
@Composable
private fun DisconnectedBanner(
    onRetry: (() -> Unit)? = null
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = DesignTokens.space4, vertical = DesignTokens.space2),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = RoundedCornerShape(DesignTokens.radiusMd)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = DesignTokens.space3, vertical = DesignTokens.space2),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.CloudOff,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = stringResource(R.string.network_not_connected),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            if (onRetry != null) {
                TextButton(
                    onClick = onRetry,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Text(stringResource(R.string.retry))
                }
            }
        }
    }
}

/**
 * 错误横幅
 */
@Composable
private fun ErrorBanner(
    message: String,
    onRetry: (() -> Unit)? = null
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = DesignTokens.space4, vertical = DesignTokens.space2),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = RoundedCornerShape(DesignTokens.radiusMd)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = DesignTokens.space3, vertical = DesignTokens.space2),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = message.take(50) + if (message.length > 50) "..." else "",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2
                )
            }

            if (onRetry != null) {
                TextButton(
                    onClick = onRetry,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Text(stringResource(R.string.retry))
                }
            }
        }
    }
}

/**
 * 连接中横幅（带动画）
 */
@Composable
private fun ConnectingBanner() {
    val infiniteTransition = rememberInfiniteTransition(label = "connecting")
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "progress"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = DesignTokens.space4, vertical = DesignTokens.space2),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = RoundedCornerShape(DesignTokens.radiusMd)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = DesignTokens.space3, vertical = DesignTokens.space2),
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 动画进度环
            Box(
                modifier = Modifier.size(18.dp)
            ) {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    trackColor = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.3f)
                )
            }

            Text(
                text = stringResource(R.string.network_connecting),
                style = MaterialTheme.typography.bodyMedium
            )

            // 脉冲动画点
            val pulseAlpha by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = 0.3f,
                animationSpec = infiniteRepeatable(
                    animation = tween(500, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "pulse"
            )

            Spacer(modifier = Modifier.width(4.dp))

            repeat(3) { index ->
                val delay = index * 0.2f
                val dotAlpha by infiniteTransition.animateFloat(
                    initialValue = 0.3f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(300, delayMillis = (delay * 300).toInt(), easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "dot_$index"
                )

                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = dotAlpha))
                )
                if (index < 2) Spacer(modifier = Modifier.width(4.dp))
            }
        }
    }
}