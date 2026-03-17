# ClawChat UI Framework

> Jetpack Compose UI 框架基础 · 主题系统 · 基础组件 · 状态管理

## 目录结构

```
ui/
├── theme/              # 主题系统
│   ├── Color.kt        # 颜色定义
│   ├── Type.kt         # 字体排印
│   ├── Theme.kt        # 主题配置
│   └── Theme.kt.md     # 主题使用文档
│
├── components/         # 基础组件
│   ├── ClawTopAppBar.kt    # 顶部导航栏
│   ├── ClawNavigation.kt   # 底部/侧边导航
│   ├── MessageList.kt      # 消息列表
│   └── Components.kt       # 模块导出
│
├── state/              # 状态管理
│   ├── UiState.kt          # UI 状态数据模型
│   ├── MainViewModel.kt    # 主界面 ViewModel
│   ├── SessionViewModel.kt # 会话界面 ViewModel
│   └── StateManagement.kt  # 模块导出
│
└── README.md           # 本文档
```

## 快速开始

### 1. 应用主题

在 `MainActivity.kt` 中包裹内容：

```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ClawChatTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen()
                }
            }
        }
    }
}
```

### 2. 使用基础组件

```kotlin
@Composable
fun MainScreen() {
    val viewModel: MainViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsState()
    
    Scaffold(
        topBar = {
            ClawTopAppBar(
                title = "ClawChat",
                subtitle = when (uiState.connectionStatus) {
                    is Connected -> "已连接"
                    is Connecting -> "连接中..."
                    else -> "未连接"
                },
                navigationIcon = { /* 菜单图标 */ },
                actions = { /* 操作按钮 */ }
            )
        },
        bottomBar = {
            ClawBottomNavigationBar(
                items = listOf(
                    BottomNavItem("sessions", "会话", Icons.Default.Chat),
                    BottomNavItem("settings", "设置", Icons.Default.Settings)
                ),
                currentRoute = currentRoute,
                onNavigate = { navigate(it) }
            )
        }
    ) { padding ->
        // 内容区域
    }
}
```

### 3. 消息列表

```kotlin
@Composable
fun ChatScreen(sessionId: String) {
    val viewModel: SessionViewModel = hiltViewModel(
        creationExtras = viewModelCreationExtrasOf(sessionId)
    )
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    
    Column {
        MessageList(
            messages = uiState.messages,
            listState = listState,
            modifier = Modifier.weight(1f)
        )
        
        MessageInput(
            text = uiState.inputText,
            onTextChange = viewModel::updateInputText,
            onSend = { viewModel.sendMessage(it) },
            isSending = uiState.isSending
        )
    }
}
```

## 状态管理架构

采用 **MVVM + StateFlow** 单向数据流：

```
UI (Compose) ← StateFlow ← ViewModel → UseCase → Repository
     ↑                                              ↓
     └─────────────────── Event Flow ──────────────┘
```

### ViewModel 职责

| ViewModel | 管理范围 | 关键状态 |
|-----------|----------|----------|
| `MainViewModel` | 全局连接、会话列表 | `connectionStatus`, `sessions`, `currentSession` |
| `SessionViewModel` | 单个会话详情 | `messages`, `inputText`, `isSending` |

### 状态更新模式

```kotlin
// 不可变状态更新
_uiState.update { currentState ->
    currentState.copy(
        isLoading = true,
        error = null
    )
}

// 事件触发（一次性）
_events.value = UiEvent.NavigateToSession(sessionId)
// 消费后重置
fun consumeEvent() {
    _events.value = null
}
```

## 依赖项

在 `build.gradle.kts` 中添加：

```kotlin
dependencies {
    // Compose BOM
    implementation(platform("androidx.compose:compose-bom:2024.03.00"))
    
    // Material3
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material3:material3-window-size-class")
    
    // Lifecycle + ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    
    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.7")
    
    // Hilt (依赖注入)
    implementation("com.google.dagger:hilt-android:2.50")
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")
    kapt("com.google.dagger:hilt-compiler:2.50")
}
```

## 待实现功能

- [ ] `MessageInput` 输入组件（支持多行、附件）
- [ ] `SessionList` 会话列表组件
- [ ] `GatewayConfigScreen` 网关配置界面
- [ ] `PairingDialog` 设备配对对话框
- [ ] 下拉刷新、加载动画
- [ ] 图片/文件预览组件
- [ ] 代码块高亮显示

## 与架构文档对齐

本 UI 框架严格遵循 `project-docs/architecture.md` 定义：

- ✅ Clean Architecture + MVVM 分层
- ✅ StateFlow 单向数据流
- ✅ Repository/UseCase 接口规范（待实现）
- ✅ 深色主题优先
- ✅ WebSocket 消息流设计（待集成）

---

*最后更新：2026-03-17*
