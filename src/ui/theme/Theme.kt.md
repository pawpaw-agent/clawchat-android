# ClawChat Theme System

## 文件结构

```
theme/
├── Color.kt      # 颜色定义（主色调、背景色、文本色、状态色）
├── Type.kt       # 字体排印（标题、正文、标签样式）
└── Theme.kt      # 主题配置（深色/浅色、Material3 配色）
```

## 设计理念

- **深色优先**: 默认使用深色主题，符合开发者工具定位
- **品牌一致性**: 使用 OpenClaw 蓝色系作为主色调
- **可访问性**: 确保足够的对比度，支持系统字体大小
- **动态取色**: 支持 Android 12+ 动态取色（默认禁用）

## 颜色使用指南

| 用途 | 颜色变量 | 说明 |
|------|----------|------|
| 主操作 | `ClawBlue` | 按钮、链接、选中状态 |
| 背景 | `BackgroundPrimary` | 主背景色 |
| 卡片背景 | `SurfacePrimary` | 卡片、对话框背景 |
| 主要文本 | `TextPrimary` | 标题、正文 |
| 次要文本 | `TextSecondary` | 副标题、提示 |
| 成功 | `Success` | 连接成功、操作成功 |
| 错误 | `Error` | 连接失败、操作失败 |
| 警告 | `Warning` | 注意提示 |

## 使用示例

```kotlin
@Composable
fun Example() {
    ClawChatTheme {
        // 使用 Material3 颜色
        Box(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp)
        ) {
            Text(
                text = "Hello, ClawChat!",
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}
```
