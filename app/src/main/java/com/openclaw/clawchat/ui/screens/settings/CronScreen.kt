package com.openclaw.clawchat.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.openclaw.clawchat.R
import com.openclaw.clawchat.network.protocol.GatewayConnection
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonArray

/**
 * 加载定时任务列表
 */
private suspend fun loadCronJobs(
    gateway: GatewayConnection,
    unnamedText: String = "未命名"
): Result<List<CronJob>> {
    return try {
        val response = gateway.cronList()
        if (response.isSuccess()) {
            val jobsArray = response.payload?.jsonObject?.get("jobs")?.jsonArray
            val jobs = jobsArray?.mapIndexed { index, element ->
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
            Result.success(jobs)
        } else {
            Result.failure(Exception(response.error?.message ?: "加载失败"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
}

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
    val snackbarHostState = remember { SnackbarHostState() }
    var cronJobs by remember { mutableStateOf<List<CronJob>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var jobToDelete by remember { mutableStateOf<CronJob?>(null) }

    val unnamedText = stringResource(R.string.cron_unnamed)
    val loadFailedText = stringResource(R.string.cron_load_failed)

    fun refreshCronJobs() {
        scope.launch {
            loadCronJobs(gateway, unnamedText).onSuccess { jobs ->
                cronJobs = jobs
            }.onFailure { err ->
                error = err.message ?: loadFailedText
            }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        refreshCronJobs()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { refreshCronJobs() }) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.retry))
                        }
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
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedButton(onClick = { showAddDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.cron_add_task))
                        }
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
                                onToggle = { enabled ->
                                    scope.launch {
                                        try {
                                            val response = gateway.cronPatch(job.id, enabled = enabled)
                                            if (response.isSuccess()) {
                                                cronJobs = cronJobs.map {
                                                    if (it.id == job.id) it.copy(enabled = enabled) else it
                                                }
                                            } else {
                                                snackbarHostState.showSnackbar(
                                                    stringResource(R.string.cron_toggle_failed, response.error?.message)
                                                )
                                            }
                                        } catch (e: Exception) {
                                            snackbarHostState.showSnackbar(
                                                stringResource(R.string.cron_toggle_failed, e.message)
                                            )
                                        }
                                    }
                                },
                                onRun = {
                                    scope.launch {
                                        try {
                                            val response = gateway.cronRun(job.id)
                                            val msg = if (response.isSuccess()) {
                                                stringResource(R.string.cron_run_success, job.name)
                                            } else {
                                                stringResource(R.string.cron_run_failed, response.error?.message ?: stringResource(R.string.error_unknown_message))
                                            }
                                            snackbarHostState.showSnackbar(msg)
                                        } catch (e: Exception) {
                                            snackbarHostState.showSnackbar(
                                                stringResource(R.string.cron_run_failed, e.message)
                                            )
                                        }
                                    }
                                },
                                onDelete = {
                                    jobToDelete = job
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
            defaultSessionKey = gateway.defaultSessionKey,
            onDismiss = { showAddDialog = false },
            onAdd = { name, cron, sessionKey, prompt ->
                scope.launch {
                    try {
                        val response = gateway.cronAdd(name, cron, sessionKey, prompt)
                        if (response.isSuccess()) {
                            snackbarHostState.showSnackbar(
                                stringResource(R.string.cron_added_success, name)
                            )
                            refreshCronJobs()
                        } else {
                            snackbarHostState.showSnackbar(
                                stringResource(R.string.cron_add_failed, response.error?.message ?: stringResource(R.string.error_unknown_message))
                            )
                        }
                    } catch (e: Exception) {
                        snackbarHostState.showSnackbar(
                            stringResource(R.string.cron_add_failed, e.message)
                        )
                    }
                }
                showAddDialog = false
            }
        )
    }

    // 删除确认对话框
    jobToDelete?.let { job ->
        AlertDialog(
            onDismissRequest = { jobToDelete = null },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(R.string.cron_delete_confirm_title)) },
            text = { Text(stringResource(R.string.cron_delete_confirm_message, job.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            try {
                                gateway.cronRemove(job.id)
                                cronJobs = cronJobs.filter { it.id != job.id }
                                snackbarHostState.showSnackbar(
                                    stringResource(R.string.cron_deleted, job.name)
                                )
                            } catch (e: Exception) {
                                snackbarHostState.showSnackbar(
                                    stringResource(R.string.cron_delete_failed, e.message)
                                )
                            }
                        }
                        jobToDelete = null
                    }
                ) {
                    Text(stringResource(R.string.cron_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { jobToDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
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
    onToggle: (Boolean) -> Unit,
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
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = job.enabled,
                    onCheckedChange = onToggle
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Cron: ${job.cron}",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.cron_prompt, job.prompt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )
            if (job.sessionKey.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.cron_session_label, job.sessionKey),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onRun,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.cron_execute))
                }
                OutlinedButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
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
    defaultSessionKey: String?,
    onDismiss: () -> Unit,
    onAdd: (name: String, cron: String, sessionKey: String, prompt: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var cron by remember { mutableStateOf("") }
    var sessionKey by remember { mutableStateOf(defaultSessionKey ?: "") }
    var prompt by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.cron_add_dialog_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.cron_task_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )
                OutlinedTextField(
                    value = cron,
                    onValueChange = { cron = it },
                    label = { Text(stringResource(R.string.cron_expression)) },
                    placeholder = {
                        Text(
                            stringResource(R.string.cron_expression_hint),
                            fontFamily = FontFamily.Monospace
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    singleLine = true
                )
                OutlinedTextField(
                    value = sessionKey,
                    onValueChange = { sessionKey = it },
                    label = { Text(stringResource(R.string.cron_session_key)) },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    singleLine = true
                )
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text(stringResource(R.string.cron_task_prompt)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && cron.isNotBlank() && prompt.isNotBlank(),
                onClick = { onAdd(name, cron, sessionKey, prompt) }
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
