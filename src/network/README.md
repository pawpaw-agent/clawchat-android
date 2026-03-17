# ClawChat Network Layer

Android 网络层模块，负责与 OpenClaw Gateway 的 WebSocket 通信。

## 模块结构

```
src/network/
├── WebSocketService.kt          # WebSocket 服务接口
├── OkHttpWebSocketService.kt    # OkHttp 实现（含自动重连）
├── GatewayMessage.kt            # 消息格式定义与解析
├── TailscaleManager.kt          # Tailscale 连接管理
├── NetworkModule.kt             # Hilt 依赖注入
├── SignatureInterceptor.kt      # 请求签名拦截器
├── NetworkErrorHandler.kt       # 错误处理与重试
└── README.md                    # 本文档
```

## 核心功能

### 1. WebSocketService

WebSocket 连接管理服务：

```kotlin
@HiltViewModel
class MainViewModel @Inject constructor(
    private val webSocketService: WebSocketService
) : ViewModel() {
    
    init {
        viewModelScope.launch {
            // 监听连接状态
            webSocketService.connectionState.collect { state ->
                when (state) {
                    is Connected -> { /* 已连接 */ }
                    is Connecting -> { /* 连接中 */ }
                    is Disconnected -> { /* 已断开 */ }
                    is Error -> { /* 错误处理 */ }
                }
            }
        }
        
        viewModelScope.launch {
            // 监听接收的消息
            webSocketService.incomingMessages.collect { message ->
                when (message) {
                    is SystemEvent -> { /* 系统事件 */ }
                    is AssistantMessage -> { /* 助手消息 */ }
                    is Error -> { /* 错误消息 */ }
                }
            }
        }
    }
    
    fun connect() {
        viewModelScope.launch {
            webSocketService.connect("ws://192.168.1.100:18789/ws", token = null)
        }
    }
    
    fun sendMessage(sessionId: String, content: String) {
        viewModelScope.launch {
            val message = GatewayMessage.UserMessage(sessionId, content)
            webSocketService.send(message)
        }
    }
}
```

### 2. 自动重连机制

内置指数退避重连：

- 初始延迟：1 秒
- 退避因子：2.0
- 最大延迟：30 秒
- 自动在连接失败时触发

```kotlin
// 重连是自动的，无需手动调用
// 当 WebSocket 失败时，会自动调度重连
```

### 3. Tailscale 支持

通过 TailscaleManager 访问 Tailnet 网络：

```kotlin
@HiltViewModel
class ConnectionViewModel @Inject constructor(
    private val tailscaleManager: TailscaleManager
) : ViewModel() {
    
    fun connectViaTailscale() {
        viewModelScope.launch {
            // 检查 Tailscale 连接
            if (tailscaleManager.isTailscaleConnected()) {
                // 方式 1: 使用 MagicDNS
                val ip = tailscaleManager.resolveMagicDns("my-gateway.tailnet.ts.net")
                
                // 方式 2: 使用 IP 地址
                val tailscaleIp = tailscaleManager.getTailscaleIpAddress()
                
                // 构建 URL
                val url = tailscaleManager.buildTailscaleUrl(
                    tailnetName = "mytailnet",
                    deviceName = "gateway",
                    port = 18789
                )
                
                webSocketService.connect(url, token = null)
            }
        }
    }
    
    // 监控 Tailscale 状态变化
    fun observeTailscale() {
        viewModelScope.launch {
            tailscaleManager.observeTailscaleConnection().collect { connected ->
                // 更新 UI
            }
        }
    }
}
```

### 4. 消息格式

支持的消息类型：

| 类型 | 方向 | 描述 |
|------|------|------|
| `systemEvent` | Gateway → Client | 系统事件（cron 提醒等） |
| `userMessage` | Client → Gateway | 用户发送的消息 |
| `assistantMessage` | Gateway → Client | Agent 响应 |
| `ping` / `pong` | 双向 | 心跳/延迟测量 |
| `error` | Gateway → Client | 错误通知 |

```kotlin
// 发送用户消息
val userMessage = GatewayMessage.UserMessage(
    sessionId = "session-123",
    content = "Hello, ClawChat!",
    attachments = emptyList()
)
webSocketService.send(userMessage)

// 接收系统事件
webSocketService.incomingMessages.collect { message ->
    if (message is GatewayMessage.SystemEvent) {
        println("System event: ${message.text}")
    }
}
```

## 依赖注入

在 `Application` 类中启用 Hilt：

```kotlin
@HiltAndroidApp
class ClawChatApplication : Application()
```

在 `build.gradle.kts` 中添加：

```kotlin
dependencies {
    // OkHttp
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    
    // Kotlinx Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
    
    // Hilt
    implementation("com.google.dagger:hilt-android:2.48.1")
    kapt("com.google.dagger:hilt-compiler:2.48.1")
}
```

## 安全特性

### 请求签名

每个请求自动添加：
- `X-ClawChat-Timestamp`: 时间戳
- `X-ClawChat-Nonce`: 随机数（防重放）
- `X-ClawChat-Signature`: 签名（使用设备私钥）

### 证书固定

生产环境启用 TLS 证书固定：

```kotlin
// 在 NetworkModule.kt 中配置
if (!BuildConfig.DEBUG) {
    val certificatePinner = CertificatePinner.Builder()
        .add("*.openclaw.ai", "sha256/your_cert_pin")
        .build()
}
```

### 日志脱敏

Debug 日志自动脱敏敏感信息：
- Bearer Token
- 签名
- 设备令牌

## 错误处理

使用 `retryWithBackoff` 进行重试：

```kotlin
val result = retryWithBackoff(
    maxRetries = 3,
    initialDelay = 1000,
    maxDelay = 5000
) {
    webSocketService.send(message)
}

result.onFailure { error ->
    val networkError = error.toNetworkError()
    println(networkError.toUserMessage())
}
```

## 使用示例

完整的连接和消息发送流程：

```kotlin
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val webSocketService: WebSocketService,
    private val tailscaleManager: TailscaleManager
) : ViewModel() {
    
    private val _uiState = MutableStateFlow<UiState>(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()
    
    init {
        observeConnectionState()
        observeIncomingMessages()
    }
    
    private fun observeConnectionState() {
        viewModelScope.launch {
            webSocketService.connectionState.collect { state ->
                _uiState.update { it.copy(connectionStatus = state) }
            }
        }
    }
    
    private fun observeIncomingMessages() {
        viewModelScope.launch {
            webSocketService.incomingMessages.collect { message ->
                when (message) {
                    is GatewayMessage.AssistantMessage -> {
                        // 添加到消息列表
                        _uiState.update { 
                            it.copy(messages = it.messages + message.toDomain())
                        }
                    }
                    is GatewayMessage.SystemEvent -> {
                        // 显示系统通知
                        showNotification(message.text)
                    }
                    is GatewayMessage.Error -> {
                        // 显示错误
                        showError(message.message)
                    }
                }
            }
        }
    }
    
    fun connect(gatewayUrl: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            webSocketService.connect(gatewayUrl, token = null)
                .onSuccess {
                    _uiState.update { it.copy(isLoading = false) }
                }
                .onFailure { e ->
                    _uiState.update { 
                        it.copy(
                            isLoading = false,
                            error = e.toNetworkError().toUserMessage()
                        )
                    }
                }
        }
    }
    
    fun sendMessage(sessionId: String, content: String) {
        viewModelScope.launch {
            val message = GatewayMessage.UserMessage(sessionId, content)
            webSocketService.send(message)
        }
    }
    
    fun disconnect() {
        viewModelScope.launch {
            webSocketService.disconnect()
        }
    }
}
```

## 注意事项

1. **线程模型**: 所有网络操作在 `Dispatchers.IO` 执行
2. **生命周期**: ViewModel 销毁时自动取消所有协程
3. **内存管理**: 使用 `SharedFlow` 避免内存泄漏
4. **Tailscale**: 需要 Tailscale Android App 配合使用
