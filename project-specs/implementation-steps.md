# ClawChat 实现步骤

> 基于 refactor-plan.md 6 个 Step 的修订版  
> **日期**: 2026-03-19  
> **核心变更**: ECDSA → Ed25519, 对齐源码验证结果

---

## 依赖关系图

```
Step 0 (清理)
  │
  ▼
Step 1 (Ed25519 密钥)
  │
  ▼
Step 2 (协议层: 帧 + 传输 + 认证)
  │
  ▼
Step 3 (Gateway SDK: Client + Chat + Session)
  │
  ├──► Step 4a (ViewModel 接入)
  │
  └──► Step 4b (本地缓存同步)
         │
         ▼
       Step 5 (打磨 + 测试)
```

---

## Step 0: 清理（30 分钟）

### 输入
- 现有项目全部文件

### 操作

```bash
# 1. 删除旧原型目录
rm -rf src/

# 2. 删除不兼容的旧层文件
rm app/src/main/java/.../network/GatewayMessage.kt
rm app/src/main/java/.../network/SignatureInterceptor.kt
rm app/src/main/java/.../network/WebSocketService.kt  # 接口被 GatewayClient 替代
rm app/src/main/java/.../security/DeviceFingerprint.kt  # deviceId 改为公钥派生

# 3. 清理 WebSocketProtocol.kt 中的旧常量
# 删除: FrameHeader, WebSocketFrame, ProtocolVersion, HTTP 头常量
# 保留: PROTOCOL_VERSION = 3, WS_PATH
```

### 输出
- 编译通过（`./gradlew assembleDebug`）
- 删除 ~8000 行无效代码

### 验收标准
- [ ] `src/` 目录不存在
- [ ] 被删文件无编译引用（或已修复引用）
- [ ] CI 绿色

---

## Step 1: Ed25519 密钥管理（1 天）

### 输入
- `security/KeystoreManager.kt`（现有 ECDSA 实现）
- `security/EncryptedStorage.kt`（现有加密存储）

### 操作

#### 1.1 添加 BouncyCastle 依赖

```kotlin
// build.gradle.kts
implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")
implementation("org.bouncycastle:bcpkix-jdk18on:1.78.1")
```

#### 1.2 创建 Ed25519KeyManager 实现

```
新建: security/Ed25519KeyManager.kt
```

核心逻辑：
- 使用 `org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator` 生成密钥对
- 私钥以 PKCS8 PEM 格式加密存储在 `EncryptedSharedPreferences`
- 公钥以 SPKI PEM 格式存储
- 提取 32 字节原始公钥 → base64url 编码（无填充）
- deviceId = `SHA-256(原始公钥32字节).hex()`
- 签名: `Ed25519Signer.generateSignature(payload)` → base64url 编码

#### 1.3 创建 DeviceIdentityProvider 实现

```
新建: security/DeviceIdentityProviderImpl.kt
```

核心逻辑：
- 封装 Ed25519KeyManager + EncryptedStorage
- `buildSignedV3Payload()`: 构建 11 段竖线分隔载荷 → Ed25519 签名
- platform/deviceFamily 做 ASCII lowercase 规范化
- 管理 deviceToken 的保存/读取/清除

#### 1.4 保留旧 KeystoreManager

不删除，但不再在认证流程中使用。保留用于未来可能的 ECDSA 场景。

### 输出
- `Ed25519KeyManager.kt`
- `DeviceIdentityProviderImpl.kt`
- 单元测试

### 验收标准
- [ ] 生成的 Ed25519 密钥对可以签名并验证
- [ ] v3 载荷格式 = `v3|{deviceId}|openclaw-android|{mode}|{role}|{scopes}|{ms}|{token}|{nonce}|{platform}|{family}`
- [ ] deviceId = SHA-256(32 字节公钥).hex()
- [ ] 签名结果为 base64url 编码（无填充）
- [ ] 密钥持久化后重启 App 仍可读取
- [ ] 单元测试覆盖: 密钥生成、签名、载荷构建、deviceId 派生

---

## Step 2: 协议层（2 天）

### 输入
- 保留的 `RequestFrame.kt`, `ResponseFrame.kt`, `EventFrame.kt`
- 保留的 `RequestTracker.kt`, `SequenceManager.kt`
- `interface-definitions.md` 中的数据模型

### 操作

#### 2.1 创建帧编解码器

```
新建: network/protocol/FrameCodec.kt
```

```kotlin
object FrameCodec {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun decode(text: String): GatewayFrame? {
        val obj = json.parseToJsonElement(text).jsonObject
        return when (obj["type"]?.jsonPrimitive?.content) {
            "req" -> json.decodeFromString<RequestFrame>(text)
            "res" -> json.decodeFromString<ResponseFrame>(text)
            "event" -> json.decodeFromString<EventFrame>(text)
            else -> null
        }
    }

    fun encodeRequest(id: String, method: String, params: JsonElement?): String {
        return json.encodeToString(RequestFrame(id = id, method = method, params = params))
    }
}
```

#### 2.2 创建 WsTransport

```
新建: network/transport/WsTransport.kt
```

职责：
- OkHttp WebSocket 连接管理
- 帧发送/接收
- 自动重连（指数退避）
- 连接状态 StateFlow

#### 2.3 创建 RpcDispatcher

```
新建: network/protocol/RpcDispatcher.kt
```

职责：
- 封装 RequestTracker
- `call(method, params, timeout)` → 发送 req，等待 res
- 自动生成 request id
- 超时处理

#### 2.4 创建 AuthFlow

```
重写: network/protocol/ChallengeResponseAuth.kt → network/auth/AuthFlow.kt
```

职责：
- 监听 `connect.challenge` 事件
- 调用 DeviceIdentityProvider 构建 v3 签名
- 发送 connect 请求
- 处理 hello-ok 响应（提取 deviceToken、snapshot、features）
- 处理认证错误（错误码映射 + 恢复建议）
- 处理配对等待（`PAIRING_REQUIRED` → WaitingApproval 状态）

#### 2.5 补充协议模型

```
新建: network/protocol/models/ConnectParams.kt    (§2 interface-definitions)
新建: network/protocol/models/HelloOk.kt          (§2)
新建: network/protocol/models/ChatEvent.kt        (§2)
新建: network/protocol/models/ChatSendParams.kt   (§2)
新建: network/protocol/models/SessionsParams.kt   (§3)
新建: network/protocol/models/DeviceParams.kt     (§4)
新建: network/protocol/models/GatewayError.kt     (§5)
```

### 输出
- `FrameCodec.kt`
- `WsTransport.kt`
- `RpcDispatcher.kt`
- `AuthFlow.kt`
- 7 个数据模型文件
- 单元测试

### 验收标准
- [ ] FrameCodec 能正确解析 req/res/event 三种帧
- [ ] WsTransport 能建立 WebSocket 连接并收发帧
- [ ] RpcDispatcher.call() 能发送请求并匹配响应
- [ ] AuthFlow 完整处理 challenge → sign → connect → hello-ok
- [ ] 认证错误能正确映射为 GatewayConnectionState.Failed
- [ ] **集成测试**: 连接本地 Gateway → 收到 hello-ok → 状态变为 Connected

---

## Step 3: Gateway SDK（1.5 天）

### 输入
- Step 2 的协议层
- `GatewayClient` 接口定义

### 操作

#### 3.1 创建 GatewayClient 实现

```
重写: network/protocol/GatewayConnection.kt → network/gateway/GatewayClientImpl.kt
```

职责：
- 组合 WsTransport + AuthFlow + RpcDispatcher
- 实现 GatewayClient 接口的所有方法
- chat 事件分发（SharedFlow）
- tick 事件分发
- 连接状态聚合

#### 3.2 创建 ChatClient

```
新建: network/gateway/ChatClient.kt
```

职责：
- ChatEvent 状态机管理（按 runId 分组、按 seq 排序）
- delta → 累积内容 → final/aborted/error
- 消息完成后通知 UI

#### 3.3 创建 SessionClient

```
新建: network/gateway/SessionClient.kt
```

职责：
- sessions.list 调用 + 结果解析
- 会话列表 StateFlow
- 定期刷新（可配置间隔）
- 从 hello-ok.snapshot 初始化

#### 3.4 桥接旧 WebSocketService（可选）

如果 Step 4 能直接迁移 ViewModel，可以跳过桥接。否则：

```
修改: network/OkHttpWebSocketService.kt → 内部委托给 GatewayClient
```

#### 3.5 Hilt 注入

```
修改: network/NetworkModule.kt
新建: di/GatewayModule.kt
```

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object GatewayModule {
    @Provides @Singleton
    fun provideGatewayClient(
        okHttpClient: OkHttpClient,
        identityProvider: DeviceIdentityProvider,
        @ApplicationScope scope: CoroutineScope
    ): GatewayClient = GatewayClientImpl(okHttpClient, identityProvider, scope)
}
```

### 输出
- `GatewayClientImpl.kt`
- `ChatClient.kt`
- `SessionClient.kt`
- `GatewayModule.kt`

### 验收标准
- [ ] GatewayClient.connect() 完成完整握手
- [ ] GatewayClient.chatSend() 发送消息并收到 chat 事件
- [ ] GatewayClient.sessionsList() 返回会话列表
- [ ] ChatEvent 状态机正确处理 delta→final
- [ ] **集成测试**: 连接 → 发送消息 → 收到回复 → 显示在 chatEvents 流中

---

## Step 4a: ViewModel 接入（1 天）

### 输入
- GatewayClient
- 现有 ViewModel（含 mock 数据）

### 操作

#### 4a.1 PairingViewModel

```
修改: ui/state/PairingViewModel.kt
```

- 删除旧 WebSocketService 引用
- 注入 GatewayClient + DeviceIdentityProvider
- Setup Code 解析: Base64 decode → `{ url, bootstrapToken }`
- 手动连接: 用户输入 host:port + token
- 监听 connectionState → 更新 UI
- WaitingApproval 状态 → 显示"等待审批"

#### 4a.2 MainViewModel

```
修改: ui/state/MainViewModel.kt
```

- 删除所有 mock 数据
- 注入 GatewayClient
- 监听 connectionState → 更新连接状态 UI
- 监听 SessionClient.sessions → 更新会话列表
- 连接后从 hello-ok snapshot 初始化

#### 4a.3 SessionViewModel

```
修改: ui/state/SessionViewModel.kt
```

- 删除所有 mock 数据
- 注入 GatewayClient
- 进入会话时调用 chatHistory()
- 监听 chatEvents → 按 runId 聚合 → 更新消息列表
- 发送消息时调用 chatSend()（含 idempotencyKey）
- delta 状态 → 显示流式输入
- final 状态 → 完成消息
- error 状态 → 显示错误

#### 4a.4 UiState 更新

```
修改: ui/state/UiState.kt
```

- 替换 `ConnectionStatus` → `GatewayConnectionState`
- 添加 `SessionItem`, `ChatMessage`, `ChatUiState` 等新类型
- 删除旧的 `WebSocketConnectionState` 引用

### 输出
- 3 个 ViewModel 更新
- UiState 更新

### 验收标准
- [ ] 配对流程: Setup Code → 连接 → 等待审批 → 成功
- [ ] 主界面: 显示真实会话列表（来自 Gateway）
- [ ] 聊天界面: 发送消息 → 收到回复 → 正确显示
- [ ] 流式效果: delta 事件期间显示"正在回复"
- [ ] 连接断开时显示正确状态

---

## Step 4b: 本地缓存同步（0.5 天）

### 输入
- 现有 Room DB (MessageEntity, SessionEntity)
- GatewayClient 事件流

### 操作

#### 4b.1 消息缓存

- chatEvents state=final → 写入 MessageEntity
- chatHistory 结果 → 写入 MessageEntity
- 离线时从 Room 读取最近消息

#### 4b.2 会话缓存

- sessions.list 结果 → 写入 SessionEntity
- 离线时从 Room 读取会话列表

#### 4b.3 缓存策略

- 每会话最多缓存 200 条消息
- 会话列表全量替换
- 超过限制时删除最旧消息

### 输出
- Repository 更新（同步 Gateway → Room）

### 验收标准
- [ ] 在线: 数据来自 Gateway，同步到 Room
- [ ] 离线: 数据来自 Room，显示缓存标记
- [ ] 重新上线: 自动刷新

---

## Step 5: 打磨 + 测试（1 天）

### 操作

#### 5.1 小修复

| 文件 | 修复 |
|------|------|
| `NotificationManager.kt` | 添加 `@ApplicationContext` |
| `GatewayRepository.kt` | `clearGatewayConfig()` 改为 `remove()` |
| `NetworkModule.kt` | 证书固定 TODO |

#### 5.2 错误处理完善

- 认证错误: 显示 `recommendedNextStep` 对应的中文提示
- 网络错误: 区分超时/无网络/服务不可达
- 配对错误: 超时/拒绝/过期 的不同提示

#### 5.3 单元测试

| 测试 | 覆盖 |
|------|------|
| `Ed25519KeyManagerTest` | 密钥生成、签名、deviceId 派生 |
| `DeviceIdentityProviderTest` | v3 载荷构建、签名、token 管理 |
| `FrameCodecTest` | req/res/event 解析 |
| `AuthFlowTest` | challenge→sign→connect 流程 |
| `ChatClientTest` | ChatEvent 状态机 |
| `RpcDispatcherTest` | 请求-响应匹配、超时 |

#### 5.4 集成测试

- 连接本地 Gateway → 完成握手 → 发送消息 → 收到回复
- Setup Code 配对完整流程
- 断线重连

### 输出
- 修复合集
- 测试套件

### 验收标准
- [ ] CI 绿色
- [ ] 单元测试覆盖 ≥ 70%
- [ ] 可连接真实 Gateway 并完成对话
- [ ] 配对流程端到端通过

---

## 时间线

```
Step 0: 清理                    [0.5 天]
Step 1: Ed25519 密钥             [1 天]
Step 2: 协议层                   [2 天]
Step 3: Gateway SDK              [1.5 天]
Step 4a: ViewModel 接入          [1 天]
Step 4b: 本地缓存同步            [0.5 天]
Step 5: 打磨 + 测试              [1 天]
─────────────────────────────────────────
总计                              7.5 天
```

### 与原 refactor-plan 的差异

| 维度 | 原计划 (6天) | 修订 (7.5天) |
|------|-------------|-------------|
| 密钥算法 | ECDSA (Keystore) | **Ed25519 (BouncyCastle)** |
| Step 1 范围 | 补全 SecurityModule | **重写密钥管理** |
| Step 2 范围 | 修复 GatewayConnection | **重建协议层** (FrameCodec + WsTransport + RpcDispatcher + AuthFlow) |
| Step 3 | 桥接旧接口 | **创建 GatewayClient SDK** (替代桥接) |
| Step 4 | ViewModel 去 mock | **拆分为 4a(VM) + 4b(缓存)** |
| 新增 | - | 协议模型 (7 个 data class 文件) |
| 新增 | - | ChatEvent 状态机 |
| 工作量增加原因 | - | Ed25519 需要 BouncyCastle；ChatEvent 结构复杂度高于预期 |

---

## 风险项

| 风险 | 概率 | 影响 | 缓解 |
|------|------|------|------|
| BouncyCastle Ed25519 与 Gateway 验证不兼容 | 中 | 阻塞 | Step 1 写对照测试：用 Node.js crypto 生成参考签名 |
| chat event `message` 字段结构不明 | 中 | 延迟 | Step 2 先用 `JsonElement` 接收，后续按实际数据细化 |
| Gateway 版本差异导致 hello-ok 字段缺失 | 低 | 小 | 所有非必需字段标记为 `Optional` |
| APK 大小增加（BouncyCastle） | 低 | 小 | 使用 ProGuard 规则裁剪未用类（~2MB 增量） |
