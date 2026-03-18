# 协议源码验证报告

> 对照 OpenClaw Gateway 源码验证协议文档  
> **验证日期**: 2026-03-18  
> **源码版本**: github.com/openclaw/openclaw (HEAD)  
> **协议版本**: v3（`PROTOCOL_VERSION = 3`，已确认）

---

## 验证摘要

| 类别 | 项目数 | 一致 | ⚠️ 不一致 | 补充 |
|------|--------|------|-----------|------|
| 密钥类型 | 1 | 0 | **1** | 0 |
| 签名载荷 | 2 | 1 | **1** | 0 |
| 握手流程 | 4 | 4 | 0 | 2 |
| 认证方式 | 5 | 4 | 0 | 1 |
| 消息格式 | 6 | 4 | **2** | 1 |
| 会话管理 | 4 | 3 | 0 | 1 |
| 设备管理 | 3 | 3 | 0 | 0 |
| 客户端标识 | 1 | 0 | **1** | 0 |
| **总计** | **26** | **19** | **5** | **5** |

### 🚨 严重不一致：5 项（直接影响 ClawChat 实现）

---

## 1. 密钥类型

### 🚨 不一致 #1: Ed25519，不是 ECDSA

| 维度 | 文档/规格 | 源码实际 |
|------|-----------|----------|
| 算法 | ECDSA secp256r1 (P-256) | **Ed25519** |
| 密钥格式 | PEM (SPKI) | PEM (SPKI)，传输时用 **base64url 编码的原始 32 字节公钥** |
| 签名方式 | SHA256withECDSA | **Ed25519 纯签名**（`crypto.sign(null, ...)`，无摘要算法）|
| deviceId 派生 | 不明确 | **SHA-256(公钥原始字节).hex()** |

**源码证据** (`src/infra/device-identity.ts`):

```typescript
// 密钥生成
const { publicKey, privateKey } = crypto.generateKeyPairSync("ed25519");

// 签名
export function signDevicePayload(privateKeyPem: string, payload: string): string {
  const key = crypto.createPrivateKey(privateKeyPem);
  const sig = crypto.sign(null, Buffer.from(payload, "utf8"), key);
  return base64UrlEncode(sig);
}

// deviceId = SHA-256(公钥原始32字节).hex()
function fingerprintPublicKey(publicKeyPem: string): string {
  const raw = derivePublicKeyRaw(publicKeyPem);
  return crypto.createHash("sha256").update(raw).digest("hex");
}
```

**对 ClawChat 的影响**:
- ❌ 不能使用 Android Keystore 的 ECDSA P-256
- ❌ 需要 Ed25519 库（Android Keystore **不支持 Ed25519**）
- ✅ 替代方案：使用 `libsodium-jni` 或 `BouncyCastle` 实现 Ed25519
- ✅ 或使用 `java.security` 的 `EdDSA`（API 33+ 原生支持，低版本需 BouncyCastle）
- ⚠️ 密钥不能存储在 Android Keystore 的 TEE 中（因为 TEE 不支持 Ed25519），需要用 EncryptedSharedPreferences 或 AndroidX Security 库软件加密存储

### 公钥传输格式

源码中公钥有两种格式：
1. **PEM 格式**（存储/生成时）
2. **base64url 编码的原始 32 字节**（传输时，通过 `normalizeDevicePublicKeyBase64Url` 转换）

验证签名时，两种格式都能接受：

```typescript
const key = publicKey.includes("BEGIN")
  ? crypto.createPublicKey(publicKey)
  : crypto.createPublicKey({
      key: Buffer.concat([ED25519_SPKI_PREFIX, base64UrlDecode(publicKey)]),
      type: "spki",
      format: "der",
    });
```

---

## 2. 签名载荷格式

### ✅ 一致: v3 签名载荷字段

v3 签名载荷格式已确认（`src/gateway/device-auth.ts`）：

```
v3|{deviceId}|{clientId}|{clientMode}|{role}|{scopes}|{signedAtMs}|{token}|{nonce}|{platform}|{deviceFamily}
```

### 🚨 不一致 #2: 签名载荷包含 clientMode 字段

| 维度 | 文档 | 源码实际 |
|------|------|----------|
| 字段列表 | deviceId, clientId, role, scopes, token, nonce, platform, deviceFamily | deviceId, clientId, **clientMode**, role, scopes, signedAtMs, token, nonce, platform, deviceFamily |
| 分隔符 | 未明确 | **竖线 `\|`** |
| scopes | 未明确格式 | **逗号分隔的字符串** |
| token | 未明确空值 | **空字符串（`""`）当无 token 时** |
| platform/deviceFamily | 原样传入 | **ASCII 小写 + trim**（`normalizeDeviceMetadataForAuth`）|

**完整 v3 载荷构建**（已确认）:

```typescript
export function buildDeviceAuthPayloadV3(params: DeviceAuthPayloadV3Params): string {
  const scopes = params.scopes.join(",");
  const token = params.token ?? "";
  const platform = normalizeDeviceMetadataForAuth(params.platform);   // ASCII lowercase + trim
  const deviceFamily = normalizeDeviceMetadataForAuth(params.deviceFamily); // ASCII lowercase + trim
  return [
    "v3",
    params.deviceId,
    params.clientId,
    params.clientMode,  // ← 文档中未提及！
    params.role,
    scopes,
    String(params.signedAtMs),
    token,
    params.nonce,
    platform,
    deviceFamily,
  ].join("|");
}
```

**v2 载荷**（向后兼容，仍被接受）:

```
v2|{deviceId}|{clientId}|{clientMode}|{role}|{scopes}|{signedAtMs}|{token}|{nonce}
```

**签名 token 优先级**（`resolveSignatureToken`）:

```
auth.token → auth.deviceToken → auth.bootstrapToken → null
```

---

## 3. 握手流程

### ✅ 一致: connect.challenge

源码确认（`src/gateway/server/ws-connection.ts:173-179`）：

```typescript
const connectNonce = randomUUID();
send({
  type: "event",
  event: "connect.challenge",
  payload: { nonce: connectNonce, ts: Date.now() },
});
```

### ✅ 一致: connect 参数结构

源码 `ConnectParamsSchema` 与文档一致，但有额外字段：

| 字段 | 文档提及 | 源码存在 | 必需 |
|------|----------|----------|------|
| `minProtocol` / `maxProtocol` | ✅ | ✅ | ✅ |
| `client.id` | ✅ | ✅ | ✅（枚举值）|
| `client.version` | ✅ | ✅ | ✅ |
| `client.platform` | ✅ | ✅ | ✅ |
| `client.mode` | ✅ | ✅ | ✅（枚举值）|
| `client.displayName` | ❌ | ✅ | 可选 |
| `client.deviceFamily` | ✅ | ✅ | 可选 |
| `client.modelIdentifier` | ❌ | ✅ | 可选 |
| `client.instanceId` | ✅ | ✅ | 可选 |
| `role` | ✅ | ✅ | 可选 |
| `scopes` | ✅ | ✅ | 可选 |
| `caps` / `commands` / `permissions` | ✅ | ✅ | 可选 |
| `auth.token` | ✅ | ✅ | 可选 |
| `auth.bootstrapToken` | ❌ | ✅ | 可选 |
| `auth.deviceToken` | ❌ | ✅ | 可选 |
| `auth.password` | ❌ | ✅ | 可选 |
| `device.*` | ✅ | ✅ | 可选 |
| `pathEnv` | ❌ | ✅ | 可选 |

### ✅ 一致: hello-ok 响应结构

### 📝 补充: hello-ok 包含更多字段

源码 `HelloOkSchema` 包含文档未详述的字段：

```typescript
HelloOk = {
  type: "hello-ok",
  protocol: 3,
  server: {
    version: "x.y.z",    // ← 文档未提及
    connId: "uuid"        // ← 文档未提及
  },
  features: {
    methods: [...],       // ← 文档未提及：可用方法列表
    events: [...]         // ← 文档未提及：可用事件列表
  },
  snapshot: {             // ← 文档未提及：初始状态快照
    presence: [...],
    health: {...},
    stateVersion: { presence: N, health: N },
    uptimeMs: N,
    sessionDefaults: {
      defaultAgentId: "main",
      mainKey: "main",
      mainSessionKey: "agent:main:main"
    },
    authMode: "token" | "password" | "none" | "trusted-proxy"
  },
  canvasHostUrl: "...",   // ← 文档未提及
  auth: { deviceToken, role, scopes, issuedAtMs },
  policy: {
    maxPayload: N,        // ← 文档未提及
    maxBufferedBytes: N,  // ← 文档未提及
    tickIntervalMs: N
  }
}
```

**对 ClawChat 的影响**:
- `features.methods` 可用于动态功能发现（客户端检查 Gateway 是否支持特定方法）
- `snapshot` 提供连接后的初始状态，免去额外查询
- `sessionDefaults` 提供默认会话键，客户端不需要硬编码
- `policy.maxPayload` 限制单帧最大大小

---

## 4. 认证方式

### ✅ 一致: Token / DeviceToken / 密码认证

源码确认四种认证方式：
1. `token` — Gateway Token
2. `deviceToken` — 配对后的设备令牌
3. `bootstrapToken` — Setup Code 中的引导令牌
4. `password` — 密码认证

### 📝 补充: 认证优先级

源码中的认证评估顺序（`resolveAuthProvidedKind`）：

```
password → token → bootstrapToken → deviceToken → none
```

### ✅ 一致: Bootstrap Token

- TTL: **10 分钟**（`DEVICE_BOOTSTRAP_TOKEN_TTL_MS = 10 * 60 * 1000`），文档说 5 分钟指的是配对请求超时
- 单次使用（验证后立即从存储中删除）

### ✅ 一致: 错误码

源码 `ConnectErrorDetailCodes` 确认了文档中所有错误码，并新增了：

| 额外错误码 | 说明 |
|------------|------|
| `AUTH_REQUIRED` | 缺少任何认证 |
| `AUTH_UNAUTHORIZED` | 通用未授权 |
| `AUTH_TOKEN_MISSING` | Token 未提供 |
| `AUTH_TOKEN_NOT_CONFIGURED` | 服务端未配置 Token |
| `AUTH_PASSWORD_MISSING` | 密码未提供 |
| `AUTH_PASSWORD_MISMATCH` | 密码不匹配 |
| `AUTH_PASSWORD_NOT_CONFIGURED` | 服务端未配置密码 |
| `AUTH_BOOTSTRAP_TOKEN_INVALID` | Bootstrap Token 无效 |
| `AUTH_DEVICE_TOKEN_MISMATCH` | DeviceToken 不匹配 |
| `AUTH_RATE_LIMITED` | 认证请求被限流 |
| `AUTH_TAILSCALE_*` | Tailscale 身份相关错误（4 个） |
| `CONTROL_UI_*` | Control UI 特定错误（2 个） |
| `DEVICE_IDENTITY_REQUIRED` | 缺少设备身份 |
| `DEVICE_AUTH_INVALID` | 通用设备认证无效 |
| `PAIRING_REQUIRED` | 需要配对 |

---

## 5. 消息格式

### 🚨 不一致 #3: chat.send 需要 idempotencyKey

| 维度 | 文档 | 源码实际 |
|------|------|----------|
| idempotencyKey | 提到"副作用方法需要幂等键" | `ChatSendParamsSchema` 中 **idempotencyKey 是必需字段** |

```typescript
export const ChatSendParamsSchema = Type.Object({
  sessionKey: ChatSendSessionKeyString,
  message: Type.String(),
  thinking: Type.Optional(Type.String()),
  deliver: Type.Optional(Type.Boolean()),
  attachments: Type.Optional(Type.Array(Type.Unknown())),
  timeoutMs: Type.Optional(Type.Integer({ minimum: 0 })),
  systemInputProvenance: Type.Optional(InputProvenanceSchema),
  systemProvenanceReceipt: Type.Optional(Type.String()),
  idempotencyKey: NonEmptyString,  // ← 必需！
});
```

### 🚨 不一致 #4: ChatEvent 结构与文档描述不同

源码中 `ChatEventSchema` 的实际结构：

```typescript
ChatEvent = {
  runId: string,           // ← 文档未提及
  sessionKey: string,
  seq: integer,            // ← 文档未提及
  state: "delta" | "final" | "aborted" | "error",  // ← 文档未提及
  message: unknown,        // ← 与文档 content/role 不同
  errorMessage: string?,   // ← 文档未提及
  usage: unknown?,         // ← 文档未提及
  stopReason: string?,     // ← 文档未提及
}
```

**对比**：

| 字段 | 文档中假设 | 源码实际 |
|------|-----------|----------|
| 消息内容 | `content: string` | `message: unknown`（结构化对象）|
| 角色 | `role: string` | 在 `message` 内部 |
| 状态 | 无 | `state: delta/final/aborted/error` |
| 运行 ID | 无 | `runId: string` |
| 序列号 | 无 | `seq: integer` |
| 用量 | 无 | `usage: unknown` |
| 停止原因 | 无 | `stopReason: string` |

### ✅ 一致: chat.history

```typescript
ChatHistoryParams = {
  sessionKey: string,    // 必需
  limit: integer?        // 可选，1-1000
}
```

### ✅ 一致: chat.inject

```typescript
ChatInjectParams = {
  sessionKey: string,    // 必需
  message: string,       // 必需
  label: string?         // 可选，≤100 字符
}
```

### ✅ 一致: chat.abort

新发现的方法（文档中为 `/stop` 命令）：

```typescript
ChatAbortParams = {
  sessionKey: string,    // 必需
  runId: string?         // 可选
}
```

### 📝 补充: chat.send 额外参数

| 参数 | 说明 |
|------|------|
| `thinking` | 思考级别覆盖 |
| `deliver` | 是否立即投递 |
| `timeoutMs` | 超时（毫秒） |
| `systemInputProvenance` | 输入来源追踪 |

---

## 6. 会话管理

### ✅ 一致: sessions.list

源码确认方法名和参数：

```typescript
SessionsListParams = {
  limit: integer?,
  activeMinutes: integer?,
  includeGlobal: boolean?,
  includeUnknown: boolean?,
  includeDerivedTitles: boolean?,   // ← 文档未提及
  includeLastMessage: boolean?,     // ← 文档未提及
  label: string?,
  spawnedBy: string?,
  agentId: string?,
  search: string?,                  // ← 文档未提及
}
```

### ✅ 一致: sessions.reset / sessions.delete

```typescript
SessionsResetParams = { key: string, reason?: "new" | "reset" }
SessionsDeleteParams = { key: string, deleteTranscript?: boolean }
```

### ✅ 一致: sessions.patch

支持更新会话的多种属性（model、label、thinkingLevel、fastMode 等）。

### 📝 补充: 额外会话方法

| 方法 | 说明 | 文档提及 |
|------|------|----------|
| `sessions.list` | 列出会话 | ✅ |
| `sessions.resolve` | 按条件查找会话 | ❌ |
| `sessions.preview` | 获取会话预览 | ❌ |
| `sessions.patch` | 更新会话属性 | ❌ |
| `sessions.reset` | 重置会话 | ✅（通过 `/new`）|
| `sessions.delete` | 删除会话 | ❌ |
| `sessions.compact` | 压缩会话 | ✅（通过 `/compact`）|
| `sessions.usage` | 使用量统计 | ❌ |

---

## 7. 设备管理

### ✅ 一致: 设备配对 API

源码确认：

```typescript
// 方法
DevicePairListParams = {}
DevicePairApproveParams = { requestId: string }
DevicePairRejectParams = { requestId: string }
DevicePairRemoveParams = { deviceId: string }

// Token 管理
DeviceTokenRotateParams = { deviceId: string, role: string, scopes?: string[] }
DeviceTokenRevokeParams = { deviceId: string, role: string }

// 事件
DevicePairRequestedEvent = {
  requestId, deviceId, publicKey, displayName?,
  platform?, deviceFamily?, clientId?, clientMode?,
  role?, roles?, scopes?, remoteIp?, silent?, isRepair?, ts
}
DevicePairResolvedEvent = { requestId, deviceId, decision, ts }
```

---

## 8. 客户端标识

### 🚨 不一致 #5: client.id 是枚举，不能自定义

| 维度 | 文档假设 | 源码实际 |
|------|----------|----------|
| client.id | 自由字符串 | **枚举白名单** |

源码中 `GATEWAY_CLIENT_IDS` 的合法值：

```typescript
GATEWAY_CLIENT_IDS = {
  WEBCHAT_UI: "webchat-ui",
  CONTROL_UI: "openclaw-control-ui",
  WEBCHAT: "webchat",
  CLI: "cli",
  GATEWAY_CLIENT: "gateway-client",
  MACOS_APP: "openclaw-macos",
  IOS_APP: "openclaw-ios",
  ANDROID_APP: "openclaw-android",    // ← ClawChat 必须使用这个
  NODE_HOST: "node-host",
  TEST: "test",
  FINGERPRINT: "fingerprint",
  PROBE: "openclaw-probe",
}
```

`client.mode` 也是枚举：

```typescript
GATEWAY_CLIENT_MODES = {
  WEBCHAT: "webchat",
  CLI: "cli",
  UI: "ui",
  BACKEND: "backend",
  NODE: "node",
  PROBE: "probe",
  TEST: "test",
}
```

**对 ClawChat 的影响**:
- `client.id` **必须**是 `"openclaw-android"`
- `client.mode` 作为 operator 时应为 `"ui"`，作为 node 时应为 `"node"`
- 不能使用自定义 client ID

---

## 9. 对 ClawChat 实现的具体影响

### 9.1 密钥实现方案（替代 Android Keystore ECDSA）

由于 Android Keystore 不支持 Ed25519，推荐方案：

**方案 A（推荐）: BouncyCastle Ed25519 + EncryptedSharedPreferences**

```kotlin
// 使用 BouncyCastle 生成 Ed25519 密钥对
val keyPair = Ed25519KeyPairGenerator().apply {
    init(Ed25519KeyGenerationParameters(SecureRandom()))
}.generateKeyPair()

// 私钥加密存储在 EncryptedSharedPreferences
// 公钥以 base64url 编码传输（32 字节原始公钥）
```

**方案 B: API 33+ 原生 EdDSA + fallback**

```kotlin
// API 33+ 原生支持
val keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()

// API 26-32: fallback 到 BouncyCastle
```

### 9.2 签名载荷构建

```kotlin
fun buildV3Payload(
    deviceId: String,
    clientId: String,       // 必须是 "openclaw-android"
    clientMode: String,     // "ui" 或 "node"
    role: String,
    scopes: List<String>,
    signedAtMs: Long,
    token: String?,
    nonce: String,
    platform: String?,      // ASCII lowercase
    deviceFamily: String?   // ASCII lowercase
): String {
    val scopeStr = scopes.joinToString(",")
    val tokenStr = token ?: ""
    val platformStr = (platform?.trim() ?: "").lowercase()
    val familyStr = (deviceFamily?.trim() ?: "").lowercase()
    return listOf(
        "v3", deviceId, clientId, clientMode, role, scopeStr,
        signedAtMs.toString(), tokenStr, nonce, platformStr, familyStr
    ).joinToString("|")
}
```

### 9.3 ChatEvent 处理

```kotlin
// 必须处理四种状态
when (chatEvent.state) {
    "delta" -> // 流式更新（部分内容）
    "final" -> // 最终完成
    "aborted" -> // 用户中止
    "error" -> // 错误，检查 errorMessage
}

// message 字段是结构化对象，不是纯字符串
// 需要解析其内部结构（role, content 等）
```

### 9.4 chat.send 必须包含 idempotencyKey

```kotlin
fun sendMessage(sessionKey: String, message: String): GatewayMessage {
    return GatewayMessage(
        type = "req",
        id = UUID.randomUUID().toString(),
        method = "chat.send",
        params = mapOf(
            "sessionKey" to sessionKey,
            "message" to message,
            "idempotencyKey" to UUID.randomUUID().toString() // 必需！
        )
    )
}
```

---

## 10. 验证结论

### 必须修正的错误（阻塞实现）

| # | 错误 | 严重程度 | 修正方案 |
|---|------|----------|----------|
| 1 | 密钥类型应为 Ed25519 而非 ECDSA | 🚨 **阻塞** | 使用 BouncyCastle 或 API 33+ EdDSA |
| 2 | 签名载荷缺少 clientMode 字段 | 🚨 **阻塞** | 在 v3 载荷中加入 clientMode |
| 3 | chat.send 必须包含 idempotencyKey | 🚨 **阻塞** | 每次发送生成 UUID |
| 4 | ChatEvent 结构与假设不同 | 🚨 **阻塞** | 按源码结构重新实现解析 |
| 5 | client.id 必须是 "openclaw-android" | ⚠️ 重要 | 硬编码枚举值 |

### 应补充到架构文档的信息

1. hello-ok 包含 `features.methods`/`features.events`（可用于功能发现）
2. hello-ok 包含 `snapshot`（连接后初始状态）
3. hello-ok 包含 `sessionDefaults`（默认会话键）
4. `sessions.resolve` / `sessions.preview` / `sessions.usage` 等额外会话方法
5. `chat.abort` 方法（对应 `/stop` 命令）
6. Bootstrap Token TTL 是 10 分钟（非 5 分钟）
7. 公钥传输使用 base64url 编码的 32 字节原始公钥
8. platform/deviceFamily 在签名前做 ASCII lowercase 规范化

### 现有规格文档受影响范围

| 文档 | 需要更新的部分 |
|------|---------------|
| `clawchat-setup.md` | §3.3 密钥管理（ECDSA→Ed25519）|
| `gateway-protocol-spec.md` | §2 握手、§4 认证、§7 消息格式 |
| `client-reference-analysis.md` | §2.5 关键设计决策 |
| `clawchat-requirements.md` | §4 技术约束 |
| `architecture.md` | §3.5 安全模块接口、§5.3 WebSocket 实现 |
| `src/domain/model/` | Message 模型需适配 ChatEvent 结构 |
| `src/network/GatewayMessage.kt` | 整个文件需要按源码重写 |
