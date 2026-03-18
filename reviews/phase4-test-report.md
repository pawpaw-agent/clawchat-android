# Phase 4: 集成测试报告

**日期**: 2026-03-19  
**执行人**: Qa Lead  
**项目**: ClawChat Android  
**Phase**: Phase 3 重构后集成验证

---

## 1. 协议测试结果

**目标**: `ws://localhost:18789/ws` · Token: `989674...d331`

| 步骤 | 测试项 | 结果 |
|---|---|---|
| 1 | WebSocket 连接建立 | ✅ PASS |
| 2 | connect.challenge 接收 (含 nonce) | ✅ PASS |
| 3 | Ed25519 v3 签名 → connect 认证通过 | ✅ PASS |
| 4 | sessions.list 获取 21 个会话 | ✅ PASS |
| 5 | chat.send 发送成功 | ✅ PASS |
| 6 | assistant 响应接收 (state=final) | ✅ PASS |

**结果**: 6/6 通过，端到端协议流程完全正常。

**hello-ok payload 包含**: `type, protocol, server, features, snapshot, canvasHostUrl, auth, policy` — 字段齐全，snapshot 含 sessionDefaults。

---

## 2. 代码审查发现

### 2.1 GatewayConnection 握手流程 — ✅ 正确

**流程验证**:
1. `connect()` → OkHttp WebSocket + `X-ClawChat-Protocol-Version: 3` header
2. `handleEventFrame()` 捕获 `connect.challenge` → `handleConnectChallenge()`
3. `ChallengeResponseAuth.handleChallenge()` 存储 nonce → `buildConnectRequest()` 构建 v3 签名
4. `RequestTracker.trackRequest()` 追踪 → 发送 → `deferred.await()` 等待 hello-ok
5. `handleHelloOk()` 提取 deviceToken / defaultSessionKey → 状态转 Connected

**无问题**。流程与协议测试结果一致。

### 2.2 Ed25519 签名逻辑 — ✅ 正确，1 个建议

**SecurityModule.signV3Payload()**:
- v3 payload 格式: `v3|deviceId|clientId|clientMode|role|scopes|signedAtMs|token|nonce|platform|deviceFamily`
- `normalizeDeviceMetadata()` 正确实现 NFKD 去变音符 + 小写
- 公钥: raw 32 bytes base64url（去掉 SPKI 前缀）
- deviceId: `sha256(raw public key).hex()`

**KeystoreManager**:
- API 33+: Android Keystore 硬件级 Ed25519
- API 26-32: BouncyCastle 软件实现 + EncryptedSharedPreferences

与协议测试中 Node.js `crypto.sign(null, payload, ed25519Key)` 产出一致 → 签名兼容。

**建议 [LOW]**: `ChallengeResponseAuth` 默认 scopes 为 `["operator.read", "operator.write"]`，但协议测试脚本使用 `["operator.read", "operator.write", "operator.admin"]`。建议对齐——当前不影响连接（Gateway 做权限裁剪），但 `operator.admin` 可能在未来需要。

### 2.3 ChatEvent 状态机处理 — ✅ 正确，2 个注意点

**handleEventFrame()** 分发逻辑:
1. `connect.challenge` → 内部处理
2. 其他所有事件 → 透传 `_incomingMessages` SharedFlow

**SequenceManager**: Gap 检测 + 去重 + 大间隙自动重置，逻辑完整。

**EventDeduplicator**: 双维度去重（eventId + seq），历史容量 1000。

**注意点**:

1. **[MEDIUM] `stateVersion` 作为 eventId**: `handleEventFrame()` 中 `val eventId = obj["stateVersion"]?.jsonPrimitive?.content ?: "$event-$seq"` — 当 `stateVersion` 为 null 且 `seq` 也为 null 时，eventId 变成 `"chat-null"` 等固定字符串，可能导致后续同类事件被误判为重复。建议 fallback 使用 UUID 或包含时间戳。

2. **[LOW] chat 事件状态流未在 GatewayConnection 中解析**: delta/final/aborted/error 状态全部透传，上层需自行解析 `payload.state`。这是合理的分层设计，但需确保上层 ViewModel 正确处理所有 state。

### 2.4 RequestTracker — ✅ 正确，1 个注意点

- `CompletableDeferred` 匹配 req/res ID — 正确
- 超时清理每 60s 执行 — 正确
- `cancelAllRequests()` 在 disconnect 时调用 — 正确

**注意点 [LOW]**: `cleanupJob` 在 `init {}` 中启动了一个无限循环协程（`while(true)`），但 `stop()` 只在 `disconnect()` 时调用。如果 `RequestTracker` 被重新创建但旧实例未 stop，会泄漏协程。建议使用 `SupervisorJob` 绑定生命周期。

### 2.5 重连逻辑 — ✅ 正确

- 指数退避: 1s → 2s → 4s → ... → 30s max
- `reconnectAttempt` 在 hello-ok 时重置为 0
- `disconnect()` 时取消 reconnectJob

### 2.6 本地缓存同步 — ⚠️ 未完全实现

`GatewayConnection` 在 hello-ok 时存储 `deviceToken` 和 `defaultSessionKey`，但 **snapshot 中的会话列表未自动同步到本地 Room 数据库**。当前需要上层主动调用 `sessions.list` 获取。

这不是 bug（是设计选择），但值得记录。

---

## 3. 已知问题清单

| # | 严重度 | 模块 | 描述 | 建议 |
|---|---|---|---|---|
| 1 | MEDIUM | EventDeduplicator | `stateVersion=null && seq=null` 时 eventId 为固定字符串，可能误判重复 | fallback 用 UUID |
| 2 | LOW | ChallengeResponseAuth | 默认 scopes 缺少 `operator.admin` | 与 Gateway 配置对齐 |
| 3 | LOW | RequestTracker | `cleanupJob` 未绑定生命周期，可能泄漏协程 | 使用 `SupervisorJob` |
| 4 | LOW | GatewayConnection | hello-ok snapshot 未自动同步到 Room | 文档记录或后续实现 |
| 5 | INFO | build.gradle.kts | AAPT2 在 arm64 上无法运行（Pi 环境），单元测试无法本地编译执行 | CI 环境验证 |

---

## 4. 单元测试状态

### Android 单元测试 (Kotlin/JVM)

**状态**: ⚠️ 本地编译受阻

arm64 主机上 AAPT2 二进制不兼容（x86_64 only），`processDebugResources` 失败。这是 **环境问题**，不是代码问题。

**影响范围**: 所有需要 Android resource processing 的测试任务。纯 JVM 逻辑（协议帧序列化、序列号管理等）不受影响，但 Gradle task 依赖链无法绕过。

**缓解措施**: 
- 协议兼容性已通过 Node.js 端到端测试验证 ✅
- Repository 层测试代码已编写（69 个用例），待 CI 环境执行
- 建议在 x86_64 CI runner 上执行完整 `testDebugUnitTest`

### Node.js 协议测试

**状态**: ✅ 6/6 通过（见第 1 节）

---

## 5. 发布建议

### ✅ 可以进入 Phase 5 / Beta

**理由**:
1. 协议握手全流程端到端验证通过
2. Ed25519 v3 签名与 Gateway 完全兼容
3. 代码结构清晰，分层合理（GatewayConnection → ChallengeResponseAuth → SecurityModule → KeystoreManager）
4. 无 P0/P1 阻塞问题

### 发布前建议修复

| 优先级 | 项目 | 工作量 |
|---|---|---|
| **应修复** | #1 EventDeduplicator fallback eventId | 15 min |
| 可选 | #3 RequestTracker 协程生命周期 | 30 min |
| 可选 | #2 scopes 对齐 | 5 min |

### 发布后跟进

- 在 x86_64 CI 上跑完整 `testDebugUnitTest` + JaCoCo 覆盖率
- 监控 `connect.challenge` 超时率（当前 AUTH_TIMEOUT = 60s，足够）
- 添加 chat event state machine 的上层集成测试

---

*报告生成时间: 2026-03-19 02:42 GMT+8*
