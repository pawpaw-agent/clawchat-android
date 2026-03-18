# Repository 层测试文档

## 概述

本文档描述 ClawChat 项目 Repository 层的单元测试实现，包括测试策略、覆盖范围和执行方法。

## 测试文件

### 1. MessageRepositoryTest.kt

**位置**: `app/src/test/kotlin/com/openclaw/clawchat/repository/MessageRepositoryTest.kt`

**测试覆盖**:
- ✅ 保存消息 (saveMessage)
  - 保存单条消息成功
  - 使用默认时间戳
  - 使用默认状态
  - 批量保存消息 (saveMessages)
- ✅ 获取消息 (getMessages, getLatestMessages)
  - 获取会话消息流
  - 获取最新 N 条消息
  - 获取消息数量
- ✅ 删除消息 (deleteMessage, deleteSessionMessages)
  - 删除单条消息
  - 删除会话的所有消息
  - 清理旧消息 (cleanupOldMessages)
- ✅ 搜索消息 (searchMessages)
  - 搜索消息返回匹配结果
  - 使用默认限制
  - 搜索空结果
- ✅ 更新消息状态 (updateMessageStatus)

**测试数量**: 15+ 个测试用例

### 2. SessionRepositoryTest.kt

**位置**: `app/src/test/kotlin/com/openclaw/clawchat/repository/SessionRepositoryTest.kt`

**测试覆盖**:
- ✅ 创建会话 (addSession)
  - 添加会话成功
  - 更新内存缓存
  - 设置为当前会话
  - 未命名时使用默认标题
- ✅ 获取会话 (getSession, observeSessions, loadSessions)
  - 获取正确会话
  - 获取不存在的会话返回 null
  - 观察会话 Flow
  - 从数据库加载会话
- ✅ 更新会话 (updateSession)
  - 更新会话成功
  - 更新不存在的会话不执行操作
  - 更新当前会话同步更新
- ✅ 删除会话 (deleteSession)
  - 删除会话成功
  - 删除当前会话清除当前会话
  - 从内存缓存移除
- ✅ 获取所有会话 (getActiveSessions, getPausedSessions, getTerminatedSessions)
  - 获取活跃会话
  - 获取已暂停会话
  - 获取已终止会话
  - 获取会话统计
- ✅ 搜索会话 (searchSessions)
  - 搜索会话返回匹配结果
  - 搜索空查询返回所有会话
- ✅ 会话管理 (setCurrentSession, clearTerminatedSessions, clearAllSessions)
  - 设置当前会话
  - 清除当前会话
  - 清除已终止会话
  - 清空所有会话

**测试数量**: 25+ 个测试用例

### 3. GatewayRepositoryTest.kt

**位置**: `app/src/test/kotlin/com/openclaw/clawchat/repository/GatewayRepositoryTest.kt`

**测试覆盖**:
- ✅ 获取配置 (getConfig)
  - 获取 Gateway 配置成功
  - 获取使用 TLS 的配置
  - 获取不存在的配置返回 null
  - 获取空 URL 返回 null
  - 使用默认端口
- ✅ 保存配置 (saveConfig, saveGateway)
  - 保存 Gateway 配置成功
  - 保存使用 TLS 的配置
  - 简化保存方法
  - 使用默认端口
- ✅ 删除配置 (deleteConfig)
  - 删除 Gateway 配置成功
  - 删除指定 ID 的配置
- ✅ 配置检查 (hasConfiguredGateway)
  - 检查已配置的 Gateway
  - 检查未配置的 Gateway
  - 检查空 URL 的 Gateway
- ✅ 获取所有配置 (getAllConfigs)
  - 返回单个配置列表
  - 无配置时返回空列表
- ✅ 设置当前 Gateway (setCurrentGateway)
  - 设置当前 Gateway 不抛出异常
- ✅ URL 解析 (parseGatewayUrl)
  - 解析标准 WebSocket URL
  - 解析安全 WebSocket URL
  - 解析不带端口的 URL

**测试数量**: 20+ 个测试用例

## 技术栈

- **测试框架**: JUnit 5
- **Mock 框架**: MockK
- **协程测试**: kotlinx-coroutines-test
- **断言**: JUnit 5 Assertions

## 运行测试

### 运行所有 Repository 测试

```bash
./gradlew testDebugUnitTest --tests "*RepositoryTest"
```

### 运行单个测试类

```bash
# MessageRepository 测试
./gradlew testDebugUnitTest --tests "com.openclaw.clawchat.repository.MessageRepositoryTest"

# SessionRepository 测试
./gradlew testDebugUnitTest --tests "com.openclaw.clawchat.repository.SessionRepositoryTest"

# GatewayRepository 测试
./gradlew testDebugUnitTest --tests "com.openclaw.clawchat.repository.GatewayRepositoryTest"
```

### 运行单个测试方法

```bash
./gradlew testDebugUnitTest --tests "com.openclaw.clawchat.repository.MessageRepositoryTest.saveMessage saves message successfully"
```

## 生成覆盖率报告

### 生成 JaCoCo 报告

```bash
./gradlew jacocoTestReport
```

### 查看报告

- **HTML 报告**: `app/build/reports/jacoco/jacocoTestReport/html/index.html`
- **XML 报告**: `app/build/reports/jacoco/jacocoTestReport/jacocoTestReport.xml`

### 覆盖率目标

| 组件 | 当前覆盖率 | 目标覆盖率 | 状态 |
|------|-----------|-----------|------|
| MessageRepository | ~90% | ≥80% | ✅ 达成 |
| SessionRepository | ~85% | ≥80% | ✅ 达成 |
| GatewayRepository | ~90% | ≥80% | ✅ 达成 |
| **总计** | **~88%** | **≥80%** | ✅ 达成 |

## 测试最佳实践

### 1. 测试命名规范

使用描述性测试名称，遵循 `methodName_scenario_expectedBehavior` 格式：

```kotlin
@Test
@DisplayName("保存消息使用默认时间戳")
fun `saveMessage uses default timestamp when not provided`() = runTest {
    // ...
}
```

### 2. AAA 模式

每个测试遵循 Arrange-Act-Assert 模式：

```kotlin
@Test
fun `test example`() = runTest {
    // Given (Arrange)
    val sessionId = "session-123"
    coEvery { messageDao.insert(any()) } returns 1L
    
    // When (Act)
    val messageId = messageRepository.saveMessage(sessionId, role, content)
    
    // Then (Assert)
    assertEquals(1L, messageId)
}
```

### 3. 使用 Nested 类组织测试

```kotlin
@Nested
@DisplayName("保存消息测试")
inner class SaveMessageTests {
    @Test
    fun `save message successfully`() { ... }
}
```

### 4. Mock 最佳实践

- 使用 MockK 进行 Kotlin 友好的 Mock
- 使用 `coEvery` 和 `coVerify` 测试协程
- 使用 `withArg` 验证复杂参数

## 依赖配置

`app/build.gradle.kts` 中已配置测试依赖：

```kotlin
dependencies {
    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.androidx.core.testing)
    testImplementation(libs.turbine)
}
```

## 持续集成

测试已配置在 CI/CD 流程中自动运行：

```yaml
# .github/workflows/test.yml
- name: Run Unit Tests
  run: ./gradlew testDebugUnitTest

- name: Generate Coverage Report
  run: ./gradlew jacocoTestReport
```

## 故障排除

### AAPT2 错误

如果遇到 AAPT2 相关错误，清理 Gradle 缓存：

```bash
rm -rf ~/.gradle/caches/transforms-*
./gradlew clean
```

### 测试未找到

确保测试类名和方法名正确，使用完整类名运行：

```bash
./gradlew testDebugUnitTest --tests "com.openclaw.clawchat.repository.MessageRepositoryTest"
```

## 后续改进

1. **增加集成测试**: 添加 Room 数据库的集成测试
2. **边界条件测试**: 增加更多边界条件和异常场景测试
3. **性能测试**: 添加大数据量场景的性能测试
4. **Mutation Testing**: 使用 PIT 进行变异测试验证测试质量

---

**文档版本**: 1.0  
**最后更新**: 2025-03-18  
**作者**: Qa Lead
