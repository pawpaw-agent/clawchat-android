package com.openclaw.clawchat.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.openclaw.clawchat.R
import com.openclaw.clawchat.network.protocol.GatewayConnection
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonArray

/**
 * 定时任务页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CronScreen(
    gateway: GatewayConnection,
    onNavigateBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var cronJobs by remember { mutableStateOf<List<CronJob>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    // Get localized strings outside of LaunchedEffect
    val unnamedText = stringResource(R.string.cron_unnamed)
    val loadFailedText = stringResource(R.string.cron_load_failed)

    // 加载定时任务列表
    LaunchedEffect(Unit) {
        scope.launch {
            try {
                val response = gateway.cronList()
                if (response.isSuccess()) {
                    val jobsArray = response.payload?.jsonObject?.get("jobs")?.jsonArray
                    cronJobs = jobsArray?.mapIndexed { index, element ->
                        val obj = element.jsonObject
                        CronJob(
                            id = obj["id"]?.jsonPrimitive?.content ?: index.toString(),
                            name = obj["name"]?.jsonPrimitive?.content ?: unnamedText,
                            cron = obj["cron"]?.jsonPrimitive?.content ?: "",
                            sessionKey = obj["sessionKey"]?.jsonPrimitive?.content ?: "",
                            prompt = obj["prompt"]?.jsonPrimitive?.content ?: "",
                            enabled = obj["enabled"]?.jsonPrimitive?.content?.toBoolean() ?: true
                        )
                    } ?: emptyList()
                } else {
                    error = response.error?.message ?: loadFailedText
                }
            } catch (e: Exception) {
                error = e.message ?: loadFailedText
            } finally {
                isLoading = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.cron_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.cron_back))
                    }
                },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.cron_add_task))
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                error != null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = error ?: stringResource(R.string.error_unknown),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                cronJobs.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.cron_no_tasks),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.cron_add_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(cronJobs, key = { it.id }) { job ->
                            CronJobItem(
                                job = job,
                                onRun = {
                                    scope.launch {
                                        try {
                                            gateway.cronRun(job.id)
                                        } catch (e: Exception) {
                                            error = e.message
                                        }
                                    }
                                },
                                onDelete = {
                                    scope.launch {
                                        try {
                                            gateway.cronRemove(job.id)
                                            cronJobs = cronJobs.filter { it.id != job.id }
                                        } catch (e: Exception) {
                                            error = e.message
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
    
    // 添加任务对话框
    if (showAddDialog) {
        AddCronJobDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { name, cron, sessionKey, prompt ->
                scope.launch {
                    try {
                        gateway.cronAdd(name, cron, sessionKey, prompt)
                        // 刷新列表
                        val response = gateway.cronList()
                        if (response.isSuccess()) {
                            // 更新列表
                        }
                    } catch (e: Exception) {
                        error = e.message
                    }
                }
                showAddDialog = false
            }
        )
    }
}

/**
 * 定时任务项
 */
@Composable
private fun CronJobItem(
    job: CronJob,
    onRun: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = job.name,
                    style = MaterialTheme.typography.titleMedium
                )
                Switch(
                    checked = job.enabled,
                    onCheckedChange = null
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Cron: ${job.cron}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.cron_prompt, "${job.prompt.take(50)}${if (job.prompt.length > 50) "..." else ""}"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = onRun) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.cron_execute))
                }
                OutlinedButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.cron_delete))
                }
            }
        }
    }
}

/**
 * 添加定时任务对话框
 */
@Composable
private fun AddCronJobDialog(
    onDismiss: () -> Unit,
    onAdd: (name: String, cron: String, sessionKey: String, prompt: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var cron by remember { mutableStateOf("") }
    var sessionKey by remember { mutableStateOf("") }
    var prompt by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.cron_add_dialog_title)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.cron_task_name)) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = cron,
                    onValueChange = { cron = it },
                    label = { Text(stringResource(R.string.cron_expression)) },
                    placeholder = { Text(stringResource(R.string.cron_expression_hint)) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = sessionKey,
                    onValueChange = { sessionKey = it },
                    label = { Text(stringResource(R.string.cron_session_key)) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text(stringResource(R.string.cron_task_prompt)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank() && cron.isNotBlank() && prompt.isNotBlank()) {
                        onAdd(name, cron, sessionKey, prompt)
                    }
                }
            ) {
                Text(stringResource(R.string.cron_add_button))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

/**
 * 定时任务数据类
 */
data class CronJob(
    val id: String,
    val name: String,
    val cron: String,
    val sessionKey: String,
    val prompt: String,
    val enabled: Boolean
)