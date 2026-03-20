# ClawChat Android 使用逻辑审查报告

**审查日期**: 2026-03-20  
**审查方式**: 按用户使用流程验证代码实现

---

## 流程 1: 首次配对

### 代码实现验证

| 步骤 | 文件 | 状态 | 说明 |
|------|------|------|------|
| 用户输入 Gateway 地址 | PairingScreen.kt | ✅ 正确 | 支持 ws/wss/http/https/裸地址 |
| App 生成密钥对 | KeystoreManager.kt | ✅ 正确 | API 33+ 硬件 Keystore，API 26-32 BouncyCastle |
| 发送配对请求 | GatewayConnection.kt | ✅ 正确 | connect.challenge → v3 签名 → connect req |
| 等待管理员批准 | PairingViewModel.kt | ⚠️ 不完整 | 缺少超时处理和轮询机制 |
| 保存 deviceToken | EncryptedStorage.kt | ✅ 正确 | AES-256-GCM 加密存储 |

### 详细分析

**✅ 正确实现**:

1. **密钥生成** (KeystoreManager.kt:70-100)
   - API 33+: 使用 `Android Keystore` 硬件级 Ed25519
   - API 26-32: 使用 BouncyCastle + EncryptedSharedPreferences
   - 私钥签名后立即清除内存引用（安全设计）

2. **v3 签名协议** (SecurityModule.kt:100-140)
   - payload 格式: `v3|deviceId|clientId|clientMode|role|scopes|signedAtMs|token|nonce|platform|deviceFamily`
   - 与 Gateway `buildDeviceAuthPayloadV3` 对齐

3. **认证流程** (GatewayConnection.kt:200-280)
   - WebSocket 连接 → 收到 connect.challenge → 签名 → 发送 connect → 收到 hello-ok

**⚠️ 不完整**:

1. **PairingViewModel.kt:120-140** - `startPairing()` 缺少等待批准的完整状态机:
   - 没有处理 `device.pairing.approved` 事件
   - 没有处理 `device.pairing.rejected` 事件
   - 没有配对超时机制

2. **PairingViewModel.kt:172-191** - 证书信任功能:
   - `confirmCertificateTrust()` 实现完整
   - 但 `GatewayTrustManager.getCurrentHostname()` 返回 null，证书验证不完整

### 改进建议

```kotlin
// PairingViewModel.kt - 添加配对事件监听
private fun observePairingEvents() {
    viewModelScope.launch {
        gateway.incomingMessages.collect { rawJson ->
            val event = parseEvent(rawJson)
            when (event) {
                "device.pairing.approved" -> handlePairingApproved(event)
                "device.pairing.rejected" -> handlePairingRejected(event)
            }
        }
    }
}
```

---

## 流程 2: 连接管理

### 代码实现验证

| 步骤 | 文件 | 状态 | 说明 |
|------|------|------|------|
| 打开 App 自动连接 | MainViewModel.kt | ⚠️ 不完整 | init 中只观察状态，未主动连接 |
| 连接状态显示 | MainViewModel.kt | ✅ 正确 | ConnectionStatus sealed class |
| 断线重连 | GatewayConnection.kt | ✅ 正确 | 指数退避，最大 15 次 |
| 延迟测量 | GatewayConnection.kt | ✅ 正确 | ping() 方法 |

### 详细分析

**✅ 正确实现**:

1. **指数退避重连** (GatewayConnection.kt:350-380)
   ```kotlin
   val delayMs = (INITIAL_RECONNECT_DELAY_MS * Math.pow(RECONNECT_BACKOFF_FACTOR, reconnectAttempt.toDouble()))
       .toLong().coerceAtMost(MAX_RECONNECT_DELAY_MS)
   ```
   - 初始 1s，最大 30s，最多 15 次

2. **心跳机制** (GatewayConnection.kt:340-350)
   - 每 30 秒发送 ping

**⚠️ 不完整**:

1. **MainViewModel.kt:50-60** - 没有自动连接逻辑:
   - `init` 中只调用 `loadSessionsFromCache()` 和 `observeConnectionState()`
   - 缺少 `autoConnectIfNeeded()` 方法

### 改进建议

```kotlin
// MainViewModel.kt - 添加自动连接
init {
    loadSessionsFromCache()
    observeConnectionState()
    autoConnectIfNeeded() // 新增
}

private fun autoConnectIfNeeded() {
    viewModelScope.launch {
        val savedUrl = securityModule.getGatewayUrl()
        if (!savedUrl.isNullOrBlank() && securityModule.getSecurityStatus().isPaired) {
            connectToGateway(savedUrl)
        }
    }
}
```

---

## 流程 3: 会话管理

### 代码实现验证

| 步骤 | 文件 | 状态 | 说明 |
|------|------|------|------|
| 获取会话列表 | MainViewModel.kt | ✅ 正确 | sessions.list RPC |
| 显示会话详情 | MainScreen.kt | ✅ 正确 | 会话卡片 UI |
| 创建会话 | MainViewModel.kt | ⚠️ 不完整 | 使用 sessions.reset 而非创建新会话 |
| 删除会话 | MainViewModel.kt | ✅ 正确 | sessions.delete RPC |

### 详细分析

**✅ 正确实现**:

1. **会话列表加载** (MainViewModel.kt:100-150)
   - 连接成功后调用 `sessionsList()`
   - 解析 JSON 并更新 UI
   - 同步到 Room 缓存

2. **离线缓存** (MainViewModel.kt:60-70)
   - 启动时从 Room 加载缓存
   - 连接后从 Gateway 同步

**⚠️ 不完整**:

1. **MainViewModel.kt:220-230** - `createSession()` 实现问题:
   - 使用 `sessions.reset` 重置现有会话，而非创建新会话
   - 没有支持选择模型

---

## 流程 4: 消息收发

### 代码实现验证

| 步骤 | 文件 | 状态 | 说明 |
|------|------|------|------|
| 进入会话 | SessionViewModel.kt | ✅ 正确 | 加载历史消息 |
| 发送消息 | SessionViewModel.kt | ✅ 正确 | chat.send + idempotencyKey |
| 实时接收 | SessionViewModel.kt | ✅ 正确 | chat 事件监听 |
| 流式显示 | SessionViewModel.kt | ✅ 正确 | delta/final/aborted 状态机 |

### 详细分析

**✅ 正确实现**:

1. **流式消息处理** (SessionViewModel.kt:100-180)
   - `delta`: 追加到流式缓冲
   - `final`: 完成消息，写入 Room
   - `aborted`: 标记中止
   - `error`: 显示错误

2. **消息发送** (SessionViewModel.kt:200-250)
   - 先显示用户消息（乐观更新）
   - 调用 `chatSend()` 发送到 Gateway
   - 包含 `idempotencyKey` 防重

3. **容量管理** (SessionViewModel.kt:190-200)
   - `MAX_TRACKED_RUNS = 100`
   - 自动清理旧的 runId

**✅ 无问题发现**

---

## 流程 5: 安全机制

### 代码实现验证

| 步骤 | 文件 | 状态 | 说明 |
|------|------|------|------|
| 密钥存储 | KeystoreManager.kt | ✅ 正确 | API 33+ 硬件，API 26-32 软件 |
| 挑战-响应认证 | ChallengeResponseAuth.kt | ✅ 正确 | v3 签名协议 |
| 证书信任 | GatewayTrustManager.kt | ⚠️ 不完整 | hostname 传递未实现 |

### 详细分析

**✅ 正确实现**:

1. **Ed25519 密钥管理** (KeystoreManager.kt)
   - API 33+: `KeyPairGenerator.getInstance("Ed25519", "AndroidKeyStore")`
   - API 26-32: BouncyCastle + EncryptedSharedPreferences
   - 私钥签名后清除内存引用

2. **v3 签名协议** (ChallengeResponseAuth.kt:60-100)
   - 使用 `SecurityModule.signV3Payload()`
   - 公钥格式: raw 32 bytes base64url

**⚠️ 不完整**:

1. **GatewayTrustManager.kt:72** - `getCurrentHostname()` 返回 null:
   ```kotlin
   private fun getCurrentHostname(): String? {
       return null // 暂时返回 null，由上层处理
   }
   ```
   - 证书验证时无法获取 hostname
   - 需要通过 OkHttp EventListener 或 ThreadLocal 传递

### 改进建议

```kotlin
// 使用 ThreadLocal 传递 hostname
object CurrentConnection {
    private val hostname = ThreadLocal<String?>()
    
    fun set(host: String?) = hostname.set(host)
    fun get(): String? = hostname.get()
}

// 在 GatewayConnection.connect() 中设置
CurrentConnection.set(url.host)

// 在 DynamicTrustManager 中获取
private fun getCurrentHostname(): String? = CurrentConnection.get()
```

---

## 功能完整度评分

| 流程 | 完整度 | 评分 |
|------|--------|------|
| 首次配对 | 85% | ⭐⭐⭐⭐ |
| 连接管理 | 90% | ⭐⭐⭐⭐ |
| 会话管理 | 95% | ⭐⭐⭐⭐⭐ |
| 消息收发 | 100% | ⭐⭐⭐⭐⭐ |
| 安全机制 | 90% | ⭐⭐⭐⭐ |

**综合评分**: ⭐⭐⭐⭐☆ (4.2/5)

---

## 关键问题列表

| 优先级 | 问题 | 文件 | 影响 |
|--------|------|------|------|
| 🔴 高 | 自动连接未实现 | MainViewModel.kt | 用户每次需手动连接 |
| 🟠 中 | 配对事件监听缺失 | PairingViewModel.kt | 无法自动完成配对 |
| 🟠 中 | 证书 hostname 未传递 | GatewayTrustManager.kt | 证书验证不完整 |
| 🟡 低 | createSession 实现不完整 | MainViewModel.kt | 无法创建新会话 |

---

## 优先修复建议

### 1. 添加自动连接 (高优先级)

```kotlin
// MainViewModel.kt
init {
    loadSessionsFromCache()
    observeConnectionState()
    autoConnectIfNeeded()
}

private fun autoConnectIfNeeded() {
    viewModelScope.launch {
        val savedUrl = securityModule.getGatewayUrl()
        val status = securityModule.getSecurityStatus()
        if (!savedUrl.isNullOrBlank() && status.isPaired) {
            connectToGateway(savedUrl)
        }
    }
}
```

### 2. 添加配对事件监听 (中优先级)

```kotlin
// PairingViewModel.kt
private fun observePairingEvents() {
    viewModelScope.launch {
        gateway.incomingMessages.collect { rawJson ->
            try {
                val obj = json.parseToJsonElement(rawJson).jsonObject
                val event = obj["event"]?.jsonPrimitive?.content
                when (event) {
                    "device.pairing.approved" -> {
                        val payload = obj["payload"]?.jsonObject
                        val token = payload?.get("deviceToken")?.jsonPrimitive?.content
                        if (!token.isNullOrBlank()) {
                            securityModule.completePairing(token)
                            _state.value = _state.value.copy(status = PairingStatus.Approved)
                            _events.emit(PairingEvent.PairingSuccess)
                        }
                    }
                    "device.pairing.rejected" -> {
                        _state.value = _state.value.copy(status = PairingStatus.Rejected)
                        _events.emit(PairingEvent.PairingRejected)
                    }
                }
            } catch (_: Exception) {}
        }
    }
}
```

### 3. 实现证书 hostname 传递 (中优先级)

使用 ThreadLocal 或 OkHttp EventListener 传递当前连接的 hostname。

---

*报告生成时间: 2026-03-20 18:15 CST*