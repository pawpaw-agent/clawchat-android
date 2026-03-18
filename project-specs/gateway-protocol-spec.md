# OpenClaw Gateway 协议规格

> 基于 docs.openclaw.ai 和本地文档的完整分析  
> **版本**: Protocol v3  
> **分析日期**: 2026-03-18  
> **来源**: gateway/protocol, concepts/session, concepts/messages, concepts/streaming, channels/pairing, platforms/android, platforms/ios, cli/devices

---

## 1. 传输层

### 1.1 基本信息

- **传输**: WebSocket（文本帧，JSON 载荷）
- **默认端口**: 18789
- **协议版本**: v3（客户端发送 `minProtocol` / `maxProtocol`，服务端拒绝不匹配的版本）
- **TLS**: 可选，支持证书固定（`gateway.tls` + `tlsFingerprint`）
- **心跳**: `tickIntervalMs`（服务端在 `hello-ok` 中下发，通常 15000ms）

### 1.2 帧格式

三种帧类型：

| 类型 | 格式 | 说明 |
|------|------|------|
| **Request** | `{ type: "req", id, method, params }` | 客户端→服务端 RPC 调用 |
| **Response** | `{ type: "res", id, ok, payload \| error }` | 服务端→客户端 RPC 响应 |
| **Event** | `{ type: "event", event, payload, seq?, stateVersion? }` | 服务端→客户端推送事件 |

- `id` 为请求唯一标识，用于关联 req/res
- 副作用方法需要幂等键（idempotency key）
- `seq` 和 `stateVersion` 用于事件排序和状态版本控制

---

## 2. 握手流程（connect）

### 2.1 完整时序

```
Client                          Gateway
  │                               │
  │      (WebSocket 建立连接)      │
  │                               │
  │  ◄──── connect.challenge ──── │  Event: nonce + ts
  │                               │
  │  ────── connect (req) ──────► │  签名 nonce，提供身份信息
  │                               │
  │  ◄──── hello-ok (res) ────── │  协议版本、策略、可选 deviceToken
  │                               │
```

### 2.2 挑战 (connect.challenge)

Gateway 在 WebSocket 连接建立后立即发送：

```json
{
  "type": "event",
  "event": "connect.challenge",
  "payload": {
    "nonce": "<随机字符串>",
    "ts": 1737264000000
  }
}
```

### 2.3 连接请求 (connect)

客户端必须签名挑战 nonce 后发送 connect 请求：

```json
{
  "type": "req",
  "id": "<uuid>",
  "method": "connect",
  "params": {
    "minProtocol": 3,
    "maxProtocol": 3,
    "client": {
      "id": "openclaw-android",
      "version": "1.0.0",
      "platform": "android",
      "mode": "operator"
    },
    "role": "operator",
    "scopes": ["operator.read", "operator.write"],
    "caps": [],
    "commands": [],
    "permissions": {},
    "auth": { "token": "<gateway_token>" },
    "locale": "zh-CN",
    "userAgent": "openclaw-android/1.0.0",
    "device": {
      "id": "<设备指纹_SHA256>",
      "publicKey": "<PEM格式公钥>",
      "signature": "<签名后的nonce>",
      "signedAt": 1737264000000,
      "nonce": "<服务端下发的nonce>"
    }
  }
}
```

#### 签名载荷格式

**v3 签名载荷**（推荐）：

签名覆盖字段：`device.id` + `client.id` + `role` + `scopes` + `token` + `nonce` + `platform` + `deviceFamily`

v3 相比 v2 新增了 `platform` 和 `deviceFamily` 绑定。

#### Node 角色连接

Node 角色用于暴露设备能力（摄像头、Canvas 等）：

```json
{
  "role": "node",
  "scopes": [],
  "caps": ["camera", "canvas", "screen", "location", "voice"],
  "commands": ["camera.snap", "canvas.navigate", "screen.record", "location.get"],
  "permissions": { "camera.capture": true, "screen.record": false }
}
```

### 2.4 连接响应 (hello-ok)

```json
{
  "type": "res",
  "id": "<同请求id>",
  "ok": true,
  "payload": {
    "type": "hello-ok",
    "protocol": 3,
    "policy": {
      "tickIntervalMs": 15000
    },
    "auth": {
      "deviceToken": "<设备令牌>",
      "role": "operator",
      "scopes": ["operator.read", "operator.write"]
    }
  }
}
```

- `auth.deviceToken` 仅在配对成功/首次连接时返回
- 客户端必须持久化 `deviceToken` 用于后续重连

---

## 3. 角色与权限

### 3.1 角色

| 角色 | 说明 | 典型客户端 |
|------|------|-----------|
| `operator` | 控制面客户端（读写消息、管理会话） | CLI、Web UI、Android/iOS（operator 模式）|
| `node` | 能力主机（暴露摄像头、屏幕、Canvas） | iOS/Android Node、Headless Node |

### 3.2 Operator 权限范围

| Scope | 说明 |
|-------|------|
| `operator.read` | 读取会话、消息、状态 |
| `operator.write` | 发送消息、创建会话 |
| `operator.admin` | 管理配置（`/config set`、`/config unset`）|
| `operator.approvals` | 处理 exec 审批 |
| `operator.pairing` | 设备配对管理 |

### 3.3 Node 能力声明

Node 在 connect 时声明 caps/commands/permissions，Gateway 将其视为**声明**并执行服务端白名单。

---

## 4. 认证体系

### 4.1 认证方式

| 方式 | 说明 | 优先级 |
|------|------|--------|
| Gateway Token | `OPENCLAW_GATEWAY_TOKEN` 或 `--token` | 必须匹配 |
| 设备令牌 | 配对成功后 Gateway 签发的 deviceToken | 重连使用 |
| 设备签名 | ECDSA secp256r1 签名挑战 nonce | 身份验证 |
| 密码认证 | `gateway.auth.password` | 备选 |

### 4.2 认证错误码

| 错误码 | 含义 | 恢复建议 |
|--------|------|----------|
| `AUTH_TOKEN_MISMATCH` | Token 不匹配 | 尝试 deviceToken 重试 |
| `DEVICE_AUTH_NONCE_REQUIRED` | 缺少 nonce | 等待 challenge 后再签名 |
| `DEVICE_AUTH_NONCE_MISMATCH` | nonce 不匹配 | 使用最新的 challenge nonce |
| `DEVICE_AUTH_SIGNATURE_INVALID` | 签名无效 | 检查签名载荷格式（v3）|
| `DEVICE_AUTH_SIGNATURE_EXPIRED` | 签名过期 | 重新签名 |
| `DEVICE_AUTH_DEVICE_ID_MISMATCH` | 设备 ID 与公钥不匹配 | 重新生成密钥对 |
| `DEVICE_AUTH_PUBLIC_KEY_INVALID` | 公钥格式错误 | 检查 PEM 格式 |

错误响应包含恢复提示：
- `error.details.canRetryWithDeviceToken` (boolean)
- `error.details.recommendedNextStep`（如 `retry_with_device_token`、`update_auth_credentials` 等）

### 4.3 Token 管理 API

| 方法 | 权限 | 说明 |
|------|------|------|
| `device.token.rotate` | `operator.pairing` | 轮换设备令牌 |
| `device.token.revoke` | `operator.pairing` | 撤销设备令牌 |

---

## 5. 设备配对

### 5.1 Setup Code 配对（推荐）

Setup Code 是 Base64 编码的 JSON 载荷：

```json
{
  "url": "ws://192.168.1.100:18789",
  "bootstrapToken": "<短期单设备引导令牌>"
}
```

流程：
1. 管理员在 Telegram 发送 `/pair` → 获得 Setup Code
2. Android App 输入 Setup Code → 解析 URL + bootstrapToken
3. App 使用 bootstrapToken 发起 WebSocket 连接
4. Gateway 创建 pending 配对请求
5. 管理员在 Telegram 发送 `/pair approve`（或 CLI `openclaw devices approve`）
6. App 收到 deviceToken → 持久化 → 正式连接

### 5.2 手动配对

1. App 手动输入 Gateway host:port
2. App 生成密钥对 → 发送 connect 请求
3. Gateway 创建 pending 配对请求
4. CLI: `openclaw devices approve <requestId>`
5. App 收到 deviceToken

### 5.3 配对状态

| 状态 | 超时 | 客户端行为 |
|------|------|-----------|
| `pending` | 5 分钟 | 轮询或等待事件 |
| `approved` | - | 保存 token，建立正式连接 |
| `rejected` | - | 显示拒绝信息 |
| `expired` | 5 分钟后 | 提示重试 |

### 5.4 配对 API

| 方法/事件 | 类型 | 说明 |
|-----------|------|------|
| `node.pair.request` | req | 发起配对请求（幂等） |
| `node.pair.list` | req | 列出 pending + paired |
| `node.pair.approve` | req | 批准配对（签发 token） |
| `node.pair.reject` | req | 拒绝配对 |
| `node.pair.verify` | req | 验证 nodeId + token |
| `node.pair.requested` | event | 新配对请求通知 |
| `node.pair.resolved` | event | 配对结果通知 |

### 5.5 设备管理 CLI

```bash
openclaw devices list                    # 列出所有设备
openclaw devices approve [requestId]     # 批准（省略则批准最新）
openclaw devices reject <requestId>      # 拒绝
openclaw devices rotate --device <id> --role operator  # 轮换 token
openclaw devices revoke --device <id> --role node      # 撤销 token
openclaw devices remove <deviceId>       # 移除设备
openclaw devices clear --yes             # 清除所有
```

---

## 6. 会话管理

### 6.1 会话模型

Gateway 是会话的**唯一 Source of Truth**。客户端不维护独立会话状态。

#### 会话键格式

| 场景 | 键格式 |
|------|--------|
| DM (main) | `agent:<agentId>:<mainKey>` |
| DM (per-peer) | `agent:<agentId>:direct:<peerId>` |
| DM (per-channel-peer) | `agent:<agentId>:<channel>:direct:<peerId>` |
| 群组 | `agent:<agentId>:<channel>:group:<id>` |
| Cron | `cron:<job.id>` |
| Webhook | `hook:<uuid>` |
| WebChat | `agent:<agentId>:main`（共享主会话）|

#### 会话元数据

```json
{
  "sessionKey": "agent:main:main",
  "sessionId": "<uuid>",
  "updatedAt": 1737264000000,
  "model": "qwen3.5-plus",
  "inputTokens": 1234,
  "outputTokens": 567,
  "totalTokens": 1801,
  "contextTokens": 890,
  "displayName": "Main Session",
  "channel": "webchat",
  "origin": {
    "label": "WebChat",
    "provider": "webchat",
    "from": "...",
    "to": "..."
  }
}
```

### 6.2 会话 API

| 方法 | 说明 |
|------|------|
| `sessions.list` | 列出所有会话 |
| `chat.history` | 获取会话消息历史（有截断保护） |
| `chat.send` | 发送消息到会话 |
| `chat.inject` | 注入助手消息（无 agent run） |
| `chat.subscribe` | 订阅会话推送更新 |

### 6.3 会话生命周期

- **每日重置**: 默认凌晨 4:00（Gateway 本地时间）
- **空闲重置**: 可选，`session.idleMinutes`
- **手动重置**: 发送 `/new` 或 `/reset`
- **会话维护**: 自动裁剪过期会话（`session.maintenance`）

---

## 7. 消息收发

### 7.1 消息流

```
用户输入 → 路由/绑定 → session key → 队列（如有活跃 run）→ agent run → 回复
```

### 7.2 发送消息

```json
{
  "type": "req",
  "id": "<uuid>",
  "method": "chat.send",
  "params": {
    "sessionKey": "agent:main:main",
    "message": "你好",
    "attachments": []
  }
}
```

### 7.3 接收消息（事件推送）

订阅后通过事件接收：

```json
{
  "type": "event",
  "event": "chat",
  "payload": {
    "sessionKey": "agent:main:main",
    "role": "assistant",
    "content": "你好！有什么可以帮你的吗？",
    "model": "qwen3.5-plus",
    "timestamp": 1737264000000
  }
}
```

### 7.4 消息历史

```json
{
  "type": "req",
  "id": "<uuid>",
  "method": "chat.history",
  "params": {
    "sessionKey": "agent:main:main"
  }
}
```

响应包含消息列表。Gateway 可能截断超长文本、省略重元数据、替换超大条目为占位符。

### 7.5 流式输出

**无真正 token 级流式** 到频道消息。两种流式层：

| 层 | 机制 | 说明 |
|----|------|------|
| Block Streaming | 完成的文本块逐个发送 | 粗粒度，普通消息 |
| Preview Streaming | 临时预览消息（send + edit） | Telegram/Discord/Slack |

模式：`off` / `partial` / `block` / `progress`

### 7.6 Typing 指示器

| 模式 | 触发时机 |
|------|----------|
| `never` | 不显示 |
| `instant` | 模型循环开始时 |
| `thinking` | 第一个推理 delta |
| `message` | 第一个非静默文本 delta |

### 7.7 消息队列

活跃 run 期间的新消息可以：
- `interrupt`: 中断当前 run
- `steer`: 引导当前 run
- `followup`: 排队等待下一轮
- `collect`: 收集合并

---

## 8. Exec 审批

| 方法/事件 | 说明 |
|-----------|------|
| `exec.approval.requested` | 广播：exec 需要审批 |
| `exec.approval.resolve` | 解决审批（需 `operator.approvals` scope）|

Node 上的 exec 请求必须包含 `systemRunPlan`（canonical argv/cwd/rawCommand）。

---

## 9. 设备存活与发现

### 9.1 Presence API

| 方法 | 说明 |
|------|------|
| `system-presence` | 获取所有连接设备的存活信息 |

Presence 条目包含：`instanceId`、`host`、`ip`、`version`、`deviceFamily`、`mode`、`lastInputSeconds`、`ts`

- TTL: 5 分钟（超时自动清理）
- 上限: 200 条

### 9.2 Gateway 发现

| 方式 | 协议 | 说明 |
|------|------|------|
| Bonjour/mDNS | `_openclaw-gw._tcp` on `local.` | 局域网自动发现 |
| Tailscale DNS-SD | 自定义域（如 `openclaw.internal.`） | 跨网络发现 |
| 手动配置 | host:port | 兜底方案 |

---

## 10. 其他 API

### 10.1 系统 API

| 方法 | 说明 |
|------|------|
| `system-presence` | 获取在线设备列表 |
| `tools.catalog` | 获取 agent 工具目录 |
| `skills.bins` | 获取 skill 可执行文件列表 |
| `gateway.identity.get` | 获取 Gateway 身份（用于 relay 注册）|

### 10.2 Node 调用

| 方法 | 说明 |
|------|------|
| `node.list` | 列出已连接 node |
| `node.invoke` | 调用 node 命令（camera.snap, canvas.navigate 等）|
| `node.pair.*` | 配对管理 |

### 10.3 模型相关

| 方法 | 说明 |
|------|------|
| `models.list` | 列出可用模型 |
| `models.status` | 模型认证状态 |

---

## 11. 错误处理

### 11.1 错误响应格式

```json
{
  "type": "res",
  "id": "<同请求id>",
  "ok": false,
  "error": {
    "message": "device nonce mismatch",
    "details": {
      "code": "DEVICE_AUTH_NONCE_MISMATCH",
      "reason": "device-nonce-mismatch",
      "canRetryWithDeviceToken": true,
      "recommendedNextStep": "retry_with_device_token"
    }
  }
}
```

### 11.2 重连策略

- `AUTH_TOKEN_MISMATCH` + `canRetryWithDeviceToken=true` → 使用缓存 deviceToken 重试一次
- 重试失败 → 停止自动重连，提示用户操作
- 一般连接失败 → 指数退避重连

---

## 12. 对 ClawChat Android 的关键约束

### 12.1 必须实现

1. **等待 connect.challenge** → 签名 nonce → 发送 connect
2. **v3 签名载荷**（绑定 platform + deviceFamily）
3. **持久化 deviceToken**（EncryptedSharedPreferences）
4. **ECDSA secp256r1 密钥对**（Android Keystore）
5. **Gateway 是 source of truth**（不维护独立会话状态）
6. **chat.history / chat.send / chat.subscribe** 三件套
7. **Setup Code 解析**（Base64 → JSON → url + bootstrapToken）

### 12.2 可选实现

1. Bonjour/NSD 发现
2. Block Streaming（粗粒度流式）
3. Typing 指示器
4. Exec 审批处理
5. Node 能力声明（camera、canvas 等）

### 12.3 安全红线

1. 所有连接必须包含 device 身份信息
2. 必须签名 server-provided challenge nonce
3. deviceToken 必须加密存储
4. 不得在日志中输出 token/signature
5. 禁止 Google Cloud 备份敏感数据
