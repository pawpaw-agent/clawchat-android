# ClawChat 接口定义

> Protocol v3 驱动的 Kotlin 接口  
> **日期**: 2026-03-19  
> **约束**: Ed25519 · client.id=openclaw-android · idempotencyKey 必需

---

## 1. 协议帧模型

保留现有 `RequestFrame` / `ResponseFrame` / `EventFrame`（审计评估��"良好"），仅补充缺失的类型。

### 1.1 Gateway 帧（顶层）

```kotlin
/** 帧类型鉴别器 — 对应源码 GatewayFrameSchema 的 discriminator="type" */
sealed interface GatewayFrame {
    val type: String
}

// RequestFrame, ResponseFrame, EventFrame 已存在且正确，保留原样
```

### 1.2 connect 参数

```kotlin
/** connect 请求参数 — 严格对齐源码 ConnectParamsSchema */
@Serializable
data class ConnectParams(
    val minProtocol: Int = 3,
    val maxProtocol: Int = 3,
    val client: ClientInfo,
    val role: String = "operator",
    val scopes: List<String> = listOf("operator.read", "operator.write"),
    val caps: List<String> = emptyList(),
    val commands: List<String> = emptyList(),
    val permissions: Map<String, Boolean> = emptyMap(),
    val auth: ConnectAuth? = null,
    val device: DeviceAuth? = null,
    val locale: String = "zh-CN",
    val userAgent: String = "openclaw-android/1.0.0",
)

@Serializable
data class ClientInfo(
    val id: String = "openclaw-android",     // 枚举值，不可自定义
    val version: String,
    val platform: String = "android",
    val mode: String = "ui",                  // operator→"ui", node→"node"
    val displayName: String? = null,
    val deviceFamily: String? = null,         // "phone", "tablet"
    val modelIdentifier: String? = null,      // 设备型号
    val instanceId: String? = null,           // 稳定实例 ID（避免 presence 重复）
)

@Serializable
data class ConnectAuth(
    val token: String? = null,
    val bootstrapToken: String? = null,
    val deviceToken: String? = null,
    val password: String? = null,
)

@Serializable
data class DeviceAuth(
    val id: String,                           // sha256(raw pubkey).hex
    val publicKey: String,                    // base64url 编码的 32 字节原始公钥
    val signature: String,                    // Ed25519 签名，base64url 编码
    val signedAt: Long,                       // 签名时间戳 ms
    val nonce: String,                        // 来自 connect.challenge 的 nonce
)
```

### 1.3 hello-ok 响应

```kotlin
/** hello-ok 响应 — 严格对齐源码 HelloOkSchema */
@Serializable
data class HelloOk(
    val type: String = "hello-ok",
    val protocol: Int,
    val server: ServerInfo,
    val features: Features,
    val snapshot: Snapshot,
    val canvasHostUrl: String? = null,
    val auth: HelloAuth? = null,
    val policy: Policy,
)

@Serializable
data class ServerInfo(
    val version: String,
    val connId: String,
)

@Serializable
data class Features(
    val methods: List<String>,
    val events: List<String>,
)

@Serializable
data class HelloAuth(
    val deviceToken: String,
    val role: String,
    val scopes: List<String>,
    val issuedAtMs: Long? = null,
)

@Serializable
data class Policy(
    val maxPayload: Int,
    val maxBufferedBytes: Int,
    val tickIntervalMs: Int,
)

@Serializable
data class Snapshot(
    val presence: List<PresenceEntry> = emptyList(),
    val stateVersion: StateVersion,
    val uptimeMs: Long,
    val sessionDefaults: SessionDefaults? = null,
    val authMode: String? = null,
)

@Serializable
data class SessionDefaults(
    val defaultAgentId: String,
    val mainKey: String,
    val mainSessionKey: String,
)

@Serializable
data class StateVersion(
    val presence: Int,
    val health: Int,
)

@Serializable
data class PresenceEntry(
    val host: String? = null,
    val ip: String? = null,
    val version: String? = null,
    val platform: String? = null,
    val deviceFamily: String? = null,
    val mode: String? = null,
    val lastInputSeconds: Int? = null,
    val ts: Long,
    val deviceId: String? = null,
    val roles: List<String>? = null,
    val instanceId: String? = null,
)
```

---

## 2. 聊天模型

### 2.1 ChatEvent（对齐源码 ChatEventSchema）

```kotlin
/** 来自 Gateway 的 chat 事件 — 核心消息推送模型 */
@Serializable
data class ChatEvent(
    val runId: String,
    val sessionKey: String,
    val seq: Int,
    val state: ChatEventState,
    val message: JsonElement? = null,     // 结构化消息对象
    val errorMessage: String? = null,
    val usage: JsonElement? = null,       // token 使用量
    val stopReason: String? = null,
)

@Serializable
enum class ChatEventState {
    @SerialName("delta") DELTA,
    @SerialName("final") FINAL,
    @SerialName("aborted") ABORTED,
    @SerialName("error") ERROR,
}
```

### 2.2 chat.send 参数（对齐源码 ChatSendParamsSchema）

```kotlin
/** chat.send 请求参数 */
@Serializable
data class ChatSendParams(
    val sessionKey: String,
    val message: String,
    val idempotencyKey: String,           // 必需！每次发送生成 UUID
    val thinking: String? = null,
    val deliver: Boolean? = null,
    val attachments: List<JsonElement>? = null,
    val timeoutMs: Int? = null,
)
```

### 2.3 chat.history / chat.inject / chat.abort

```kotlin
@Serializable
data class ChatHistoryParams(
    val sessionKey: String,
    val limit: Int? = null,               // 1-1000
)

@Serializable
data class ChatInjectParams(
    val sessionKey: String,
    val message: String,
    val label: String? = null,            // ≤100 字符
)

@Serializable
data class ChatAbortParams(
    val sessionKey: String,
    val runId: String? = null,
)
```

---

## 3. 会话模型

### 3.1 sessions.list（对齐源码 SessionsListParamsSchema）

```kotlin
@Serializable
data class SessionsListParams(
    val limit: Int? = null,
    val activeMinutes: Int? = null,
    val includeGlobal: Boolean? = null,
    val includeDerivedTitles: Boolean? = null,
    val includeLastMessage: Boolean? = null,
    val agentId: String? = null,
    val search: String? = null,
)
```

### 3.2 sessions.reset / sessions.delete

```kotlin
@Serializable
data class SessionsResetParams(
    val key: String,
    val reason: String? = null,           // "new" | "reset"
)

@Serializable
data class SessionsDeleteParams(
    val key: String,
    val deleteTranscript: Boolean? = null,
)
```

---

## 4. 设备管理模型

### 4.1 配对（对齐源码 devices schema）

```kotlin
@Serializable
data class DevicePairApproveParams(val requestId: String)

@Serializable
data class DevicePairRejectParams(val requestId: String)

@Serializable
data class DeviceTokenRotateParams(
    val deviceId: String,
    val role: String,
    val scopes: List<String>? = null,
)

@Serializable
data class DeviceTokenRevokeParams(
    val deviceId: String,
    val role: String,
)

/** 配对请求事件 */
@Serializable
data class DevicePairRequestedEvent(
    val requestId: String,
    val deviceId: String,
    val publicKey: String,
    val displayName: String? = null,
    val platform: String? = null,
    val role: String? = null,
    val silent: Boolean? = null,
    val ts: Long,
)

/** 配对结果事件 */
@Serializable
data class DevicePairResolvedEvent(
    val requestId: String,
    val deviceId: String,
    val decision: String,                 // "approved" | "rejected" | "expired"
    val ts: Long,
)
```

---

## 5. 错误模型

```kotlin
/** 对齐源码 ErrorShapeSchema */
@Serializable
data class GatewayError(
    val code: String,
    val message: String,
    val details: JsonElement? = null,
    val retryable: Boolean? = null,
    val retryAfterMs: Int? = null,
)

/** 连接错误恢复建议 — 从 error.details 中提取 */
data class ConnectErrorRecovery(
    val canRetryWithDeviceToken: Boolean = false,
    val recommendedNextStep: String? = null,
    // "retry_with_device_token" | "update_auth_configuration" |
    // "update_auth_credentials" | "wait_then_retry" | "review_auth_configuration"
)
```

---

## 6. Gateway SDK 接口

### 6.1 GatewayClient（核心入口）

```kotlin
/**
 * Gateway 客户端 — ClawChat 与 Gateway 的唯一交互入口
 *
 * 职责: 连接管理、认证、RPC 调度、事件分发
 */
interface GatewayClient {

    /** 连接状态（全局） */
    val connectionState: StateFlow<GatewayConnectionState>

    /** 所有 chat 事件流 */
    val chatEvents: SharedFlow<ChatEvent>

    /** tick 事件流（心跳） */
    val tickEvents: SharedFlow<Long>

    // ── 连接 ──

    /** 连接到 Gateway（自动处理 challenge→签名→connect→hello-ok） */
    suspend fun connect(url: String, auth: ConnectAuth): Result<HelloOk>

    /** 断开连接 */
    suspend fun disconnect()

    /** 重连（使用保存的配置） */
    suspend fun reconnect(): Result<HelloOk>

    // ── RPC ──

    /** 通用 RPC 调用 */
    suspend fun call(method: String, params: JsonElement? = null, timeoutMs: Long = 30_000): Result<JsonElement?>

    // ── Chat ──

    /** 发送消息 */
    suspend fun chatSend(params: ChatSendParams): Result<JsonElement?>

    /** 获取历史 */
    suspend fun chatHistory(params: ChatHistoryParams): Result<JsonElement?>

    /** 注入消息 */
    suspend fun chatInject(params: ChatInjectParams): Result<JsonElement?>

    /** 中止运行 */
    suspend fun chatAbort(params: ChatAbortParams): Result<JsonElement?>

    // ── Sessions ──

    /** 列出会�� */
    suspend fun sessionsList(params: SessionsListParams = SessionsListParams()): Result<JsonElement?>

    /** 重置会话 */
    suspend fun sessionsReset(params: SessionsResetParams): Result<JsonElement?>

    /** 删除会话 */
    suspend fun sessionsDelete(params: SessionsDeleteParams): Result<JsonElement?>

    // ── Device ──

    /** 旋转令牌 */
    suspend fun deviceTokenRotate(params: DeviceTokenRotateParams): Result<JsonElement?>

    /** 撤销令牌 */
    suspend fun deviceTokenRevoke(params: DeviceTokenRevokeParams): Result<JsonElement?>
}
```

### 6.2 Ed25519KeyManager

```kotlin
/**
 * Ed25519 密钥管理器
 *
 * 使用 BouncyCastle 生成和管理 Ed25519 密钥对。
 * 私钥加密存储在 EncryptedSharedPreferences 中。
 */
interface Ed25519KeyManager {

    /** 获取或生成密钥对（首次调用时自动生成） */
    fun ensureKeyPair()

    /** 获取公钥原始字节（32 bytes） */
    fun getPublicKeyRawBytes(): ByteArray

    /** 获取公钥 base64url 编码（无填充） */
    fun getPublicKeyBase64Url(): String

    /** 获取 deviceId = SHA-256(公钥原始字节).hex() */
    fun getDeviceId(): String

    /** 使用 Ed25519 私钥签名 payload */
    fun sign(payload: ByteArray): ByteArray

    /** 签名并返回 base64url 编码 */
    fun signBase64Url(payload: String): String

    /** 密钥是否存在 */
    fun hasKeyPair(): Boolean

    /** 删除密钥对（用于重新配对） */
    fun deleteKeyPair()
}
```

### 6.3 DeviceIdentity

```kotlin
/**
 * 设备身份 — 封装 Ed25519 密钥派生的设备标识
 */
data class DeviceIdentity(
    val deviceId: String,              // sha256(raw pubkey).hex
    val publicKeyBase64Url: String,    // raw 32 bytes, base64url
)

/**
 * 设备身份提供者
 */
interface DeviceIdentityProvider {
    /** 获取设备身份 */
    fun getIdentity(): DeviceIdentity

    /** 构建并签名 v3 载荷 */
    fun buildSignedV3Payload(
        nonce: String,
        signedAtMs: Long = System.currentTimeMillis(),
        clientMode: String = "ui",
        role: String = "operator",
        scopes: List<String> = listOf("operator.read", "operator.write"),
        token: String = "",
        platform: String = "android",
        deviceFamily: String = "phone",
    ): DeviceAuth

    /** 保存 / 读取 / 清除设备令牌 */
    fun saveDeviceToken(token: String)
    fun getDeviceToken(): String?
    fun clearDeviceToken()
}
```

---

## 7. 连接状态

```kotlin
/** 连接状态密封类 — 覆盖完整生命周期 */
sealed class GatewayConnectionState {

    /** 未连接 */
    data object Disconnected : GatewayConnectionState()

    /** WebSocket 连接中 */
    data object Connecting : GatewayConnectionState()

    /** 等待 challenge / 签名 / 发送 connect */
    data object Authenticating : GatewayConnectionState()

    /** 已发送 connect，等待管理员审批配对 */
    data class WaitingApproval(
        val requestId: String,
    ) : GatewayConnectionState()

    /** 已连接 */
    data class Connected(
        val serverVersion: String,
        val connId: String,
        val defaultSessionKey: String,
        val availableMethods: Set<String>,
        val latencyMs: Long? = null,
    ) : GatewayConnectionState()

    /** 自动重连中 */
    data class Reconnecting(
        val attempt: Int,
        val nextRetryMs: Long,
        val lastError: String? = null,
    ) : GatewayConnectionState()

    /** 错误（不可自动恢复） */
    data class Failed(
        val code: String,
        val message: String,
        val recovery: ConnectErrorRecovery? = null,
    ) : GatewayConnectionState()

    fun isConnected(): Boolean = this is Connected
}
```

---

## 8. UI 状态（更新现有 UiState.kt）

```kotlin
/** 主界面状态 */
data class MainUiState(
    val connectionState: GatewayConnectionState = GatewayConnectionState.Disconnected,
    val sessions: List<SessionItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

/** 会话列表项 — 来自 sessions.list */
data class SessionItem(
    val key: String,
    val displayName: String?,
    val model: String?,
    val lastActivityAt: Long,
    val messagePreview: String?,
    val channel: String?,
    val totalTokens: Long?,
)

/** 聊天界面状态 */
data class ChatUiState(
    val sessionKey: String,
    val messages: List<ChatMessage> = emptyList(),
    val isLoading: Boolean = false,
    val isAgentRunning: Boolean = false,     // 有活跃的 runId
    val runningContent: String = "",          // delta 累积的部分内容
    val error: String? = null,
)

/** UI 层消息 — 从 ChatEvent + Room 缓存聚合 */
data class ChatMessage(
    val id: String,
    val role: String,              // "user" | "assistant" | "system"
    val content: String,
    val timestamp: Long,
    val isStreaming: Boolean = false,
    val isAborted: Boolean = false,
    val usage: TokenUsage? = null,
)

data class TokenUsage(
    val inputTokens: Long,
    val outputTokens: Long,
    val totalTokens: Long,
)
```
