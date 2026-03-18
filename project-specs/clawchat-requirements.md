# ClawChat Android 功能需求

> 基于 Gateway 协议能力和官方客户端参考推导  
> **版本**: 1.0.0  
> **日期**: 2026-03-18  
> **前置文档**: gateway-protocol-spec.md, client-reference-analysis.md

---

## 1. 产品定位修订

基于协议分析，ClawChat 应定位为 **operator 优先的移动客户端**，而非 node 优先。

| 维度 | 原定位 | 修订定位 |
|------|--------|----------|
| 主角色 | 未明确 | **operator**（控制面客户端）|
| 次角色 | 未明确 | node（可选，Phase 2+）|
| 核心功能 | 通信 | **会话管理 + 消息收发** |
| 连接方式 | WebSocket | WebSocket（与协议完全一致）|

---

## 2. 功能需求（按 Phase 分级）

### Phase 1 — MVP（核心聊天）

#### P0: 连接管理

| ID | 功能 | 协议依赖 | 说明 |
|----|------|----------|------|
| C-01 | Setup Code 连接 | `connect` | 解析 Base64 → url + bootstrapToken |
| C-02 | 手动 host:port 连接 | `connect` | 输入 Gateway 地址 + 可选 token |
| C-03 | 挑战-响应认证 | `connect.challenge` | 等待 nonce → ECDSA 签名 → connect |
| C-04 | 设备密钥管理 | Android Keystore | ECDSA secp256r1，不可导出 |
| C-05 | Token 持久化 | EncryptedSharedPreferences | 保存 deviceToken 用于重连 |
| C-06 | 自动重连 | `connect` | 断线后指数退避重连 |
| C-07 | 连接状态显示 | `hello-ok` | 已连接/连接中/已断开/错误 |

#### P0: 设备配对

| ID | 功能 | 协议依赖 | 说明 |
|----|------|----------|------|
| P-01 | Setup Code 配对 | bootstrapToken | 从 Setup Code 解析 url + token |
| P-02 | 等待审批 | `node.pair.requested` 事件 | 显示"等待管理员审批"状态 |
| P-03 | 配对成功处理 | `hello-ok.auth.deviceToken` | 保存 deviceToken |
| P-04 | 配对超时处理 | 5 分钟 | 提示重试 |
| P-05 | 配对错误处理 | 错误码 | 显示具体错误和恢复建议 |

#### P0: 会话管理

| ID | 功能 | 协议依赖 | 说明 |
|----|------|----------|------|
| S-01 | 会话列表 | `sessions.list` | 显示所有会话，按最后活动排序 |
| S-02 | 会话详情 | `sessions.list` 元数据 | 显示模型、token 计数、来源 |
| S-03 | 新建会话 | `chat.send` + `/new` | 发送 `/new` 或 `/new <model>` |
| S-04 | 重置会话 | `chat.send` + `/reset` | 发送 `/reset` 重置当前会话 |

#### P0: 消息收发

| ID | 功能 | 协议依赖 | 说明 |
|----|------|----------|------|
| M-01 | 消息历史加载 | `chat.history` | 打开会话时加载历史 |
| M-02 | 发送文本消息 | `chat.send` | 纯文本消息发送 |
| M-03 | 实时消息接收 | `chat.subscribe` → `chat` 事件 | 实时收到助手回复 |
| M-04 | 消息时间戳 | 事件 payload | 显示消息时间 |
| M-05 | 消息角色区分 | role 字段 | user / assistant / system 视觉区分 |
| M-06 | 截断内容处理 | Gateway 截断保护 | 处理 `[chat.history omitted]` 占位符 |

### Phase 2 — 增强体验

#### P1: 高级连接

| ID | 功能 | 协议依赖 | 说明 |
|----|------|----------|------|
| C-10 | NSD/Bonjour 发现 | `_openclaw-gw._tcp` | Android NSD API 自动发现 |
| C-11 | 多 Gateway 配置 | 本地存储 | 保存/切换多个 Gateway |
| C-12 | Tailscale 检测 | VPN 接口检查 | 检测 tun0/tailscale0 |
| C-13 | 延迟监测 | ping/pong 或自定义 | 显示连接延迟 |
| C-14 | Foreground Service | Android API | 保持 WebSocket 连接 |

#### P1: 增强消息

| ID | 功能 | 协议依赖 | 说明 |
|----|------|----------|------|
| M-10 | Markdown 渲染 | 客户端 | 代码块、链接、列表等 |
| M-11 | 图片附件发送 | `chat.send` + attachments | 选择/拍照上传 |
| M-12 | 图片附件显示 | chat 事件 payload | 显示助手发送的图片 |
| M-13 | 停止生成 | `chat.send` + `/stop` | 中止当前 agent run |
| M-14 | Typing 指示器 | typing 事件 | 显示"正在输入" |
| M-15 | 本地消息缓存 | Room DB | 离线查看最近消息 |

#### P1: 增强会话

| ID | 功能 | 协议依赖 | 说明 |
|----|------|----------|------|
| S-10 | 会话搜索 | 本地 | 按标签/内容搜索 |
| S-11 | 模型切换 | `/model <name>` | 在会话中切换 AI 模型 |
| S-12 | Token 使用量 | session 元数据 | 显示 input/output/context tokens |
| S-13 | 会话来源显示 | origin 元数据 | 显示会话来自哪个渠道 |

#### P1: 通知

| ID | 功能 | 协议依赖 | 说明 |
|----|------|----------|------|
| N-01 | 本地通知 | `chat` 事件 | 新消息系统通知 |
| N-02 | 通知配置 | 本地 | 按会话配置通知 |
| N-03 | 勿扰模式 | 本地 | 定时静音 |

#### P1: 安全增强

| ID | 功能 | 协议依赖 | 说明 |
|----|------|----------|------|
| X-01 | Token 查看/撤销 | `device.token.revoke` | 管理设备令牌 |
| X-02 | 生物认证 | Android BiometricPrompt | 可选指纹/面部解锁 |
| X-03 | TLS 证书固定 | OkHttp CertificatePinner | 生产环境证书固定 |

### Phase 3 — Node 能力

#### P2: 设备能力

| ID | 功能 | 协议依赖 | 说明 |
|----|------|----------|------|
| D-01 | Node 角色注册 | `connect` (role: node) | 声明设备能力 |
| D-02 | 摄像头 | `camera.snap`, `camera.clip` | 拍照/录像命令 |
| D-03 | Canvas | `canvas.navigate`, `canvas.eval` | WebView 渲染 |
| D-04 | 位置 | `location.get` | 获取设备位置 |
| D-05 | 扩展命令 | `device.*`, `notifications.*` 等 | Android 独有能力 |

#### P2: 语音

| ID | 功能 | 协议依赖 | 说明 |
|----|------|----------|------|
| V-01 | 语音输入 | 麦克风 + 转写 | 语音转文字发送 |
| V-02 | TTS 播放 | ElevenLabs / 系统 TTS | 助手回复语音播放 |

#### P2: 推送

| ID | 功能 | 协议依赖 | 说明 |
|----|------|----------|------|
| K-01 | FCM 推送 | Firebase + Gateway 配置 | 后台消息推送 |
| K-02 | 后台唤醒 | FCM data message | 唤醒 WebSocket 连接 |

#### P2: Exec 审批

| ID | 功能 | 协议依赖 | 说明 |
|----|------|----------|------|
| A-01 | 审批请求通知 | `exec.approval.requested` | 显示审批请求 |
| A-02 | 审批/拒绝 | `exec.approval.resolve` | 处理审批 |
| A-03 | 审批历史 | 本地缓存 | 查看历史审批记录 |

---

## 3. 非功能需求

### 3.1 性能

| 指标 | 目标 |
|------|------|
| 冷启动 | < 2 秒 |
| 连接建立 | < 3 秒（LAN）/ < 5 秒（Tailscale）|
| 消息发送延迟 | < 500ms（到 Gateway）|
| 内存占用 | < 100MB |
| APK 大小 | < 15MB |
| 消息列表帧率 | 60fps |

### 3.2 兼容性

| 维度 | 要求 |
|------|------|
| Android 最低版本 | API 26 (Android 8.0) |
| 协议版本 | v3 |
| Gateway 版本 | 当前稳定版 |

### 3.3 安全

| 维度 | 要求 |
|------|------|
| 密钥存储 | Android Keystore (TEE/StrongBox) |
| Token 存储 | EncryptedSharedPreferences (AES256-GCM) |
| 网络安全 | TLS 1.3 + 可选证书固定 |
| 日志脱敏 | token/signature/key 自动脱敏 |
| 备份排除 | 禁止备份敏感数据 |

### 3.4 可用性

| 维度 | 要求 |
|------|------|
| 离线降级 | 显示缓存消息 + 连接状态 |
| 错误恢复 | 认证错误显示恢复建议 |
| 国际化 | 中文 + 英文 |
| 无障碍 | TalkBack 支持，WCAG 2.1 AA |

---

## 4. 技术约束（来自协议分析）

### 4.1 必须遵守

| 约束 | 来源 | 影响 |
|------|------|------|
| 第一帧必须是 connect | 协议规范 | 连接流程固定 |
| 必须等待 connect.challenge | 协议 v3 | 不能提前发送 connect |
| v3 签名载荷 | 协议 v3 | 必须包含 platform + deviceFamily |
| Gateway 是 session 的 source of truth | 架构规范 | 客户��不维护独立会话状态 |
| chat.history 可能截断 | WebChat 行为 | 处理占位符和截断内容 |
| 幂等键 | 副作用方法 | 发送消息需带 idempotency key |

### 4.2 架构约束

| 约束 | 说明 |
|------|------|
| 单 Gateway 连接 | 一次只连一个 Gateway |
| operator 优先 | Phase 1 只实现 operator 角色 |
| 无独立后端 | 直连 Gateway，不需要中间服务器 |
| 无 token 级流式 | 协议不支持真正的 token 流式到客户端 |

---

## 5. 与现有 clawchat-setup.md 的差异

| 维度 | 原规格 | 修订（基于协议分析） |
|------|--------|----------------------|
| 消息格式 | 自定义 GatewayMessage | 使用协议标准帧格式 (req/res/event) |
| 配对流程 | 简化描述 | 补充 connect.challenge + v3 签名 |
| 会话管理 | 本地 CRUD | Gateway 是 source of truth |
| 流式 | 未定义 | 无 token 级流式，仅 block streaming |
| 设备发现 | Tailscale 检测 | 补充 NSD/Bonjour 发现 |
| Node 能力 | 未提及 | 新增 Phase 3 规划 |
| Exec 审批 | 未提及 | 新增 Phase 3 规划 |
| 推送 | 未提及 | 新增 FCM 推送规划 |

### 5.1 Domain 层影响

现有 Domain 层需要调整：

| 现有模型 | 调整 |
|----------|------|
| `Session` | 移除本地 CRUD，改为 Gateway 查询结果的映射 |
| `Message` | 移除本地生成逻辑，改为协议事件的映射 |
| `GatewayConfig` | 增加 bootstrapToken 字段（Setup Code） |
| `ConnectionStatus` | 增加 `Pairing`、`WaitingApproval` 状态 |
| `SessionRepository` | 重构为 Gateway RPC 的代理，而非本地存储 |
| `ConnectionRepository` | 增加 challenge-response 流程 |

### 5.2 网络层影响

| 现有设计 | 调整 |
|----------|------|
| `GatewayMessage` | 替换为协议标准帧格式 |
| `WebSocketService` | 增加 connect.challenge 处理 |
| `SignatureInterceptor` | 改为 v3 签名载荷 |
| 消息解析 | 按 type (req/res/event) 分发 |

---

## 6. MVP 开发路线图

```
Week 1-2: 协议层
  ├── WebSocket 连接 + connect.challenge 处理
  ├── v3 签名实现（Android Keystore）
  ├── 帧解析器（req/res/event 分发）
  └── 自动重连 + 错误处理

Week 3-4: 配对 + 认证
  ├── Setup Code 解析
  ├── 配对状态机
  ├── deviceToken 持久化
  └── 配对 UI

Week 5-6: 会话 + 消息
  ├── sessions.list → 会话列表
  ├── chat.history → 消息历史
  ├── chat.send → 发送消息
  ├── chat.subscribe → 实时接收
  └── 消息 UI (Compose)

Week 7-8: 打磨
  ├── 连接状态 UI
  ├── 错误处理 + 恢复建议
  ├── 本地消息缓存
  ├── 基础 Markdown 渲染
  └── 测试 + Bug 修复
```

**预计 MVP 交付**: 8 周
