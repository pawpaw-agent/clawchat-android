# ClawChat Android 架构说明

## 概述

ClawChat Android 是一个使用 Jetpack Compose 构建的 AI 聊天客户端，采用 MVVM 架构。

## 技术栈

### 核心框架
- **Jetpack Compose** - 现代 Android UI 工具包
- **Hilt** - 依赖注入
- **Kotlin Coroutines** - 异步编程
- **StateFlow** - 响应式状态管理

### 网络层
- **OkHttp** - WebSocket 连接
- **Kotlinx Serialization** - JSON 序列化

### 数据层
- **Room** - 本地数据库
- **DataStore** - 偏好设置存储

## 架构图

```
┌─────────────────────────────────────────────────┐
│                    UI Layer                      │
│  ┌─────────────┐  ┌──────────────┐  ┌────────┐ │
│  │ Compose UI  │  │ ViewModels   │  │UiState │ │
│  └──────┬──────┘  └──────┬───────┘  └───┬────┘ │
└─────────┼────────────────┼──────────────┼──────┘
          │                │              │
┌─────────┼────────────────┼──────────────┼──────┐
│         │     Domain Layer                │      │
│  ┌──────▼──────┐  ┌──────▼───────┐        │      │
│  │ Repository  │  │ Use Cases    │        │      │
│  └──────┬──────┘  └──────────────┘        │      │
└─────────┼─────────────────────────────────┘
          │
┌─────────▼─────────────────────────────────┐
│              Data Layer                     │
│  ┌─────────────┐  ┌──────────────────┐    │
│  │ Room DB     │  │ GatewayConnection│    │
│  └─────────────┘  └──────────────────┘    │
└────────────────────────────────────────────┘
```

## 核心组件

### 1. GatewayConnection
WebSocket 连接管理器，负责：
- 建立和维护 WebSocket 连接
- 发送/接收消息
- 处理心跳和重连
- API 调用（chat.send, sessions.steer, cron.*）

```kotlin
// API 调用示例
gateway.chatSend(sessionId, message)
gateway.sessionsSteer(sessionKey, text)
gateway.cronList()
```

### 2. SessionViewModel
会话页面 ViewModel，职责：
- 管理会话状态（SessionUiState）
- 处理用户操作（发送消息、删除等）
- 协调 ChatEventHandler 和 ToolStreamManager

```kotlin
// 状态管理
_state.update { it.copy(isLoading = true) }
```

### 3. ChatEventHandler
聊天事件处理器：
- 解析 WebSocket 帧
- 处理 delta/final/aborted/error 状态
- 更新消息状态

### 4. ToolStreamManager
工具流管理器：
- 管理 toolStreamById/Order 状态
- 构建 chatToolMessages
- 处理工具流事件

## 数据流

### 发送消息
```
User Input → SessionViewModel.sendMessage()
           → GatewayConnection.chatSend()
           → WebSocket
           → Gateway
```

### 接收消息
```
WebSocket → GatewayConnection.incomingMessages
          → ChatEventHandler.handleIncomingFrame()
          → SessionViewModel._state.update()
          → Compose UI
```

## 状态管理

### UiState 类
```kotlin
@Stable
data class SessionUiState(
    val sessionId: String? = null,
    val chatMessages: List<MessageUi> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)
```

### StateFlow 更新
```kotlin
// ❌ 错误：直接赋值（竞态条件）
_state.value = _state.value.copy(isLoading = true)

// ✅ 正确：使用 update {} 原子操作
_state.update { it.copy(isLoading = true) }
```

## 性能优化

### 1. Compose 重组优化
- 使用 `@Stable` 注解数据类
- 使用 `derivedStateOf` 避免不必要重组
- LazyColumn 使用 `key` 和 `contentType`

### 2. 内存优化
- 图片采样加载（MAX_IMAGE_SIZE = 1024px）
- 消息数量限制（MAX_MESSAGES_PER_SESSION = 500）

### 3. 网络优化
- StateFlow 原子更新
- 请求去重（RequestTracker）

## 测试策略

### 单元测试
- ViewModel 测试：MainViewModelTest, SessionViewModelTest
- Repository 测试：MessageRepositoryTest
- 协议测试：GatewayConnectionTest

### 测试覆盖
- 3509 行测试代码
- 覆盖核心业务逻辑

## 扩展指南

### 添加新 API
1. 在 GatewayConnection 添加方法
2. 在 ViewModel 添加对应操作
3. 在 UI 添加入口
4. 添加单元测试

### 添加新页面
1. 创建 Screen.kt 文件
2. 创建对应 ViewModel
3. 添加导航路由
4. 更新 UiState

## 参考

- [Jetpack Compose 文档](https://developer.android.com/jetpack/compose)
- [Kotlin Coroutines 指南](https://kotlinlang.org/docs/coroutines-guide.html)
- [Hilt 依赖注入](https://dagger.dev/hilt/)