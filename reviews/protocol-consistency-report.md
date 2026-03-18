# ClawChat Android 协议一致性检查报告

**审查编号**: protocol-check-001  
**审查日期**: 2026-03-18  
**参考文档**: https://docs.openclaw.ai/gateway/protocol  
**审查范围**: WebSocket 协议实现一致性

---

## 📊 执行摘要

| 检查项 | 状态 | 一致性 |
|--------|------|--------|
| **Connect 握手** | ❌ 不通过 | 0% |
| **消息格式** | ❌ 不通过 | 20% |
| **认证流程** | ⚠️ 部分通过 | 50% |
| **错误处理** | ⚠️ 部分通过 | 40% |
| **整体一致性** | ❌ **不通过** | **35%** |

**关键发现**: 当前实现使用**自定义协议**而非 OpenClaw Gateway 标准协议，存在**严重不兼容**问题。

---

## 1. Connect 握手检查

### 协议规范要求

```
┌──────────────┐                          ┌──────────────┐
│   Gateway    │                          │    Client    │
│   Server     │                          │   (ClawChat) │
└──────┬───────┘                          └──────┬───────┘
       │                                         │
       │  1. connect.challenge (event)           │
       │  {nonce, ts}                            │
       │ ──────────────────────────────────────> │
       │                                         │
       │  2. connect (req)                       │
       │  {                                      │
       │    minProtocol: 3,                      │
       │    maxProtocol: 3,                      │
       │    client: {id, version, platform},     │
       │    role: "operator",                    │
       │    scopes: ["operator.read", ...],      │
       │    device: {id, publicKey, signature,   │
       │             signedAt, nonce},           │
       │    auth: {token}                        │
       │  }                                      │
       │ <────────────────────────────────────── │
       │                                         │
       │  3. hello-ok (res)                      │
       │  {                                      │
       │    protocol: 3,                         │
       │    auth: {deviceToken}                  │
       │  }                                      │
       │ ──────────────────────────────────────> │
       │                                         │
```

### 当前实现

```kotlin
// OkHttpWebSocketService.kt
override suspend fun connect(url: String, token: String?): Result<Unit> {
    // ❌ 1. 没有等待 connect.challenge
    // ❌ 2. 没有发送 connect 请求帧
    // ❌ 3. 使用 HTTP 头进行认证而非 WebSocket 消息
    
    val request = buildWebSocketRequest(url, token)
    webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
        // ...
    })
}

private fun buildWebSocketRequest(url: String, token: String?): Request {
    val timestamp = System.currentTimeMillis()
    val nonce = UUID.randomUUID().toString()  // ❌ 客户端生成 nonce
    val dataToSign = "/ws\n$timestamp\n$nonce"  // ❌ 非标准签名载荷
    val signature = securityModule.signChallenge(dataToSign)
    
    // ❌ 使用 HTTP 头而非 WebSocket 消息帧
    return Request.Builder()
        .url(url)
        .addHeader("X-ClawChat-Timestamp", timestamp.toString())
        .addHeader("X-ClawChat-Nonce", nonce)
        .addHeader("X-ClawChat-Signature", signature)
        .addHeader("Authorization", "Bearer $token")
        .build()
}
```

### 一致性检查

| 要求 | 当前实现 | 状态 |
|------|----------|------|
| 等待 `connect.challenge` | ❌ 未实现 | ❌ |
| 发送 `connect` 请求帧 | ❌ 未实现 | ❌ |
| `minProtocol`/`maxProtocol` = 3 | ❌ 缺失 | ❌ |
| `client` 对象完整 | ❌ 缺失 | ❌ |
| `role` 声明 | ❌ 缺失 | ❌ |
| `scopes` 声明 | ❌ 缺失 | ❌ |
| `device` 对象完整 | ❌ 缺失 | ❌ |
| `device.nonce` 使用服务器 nonce | ❌ 使用客户端 nonce | ❌ |
| 接收 `hello-ok` 响应 | ❌ 未实现 | ❌ |
| 存储 `deviceToken` | ✅ 已实现 | ✅ |

**通过率**: 1/10 = **10%**

---

## 2. 消息格式检查

### 协议规范要求

```json
// Request
{
  "type": "req",
  "id": "unique-request-id",
  "method": "chat.send",
  "params": { ... }
}

// Response
{
  "type": "res",
  "id": "unique-request-id",
  "ok": true,
  "payload": { ... }
}

// Event
{
  "type": "event",
  "event": "session.message",
  "payload": { ... },
  "seq": 123,
  "stateVersion": "v1"
}
```

### 当前实现

```json
// 当前 ClawChat 消息格式
{
  "type": "userMessage",
  "sessionId": "xxx",
  "content": "Hello",
  "timestamp": 1234567890
}

{
  "type": "assistantMessage",
  "sessionId": "xxx",
  "content": "Hi there",
  "model": "qwen3.5-plus",
  "timestamp": 1234567891
}

{
  "type": "systemEvent",
  "text": "Reminder",
  "timestamp": 1234567892
}

{
  "type": "ping",
  "timestamp": 1234567893
}

{
  "type": "pong",
  "timestamp": 1234567894,
  "latency": 50
}
```

### 一致性检查

| 要求 | 当前实现 | 状态 |
|------|----------|------|
| Request 使用 `type: "req"` | ❌ 使用 `userMessage` 等 | ❌ |
| Request 包含 `id` | ❌ 缺失 | ❌ |
| Request 包含 `method` | ❌ 缺失 | ❌ |
| Request 包含 `params` | ❌ 缺失 | ❌ |
| Response 使用 `type: "res"` | ❌ 使用 `assistantMessage` | ❌ |
| Response 包含 `ok` 字段 | ❌ 缺失 | ❌ |
| Response 包含 `payload` 或 `error` | ❌ 缺失 | ❌ |
| Event 使用 `type: "event"` | ❌ 使用 `systemEvent` | ❌ |
| Event 包含 `event` 字段 | ❌ 缺失 | ❌ |
| 支持 `seq` 和 `stateVersion` | ❌ 缺失 | ❌ |
| 心跳使用 `ping`/`pong` | ✅ 已实现 | ✅ |

**通过率**: 1/11 = **9%**

---

## 3. 认证流程检查

### 协议规范要求

```
1. 使用 ECDSA secp256r1 签名
2. 签名载荷包含服务器提供的 nonce
3. 签名 v3 payload 格式:
   {
     "device": {id, publicKey},
     "client": {id, version, platform},
     "role": "operator",
     "scopes": ["operator.read", "operator.write"],
     "token": "",  // 首次配对为空
     "nonce": "<server-nonce>",
     "signedAt": 1234567890,
     "deviceFamily": "phone"
   }
4. 存储和复用 deviceToken
```

### 当前实现

```kotlin
// SecurityModule.kt - ✅ 正确的密钥管理
private val keystoreManager = KeystoreManager(KEYPAIR_ALIAS)  // ECDSA secp256r1

// ❌ 错误的签名载荷
private fun buildWebSocketRequest(url: String, token: String?): Request {
    val dataToSign = "/ws\n$timestamp\n$nonce"  // ❌ 非标准格式
    val signature = securityModule.signChallenge(dataToSign)
    // ...
}

// SecurityModule.kt - ✅ 正确的 deviceToken 存储
fun completePairing(deviceToken: String) {
    encryptedStorage.saveDeviceToken(deviceToken)
}

fun getAuthToken(): String? {
    return encryptedStorage.getDeviceToken()
}
```

### 一致性检查

| 要求 | 当前实现 | 状态 |
|------|----------|------|
| 使用 ECDSA secp256r1 | ✅ KeystoreManager | ✅ |
| 签名包含服务器 nonce | ❌ 使用客户端生成 nonce | ❌ |
| 签名 v3 payload 格式 | ❌ 使用 `/ws\n$timestamp\n$nonce` | ❌ |
| `device.id` 指纹 | ✅ DeviceFingerprint | ✅ |
| `device.publicKey` PEM | ✅ KeystoreManager | ✅ |
| `client.id`/`version`/`platform` | ❌ 缺失 | ❌ |
| `role` 声明 | ❌ 缺失 | ❌ |
| `scopes` 声明 | ❌ 缺失 | ❌ |
| `deviceFamily` | ❌ 缺失 | ❌ |
| 存储 deviceToken | ✅ EncryptedStorage | ✅ |
| 复用 deviceToken | ✅ 连接时传入 | ✅ |

**通过率**: 5/11 = **45%**

---

## 4. 错误处理检查

### 协议规范要求

```json
// 认证错误
{
  "type": "res",
  "id": "...",
  "ok": false,
  "error": {
    "code": "AUTH_TOKEN_MISMATCH",
    "details": {
      "canRetryWithDeviceToken": true,
      "recommendedNextStep": "retry_with_device_token"
    }
  }
}

// 设备认证错误
{
  "error": {
    "code": "DEVICE_AUTH_NONCE_REQUIRED",
    "details": {
      "reason": "device-nonce-missing"
    }
  }
}

// 重连逻辑
// AUTH_TOKEN_MISMATCH 时：
// 1. 尝试使用缓存的 deviceToken 重试一次
// 2. 如果失败，停止自动重连，提示用户操作
```

### 当前实现

```kotlin
// GatewayMessage.kt
@Serializable
@SerialName("error")
data class Error(
    val code: String,
    val message: String,
    val details: String? = null,  // ✅ 有 details 字段
    val timestamp: Long = System.currentTimeMillis()
) : GatewayMessage()

// OkHttpWebSocketService.kt - 重连逻辑
override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
    _connectionState.value = WebSocketConnectionState.Error(t)
    // ✅ 有重连逻辑（指数退避）
    if (url != null) {
        scheduleReconnect(url, token)
    }
}

private fun scheduleReconnect(url: String, token: String?) {
    // ✅ 指数退避
    val delayMs = calculateBackoffDelay()
    // ...
}
```

### 一致性检查

| 要求 | 当前实现 | 状态 |
|------|----------|------|
| 处理 `AUTH_TOKEN_MISMATCH` | ❌ 未识别特定错误码 | ❌ |
| 处理 `DEVICE_AUTH_*` 错误 | ❌ 未识别 | ❌ |
| 错误包含 `canRetryWithDeviceToken` | ❌ 缺失 | ❌ |
| 错误包含 `recommendedNextStep` | ❌ 缺失 | ❌ |
| 重连逻辑（指数退避） | ✅ 已实现 | ✅ |
| 设备 token 重试机制 | ❌ 未实现 | ❌ |
| 重连失败后提示用户 | ❌ 未实现 | ❌ |

**通过率**: 1/7 = **14%**

---

## 5. 不匹配项清单

### 🔴 严重不匹配（阻塞兼容）

| # | 不匹配项 | 影响 | 修复复杂度 |
|---|----------|------|------------|
| 1 | **缺少 connect.challenge 等待** | 无法与 Gateway 握手 | 高 |
| 2 | **缺少 connect 请求帧** | Gateway 拒绝连接 | 高 |
| 3 | **消息帧格式不兼容** | 无法解析 Gateway 消息 | 高 |
| 4 | **缺少 minProtocol/maxProtocol** | 协议版本检查失败 | 中 |
| 5 | **缺少 device 对象** | 设备认证失败 | 高 |
| 6 | **签名载荷格式错误** | 签名验证失败 | 中 |
| 7 | **使用 HTTP 头而非 WS 消息** | 协议完全不兼容 | 高 |

### 🟠 高优先级不匹配

| # | 不匹配项 | 影响 | 修复复杂度 |
|---|----------|------|------------|
| 8 | 缺少 `role`/`scopes` 声明 | 权限检查失败 | 中 |
| 9 | 缺少 `client` 对象 | 客户端识别失败 | 中 |
| 10 | 缺少 `hello-ok` 响应处理 | 无法获取 deviceToken | 中 |
| 11 | 缺少 `id` 字段 | 请求 - 响应关联失败 | 中 |
| 12 | 缺少 `method`/`params` | 方法调用失败 | 中 |

### 🟡 中优先级不匹配

| # | 不匹配项 | 影响 | 修复复杂度 |
|---|----------|------|------------|
| 13 | 缺少 `seq`/`stateVersion` | 状态同步问题 | 低 |
| 14 | 缺少错误码识别 | 错误处理不当 | 低 |
| 15 | 缺少 deviceToken 重试 | 认证恢复失败 | 低 |

---

## 6. 修复建议

### 6.1 架构调整（必须）

**当前架构**:
```
WebSocket 连接 → HTTP 风格头认证 → 自定义消息格式
```

**目标架构**:
```
WebSocket 连接 → 等待 connect.challenge → 发送 connect 请求 → 接收 hello-ok → 标准 req/res/event 通信
```

### 6.2 具体修复步骤

#### 步骤 1: 实现协议帧类型

```kotlin
// network/ProtocolMessage.kt
@Serializable
sealed class ProtocolMessage {
    @SerialName("type")
    abstract val type: String
}

@Serializable
data class ProtocolRequest(
    @SerialName("type") override val type: String = "req",
    @SerialName("id") val id: String,
    @SerialName("method") val method: String,
    @SerialName("params") val params: ConnectParams
) : ProtocolMessage()

@Serializable
data class ProtocolResponse(
    @SerialName("type") override val type: String = "res",
    @SerialName("id") val id: String,
    @SerialName("ok") val ok: Boolean,
    @SerialName("payload") val payload: HelloOkPayload? = null,
    @SerialName("error") val error: ProtocolError? = null
) : ProtocolMessage()

@Serializable
data class ProtocolEvent(
    @SerialName("type") override val type: String = "event",
    @SerialName("event") val event: String,
    @SerialName("payload") val payload: JsonObject,
    @SerialName("seq") val seq: Int? = null,
    @SerialName("stateVersion") val stateVersion: String? = null
) : ProtocolMessage()
```

#### 步骤 2: 实现 Connect 请求

```kotlin
// network/ConnectRequest.kt
@Serializable
data class ConnectParams(
    @SerialName("minProtocol") val minProtocol: Int = 3,
    @SerialName("maxProtocol") val maxProtocol: Int = 3,
    @SerialName("client") val client: ClientInfo,
    @SerialName("role") val role: String,
    @SerialName("scopes") val scopes: List<String>,
    @SerialName("device") val device: DeviceInfo,
    @SerialName("auth") val auth: AuthInfo
)

@Serializable
data class ClientInfo(
    @SerialName("id") val id: String = "openclaw-android",
    @SerialName("version") val version: String,
    @SerialName("platform") val platform: String = "android",
    @SerialName("mode") val mode: String = "operator"
)

@Serializable
data class DeviceInfo(
    @SerialName("id") val id: String,
    @SerialName("publicKey") val publicKey: String,
    @SerialName("signature") val signature: String,
    @SerialName("signedAt") val signedAt: Long,
    @SerialName("nonce") val nonce: String,
    @SerialName("deviceFamily") val deviceFamily: String = "phone"
)

@Serializable
data class AuthInfo(
    @SerialName("token") val token: String?
)
```

#### 步骤 3: 重写 WebSocket 连接流程

```kotlin
// network/OkHttpWebSocketService.kt
override suspend fun connect(url: String, token: String?): Result<Unit> {
    _connectionState.value = Connecting
    
    return try {
        // 1. 建立原始 WebSocket 连接（无认证头）
        val request = Request.Builder()
            .url(url)
            .build()
        
        val webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                // 等待 connect.challenge
            }
            
            override fun onMessage(ws: WebSocket, text: String) {
                val message = parseMessage(text)
                when (message) {
                    is ConnectChallenge -> {
                        // 收到挑战，发送 connect 请求
                        sendConnectRequest(ws, message.nonce, token)
                    }
                    is HelloOk -> {
                        // 连接成功
                        _connectionState.value = Connected
                        message.deviceToken?.let { saveDeviceToken(it) }
                    }
                    // ...
                }
            }
        })
        
        this.webSocket = webSocket
        Result.success(Unit)
    } catch (e: Exception) {
        _connectionState.value = Error(e)
        Result.failure(e)
    }
}

private fun sendConnectRequest(
    ws: WebSocket,
    serverNonce: String,
    token: String?
) {
    val deviceId = securityModule.getDeviceId()
    val publicKey = securityModule.getPublicKeyPem()
    
    // 构建 v3 签名载荷
    val payloadToSign = JSONObject().apply {
        put("device", JSONObject().apply {
            put("id", deviceId)
            put("publicKey", publicKey)
        })
        put("client", JSONObject().apply {
            put("id", "openclaw-android")
            put("version", getAppVersion())
            put("platform", "android")
        })
        put("role", "operator")
        put("scopes", JSONArray(arrayOf("operator.read", "operator.write")))
        put("token", token ?: "")
        put("nonce", serverNonce)  // ✅ 使用服务器 nonce
        put("signedAt", System.currentTimeMillis())
        put("deviceFamily", "phone")
    }
    
    val signature = securityModule.signChallenge(payloadToSign.toString())
    
    val connectRequest = ProtocolRequest(
        id = UUID.randomUUID().toString(),
        method = "connect",
        params = ConnectParams(
            minProtocol = 3,
            maxProtocol = 3,
            client = ClientInfo(version = getAppVersion()),
            role = "operator",
            scopes = listOf("operator.read", "operator.write"),
            device = DeviceInfo(
                id = deviceId,
                publicKey = publicKey,
                signature = signature,
                signedAt = System.currentTimeMillis(),
                nonce = serverNonce
            ),
            auth = AuthInfo(token)
        )
    )
    
    ws.send(json.encodeToString(connectRequest))
}
```

#### 步骤 4: 实现标准消息发送

```kotlin
// network/MessageSender.kt
suspend fun sendChatMessage(sessionId: String, content: String): Result<Message> {
    val requestId = UUID.randomUUID().toString()
    
    val request = ProtocolRequest(
        id = requestId,
        method = "chat.send",
        params = ChatSendParams(
            sessionId = sessionId,
            content = content,
            idempotencyKey = UUID.randomUUID().toString()
        )
    )
    
    // 发送并等待响应
    return suspendCancellableCoroutine { continuation ->
        val job = appScope.launch {
            incomingMessages.collect { message ->
                if (message is ProtocolResponse && message.id == requestId) {
                    if (message.ok) {
                        continuation.resume(Result.success(message.payload.toMessage()))
                    } else {
                        continuation.resume(Result.failure(ProtocolException(message.error)))
                    }
                    cancel()
                }
            }
        }
        
        webSocket?.send(json.encodeToString(request))
        
        // 超时处理
        appScope.launch {
            delay(30000)
            if (continuation.isActive) {
                job.cancel()
                continuation.resume(Result.failure(TimeoutException()))
            }
        }
    }
}
```

#### 步骤 5: 实现错误处理

```kotlin
// network/ErrorHandler.kt
sealed class ProtocolError {
    data class AuthTokenMismatch(
        val canRetryWithDeviceToken: Boolean,
        val recommendedNextStep: String
    ) : ProtocolError()
    
    data class DeviceAuthError(
        val code: String,
        val reason: String
    ) : ProtocolError()
    
    // ...
}

fun parseError(error: JsonObject): ProtocolError {
    val code = error["code"]?.jsonPrimitive?.content
    val details = error["details"]?.jsonObject
    
    return when (code) {
        "AUTH_TOKEN_MISMATCH" -> {
            AuthTokenMismatch(
                canRetryWithDeviceToken = details?.get("canRetryWithDeviceToken")?.jsonPrimitive?.boolean ?: false,
                recommendedNextStep = details?.get("recommendedNextStep")?.jsonPrimitive?.content ?: "retry_with_device_token"
            )
        }
        "DEVICE_AUTH_NONCE_REQUIRED",
        "DEVICE_AUTH_NONCE_MISMATCH",
        "DEVICE_AUTH_SIGNATURE_INVALID",
        "DEVICE_AUTH_SIGNATURE_EXPIRED",
        "DEVICE_AUTH_DEVICE_ID_MISMATCH",
        "DEVICE_AUTH_PUBLIC_KEY_INVALID" -> {
            DeviceAuthError(
                code = code,
                reason = details?.get("reason")?.jsonPrimitive?.content ?: "unknown"
            )
        }
        else -> UnknownError(error.toString())
    }
}

// 重连逻辑
private fun handleAuthError(error: AuthTokenMismatch) {
    if (error.canRetryWithDeviceToken) {
        val cachedToken = securityModule.getAuthToken()
        if (cachedToken != null) {
            // 重试一次
            reconnectWithToken(cachedToken)
            return
        }
    }
    
    // 停止重连，提示用户
    _connectionState.value = Error(AuthenticationException(error.recommendedNextStep))
}
```

---

## 7. 修复优先级与工时估算

### 🔴 阶段 1: 核心协议兼容（2-3 周）

| 任务 | 工时 | 依赖 |
|------|------|------|
| 实现协议帧类型 (req/res/event) | 2d | - |
| 实现 connect.challenge 等待 | 2d | - |
| 实现 connect 请求构建 | 3d | 协议帧 |
| 实现 hello-ok 响应处理 | 2d | connect 请求 |
| 重写 WebSocket 连接流程 | 3d | 以上全部 |
| **小计** | **12d** | |

### 🟠 阶段 2: 消息格式兼容（1-2 周）

| 任务 | 工时 | 依赖 |
|------|------|------|
| 迁移现有消息到标准格式 | 3d | 阶段 1 |
| 实现请求 - 响应关联 (id) | 2d | 阶段 1 |
| 实现 method/params 解析 | 2d | 阶段 1 |
| 实现 Event 处理 | 2d | 阶段 1 |
| **小计** | **9d** | |

### 🟡 阶段 3: 认证完善（1 周）

| 任务 | 工时 | 依赖 |
|------|------|------|
| 实现 v3 签名载荷 | 2d | - |
| 使用服务器 nonce 签名 | 1d | - |
| deviceToken 重试机制 | 2d | 错误处理 |
| **小计** | **5d** | |

### 🟢 阶段 4: 错误处理（1 周）

| 任务 | 工时 | 依赖 |
|------|------|------|
| 实现错误码识别 | 2d | - |
| 实现 AUTH_TOKEN_MISMATCH 处理 | 2d | - |
| 实现 DEVICE_AUTH_* 处理 | 2d | - |
| 用户提示 UI | 2d | - |
| **小计** | **8d** | |

**总工时估算**: 34 工作日 (~7 周)

---

## 8. 总结

### 当前状态

ClawChat Android 当前实现使用**自定义 WebSocket 协议**，与 OpenClaw Gateway 标准协议**严重不兼容**。主要问题：

1. **握手流程完全错误**: 使用 HTTP 头认证而非 WebSocket `connect` 请求
2. **消息格式不兼容**: 缺少 `req`/`res`/`event` 帧结构
3. **认证流程错误**: 使用客户端 nonce 而非服务器 challenge
4. **错误处理缺失**: 无法识别标准错误码

### 建议

**选项 A: 完整重构（推荐）**
- 按照本协议修复报告实现标准协议
- 工时：~7 周
- 收益：完全兼容 OpenClaw 生态

**选项 B: Gateway 适配层**
- 在 Gateway 端添加自定义协议适配
- 工时：~2 周（Gateway 端）
- 风险：长期维护成本，偏离标准

**选项 C: 并行实现**
- 保留当前实现用于内部测试
- 新建标准协议实现用于生产
- 工时：~7 周（与 A 相同）

### 下一步

1. **确认修复策略** (选项 A/B/C)
2. **创建协议实现任务** (如选 A/C)
3. **或联系 Gateway 团队** (如选 B)

---

*审查完成时间：2026-03-18*  
*审查依据：OpenClaw Gateway Protocol v3*  
*建议行动：立即启动协议兼容性修复*
