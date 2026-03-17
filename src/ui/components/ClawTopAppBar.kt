package ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import ui.theme.BackgroundSecondary

/**
 * ClawChat 自定义 TopAppBar
 * 用于主界面和会话界面的顶部导航栏
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClawTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    navigationIcon: @Composable (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    showConnectionStatus: Boolean = false,
    isConnected: Boolean = false
) {
    TopAppBar(
        title = {
            Column {
                Text(
                    text = title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleLarge
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        modifier = modifier,
        navigationIcon = { navigationIcon?.invoke() },
        actions = { actions() },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = BackgroundSecondary,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
            actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            scrolledContainerColor = BackgroundSecondary
        )
    )
}

/**
 * 简化的 TopAppBar - 用于次级页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClawSimpleTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    TopAppBar(
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
        },
        modifier = modifier,
        navigationIcon = { navigationIcon?.invoke() },
        actions = { actions() },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = BackgroundSecondary,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            navigationIconContentColor = MaterialTheme.colorScheme.onSurface
        )
    )
}
