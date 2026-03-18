# Phase 1 重构计划

**日期**: 2026-03-18  
**依据**: `code-audit-phase1.md`  
**目标**: 最小改动使 ClawChat 能连接真实 Gateway

---

## 重构原则

1. **不重写能用的代码** — UI、数据层、安全基础全部保留
2. **修复而非替换** — 新层协议代码方向正确，修 Bug 即可
3. **桥接而非撕裂** — 旧接口 `WebSocketService` 保留，内部委托给新层
4. **一步一步验证** — 每个 Step 结束后都可以编译 + 运行

---

## Step 0: 清理（30 分钟）

### 删除

```
rm -rf src/                          # 旧原型目录（41 文件，7400 行）
```

### 清理 `WebSocketProtocol.kt`

删除以下自创结构（Gateway 不使用）：

```kotlin
// 删除
data class FrameHeader(...)
data class WebSocketFrame(...)
data class ProtocolVersion(...)  // 替换为 const val PROTOCOL_VERSION = 3

// 删除旧 HTTP 头常量
const val TIMESTAMP_HEADER = ...
const val NONCE_HEADER = ...
const val SIGNATURE_HEADER = ...
const val DEVICE_ID_HEADER = ...
```

保留：
```kotlin
object WebSocketProtocol {
    const val PROTOCOL_VERSION = 3
    const val WS_PATH = "/ws"
}
```

---

## Step 1: 补全 SecurityModule（1 天）

### 1.1 添加 `SignedPayload` 数据类

```kotlin
// security/SignedPayload.kt
data class SignedPayload(
    val deviceId: String,          // sha256(raw public key bytes).hex
    val publicKeyBase64Url: String, // raw 32 bytes, base64url-no-pad
    val signature: String,          // ECDSA signature, base64url-no-pad
    val payload: String             // 被签名的原文
)
```

### 1.2 在 `SecurityModule` 中添加方法

```kotlin
// SecurityModule.kt 新增

/**
 * 获取公钥的 raw bytes base64url 编码
 * 
 * Gateway 期望的格式：EC point 的未压缩 X||Y（去掉 0x04 前缀后 64 bytes）
 * 或者 SPKI DER 的 base64url。需根据 Gateway 实际验证确定。
 */
fun getPublicKeyBase64Url(): String {
    val publicKey = keystoreManager.getPublicKeyRawBytes()
    return Base64.encodeToString(publicKey, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
}

/**
 * 构建并签名 v3 payload
 *
 * payload = "v3|{deviceId}|{clientId}|{clientMode}|{role}|{scopesJoined}|{signedAtMs}|{token}|{nonce}|{platform}|{deviceFamily}"
 */
fun signV3Payload(
    nonce: String,
    signedAtMs: Long,
    clientId: String = "openclaw-android",
    clientMode: String = "operator",
    role: String = "operator",
    scopes: List<String> = listOf("operator.read", "operator.write"),
    token: String = "",
    platform: String = "android",
    deviceFamily: String = "phone"
): SignedPayload {
    val deviceId = getDeviceIdFromPublicKey()
    val scopesStr = scopes.joinToString(",")
    
    val payload = "v3|$deviceId|$clientId|$clientMode|$role|$scopesStr|$signedAtMs|$token|$nonce|$platform|$deviceFamily"
    
    val signatureBytes = keystoreManager.signChallenge(payload.toByteArray())
    val signature = Base64.encodeToString(signatureBytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    
    return SignedPayload(
        deviceId = deviceId,
        publicKeyBase64Url = getPublicKeyBase64Url(),
        signature = signature,
        payload = payload
    )
}

/**
 * 从公钥计算 deviceId: sha256(raw public key bytes).hex
 */
private fun getDeviceIdFromPublicKey(): String {
    val rawBytes = keystoreManager.getPublicKeyRawBytes()
    val digest = MessageDigest.getInstance("SHA-256")
    return digest.digest(rawBytes).joinToString("") { "%02x".format(it) }
}
```

### 1.3 在 `KeystoreManager` 中添加方法

```kotlin
// KeystoreManager.kt 新增

/**
 * 获取公钥的 raw bytes（SPKI DER 编码）
 */
fun getPublicKeyRawBytes(): ByteArray {
    val entry = keyStore.getEntry(alias, null) as? KeyStore.PrivateKeyEntry
        ?: throw IllegalStateException("Key pair not found")
    return entry.certificate.publicKey.encoded
}
```

---

## Step 2: 修复 GatewayConnection 握手流程（2 天）

### 2.1 修复 hello-ok 匹配

**问题**: connect 请求发送后未追踪，导致 res 响应无法匹配。

```kotlin
// GatewayConnection.kt — handleConnectChallenge()

private suspend fun handleConnectChallenge(event: EventFrame) {
    val challenge = event.parseConnectChallengePayload() ?: return
    
    authHandler.handleChallenge(challenge)
    val connectRequest = authHandler.buildConnectRequest()
    
    val requestFrame = RequestFrame(
        id = RequestIdGenerator.generateRequestId(),
        method = "connect",
        params = buildConnectParams(connectRequest)
    )
    
    // ✅ 修复：追踪请求，等待 res 响应
    val deferred = requestTracker.trackRequest(requestFrame.id, "connect")
    
    webSocket?.send(json.encodeToString(requestFrame))
    
    // 在协程中等待响应
    appScope.launch {
        try {
            val response = withTimeout(AUTH_TIMEOUT_MS) { deferred.await() }
            if (response.ok) {
                handleConnectResponse(response)
            } else {
                handleConnectError(response)
            }
        } catch (e: Exception) {
            _connectionState.value = WebSocketConnectionState.Error(e)
        }
    }
}

private fun handleConnectResponse(response: ResponseFrame) {
    // 解析 hello-ok payload
    // payload.auth.deviceToken
    val payload = response.payload?.jsonObject
    val auth = payload?.get("auth")?.jsonObject
    val deviceToken = auth?.get("deviceToken")?.jsonPrimitive?.content
    
    if (deviceToken != null) {
        securityModule.completePairing(deviceToken)
    }
    
    _connectionState.value = WebSocketConnectionState.Connected
}
```

### 2.2 修复 `handleIncomingMessage` — 删除 `connect.ok` 分支

```kotlin
private fun handleIncomingMessage(text: String) {
    val jsonElement = json.parseToJsonElement(text)
    val type = jsonElement.jsonObject["type"]?.jsonPrimitive?.content
    
    when (type) {
        "res" -> {
            val response = json.decodeFromString<ResponseFrame>(text)
            appScope.launch { requestTracker.completeRequest(response) }
        }
        "event" -> {
            val event = json.decodeFromString<EventFrame>(text)
            handleEvent(event)
        }
        // 不再需要 else 分支的旧格式兼容
    }
}
```

### 2.3 补全 `buildConnectParams`

```kotlin
private fun buildConnectParams(request: ConnectRequest): Map<String, JsonElement> {
    return mapOf(
        "minProtocol" to JsonPrimitive(3),
        "maxProtocol" to JsonPrimitive(3),
        "client" to buildJsonObject {
            put("id", request.client.clientId)
            put("version", request.client.clientVersion)
            put("platform", request.client.platform)
            put("mode", "operator")
        },
        "role" to JsonPrimitive(request.role),
        "scopes" to JsonArray(request.scopes.map { JsonPrimitive(it) }),
        "caps" to JsonArray(emptyList()),
        "commands" to JsonArray(emptyList()),
        "permissions" to JsonObject(emptyMap()),
        "auth" to buildJsonObject {
            put("token", request.token ?: "")
        },
        "locale" to JsonPrimitive("zh-CN"),
        "userAgent" to JsonPrimitive("openclaw-android/${request.client.clientVersion}"),
        "device" to buildJsonObject {
            put("id", request.device.id)
            put("publicKey", request.device.publicKey)
            put("signature", request.device.signature)
            put("signedAt", request.device.signedAt)
            put("nonce", request.device.nonce)
        }
    )
}
```

---

## Step 3: 桥接 WebSocketService → GatewayConnection（1 天）

### 方案：让 `OkHttpWebSocketService` 内部委托给 `GatewayConnection`

```kotlin
// OkHttpWebSocketService.kt — 重写为桥接

class OkHttpWebSocketService @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val securityModule: SecurityModule,
    private val appScope: CoroutineScope
) : WebSocketService {
    
    private val gateway = GatewayConnection(okHttpClient, securityModule, appScope)
    
    override val connectionState: StateFlow<WebSocketConnectionState> = gateway.connectionState
    override val incomingMessages: SharedFlow<GatewayMessage> = gateway.incomingMessages
    
    override suspend fun connect(url: String, token: String?): Result<Unit> {
        // token 由 SecurityModule 管理，或通过 GatewayConnection 传入
        return gateway.connect(url)
    }
    
    override suspend fun send(message: GatewayMessage): Result<Unit> {
        // 将旧格式消息转换为标准 req 帧
        return when (message) {
            is GatewayMessage.UserMessage -> {
                gateway.sendMessage(message.sessionId, message.content)
                    .map { Unit }
            }
            is GatewayMessage.Ping -> {
                gateway.ping().map { Unit }
            }
            else -> Result.failure(UnsupportedOperationException("Use GatewayConnection directly"))
        }
    }
    
    override suspend fun disconnect(): Result<Unit> = gateway.disconnect()
    
    override suspend fun measureLatency(): Long? {
        return gateway.ping().getOrNull()
    }
}
```

### Hilt 注入更新

```kotlin
// NetworkModule.kt
@Provides
@Singleton
fun provideWebSocketService(
    okHttpClient: OkHttpClient,
    securityModule: SecurityModule,
    appScope: CoroutineScope
): WebSocketService {
    return OkHttpWebSocketService(okHttpClient, securityModule, appScope)
}
```

---

## Step 4: ViewModel 去 Mock（1 天）

### 4.1 MainViewModel

```kotlin
// 删除 loadSessions() 中的 mock 数据
// 改为从 SessionRepository.observeSessions() 获取

init {
    viewModelScope.launch {
        sessionRepository.observeSessions().collect { sessions ->
            _uiState.update { it.copy(sessions = sessions, isLoading = false) }
        }
    }
}
```

### 4.2 SessionViewModel

```kotlin
// 删除 loadMessages() 中的 mock 数据
// 改为从 MessageRepository.getMessages(sessionId) 获取

private fun loadMessages() {
    viewModelScope.launch {
        messageRepository.getMessages(sessionId).collect { messages ->
            _uiState.update { 
                it.copy(messages = messages.map { it.toMessageUi() }, isLoading = false) 
            }
        }
    }
}
```

### 4.3 事件通道修复

```kotlin
// MainViewModel — 改用 Channel
private val _events = Channel<UiEvent>(Channel.BUFFERED)
val events: Flow<UiEvent> = _events.receiveAsFlow()
```

---

## Step 5: 小修复合集（半天）

| 文件 | 修复 |
|------|------|
| `NotificationManager.kt` | 构造函数添加 `@ApplicationContext` |
| `MessageRepository.kt` | `getMessageById()` 添加 DAO 方法并实现 |
| `GatewayRepository.kt` | `clearGatewayConfig()` 改为 `remove(KEY_GATEWAY_URL)` |
| `SessionRepository.kt` | `clearTerminatedSessions()` 改为单条 `@Query("DELETE FROM sessions WHERE isActive = false")` |
| `NetworkModule.kt` | 证书固定：添加 TODO 标记 + 运行时检查（debug 跳过，release 失败） |
| `ChallengeResponseAuth.kt` | 注释中 Ed25519 → ECDSA secp256r1 |

---

## 时间线

```
Step 0: 清理                    [0.5 天]
Step 1: SecurityModule 补全      [1 天]
Step 2: GatewayConnection 修复   [2 天]
Step 3: WebSocketService 桥接    [1 天]
Step 4: ViewModel 去 Mock        [1 天]
Step 5: 小修复合集               [0.5 天]
─────────────────────────────────────
总计                              6 天
```

---

## 验证里程碑

| 里程碑 | 验证方式 | 完成条件 |
|--------|----------|----------|
| Step 0 完成 | `./gradlew assembleDebug` | 编译通过 |
| Step 1 完成 | 单元测试 `signV3Payload()` | 签名格式正确 |
| Step 2 完成 | 连接本地 Gateway | 收到 `hello-ok`，状态变为 Connected |
| Step 3 完成 | PairingViewModel 连接 | Token 模式 + 配对模式均可连接 |
| Step 4 完成 | 主界面显示真实会话列表 | 数据来自 Gateway 而非 mock |
| Step 5 完成 | CI 通过 + 全量单元测试 | 无回归 |

---

## 不在本轮范围

以下改进重要但**不阻塞连接 Gateway**，留到 Phase 2：

- Domain 层 (UseCase) 抽象
- 消息分页 (Paging 3)
- Compose UI 测试
- ProGuard 规则
- DeviceFingerprint 隐私合规
- 多 Gateway 配置支持
