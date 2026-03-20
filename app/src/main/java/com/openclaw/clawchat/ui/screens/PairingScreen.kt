package com.openclaw.clawchat.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openclaw.clawchat.network.GatewayUrlUtil
import com.openclaw.clawchat.ui.state.ConnectMode
import com.openclaw.clawchat.ui.state.PairingEvent
import com.openclaw.clawchat.ui.state.PairingStatus
import com.openclaw.clawchat.ui.state.PairingViewModel

/**
 * 设备连接/配对屏幕
 *
 * 三种连接模式：
 * 1. Token — 输入 Gateway 地址 + Token 直连
 * 2. 配对 — Ed25519 设备签名 + 管理员批准
 * 3. Setup Code — 粘贴 base64 配对码
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PairingScreen(
    viewModel: PairingViewModel,
    onPairingSuccess: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // 证书确认对话框
    state.certificateEvent?.let { event ->
        CertificateConfirmationDialog(
            hostname = event.hostname,
            fingerprint = event.fingerprint,
            isMismatch = event.isMismatch,
            storedFingerprint = event.storedFingerprint,
            onTrust = { viewModel.confirmCertificateTrust() },
            onReject = { viewModel.rejectCertificate() }
        )
    }

    LaunchedEffect(viewModel.events) {
        viewModel.events.collect { event ->
            when (event) {
                is PairingEvent.PairingSuccess -> onPairingSuccess()
                is PairingEvent.PairingTimeout,
                is PairingEvent.PairingRejected -> {}
                else -> {}
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("连接 Gateway") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── 连接模式选择 ──
            ConnectModeSelector(
                selected = state.connectMode,
                onSelect = { viewModel.setConnectMode(it) },
                enabled = !state.isPairing
            )

            Spacer(modifier = Modifier.height(20.dp))

            // ── 模式内容 ──
            when (state.connectMode) {
                ConnectMode.TOKEN -> TokenModeContent(
                    gatewayUrl = state.gatewayUrl,
                    token = state.token,
                    onGatewayUrlChange = { viewModel.setGatewayUrl(it) },
                    onTokenChange = { viewModel.setToken(it) },
                    onConnect = { viewModel.connectWithToken() },
                    isPairing = state.isPairing,
                    status = state.status
                )

                ConnectMode.PAIRING -> PairingModeContent(
                    viewModel = viewModel,
                    state = state,
                    context = context
                )
            }

            // ── 状态指示器（非初始化时显示） ──
            if (state.status !is PairingStatus.Initializing || state.isPairing) {
                Spacer(modifier = Modifier.height(24.dp))
                PairingStatusIndicator(
                    status = state.status,
                    onRetry = {
                        when (state.connectMode) {
                            ConnectMode.TOKEN -> viewModel.connectWithToken()
                            ConnectMode.PAIRING -> viewModel.startPairing()
                        }
                    },
                    onCancel = { viewModel.cancelPairing() }
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// 连接模式选择器
// ─────────────────────────────────────────────────────────────

@Composable
private fun ConnectModeSelector(
    selected: ConnectMode,
    onSelect: (ConnectMode) -> Unit,
    enabled: Boolean
) {
    val modes = listOf(
        ConnectMode.TOKEN to "Token",
        ConnectMode.PAIRING to "配对"
    )

    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        modes.forEachIndexed { index, (mode, label) ->
            SegmentedButton(
                selected = selected == mode,
                onClick = { onSelect(mode) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                enabled = enabled
            ) {
                Text(label)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Token 模式
// ─────────────────────────────────────────────────────────────

@Composable
private fun TokenModeContent(
    gatewayUrl: String,
    token: String,
    onGatewayUrlChange: (String) -> Unit,
    onTokenChange: (String) -> Unit,
    onConnect: () -> Unit,
    isPairing: Boolean,
    status: PairingStatus
) {
    val urlValid = gatewayUrl.isBlank() || GatewayUrlUtil.isValidInput(gatewayUrl)
    val wsPreview = if (gatewayUrl.isNotBlank() && urlValid) {
        GatewayUrlUtil.normalizeToWebSocketUrl(gatewayUrl)
    } else null

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "直接输入 Gateway 地址和 Token 连接",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Gateway 地址
            OutlinedTextField(
                value = gatewayUrl,
                onValueChange = onGatewayUrlChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Gateway 地址") },
                placeholder = { Text("192.168.0.213:18789") },
                leadingIcon = { Icon(Icons.Default.Link, null) },
                enabled = !isPairing,
                singleLine = true,
                isError = gatewayUrl.isNotBlank() && !urlValid
            )

            if (wsPreview != null) {
                Text(
                    text = "→ $wsPreview",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Token
            OutlinedTextField(
                value = token,
                onValueChange = onTokenChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Gateway Token") },
                placeholder = { Text("粘贴 OPENCLAW_GATEWAY_TOKEN") },
                leadingIcon = { Icon(Icons.Default.Key, null) },
                enabled = !isPairing,
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onConnect,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isPairing
                        && gatewayUrl.isNotBlank() && urlValid
                        && token.isNotBlank()
            ) {
                if (isPairing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("连接")
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// 配对模式
// ─────────────────────────────────────────────────────────────

@Composable
private fun PairingModeContent(
    viewModel: PairingViewModel,
    state: com.openclaw.clawchat.ui.state.PairingState,
    context: Context
) {
    // 初始化密钥
    LaunchedEffect(Unit) {
        viewModel.initializePairing()
    }

    // 设备信息卡片
    DeviceInfoCard(
        deviceId = state.deviceId ?: "生成中...",
        publicKey = state.publicKey,
        isInitializing = state.isInitializing,
        onCopyDeviceId = { copyToClipboard(context, "设备 ID", state.deviceId ?: "") },
        onCopyPublicKey = { copyToClipboard(context, "公钥", state.publicKey ?: "") }
    )

    Spacer(modifier = Modifier.height(16.dp))

    // Gateway 地址
    GatewayUrlInput(
        value = state.gatewayUrl,
        onValueChange = { viewModel.setGatewayUrl(it) },
        enabled = !state.isPairing && !state.isInitializing
    )

    Spacer(modifier = Modifier.height(16.dp))

    Button(
        onClick = { viewModel.startPairing() },
        modifier = Modifier.fillMaxWidth(),
        enabled = !state.isPairing && !state.isInitializing
                && state.gatewayUrl.isNotBlank()
                && GatewayUrlUtil.isValidInput(state.gatewayUrl)
    ) {
        if (state.isPairing) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text("开始配对")
    }

    Spacer(modifier = Modifier.height(16.dp))

    // 帮助文本
    PairingHelpText()
}

// ─────────────────────────────────────────────────────────────
// Setup Code 模式
// ─────────────────────────────────────────────────────────────

// ─────────────────────────────────────────────────────────────
// 共用组件
// ─────────────────────────────────────────────────────────────

@Composable
private fun DeviceInfoCard(
    deviceId: String,
    publicKey: String?,
    isInitializing: Boolean,
    onCopyDeviceId: () -> Unit,
    onCopyPublicKey: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                text = "设备信息",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (isInitializing) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("正在生成 Ed25519 设备密钥...", style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                // 设备 ID
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("设备 ID", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(deviceId, style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace)
                    }
                    IconButton(onClick = onCopyDeviceId) {
                        Icon(Icons.Default.ContentCopy, "复制",
                            tint = MaterialTheme.colorScheme.primary)
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // 公钥
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("公钥 (Ed25519)", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = publicKey?.let { "${it.take(30)}..." } ?: "无",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace, maxLines = 2
                        )
                    }
                    IconButton(onClick = onCopyPublicKey) {
                        Icon(Icons.Default.ContentCopy, "复制",
                            tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
private fun GatewayUrlInput(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean
) {
    val isValid = value.isBlank() || GatewayUrlUtil.isValidInput(value)
    val wsPreview = if (value.isNotBlank() && isValid) {
        GatewayUrlUtil.normalizeToWebSocketUrl(value)
    } else null

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Gateway 地址") },
        placeholder = { Text("192.168.0.213:18789") },
        leadingIcon = { Icon(Icons.Default.Link, null) },
        enabled = enabled,
        singleLine = true,
        isError = value.isNotBlank() && !isValid,
        supportingText = {
            when {
                value.isNotBlank() && !isValid -> Text("请输入有效的地址")
                wsPreview != null -> Text("→ $wsPreview", fontFamily = FontFamily.Monospace)
            }
        }
    )
}

@Composable
private fun PairingStatusIndicator(
    status: PairingStatus,
    onRetry: () -> Unit,
    onCancel: () -> Unit
) {
    when (status) {
        is PairingStatus.WaitingForApproval -> {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.MoreTime, null, Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onTertiaryContainer)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("等待管理员批准", style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("请在终端运行：openclaw devices approve",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onTertiaryContainer)
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onCancel) { Text("取消") }
                        Button(onClick = onRetry) { Text("重试") }
                    }
                }
            }
        }

        is PairingStatus.Approved -> {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.CheckCircle, null, Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("连接成功！", style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
        }

        is PairingStatus.Error, is PairingStatus.Rejected, is PairingStatus.Timeout -> {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.Error, null, Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onErrorContainer)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = when (status) {
                            is PairingStatus.Error -> status.message
                            is PairingStatus.Rejected -> "配对已被拒绝"
                            is PairingStatus.Timeout -> "连接超时"
                            else -> "连接失败"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onCancel) { Text("返回") }
                        Button(onClick = onRetry) { Text("重试") }
                    }
                }
            }
        }

        else -> {} // Initializing — 不显示
    }
}

@Composable
private fun PairingHelpText() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("如何配对？", style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(modifier = Modifier.height(12.dp))
            HelpStep("1", "确保手机和网关在同一网络")
            HelpStep("2", "输入网关地址并点击开始配对")
            HelpStep("3", "在网关终端运行批准命令")
            HelpStep("4", "等待配对完成")
        }
    }
}

@Composable
private fun HelpStep(number: String, text: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Box(
            modifier = Modifier.size(24.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Text(number, style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimary)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f))
    }
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
}

// ─────────────────────────────────────────────────────────────
// 证书确认对话框
// ─────────────────────────────────────────────────────────────

/**
 * 证书确认对话框（SSH 风格 TOFU）
 *
 * 首次连接或证书变更时显示，要求用户手动确认指纹
 */
@Composable
private fun CertificateConfirmationDialog(
    hostname: String,
    fingerprint: String,
    isMismatch: Boolean,
    storedFingerprint: String?,
    onTrust: () -> Unit,
    onReject: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { }, // 不允许点击外部关闭
        icon = {
            Icon(
                if (isMismatch) Icons.Default.Warning else Icons.Default.Security,
                null,
                tint = if (isMismatch) MaterialTheme.colorScheme.error
                       else MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = if (isMismatch) "⚠️ 证书已变更" else "🔐 首次连接",
                color = if (isMismatch) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column {
                Text(
                    text = "正在连接：$hostname",
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (isMismatch && storedFingerprint != null) {
                    Text(
                        text = "原证书指纹：",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = storedFingerprint.formatFingerprint(),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Text(
                    text = if (isMismatch) "新证书指纹：" else "证书指纹：",
                    style = MaterialTheme.typography.labelMedium
                )
                Text(
                    text = fingerprint.formatFingerprint(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = if (isMismatch)
                        "⚠️ 这可能是中间人攻击，也可能是服务器证书正常更新。\n" +
                        "请联系管理员确认后再继续。"
                    else
                        "ℹ️ 这是您首次连接此服务器。\n" +
                        "请通过安全渠道（如管理员提供的二维码或面对面）验证指纹。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = onTrust) {
                Text(if (isMismatch) "信任新证书" else "信任并继续")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onReject,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("取消连接")
            }
        }
    )
}

/**
 * 格式化指纹为人类可读格式
 */
private fun String.formatFingerprint(): String {
    return this
        .replace(":", "")
        .chunked(8)
        .joinToString(" ") { it.chunked(2).joinToString(":") }
}
