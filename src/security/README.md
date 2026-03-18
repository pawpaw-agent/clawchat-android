# ClawChat Security Module 🔒

Android 安全模块，为 ClawChat 提供硬件级安全保护。

## 📁 模块结构

```
security/
├── KeystoreManager.kt              # 客户端密钥生成/签名（Android Keystore）
├── EncryptedStorage.kt             # 加密 SharedPreferences 封装
├── DeviceFingerprint.kt            # 设备指纹生成（随机 UUID）
├── SecurityModule.kt               # 统一入口 + 安全日志
├── ServerPublicKeyManager.kt       # 服务端公钥管理（密钥轮换）
├── ServerSignatureVerifier.kt      # 服务端签名验证器
├── NonceCache.kt                   # Nonce 缓存（防重放攻击）
├── SecureWebSocketService.kt       # 带签名验证的 WebSocket 服务
├── ServerSignatureVerifierTest.kt  # 单元测试
├── build-security.gradle.kts       # Gradle 依赖配置
└── README.md                       # 本文档
```

## 🔧 依赖项

在 `app/build.gradle.kts` 中添加：

```kotlin
dependencies {
    // 安全加密
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    
    // 协程
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
```

## 🚀 快速开始

### 1. 初始化（Application onCreate）

```kotlin
@HiltAndroidApp
class ClawChatApplication : Application() {
    
    @Inject lateinit var securityModule: SecurityModule
    
    override fun onCreate() {
        super.onCreate()
        
        // 初始化安全模块
        lifecycleScope.launch {
            val status = securityModule.initialize()
            if (!status.isReady()) {
                // 需要配对或处理错误
            }
        }
    }
}
```

### 2. Hilt 模块配置

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object SecurityModule {
    
    @Provides
    @Singleton
    fun provideSecurityModule(@ApplicationContext context: Context): SecurityModule {
        return SecurityModule(context)
    }
}
```

### 3. 设备配对流程

```kotlin
@HiltViewModel
class PairingViewModel @Inject constructor(
    private val securityModule: SecurityModule
) : ViewModel() {
    
    // 准备配对请求
    suspend fun requestPairing(nodeId: String) {
        val pairingPayload = securityModule.preparePairingRequest(nodeId)
        
        // 发送到 Gateway
        val response = gatewayApi.pair(pairingPayload)
        
        // 等待管理员批准...
    }
    
    // 签名挑战
    fun signChallenge(nonce: String): String {
        return securityModule.signChallenge(nonce)
    }
    
    // 完成配对
    fun completePairing(deviceToken: String) {
        securityModule.completePairing(deviceToken)
    }
}
```

### 4. WebSocket 连接认证

```kotlin
class WebSocketService @Inject constructor(
    private val securityModule: SecurityModule
) {
    suspend fun connect() {
        val token = securityModule.getAuthToken()
            ?: throw NotPairedException("Device not paired")
        
        val gatewayUrl = securityModule.getGatewayUrl()
            ?: "ws://192.168.1.1:18789"
        
        // 建立带认证的 WebSocket 连接
        val request = Request.Builder()
            .url("$gatewayUrl/ws?token=$token")
            .build()
        
        webSocketClient.newWebSocket(request, listener)
    }
}
```

## 📋 API 参考

### SecurityModule

| 方法 | 描述 |
|------|------|
| `initialize()` | 初始化安全模块（检查/生成密钥） |
| `getSecurityStatus()` | 获取当前安全状态 |
| `preparePairingRequest()` | 准备配对请求（Protocol v3） |
| `signChallenge(nonce)` | 签名挑战（用于认证） |
| `completePairing(token)` | 完成配对，存储令牌 |
| `getAuthToken()` | 获取认证令牌 |
| `needsPairing()` | 检查是否需要配对 |
| `resetPairing()` | 重置配对（保留密钥） |
| `factoryReset()` | 完全重置（清除所有） |

### EncryptedStorage

| 方法 | 描述 |
|------|------|
| `saveDeviceToken(token)` | 存储设备令牌 |
| `getDeviceToken()` | 获取设备令牌 |
| `saveDeviceId(id)` | 存储设备指纹 |
| `getDeviceId()` | 获取设备指纹 |
| `saveGatewayUrl(url)` | 存储 Gateway URL |
| `getGatewayUrl()` | 获取 Gateway URL |
| `isPaired()` | 检查是否已配对 |
| `clearAll()` | 清除所有数据 |

### KeystoreManager

| 方法 | 描述 |
|------|------|
| `hasKeyPair()` | 检查密钥对是否存在 |
| `generateKeyPair()` | 生成 ECDSA 密钥对 |
| `getPublicKeyPem()` | 获取公钥（PEM 格式） |
| `signChallenge(challenge)` | 签名挑战 |
| `deleteKey()` | 删除密钥对 |
| `getKeyInfo()` | 获取密钥信息（诊断） |

### DeviceFingerprint

| 方法 | 描述 |
|------|------|
| `generateDeviceId()` | 生成/获取设备指纹（随机 UUID） |
| `getDeviceDescription()` | 获取设备描述（人类可读） |
| `getPlatformInfo()` | 获取平台信息 |
| `resetDeviceId()` | 重置设备指纹（用户主动重置） |
| `hasDeviceId()` | 检查是否已有设备指纹 |

### ServerPublicKeyManager

| 方法 | 描述 |
|------|------|
| `savePrimaryPublicKey(pem)` | 保存主公钥（PEM 格式） |
| `saveBackupPublicKey(pem)` | 保存备份公钥（密钥轮换） |
| `getPrimaryPublicKey()` | 获取主公钥 |
| `getPrimaryFingerprint()` | 获取公钥指纹（SHA256） |
| `verifyFingerprint(fp)` | 验证公钥指纹 |
| `hasValidPublicKey()` | 检查是否有有效公钥 |
| `clearAllKeys()` | 清除所有公钥 |

### ServerSignatureVerifier

| 方法 | 描述 |
|------|------|
| `verify(method, path, body, ts, nonce, sig)` | 验证 HTTP 请求签名 |
| `verifyWebSocketMessage(type, session, content, ts, nonce, sig)` | 验证 WebSocket 消息签名 |
| `toException()` | 转换为 SecurityException |

## 🔐 安全特性

### 1. 硬件级密钥存储

- 私钥存储在 Android Keystore（TEE/StrongBox）
- 私钥不可导出，即使 Root 也无法提取
- 使用 ECDSA secp256r1（P-256）算法

### 2. 加密存储

- EncryptedSharedPreferences 存储敏感数据
- MasterKey 存储在 Android Keystore
- AES256-GCM 加密（值），AES256-SIV（密钥）

### 3. 设备指纹（隐私合规）

- 基于随机 UUID（首次安装时生成）
- 不使用任何硬件标识符（IMEI、Android ID、序列号等）
- 不请求敏感权限
- 应用卸载后自动重置（用户可控制）
- 符合 Google Play 开发者政策

### 4. 服务端签名验证

- ECDSA secp256r1 签名验证（与客户端一致）
- 时间戳验证（±5 分钟窗口，防止重放攻击）
- Nonce 缓存（5 分钟内不重复）
- 支持主公钥和备份公钥（密钥轮换）
- WebSocket 消息签名验证
- 验证失败自动丢弃消息并记录安全日志

### 5. 日志安全

- SecureLogger 自动脱敏敏感信息
- 生产环境不记录敏感数据
- token/key/signature 自动替换为 `***`

## 🧪 测试

### 单元测试示例

```kotlin
@Test
fun `sign challenge should produce valid signature`() = runTest {
    // Given
    val securityModule = SecurityModule(testContext)
    awaitInitialization(securityModule)
    
    // When
    val signature = securityModule.signChallenge("test_nonce")
    
    // Then
    assertNotNull(signature)
    assertTrue(signature.isNotEmpty())
}

@Test
fun `device fingerprint should be consistent`() {
    // Given
    val fingerprint = DeviceFingerprint(testContext)
    
    // When
    val id1 = fingerprint.generateDeviceId()
    val id2 = fingerprint.generateDeviceId()
    
    // Then
    assertEquals(id1, id2)
}

@Test
fun `encrypted storage should persist data`() {
    // Given
    val storage = EncryptedStorage(testContext)
    
    // When
    storage.saveDeviceToken("test_token_123")
    
    // Then
    assertEquals("test_token_123", storage.getDeviceToken())
}
```

### 测试环境使用

```kotlin
// 单元测试中使用简化版指纹生成器
val testId = SimpleDeviceFingerprint.generateTestId()
val deterministicId = SimpleDeviceFingerprint.generateFromSeed("fixed_seed")
```

## 📊 安全状态

```kotlin
val status = securityModule.getSecurityStatus()

when {
    !status.isInitialized -> "初始化中..."
    !status.hasKeyPair -> "生成密钥中..."
    !status.isPaired -> "未配对"
    !status.hasDeviceToken -> "配对完成，等待连接"
    else -> "已就绪"
}
```

## 🔄 配对流程

```
┌─────────────┐              ┌─────────────┐
│  Android    │              │   Gateway   │
│    App      │              │   Server    │
└──────┬──────┘              └──────┬──────┘
       │                            │
       │  1. preparePairingRequest  │
       │  (deviceId, publicKey)     │
       │ ──────────────────────────>│
       │                            │
       │  2. 返回配对请求            │
       │ <──────────────────────────│
       │                            │
       │  3. 提交配对请求            │
       │ ──────────────────────────>│
       │                            │
       │  4. 返回挑战 nonce          │
       │ <──────────────────────────│
       │                            │
       │  5. signChallenge(nonce)   │
       │ ──────────────────────────>│
       │                            │
       │  6. 验证签名，颁发令牌      │
       │ <──────────────────────────│
       │  (deviceToken)             │
       │                            │
       │  7. completePairing(token) │
       │  (存储到 EncryptedStorage) │
       │                            │
       │  8. 使用令牌建立 WS 连接     │
       │ ──────────────────────────>│
       │                            │
```

## ⚠️ 注意事项

### 1. 备份安全

```xml
<!-- AndroidManifest.xml -->
<application
    android:allowBackup="false"
    android:fullBackupContent="false">
```

### 2. 网络安全性

```xml
<!-- res/xml/network_security_config.xml -->
<network-security-config>
    <domain-config cleartextTrafficPermitted="false">
        <domain includeSubdomains="true">*.ts.net</domain>
        <pin-set>
            <pin digest="SHA-256">production_cert_fingerprint</pin>
        </pin-set>
    </domain-config>
</network-security-config>
```

### 3. ProGuard 规则

确保添加 ProGuard 规则（见 `build-security.gradle.kts`）。

### 4. 密钥轮换

服务端公钥支持主/备双密钥轮换机制：
- 使用 `saveBackupPublicKey()` 保存新公钥
- 验证时会尝试主公钥，失败后尝试备份公钥
- 验证成功后可以调用 `savePrimaryPublicKey()` 提升新密钥为主密钥

## 📝 变更日志

- **v2.1.0** (2026-03-18) - 服务端签名验证 🟠
  - ServerPublicKeyManager: 服务端公钥管理（支持密钥轮换）
  - ServerSignatureVerifier: 服务端签名验证器
  - NonceCache: Nonce 缓存（防重放攻击）
  - SecureWebSocketService: 带签名验证的 WebSocket 服务
  - 时间戳验证（±5 分钟窗口）
  - 添加完整单元测试

- **v2.0.0** (2026-03-18) - 隐私合规修复 🔴
  - DeviceFingerprint: 改用随机 UUID（移除硬件标识符）
  - DeviceFingerprint: 添加 resetDeviceId() 方法
  - DeviceFingerprint: 添加 hasDeviceId() 方法
  - 符合 Google Play 开发者政策

- **v1.0.0** (2026-03-17) - 初始版本
  - KeystoreManager: ECDSA 密钥生成/签名
  - EncryptedStorage: AES256-GCM 加密存储
  - DeviceFingerprint: 设备指纹生成
  - SecurityModule: 统一入口

## 🔐 服务端签名验证

### 签名格式

服务端消息使用以下格式进行签名：

```
签名字符串 = messageType + \n + sessionId + \n + timestamp + \n + nonce + \n + contentHash
```

其中：
- `messageType`: 消息类型（如 `assistantMessage`, `systemEvent`）
- `sessionId`: 会话 ID
- `timestamp`: Unix 时间戳（毫秒）
- `nonce`: 随机数（UUID 格式）
- `contentHash`: 消息内容的 SHA256 哈希

### 验证流程

```kotlin
// 1. 保存服务端公钥（首次配对时）
val fingerprint = securityModule.saveServerPublicKey(serverPublicKeyPem)

// 2. 验证 WebSocket 消息
val result = securityModule.verifyWebSocketSignature(
    messageType = "assistantMessage",
    sessionId = sessionId,
    content = messageContent,
    timestamp = messageTimestamp,
    nonce = messageNonce,
    signature = messageSignature
)

if (!result.isSuccess()) {
    // 验证失败，丢弃消息
    throw result.toException()
}
```

### 消息格式（带签名）

```json
{
  "type": "assistantMessage",
  "sessionId": "session-123",
  "content": "Hello from assistant",
  "model": "gpt-4",
  "timestamp": 1710756000000,
  "metadata": {
    "nonce": "uuid-here",
    "signature": "base64-encoded-signature",
    "messageType": "assistantMessage"
  }
}
```

## 📚 参考资料

- [Android Keystore System](https://developer.android.com/training/articles/keystore)
- [EncryptedSharedPreferences](https://developer.android.com/reference/androidx/security/crypto/EncryptedSharedPreferences)
- [OpenClaw Pairing Protocol](https://docs.openclaw.ai/channels/pairing)
- [ClawChat Setup Spec](../../project-specs/clawchat-setup.md)
- [ECDSA Digital Signature](https://en.wikipedia.org/wiki/Elliptic_Curve_Digital_Signature_Algorithm)
