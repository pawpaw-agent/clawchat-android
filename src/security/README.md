# ClawChat Security Module 🔒

Android 安全模块，为 ClawChat 提供硬件级安全保护。

## 📁 模块结构

```
security/
├── KeystoreManager.kt        # 密钥生成/签名（Android Keystore）
├── EncryptedStorage.kt       # 加密 SharedPreferences 封装
├── DeviceFingerprint.kt      # 设备指纹生成
├── SecurityModule.kt         # 统一入口 + 安全日志
├── build-security.gradle.kts # Gradle 依赖配置
└── README.md                 # 本文档
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
| `generateDeviceId()` | 生成设备指纹 |
| `getDeviceDescription()` | 获取设备描述（人类可读） |
| `getPlatformInfo()` | 获取平台信息 |

## 🔐 安全特性

### 1. 硬件级密钥存储

- 私钥存储在 Android Keystore（TEE/StrongBox）
- 私钥不可导出，即使 Root 也无法提取
- 使用 ECDSA secp256r1（P-256）算法

### 2. 加密存储

- EncryptedSharedPreferences 存储敏感数据
- MasterKey 存储在 Android Keystore
- AES256-GCM 加密（值），AES256-SIV（密钥）

### 3. 设备指纹

- 基于多标识符组合（Android ID + Build 信息 + 安装 ID）
- SHA256 哈希，不可逆
- 应用卸载后重置（尊重用户隐私）

### 4. 日志安全

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

当前版本不支持密钥轮换。如需支持，需在 `KeystoreManager` 中添加版本管理。

## 📝 变更日志

- **v1.0.0** (2026-03-17) - 初始版本
  - KeystoreManager: ECDSA 密钥生成/签名
  - EncryptedStorage: AES256-GCM 加密存储
  - DeviceFingerprint: 设备指纹生成
  - SecurityModule: 统一入口

## 📚 参考资料

- [Android Keystore System](https://developer.android.com/training/articles/keystore)
- [EncryptedSharedPreferences](https://developer.android.com/reference/androidx/security/crypto/EncryptedSharedPreferences)
- [OpenClaw Pairing Protocol](https://docs.openclaw.ai/channels/pairing)
- [ClawChat Setup Spec](../../project-specs/clawchat-setup.md)
