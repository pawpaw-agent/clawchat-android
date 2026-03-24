# ClawChat Android 流程交互逻辑审查报告

> **审查日期**: 2026-03-24  
> **审查范围**: 启动流程、会话列表、会话详情、实时通信  
> **项目路径**: `/home/xsj/.openclaw/workspace-ClawChat`

---

## 目录

1. [执行摘要](#1-执行摘要)
2. [当前交互流程梳理](#2-当前交互流程梳理)
3. [发现的问题](#3-发现的问题)
4. [改进建议](#4-改进建议)
5. [优先级排序](#5-优先级排序)

---

## 1. 执行摘要

### 整体评估

| 维度 | 评分 | 说明 |
|------|------|------|
| 架构清晰度 | ⭐⭐⭐⭐ | MVVM + 单向数据流，结构清晰 |
| 代码质量 | ⭐⭐⭐⭐ | 代码规范，有测试覆盖 |
| 错误处理 | ⭐⭐⭐ | 基本完善，但有遗漏场景 |
| 用户体验 | ⭐⭐⭐ | 核心流程完整，细节待优化 |
| 可维护性 | ⭐⭐⭐⭐ | 模块化良好，依赖清晰 |

### 关键发现

- ✅ **优点**: 协议 v3 实现完整，消息流对标 webchat，离线缓存支持
- ⚠️ **问题**: 启动闪屏问题、状态同步边界情况、重连策略不够健壮
- 🔴 **风险**: 消息去重逻辑可能导致消息丢失

---

## 2. 当前交互流程梳理

### 2.1 启动流程

```
┌─────────────────────────────────────────────────────────────┐
│                     应用启动流程                              │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  MainActivity.onCreate()                                    │
│       │                                                     │
│       ├─ installSplashScreen()                              │
│       ├─ enableEdgeToEdge()                                 │
│       └─ setContent { ClawChatNavHost() }                   │
│               │                                             │
│               ├─ 读取 isPaired 状态                          │
│               │   (EncryptedStorage.isPaired())             │
│               │                                             │
│               └─ 决定初始目的地                               │
│                   ├─ paired=false → "pairing"               │
│                   └─ paired=true  → "main"                  │
│                                                             │
│  ─────────────────────────────────────────────────────────  │
│                                                             │
│  【如果导航到 "main"】                                        │
│                                                             │
│  MainViewModel.init {                                       │
│       1. loadSessionsFromCache() ← 从 Room 加载缓存         │
│       2. observeConnectionState() ← 监听连接状态            │
│       3. autoConnectIfNeeded()  ← 自动连接 Gateway          │
│  }                                                          │
│                                                             │
│  autoConnectIfNeeded() {                                    │
│       if (!isPaired) return                                 │
│       val url = getGatewayUrl()                             │
│       val token = getDeviceToken()                          │
│       gateway.connect(wsUrl, token)                         │
│  }                                                          │
│                                                             │
│  连接成功后:                                                  │
│       loadSessionsFromGateway() → 同步到 Room               │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

**代码引用**:
- `MainActivity.kt:46-60` - 导航起点决策
- `MainViewModel.kt:51-92` - 初始化与自动连接

### 2.2 配对流程

```
┌─────────────────────────────────────────────────────────────┐
│                     配对流程                                  │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  PairingScreen                                              │
│       │                                                     │
│       ├─ 模式选择: TOKEN | PAIRING                          │
│       │                                                     │
│       └─ [TOKEN 模式]                                        │
│           ├─ 输入 Gateway URL                                │
│           ├─ 输入 Token                                      │
│           └─ connectWithToken()                             │
│               │                                             │
│               └─ GatewayConnection.connect(url, token)      │
│                   │                                         │
│                   ├─ WebSocket 握手                          │
│                   ├─ 收到 connect.challenge                  │
│                   ├─ Ed25519 签名响应                        │
│                   └─ 收到 hello-ok                           │
│                       │                                     │
│                       └─ SecurityModule.completePairing()   │
│                           └─ 保存 deviceToken               │
│                                                             │
│  ─────────────────────────────────────────────────────────  │
│                                                             │
│  [PAIRING 模式]                                              │
│       ├─ initializePairing() → 生成 Ed25519 密钥对          │
│       ├─ 显示设备 ID / 公钥                                   │
│       └─ startPairing()                                     │
│           └─ 等待管理员批准 (device.pairing.approved)        │
│                                                             │
│  ─────────────────────────────────────────────────────────  │
│                                                             │
│  [证书 TOFU 流程]                                            │
│       ├─ 首次连接 → 显示指纹确认对话框                        │
│       ├─ 证书变更 → 警告对话框 (isMismatch=true)             │
│       └─ 用户确认 → 保存指纹 → 继续连接                       │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

**代码引用**:
- `PairingViewModel.kt:83-133` - Token 连接
- `PairingScreen.kt:174-230` - 证书确认对话框
- `GatewayConnection.kt:140-167` - 证书事件处理

### 2.3 会话列表 (MainScreen)

```
┌─────────────────────────────────────────────────────────────┐
│                   会话列表流程                                │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  MainScreen                                                 │
│       │                                                     │
│       ├─ [未连接状态]                                        │
│       │   └─ NotConnectedContent                            │
│       │       └─ "返回配对" 按钮                             │
│       │                                                     │
│       ├─ [已连接 + 空会话]                                   │
│       │   └─ EmptySessionList                               │
│       │       └─ FAB: 创建新会话                             │
│       │                                                     │
│       └─ [已连接 + 有会话]                                   │
│           ├─ 搜索栏                                          │
│           ├─ SessionList (LazyColumn)                       │
│           │   └─ SessionItem                                │
│           │       ├─ 点击 → selectSession() → 导航到详情     │
│           │       └─ 长按 → SessionOptionsDialog             │
│           │           ├─ 重命名                              │
│           │           ├─ 暂停/恢复                           │
│           │           ├─ 终止                                │
│           │           └─ 删除                                │
│           └─ FAB: 创建新会话                                 │
│                                                             │
│  ─────────────────────────────────────────────────────────  │
│                                                             │
│  会话操作:                                                   │
│                                                             │
│  selectSession(sessionId) {                                 │
│       _uiState.update { it.copy(currentSession = session) } │
│       _events.send(NavigateToSession(sessionId))            │
│  }                                                          │
│                                                             │
│  deleteSession(sessionId) {                                 │
│       gateway.call("sessions.delete", ...)                  │
│       sessionRepository.deleteSession(sessionId)            │
│       _uiState.update { sessions.filter { ... } }           │
│  }                                                          │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

**代码引用**:
- `MainScreen.kt:31-82` - 状态判断与 UI 渲染
- `MainViewModel.kt:160-180` - 会话操作

### 2.4 会话详情 (SessionScreen)

```
┌─────────────────────────────────────────────────────────────┐
│                   会话详情流程                                │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  SessionScreen                                              │
│       │                                                     │
│       ├─ LaunchedEffect(sessionId)                          │
│       │   └─ viewModel.setSessionId(sessionId)              │
│       │       ├─ loadMessageHistory(sessionId)              │
│       │       │   ├─ 从 Room 加载本地缓存                    │
│       │       │   └─ 从 Gateway 拉取历史消息                 │
│       │       └─ sessionsPatch(verboseLevel="full")         │
│       │                                                     │
│       ├─ 消息列表 (MessageGroupList)                         │
│       │   ├─ chatMessages (历史消息)                         │
│       │   ├─ chatStreamSegments (工具执行前的文本段)         │
│       │   ├─ chatToolMessages (工具消息)                     │
│       │   └─ chatStream (当前流式文本)                       │
│       │                                                     │
│       ├─ 输入栏 (MessageInputBar)                            │
│       │   ├─ 文本输入                                        │
│       │   ├─ 附件添加                                        │
│       │   ├─ 斜杠命令补全                                    │
│       │   └─ 发送按钮                                        │
│       │                                                     │
│       └─ 错误提示 (ErrorSnackbar)                            │
│                                                             │
│  ─────────────────────────────────────────────────────────  │
│                                                             │
│  消息发送流程:                                               │
│                                                             │
│  sendMessage(message) {                                     │
│       // 1. 构建用户消息                                     │
│       val userMessage = MessageUi(...)                      │
│       _state.update {                                       │
│           it.copy(                                          │
│               chatMessages = it.chatMessages + userMessage, │
│               isSending = true,                             │
│               isLoading = true                              │
│           )                                                 │
│       }                                                     │
│                                                             │
│       // 2. 发送到 Gateway                                   │
│       gateway.chatSend(sessionId, message, attachments)     │
│  }                                                          │
│                                                             │
│  ─────────────────────────────────────────────────────────  │
│                                                             │
│  消息接收流程:                                               │
│                                                             │
│  observeIncomingMessages() {                                │
│       gateway.incomingMessages.collect { rawJson ->         │
│           handleIncomingFrame(rawJson)                      │
│       }                                                     │
│  }                                                          │
│                                                             │
│  handleIncomingFrame() {                                    │
│       when (event) {                                        │
│           "chat" -> handleChatEvent()                       │
│               ├─ state="delta" → 更新 chatStream            │
│               ├─ state="final" → 添加到 chatMessages        │
│               ├─ state="aborted" → 处理中断                 │
│               └─ state="error" → 显示错误                   │
│                                                             │
│           "agent" -> handleAgentEvent()                     │
│               └─ stream="tool" → 更新工具状态               │
│       }                                                     │
│  }                                                          │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

**代码引用**:
- `SessionScreen.kt:30-115` - 组件结构
- `SessionViewModel.kt:124-240` - 消息处理逻辑

### 2.5 实时通信

```
┌─────────────────────────────────────────────────────────────┐
│                   WebSocket 通信架构                          │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  GatewayConnection                                          │
│       │                                                     │
│       ├─ 连接管理                                            │
│       │   ├─ connect(url, token)                            │
│       │   ├─ disconnect()                                   │
│       │   └─ reconnect (指数退避, 最大 15 次)                │
│       │                                                     │
│       ├─ 状态流                                              │
│       │   ├─ connectionState: StateFlow<ConnectionState>    │
│       │   ├─ incomingMessages: SharedFlow<String>           │
│       │   └─ certificateEvent: SharedFlow<CertificateEvent> │
│       │                                                     │
│       ├─ 请求追踪                                            │
│       │   └─ RequestTracker (30s 超时)                      │
│       │                                                     │
│       └─ 心跳                                                │
│           └─ ping() 每 30 秒                                │
│                                                             │
│  ─────────────────────────────────────────────────────────  │
│                                                             │
│  握手协议 (v3):                                              │
│                                                             │
│  1. WebSocket 连接建立                                       │
│  2. 收到 { type: "event", event: "connect.challenge" }      │
│  3. 构建签名载荷:                                            │
│     {                                                       │
│       device: { id, publicKey, signature, nonce },          │
│       auth: { token } | { pairingToken }                    │
│     }                                                       │
│  4. 发送 { type: "req", method: "connect", ... }            │
│  5. 收到 { type: "res", ok: true, ... } (hello-ok)          │
│                                                             │
│  ─────────────────────────────────────────────────────────  │
│                                                             │
│  重连策略:                                                   │
│                                                             │
│  scheduleReconnect() {                                      │
│       val delay = min(                                      │
│           INITIAL_DELAY * backoff^attempt,                  │
│           MAX_DELAY                                         │
│       )                                                     │
│       // 1s → 2s → 4s → 8s → 16s → 30s (max)               │
│       if (attempt > 15) {                                   │
│           connectionState = Error("Max reconnect attempts") │
│       }                                                     │
│  }                                                          │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

**代码引用**:
- `GatewayConnection.kt:68-115` - 连接与握手
- `GatewayConnection.kt:300-330` - 重连策略
- `RequestTracker.kt` - 请求追踪

---

## 3. 发现的问题

### 3.1 🔴 高优先级问题

#### 问题 1: 启动闪屏问题

**描述**: 应用启动时，用户可能看到短暂的黑屏或白屏，因为 `startDestination` 取决于异步加载的 `isPaired` 状态。

**代码位置**: `MainActivity.kt:55-58`

```kotlin
val isPairedState = mainViewModel.isPaired.collectAsStateWithLifecycle(initialValue = false)
val startDestination = if (isPairedState.value) "main" else "pairing"
```

**问题分析**:
- `isPaired` 初始值为 `false`，但实际可能是 `true`
- 如果用户已配对，会先看到 PairingScreen 然后立即切换到 MainScreen
- 造成视觉闪烁

**影响**: 用户体验差，看起来像"闪退"

---

#### 问题 2: 消息去重可能导致消息丢失

**描述**: `EventDeduplicator` 和 `SequenceManager` 可能错误地丢弃有效消息。

**代码位置**: `GatewayConnection.kt:195-220`

```kotlin
when (sequenceManager.checkSequence(seq)) {
    is SequenceManager.SequenceResult.Duplicate,
    is SequenceManager.SequenceResult.Old -> return@launch  // 直接丢弃
    else -> {}
}
if (eventDeduplicator.isAlreadySeen(eventId, seq)) return@launch
```

**问题分析**:
- 如果 `seq` 为 `null`（某些事件可能没有 seq），跳过检查
- 如果客户端重启，内存中的去重状态丢失，可能导致重复消息
- 但如果服务端重发事件，客户端可能因为 seq 已过期而丢弃

**影响**: 潜在的消息丢失或重复显示

---

#### 问题 3: 离线状态处理不完整

**描述**: 当网络断开时，用户可以继续输入和"发送"消息，但消息实际上不会发送。

**代码位置**: `SessionScreen.kt:97-105`, `SessionViewModel.kt:280-320`

```kotlin
MessageInputBar(
    enabled = state.connectionStatus is ConnectionStatus.Connected && !state.isSending,
    // ...
)
```

**问题分析**:
- 输入框会被禁用，但用户可能不知道为什么
- 没有显示"离线模式"提示
- 没有消息队列在恢复连接后重发

**影响**: 用户困惑，不知道消息是否发送成功

---

### 3.2 ⚠️ 中优先级问题

#### 问题 4: 重连策略过于激进

**描述**: 最大重连次数为 15 次，但没有用户可取消的选项。

**代码位置**: `GatewayConnection.kt:310-330`

```kotlin
if (reconnectAttempt >= MAX_RECONNECT_ATTEMPTS) {
    _connectionState.value = WebSocketConnectionState.Error(...)
}
```

**问题分析**:
- 用户可能在切换网络，持续重连浪费电量
- 没有提供"稍后重试"选项
- 重连期间没有 UI 指示

**影响**: 电量消耗，用户无法控制

---

#### 问题 5: 会话列表状态同步延迟

**描述**: 会话列表先显示缓存，然后被 Gateway 数据覆盖，可能导致视觉跳跃。

**代码位置**: `MainViewModel.kt:69-78`, `MainViewModel.kt:130-175`

```kotlin
private fun loadSessionsFromCache() {
    sessionRepository.observeSessions().collect { cachedSessions ->
        // 仅在没有 Gateway 数据时使用缓存
        if (_uiState.value.connectionStatus !is ConnectionStatus.Connected) {
            _uiState.update { it.copy(sessions = cachedSessions) }
        }
    }
}
```

**问题分析**:
- 缓存数据可能是过期的
- 连接成功后立即覆盖，可能造成列表重排
- 没有平滑过渡动画

**影响**: 视觉闪烁，用户体验差

---

#### 问题 6: 消息历史加载竞态条件

**描述**: `loadMessageHistory` 同时从本地数据库和 Gateway 加载，可能导致重复或顺序错乱。

**代码位置**: `SessionViewModel.kt:350-400`

```kotlin
loadMessagesJob = viewModelScope.launch {
    launch { messageRepository.observeMessages(sessionId).collect { ... } }
    launch { /* 从 Gateway 加载 */ }
}
```

**问题分析**:
- 两个协程并发执行，更新顺序不确定
- Gateway 消息保存到 Room 后，会触发 `observeMessages` 再次更新 UI
- 可能导致消息重复显示或顺序错乱

**影响**: UI 显示异常

---

### 3.3 ℹ️ 低优先级问题

#### 问题 7: 缺少网络状态指示

**描述**: 连接状态只显示在 TopAppBar 的图标，没有详细信息。

**代码位置**: `MainScreen.kt:184-200`

```kotlin
@Composable
private fun ConnectionStatusIcon(status: ConnectionStatus) {
    val (icon, color) = when (status) { ... }
    Icon(imageVector = icon, contentDescription = "连接状态", tint = color)
}
```

**问题分析**:
- 用户可能不知道当前网络状态
- 没有延迟显示
- 没有连接质量指示

**影响**: 信息不足，用户困惑

---

#### 问题 8: 搜索功能不完整

**描述**: 会话搜索只在本地过滤，没有后端搜索支持。

**代码位置**: `MainScreen.kt:280-290`

```kotlin
private fun filterSessions(sessions: List<SessionUi>, query: String): List<SessionUi> {
    // 仅本地过滤
}
```

**问题分析**:
- 无法搜索消息内容
- 无法搜索历史会话（如果不在列表中）

**影响**: 功能受限

---

#### 问题 9: 会话状态显示不完整

**描述**: 会话状态只有 RUNNING/PAUSED/TERMINATED，没有显示"正在输入"等实时状态。

**代码位置**: `UiState.kt:12-20`

```kotlin
enum class SessionStatus {
    RUNNING,
    PAUSED,
    TERMINATED
}
```

**问题分析**:
- 没有 `thinking` 状态的实时显示
- 没有"正在输入"指示

**影响**: 用户体验不够流畅

---

## 4. 改进建议

### 4.1 启动流程优化

#### 建议 1: 添加启动加载状态

```kotlin
// MainActivity.kt
@Composable
fun ClawChatNavHost() {
    val navController = rememberNavController()
    val isPaired by mainViewModel.isPaired.collectAsStateWithLifecycle(initialValue = null)
    
    when (isPaired) {
        null -> LoadingScreen()  // 显示加载动画
        false -> NavHost(startDestination = "pairing") { ... }
        true -> NavHost(startDestination = "main") { ... }
    }
}
```

#### 建议 2: 预加载关键数据

```kotlin
// MainViewModel.kt
init {
    // 在显示 UI 前预加载
    viewModelScope.launch {
        val isPaired = encryptedStorage.isPaired()
        if (isPaired) {
            // 预加载会话列表到缓存
            sessionRepository.preloadSessions()
        }
        _isPaired.value = isPaired
    }
}
```

### 4.2 消息处理优化

#### 建议 3: 改进消息去重逻辑

```kotlin
// GatewayConnection.kt
private fun handleEventFrame(obj: JsonObject, rawText: String) {
    val seq = obj["seq"]?.jsonPrimitive?.content?.toIntOrNull()
    val eventId = obj["id"]?.jsonPrimitive?.content 
        ?: obj["stateVersion"]?.let { deriveEventId(it) }
        ?: generateTempId()
    
    // 使用持久化存储
    if (eventDeduplicator.isAlreadySeen(eventId, seq)) {
        Log.w(TAG, "Duplicate event: $eventId, but still processing")
        // 不要直接丢弃，而是标记为重复
    }
    
    // 正常处理事件
    _incomingMessages.emit(rawText)
}
```

#### 建议 4: 添加消息队列

```kotlin
// SessionViewModel.kt
private val pendingMessages = Channel<PendingMessage>(Channel.UNLIMITED)

fun sendMessage(message: String) {
    val pending = PendingMessage(message, timestamp)
    
    if (connectionStatus !is ConnectionStatus.Connected) {
        // 保存到队列，恢复连接后发送
        pendingMessages.trySend(pending)
        _state.update { it.copy(
            chatMessages = it.chatMessages + pending.toMessageUi(status = MessageStatus.PENDING)
        )}
    } else {
        // 正常发送
        gateway.chatSend(sessionId, message)
    }
}

private fun observeConnectionAndFlushPending() {
    viewModelScope.launch {
        gateway.connectionState.collect { state ->
            if (state is WebSocketConnectionState.Connected) {
                flushPendingMessages()
            }
        }
    }
}
```

### 4.3 网络体验优化

#### 建议 5: 添加连接状态卡片

```kotlin
// MainScreen.kt
@Composable
fun ConnectionStatusCard(status: ConnectionStatus) {
    when (status) {
        is ConnectionStatus.Connecting -> {
            Card(colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer
            )) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("正在连接...")
                }
            }
        }
        is ConnectionStatus.Error -> {
            Card(colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            )) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Error, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("连接失败：${status.message}")
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = { viewModel.connectToGateway() }) {
                        Text("重试")
                    }
                }
            }
        }
        // ...
    }
}
```

#### 建议 6: 改进重连策略

```kotlin
// GatewayConnection.kt
data class ReconnectConfig(
    val maxAttempts: Int = 15,
    val initialDelay: Long = 1000,
    val maxDelay: Long = 30000,
    val enableUserCancel: Boolean = true
)

private var userCancelledReconnect = false

fun cancelReconnect() {
    userCancelledReconnect = true
    reconnectJob?.cancel()
}

private fun scheduleReconnect(url: String, token: String?) {
    if (userCancelledReconnect) {
        Log.i(TAG, "Reconnect cancelled by user")
        return
    }
    // ... 原有逻辑
}
```

### 4.4 UI/UX 优化

#### 建议 7: 添加离线模式提示

```kotlin
// SessionScreen.kt
if (state.connectionStatus !is ConnectionStatus.Connected) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.CloudOff, null, tint = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.width(8.dp))
            Text("离线模式 - 消息将在恢复连接后发送", color = MaterialTheme.colorScheme.onErrorContainer)
        }
    }
}
```

#### 建议 8: 平滑会话列表更新

```kotlin
// MainViewModel.kt
private fun loadSessionsFromGateway() {
    viewModelScope.launch {
        val response = gateway.sessionsList()
        val newSessions = parseSessions(response)
        
        // 计算差异，只更新变化的部分
        val currentIds = _uiState.value.sessions.map { it.id }.toSet()
        val newIds = newSessions.map { it.id }.toSet()
        
        if (currentIds != newIds) {
            // 使用动画过渡
            _uiState.update { it.copy(sessions = newSessions) }
        } else {
            // 只更新内容，不触发列表重排
            _uiState.update { it.copy(sessions = newSessions) }
        }
    }
}
```

### 4.5 错误处理优化

#### 建议 9: 统一错误处理

```kotlin
// ErrorHandler.kt
sealed class AppError {
    data class Network(val message: String, val retry: (() -> Unit)?) : AppError()
    data class Auth(val message: String) : AppError()
    data class Validation(val message: String) : AppError()
    data class Unknown(val throwable: Throwable) : AppError()
    
    fun toUserMessage(): String = when (this) {
        is Network -> "网络错误: $message"
        is Auth -> "认证失败，请重新登录"
        is Validation -> message
        is Unknown -> "发生未知错误，请稍后重试"
    }
    
    fun getAction(): ErrorAction? = when (this) {
        is Network -> if (retry != null) ErrorAction("重试", retry!!) else null
        is Auth -> ErrorAction("重新登录") { navigateToPairing() }
        else -> null
    }
}

data class ErrorAction(
    val label: String,
    val action: () -> Unit
)
```

---

## 5. 优先级排序

### 立即修复 (P0)

| 问题 | 影响 | 工作量 |
|------|------|--------|
| 启动闪屏问题 | 用户体验差 | 2h |
| 消息去重竞态 | 数据丢失风险 | 4h |

### 短期优化 (P1)

| 问题 | 影响 | 工作量 |
|------|------|--------|
| 离线状态处理 | 用户困惑 | 3h |
| 重连策略改进 | 电量消耗 | 2h |
| 消息历史竞态 | UI 异常 | 3h |

### 中期改进 (P2)

| 问题 | 影响 | 工作量 |
|------|------|--------|
| 会话列表同步延迟 | 视觉闪烁 | 4h |
| 网络状态指示 | 信息不足 | 2h |
| 错误处理统一 | 可维护性 | 4h |

### 长期优化 (P3)

| 问题 | 影响 | 工作量 |
|------|------|--------|
| 搜索功能增强 | 功能受限 | 8h |
| 实时状态显示 | 体验提升 | 4h |

---

## 附录: 关键代码路径

```
app/src/main/java/com/openclaw/clawchat/
├── MainActivity.kt                    # 应用入口
├── ui/
│   ├── screens/
│   │   ├── MainScreen.kt              # 会话列表 UI
│   │   ├── SessionScreen.kt           # 会话详情 UI
│   │   ├── PairingScreen.kt           # 配对 UI
│   │   └── settings/
│   │       └── SettingsScreen.kt      # 设置 UI
│   └── state/
│       ├── MainViewModel.kt           # 会话列表逻辑
│       ├── SessionViewModel.kt        # 会话详情逻辑
│       ├── PairingViewModel.kt        # 配对逻辑
│       └── UiState.kt                 # 数据模型
├── network/
│   └── protocol/
│       ├── GatewayConnection.kt       # WebSocket 连接
│       ├── RequestFrame.kt            # 请求帧
│       ├── ResponseFrame.kt           # 响应帧
│       └── ChallengeResponseAuth.kt   # 认证握手
├── repository/
│   ├── SessionRepository.kt           # 会话缓存
│   └── MessageRepository.kt           # 消息缓存
└── security/
    └── SecurityModule.kt              # 加密/认证
```

---

**报告生成**: UX Architect Agent  
**审查状态**: ✅ 已完成