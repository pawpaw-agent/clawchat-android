# Testing Guide for ClawChat

## 测试指南

本文档介绍如何在 ClawChat 中编写和运行测试。

---

## 测试结构

```
app/src/
├── test/                    # 单元测试（JVM）
│   └── java/
│       └── com/openclaw/clawchat/
│           └── util/        # 工具类测试
│               ├── DateTimeUtilsTest.kt
│               ├── StringUtilsTest.kt
│               ├── CollectionUtilsTest.kt
│               ├── ValidationUtilsTest.kt
│               └── StringExtTest.kt
│
└── androidTest/             # 仪器测试（Android）
    └── java/
        └── com/openclaw/clawchat/
```

---

## 运行测试

### 单元测试

```bash
./gradlew testDebugUnitTest
```

### 仪器测试

```bash
./gradlew connectedAndroidTest
```

### 测试覆盖率

```bash
./gradlew testDebugUnitTestCoverage
```

---

## 测试工具

### TestDataFactory

提供一致的测试数据生成：

```kotlin
// 创建测试会话
val session = TestDataFactory.createTestSession()

// 创建多个测试会话
val sessions = TestDataFactory.createTestSessions(10)

// 创建测试消息
val message = TestDataFactory.createTestMessage(
    content = "Hello",
    role = MessageRole.USER
)
```

### TestCoroutinesRule

提供协程测试支持：

```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
class MyViewModelTest {
    @get:Rule
    val coroutinesRule = TestCoroutinesRule()
    
    @Test
    fun testSomething() = coroutinesRule.runTest {
        // 协程测试代码
    }
}
```

---

## 测试命名规范

### 描述性命名

```kotlin
// ✅ 好的命名
@Test
fun `isValidEmail returns true for valid email`() { }

@Test
fun `formatRelativeTime returns 刚刚 for less than 1 minute`() { }

// ❌ 不好的命名
@Test
fun testEmail() { }

@Test
fun test1() { }
```

### AAA 模式

```kotlin
@Test
fun `truncate adds ellipsis when string is too long`() {
    // Arrange
    val input = "hello world"
    val maxLength = 8
    
    // Act
    val result = StringUtils.truncate(input, maxLength)
    
    // Assert
    assertEquals("hello...", result)
}
```

---

## 测试分类

### 单元测试

测试单个函数或类：

```kotlin
@Test
fun `countCharacters counts Chinese characters correctly`() {
    val result = "你好世界".countCharacters()
    assertEquals(4, result)
}
```

### 边界测试

测试边界条件：

```kotlin
@Test
fun `isValidPort returns false for port 0`() {
    assertFalse(ValidationUtils.isValidPort(0))
}

@Test
fun `isValidPort returns false for port over 65535`() {
    assertFalse(ValidationUtils.isValidPort(70000))
}
```

### 错误测试

测试错误处理：

```kotlin
@Test
fun `getOrNull returns default for null list`() {
    val result = CollectionUtils.getOrNull(null, 0, "default")
    assertEquals("default", result)
}
```

---

## 最佳实践

### Do ✅

- 使用 `@get:Rule` 注入测试规则
- 使用描述性测试名称
- 遵循 AAA 模式（Arrange-Act-Assert）
- 测试边界条件
- 测试错误情况
- 使用 TestDataFactory 生成测试数据

### Don't ❌

- 不要在测试中使用 `Thread.sleep()`
- 不要依赖测试执行顺序
- 不要使用硬编码时间戳
- 不要忽略失败的测试
- 不要在测试中进行 I/O 操作

---

## 测试覆盖率目标

| 模块 | 目标覆盖率 |
|-----|-----------|
| 工具类 | 90%+ |
| ViewModel | 80%+ |
| Repository | 80%+ |
| UI | 60%+ |

---

## 测试报告

运行测试后，查看报告：

```bash
# 单元测试报告
open app/build/reports/tests/testDebugUnitTest/index.html

# 覆盖率报告
open app/build/reports/jacoco/jacocoTestDebugUnitTestReport/html/index.html
```

---

**版本**: v1.2.0  
**更新日期**: 2026-04-01