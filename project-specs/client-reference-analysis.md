# OpenClaw 官方客户端参考分析

> 基于文档和源码的架构分析  
> **分析日期**: 2026-03-18  
> **来源**: docs.openclaw.ai, github.com/openclaw/openclaw (apps/android, platforms/ios, web/webchat)

---

## 1. 客户端矩阵

| 客户端 | 角色 | 平台 | 连接方式 | 状态 |
|--------|------|------|----------|------|
| CLI | operator | macOS/Linux/Windows | WebSocket | 稳定 |
| macOS App | operator + node | macOS | WebSocket + XPC | 稳定 |
| iOS App | operator + node | iOS | WebSocket | 内部预览 |
| Android App | operator + node | Android | WebSocket | 源码可用，未发布 |
| WebChat | operator | 浏览器 | WebSocket | 稳定（Gateway 内嵌）|
| Control UI | operator | 浏览器 | WebSocket | 稳定 |
| TUI | operator | 终端 | WebSocket | 稳定 |

---

## 2. iOS App 架构分析

### 2.1 定位

- **双角色**: operator（聊天）+ node（设备能力）
- **Swift/SwiftUI 原生**: 使用 Apple 平台 API
- **不托管 Gateway**: Gateway 必须运行在其他设备上

### 2.2 核心能力

| 能力 | 说明 |
|------|------|
| WebSocket 连接 | 直连 Gateway，支持 LAN + Tailscale |
| 设备配对 | Setup Code / 手动配置 → 设备审批 |
| 聊天 | chat.history / chat.send / chat.subscribe |
| Canvas | WKWebView 渲染，支持 A2UI |
| 摄像头 | camera.snap (jpg), camera.clip (mp4) |
| 位置 | location.get |
| 语音 | Talk mode + Voice wake |
| 推送 | APNs relay（官方构建）/ 直接 APNs（开发构建）|

### 2.3 连接架构

```
iOS App
  ├── Operator Session (WebSocket)
  │     ├── chat.send / chat.history
  │     ├── chat.subscribe → 实时消息
  │     └── gateway.identity.get（用于 relay 注册）
  │
  ├── Node Session (WebSocket)
  │     ├── caps: camera, canvas, screen, location, voice
  │     ├── commands: camera.snap, canvas.navigate, ...
  │     └── node.invoke 接收 + 执行
  │
  └── APNs Push (后台唤醒)
        ├── 官方: relay-backed（App Attest + Receipt 验证）
        └── 开发: 直接 APNs 密钥
```

### 2.4 发现机制

| 方式 | 协议 | 优先级 |
|------|------|--------|
| Bonjour | `_openclaw-gw._tcp` on `local.` | 1 (LAN) |
| Tailscale DNS-SD | 自定义域 unicast | 2 (跨网) |
| 手动 host:port | 直接输入 | 3 (兜底) |

### 2.5 关键设计决策

1. **双 WebSocket 连接**: operator + node 分开连接
2. **Keychain 存储**: 配对 token 存储在 iOS Keychain
3. **前台限制**: camera/canvas/screen 命令要求 App 在前台
4. **relay 架构**: 官方构建不直接持有 APNs 密钥，通过 relay 中转

---

## 3. Android App 架构分析

### 3.1 定位

- **双角色**: operator + node（与 iOS 对齐）
- **源码可用**: `apps/android`，Java 17 + Android SDK
- **前台服务**: 通过 Foreground Service 保持连接

### 3.2 核心能力

| 能力 | 说明 |
|------|------|
| WebSocket 连�� | 直连 Gateway，LAN + Tailscale |
| 设备配对 | Setup Code / 手动配置 |
| 聊天 | chat.history / chat.send / chat.subscribe |
| Canvas | WebView 渲染 |
| 摄像头 | camera.snap (jpg), camera.clip (mp4) |
| 语音 | 麦克风 on/off + TTS 播放（ElevenLabs / 系统） |
| 扩展命令 | device.*, notifications.*, photos.*, contacts.*, calendar.*, motion.* |

### 3.3 独特的 Android 命令面

Android 暴露了比 iOS 更丰富的 node 命令：

```
device.status, device.info, device.permissions, device.health
notifications.list, notifications.actions
photos.latest
contacts.search, contacts.add
calendar.events, calendar.add
motion.activity, motion.pedometer
```

### 3.4 连接保活

- **Foreground Service**: 持久通知保持 WebSocket 连接
- **自动重连**: 配对后 App 启动自动重连
- **优先级**: 手动端点 > 上次发现的 Gateway

---

## 4. WebChat / Control UI 架构分析

### 4.1 定位

- **纯 WebSocket 客户端**: 无独立后端，直接连 Gateway
- **原生 SwiftUI（macOS/iOS）** 或 **浏览器 HTML**
- **确定性路由**: 回复始终路由回 WebChat

### 4.2 使用的 API

| API | 用途 |
|-----|------|
| `chat.history` | 获取消息历史（有截断保护）|
| `chat.send` | 发送消息 |
| `chat.inject` | 注入助手消息（无 agent run）|
| `tools.catalog` | 获取工具目录（Control UI Tools 面板）|
| `system-presence` | 获取在线实例列表 |

### 4.3 关键行为

- **Gateway 不可达时只读**
- **历史始终从 Gateway 获取**（不做本地文件监听）
- **中止的 run 保留部分助手输出**
- **长文本截断**: Gateway 可能截断、省略或替换超大条目

---

## 5. CLI 架构分析

### 5.1 连接模式

- 短连接（单次命令执行）→ 不产生 presence 条目
- 长连接（TUI / 交互模式）→ 产生 presence 条目
- `client.mode = "cli"` 不写入 presence（避免刷屏）

### 5.2 核心命令映射

| CLI 命令 | Gateway 方法 |
|----------|-------------|
| `openclaw sessions` | `sessions.list` |
| `openclaw devices list` | 设备管理 |
| `openclaw devices approve` | `node.pair.approve` |
| `openclaw nodes status` | `node.list` |
| `openclaw gateway call <method>` | 任意 RPC |

---

## 6. 跨客户端共性模式

### 6.1 统一协议层

所有客户端共享同一个 Gateway WebSocket 协议（v3），差异仅在：
- `client.id` / `client.platform` / `client.mode`
- `role` (operator / node)
- `caps` / `commands` / `permissions`（仅 node）

### 6.2 统一状态管理模式

```
所有客户端
  │
  ├── Gateway 是唯一 Source of Truth
  │     ├── 会话状态 → sessions.list
  │     ├── 消息历史 → chat.history
  │     ├── 在线状态 → system-presence
  │     └── Token 计数 → store 字段
  │
  ├── 客户端仅缓存（可选）
  │     ├── 最近消息（性能优化）
  │     └── Gateway 配置（持久化）
  │
  └── 实时更新
        ├── chat.subscribe → 消息推送
        ├── 事件流 → 状态变更
        └── tickIntervalMs → 心跳
```

### 6.3 认证统一流程

```
1. 生成 ECDSA secp256r1 密钥对（首次）
2. 等待 connect.challenge → 获取 nonce
3. v3 签名 nonce
4. 发送 connect → 包含 device 身份
5. 接收 hello-ok → 保存 deviceToken
6. 后续重连使用 deviceToken
```

### 6.4 配对统一流程

```
1. Setup Code（推荐）或手动输入 host:port
2. 使用 bootstrapToken 或直接连接
3. Gateway 创建 pending 请求
4. 管理员审批（CLI / Telegram / macOS UI）
5. Gateway 签发 deviceToken
6. 客户端持久化 token
```

---

## 7. ClawChat 可借鉴的设计

### 7.1 从 iOS App 借鉴

| 特性 | 借鉴要点 |
|------|----------|
| 双角色连接 | operator + node 分开管理 |
| Setup Code 配对 | Base64 解码 → url + bootstrapToken |
| Keychain → Keystore | 安全存储映射 |
| 前台限制 | camera/canvas 命令需 App 可见 |

### 7.2 从 Android 官方 App 借鉴

| 特性 | 借鉴要点 |
|------|----------|
| Foreground Service | 保持 WebSocket 连接 |
| NSD 发现 | Android 原生服务发现 |
| 扩展命令 | device.*/notifications.*/contacts.* 等 |
| 语音 | ElevenLabs TTS + 系统 TTS fallback |

### 7.3 从 WebChat 借鉴

| 特性 | 借鉴要点 |
|------|----------|
| API 三件套 | chat.history + chat.send + chat.subscribe |
| 截断保护 | 处理 Gateway 的截断/省略/替换 |
| 只读降级 | Gateway 不可达时的优雅降级 |
| 中止输出 | 保留部分助手输出 |

### 7.4 不建议照搬

| 特性 | 原因 |
|------|------|
| APNs Relay | Android 使用 FCM，架构不同 |
| XPC 通信 | macOS 特有 |
| WKWebView Canvas | Android 用 WebView 替代 |
| 系统 TTS 优先 | Android 的 TTS 引擎生态不同 |

---

## 8. 结论

### 8.1 ClawChat 的最小可行协议集

**Phase 1（MVP）**:
- `connect`（含 challenge 签名）
- `chat.history` / `chat.send` / `chat.subscribe`
- Setup Code 配对
- `sessions.list`

**Phase 2（增强）**:
- NSD/Bonjour 发现
- Node 能力声明（camera、canvas）
- Block Streaming
- Typing 指示器
- `exec.approval.*`

**Phase 3（完整）**:
- 扩展命令（device.*, notifications.*, contacts.* 等）
- 语音（TTS + 麦克风）
- FCM 推送
- 多 Gateway 管理
