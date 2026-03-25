# Changelog

所有重要的项目变更都将记录在此文件中。

格式基于 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.0.0/)，项目遵循 [Semantic Versioning](https://semver.org/lang/zh-CN/)。

---

## [1.1.0] - 2026-03-26

### 新增

- **消息时间戳显示** - 显示相对时间（刚刚、X分钟前）和绝对时间
- **消息删除确认对话框** - 防止误删消息
- **会话删除确认对话框** - 防止误删会话
- **消息重试功能** - 失败消息可重新发送
- **发送中进度指示器** - LinearProgressIndicator 显示发送状态
- **会话日期分组** - 今天、昨天、本周、更早
- **代码语法高亮** - Kotlin, Java, Python, JS, Rust, Go, C/C++ 等
- **图片全屏查看** - 支持缩放和手势
- **会话搜索功能** - 按关键词过滤会话
- **双击复制消息** - 快速复制消息内容
- **触觉反馈** - 发送按钮振动反馈
- **流式光标动画** - 打字机效果
- **工具卡片展开动画** - 平滑过渡
- **消息项动画** - 入场动画
- **会话列表动画** - 列表项动画
- **Markdown 链接可点击** - 点击链接在浏览器中打开
- **代码块复制按钮** - 一键复制代码
- **长按震动反馈** - 消息和会话项长按振动

### 改进

- **滚动逻辑重写** - 完全对标 webchat app-scroll.ts
- **Markdown 渲染** - 代码块、表格、blockquote、可点击链接支持
- **IME 滚动处理** - 键盘弹出自动滚动
- **空状态视觉层次** - 优化图标和文字透明度
- **清理未使用导入** - 移除 isSystemInDarkTheme 未使用导入

### 修复

- **formatTimestamp 函数重载冲突** - 删除重复定义

---

## [未发布]

### 修复 - 代码审查 #003 (2026-03-19)

本次修复针对代码审查 #003 发现的 32 个问题，涵盖架构、安全、性能和代码质量。

#### 🔴 严重问题 (5 个)

| 编号 | 问题 | 修复状态 |
|------|------|----------|
| S1 | SecurityModuleBindings 提供冗余实例，导致 DI 双实例问题 | ✅ 已修复 |
| S2 | GatewayConnection 无最大重连次数限制，永久不可达时无限重连 | ✅ 已修复 |
| S3 | Ed25519 私钥存储在 SharedPreferences（API 26-32 软件密钥路径） | ⚠️ 已知限制，已记录 |
| S4 | GatewayRepository.clearGatewayConfig 使用私有扩展函数遮蔽 EncryptedStorage 方法 | ✅ 已修复 |
| S5 | MessageRepository.getMessageById() 硬编码返回 null，消息状态更新为空操作 | ✅ 已修复 |

#### 🟠 高优问题 (7 个)

| 编号 | 问题 | 修复状态 |
|------|------|----------|
| H1 | MainViewModel 事件通道使用 MutableStateFlow 导致快速连续事件丢失 | ✅ 已修复 |
| H2 | 协程异常未全局捕获，遗漏的 try/catch 导致静默失败 | ✅ 已修复 |
| H3 | RequestTracker 清理协程泄漏，使用匿名 CoroutineScope 创建无限循环 | ✅ 已修复 |
| H4 | GatewayConnection.connect() 使用 busy-wait 轮询 StateFlow，浪费 CPU | ✅ 已修复 |
| H5 | 日志中可能泄露 nonce 和 deviceId 敏感信息 | ✅ 已修复 |
| H6 | BouncyCastle Ed25519 私钥缓存在内存中不清除（cachedBcPrivateKey） | ✅ 已修复 |
| H7 | Room 数据库使用 fallbackToDestructiveMigration，生产环境不安全 | ✅ 已修复 |
| H8 | SessionRepository 直接操作 UI 模型 SessionUi，违反 Clean Architecture | ✅ 已重构 |

#### 🟡 中优问题 (10 个)

| 编号 | 问题 | 修复状态 |
|------|------|----------|
| M1 | Converters 注册了 Instant 转换器但 Entity 中未使用 | ✅ 已清理 |
| M2 | clearTerminatedSessions 循环删除无事务保护 | ✅ 已修复 |
| M3 | EventDeduplicator.isDuplicate 返回值语义反直觉 | ✅ 已重命名 |
| M4 | WebSocketProtocol.PROTOCOL_VERSION 作为 Int 但 Header 传 String（多余） | ✅ 已删除 |
| M5 | RetryManager 过度设计（312 行）但项目中从未使用 | ✅ 已删除 |
| M6 | NotificationManager 构造函数缺少 @ApplicationContext | ✅ 已修复 |
| M7 | PairingViewModel.consumeEvent() 是空操作，误导调用者 | ✅ 已删除 |
| M8 | SessionViewModel.sendMessage 中用户消息可能重复保存（insert 而非 update） | ✅ 已修复 |
| M9 | SessionViewModel 的 streamingBuffers/completedRuns 无界增长 | ✅ 已修复 |
| M10 | 魔法数字（delay(50)、take(8)、take(16)、18789 等）散布多处 | ✅ 已提取常量 |

#### 🟢 低优问题 (6 个)

| 编号 | 问题 | 修复状态 |
|------|------|----------|
| L1 | RequestFrame 中有未使用的 data class（SendMessageParams 等） | ✅ 已删除 |
| L2 | EventFrame 中有未使用的事件创建辅助函数（客户端不发送事件） | ✅ 已删除 |
| L3 | ResponseFrame 中有未使用的辅助函数（errorResponse 等） | ✅ 已删除 |
| L4 | 注释语言混用（中英文混杂） | ✅ 已统一 |
| L5 | RequestIdGenerator 非线程安全（requestCounter++ 非原子） | ✅ 已修复 |
| L6 | GatewayUrlUtil 缺少 IPv6 支持 | ⚠️ 待后续版本 |
| L7 | 测试覆盖不足（新架构无测试） | ⚠️ 待后续版本 |
| L8 | Converters 中未使用的 import java.time.Instant | ✅ 已清理 |

### 重构

#### H8: Domain 层重构

- 引入 Domain 模型 `Session`、`Message`、`ConnectionStatus` 等
- Repository 层改为操作 Domain 模型，不再直接操作 UI 模型
- ViewModel 负责 Domain 模型到 UI 模型的转换
- 符合 Clean Architecture 分层原则

**影响范围**:
- `domain/model/` - 新增 Domain 模型
- `repository/` - 改为返回 Domain 模型
- `ui/state/` - 添加转换逻辑

### CI/CD

#### 编译修复

- 修复 Hilt DI 配置，消除双实例问题
- 修复 Room 数据库迁移配置
- 添加 ProGuard 规则，保留 Ed25519 相关类
- 优化 Gradle 构建缓存

---

## [0.2.0] - 2026-03-17

### 新增

- 完整的 OpenClaw Gateway 协议 v3 实现
- Ed25519 设备密钥管理（API 33+ 使用 Android Keystore，API 26-32 使用 BouncyCastle）
- WebSocket 连接管理（自动重连、延迟测量）
- 会话管理（创建、终止、删除）
- 消息发送与流式接收
- 设备配对流程（挑战 - 响应签名）
- 通知系统集成

### 技术栈

- Jetpack Compose + Material Design 3
- Hilt 依赖注入
- Room 数据库
- OkHttp WebSocket
- Kotlin Coroutines + Flow

---

## [0.1.0] - 2026-03-15

### 新增

- 项目初始版本
- 基础架构搭建
- UI 框架（Compose）

---

## 约定

### 版本格式

- **主版本号** - 不兼容的 API 变更
- **次版本号** - 向后兼容的新功能
- **修订号** - 向后兼容的问题修复

### 标签说明

- 🔴 **严重** - 必须修复，影响核心功能或安全
- 🟠 **高优** - 应该修复，影响稳定性或性能
- 🟡 **中优** - 建议修复，影响代码质量或可维护性
- 🟢 **低优** - 可以修复，代码清理或优化

---

*最后更新：2026-03-19*
