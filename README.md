# ClawChat Android 📱

<div align="center">

**OpenClaw 第三方 Android 客户端（非官方项目）**

[![Platform](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com/)
[![API](https://img.shields.io/badge/API-26%2B-brightgreen.svg)](https://android-arsenal.com/api?level=26)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0%2B-purple.svg)](https://kotlinlang.org/)
[![Compose](https://img.shields.io/badge/Compose-1.6%2B-blue.svg)](https://developer.android.com/compose)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

</div>

---

## 📖 目录

1. [项目介绍](#-项目介绍)
2. [功能特性](#-功能特性)
3. [技术架构](#-技术架构)
4. [快速开始](#-快速开始)
5. [构建指南](#-构建指南)
6. [使用文档](#-使用文档)
7. [安全设计](#-安全设计)
8. [测试](#-测试)
9. [贡献指南](#-贡献指南)
10. [常见问题](#-常见问题)

---

## 📱 项目介绍

ClawChat 是 OpenClaw 生态系统的**第三方** Android 客户端应用（非官方项目），提供与 OpenClaw Gateway 的实时通信能力，让开发者和管理员能够随时随地监控和管理 AI Agent 会话。

> ⚠️ **声明**: 本项目为第三方客户端，与 OpenClaw 官方无关联。OpenClaw® 为 OpenClaw 项目所有。

### 产品定位

- 🔐 **安全优先** - 硬件级密钥存储，端到端加密通信
- 📱 **原生体验** - Jetpack Compose + Material Design 3
- 🌐 **灵活连接** - 支持局域网和 Tailscale 远程访问
- 🔔 **实时通知** - WebSocket 推送 + 系统通知集成
- 📦 **轻量高效** - APK < 15MB，低内存占用

### 目标用户

| 用户类型 | 使用场景 |
|----------|----------|
| **开发者** | 本地调试、远程管理 OpenClaw 节点、查看 Agent 输出 |
| **高级用户** | 移动端监控会话、接收 cron 提醒、执行快捷命令 |
| **运维人员** | 7x24 小时值班、紧急响应、系统状态监控 |

### 系统要求

| 项目 | 要求 |
|------|------|
| Android 版本 | API 26 (Android 8.0) 及以上 |
| 内存 | 最低 2GB RAM |
| 存储 | 50MB 可用空间 |
| 网络 | Wi-Fi 或移动数据（支持 Tailscale） |

---

## ✨ 功能特性

### 核心功能

#### 🔗 连接管理

- ✅ 添加/编辑/删除多个 Gateway 连接配置
- ✅ 实时连接状态显示（延迟、最后活动时间）
- ✅ 断线自动重连（可配置间隔）
- ✅ 局域网直连 + Tailscale 远程访问

#### 💬 会话管理

- ✅ 查看所有活动会话列表
- ✅ 会话详情（模型、状态、耗时、消息数）
- ✅ 完整消息历史（支持文本、图片、代码块）
- ✅ 文本消息发送
- ✅ 图片/文件附件上传
- ✅ 会话操作（暂停、终止、删除）

#### 🔔 通知系统

- ✅ 新消息系统通知推送
- ✅ 按会话/类型配置通知偏好
- ✅ 勿扰模式（定时静音）
- ✅ Cron 任务提醒

#### 🔐 安全功能

- ✅ 设备配对（首次连接需管理员批准）
- ✅ 设备令牌管理（查看/撤销）
- ✅ 生物认证（指纹/面部解锁，可选）
- ✅ Android Keystore 硬件级密钥存储

### 界面特性

- 🎨 **Material Design 3** - 遵循最新 Material 设计规范
- 🌓 **深色模式** - 支持系统深色/浅色主题自动切换
- ♿ **无障碍** - 支持 TalkBack，满足 WCAG 2.1 AA 标准
- 🌍 **国际化** - 支持中文、英文（可扩展）

---

## 🏗 技术架构

### 架构模式

采用 **Clean Architecture + MVVM** 混合架构：

```
┌─────────────────────────────────────────────────────────────┐
│                    Presentation Layer                        │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐  │
│  │  Screens    │  │ Components  │  │     ViewModels      │  │
│  │ (Compose)   │  │  (UI)       │  │     (StateFlow)     │  │
│  └─────────────┘  └─────────────┘  └─────────────────────┘  │
├─────────────────────────────────────────────────────────────┤
│                      Domain Layer                            │
│  ┌─────────────────────────────────────────────────────────┐│
│  │                    Use Cases                             ││
│  │  (ConnectGateway, SendMessage, PairDevice, etc.)        ││
│  └─────────────────────────────────────────────────────────┘│
├─────────────────────────────────────────────────────────────┤
│                       Data Layer                             │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐  │
│  │ Repository  │  │  DataSource │  │       DTOs          │  │
│  │             │  │ (Local/Remote)│  │    (Mappers)      │  │
│  └─────────────┘  └─────────────┘  └─────────────────────┘  │
├─────────────────────────────────────────────────────────────┤
│                    Infrastructure Layer                      │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐  │
│  │   Network   │  │   Storage   │  │      Security       │  │
│  │  (OkHttp)   │  │  (Room/SP)  │  │   (Keystore/Crypto) │  │
│  └─────────────┘  └─────────────┘  └─────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

### 技术栈

| 层级 | 技术选型 |
|------|----------|
| **UI** | Jetpack Compose + Material 3 |
| **架构** | MVVM + Clean Architecture |
| **依赖注入** | Hilt |
| **网络** | OkHttp + WebSocket |
| **本地存储** | Room + EncryptedSharedPreferences |
| **异步** | Kotlin Coroutines + Flow |
| **序列化** | Kotlinx Serialization |
| **导航** | Compose Navigation |

### 项目结构

```
clawchat-android/
├── app/
│   ├── src/main/
│   │   ├── java/com/openclaw/clawchat/
│   │   │   ├── ClawChatApplication.kt    # Application 入口
│   │   │   ├── MainActivity.kt           # 主 Activity
│   │   │   ├── di/                       # Hilt 依赖注入
│   │   │   ├── ui/                       # UI 层
│   │   │   │   ├── theme/                # 主题
│   │   │   │   ├── components/           # 可复用组件
│   │   │   │   ├── screens/              # 屏幕
│   │   │   │   └── navigation/           # 导航
│   │   │   ├── viewmodel/                # ViewModel
│   │   │   ├── data/                     # 数据层
│   │   │   │   ├── repository/           # 仓库
│   │   │   │   ├── local/                # 本地存储
│   │   │   │   └── remote/               # 远程 API
│   │   │   ├── domain/                   # 领域层
│   │   │   └── util/                     # 工具类
│   │   └── res/                          # 资源文件
│   └── build.gradle.kts
├── tests/                                # 单元测试
├── project-docs/                         # 项目文档
├── project-specs/                        # 规格文档
├── build.gradle.kts                      # 根构建配置
├── settings.gradle.kts                   # 项目设置
└── README.md                             # 本文档
```

---

## 🚀 快速开始

### 1. 环境准备

确保已安装以下工具：

```bash
# 必需
- Android Studio Hedgehog (2023.1.1) 或更新
- JDK 17 或 21
- Android SDK (API 34 推荐)
- Git

# 可选
- Tailscale App（用于远程连接）
```

### 2. 克隆项目

```bash
git clone https://github.com/openclaw/clawchat-android.git
cd clawchat-android
```

### 3. 配置本地属性

```bash
# 创建 local.properties
echo "sdk.dir=$ANDROID_HOME" > local.properties

# 或者手动编辑
# sdk.dir=/Users/username/Library/Android/sdk  # macOS
# sdk.dir=C:\\Users\\username\\AppData\\Local\\Android\\Sdk  # Windows
```

### 4. 构建并运行

```bash
# 构建 Debug 版本
./gradlew assembleDebug

# 运行测试
./gradlew test

# 安装到连接的设备
./gradlew installDebug

# 或直接使用 Android Studio 运行
```

### 5. 首次使用

1. 打开应用，进入 **设置** → **Gateway 配置**
2. 添加你的 OpenClaw Gateway 地址（如 `ws://192.168.1.100:18789`）
3. 返回主页，点击 **连接**
4. 首次连接需要设备配对（见 [使用文档](#-使用文档)）

---

## 🛠 构建指南

### 构建变体

| 变体 | 描述 | 用途 |
|------|------|------|
| `debug` | Debug 版本，可调试 | 开发测试 |
| `release` | Release 版本，优化 + 混淆 | 生产发布 |

### 构建命令

```bash
# Debug APK
./gradlew assembleDebug

# Release APK（需配置签名）
./gradlew assembleRelease

# 运行所有测试
./gradlew test

# 生成测试覆盖率报告
./gradlew jacocoTestReport

# 清理构建缓存
./gradlew clean
```

### 签名配置（Release）

1. 创建 `keystore.properties`：

```properties
storePassword=your_store_password
keyPassword=your_key_password
keyAlias=clawchat-release
storeFile=/path/to/keystore.jks
```

2. 在 `app/build.gradle.kts` 中配置签名：

```kotlin
android {
    signingConfigs {
        create("release") {
            // 从 keystore.properties 读取
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
        }
    }
}
```

### 依赖版本

核心依赖版本（`libs.versions.toml`）：

```toml
[versions]
kotlin = "2.0.21"
compose-bom = "2024.12.01"
hilt = "2.53.1"
okhttp = "4.12.0"
room = "2.6.1"
coroutines = "1.9.0"

[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
kotlinx-coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "coroutines" }
hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }
okhttp = { group = "com.squareup.okhttp3", name = "okhttp", version.ref = "okhttp" }
```

---

## 📚 使用文档

### 设备配对流程

首次连接 Gateway 需要管理员批准：

```
┌──────────────┐         ┌──────────────┐         ┌──────────────┐
│   Android    │         │   Gateway    │         │   管理员     │
│    App       │         │   Server     │         │   (CLI/UI)   │
└──────┬───────┘         └──────┬───────┘         └──────┬───────┘
       │                        │                        │
       │  1. 请求配对            │                        │
       │ ──────────────────────>│                        │
       │  (deviceId, publicKey) │                        │
       │                        │                        │
       │                        │  2. 通知管理员          │
       │                        │ ──────────────────────>│
       │                        │  "新设备请求配对"        │
       │                        │                        │
       │                        │  3. 批准               │
       │                        │ <──────────────────────│
       │                        │  openclaw device pair approve
       │                        │                        │
       │  4. 返回设备令牌        │                        │
       │ <──────────────────────│                        │
       │                        │                        │
       │  5. 存储令牌并连接      │                        │
       │                        │                        │
```

**操作步骤：**

1. 在 ClawChat 中添加 Gateway 配置
2. 点击 **配对设备**
3. 在 Gateway 服务器上执行批准命令：
   ```bash
   openclaw device pair approve --device-id <device_id>
   ```
4. 等待应用显示 **已连接**

### 连接 Gateway

#### 局域网连接（推荐）

```
Gateway 地址：ws://192.168.1.100:18789
```

确保 Android 设备与 Gateway 在同一 Wi-Fi 网络。

#### Tailscale 远程连接

1. 在 Gateway 服务器和 Android 设备上安装 [Tailscale](https://tailscale.com/)
2. 加入同一 Tailnet
3. 使用 MagicDNS 名称或 Tailscale IP：
   ```
   Gateway 地址：ws://gateway.tailnet-name.ts.net:18789
   或
   Gateway 地址：ws://100.x.y.z:18789
   ```

### 会话管理

#### 创建会话

1. 点击主页 **+** 按钮
2. 选择模型（如 `aliyun/qwen3.5-plus`）
3. 可选：开启思考模式（Thinking）
4. 输入初始消息

#### 发送消息

- 在输入框输入文本
- 点击 **发送** 或按回车
- 支持多行输入（Shift+Enter 换行）

#### 发送附件

1. 点击输入框旁的 **📎** 按钮
2. 选择图片或文件
3. 确认后发送

#### 查看历史

1. 在会话列表点击会话
2. 滚动查看历史消息
3. 支持搜索消息内容

### 通知配置

1. 进入 **设置** → **通知**
2. 配置以下选项：
   - 启用/禁用通知
   - 按会话配置通知
   - 勿扰时间段
   - 通知音效/振动

### 安全设置

#### 生物认证

1. 进入 **设置** → **安全**
2. 启用 **指纹/面部解锁**
3. 按提示录入生物特征

#### 设备令牌管理

1. 进入 **设置** → **设备信息**
2. 查看当前设备令牌
3. 可撤销令牌（需重新配对）

---

## 🔐 安全设计

### 安全架构

```
┌─────────────────────────────────────────────────────────────┐
│                    Security Layers                           │
├─────────────────────────────────────────────────────────────┤
│  ┌───────────────────────────────────────────────────────┐  │
│  │           Ed25519 密钥对 (Gateway Protocol v3)         │  │
│  │  - API 33+: Android Keystore (TEE/StrongBox)           │  │
│  │  - API 26-32: BouncyCastle + EncryptedSharedPreferences│  │
│  │  - deviceId = SHA-256(raw 32-byte pubkey).hex()        │  │
│  └───────────────────────────────────────────────────────┘  │
│                              │                               │
│                              ▼                               │
│  ┌───────────────────────────────────────────────────────┐  │
│  │           EncryptedSharedPreferences                   │  │
│  │  - AES256-GCM 加密（值）                               │  │
│  │  - AES256-SIV 加密（密钥）                             │  │
│  │  - MasterKey 存储在 Keystore                           │  │
│  └───────────────────────────────────────────────────────┘  │
│                              │                               │
│                              ▼                               │
│  ┌───────────────────────────────────────────────────────┐  │
│  │              TLS 1.3 + WebSocket (Protocol v3)         │  │
│  │  - Challenge-Response Ed25519 签名认证                  │  │
│  │  - v3 签名载荷: 11 段竖线分隔                           │  │
│  │  - RequestTracker 追踪 req/res 匹配                    │  │
│  └───────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

### 数据存储安全

| 数据类型 | 存储位置 | 加密方式 |
|----------|----------|----------|
| Ed25519 私钥 | EncryptedSharedPreferences (API<33) / Keystore (API 33+) | AES256-GCM / 硬件级 |
| 设备令牌 | EncryptedSharedPreferences | AES256-GCM |
| Gateway 地址 | EncryptedSharedPreferences | AES256-GCM |
| 消息缓存 | Room | 明文（本地存储） |

### 密钥管理

```kotlin
// Ed25519 密钥对生成（BouncyCastle 回退路径）
val generator = Ed25519KeyPairGenerator()
generator.init(Ed25519KeyGenerationParameters(SecureRandom()))
val keyPair = generator.generateKeyPair()

// Ed25519 签名（等效 Node.js crypto.sign(null, payload, key)）
val signer = Ed25519Signer()
signer.init(true, privateKeyParams)
signer.update(data, 0, data.size)
val signature = signer.generateSignature()

// v3 签名载荷
// v3|{deviceId}|openclaw-android|ui|operator|operator.read,operator.write|{ms}|{token}|{nonce}|android|phone
```

### 安全最佳实践

- ✅ 禁止备份敏感数据（`allowBackup="false"`）
- ✅ 生产环境禁用明文 HTTP
- ✅ 日志自动脱敏敏感信息
- ✅ 定期轮换设备令牌

---

## 🧪 测试

### 测试框架

- **JUnit 5** - 测试框架
- **MockK** - Kotlin Mocking
- **Kotlinx Coroutines Test** - 协程测试
- **AndroidX Test** - Android 测试支持

### 运行测试

```bash
# 所有测试
./gradlew test

# 特定测试类
./gradlew test --tests "com.openclaw.clawchat.security.KeystoreManagerTest"

# 生成覆盖率报告
./gradlew jacocoTestReport
```

### 测试覆盖

| 模块 | 覆盖率目标 | 当前状态 |
|------|------------|----------|
| 安全模块 | 100% | ✅ |
| 网络模块 | ≥80% | ✅ |
| ViewModel | ≥80% | ✅ |
| UI 组件 | ≥60% | 🔄 |

### 添加测试

在 `tests/src/test/kotlin/` 对应目录下创建测试文件：

```kotlin
@DisplayName("KeystoreManager 测试")
class KeystoreManagerTest {
    
    @Test
    @DisplayName("生成密钥对成功")
    fun `generate key pair successfully`() = runTest {
        // Given
        val manager = KeystoreManager(testContext)
        
        // When
        manager.generateKeyPair()
        
        // Then
        assertTrue(manager.hasKeyPair())
    }
}
```

详细内容见 [Tests README](tests/README.md)。

---

## 🤝 贡献指南

### 代码规范

- 遵循 [Kotlin 代码风格指南](https://kotlinlang.org/docs/coding-conventions.html)
- 使用 Ktlint 进行代码格式化：
  ```bash
  ./gradlew ktlintCheck
  ./gradlew ktlintFormat
  ```

### 提交流程

1. **Fork** 项目
2. 创建功能分支 (`git checkout -b feature/amazing-feature`)
3. 提交更改 (`git commit -m 'Add amazing feature'`)
4. 推送到分支 (`git push origin feature/amazing-feature`)
5. 创建 **Pull Request**

### Commit 规范

遵循 [Conventional Commits](https://www.conventionalcommits.org/)：

```
feat: 添加新功能
fix: 修复 bug
docs: 文档更新
style: 代码格式（不影响功能）
refactor: 重构（非新功能/修复）
test: 添加/修改测试
chore: 构建/工具/配置
```

### 分支策略

```
main          - 生产分支，受保护
develop       - 开发分支
feature/*     - 功能分支
bugfix/*      - 修复分支
release/*     - 发布分支
hotfix/*      - 紧急修复
```

### 代码审查

所有 PR 需要：
- ✅ 至少 1 人审查
- ✅ 所有测试通过
- ✅ 覆盖率不降低
- ✅ 文档更新（如需要）

### 报告问题

在 [GitHub Issues](https://github.com/openclaw/clawchat-android/issues) 报告：
- 🐛 Bug 报告
- 💡 功能建议
- 📖 文档问题

---

## ❓ 常见问题

### Q: 配对一直显示"等待管理员批准"

**A:** 检查以下几点：
1. Gateway 服务是否正常运行
2. 网络是否连通（ping Gateway 地址）
3. 在 Gateway 服务器查看配对请求：
   ```bash
   openclaw device list
   ```
4. 手动批准：
   ```bash
   openclaw device pair approve --device-id <id>
   ```

### Q: 远程连接失败

**A:** 确认 Tailscale 配置：
1. 两端都安装 Tailscale 并登录
2. 检查是否在同一个 Tailnet
3. 使用 MagicDNS 名称而非 IP
4. 检查防火墙规则

### Q: 应用崩溃

**A:** 收集日志并报告：
```bash
adb logcat | grep ClawChat > crash.log
```
在 GitHub Issues 附上日志文件。

### Q: 通知不推送

**A:** 检查以下设置：
1. 系统通知权限已开启
2. 应用内通知设置已启用
3. 电池优化未限制应用
4. WebSocket 连接正常

### Q: 如何重置配对

**A:** 
1. 进入 **设置** → **设备信息**
2. 点击 **撤销令牌**
3. 重新配对

或完全重置：
1. 清除应用数据
2. 重新打开应用

---

## 📄 许可证

Apache License 2.0 - 详见 [LICENSE](LICENSE) 文件。

---

## 📚 相关资源

- [OpenClaw 官方文档](https://docs.openclaw.ai)
- [ClawHub 技能市场](https://clawhub.com)
- [Discord 社区](https://discord.com/invite/clawd)
- [GitHub 仓库](https://github.com/openclaw/clawchat-android)

---

## 🙏 致谢

感谢所有贡献者和 OpenClaw 社区！

<div align="center">

**Made with ❤️ by the OpenClaw Team**

</div>
