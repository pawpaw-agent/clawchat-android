# ClawChat 单元测试

本目录包含 ClawChat 项目的完整单元测试套件。

## 测试框架

- **JUnit 5** - 测试框架
- **MockK** - Kotlin mocking 库
- **Kotlinx Coroutines Test** - 协程测试支持
- **AndroidX Test** - Android 测试支持

## 测试目录结构

```
tests/
├── build.gradle.kts                      # Gradle 构建配置
├── src/test/kotlin/
│   └── com/openclaw/clawchat/
│       ├── TestUtils.kt                  # 测试工具类
│       ├── security/
│       │   └── KeystoreManagerTest.kt    # 密钥管理测试
│       ├── network/
│       │   └── WebSocketServiceTest.kt   # WebSocket 服务测试
│       └── ui/
│           ├── MainViewModelTest.kt      # 主界面 ViewModel 测试
│           └── SessionViewModelTest.kt   # 会话 ViewModel 测试
```

## 运行测试

### 运行所有测试

```bash
cd ~/.openclaw/workspace-ClawChat/tests
./gradlew test
```

### 运行特定测试类

```bash
# KeystoreManager 测试
./gradlew test --tests "com.openclaw.clawchat.security.KeystoreManagerTest"

# WebSocketService 测试
./gradlew test --tests "com.openclaw.clawchat.network.WebSocketServiceTest"

# MainViewModel 测试
./gradlew test --tests "com.openclaw.clawchat.ui.MainViewModelTest"

# SessionViewModel 测试
./gradlew test --tests "com.openclaw.clawchat.ui.SessionViewModelTest"
```

### 运行特定测试方法

```bash
./gradlew test --tests "com.openclaw.clawchat.security.KeystoreManagerTest.hasKeyPair*"
```

### 生成测试报告

```bash
./gradlew test jacocoTestReport
```

报告位置：`build/reports/tests/test/index.html`

## 测试覆盖

### KeystoreManagerTest

测试 Android Keystore 密钥管理功能：

- ✅ `hasKeyPair()` - 检查密钥对是否存在
- ✅ `generateKeyPair()` - 生成 ECDSA 密钥对
- ✅ `getPublicKeyPem()` - 获取 PEM 格式公钥
- ✅ `signChallenge()` - 挑战 - 响应签名（ByteArray 和 String）
- ✅ `deleteKey()` - 删除密钥
- ✅ `getKeyInfo()` - 获取密钥信息
- ✅ `KeyInfo` 数据类测试

### WebSocketServiceTest

测试 WebSocket 连接服务：

- ✅ `connect()` - 建立连接（成功/失败场景）
- ✅ `send()` - 发送消息
- ✅ `disconnect()` - 断开连接
- ✅ `measureLatency()` - 测量延迟
- ✅ `incomingMessages` 流测试
- ✅ `WebSocketConnectionState` 状态转换测试

### MainViewModelTest

测试主界面 ViewModel：

- ✅ 初始状态验证
- ✅ `connectToGateway()` - 连接网关
- ✅ `disconnect()` - 断开连接
- ✅ `selectSession()` - 选择会话
- ✅ `createSession()` - 创建会话
- ✅ `deleteSession()` - 删除会话
- ✅ `loadSessions()` - 加载会话列表
- ✅ `clearError()` / `consumeEvent()` - 状态清理
- ✅ `UiEvent` 密封类测试
- ✅ `ConnectionStatus` 密封类测试

### SessionViewModelTest

测试会话界面 ViewModel：

- ✅ 初始状态验证
- ✅ `loadMessages()` - 加载消息历史
- ✅ `sendMessage()` - 发送消息
- ✅ `updateInputText()` - 更新输入
- ✅ `regenerateLastMessage()` - 重新生成消息
- ✅ `scrollToBottom()` - 滚动控制
- ✅ `clearError()` / `consumeEvent()` - 状态清理
- ✅ `SessionUiEvent` 密封类测试
- ✅ `MessageUi` / `SessionUi` 数据类测试
- ✅ `MessageRole` / `SessionStatus` 枚举测试

## 测试最佳实践

### 1. 测试命名

使用描述性的测试方法名，遵循 `功能_场景_预期结果` 格式：

```kotlin
@Test
fun `successfully connects to gateway`()

@Test
fun `returns error state on connection failure`()

@Test
fun `does nothing when sending empty message`()
```

### 2. 使用 MockK

```kotlin
// 创建 mock
val mockService = mockk<WebSocketService>()

// 定义行为
every { mockService.connect(any(), any()) } returns Result.success(Unit)

// 验证调用
verify { mockService.send(message) }
verify(exactly = 0) { mockService.disconnect() }
```

### 3. 协程测试

```kotlin
@Test
fun `test suspending function`() = runTest {
    val viewModel = MainViewModel()
    viewModel.connectToGateway("ws://localhost")
    advanceUntilIdle()
    
    assertEquals(ConnectionStatus.Connected, viewModel.uiState.value.connectionStatus)
}
```

### 4. 状态流测试

```kotlin
@Test
fun `state changes correctly`() = runTest {
    val states = mutableListOf<State>()
    val job = launch {
        viewModel.state.collect { states.add(it) }
    }
    
    viewModel.doSomething()
    advanceUntilIdle()
    job.cancel()
    
    assertTrue(states.contains(ExpectedState))
}
```

### 5. 嵌套测试类

使用 `@Nested` 组织相关测试：

```kotlin
@Nested
@DisplayName("connect() 测试")
inner class ConnectTests {
    @Test
    fun `successful connection`() { }
    
    @Test
    fun `failed connection`() { }
}
```

## 添加新测试

1. 在对应模块目录下创建测试文件
2. 使用 `@DisplayName` 描述测试类
3. 使用 `@Nested` 组织测试场景
4. 每个测试方法使用 `@Test` 和 `@DisplayName`
5. 使用 MockK 模拟依赖
6. 使用 `runTest` 测试协程代码

## 依赖版本

| 依赖 | 版本 |
|------|------|
| JUnit 5 | 5.10.0 |
| MockK | 1.13.7 |
| Kotlinx Coroutines | 1.7.3 |
| AndroidX Test | 1.5.0 |
| Robolectric | 4.11.1 |

## 故障排除

### 测试失败：Android 依赖不可用

确保在 `build.gradle.kts` 中配置了：

```kotlin
testOptions {
    unitTests {
        isIncludeAndroidResources = true
        isReturnDefaultValues = true
    }
}
```

### MockK 无法 mock Android 类

使用 `mockkStatic()` 模拟静态方法：

```kotlin
mockkStatic(KeyStore::class)
every { KeyStore.getInstance("AndroidKeyStore") } returns mockKeyStore
```

### 协程测试超时

增加超时时间：

```kotlin
@Test
fun `long running test`() = runTest(timeout = 30000.milliseconds) {
    // ...
}
```

## 参考资料

- [JUnit 5 文档](https://junit.org/junit5/docs/current/user-guide/)
- [MockK 文档](https://mockk.io/)
- [Kotlinx Coroutines 测试](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-test/)
- [Android 单元测试指南](https://developer.android.com/training/testing/local-tests)
