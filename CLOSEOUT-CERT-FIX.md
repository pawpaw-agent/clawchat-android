# 紧急修复：证书固定占位符 - Closeout

**任务**: 紧急修复 - 证书固定占位符  
**优先级**: 🔴 严重（阻塞发布）  
**来源**: Code Review #002  
**状态**: ✅ 完成  
**完成时间**: 2026-03-18 13:05 GMT+8  
**CI 状态**: ✅ 通过 (#23229892917)

---

## 📋 问题描述

**问题**: `NetworkModule.kt` 中使用了无效的证书固定占位符

```kotlin
// ❌ 错误代码
.add("*.openclaw.ai", "sha256/production_pin_1")
.add("*.openclaw.ai", "sha256/production_pin_2")
```

**风险**: 
- 证书固定使用无效占位符会导致生产环境连接失败
- 阻塞发布流程

---

## 🔧 修复方案

### 选择：选项 A - 临时移除证书固定

**理由**:
- 适用于开发/测试阶段
- 降低安全性但可立即使用
- 发布前可快速配置真实证书指纹

### 修复内容

**修改文件**: `NetworkModule.kt`

**修改前**:
```kotlin
// 生产环境添加证书固定
if (!BuildConfig.DEBUG) {
    val certificatePinner = CertificatePinner.Builder()
        // TODO: 替换为实际的证书指纹
        .add("*.openclaw.ai", "sha256/production_pin_1")
        .add("*.openclaw.ai", "sha256/production_pin_2")
        .build()
    builder.certificatePinner(certificatePinner)
}
```

**修改后**:
```kotlin
// 证书固定：临时移除（开发/测试阶段）
// TODO: 发布前配置真实的证书指纹
// if (!BuildConfig.DEBUG) {
//     val certificatePinner = CertificatePinner.Builder()
//         .add("your-domain.com", "sha256/ACTUAL_CERTIFICATE_PIN_HERE")
//         .build()
//     builder.certificatePinner(certificatePinner)
// }
```

**清理**: 移除未使用的 `CertificatePinner` 导入

---

## ✅ 验收标准

| 标准 | 状态 |
|------|------|
| 无效占位符已移除 | ✅ |
| 代码可编译 | ✅ |
| CI 通过 | ✅ (#23229892917) |
| TODO 提醒添加 | ✅ |

---

## 📝 后续任务

### 发布前必须完成

1. **获取真实证书指纹**:
   ```bash
   # 使用 OpenSSL 获取证书 SHA256 指纹
   echo | openssl s_client -connect your-domain.com:443 2>/dev/null | \
     openssl x509 -pubkey -noout | \
     openssl pkey -pubin -outform der | \
     openssl dgst -sha256 -binary | \
     openssl enc -base64
   ```

2. **配置证书固定**:
   ```kotlin
   if (!BuildConfig.DEBUG) {
       val certificatePinner = CertificatePinner.Builder()
           .add("your-domain.com", "sha256/ACTUAL_CERTIFICATE_PIN_HERE")
           .build()
       builder.certificatePinner(certificatePinner)
   }
   ```

3. **测试证书固定**:
   - 测试正常连接
   - 测试证书过期场景
   - 测试中间人攻击防护

---

## 📊 修改统计

| 文件 | 修改行数 | 说明 |
|------|----------|------|
| NetworkModule.kt | -10 行 | 移除证书固定代码 |
| NetworkModule.kt | -1 导入 | 移除 CertificatePinner |

---

## 🎯 提交记录

| 提交 | 说明 |
|------|------|
| `b5e5a64` | fix: 临时移除证书固定占位符 |

---

**紧急修复完成** ✅  
**发布阻塞已解除** ✅
