package com.openclaw.clawchat.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.openclaw.clawchat.R

/**
 * 创建会话对话框
 *
 * 功能：
 * - 选择 Agent
 * - 选择 Model
 * - 输入初始消息（可选）
 * - 设置会话名称（可选）
 */
@Composable
fun CreateSessionDialog(
    agents: List<AgentItem>,
    models: List<ModelItem>,
    onDismiss: () -> Unit,
    onCreate: (agentId: String?, model: String?, initialMessage: String?, label: String?) -> Unit,
    isLoading: Boolean = false
) {
    var selectedAgent by remember { mutableStateOf<AgentItem?>(null) }
    var selectedModel by remember { mutableStateOf<ModelItem?>(null) }
    var initialMessage by remember { mutableStateOf("") }
    var sessionLabel by remember { mutableStateOf("") }
    var showAdvanced by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 标题
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.create_session_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.create_session_close)
                        )
                    }
                }

                // Agent 选择器（如果有可用的 Agent）
                if (agents.isNotEmpty()) {
                    AgentSelector(
                        agents = agents,
                        selectedAgent = selectedAgent,
                        onAgentSelected = { agent ->
                            selectedAgent = agent
                            // 选择 Agent 后自动选择其默认模型
                            if (agent?.model != null) {
                                val matchingModel = models.find { it.id == agent.model }
                                if (matchingModel != null) {
                                    selectedModel = matchingModel
                                }
                            }
                        }
                    )
                }

                // Model 选择器（如果没有选择 Agent 或 Agent 没有默认模型）
                if (selectedAgent == null || selectedAgent?.model == null) {
                    ModelSelector(
                        models = models,
                        selectedModel = selectedModel,
                        onModelSelected = { selectedModel = it },
                        enabled = models.isNotEmpty()
                    )
                }

                // 高级选项开关
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showAdvanced = !showAdvanced },
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (showAdvanced) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null
                    )
                    Text(
                        text = stringResource(R.string.create_session_advanced),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                // 高级选项：初始消息和会话名称
                if (showAdvanced) {
                    OutlinedTextField(
                        value = sessionLabel,
                        onValueChange = { sessionLabel = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.create_session_name)) },
                        placeholder = { Text(stringResource(R.string.create_session_name_placeholder)) },
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = initialMessage,
                        onValueChange = { initialMessage = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 80.dp, max = 150.dp),
                        label = { Text(stringResource(R.string.create_session_initial_message)) },
                        placeholder = { Text(stringResource(R.string.create_session_initial_message_placeholder)) },
                        maxLines = 4
                    )
                }

                // 创建按钮
                Button(
                    onClick = {
                        onCreate(
                            selectedAgent?.id,
                            selectedModel?.id,
                            initialMessage.takeIf { it.isNotBlank() },
                            sessionLabel.takeIf { it.isNotBlank() }
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.create_session_creating))
                    } else {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.create_session_button))
                    }
                }

                // 提示信息
                if (agents.isEmpty() && models.isEmpty()) {
                    Text(
                        text = stringResource(R.string.create_session_no_agents),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * 快速创建会话按钮
 *
 * 显示在会话列表底部，点击后展开创建对话框
 */
@Composable
fun QuickCreateSessionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = null
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.create_session_quick),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}