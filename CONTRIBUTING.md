# 贡献指南

感谢你考虑为 ClawChat Android 做出贡献！

## 开发环境

### 要求
- Android Studio Hedgehog 或更高版本
- JDK 17+
- Android SDK 34
- Gradle 8.0+

### 设置
```bash
git clone https://github.com/pawpaw-agent/clawchat-android.git
cd clawchat-android
./gradlew build
```

## 代码规范

### Kotlin 风格
- 遵循 [Kotlin 编码规范](https://kotlinlang.org/docs/coding-conventions.html)
- 使用 4 空格缩进
- 最大行宽 120 字符

### 命名约定
- 类：PascalCase (例：`SessionViewModel`)
- 函数/变量：camelCase (例：`sendMessage`)
- 常量：UPPER_SNAKE_CASE (例：`MAX_IMAGE_SIZE`)

### Compose 最佳实践
- 使用 `@Stable` 注解数据类
- 使用 `derivedStateOf` 避免不必要的重组
- 使用 `key` 和 `contentType` 优化 LazyColumn

## 提交规范

### Commit 消息格式
```
<type>: <subject>

<body>
```

### Type 类型
- `feat`: 新功能
- `fix`: Bug 修复
- `refactor`: 重构
- `docs`: 文档更新
- `test`: 测试相关
- `chore`: 构建/工具

### 示例
```
feat: add message feedback (like/dislike)

Add feedback mechanism for assistant messages:
- Add MessageFeedback enum
- Add like/dislike buttons
- Store feedback locally (pending backend API)
```

## 架构

### MVVM 架构
- **Model**: Repository + Room Database
- **ViewModel**: androidx.lifecycle.ViewModel
- **View**: Jetpack Compose

### 模块结构
```
app/
├── data/           # 数据层
├── network/        # 网络层
├── ui/             # UI 层
│   ├── components/ # 可复用组件
│   ├── screens/    # 页面
│   └── state/      # ViewModel + UiState
└── util/           # 工具类
```

## 测试

### 运行测试
```bash
./gradlew test           # 单元测试
./gradlew connectedTest  # UI 测试
```

### 测试覆盖
- ViewModel 测试
- Repository 测试
- 网络协议测试

## 发布流程

1. 更新 `CHANGELOG.md`
2. 更新版本号
3. 创建 Git tag
4. GitHub Actions 自动构建

## 问题反馈

- 使用 GitHub Issues
- 提供复现步骤
- 附上日志/截图

## 许可证

MIT License