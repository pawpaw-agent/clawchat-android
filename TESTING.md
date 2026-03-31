# ClawChat Testing Guide

## 测试指南

ClawChat 采用多层次测试策略，确保代码质量和稳定性。

---

## 测试结构

```
app/src/
├── test/           # 单元测试
│   └── java/
│       └── com/openclaw/clawchat/
├── androidTest/    # UI 测试
│   └── java/
│       └── com/openclaw/clawchat/
└── ...
```

---

## 单元测试

### 运行单元测试

```bash
./gradlew test
```

### 测试覆盖率

```bash
./gradlew testDebugUnitTestCoverage
```

### 测试原则

1. **单一职责** - 每个测试只验证一个功能
2. **独立性** - 测试之间不依赖
3. **可重复** - 多次运行结果一致
4. **快速** - 单元测试不依赖外部资源

### 测试示例

```kotlin
@Test
fun `formatRelativeTime returns correct format`() {
    val now = System.currentTimeMillis()
    val oneMinuteAgo = now - 60_000
    
    val result = formatRelativeTime(oneMinuteAgo, now)
    
    assertEquals("1 分钟前", result)
}
```

---

## UI 测试

### 运行 UI 测试

```bash
./gradlew connectedAndroidTest
```

### Compose 测试

```kotlin
@Test
fun displaysSessionList() {
    composeTestRule.setContent {
        SessionListContent(
            state = MainUiState(sessions = testSessions),
            onSelectSession = {},
            onSessionLongPress = {},
            onCreateSession = {},
            onRefresh = {}
        )
    }
    
    composeTestRule.onNodeWithText("Test Session").assertIsDisplayed()
}
```

### 测试选择器

- `onNodeWithText()` - 按文本查找
- `onNodeWithTag()` - 按标签查找
- `onNodeWithContentDescription()` - 按内容描述查找
- `onNodeWithText().performClick()` - 执行点击

---

## 压力测试

### 快速点击测试

连续快速点击按钮，验证不会触发多次操作。

```kotlin
@Test
fun rapidButtonPresses() {
    repeat(100) {
        composeTestRule.onNodeWithText("发送").performClick()
    }
    // 验证只发送了一次
}
```

### 快速滑动测试

快速滑动列表，验证不会崩溃或内存泄漏。

### 网络切换测试

模拟网络切换，验证连接状态正确处理。

### 内存压力测试

模拟低内存场景，验证资源正确释放。

---

## 性能测试

### Benchmark 配置

```kotlin
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {
    @get:Rule
    val benchmarkRule = BenchmarkRule()
    
    @Test
    fun startup() = benchmarkRule.measureRepeated(
        packageName = "com.openclaw.clawchat",
        metrics = listOf(StartupTimingMetric()),
        iterations = 10,
        startupMode = StartupMode.COLD
    ) {
        pressHome()
        startActivityAndWait()
    }
}
```

### 运行 Benchmark

```bash
./gradlew :benchmark:benchmarkRelease
```

---

## Mock 数据

### 依赖注入

使用 Hilt 进行依赖注入，测试时可以替换为 Mock 实现。

```kotlin
@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [NetworkModule::class]
)
object TestNetworkModule {
    @Provides
    @Singleton
    fun provideMockOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder().build()
    }
}
```

### 测试数据工厂

```kotlin
object TestDataFactory {
    fun createTestSession(id: String = "test-id"): SessionUi {
        return SessionUi(
            id = id,
            title = "Test Session",
            timestamp = System.currentTimeMillis()
        )
    }
}
```

---

## 测试覆盖目标

| 模块 | 目标覆盖率 |
|-----|-----------|
| ViewModel | 80%+ |
| Repository | 80%+ |
| Util | 90%+ |
| UI | 60%+ |

---

## CI 集成

### GitHub Actions

```yaml
- name: Run Unit Tests
  run: ./gradlew test
  
- name: Run UI Tests
  run: ./gradlew connectedAndroidTest
  
- name: Upload Coverage
  uses: codecov/codecov-action@v3
```

---

## 测试最佳实践

### Do ✅

- 使用 `@Before` 初始化测试状态
- 使用 `@After` 清理测试资源
- 使用有意义的测试名称
- 验证边界条件
- 验证错误处理

### Don't ❌

- 不要在测试中使用 `Thread.sleep()`
- 不要依赖测试执行顺序
- 不要在单元测试中进行 I/O 操作
- 不要忽略失败的测试

---

## 调试技巧

### 查看测试日志

```bash
./gradlew test --info
```

### 运行特定测试

```bash
./gradlew test --tests "com.openclaw.clawchat.util.*"
```

### 调试测试

在 Android Studio 中右键测试 → Debug

---

**版本**: v1.2.0  
**更新日期**: 2026-04-01