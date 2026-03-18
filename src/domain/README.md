# Domain Layer - ClawChat Android

> 领域模型 · 业务逻辑 · 核心规则

## 概述

Domain 层是 ClawChat 应用的**核心业务逻辑层**，遵循 Clean Architecture 原则：

- **纯 Kotlin 实现**：无 Android 依赖，可在多平台复用
- **业务规则集中**：所有核心业务逻辑在此定义
- **依赖倒置**：上层（Presentation/Data）依赖此层接口

## 目录结构

```
domain/
├── model/              # 领域模型
│   ├── User.kt         # 用户信息
│   ├── Session.kt      # 会话实体
│   ├── Message.kt      # 消息实体
│   ├── Attachment.kt   # 附件实体
│   ├── GatewayConfig.kt # Gateway 配置
│   └── ...
├── repository/         # 仓库接口
│   ├── SessionRepository.kt
│   ├── ConnectionRepository.kt
│   └── SettingsRepository.kt
├── usecase/           # 业务用例
│   ├── SendMessage.kt
│   ├── CreateSession.kt
│   ├── DeleteSession.kt
│   ├── GetSessionHistory.kt
│   ├── PairDevice.kt
│   ├── ConnectGateway.kt
│   ├── ReceiveMessage.kt
│   └── UseCaseModule.kt
└── README.md
```

## 领域模型

### Session（会话）

```kotlin
// 创建新会话
val session = Session.create(
    model = "aliyun/qwen3.5-plus",
    label = "Product Research",
    thinking = true
)

// 会话状态
session.isActive()      // true
session.isTerminated()  // false

// 更新会话
val updated = session.withMessageAdded()
```

### Message（消息）

```kotlin
// 创建用户消息
val userMessage = Message.createUserMessage(
    sessionId = "session_123",
    content = "Hello, World!",
    attachments = listOf(imageAttachment)
)

// 创建助手消息
val assistantMessage = Message.createAssistantMessage(
    sessionId = "session_123",
    content = "Hello! How can I help you?",
    model = "aliyun/qwen3.5-plus"
)

// 消息检查
message.isUserMessage()      // true
message.hasAttachments()     // true
message.getPreview(50)       // "Hello, World!"
```

### GatewayConfig（Gateway 配置）

```kotlin
// 创建配置
val config = GatewayConfig(
    name = "Home Server",
    host = "192.168.1.100",
    port = 18789,
    useTls = false
)

// 生成 URL
config.toWebSocketUrl()  // "ws://192.168.1.100:18789"
config.toHttpUrl()       // "http://192.168.1.100:18789"

// 连接类型检测
config.isLocalConnection()       // true
config.isTailscaleConnection()   // false
```

## Repository 接口

Repository 接口定义了数据访问的契约，由 Data 层实现：

### SessionRepository

```kotlin
interface SessionRepository {
    // 观察会话列表
    fun observeSessions(): Flow<List<Session>>
    
    // 创建会话
    suspend fun createSession(
        model: String?,
        label: String?,
        thinking: Boolean
    ): Result<Session>
    
    // 发送消息
    suspend fun sendMessage(
        sessionId: String,
        content: String,
        attachments: List<Attachment>
    ): Result<Message>
    
    // 删除会话
    suspend fun deleteSession(sessionId: String): Result<Unit>
    
    // ... 更多方法
}
```

### ConnectionRepository

```kotlin
interface ConnectionRepository {
    // 观察连接状态
    fun observeConnectionStatus(): Flow<ConnectionStatus>
    
    // 连接 Gateway
    suspend fun connect(config: GatewayConfig): Result<Unit>
    
    // 设备配对
    suspend fun requestPairing(
        config: GatewayConfig,
        deviceName: String
    ): Result<DeviceToken>
    
    // ... 更多方法
}
```

## UseCase（业务用例）

UseCase 封装了具体的业务逻辑，每个 UseCase 负责单一功能：

### 使用示例

```kotlin
// 初始化 UseCase 模块
val useCases = UseCaseModule(
    sessionRepository = sessionRepository,
    connectionRepository = connectionRepository,
    settingsRepository = settingsRepository
)

// 创建会话
val sessionResult = useCases.createSession(
    model = "aliyun/qwen3.5-plus",
    label = "Code Review"
)

// 发送消息
val messageResult = useCases.sendMessage(
    sessionId = session.id,
    content = "请帮我 review 这段代码"
)

// 获取会话历史
val history = useCases.getSessionHistory(session.id)

// 连接 Gateway
val connectResult = useCases.connectGateway()

// 设备配对
val pairResult = useCases.pairDevice(
    config = gatewayConfig,
    deviceName = "My Phone"
)
```

### 错误处理

所有 UseCase 返回 `Result<T>`，调用方应处理成功和失败情况：

```kotlin
useCases.sendMessage(sessionId, content)
    .onSuccess { message ->
        // 发送成功
        updateUi(message)
    }
    .onFailure { error ->
        // 处理错误
        showError(error.message)
    }
```

## 设计原则

### 1. 单一职责

每个类只负责一项职责：

```kotlin
// ✅ 正确：SendMessage 只处理发送逻辑
class SendMessage(private val repository: SessionRepository) {
    suspend operator fun invoke(...): Result<Message>
}

// ❌ 错误：不要在 UseCase 中混合多个职责
class SessionManager { // 不要这样做
    suspend fun sendMessage() { }
    suspend fun createSession() { }
    suspend fun deleteSession() { }
    suspend fun connect() { }
}
```

### 2. 依赖倒置

上层依赖 Domain 层接口，而非具体实现：

```kotlin
// ✅ 正确：依赖接口
class MainViewModel(
    private val sendMessage: SendMessage,
    private val createSession: CreateSession
)

// ❌ 错误：依赖具体实现
class MainViewModel(
    private val repository: SqliteSessionRepository // 不要这样做
)
```

### 3. 纯 Kotlin

Domain 层不包含任何 Android 依赖：

```kotlin
// ✅ 正确：使用标准库
import kotlinx.coroutines.flow.Flow
import kotlin.Result

// ❌ 错误：避免 Android 依赖
import android.content.Context  // 不要这样做
import androidx.lifecycle.ViewModel  // 不要这样做
```

### 4. 不可变性

领域模型使用 `data class`，默认不可变：

```kotlin
// ✅ 正确：使用 copy 更新
val updated = session.copy(status = SessionStatus.TERMINATED)

// ❌ 错误：避免可变状态
var sessionStatus = RUNNING  // 不要这样做
sessionStatus = TERMINATED
```

## 测试

### 领域模型测试

```kotlin
class DomainModelTest {
    @Test
    fun `session creation with default values`() {
        val session = Session.create()
        
        assertNotNull(session.id)
        assertEquals(SessionStatus.RUNNING, session.status)
    }
    
    @Test
    fun `message role parsing`() {
        assertEquals(MessageRole.USER, MessageRole.fromString("user"))
        assertEquals(MessageRole.ASSISTANT, MessageRole.fromString("assistant"))
    }
}
```

### UseCase 测试

```kotlin
class UseCaseTest {
    private class MockSessionRepository : SessionRepository {
        // Mock 实现
    }
    
    @Test
    fun `SendMessage validates empty content`() = runTest {
        val repository = MockSessionRepository()
        val useCase = SendMessage(repository)
        
        val result = useCase.invoke("session_123", "")
        
        assertTrue(result.isFailure)
    }
}
```

## 扩展指南

### 添加新的领域模型

1. 在 `model/` 目录创建新的 `data class`
2. 添加必要的业务方法
3. 编写单元测试

```kotlin
// model/Notification.kt
data class Notification(
    val id: String,
    val title: String,
    val content: String,
    val timestamp: Long,
    val isRead: Boolean = false
) {
    fun markAsRead(): Notification = copy(isRead = true)
}
```

### 添加新的 UseCase

1. 在 `usecase/` 目录创建新类
2. 通过构造函数注入依赖的 Repository
3. 实现 `invoke` 方法
4. 添加输入验证和错误处理
5. 编写单元测试

```kotlin
// usecase/MarkNotificationRead.kt
class MarkNotificationRead(
    private val notificationRepository: NotificationRepository
) {
    suspend operator fun invoke(notificationId: String): Result<Unit> {
        require(notificationId.isNotBlank()) { "Notification ID cannot be empty" }
        
        return notificationRepository.markAsRead(notificationId)
    }
}
```

### 添加新的 Repository 接口

1. 在 `repository/` 目录创建新接口
2. 定义数据访问方法
3. 在 Data 层实现接口

```kotlin
// repository/NotificationRepository.kt
interface NotificationRepository {
    fun observeNotifications(): Flow<List<Notification>>
    suspend fun markAsRead(notificationId: String): Result<Unit>
    suspend fun deleteNotification(notificationId: String): Result<Unit>
}
```

## 参考

- [Clean Architecture](https://blog.cleancoder.com/uncle-bob/2012/08/13/the-clean-architecture.html)
- [Kotlin Coroutines](https://kotlinlang.org/docs/coroutines-overview.html)
- [Kotlin Flow](https://kotlinlang.org/docs/flow.html)
- [Result 类型](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-result/)
