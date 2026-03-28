package com.openclaw.clawchat.ui.screens.session

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 会话设置选项
 */
data class SessionSettingOption(
    val value: String,
    val displayName: String
)

/**
 * Thinking 级别选项
 */
val THINKING_LEVELS = listOf(
    SessionSettingOption("inherit", "继承"),
    SessionSettingOption("off", "关闭"),
    SessionSettingOption("minimal", "最小"),
    SessionSettingOption("low", "低"),
    SessionSettingOption("medium", "中"),
    SessionSettingOption("high", "高"),
    SessionSettingOption("xhigh", "极高")
)

/**
 * Fast 模式选项
 */
val FAST_MODE_OPTIONS = listOf(
    SessionSettingOption("inherit", "继承"),
    SessionSettingOption("on", "开启"),
    SessionSettingOption("off", "关闭")
)

/**
 * Verbose 级别选项
 */
val VERBOSE_LEVELS = listOf(
    SessionSettingOption("inherit", "继承"),
    SessionSettingOption("off", "关闭"),
    SessionSettingOption("on", "开启"),
    SessionSettingOption("full", "完整")
)

/**
 * Reasoning 级别选项
 */
val REASONING_LEVELS = listOf(
    SessionSettingOption("inherit", "继承"),
    SessionSettingOption("off", "关闭"),
    SessionSettingOption("on", "开启"),
    SessionSettingOption("stream", "流式")
)

/**
 * 会话设置底部抽屉
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionSettingsBottomSheet(
    thinkingLevel: String?,
    fastMode: Boolean?,
    verboseLevel: String?,
    reasoningLevel: String?,
    onUpdateSettings: (
        thinkingLevel: String?,
        fastMode: Boolean?,
        verboseLevel: String?,
        reasoningLevel: String?
    ) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedThinking by remember { mutableStateOf(thinkingLevel ?: "inherit") }
    var selectedFast by remember { mutableStateOf(fastMode) }
    var selectedVerbose by remember { mutableStateOf(verboseLevel ?: "inherit") }
    var selectedReasoning by remember { mutableStateOf(reasoningLevel ?: "inherit") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 24.dp)
    ) {
        // 标题
        Text(
            text = "会话设置",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Thinking 级别
        SettingSection(
            title = "思考级别",
            subtitle = "控制模型思考深度"
        ) {
            SettingChipGroup(
                options = THINKING_LEVELS,
                selectedValue = selectedThinking,
                onSelect = { selectedThinking = it }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Fast 模式
        SettingSection(
            title = "快速模式",
            subtitle = "加速响应，降低质量"
        ) {
            SettingChipGroup(
                options = FAST_MODE_OPTIONS,
                selectedValue = when (selectedFast) {
                    true -> "on"
                    false -> "off"
                    null -> "inherit"
                },
                onSelect = { 
                    selectedFast = when (it) {
                        "on" -> true
                        "off" -> false
                        else -> null
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Verbose 级别
        SettingSection(
            title = "详细模式",
            subtitle = "显示工具执行详情"
        ) {
            SettingChipGroup(
                options = VERBOSE_LEVELS,
                selectedValue = selectedVerbose,
                onSelect = { selectedVerbose = it }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Reasoning 级别
        SettingSection(
            title = "推理模式",
            subtitle = "显示推理过程"
        ) {
            SettingChipGroup(
                options = REASONING_LEVELS,
                selectedValue = selectedReasoning,
                onSelect = { selectedReasoning = it }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 应用按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.weight(1f)
            ) {
                Text("取消")
            }
            
            Button(
                onClick = {
                    onUpdateSettings(
                        if (selectedThinking != "inherit") selectedThinking else null,
                        selectedFast,
                        if (selectedVerbose != "inherit") selectedVerbose else null,
                        if (selectedReasoning != "inherit") selectedReasoning else null
                    )
                    onDismiss()
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("应用")
            }
        }

        // 底部安全区域
        Spacer(modifier = Modifier.height(16.dp))
    }
}

/**
 * 设置分区标题
 */
@Composable
private fun SettingSection(
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        content()
    }
}

/**
 * 设置选项芯片组
 */
@Composable
private fun SettingChipGroup(
    options: List<SessionSettingOption>,
    selectedValue: String?,
    onSelect: (String) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.chunked(4).forEach { rowOptions ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                rowOptions.forEach { option ->
                    FilterChip(
                        selected = selectedValue == option.value,
                        onClick = { onSelect(option.value) },
                        label = { Text(option.displayName, maxLines = 1) },
                        modifier = Modifier.weight(1f)
                    )
                }
                // 填充空白
                repeat(4 - rowOptions.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}