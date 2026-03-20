# ClawChat Android 安全审计报告

**日期**: 2026-03-20  
**审查人**: AI Assistant  
**项目**: ClawChat Android (OpenClaw 客户端)  
**版本**: 1.0.3 (b34ecc2)  
**审计范围**: 密钥管理、网络通信、证书验证、数据加密

---

## 🔐 安全架构总览

```
┌─────────────────────────────────────────────────────────────────┐
│                    Security Layers                               │
├─────────────────────────────────────────────────────────────────┤
│  ┌───────────────────────────────────────────────────────────┐  │
│  │              Android Keystore (API 33+)                    │  │
│  │  - Ed25519 硬件级密钥                                      │  │
│  │  - 私钥不可导出                                            │  │
│  │  - TEE/StrongBox 保护                                      │  │
│  └───────────────────────────────────────────────────────────┘  │
│                              │                                   │
│                              ▼                                   │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │         BouncyCastle + EncryptedSharedPreferences          │  │
│  │         (API 26-32 软件回退)                                │  │
│  │  - Ed25519 软件实现                                        │  │
│  │  - AES-256-GCM 加密存储                                    │  │
│  │  - MasterKey 在 Keystore                                   │  │
│  └───────────────────────────────────────────────────────────┘  │
│                              │                                   │
│                              ▼                                   │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │              证书信任管理 (TOFU)                            │  │
│  │  - 首次连接用户确认                                         │  │
│  │  - 指纹存储于 EncryptedSharedPreferences                   │  │
│  │  - 证书变更告警                                            │  │
│  └───────────────────────────────────────────────────────────┘  │
│                              │                                   │
│                              ▼                                   │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │              Protocol v3 签名                              │  │
│  │  - v3 payload: "v3|deviceId|clientId|...|nonce|..."       │  │
│  │  - Ed25519 签名                                            │  │
│  │  - 公钥：raw 32 bytes base64url                            │  │
│  │  - deviceId: sha256(raw public key).hex()                 │  │
│  └───────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

---

## 🔑 密钥管理审计

### Ed25519 密钥生成

**实现**: `KeystoreManager.kt`

| API 级别 | 实现方式 | 安全等级 |
|----------|----------|----------|
| **API 33+** | Android Keystore 硬件 Ed25519 | 🔒 高 (TEE/StrongBox) |
| **API 26-32** | BouncyCastle + EncryptedSharedPreferences | ⚠️ 中 (软件实现) |

**密钥生成流程**:
```kotlin
// API 33+
KeyPairGenerator.getInstance("Ed25519", "AndroidKeyStore")
    .initialize(KeyGenParameterSpec.Builder(alias, PURPOSE_SIGN or PURPOSE_VERIFY).build())
    .generateKeyPair()

// API 26-32
Ed25519KeyPairGenerator()
    .init(Ed25519KeyGenerationParameters(SecureRandom()))
    .generateKeyPair()
// 存储到 EncryptedSharedPreferences (AES-256-GCM)
```

**审计发现**:

| 检查项 | 状态 | 说明 |
|--------|------|------|
| 密钥算法 | ✅ Ed25519 | 符合 Gateway 协议 v3 |
| 密钥长度 | ✅ 256-bit | 标准 Ed25519 |
| 随机数生成 | ✅ SecureRandom | 密码学安全 |
| 私钥存储 | ⚠️ 部分安全 | API 33+ 硬件级，API 26-32 软件加密 |
| 私钥导出防护 | ✅ 不可导出 | Keystore 路径私钥不可提取 |
| 密钥生命周期 | ✅ 可删除 | deleteKey() 清除 |

---

### 签名实现

**实现**: `KeystoreManager.sign()`, `SecurityModule.signV3Payload()`

**v3 Payload 格式**:
```
"v3|{deviceId}|{clientId}|{clientMode}|{role}|{scopes}|{signedAtMs}|{token}|{nonce}|{platform}|{deviceFamily}"
```

**签名流程**:
```kotlin
// 1. 构建 payload
val payload = listOf(
    "v3", deviceId, clientId, clientMode, role,
    scopes.joinToString(","), signedAtMs.toString(),
    token, nonce, normalizeDeviceMetadata(platform),
    normalizeDeviceMetadata(deviceFamily)
).joinToString("|")

// 2. Ed25519 签名
val signature = keystoreManager.sign(payload)

// 3. base64url 编码（无填充）
val signatureBase64Url = Base64.encodeToString(signature, URL_SAFE or NO_WRAP or NO_PADDING)
```

**审计发现**:

| 检查项 | 状态 | 说明 |
|--------|------|------|
| Payload 格式 | ✅ 符合 Gateway 规范 | 与 Gateway buildDeviceAuthPayloadV3 一致 |
| 签名算法 | ✅ Ed25519 | 等效 Node.js crypto.sign |
| 编码格式 | ✅ base64url 无填充 | Gateway 要求 |
| 元数据标准化 | ✅ NFKD 归一化 | 与 Gateway normalizeDeviceMetadataForAuth 一致 |
| nonce 验证 | ✅ 检查空 nonce | handleChallenge 拒绝空 nonce |
| token 绑定 | ✅ token 参与签名 | 防止 token 劫持 |

---

## 📡 网络通信审计

### 证书验证

**实现**: `GatewayTrustManager.kt`, `CertificateFingerprintManager.kt`

**信任模式**:
1. **系统证书** — 标准 HTTPS 证书（银行、Google 等）
2. **用户信任证书** — TOFU 模式，首次连接用户确认

**TOFU 流程**:
```
首次连接 → 获取证书指纹 → 抛出 CertificateExceptionFirstTime
    ↓
UI 显示指纹 → 用户确认
    ↓
保存到 EncryptedSharedPreferences
    ↓
后续连接 → 验证指纹匹配
    ↓
不匹配 → 抛出 CertificateExceptionMismatch → UI 告警
```

**审计发现**:

| 检查项 | 状态 | 说明 |
|--------|------|------|
| 证书指纹算法 | ✅ SHA-256 | 标准指纹算法 |
| 指纹存储 | ✅ EncryptedSharedPreferences | AES-256-GCM 加密 |
| 首次连接确认 | ✅ 用户手动确认 | SSH 风格 TOFU |
| 证书变更告警 | ✅ CertificateExceptionMismatch | 防中间人攻击 |
| 系统证书兼容 | ✅ 复合 TrustManager | 不影响正常 HTTPS |
| 主机名传递 | ⚠️ 未实现 | TODO: 通过 EventListener 传递 |

**关键代码**:
```kotlin
// DynamicTrustManager.checkServerTrusted()
val fingerprint = serverCert.getSha256Fingerprint()
val status = fingerprintManager.isTrusted(hostname, fingerprint)

when (status) {
    is Trusted -> return // 信任
    is NotTrusted -> throw CertificateExceptionFirstTime(...) // 首次连接
    is Mismatch -> throw CertificateExceptionMismatch(...) // 证书变更
}
```

---

### WebSocket 通信

**实现**: `GatewayConnection.kt`, `OkHttpWebSocketService.kt`

**握手流程**:
```
1. WebSocket 连接建立
2. 收到 connect.challenge (含 nonce)
3. 构建 v3 签名 payload → Ed25519 签名
4. 发送 connect req (含签名)
5. 收到 hello-ok res (含 deviceToken)
6. 存储 deviceToken，连接成功
```

**审计发现**:

| 检查项 | 状态 | 说明 |
|--------|------|------|
| 挑战 - 响应 | ✅ 完整实现 | 等待 challenge → 签名 → 发送 connect |
| nonce 验证 | ✅ 拒绝空 nonce | handleChallenge 检查 |
| Request-Response 匹配 | ✅ RequestTracker | 根据 id 匹配响应 |
| 超时处理 | ✅ 60s 认证超时 | withTimeoutOrNull |
| 重连限制 | ✅ 最多 15 次 | MAX_RECONNECT_ATTEMPTS = 15 |
| 指数退避 | ✅ 1s-30s | INITIAL_RECONNECT_DELAY_MS = 1000 |

---

## 🔒 数据加密审计

### EncryptedSharedPreferences

**实现**: `EncryptedStorage.kt`, `CertificateFingerprintManager.kt`

**加密配置**:
```kotlin
val masterKey = MasterKey.Builder(context)
    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
    .build()

EncryptedSharedPreferences.create(
    context,
    PREFS_NAME,
    masterKey,
    PrefKeyEncryptionScheme.AES256_SIV,  // 密钥加密
    PrefValueEncryptionScheme.AES256_GCM // 值加密
)
```

**存储内容**:
- 设备令牌 (deviceToken)
- 设备 ID (deviceId)
- Gateway URL
- TLS 指纹
- 证书指纹 (gw:{hostname})

**审计发现**:

| 检查项 | 状态 | 说明 |
|--------|------|------|
| 密钥加密 | ✅ AES-256-SIV | 确定性加密，防重放 |
| 值加密 | ✅ AES-256-GCM | 认证加密 |
| MasterKey 存储 | ✅ Android Keystore | 硬件级保护 |
| 密钥轮换 | ✅ 自动处理 | EncryptedSharedPreferences 特性 |
| 访问控制 | ✅ 仅本应用可访问 | Android 沙箱 |

---

### 日志脱敏

**实现**: `SecureLogger.kt`

**脱敏规则**:
```kotlin
private fun String.redactSensitive(): String = this
    .replace(Regex("token[\"']?\\s*[:=]\\s*[\"']?[^,}\"'\\s]+"), "token=***")
    .replace(Regex("key[\"']?\\s*[:=]\\s*[\"']?[^,}\"'\\s]+"), "key=***")
    .replace(Regex("signature[\"']?\\s*[:=]\\s*[\"']?[^,}\"'\\s]+"), "signature=***")
    .replace(Regex("secret[\"']?\\s*[:=]\\s*[\"']?[^,}\"'\\s]+"), "secret=***")
    .replace(Regex("password[\"']?\\s*[:=]\\s*[\"']?[^,}\"'\\s]+"), "password=***")
```

**审计发现**:

| 检查项 | 状态 | 说明 |
|--------|------|------|
| token 脱敏 | ✅ | Bearer token, deviceToken |
| 密钥脱敏 | ✅ | privateKey, publicKey |
| 签名脱敏 | ✅ | signature |
| nonce 脱敏 | ⚠️ 部分 | 仅记录前 8 字符 (NONCE_LOG_PREFIX_LEN = 8) |
| Release 构建 | ⚠️ 未区分 | 建议 BuildConfig.DEBUG 检查 |

---

## 🚨 安全风险评级

### 高风险

| 风险 | 可能性 | 影响 | 缓解措施 | 状态 |
|------|--------|------|----------|------|
| API 26-32 私钥提取 (root 设备) | 低 | 高 | 记录为已知限制 | ⚠️ 接受 |
| fallbackToDestructiveMigration | 中 | 高 | 发布前添加 Migration | ❌ 待修复 |

### 中风险

| 风险 | 可能性 | 影响 | 缓解措施 | 状态 |
|------|--------|------|----------|------|
| BouncyCastle 私钥内存缓存 | 低 | 中 | 签名后清除 | ⚠️ 部分修复 |
| 主机名传递未实现 | 中 | 中 | EventListener 传递 | ❌ 待修复 |
| Release 构建日志脱敏 | 低 | 中 | BuildConfig.DEBUG 检查 | ❌ 待修复 |

### 低风险

| 风险 | 可能性 | 影响 | 缓解措施 | 状态 |
|------|--------|------|----------|------|
| RequestIdGenerator 非线程安全 | 低 | 低 | AtomicLong | ❌ 待修复 |
| 证书指纹存储密钥轮换 | 低 | 低 | EncryptedSharedPreferences 自动处理 | ✅ 已缓解 |

---

## ✅ 安全最佳实践遵循

| 实践 | 状态 | 说明 |
|------|------|------|
| 最小权限原则 | ✅ | 仅请求必要权限 |
| 数据加密存储 | ✅ | EncryptedSharedPreferences |
| 密钥硬件保护 | ✅ (API 33+) | Android Keystore |
| 证书验证 | ✅ | TOFU + 系统证书 |
| 日志脱敏 | ✅ | SecureLogger |
| 防重放攻击 | ✅ | nonce + timestamp |
| 安全随机数 | ✅ | SecureRandom |
| 超时处理 | ✅ | connect timeout 60s |
| 重连限制 | ✅ | 最多 15 次 |

---

## 📋 安全修复优先级

### P0 (发布前必须修复)

| # | 问题 | 文件 | 工时 |
|---|------|------|------|
| 1 | fallbackToDestructiveMigration | `ClawChatDatabase.kt` | 2h |

### P1 (高优先级)

| # | 问题 | 文件 | 工时 |
|---|------|------|------|
| 2 | 主机名传递未实现 | `GatewayTrustManager.kt` | 2h |
| 3 | BouncyCastle 私钥缓存 | `KeystoreManager.kt` | 1h |
| 4 | Release 构建日志脱敏 | `SecureLogger.kt` | 0.5h |

### P2 (中优先级)

| # | 问题 | 文件 | 工时 |
|---|------|------|------|
| 5 | RequestIdGenerator 线程安全 | `RequestFrame.kt` | 0.5h |
| 6 | API 26-32 安全警告 | 文档 | 0.5h |

---

## 📊 安全评分

| 维度 | 评分 | 说明 |
|------|------|------|
| **密钥管理** | ⭐⭐⭐⭐☆ | Ed25519 双路径，API 33+ 硬件级 |
| **网络通信** | ⭐⭐⭐⭐⭐ | TOFU 证书管理，完整握手流程 |
| **数据加密** | ⭐⭐⭐⭐⭐ | AES-256-GCM, Keystore MasterKey |
| **日志脱敏** | ⭐⭐⭐⭐☆ | 完整脱敏规则，Release 构建待完善 |
| **错误处理** | ⭐⭐⭐⭐☆ | 超时、重连限制、异常捕获 |

**整体安全评分**: ⭐⭐⭐⭐☆ (4/5)

---

## 📝 总结

ClawChat Android 安全实现整体优秀，主要亮点：

1. **Ed25519 密钥管理** — API 33+ 硬件级，API 26-32 软件加密回退
2. **TOFU 证书信任** — SSH 风格首次连接确认，防中间人攻击
3. **Protocol v3 签名** — 完整实现，与 Gateway 对齐
4. **数据加密存储** — AES-256-GCM + Keystore MasterKey

**主要风险**:
- fallbackToDestructiveMigration (发布前必须修复)
- API 26-32 私钥存储 (接受为已知限制)
- 主机名传递未实现 (影响证书验证)

**建议**: 修复 P0 问题后可发布，P1 问题在 v1.0.4 中修复。
