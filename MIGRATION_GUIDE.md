# Migration Guide: v1.1.0 → v1.2.0

本文档帮助您从 v1.1.0 迁移到 v1.2.0。

---

## 重大变更

### 1. 新依赖

v1.2.0 新增了以下依赖：

```kotlin
// app/build.gradle.kts
implementation("com.google.zxing:core:3.5.2")
implementation("com.journeyapps:zxing-android-embedded:4.3.0")
implementation("androidx.datastore:datastore-preferences:1.0.0")
```

### 2. 新权限

```xml
<!-- AndroidManifest.xml -->
<uses-permission android:name="android.permission.RECORD_AUDIO" />
```

---

## API 变更

### 新增工具类

| 工具类 | 用途 |
|--------|------|
| `QRCodeUtils` | 二维码生成 |
| `VoiceInputManager` | 语音输入 |
| `RecentPromptsStore` | 提示词存储 |
| `DraftStore` | 草稿保存 |
| `ExportUtils` | 消息导出 |
| `StatsUtils` | 字数统计 |
| `DateTimeUtils` | 日期格式化 |
| `StringUtils` | 字符串处理 |
| `CollectionUtils` | 集合处理 |
| `ValidationUtils` | 输入验证 |

### 新增扩展函数

#### String 扩展

```kotlin
// 截断
"hello world".truncate(8)  // "hello..."

// 移除协议
"https://example.com".removeProtocol()  // "example.com"

// 添加协议
"example.com".addProtocol()  // "https://example.com"

// 字数统计
"你好hello世界".countCharacters()  // 4 (中文) + 2 (英文) = 6
```

#### Modifier 扩展

```kotlin
// 无涟漪点击
Modifier.clickableNoRipple { }

// 防抖点击
Modifier.debounceClickable(500) { }

// 双击检测
Modifier.doubleClick(
    onDoubleClick = { },
    onClick = { }
)
```

---

## 新增组件

### UI 组件

```kotlin
// 动画 FAB
AnimatedFloatingActionButton(
    visible = true,
    onClick = { },
    icon = Icons.Default.Add,
    contentDescription = "添加"
)

// 进度按钮
ProgressButton(
    text = "发送",
    onClick = { },
    loading = isLoading,
    progress = 0.5f
)

// 可展开卡片
ExpandableCard(
    title = "详情",
    expanded = isExpanded
) {
    // 内容
}

// 撤销 Snackbar
UndoSnackbar(
    visible = showUndo,
    message = "已删除",
    onUndo = { },
    onDismiss = { }
)
```

---

## 数据迁移

### 提示词存储

```kotlin
// 保存提示词
recentPromptsStore.addPrompt("你好")

// 获取提示词列表
recentPromptsStore.recentPrompts.collect { prompts ->
    // prompts: List<String>
}
```

### 草稿存储

```kotlin
// 保存草稿
draftStore.saveDraft(sessionId, "未发送的消息")

// 获取草稿
draftStore.getDraft(sessionId).collect { draft ->
    // draft: DraftData?
}
```

---

## 配置迁移

### 主题偏好

```kotlin
// 设置主题色
themePreferencesStore.setThemeColor(0)  // Blue

// 设置字体大小
viewModel.setMessageFontSize(FontSize.LARGE)
```

---

## 测试迁移

### 新增测试工具

```kotlin
// 测试数据工厂
val session = TestDataFactory.createTestSession()
val messages = TestDataFactory.createTestMessages(10)

// 协程测试规则
@get:Rule
val coroutinesRule = TestCoroutinesRule()

// 内存分析
MemoryProfiler.takeSnapshot()
```

---

## 行为变更

### 消息缓存

- 每个会话最多缓存 **500 条消息**
- 超过 400 条时自动清理

### 草稿过期

- 草稿 **24 小时** 后自动过期

### 撤销超时

- 撤销操作 **5 秒** 后自动消失

---

## 弃用 API

无弃用 API。

---

## 破坏性变更

无破坏性变更。v1.2.0 完全向后兼容 v1.1.0。

---

## 常见问题

### Q: 升级后编译失败？

A: 清理并重新构建：
```bash
./gradlew clean
./gradlew assembleDebug
```

### Q: 二维码生成失败？

A: 确保 ZXing 依赖已添加：
```kotlin
implementation("com.google.zxing:core:3.5.2")
```

### Q: 语音输入无权限？

A: 在运行时请求权限：
```kotlin
requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 100)
```

---

## 支持

如有问题，请在 GitHub Issues 提交反馈。

---

**ClawChat Team**  
2026-04-01