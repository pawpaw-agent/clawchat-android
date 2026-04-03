# 已知问题与限制

> ClawChat Android 项目的已知问题、安全限制和注意事项

**版本**: 1.2.1  
**最后更新**: 2026-04-03

---

## 📋 目录

1. [安全限制](#-安全限制)
2. [编译环境要求](#-编译环境要求)
3. [用户可见行为变化](#-用户可见行为变化)
4. [计划修复](#-计划修复)

---

## 🔐 安全限制

### 1. API 26-32 设备使用软件密钥存储

**问题描述**:

在 Android API 26-32 设备上，Ed25519 设备私钥使用 BouncyCastle 库生成，并以加密形式存储在 `EncryptedSharedPreferences` 中。虽然使用了 AES-256-GCM 加密，且 MasterKey 存储在 Android Keystore 中，但存在以下理论风险：

- **Root 设备风险**: 如果设备被 Root，攻击者可能提取 Keystore 中的 MasterKey，进而解密私钥
- **内存转储风险**: 私钥在使用时会短暂加载到内存中，可能被内存转储攻击提取

**影响范围**:
- Android 8.0 - Android 12 (API 26-32)
- Android 13+ (API 33+) 使用硬件级 Keystore，不受此限制影响

**缓解措施**:
1. API 33+ 设备自动使用 Android Keystore 硬件级保护
2. 软件密钥路径使用 AES-256-GCM 加密，MasterKey 由 Keystore 保护
3. 签名完成后立即清除内存中的私钥缓存
4. 日志中不输出敏感密钥信息

**建议**:
- 高安全需求用户建议使用 API 33+ 设备
- 企业部署可考虑强制要求 API 33+

**追踪**: 代码审查 #003 - S1

---

### 2. 重连次数限制

**问题描述**:

当 Gateway 服务器不可达时，客户端会自动重连。为防止无限重连消耗电量和流量，设置了以下限制：

- **最大重连次数**: 15 次
- **重连策略**: 指数退避（初始 1 秒，每次翻倍，最大 30 秒）
- **总耗时**: 约 30 分钟（1+2+4+8+16+30+30+...秒）
- **超过限制后**: 停止重连，显示错误提示，需手动重启应用

**用户可见行为**:
- 连接失败时，状态栏显示 "连接失败：已达最大重连次数"
- 用户需要手动点击 "重新连接" 或重启应用

**设计理由**:
- 防止服务器永久下线时客户端无限重连
- 节省电池和流量
- 避免后台服务持续占用资源

**追踪**: 代码审查 #003 - S2

---

## 🛠 编译环境要求

### 1. Android SDK 许可证

**要求**:
- 必须接受 Android SDK 许可证才能构建项目
- 某些 SDK 组件（如 Build-Tools、Platform-Tools）需要单独接受许可证

**常见问题**:

```bash
# 许可证未接受错误
FAILURE: Build failed with an exception.

* What went wrong:
A problem occurred configuring root project 'clawchat-android'.
> You have not accepted the license agreements of the following SDK components:
  [Android SDK Build-Tools 34, Android SDK Platform 34].
```

**解决方案**:

```bash
# 方法 1: 使用 sdkmanager 接受许可证
$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --licenses

# 方法 2: 在 Android Studio 中构建时自动提示接受
# 方法 3: 复制已有许可证文件
cp -r ~/.android/licenses $ANDROID_HOME/licenses/
```

### 2. JDK 版本要求

**要求**: JDK 17 或 JDK 21

```bash
# 检查 JDK 版本
java -version

# 应该输出类似:
# openjdk version "17.0.8" 2023-07-18
```

### 3. Gradle 版本

**要求**: Gradle 8.5+

项目使用 Gradle Wrapper，会自动下载正确版本：

```bash
./gradlew --version
```

### 4. 内存要求

**建议**:
- 最低 4GB RAM
- 推荐 8GB+ RAM（构建速度更快）

在 `gradle.properties` 中配置：

```properties
org.gradle.jvmargs=-Xmx2048m -XX:MaxMetaspaceSize=512m
```

---

## 👤 用户可见行为变化

### 1. 连接失败提示

**v0.2.0 之前**:
- 无限重连，无明显错误提示
- 用户不知道连接是否成功

**v0.2.0 及之后**:
- 15 次重连失败后显示明确错误："已达最大重连次数，请检查网络或手动重连"
- 状态栏显示红色错误图标
- 提供"重新连接"按钮

### 2. 设备配对流程

**v0.2.0 之前**:
- 配对失败时无明确提示
- 用户不知道是否配对成功

**v0.2.0 及之后**:
- 配对中显示"等待管理员批准..."
- 配对成功显示"设备已配对"
- 配对失败显示具体原因（超时/拒绝）

### 3. 消息发送状态

**v0.2.0 之前**:
- 消息发送后无状态指示
- 发送失败时消息丢失

**v0.2.0 及之后**:
- 发送中：消息旁显示旋转图标
- 发送成功：显示绿色勾选
- 发送失败：显示红色感叹号，可点击重试

### 4. 日志输出

**v0.2.0 之前**:
- Release 构建中仍输出详细日志
- 可能泄露敏感信息（nonce、deviceId）

**v0.2.0 及之后**:
- Release 构建仅输出错误级别日志
- 敏感信息自动脱敏
- 使用 `SecureLogger` 统一日志管理

---

## 📅 计划修复

### 近期（v0.3.0）

| 问题 | 优先级 | 预计版本 |
|------|--------|----------|
| 添加单元测试覆盖（KeystoreManager、GatewayConnection） | 高 | v0.3.0 |
| GatewayUrlUtil IPv6 支持 | 中 | v0.3.0 |
| 添加正式 Room 数据库 Migration | 高 | v0.3.0 |

### 中期（v0.4.0）

| 问题 | 优先级 | 预计版本 |
|------|--------|----------|
| API 26-32 设备支持硬件密钥（如可用） | 中 | v0.4.0 |
| 添加端到端加密测试套件 | 中 | v0.4.0 |
| 优化重连策略（可配置次数） | 低 | v0.4.0 |

### 长期（v1.0.0）

| 问题 | 优先级 | 预计版本 |
|------|--------|----------|
| 完整安全审计 | 高 | v1.0.0 |
| 通过 Google Play 安全审核 | 高 | v1.0.0 |
| 支持生物认证解锁应用 | 中 | v1.0.0 |

---

## 📞 报告问题

如果您发现新的问题或有安全疑虑，请通过以下方式报告：

- **GitHub Issues**: https://github.com/pawpaw-agent/clawchat-android/issues
- **安全漏洞**: 请发送电子邮件至 security@openclaw.ai（不要公开披露）

---

## 📚 相关文档

- [CHANGELOG.md](../CHANGELOG.md) - 版本变更历史
- [README.md](../README.md) - 项目说明
- [代码审查 #003](../reviews/code-review-003.md) - 详细问题列表

---

*最后更新：2026-03-19*
