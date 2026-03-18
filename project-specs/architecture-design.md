# ClawChat Android 架构设计

> 基于源码验证的最终架构  
> **版本**: 2.0  
> **日期**: 2026-03-19  
> **约束**: Ed25519 · Protocol v3 · Gateway = Source of Truth

---

## 1. 整体架构

### 1.1 分层架构图

```
┌─────────────────────────────────────────────────────────────────────┐
│                         UI Layer (Compose)                          │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐ ┌───────────┐  │
│  │ PairingScreen│ │  MainScreen  │ │SessionScreen │ │ Settings  │  │
│  └──────┬───────┘ └──────┬───────┘ └──────┬───────┘ └─────┬─────┘  │
│         │                │                │               │         │
│  ┌──────▼───────┐ ┌──────▼───────┐ ┌──────▼───────┐ ┌─────▼─────┐  │
│  │PairingVM     │ │  MainVM      │ │ SessionVM    │ │SettingsVM │  │
│  └──────┬───────┘ └──────┬───────┘ └──────┬───────┘ └─────┬─────┘  │
├─────────┼────────────────┼────────────────┼───────────────┼─────────┤
│         │          Gateway SDK Layer                      │         │
│         │     ┌──────────────────────────────────┐        │         │
│         └────►│       GatewayClient               │◄───────┘        │
│               │  ┌──────────┐  ┌──────────────┐  │                  │
│               │  │ AuthFlow │  │ RpcDispatcher │  │                  │
│               │  └──────────┘  └──────────────┘  │                  │
│               │  ┌──────────┐  ┌──────────────┐  │                  │
│               │  │ChatClient│  │SessionClient │  │                  │
│               │  └──────────┘  └──────────────┘  │                  │
│               └──────────┬───────────────────────┘                  │
├──────────────────────────┼──────────────────────────────────────────┤
│                   Protocol Layer                                    │
│  ┌───────────────┐ ┌────▼──────┐ ┌───────────────┐ ┌────────────┐  │
│  │ FrameCodec    │ │ WsTransport│ │RequestTracker │ │ SeqManager │  │
│  │ (JSON↔Frame)  │ │ (OkHttp)   │ │ (req↔res)    │ │ (dedup)    │  │
│  └───────────────┘ └───────────┘ └───────────────┘ └────────────┘  │
├─────────────────────────────────────────────────────────────────────┤
│                    Security Layer                                    │
│  ┌───────────────┐ ┌───────────────┐ ┌───────────────────────────┐  │
│  │ Ed25519KeyMgr │ │ EncryptedStore│ │ DeviceIdentity            │  │
│  │ (BouncyCastle)│ │ (AES-256-GCM) │ │ (id=sha256(pubkey).hex)  │  │
│  └───────────────┘ └───────────────┘ └───────────────────────────┘  │
├─────────────────────────────────────────────────────────────────────┤
│                    Local Cache Layer                                 │
│  ┌───────────────┐ ┌───────────────┐ ┌───────────────────────────┐  │
│  │ Room DB       │ │ SharedPrefs   │ │ DataStore                 │  │
│  │ (messages)    │ │ (gateway cfg) │ │ (user prefs)              │  │
│  └───────────────┘ └───────────────┘ └───────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────┘
```

### 1.2 设计原则

| 原则 | 说明 |
|------|------|
| **Gateway 是 Source of Truth** | 会话、消息、状态全部以 Gateway 为准；本地仅做缓存 |
| **协议驱动** | 所有网络交互严格按 Protocol v3 帧格式（req/res/event） |
| **保留可用代码** | 保留 ~70% 现有代码（UI/Room/Security 基础），修复而非重写 |
| **单向数据流** | Gateway 事件 → StateFlow → Compose UI |

---

## 2. 网络层设计

### 2.1 协议帧处理

```
Gateway WebSocket
     │
     ▼
┌─────────────┐     ┌─────────────┐
│ WsTransport │────►│ FrameCodec  │
│ (OkHttp WS) │     │ JSON↔Frame  │
└─────────────┘     └──────┬──────┘
                           │
              ┌────────────┼────────────┐
              ▼            ▼            ▼
        ┌──────────┐ ┌──────────┐ ┌──────────┐
        │ type=req │ │ type=res │ │type=event│
        │ (ignore) │ │→Tracker  │ │→Dispatch │
        └──────────┘ └──────────┘ └────┬─────┘
                                       │
                    ┌──────────────────┼──────────────────┐
                    ▼                  ▼                  ▼
            ┌──────────────┐ ┌──────────────┐ ┌──────────────┐
            │connect.      │ │ chat         │ │ device.pair. │
            │challenge     │ │ (ChatEvent)  │ │ resolved     │
            │→ AuthFlow    │ │→ ChatClient  │ │→ AuthFlow    │
            └──────────────┘ └──────────────┘ └──────────────┘
```

### 2.2 连接生命周期

```
DISCONNECTED ──connect()──► CONNECTING
     ▲                │
     │                   [WS open]
     │                         ▼
     │                   AUTHENTICATING
     │                         │
     │               [challenge received]
     │                         ▼
     │                   SIGNING (build v3 payload, sign)
     │                         │
     │               [send connect req]
     │                         ▼
     │                   AWAITING_HELLO
     │                         │
     │                 ┌───────┴───────┐
     │                 ▼               ▼
     │           [hello-ok]      [error/timeout]
     │                 │               │
     │                 ▼               │
     │            CONNECTED ◄──────────┘ (retry)
     │                 │
     │          [ws close/error]
     │                 │
     └─────────── RECONNECTING (exponential backoff)
```

### 2.3 RPC 调用模式

所有 Gateway 交互使用统一的 req/res 模式：

```kotlin
// 发送请求，等待响应
val response = rpcDispatcher.call(
    method = "chat.send",
    params = buildJsonObject {
        put("sessionKey", sessionKey)
        put("message", text)
        put("idempotencyKey", UUID.randomUUID().toString())
    },
    timeoutMs = 30_000
)
```

---

## 3. 认证流程设计

### 3.1 Ed25519 密钥方案

Android Keystore 不支持 Ed25519，采用 **BouncyCastle + EncryptedSharedPreferences** 方案：

```
首次启动
  │
  ├── BouncyCastle Ed25519KeyPairGenerator → 生成密钥对
  │     ├── 私钥 (PKCS8 PEM) → EncryptedSharedPreferences (AES-256-GCM)
  │     └── 公钥 (SPKI PEM)  → EncryptedSharedPreferences
  │
  └── 派生 deviceId = SHA-256(公钥原始32字节).hex()
```

### 3.2 v3 签名载荷

**精确格式**（11 段，竖线分隔）：

```
v3|{deviceId}|openclaw-android|{mode}|{role}|{scopes,逗号}|{signedAtMs}|{token或空}|{nonce}|{platform小写}|{deviceFamily小写}
```

示例：
```
v3|a1b2c3...hex64|openclaw-android|ui|operator|operator.read,operator.write|1737264000000||f47ac10b-58cc|android|phone
```

### 3.3 三种连接路径

```
路径 A: 首次连接（Setup Code）
  1. 解析 Setup Code → { url, bootstrapToken }
  2. WS 连接 → 收到 challenge
  3. 签名 v3 payload（token = bootstrapToken）
  4. 发送 connect → 等待配对审批
  5. 收到 hello-ok + deviceToken → 持久化

路径 B: 首次连接（手动输入）
  1. 输入 host:port + gatewayToken
  2. WS 连接 → 收到 challenge
  3. 签名 v3 payload（token = gatewayToken）
  4. 发送 connect → 等待配对审批
  5. 收到 hello-ok + deviceToken → 持久化

路径 C: 重连（已配对）
  1. 读取保存的 url + deviceToken
  2. WS 连接 → 收到 challenge
  3. 签名 v3 payload（token = deviceToken）
  4. 发送 connect → 收到 hello-ok
```

### 3.4 认证错误恢复

```kotlin
when (errorCode) {
    "AUTH_TOKEN_MISMATCH" -> {
        if (canRetryWithDeviceToken && hasDeviceToken) {
            // 自动重试一次（用 deviceToken）
            retryWithDeviceToken()
        } else {
            // 提示用户重新配对
            showRepairDialog()
        }
    }
    "PAIRING_REQUIRED" -> showPairingScreen()
    "AUTH_RATE_LIMITED" -> scheduleRetry(retryAfterMs)
    "DEVICE_AUTH_NONCE_MISMATCH" -> reconnect() // nonce 过期，重连获取新 challenge
    "DEVICE_AUTH_SIGNATURE_INVALID" -> resetKeysAndRepair()
    else -> showError(errorMessage)
}
```

---

## 4. 消息收发设计

### 4.1 ChatEvent 状态机

```
Gateway chat event 流
     │
     ▼
┌─────────────────────────────────────┐
│           ChatEventHandler          │
│                                     │
│  state="delta"  → 追加到消息缓冲     │
│  state="final"  → 完成消息，写入缓存  │
│  state="aborted"→ 标记中止，保留部分  │
│  state="error"  → 显示错误           │
│                                     │
│  按 runId 分组，按 seq 排序          │
└─────────┬───────────────────────────┘
          │
          ▼
   MessageStateFlow → UI
```

### 4.2 发送消息

```kotlin
// chat.send 请求
{
  "type": "req",
  "id": "<uuid>",
  "method": "chat.send",
  "params": {
    "sessionKey": "agent:main:main",
    "message": "你好",
    "idempotencyKey": "<uuid>",     // 必需！
    "thinking": null,               // 可选
    "attachments": []               // 可选
  }
}
```

### 4.3 接收消息（chat 事件）

```kotlin
// Gateway 推送 chat 事件
{
  "type": "event",
  "event": "chat",
  "payload": {
    "runId": "run_abc123",
    "sessionKey": "agent:main:main",
    "seq": 0,
    "state": "delta",              // delta/final/aborted/error
    "message": { ... },            // 结构化消息对象
    "usage": { ... },              // token 使用量
    "stopReason": null
  }
}
```

### 4.4 消息生命周期

```
用户输入 → chat.send req → Gateway 处理
                              │
                              ├── chat event (state=delta, seq=0) → UI 显示"正在回复"
                              ├── chat event (state=delta, seq=1) → UI 更新内容
                              ├── ...
                              └── chat event (state=final, seq=N) → UI 完成，写入 Room 缓存
```

---

## 5. 会话管理设计

### 5.1 数据流

```
Gateway (source of truth)
     │
     │  sessions.list RPC
     ▼
┌──────────────┐
│ SessionClient │──── 定期刷新 ────► sessions.list
│              │                         │
│              │◄── hello-ok.snapshot ────┘ (连接时初始数据)
│              │
│              │──── 实时更新 ────► chat events (更新 lastActivity)
└──────┬───────┘
       │
       ▼
 SessionStateFlow
       │
       ├──► MainViewModel → 会话列表 UI
       │
       └──► Room DB (本地缓存，离线查看)
```

### 5.2 会话键映射

ClawChat 作为 operator 客户端，主要使用：

| 场景 | sessionKey | 说明 |
|------|------------|------|
| 默认主会话 | `agent:main:main` | 从 hello-ok.snapshot.sessionDefaults 获取 |
| 指定会话 | sessions.list 返回的 key | 直接使用 |
| 新建会话 | 发送 `/new` 到主会话 | Gateway 自动创建 |

### 5.3 hello-ok 初始快照

连接成功后，hello-ok 包含初始状态，免去额外查询：

```kotlin
// 从 hello-ok 提取初始数据
fun handleHelloOk(payload: JsonObject) {
    // 1. 保存 deviceToken
    val deviceToken = payload.path("auth.deviceToken")
    
    // 2. 提取可用方法/事件
    val methods = payload.path("features.methods") // 功能发现
    val events = payload.path("features.events")
    
    // 3. 提取默认会话键
    val mainSessionKey = payload.path("snapshot.sessionDefaults.mainSessionKey")
    
    // 4. 提取初始 presence
    val presence = payload.path("snapshot.presence")
    
    // 5. 提取策略限制
    val maxPayload = payload.path("policy.maxPayload")
    val tickIntervalMs = payload.path("policy.tickIntervalMs")
}
```

---

## 6. 状态管理设计

### 6.1 全局状态

```kotlin
// 连接状态（全局单例）
sealed class GatewayConnectionState {
    data object Disconnected : GatewayConnectionState()
    data object Connecting : GatewayConnectionState()
    data object Authenticating : GatewayConnectionState()
    data class WaitingApproval(val requestId: String) : GatewayConnectionState()
    data class Connected(
        val serverVersion: String,
        val defaultSessionKey: String,
        val latencyMs: Long? = null
    ) : GatewayConnectionState()
    data class Reconnecting(val attempt: Int, val nextRetryMs: Long) : GatewayConnectionState()
    data class Error(val code: String, val message: String, val recoverable: Boolean) : GatewayConnectionState()
}
```

### 6.2 数据流向

```
GatewayClient.connectionState ─────────► PairingViewModel
                                          MainViewModel
                                          SessionViewModel

GatewayClient.chatEvents ──────────────► SessionViewModel
                                          NotificationManager

GatewayClient.sessions ────────────────► MainViewModel

Room DB (缓存) ────────────────────────► MainViewModel (离线)
                                          SessionViewModel (离线)
```

---

## 7. Hilt 依赖注入

```
@Singleton Component
├── Ed25519KeyManager          (密钥管理)
├── EncryptedStorage           (加密存储)
├── DeviceIdentity             (设备身份: id + publicKey)
├── OkHttpClient               (网络客户端)
├── GatewayClient              (Gateway SDK 入口)
│   ├── WsTransport            (WebSocket 传输)
│   ├── FrameCodec             (帧编解码)
│   ├── AuthFlow               (认证流程)
│   ├── RpcDispatcher          (RPC 调度)
│   ├── ChatClient             (聊天)
│   └── SessionClient          (会话)
├── ClawChatDatabase           (Room)
│   ├── MessageDao
│   └── SessionDao
├── GatewayRepository          (配置存储)
├── MessageRepository          (消息缓存)
└── SessionRepository          (会话缓存)
```

---

## 8. 与现有代码的映射

### 8.1 保留

| 现有文件 | 新架构对应 |
|----------|-----------|
| `ui/screens/*` | UI Layer — 保留，小重构 |
| `ui/state/UiState.kt` | UI Layer — 保留，更新状态类型 |
| `ui/theme/*` | UI Layer — 原样保留 |
| `data/local/*` | Local Cache Layer — 原样保留 |
| `security/EncryptedStorage.kt` | Security Layer — 保留 |
| `network/protocol/RequestFrame.kt` | Protocol Layer — 保留 |
| `network/protocol/ResponseFrame.kt` | Protocol Layer — 保留 |
| `network/protocol/EventFrame.kt` | Protocol Layer — 保留 |
| `network/protocol/RequestTracker.kt` | Protocol Layer — 保留 |
| `network/protocol/SequenceManager.kt` | Protocol Layer — 保留 |
| `network/GatewayUrlUtil.kt` | Protocol Layer — 保留 |
| `network/NetworkErrorHandler.kt` | Protocol Layer — 保留 |
| `network/TailscaleManager.kt` | Protocol Layer — 保留 |
| `repository/*` | Local Cache Layer — 保留，调整接口 |

### 8.2 重写

| 现有文件 | 新架构替代 |
|----------|-----------|
| `security/KeystoreManager.kt` | → `Ed25519KeyManager`（ECDSA→Ed25519）|
| `security/SecurityModule.kt` | → `DeviceIdentity` + `AuthFlow` |
| `network/protocol/ChallengeResponseAuth.kt` | → `AuthFlow` |
| `network/protocol/GatewayConnection.kt` | → `GatewayClient` + `WsTransport` |
| `network/OkHttpWebSocketService.kt` | → `WsTransport`（桥接或替换）|
| `ui/state/MainViewModel.kt` | 删除 mock，接入 GatewayClient |
| `ui/state/SessionViewModel.kt` | 删除 mock，接入 ChatClient |
| `ui/state/PairingViewModel.kt` | 接入 AuthFlow |

### 8.3 删除

| 文件 | 原因 |
|------|------|
| `src/` 整个目录 | 旧原型，与 app/ 重复 |
| `network/GatewayMessage.kt` | 自定义格式，不兼容 v3 |
| `network/SignatureInterceptor.kt` | HTTP 头签名，WS 不使用 |
| `network/WebSocketService.kt` 接口 | 被 GatewayClient 替代 |
| `security/DeviceFingerprint.kt` | deviceId 改为从公钥派生 |
