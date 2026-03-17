# ClawChat Android 项目规格文档

> 完整技术规格 · 安全架构 · 开发指南

**版本**: 1.0.0  
**最后更新**: 2026-03-17  
**状态**: 规格定义

---

## 📋 目录

1. [项目概述](#1-项目概述)
2. [技术架构](#2-技术架构)
3. [安全设计](#3-安全设计)
4. [功能规格](#4-功能规格)
5. [设备配对流程](#5-设备配对流程)
6. [Tailscale 集成](#6-tailscale-集成)
7. [项目结构](#7-项目结构)
8. [开发环境](#8-开发环境)
9. [依赖项](#9-依赖项)
10. [测试策略](#10-测试策略)
11. [发布流程](#11-发布流程)

---

## 1. 项目概述

### 1.1 产品定位

ClawChat 是 OpenClaw 生态系统的官方 Android 客户端应用，提供：
- 与 OpenClaw Gateway 的实时 WebSocket 通信
- 安全的设备配对与身份认证
- 多会话管理与消息收发
- 支持局域网和 Tailscale 远程连接

### 1.2 目标用户

| 用户类型 | 使用场景 |
|----------|----------|
| 开发者 | 本地调试、远程管理 OpenClaw 节点 |
| 高级用户 | 移动端监控、接收通知、执行命令 |
| 运维人员 | 7x24 小时值班、紧急响应 |

### 1.3 核心特性

- 🔐 **硬件级安全** - Android Keystore 存储设备私钥
- 📱 **原生体验** - Material Design 3，流畅动画
- 🌐 **灵活连接** - 局域网 + Tailscale 远程访问
- 🔔 **实时通知** - WebSocket 推送 + 系统通知
- 📦 **轻量级** - APK < 15MB，低内存占用

---

## 2. 技术架构

### 2.1 整体架构

```
┌─────────────────────────────────────────────────────────────┐
│                      ClawChat Android                        │
├─────────────────────────────────────────────────────────────┤
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐  │
│  │  UI Layer   │  │  ViewModel  │  │  WebSocket Client   │  │
│  │  (Compose)  │  │  (State)    │  │  (OkHttp)           │  │
│  └─────────────┘  └─────────────┘  └─────────────────────┘  │
│                           │                                  │
│                           ▼                                  │
│  ┌─────────────────────────────────────────────────────┐    │
│  │              Repository Layer                        │    │
│  │  ┌───────────────┐  ┌───────────────────────────────┐│    │
│  │  │ Message Repo  │  │  Settings Repo                ││    │
│  │  └───────────────┘  └───────────────────────────────┘│    │
│  └─────────────────────────────────────────────────────┘    │
│                           │                                  │
│                           ▼                                  │
│  ┌─────────────────────────────────────────────────────┐    │
│  │              Secure Storage Layer                     │    │
│  │  ┌───────────────┐  ┌───────────────────────────────┐│    │
│  │  │ Android       │  │ Encrypted SharedPreferences   ││    │
│  │  │ Keystore      │  │ (Device Token, Config)        ││    │
│  │  │ (Key Storage) │  └───────────────────────────────┘│    │
│  │  └───────────────┘                                   │    │
│  └─────────────────────────────────────────────────────┘    │
│                           │                                  │
│                           ▼                                  │
│  ┌─────────────────────────────────────────────────────┐    │
│  │              Network Security Layer                   │    │
│  │  ┌───────────┐  ┌───────────────┐  ┌──────────────┐  │    │
│  │  │ TLS 1.3   │  │ Certificate   │  │ Signature    │  │    │
│  │  │           │  │ Pinning       │  │ Verification │  │    │
│  │  └───────────┘  └───────────────┘  └──────────────┘  │    │
│  └─────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────┘
                           │
                           ▼
              ┌────────────────────────┐
              │   OpenClaw Gateway     │
              │   (WebSocket + TLS)    │
              └────────────────────────┘
```

### 2.2 技术栈

| 层级 | 技术选型 |
|------|----------|
| UI | Jetpack Compose + Material 3 |
| 架构 | MVVM + Clean Architecture |
| 依赖注入 | Hilt |
| 网络 | OkHttp + WebSocket |
| 本地存储 | Room + EncryptedSharedPreferences |
| 异步 | Kotlin Coroutines + Flow |
| 序列化 | Kotlinx Serialization |
| 导航 | Compose Navigation |

### 2.3 最小系统要求

- **Android 版本**: API 26 (Android 8.0) 及以上
- **内存**: 最低 2GB RAM
- **存储**: 50MB 可用空间
- **网络**: Wi-Fi 或移动数据

---

## 3. 安全设计

### 3.1 安全架构概览

详见 [SECURITY-DESIGN.md](../../clawchat-research/SECURITY-DESIGN.md)

### 3.2 数据存储安全

| 数据类型 | 存储位置 | 加密方式 |
|----------|----------|----------|
| 设备私钥 | Android Keystore | 硬件级加密（TEE/StrongBox） |
| 设备令牌 | EncryptedSharedPreferences | AES256-GCM |
| Gateway 地址 | SharedPreferences | 明文（可公开） |
| 用户偏好 | SharedPreferences | 明文（可公开） |
| 消息缓存 | Room (可选 SQLCipher) | 应用级加密 |

### 3.3 密钥管理

#### 密钥对生成规范

```kotlin
// 算法：ECDSA secp256r1 (P-256)
// 用途：设备身份签名
// 存储：AndroidKeyStore，不可导出

val keyPairGenerator = KeyPairGenerator.getInstance(
    KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore"
)

val spec = KeyGenParameterSpec.Builder("clawchat_device_key",
    KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
)
    .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
    .setDigests(KeyProperties.DIGEST_SHA256)
    .setUserAuthenticationRequired(false)
    .setKeyValidityStartFromDate(Date())
    .build()
```

#### 签名操作

```kotlin
// 挑战 - 响应签名流程
fun signChallenge(challenge: ByteArray): ByteArray {
    val keyStore = KeyStore.getInstance("AndroidKeyStore")
    keyStore.load(null)
    
    val entry = keyStore.getEntry("clawchat_device_key", null) 
        as KeyStore.PrivateKeyEntry
    val signature = Signature.getInstance("SHA256withECDSA")
    signature.initSign(entry.privateKey)
    signature.update(challenge)
    
    return signature.sign()
}
```

### 3.4 加密存储实现

```kotlin
// EncryptedSharedPreferences 初始化
fun createEncryptedPrefs(context: Context): SharedPreferences {
    val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    
    return EncryptedSharedPreferences.create(
        context,
        "clawchat_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
}
```

### 3.5 日志安全

```kotlin
object SecureLogger {
    fun d(message: String) {
        if (BuildConfig.DEBUG) {
            Log.d("ClawChat", message.redactSensitive())
        }
    }
    
    private fun String.redactSensitive(): String {
        return this
            .replace(Regex("token[\"']?\\s*[:=]\\s*[\"']?[^,}\"'\\s]+"), "token=***")
            .replace(Regex("key[\"']?\\s*[:=]\\s*[\"']?[^,}\"'\\s]+"), "key=***")
            .replace(Regex("signature[\"']?\\s*[:=]\\s*[\"']?[^,}\"'\\s]+"), "signature=***")
    }
}
```

### 3.6 备份安全

```xml
<!-- AndroidManifest.xml -->
<application
    android:allowBackup="false"
    android:fullBackupContent="false"
    android:dataExtractionRules="@xml/data_extraction_rules">
```

```xml
<!-- res/xml/data_extraction_rules.xml -->
<data-extraction-rules>
    <cloud-backup>
        <exclude domain="sharedpref" path="clawchat_secure_prefs.xml"/>
        <exclude domain="keystore" path="clawchat_device_key"/>
        <exclude domain="database" path="messages.db"/>
    </cloud-backup>
</data-extraction-rules>
```

---

## 4. 功能规格

### 4.1 核心功能

#### 4.1.1 连接管理

| 功能 | 描述 | 优先级 |
|------|------|--------|
| Gateway 配置 | 添加/编辑/删除 Gateway 连接配置 | P0 |
| 连接状态 | 显示连接状态、延迟、最后活动时间 | P0 |
| 自动重连 | 断线后自动重连（可配置间隔） | P1 |
| 多 Gateway | 支持保存多个 Gateway 配置 | P2 |

#### 4.1.2 会话管理

| 功能 | 描述 | 优先级 |
|------|------|--------|
| 会话列表 | 显示所有活动会话 | P0 |
| 会话详情 | 查看会话元数据（模型、状态、耗时） | P0 |
| 会话历史 | 查看消息历史（支持图片/文件） | P0 |
| 发送消息 | 文本消息发送 | P0 |
| 发送附件 | 图片、文件上传 | P1 |
| 会话操作 | 暂停、终止、删除会话 | P1 |

#### 4.1.3 通知系统

| 功能 | 描述 | 优先级 |
|------|------|--------|
| 推送通知 | 新消息系统通知 | P0 |
| 通知配置 | 按会话/类型配置通知 | P1 |
| 勿扰模式 | 定时静音通知 | P2 |

#### 4.1.4 安全功能

| 功能 | 描述 | 优先级 |
|------|------|--------|
| 设备配对 | 首次连接需管理员批准 | P0 |
| 令牌管理 | 查看/撤销设备令牌 | P1 |
| 生物认证 | 指纹/面部解锁应用（可选） | P2 |

### 4.2 用户界面

#### 4.2.1 主要屏幕

```
┌─────────────────────────────────────┐
│  ClawChat              [+][Settings]│
├─────────────────────────────────────┤
│  ┌─────────────────────────────────┐│
│  │ 🟢 Gateway: home-server         ││
│  │    Connected · 45ms             ││
│  └─────────────────────────────────┘│
│                                     │
│  Sessions                           │
│  ┌─────────────────────────────────┐│
│  │ 💬 Product Research             ││
│  │    Last: 2 min ago              ││
│  ├─────────────────────────────────┤│
│  │ 🤖 Code Review                  ││
│  │    Last: 1 hour ago             ││
│  ├─────────────────────────────────┤│
│  │ 📊 Data Analysis                ││
│  │    Last: Yesterday              ││
│  └─────────────────────────────────┘│
│                                     │
│  ┌─────────────────────────────────┐│
│  │ [New Session]    [Quick Action] ││
│  └─────────────────────────────────┘│
└─────────────────────────────────────┘
```

#### 4.2.2 设计原则

- **Material Design 3**: 遵循最新 Material 设计规范
- **深色模式**: 支持系统深色/浅色主题
- **无障碍**: 支持 TalkBack，满足 WCAG 2.1 AA
- **国际化**: 支持中文、英文（可扩展）

---

## 5. 设备配对流程

### 5.1 配对流程时序图

```
┌──────────────┐         ┌──────────────┐         ┌──────────────┐
│   Android    │         │   Gateway    │         │   CLI/UI     │
│    App       │         │   Server     │         │  (Operator)  │
└──────┬───────┘         └──────┬───────┘         └──────┬───────┘
       │                        │                        │
       │  1. Request Pairing    │                        │
       │ ──────────────────────>│                        │
       │  (nodeId, publicKey)   │                        │
       │                        │                        │
       │                        │  2. Notify Operator    │
       │                        │ ──────────────────────>│
       │                        │  "New device requesting
       │                        │   pairing: Android-XYZ" │
       │                        │                        │
       │                        │  3. Approve/Reject     │
       │                        │ <──────────────────────│
       │                        │  "openclaw device pair approve" │
       │                        │                        │
       │  4. Pairing Result     │                        │
       │ <──────────────────────│                        │
       │  (deviceToken)         │                        │
       │                        │                        │
       │  5. Store Token        │                        │
       │  (EncryptedPrefs)      │                        │
       │                        │                        │
       │  6. Connect with Token │                        │
       │ ──────────────────────>│                        │
       │  (Authenticated WS)    │                        │
       │                        │                        │
       │  7. Connection Ready   │                        │
       │ <──────────────────────│                        │
       │                        │                        │
```

### 5.2 签名载荷格式（Protocol v3）

```json
{
  "device": {
    "id": "device_fingerprint_sha256",
    "publicKey": "-----BEGIN PUBLIC KEY-----\nMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE...\n-----END PUBLIC KEY-----"
  },
  "client": {
    "id": "openclaw-android",
    "version": "1.0.0",
    "platform": "android"
  },
  "role": "operator",
  "scopes": ["operator.read", "operator.write"],
  "token": "<gateway_token_or_empty_for_pairing>",
  "nonce": "<challenge_nonce_from_server>",
  "signedAt": 1737264000000,
  "platform": "android",
  "deviceFamily": "phone"
}
```

### 5.3 配对状态处理

| 状态 | 客户端行为 |
|------|------------|
| `pending` | 显示"等待管理员批准"，轮询状态（每 5 秒） |
| `approved` | 存储设备令牌，建立正式连接 |
| `rejected` | 显示拒绝原因，返回配置页面 |
| `timeout` | 配对超时（5 分钟），提示重试 |

### 5.4 错误处理

| 错误码 | 含义 | 处理方式 |
|--------|------|----------|
| `PAIRING_TIMEOUT` | 配对超时 | 提示用户重试，检查网络 |
| `PAIRING_REJECTED` | 管理员拒绝 | 显示拒绝原因 |
| `TOKEN_INVALID` | 令牌失效 | 清除令牌，重新配对 |
| `SIGNATURE_INVALID` | 签名验证失败 | 重置密钥，重新配对 |

---

## 6. Tailscale 集成

### 6.1 连接场景

```
┌─────────────────────────────────────────────────────────────┐
│                      连接场景对比                            │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  局域网（推荐）                                              │
│  ┌──────────┐         ┌──────────┐                         │
│  │ Android  │ ──────> │ Gateway  │  低延迟，无需额外配置    │
│  │  (WiFi)  │  直连   │ (同网络) │                         │
│  └──────────┘         └──────────┘                         │
│                                                             │
│  Tailscale（远程）                                           │
│  ┌──────────┐         ┌──────────┐     ┌──────────────┐    │
│  │ Android  │ ──────> │ Tailscale│ ──> │   Gateway    │    │
│  │ (4G/5G)  │  加密   │  Relay   │     │ (远程服务器) │    │
│  └──────────┘         └──────────┘     └──────────────┘    │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### 6.2 集成方式

#### 方式一：依赖 Tailscale App（推荐）

用户自行安装 Tailscale 官方 App，ClawChat 检测 VPN 状态后使用 Tailscale IP 连接。

```kotlin
fun checkTailscaleConnection(): Boolean {
    val interfaces = NetworkInterface.getNetworkInterfaces()
    while (interfaces.hasMoreElements()) {
        val iface = interfaces.nextElement()
        if (iface.name == "tun0" || iface.name == "tailscale0") {
            return true
        }
    }
    return false
}

fun getTailscaleIp(): String? {
    // 从 Tailscale 接口获取 IP
    // 或使用 MagicDNS 名称
}
```

#### 方式二：MagicDNS 连接

```kotlin
data class GatewayConfig(
    val host: String,
    val port: Int = 18789,
    val useTls: Boolean = false,
    val tlsFingerprint: String? = null
) {
    fun toWebSocketUrl(): String {
        val protocol = if (useTls) "wss" else "ws"
        return "$protocol://$host:$port"
    }
}

// Tailscale MagicDNS 示例
val gatewayConfig = GatewayConfig(
    host = "gateway.tailnet-name.ts.net",  // MagicDNS 名称
    port = 18789,
    useTls = true
)
```

### 6.3 证书固定（TLS Pinning）

```kotlin
fun createPinnedOkHttpClient(fingerprint: String): OkHttpClient {
    val certificatePinner = CertificatePinner.Builder()
        .add("*.ts.net", "sha256/$fingerprint")
        .build()
    
    return OkHttpClient.Builder()
        .certificatePinner(certificatePinner)
        .build()
}
```

### 6.4 Network Security Configuration

```xml
<!-- res/xml/network_security_config.xml -->
<network-security-config>
    <!-- 开发/内网：允许明文和自签名证书 -->
    <domain-config cleartextTrafficPermitted="true">
        <domain includeSubdomains="true">192.168.1.1</domain>
        <domain includeSubdomains="true">10.0.0.1</domain>
        <domain includeSubdomains="true">.local</domain>
    </domain-config>
    
    <!-- 生产环境：强制 TLS + 证书固定 -->
    <domain-config>
        <domain includeSubdomains="true">openclaw.ai</domain>
        <domain includeSubdomains="true">*.ts.net</domain>
        <pin-set>
            <pin digest="SHA-256">production_cert_fingerprint</pin>
        </pin-set>
    </domain-config>
</network-security-config>
```

---

## 7. 项目结构

### 7.1 目录结构

```
clawchat-android/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/openclaw/clawchat/
│   │   │   │   ├── ClawChatApplication.kt      # Application 入口
│   │   │   │   ├── MainActivity.kt             # 主 Activity
│   │   │   │   ├── di/                         # Hilt 依赖注入
│   │   │   │   │   ├── AppModule.kt
│   │   │   │   │   └── NetworkModule.kt
│   │   │   │   ├── ui/                         # UI 层
│   │   │   │   │   ├── theme/                  # 主题
│   │   │   │   │   ├── components/             # 可复用组件
│   │   │   │   │   ├── screens/                # 屏幕
│   │   │   │   │   │   ├── home/
│   │   │   │   │   │   ├── sessions/
│   │   │   │   │   │   ├── settings/
│   │   │   │   │   │   └── pairing/
│   │   │   │   │   └── navigation/             # 导航
│   │   │   │   ├── viewmodel/                  # ViewModel
│   │   │   │   │   ├── MainViewModel.kt
│   │   │   │   │   ├── SessionViewModel.kt
│   │   │   │   │   └── SettingsViewModel.kt
│   │   │   │   ├── data/                       # 数据层
│   │   │   │   │   ├── repository/             # 仓库
│   │   │   │   │   ├── local/                  # 本地存储
│   │   │   │   │   │   ├── dao/
│   │   │   │   │   │   ├── entity/
│   │   │   │   │   │   └── SecureStorage.kt
│   │   │   │   │   └── remote/                 # 远程 API
│   │   │   │   │       ├── GatewayApi.kt
│   │   │   │   │       ├── WebSocketService.kt
│   │   │   │   │       └── dto/
│   │   │   │   ├── domain/                     # 领域层
│   │   │   │   │   ├── model/
│   │   │   │   │   └── usecase/
│   │   │   │   └── util/                       # 工具类
│   │   │   │       ├── SecurityUtils.kt
│   │   │   │       ├── Logger.kt
│   │   │   │       └── Extensions.kt
│   │   │   ├── res/
│   │   │   │   ├── values/                     # 字符串、颜色、样式
│   │   │   │   ├── drawable/                   # 图标
│   │   │   │   ├── xml/                        # Network security config
│   │   │   │   └── raw/                        # 原始资源
│   │   │   └── AndroidManifest.xml
│   │   └── test/                               # 单元测试
│   └── build.gradle.kts
├── gradle/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── local.properties
└── README.md
```

### 7.2 模块划分

| 模块 | 职责 |
|------|------|
| `:app` | 主应用模块 |
| `:core-network` | 网络库封装（可选） |
| `:core-security` | 安全工具封装（可选） |
| `:feature-sessions` | 会话功能模块（可选） |

---

## 8. 开发环境

### 8.1 系统要求

| 组件 | 要求 |
|------|------|
| Android Studio | Hedgehog (2023.1.1) 或更新 |
| JDK | 17 或 21 |
| Android SDK | API 34 (推荐) |
| Gradle | 8.5+ |
| Kotlin | 2.0+ |

### 8.2 环境设置

```bash
# 1. 克隆项目
git clone https://github.com/openclaw/clawchat-android.git
cd clawchat-android

# 2. 配置本地属性
echo "sdk.dir=$ANDROID_HOME" > local.properties

# 3. 构建 Debug 版本
./gradlew assembleDebug

# 4. 运行测试
./gradlew test

# 5. 安装到设备
./gradlew installDebug
```

### 8.3 模拟器配置

推荐配置：
- **设备**: Pixel 7 或更新
- **API**: 34 (Android 14)
- **内存**: 4GB+
- **存储**: 8GB+

### 8.4 真实设备调试

```bash
# 启用 USB 调试
adb devices

# 查看日志
adb logcat | grep ClawChat

# 安装 APK
adb install app/build/outputs/apk/debug/app-debug.apk
```

---

## 9. 依赖项

### 9.1 核心依赖

```kotlin
// app/build.gradle.kts

dependencies {
    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.0.21")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    
    // AndroidX Core
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    
    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    
    // Hilt
    implementation("com.google.dagger:hilt-android:2.53.1")
    ksp("com.google.dagger:hilt-compiler:2.53.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    
    // Network
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    
    // Security
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    
    // Local Storage
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    
    // DataStore (可选，替代 SharedPreferences)
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    
    // Coil (图片加载)
    implementation("io.coil-kt:coil-compose:2.7.0")
    
    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
```

### 9.2 插件配置

```kotlin
// app/build.gradle.kts

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

android {
    namespace = "com.openclaw.clawchat"
    compileSdk = 35
    
    defaultConfig {
        applicationId = "com.openclaw.clawchat"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"
        }
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    
    kotlinOptions {
        jvmTarget = "17"
    }
    
    buildFeatures {
        compose = true
        buildConfig = true
    }
}
```

---

## 10. 测试策略

### 10.1 测试金字塔

```
           /\
          /  \
         / E2E \        端到端测试 (5%)
        /──────\       - UI 流程测试
       /        \      - 关键路径验证
      /──────────\
     /   Integration\   集成测试 (20%)
    /────────────────\  - ViewModel + Repository
   /                  \ - WebSocket 连接测试
  /────────────────────\
 /      Unit Tests      \ 单元测试 (75%)
/────────────────────────\ - 工具函数
                          - 数据转换
                          - 业务逻辑
```

### 10.2 单元测试示例

```kotlin
// SecurityUtilsTest.kt

@Test
fun `sign challenge should produce valid signature`() {
    // Given
    val securityUtils = SecurityUtils(testContext)
    val challenge = "test_challenge_nonce".toByteArray()
    
    // When
    val signature = securityUtils.signChallenge(challenge)
    
    // Then
    assertNotNull(signature)
    assertTrue(signature.size > 0)
}

@Test
fun `device fingerprint should be consistent`() {
    // Given
    val securityUtils = SecurityUtils(testContext)
    
    // When
    val fingerprint1 = securityUtils.generateDeviceId()
    val fingerprint2 = securityUtils.generateDeviceId()
    
    // Then
    assertEquals(fingerprint1, fingerprint2)
}
```

### 10.3 集成测试示例

```kotlin
// WebSocketServiceTest.kt

@Test
fun `connection should succeed with valid token`() = runTest {
    // Given
    val mockApi = MockGatewayApi()
    val service = WebSocketService(mockApi, secureStorage)
    
    // When
    val result = service.connect("ws://test-gateway:18789")
    
    // Then
    assertTrue(result.isSuccess)
}
```

### 10.4 E2E 测试

使用 Espresso + Compose Testing:

```kotlin
@Test
fun pairingFlow_completesSuccessfully() {
    // 打开应用
    // 导航到配对页面
    // 输入 Gateway 地址
    // 点击配对
    // 验证显示"等待批准"状态
    // 模拟批准（通过 mock）
    // 验证连接成功
}
```

### 10.5 测试覆盖率目标

| 类别 | 目标覆盖率 |
|------|------------|
| 单元测试 | ≥80% |
| 关键路径 | 100% |
| 安全模块 | 100% |
| 整体 | ≥75% |

---

## 11. 发布流程

### 11.1 版本管理

遵循 [Semantic Versioning](https://semver.org/):

```
MAJOR.MINOR.PATCH
  │     │     │
  │     │     └─ 向后兼容的 bug 修复
  │     └─────── 向后兼容的新功能
  └───────────── 不兼容的 API 变更
```

### 11.2 构建流程

```bash
# 1. 更新版本号
# app/build.gradle.kts
versionCode = 2
versionName = "1.1.0"

# 2. 生成 Release APK
./gradlew assembleRelease

# 3. 签名 APK（使用 release keystore）
# keystore.properties 配置
# keyAlias=clawchat-release
# storePassword=***
# keyPassword=***

# 4. 验证 APK
apksigner verify --verbose app-release.apk

# 5. 生成变更日志
# 从 Git commits 生成 CHANGELOG.md
```

### 11.3 发布渠道

| 渠道 | 用途 |
|------|------|
| Google Play | 公开分发（如需） |
| GitHub Releases | 开源版本发布 |
| 内部测试 | 团队内部测试 |
| F-Droid | 开源社区分发（可选） |

### 11.4 发布清单

#### 发布前检查

- [ ] 所有测试通过
- [ ] 代码审查完成
- [ ] CHANGELOG.md 已更新
- [ ] 版本号已更新
- [ ] 安全审计通过（无敏感日志、密钥已保护）
- [ ] 性能测试通过（启动时间 < 2s，内存 < 100MB）
- [ ] 无障碍测试通过

#### 发布后检查

- [ ] 监控崩溃率（Firebase Crashlytics）
- [ ] 收集用户反馈
- [ ] 更新文档
- [ ] 通知社区（Discord/GitHub）

### 11.5 热修复流程

对于紧急 bug 修复：

1. 创建 `hotfix/` 分支
2. 修复并测试
3. 快速代码审查
4. 发布补丁版本（PATCH 升级）
5. 合并回 `main` 和 `develop`

---

## 附录

### A. 术语表

| 术语 | 定义 |
|------|------|
| Gateway | OpenClaw 网关服务，处理 WebSocket 连接 |
| Device Token | 设备身份令牌，用于认证 |
| Keystore | Android 系统级密钥存储 |
| Tailscale | 零配置 VPN，用于远程安全连接 |
| Pairing | 设备配对流程，需管理员批准 |

### B. 参考资料

- [Android Keystore System](https://developer.android.com/training/articles/keystore)
- [EncryptedSharedPreferences](https://developer.android.com/reference/androidx/security/crypto/EncryptedSharedPreferences)
- [OpenClaw Gateway Protocol](https://docs.openclaw.ai/gateway/protocol)
- [OpenClaw Pairing](https://docs.openclaw.ai/channels/pairing)
- [Tailscale Documentation](https://tailscale.com/kb)
- [Material Design 3](https://m3.material.io/)
- [Jetpack Compose](https://developer.android.com/compose)

### C. 安全清单

#### 启动前检查

- [ ] 设备密钥已生成并存储在 Keystore
- [ ] 设备令牌使用 EncryptedSharedPreferences 存储
- [ ] WebSocket 连接使用 WSS（生产环境）
- [ ] 签名验证正确实现 v3 格式
- [ ] 敏感日志已脱敏
- [ ] 备份已排除敏感数据

#### 定期检查

- [ ] 设备令牌有效性（连接失败时刷新）
- [ ] TLS 证书有效性
- [ ] 密钥是否需要轮换

#### 泄露应对

| 泄露类型 | 应对措施 |
|----------|----------|
| 设备令牌泄露 | 调用 `device.token.revoke` 撤销 |
| 设备私钥泄露 | 删除 Keystore 密钥，重新配对 |
| Gateway 地址泄露 | 更改 Gateway 认证令牌 |

---

*文档结束*
