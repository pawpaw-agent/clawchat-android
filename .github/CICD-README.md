# ClawChat Android CI/CD 配置指南

> 完整的 GitHub Actions 工作流配置 · 自动构建 · 自动发布 · 代码覆盖率

**版本**: 1.0.0  
**最后更新**: 2026-03-17

---

## 📁 文件结构

```
.github/
└── workflows/
    ├── ci.yml          # 主 CI 流程（构建/测试/lint）
    ├── release.yml     # 自动发布 APK
    └── coverage.yml    # 代码覆盖率报告
```

---

## 🚀 工作流说明

### 1. CI Pipeline (`ci.yml`)

**触发条件**:
- Push 到 `main` 或 `develop` 分支
- Pull Request 到 `main` 或 `develop` 分支

**执行任务**:

| 任务 | 描述 | 超时 |
|------|------|------|
| `lint` | Ktlint + Android Lint + 依赖检查 | 15 分钟 |
| `test` | 单元测试 + 生成测试报告 | 20 分钟 |
| `build` | 构建 Debug/Release APK | 30 分钟 |
| `summary` | 聚合所有任务结果 | - |

**产出物**:
- `lint-reports` - Lint 报告 (HTML/XML)
- `test-reports` - 测试结果 (JUnit XML + HTML)
- `coverage-reports` - 覆盖率报告 (JaCoCo)
- `app-debug` - Debug APK
- `app-release` - Release APK (仅 main 分支)

---

### 2. Release APK (`release.yml`)

**触发条件**:
- 推送版本标签 (如 `v1.0.0`, `v1.2.3-beta`)
- 手动触发 (Workflow Dispatch)

**功能**:
- ✅ 验证版本号格式
- ✅ 构建签名的 Release APK
- ✅ 生成发布说明 (自动从 Git commits 提取)
- ✅ 创建 GitHub Release
- ✅ 上传 APK 到 Release Assets
- ✅ (可选) 提交到 F-Droid

**版本格式**:
```
MAJOR.MINOR.PATCH          # 正式版本 (例：1.0.0)
MAJOR.MINOR.PATCH-beta     # 测试版本 (例：1.0.0-beta)
MAJOR.MINOR.PATCH-alpha    # 预览版本 (例：1.0.0-alpha)
MAJOR.MINOR.PATCH-rc1      # 候选版本 (例：1.0.0-rc1)
```

**手动触发参数**:

| 参数 | 类型 | 默认值 | 描述 |
|------|------|--------|------|
| `version` | string | 1.0.0 | 版本号 |
| `is_prerelease` | boolean | false | 标记为预发布 |
| `generate_tag` | boolean | true | 创建新标签 |

---

### 3. Code Coverage (`coverage.yml`)

**触发条件**:
- Push 到 `main` 分支
- 每周一 2:00 AM UTC (定时任务)
- 手动触发

**功能**:
- ✅ 运行单元测试并收集覆盖率
- ✅ 生成 JaCoCo XML + HTML 报告
- ✅ 上传到 Codecov.io (可选)
- ✅ 生成覆盖率徽章
- ✅ PR 覆盖率门禁 (≥60%)
- ✅ 历史覆盖率追踪

**覆盖率阈值**:

| 覆盖率 | 状态 | 徽章颜色 |
|--------|------|----------|
| ≥80% | 🟢 优秀 | brightgreen |
| 60-80% | 🟡 良好 | yellow |
| <60% | 🔴 需改进 | red |

---

## 🔐 必需配置 (GitHub Secrets)

### 签名密钥 (Release 发布)

```bash
# 1. 生成 Keystore
keytool -genkey -v -keystore clawchat-release.jks \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias clawchat-release

# 2. 编码为 Base64
base64 -w 0 clawchat-release.jks

# 3. 添加到 GitHub Secrets
```

**Secrets 列表**:

| Secret 名称 | 描述 | 示例 |
|-------------|------|------|
| `RELEASE_KEYSTORE_B64` | Keystore 文件 (Base64 编码) | `UEsDBBQAAAg...` |
| `KEYSTORE_PASSWORD` | Keystore 密码 | `your-store-password` |
| `KEY_ALIAS` | 密钥别名 | `clawchat-release` |
| `KEY_PASSWORD` | 密钥密码 | `your-key-password` |
| `CODECOV_TOKEN` | Codecov.io Token (可选) | `xxxxxxxx-xxxx-...` |
| `FDROID_BOT_TOKEN` | F-Droid 提交 Token (可选) | `ghp_xxxxxxxxxxxx` |

### 配置路径

```
GitHub Repository → Settings → Secrets and variables → Actions
```

---

## 📊 代码覆盖率配置

### JaCoCo 设置

在 `app/build.gradle.kts` 中已配置:

```kotlin
jacoco {
    toolVersion = "0.8.12"
}

tasks.register<JacocoReport>("jacocoTestReport") {
    dependsOn("testDebugUnitTest")
    
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}
```

### 运行本地覆盖率

```bash
# 运行测试并生成报告
./gradlew testDebugUnitTest jacocoTestReport

# 查看 HTML 报告
open app/build/reports/jacoco/jacocoTestReport/html/index.html
```

### 排除项

以下文件自动排除在覆盖率统计外:

- R 类、BuildConfig、Manifest
- 测试类 (*Test*)
- Hilt 生成类
- Compose 编译器生成类
- di/、ui/theme/ 包

---

## 🏷️ 发布流程

### 方式一：推送标签 (推荐)

```bash
# 1. 更新版本号 (app/build.gradle.kts)
versionCode = 2
versionName = "1.1.0"

# 2. 提交并推送
git add .
git commit -m "chore: release v1.1.0"
git tag v1.1.0
git push origin main --tags

# 3. GitHub Actions 自动构建发布
```

### 方式二：手动触发

1. 进入 GitHub Actions → Release APK
2. 点击 "Run workflow"
3. 填写参数:
   - Version: `1.1.0`
   - Mark as pre-release: `false`
   - Create new tag: `true`
4. 点击 "Run workflow"

### 发布后检查

- [ ] GitHub Release 已创建
- [ ] APK 已上传到 Assets
- [ ] 发布说明正确
- [ ] (可选) F-Droid PR 已创建

---

## 📈 查看报告

### CI 状态

```
Pull Request → Checks 标签页
```

### 覆盖率报告

```
Actions → Code Coverage → 最新运行 → Artifacts
- coverage-xml (Codecov 上传)
- coverage-html (浏览器查看)
```

### 历史趋势

```
.github/coverage-data/coverage-history.csv
```

---

## 🔧 自定义配置

### 添加新的 Lint 规则

编辑 `ci.yml`:

```yaml
- name: Run Custom Lint
  run: ./gradlew yourCustomLintTask
```

### 修改覆盖率阈值

编辑 `coverage.yml`:

```yaml
env:
  MIN_COVERAGE: 70  # 改为 70%
```

### 添加通知

在 `release.yml` 中添加:

```yaml
- name: Notify Discord
  uses: sarisia/actions-status-discord@v1
  with:
    webhook: ${{ secrets.DISCORD_WEBHOOK }}
    title: "ClawChat v${{ needs.validate.outputs.version }} Released!"
```

---

## 🐛 故障排除

### 构建失败

```bash
# 本地复现
./gradlew clean assembleRelease --no-daemon --stacktrace

# 检查 Gradle 缓存
rm -rf ~/.gradle/caches
```

### 签名失败

```bash
# 验证 Keystore
keytool -list -v -keystore clawchat-release.jks

# 检查密码
jarsigner -verify -verbose -certs app-release.apk
```

### 覆盖率报告为空

```bash
# 确保测试已运行
./gradlew testDebugUnitTest

# 检查执行文件
find . -name "*.exec" -o -name "jacocoTestReport.xml"
```

---

## 📚 相关文档

- [GitHub Actions 文档](https://docs.github.com/en/actions)
- [JaCoCo 官方文档](https://www.jacoco.org/jacoco/)
- [Android CI/CD 最佳实践](https://developer.android.com/studio/build/building-cmdline)
- [Codecov 配置指南](https://docs.codecov.com/)

---

*CI/CD 配置完成 · 自动化部署就绪*
