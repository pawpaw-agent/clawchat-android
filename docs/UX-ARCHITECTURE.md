# ClawChat Android UX 架构设计

> **版本**: 1.0  
> **日期**: 2026-03-22  
> **对标**: WebChat (Lit/TypeScript) → Android (Jetpack Compose)

---

## 目录

1. [设计原则](#1-设计原则)
2. [交互模式](#2-交互模式)
3. [组件层级](#3-组件层级)
4. [用户流程](#4-用户流程)
5. [状态管理](#5-状态管理)
6. [可访问性](#6-可访问性)
7. [设计令牌](#7-设计令牌)
8. [性能优化](#8-性能优化)

---

## 1. 设计原则

### 1.1 核心理念

```
WebChat 的设计理念 → Android 实现

1. 简约至上 (Simplicity First)
   - 最小化视觉噪音
   - 内容优先于装饰
   - 功能可见性 > 文字说明

2. 流式优先 (Stream-First)
   - 实时反馈（打字指示器、进度条）
   - 渐进式内容展示
   - 非阻塞式交互

3. 平台原生 (Platform Native)
   - Material Design 3
   - 遵循 Android 设计规范
   - 系统级手势支持
```

### 1.2 设计对标

| WebChat (Lit) | Android (Compose) | 说明 |
|---------------|-------------------|------|
| `renderMessageGroup` | `MessageGroupItem` | Slack 风格消息分组 |
| `renderToolCardSidebar` | `ToolDetailCard` | 工具调用可视化 |
| `renderChatControls` | `MessageInputBar` | 输入组件 + 斜杠命令 |
| `app-scroll.ts` | `LazyListState` | 自动滚动策略 |
| `message-normalizer.ts` | `MessageNormalizer` | 消息归一化处理 |

---

## 2. 交互模式

### 2.1 手势系统

#### 2.1.1 消息交互

```kotlin
/**
 * 消息卡片手势映射
 */
MessageContentCard:
  ├─ 点击 (onClick)           → 无操作（保持选中状态）
  ├─ 长按 (onLongPress)       → 显示操作菜单
  │   ├─ 复制                 → 复制文本到剪贴板
  │   ├─ 复制为 Markdown     → 保留格式复制
  │   ├─ 删除                 → 删除消息（需确认）
  │   └─ 重新生成             → 仅助手消息
  └─ 双击 (onDoubleClick)     → 切换文本选择模式
```

**实现参考**:
```kotlin
// SessionScreen.kt - MessageContentCard
Modifier.combinedClickable(
    onClick = { /* 保持焦点 */ },
    onLongClick = { showMenu = true },
    onDoubleClick = { enableTextSelection() }
)
```

#### 2.1.2 列表滚动

```kotlin
/**
 * 滚动行为规范
 */
LazyColumn:
  ├─ 自动滚动                 → 新消息到达时滚动到底部
  ├─ 手动中断                 → 用户向上滚动时禁用自动滚动
  ├─ 滚动到底部按钮           → 距底部 > 3 条消息时显示
  └─ 滚动位置记忆             → 切换会话时保存/恢复
```

**参考 WebChat**:
```typescript
// app-scroll.ts
export function scheduleChatScroll(host: ChatHost, force?: boolean) {
  if (!force && !host.chatAutoScroll) return;
  // 滚动到底部逻辑
}
```

#### 2.1.3 会话管理

```kotlin
/**
 * 会话列表手势
 */
SessionItem:
  ├─ 点击 (onClick)           → 打开会话
  ├─ 长按 (onLongPress)       → 显示选项菜单
  │   ├─ 重命名               → 弹出重命名对话框
  │   ├─ 暂停/恢复            → 切换会话状态
  │   ├─ 终止                 → 终止运行中的会话
  │   └─ 删除                 → 删除会话（需确认）
  └─ 左滑 (SwipeToDismiss)    → 快速删除（可选）
```

### 2.2 导航模式

#### 2.2.1 屏幕层级

```
┌─────────────────────────────────────┐
│          App 导航图                  │
├─────────────────────────────────────┤
│                                     │
│  PairingScreen (未配对)             │
│       ↓                             │
│  MainScreen (会话列表)              │
│       ↓                             │
│  SessionScreen (聊天界面)           │
│       ↓                             │
│  SettingsScreen (设置)              │
│                                     │
└─────────────────────────────────────┘
```

#### 2.2.2 返回键行为

```kotlin
/**
 * Android 返回键处理
 */
BackHandler {
  when (currentScreen) {
    SessionScreen -> navigateBackToMain()
    SettingsScreen -> closeSettings()
    MainScreen -> showExitConfirmation()
    else -> onBackPressedDispatcher.onBackPressed()
  }
}
```

### 2.3 反馈机制

#### 2.3.1 视觉反馈

| 状态 | 反馈组件 | 时机 | 持续时间 |
|------|----------|------|----------|
| 发送中 | CircularProgressIndicator | 消息发送中 | 持续 |
| 流式响应 | 打字指示器动画 | 助手思考中 | 持续 |
| 成功 | Toast / Snackbar | 操作完成 | 2-3 秒 |
| 错误 | ErrorSnackbar | 出现错误 | 用户关闭 |
| 加载 | Shimmer 效果 | 加载历史 | 持续 |

#### 2.3.2 触觉反馈

```kotlin
/**
 * 触觉反馈规范
 */
val hapticFeedback = LocalHapticFeedback.current

// 轻触反馈 - 点击按钮
hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)

// 长按反馈 - 显示菜单
hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)

// 错误反馈 - 非法操作
hapticFeedback.performHapticFeedback(HapticFeedbackType.Reject)
```

#### 2.3.3 音效反馈（可选）

```kotlin
/**
 * 音效事件
 */
sealed class SoundEffect {
  object MessageSent : SoundEffect()      // 发送消息
  object MessageReceived : SoundEffect()  // 收到消息
  object Error : SoundEffect()            // 错误提示
}
```

---

## 3. 组件层级

### 3.1 原子组件 (Atoms)

```
┌─────────────────────────────────────┐
│           原子组件层                 │
├─────────────────────────────────────┤
│                                     │
│  • ClawAvatar          头像         │
│  • ClawIcon            图标         │
│  • ClawText            文本         │
│  • ClawButton          按钮         │
│  • ClawTextField       输入框       │
│  • ClawCard            卡片         │
│  • ClawChip            标签         │
│  • ClawDivider         分隔线       │
│  • ClawBadge           徽章         │
│  • ClawProgressBar     进度条       │
│                                     │
└─────────────────────────────────────┘
```

**示例实现**:
```kotlin
/**
 * 原子组件 - ClawAvatar
 */
@Composable
fun ClawAvatar(
  role: MessageRole,
  modifier: Modifier = Modifier,
  assistantIdentity: AssistantIdentity? = null,
  size: AvatarSize = AvatarSize.MEDIUM
) {
  when (role) {
    MessageRole.USER -> UserAvatar(size)
    MessageRole.ASSISTANT -> AssistantAvatar(assistantIdentity, size)
    MessageRole.TOOL -> ToolAvatar(size)
    MessageRole.SYSTEM -> SystemAvatar(size)
  }
}
```

### 3.2 分子组件 (Molecules)

```
┌─────────────────────────────────────┐
│           分子组件层                 │
├─────────────────────────────────────┤
│                                     │
│  • MessageBubble       消息气泡     │
│  • ToolTag             工具标签     │
│  • ConnectionBadge     连接状态     │
│  • SessionPreview      会话预览     │
│  • SlashCommandItem    命令项       │
│  • AttachmentPreview   附件预览     │
│  • MessageTimestamp    消息时间戳   │
│  • MessageMeta         消息元数据   │
│                                     │
└─────────────────────────────────────┘
```

**示例实现**:
```kotlin
/**
 * 分子组件 - ToolTag
 * 对标 WebChat: chat-tool-tag
 */
@Composable
fun ToolTag(
  name: String,
  isError: Boolean,
  isExpanded: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(4.dp),
    color = when {
      isError -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
      else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
    },
    modifier = modifier.height(24.dp)
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
      Icon(
        imageVector = if (isError) Icons.Default.ErrorOutline else Icons.Default.Terminal,
        contentDescription = null,
        modifier = Modifier.size(12.dp),
        tint = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
      )
      Text(
        text = name,
        style = MaterialTheme.typography.labelSmall
      )
    }
  }
}
```

### 3.3 组织组件 (Organisms)

```
┌─────────────────────────────────────┐
│           组织组件层                 │
├─────────────────────────────────────┤
│                                     │
│  • MessageGroupItem    消息分组项   │
│  • ToolDetailCard      工具详情卡   │
│  • MessageInputBar     消息输入栏   │
│  • SessionList         会话列表     │
│  • SlashCommandMenu    命令菜单     │
│  • SessionOptionsDialog 会话选项    │
│                                     │
└─────────────────────────────────────┘
```

**示例实现**:
```kotlin
/**
 * 组织组件 - MessageGroupItem
 * 对标 WebChat: renderMessageGroup
 * 
 * Slack 风格消息分组：
 * - 连续同角色消息合并
 * - 仅最后一条消息显示时间戳
 * - 工具消息内联显示
 */
@Composable
fun MessageGroupItem(
  group: MessageGroup,
  modifier: Modifier = Modifier,
  messageFontSize: FontSize = FontSize.MEDIUM,
  onDeleteMessage: (String) -> Unit = {},
  onRegenerate: () -> Unit = {}
) {
  val isUser = group.role == MessageRole.USER
  val isSystem = group.role == MessageRole.SYSTEM
  val isTool = group.role == MessageRole.TOOL
  
  when {
    isSystem -> SystemMessageGroup(group.messages)
    isTool -> ToolMessageGroup(group.messages)
    else -> ChatMessageGroup(
      group = group,
      isUser = isUser,
      messageFontSize = messageFontSize,
      onDeleteMessage = onDeleteMessage,
      onRegenerate = onRegenerate
    )
  }
}
```

### 3.4 屏幕组件 (Screens)

```
┌─────────────────────────────────────┐
│           屏幕组件层                 │
├─────────────────────────────────────┤
│                                     │
│  • PairingScreen       配对屏幕     │
│  • MainScreen          主屏幕       │
│  • SessionScreen       会话屏幕     │
│  • SettingsScreen      设置屏幕     │
│                                     │
└─────────────────────────────────────┘
```

---

## 4. 用户流程

### 4.1 首次使用流程

```
┌─────────────────────────────────────┐
│          首次使用流程                │
├─────────────────────────────────────┤
│                                     │
│  1. 启动应用                        │
│     ↓                               │
│  2. PairingScreen                   │
│     ├─ 显示二维码                   │
│     ├─ 或输入配对码                 │
│     └─ 等待审批                     │
│     ↓                               │
│  3. 审批成功                        │
│     ├─ 保存设备令牌                 │
│     └─ 跳转到 MainScreen            │
│     ↓                               │
│  4. MainScreen (空状态)             │
│     ├─ 显示"创建会话"引导           │
│     └─ 用户点击 FAB                 │
│     ↓                               │
│  5. 创建第一个会话                  │
│     └─ 跳转到 SessionScreen         │
│                                     │
└─────────────────────────────────────┘
```

### 4.2 聊天流程

```
┌─────────────────────────────────────┐
│          聊天交互流程                │
├─────────────────────────────────────┤
│                                     │
│  用户输入                            │
│     ↓                               │
│  MessageInputBar                     │
│     ├─ 输入文本                     │
│     ├─ 添加附件（可选）             │
│     └─ 执行斜杠命令（可选）         │
│     ↓                               │
│  消息验证                            │
│     ├─ 检查连接状态                 │
│     ├─ 检查输入非空                 │
│     └─ 验证附件大小                 │
│     ↓                               │
│  发送消息                            │
│     ├─ 显示用户消息                 │
│     ├─ 清空输入框                   │
│     ├─ 滚动到底部                   │
│     └─ 显示加载指示器               │
│     ↓                               │
│  接收响应                            │
│     ├─ 流式文本渲染                 │
│     ├─ 工具调用卡片                 │
│     └─ 最终消息                     │
│     ↓                               │
│  完成状态                            │
│     ├─ 显示元数据（tokens, cost）   │
│     ├─ 显示操作按钮                 │
│     └─ 恢复输入状态                 │
│                                     │
└─────────────────────────────────────┘
```

### 4.3 会话管理流程

```
┌─────────────────────────────────────┐
│        会话管理流程                  │
├─────────────────────────────────────┤
│                                     │
│  MainScreen                          │
│     ├─ 显示会话列表                 │
│     ├─ 搜索过滤                     │
│     └─ 状态指示                     │
│     ↓                               │
│  会话操作                            │
│     ├─ 点击 → 进入会话              │
│     └─ 长按 → 选项菜单              │
│         ↓                           │
│     ┌─────────────────┐             │
│     │ 重命名           │             │
│     │ 暂停/恢复        │             │
│     │ 终止             │             │
│     │ 删除             │             │
│     └─────────────────┘             │
│     ↓                               │
│  确认操作                            │
│     ├─ 危险操作需确认               │
│     └─ 显示操作结果                 │
│                                     │
└─────────────────────────────────────┘
```

### 4.4 设置流程

```
┌─────────────────────────────────────┐
│          设置流程                    │
├─────────────────────────────────────┤
│                                     │
│  MainScreen → SettingsScreen        │
│     ↓                               │
│  设置项                              │
│     ├─ 网关配置                     │
│     │   ├─ 查看当前网关             │
│     │   ├─ 切换网关                 │
│     │   └─ 添加新网关               │
│     ├─ 通知设置                     │
│     │   ├─ 启用/禁用通知             │
│     │   └─ 勿扰模式                 │
│     ├─ 显示设置                     │
│     │   ├─ 字体大小                 │
│     │   └─ 主题模式                 │
│     └─ 账户管理                     │
│         ├─ 设备信息                 │
│         └─ 注销登录                 │
│                                     │
└─────────────────────────────────────┘
```

---

## 5. 状态管理

### 5.1 状态层次

```
┌─────────────────────────────────────┐
│          状态管理架构                │
├─────────────────────────────────────┤
│                                     │
│  ┌─────────────────────────────┐    │
│  │      Application Scope      │    │
│  │  (Hilt/Singleton)           │    │
│  ├─────────────────────────────┤    │
│  │  • UserRepository           │    │
│  │  • GatewayRepository        │    │
│  │  • SessionRepository        │    │
│  │  • SettingsRepository       │    │
│  └─────────────────────────────┘    │
│               ↓                     │
│  ┌─────────────────────────────┐    │
│  │      ViewModel Scope        │    │
│  │  (ViewModel)                │    │
│  ├─────────────────────────────┤    │
│  │  • MainViewModel            │    │
│  │  • SessionViewModel         │    │
│  │  • SettingsViewModel        │    │
│  │  • PairingViewModel         │    │
│  └─────────────────────────────┘    │
│               ↓                     │
│  ┌─────────────────────────────┐    │
│  │      UI State               │    │
│  │  (Compose State)            │    │
│  ├─────────────────────────────┤    │
│  │  • SessionUiState           │    │
│  │  • MainUiState              │    │
│  │  • SettingsUiState          │    │
│  └─────────────────────────────┘    │
│                                     │
└─────────────────────────────────────┘
```

### 5.2 状态定义

#### 5.2.1 连接状态

```kotlin
/**
 * 连接状态机
 */
sealed class ConnectionStatus {
  data object Disconnected : ConnectionStatus()
  data object Connecting : ConnectionStatus()
  data class Connected(val latency: Long? = null) : ConnectionStatus()
  data object Disconnecting : ConnectionStatus()
  data class Error(val message: String, val throwable: Throwable? = null) : ConnectionStatus()
  
  val isConnected: Boolean get() = this is Connected
  val isConnecting: Boolean get() = this is Connecting || this is Disconnecting
}

/**
 * 状态转换图
 */
Disconnected → Connecting → Connected → Disconnecting → Disconnected
     ↓              ↓           ↓              ↓
   Error ←─────── Error ←──── Error ←──────── Error
```

#### 5.2.2 消息状态

```kotlin
/**
 * 会话状态（1:1 对应 WebChat ChatState + ToolStreamHost）
 */
data class SessionUiState(
  // 连接状态
  val connectionStatus: ConnectionStatus = ConnectionStatus.Disconnected,
  val isLoading: Boolean = false,
  val isSending: Boolean = false,
  val error: String? = null,
  
  // 会话信息
  val sessionId: String? = null,
  val session: SessionUi? = null,
  
  // 消息状态（对标 WebChat）
  val chatMessages: List<MessageUi> = emptyList(),           // 历史消息
  val chatStream: String? = null,                             // 当前流式文本
  val chatStreamStartedAt: Long? = null,                      // 流式开始时间
  val chatRunId: String? = null,                              // 当前 runId
  val chatStreamSegments: List<StreamSegment> = emptyList(),  // 已提交的文本段
  
  // 工具流状态（对标 WebChat ToolStreamHost）
  val toolStreamById: Map<String, ToolStreamEntry> = emptyMap(),
  val toolStreamOrder: List<String> = emptyList(),
  val chatToolMessages: List<MessageUi> = emptyList(),
  
  // 输入
  val inputText: String = "",
  val attachments: List<ChatAttachment> = emptyList()
)
```

### 5.3 加载状态处理

```kotlin
/**
 * 加载状态组件
 */
@Composable
fun LoadingStateHost(
  isLoading: Boolean,
  error: String?,
  onRetry: () -> Unit,
  content: @Composable () -> Unit
) {
  when {
    isLoading -> LoadingIndicator()
    error != null -> ErrorState(error = error, onRetry = onRetry)
    else -> content()
  }
}

/**
 * 分页加载状态
 */
sealed class PagingState<T> {
  data class Initial<T> : PagingState<T>()
  data class Loading<T> : PagingState<T>()
  data class Content<T>(val data: List<T>, val hasMore: Boolean) : PagingState<T>()
  data class Error<T>(val message: String) : PagingState<T>()
  data class Empty<T> : PagingState<T>()
}
```

### 5.4 错误状态处理

```kotlin
/**
 * 错误类型定义
 */
sealed class AppError {
  data class Network(val message: String) : AppError()
  data class Authentication(val message: String) : AppError()
  data class Validation(val message: String) : AppError()
  data class Unknown(val throwable: Throwable) : AppError()
  
  fun toUserMessage(): String = when (this) {
    is Network -> "网络错误: $message"
    is Authentication -> "认证失败: $message"
    is Validation -> message
    is Unknown -> "发生未知错误"
  }
}

/**
 * 错误展示策略
 */
@Composable
fun ErrorHandler(
  error: AppError?,
  onDismiss: () -> Unit,
  onRetry: (() -> Unit)?
) {
  error?.let {
    when (it) {
      is AppError.Network -> NetworkErrorSnackbar(it.toUserMessage(), onDismiss, onRetry)
      is AppError.Authentication -> AuthErrorDialog(it.toUserMessage(), onDismiss)
      is AppError.Validation -> ValidationErrorToast(it.toUserMessage())
      is AppError.Unknown -> GenericErrorSnackbar(it.toUserMessage(), onDismiss)
    }
  }
}
```

### 5.5 空状态处理

```kotlin
/**
 * 空状态定义
 */
sealed class EmptyState {
  data class NoSessions(val onAction: () -> Unit) : EmptyState()
  data class NoMessages(val isOnline: Boolean) : EmptyState()
  data class NoSearchResults(val query: String) : EmptyState()
}

/**
 * 空状态组件
 */
@Composable
fun EmptyStateView(state: EmptyState) {
  when (state) {
    is EmptyState.NoSessions -> NoSessionsView(state.onAction)
    is EmptyState.NoMessages -> NoMessagesView(state.isOnline)
    is EmptyState.NoSearchResults -> NoSearchResultsView(state.query)
  }
}

@Composable
private fun NoSessionsView(onCreateSession: () -> Unit) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center
  ) {
    Icon(Icons.Default.ChatBubbleOutline, modifier = Modifier.size(64.dp))
    Spacer(modifier = Modifier.height(16.dp))
    Text("暂无会话", style = MaterialTheme.typography.titleMedium)
    Spacer(modifier = Modifier.height(8.dp))
    Text("点击 + 创建新会话", style = MaterialTheme.typography.bodyMedium)
    Spacer(modifier = Modifier.height(24.dp))
    Button(onClick = onCreateSession) {
      Text("创建会话")
    }
  }
}
```

---

## 6. 可访问性

### 6.1 语义化描述

```kotlin
/**
 * 语义化标注规范
 */
@Composable
fun AccessibleMessageCard(message: MessageUi) {
  Card(
    modifier = Modifier
      .semantics {
        // 角色描述
        role = if (message.role == MessageRole.USER) Role.Button else Role.Image
        // 内容描述
        contentDescription = buildContentDescription(message)
        // 状态描述
        stateDescription = if (message.isLoading) "加载中" else "已完成"
      }
  ) {
    // 内容...
  }
}

private fun buildContentDescription(message: MessageUi): String {
  val role = when (message.role) {
    MessageRole.USER -> "你"
    MessageRole.ASSISTANT -> "助手"
    MessageRole.SYSTEM -> "系统"
    MessageRole.TOOL -> "工具"
  }
  val content = message.getTextContent().take(100)
  val time = formatTimestamp(message.timestamp)
  return "$role 说于 $time: $content"
}
```

### 6.2 焦点管理

```kotlin
/**
 * 焦点导航规范
 */
@Composable
fun SessionScreen() {
  val focusRequester = remember { FocusRequester() }
  
  // 初始化时聚焦输入框
  LaunchedEffect(Unit) {
    focusRequester.requestFocus()
  }
  
  Column {
    // 消息列表 - 焦点可遍历
    LazyColumn(
      modifier = Modifier.semantics {
        traversalGroup = true
      }
    ) {
      // 消息项...
    }
    
    // 输入框 - 初始焦点
    TextField(
      modifier = Modifier.focusRequester(focusRequester),
      // ...
    )
  }
}
```

### 6.3 字体缩放

```kotlin
/**
 * 字体大小设置
 */
enum class FontSize {
  SMALL,   // 10.sp
  MEDIUM,  // 13.sp (默认)
  LARGE    // 16.sp
}

/**
 * 动态字体支持
 */
@Composable
fun ScalableText(
  text: String,
  fontSize: FontSize,
  modifier: Modifier = Modifier
) {
  val textSize = when (fontSize) {
    FontSize.SMALL -> 10.sp
    FontSize.MEDIUM -> 13.sp
    FontSize.LARGE -> 16.sp
  }
  
  // 支持系统字体缩放
  val scaledSize = with(LocalDensity.current) {
    textSize * LocalFontScale.current
  }
  
  Text(
    text = text,
    fontSize = scaledSize,
    modifier = modifier
  )
}
```

### 6.4 色彩对比度

```kotlin
/**
 * WCAG 2.1 AA 标准对比度
 * 
 * 常规文本: ≥ 4.5:1
 * 大文本: ≥ 3:1
 */
object ColorContrast {
  // 主色调对比
  val primaryOnBackground = Color(0xFF6750A4) // 对比度 7.2:1 ✓
  val errorOnBackground = Color(0xFFB3261E)   // 对比度 5.1:1 ✓
  
  // 次要文本对比
  val secondaryOnBackground = Color(0xFF49454F) // 对比度 8.5:1 ✓
  val hintOnBackground = Color(0xFF79747E)      // 对比度 4.8:1 ✓
  
  // 暗色模式对比
  val primaryOnBackgroundDark = Color(0xFFD0BCFF) // 对比度 8.1:1 ✓
}
```

### 6.5 屏幕阅读器支持

```kotlin
/**
 * TalkBack 优化
 */
@Composable
fun TalkBackOptimizedMessage(
  message: MessageUi,
  onDelete: () -> Unit,
  onRegenerate: () -> Unit
) {
  Row(
    modifier = Modifier
      .semantics {
        // 自定义动作
        customActions = listOf(
          CustomAccessibilityAction("删除") {
            onDelete()
            true
          },
          CustomAccessibilityAction("重新生成") {
            onRegenerate()
            true
          }
        )
      }
  ) {
    // 内容...
  }
}
```

### 6.6 动画可访问性

```kotlin
/**
 * 减少动画支持
 */
@Composable
fun AccessibleAnimatedVisibility(
  visible: Boolean,
  content: @Composable AnimatedVisibilityScope.() -> Unit
) {
  val reduceMotion = LocalReducedMotion.current
  
  AnimatedVisibility(
    visible = visible,
    enter = if (reduceMotion) EnterTransition.None else fadeIn() + slideInVertically(),
    exit = if (reduceMotion) ExitTransition.None else fadeOut() + slideOutVertically(),
    content = content
  )
}
```

---

## 7. 设计令牌

### 7.1 颜色系统

```kotlin
/**
 * Material Design 3 颜色令牌
 */
object ClawColors {
  // Primary
  val primary = Color(0xFF6750A4)
  val onPrimary = Color(0xFFFFFFFF)
  val primaryContainer = Color(0xFFEADDFF)
  val onPrimaryContainer = Color(0xFF21005D)
  
  // Secondary
  val secondary = Color(0xFF625B71)
  val onSecondary = Color(0xFFFFFFFF)
  val secondaryContainer = Color(0xFFE8DEF8)
  val onSecondaryContainer = Color(0xFF1D192B)
  
  // Error
  val error = Color(0xFFB3261E)
  val onError = Color(0xFFFFFFFF)
  val errorContainer = Color(0xFFF9DEDC)
  val onErrorContainer = Color(0xFF410E0B)
  
  // Surface
  val surface = Color(0xFFFFFBFE)
  val onSurface = Color(0xFF1C1B1F)
  val surfaceVariant = Color(0xFFE7E0EC)
  val onSurfaceVariant = Color(0xFF49454F)
  
  // Background
  val background = Color(0xFFFFFBFE)
  val onBackground = Color(0xFF1C1B1F)
}
```

### 7.2 排版系统

```kotlin
/**
 * Material Design 3 排版令牌
 */
object ClawTypography {
  // Display
  val displayLarge = TextStyle(
    fontSize = 57.sp,
    lineHeight = 64.sp,
    letterSpacing = (-0.25).sp,
    fontWeight = FontWeight.Normal
  )
  
  val displayMedium = TextStyle(
    fontSize = 45.sp,
    lineHeight = 52.sp,
    letterSpacing = 0.sp,
    fontWeight = FontWeight.Normal
  )
  
  // Headline
  val headlineLarge = TextStyle(
    fontSize = 32.sp,
    lineHeight = 40.sp,
    letterSpacing = 0.sp,
    fontWeight = FontWeight.Normal
  )
  
  // Title
  val titleLarge = TextStyle(
    fontSize = 22.sp,
    lineHeight = 28.sp,
    letterSpacing = 0.sp,
    fontWeight = FontWeight.Normal
  )
  
  val titleMedium = TextStyle(
    fontSize = 16.sp,
    lineHeight = 24.sp,
    letterSpacing = 0.15.sp,
    fontWeight = FontWeight.Medium
  )
  
  // Body
  val bodyLarge = TextStyle(
    fontSize = 16.sp,
    lineHeight = 24.sp,
    letterSpacing = 0.5.sp,
    fontWeight = FontWeight.Normal
  )
  
  val bodyMedium = TextStyle(
    fontSize = 14.sp,
    lineHeight = 20.sp,
    letterSpacing = 0.25.sp,
    fontWeight = FontWeight.Normal
  )
  
  // Label
  val labelLarge = TextStyle(
    fontSize = 14.sp,
    lineHeight = 20.sp,
    letterSpacing = 0.1.sp,
    fontWeight = FontWeight.Medium
  )
  
  val labelMedium = TextStyle(
    fontSize = 12.sp,
    lineHeight = 16.sp,
    letterSpacing = 0.5.sp,
    fontWeight = FontWeight.Medium
  )
  
  val labelSmall = TextStyle(
    fontSize = 11.sp,
    lineHeight = 16.sp,
    letterSpacing = 0.5.sp,
    fontWeight = FontWeight.Medium
  )
}
```

### 7.3 间距系统

```kotlin
/**
 * 8dp 网格间距系统
 */
object ClawSpacing {
  val none = 0.dp
  val xs = 2.dp
  val sm = 4.dp
  val md = 8.dp
  val lg = 12.dp
  val xl = 16.dp
  val xxl = 24.dp
  val xxxl = 32.dp
  val huge = 48.dp
  val massive = 64.dp
}

/**
 * 语义化间距
 */
object ClawSemanticSpacing {
  // 组件内部间距
  val buttonPadding = ClawSpacing.lg
  val cardPadding = ClawSpacing.xl
  val listItemPadding = ClawSpacing.xl
  
  // 组件间间距
  val betweenItems = ClawSpacing.md
  val betweenSections = ClawSpacing.xxl
  val betweenScreens = ClawSpacing.xxxl
  
  // 屏幕边距
  val screenPadding = ClawSpacing.xl
  val inputPadding = ClawSpacing.md
}
```

### 7.4 圆角系统

```kotlin
/**
 * Material Design 3 圆角令牌
 */
object ClawShapes {
  val none = RoundedCornerShape(0.dp)
  val extraSmall = RoundedCornerShape(4.dp)
  val small = RoundedCornerShape(8.dp)
  val medium = RoundedCornerShape(12.dp)
  val large = RoundedCornerShape(16.dp)
  val extraLarge = RoundedCornerShape(28.dp)
  val full = RoundedCornerShape(50)
  
  // 语义化形状
  val button = medium
  val card = large
  val dialog = extraLarge
  val textField = small
  val chip = full
  val avatar = full
}
```

### 7.5 阴影系统

```kotlin
/**
 * 阴影层级
 */
object ClawElevation {
  val none = 0.dp
  val level0 = 0.dp     // 无阴影
  val level1 = 1.dp     // 卡片
  val level2 = 3.dp     // 按钮
  val level3 = 6.dp     // FAB
  val level4 = 8.dp     // 导航栏
  val level5 = 12.dp    // 对话框
}
```

---

## 8. 性能优化

### 8.1 列表优化

```kotlin
/**
 * LazyColumn 优化策略
 */
@Composable
fun OptimizedMessageList(
  messages: List<MessageUi>,
  listState: LazyListState
) {
  LazyColumn(
    state = listState,
    // 使用 key 确保正确的重组
    content = {
      items(
        items = messages,
        key = { it.id }
      ) { message ->
        // 使用 remember 避免不必要的计算
        val content by remember(message.content) {
          derivedStateOf { message.getTextContent() }
        }
        
        MessageItem(message = message, content = content)
      }
    }
  )
}

/**
 * 消息分页加载
 */
@Composable
fun PagedMessageList(
  viewModel: SessionViewModel
) {
  val messages by viewModel.messages.collectAsLazyPagingItems()
  
  LazyColumn {
    items(messages.itemCount) { index ->
      messages[index]?.let { message ->
        MessageItem(message = message)
      }
    }
    
    // 加载更多指示器
    when (messages.loadState.append) {
      LoadState.Loading -> item { LoadingItem() }
      is LoadState.Error -> item { ErrorItem() }
      else -> {}
    }
  }
}
```

### 8.2 图片优化

```kotlin
/**
 * 图片加载优化
 */
@Composable
fun OptimizedImage(
  dataUrl: String,
  modifier: Modifier = Modifier
) {
  // 使用 Coil 进行图片加载和缓存
  AsyncImage(
    model = ImageRequest.Builder(LocalContext.current)
      .data(dataUrl)
      .crossfade(true)
      .memoryCachePolicy(CachePolicy.ENABLED)
      .diskCachePolicy(CachePolicy.ENABLED)
      .build(),
    contentDescription = null,
    modifier = modifier,
    contentScale = ContentScale.FillWidth
  )
}

/**
 * 图片尺寸限制
 */
object ImageConstraints {
  const val MAX_WIDTH = 1920
  const val MAX_HEIGHT = 1080
  const val MAX_FILE_SIZE = 10 * 1024 * 1024 // 10MB
  const val THUMBNAIL_SIZE = 200
}
```

### 8.3 动画优化

```kotlin
/**
 * 动画性能优化
 */
@Composable
fun SmoothMessageAnimation(
  visible: Boolean,
  content: @Composable () -> Unit
) {
  // 使用 Animatable 替代 animate*AsState 以获得更好的控制
  val alpha = remember { Animatable(0f) }
  
  LaunchedEffect(visible) {
    alpha.animateTo(
      targetValue = if (visible) 1f else 0f,
      animationSpec = tween(durationMillis = 200)
    )
  }
  
  Box(modifier = Modifier.alpha(alpha.value)) {
    content()
  }
}

/**
 * 流式文本动画
 */
@Composable
fun StreamingTextAnimation(
  text: String,
  modifier: Modifier = Modifier
) {
  var displayedText by remember { mutableStateOf("") }
  
  LaunchedEffect(text) {
    // 逐字显示效果（可选）
    text.forEach { char ->
      delay(10) // 10ms 每字符
      displayedText += char
    }
  }
  
  Text(
    text = displayedText,
    modifier = modifier
  )
}
```

### 8.4 内存优化

```kotlin
/**
 * 消息内容缓存
 */
object MessageContentCache {
  private val cache = LruCache<String, Spanned>(100)
  
  fun getMarkdownContent(text: String): Spanned {
    return cache.get(text) ?: run {
      val spanned = Markdown.parse(text)
      cache.put(text, spanned)
      spanned
    }
  }
}

/**
 * 图片内存管理
 */
@Composable
fun MemoryOptimizedImage(
  base64: String,
  modifier: Modifier = Modifier
) {
  // 使用 remember 缓存解码结果
  val bitmap by remember(base64) {
    derivedStateOf {
      decodeBase64Image(base64, maxWidth = ImageConstraints.MAX_WIDTH)
    }
  }
  
  bitmap?.let {
    Image(
      bitmap = it.asImageBitmap(),
      contentDescription = null,
      modifier = modifier
    )
  }
}
```

### 8.5 网络优化

```kotlin
/**
 * WebSocket 连接管理
 */
class ConnectionManager {
  private var reconnectAttempts = 0
  private val maxReconnectAttempts = 5
  
  fun connect(url: String) {
    // 指数退避重连
    val delay = minOf(
      1000 * (1 shl reconnectAttempts),
      30000 // 最大 30 秒
    )
    
    // 连接逻辑...
  }
  
  fun disconnect() {
    // 清理资源...
  }
}

/**
 * 消息队列管理
 */
class MessageQueue {
  private val queue = Channel<ChatMessage>(capacity = Channel.UNLIMITED)
  
  suspend fun enqueue(message: ChatMessage) {
    queue.send(message)
  }
  
  fun processQueue() {
    // 批量处理消息
  }
}
```

---

## 附录 A: 组件清单

### A.1 已实现组件

| 组件 | 文件 | 状态 |
|------|------|------|
| MainScreen | MainScreen.kt | ✅ |
| SessionScreen | SessionScreen.kt | ✅ |
| SettingsScreen | SettingsScreen.kt | ✅ |
| PairingScreen | PairingScreen.kt | ✅ |
| ChatComponents | ChatComponents.kt | ✅ |
| ChatInputComponents | ChatInputComponents.kt | ✅ |
| ToolCardComponents | ToolCardComponents.kt | ✅ |
| MarkdownText | MarkdownText.kt | ✅ |

### A.2 待优化组件

| 组件 | 优化项 | 优先级 |
|------|--------|--------|
| MessageGroupItem | 滑动删除 | Medium |
| ToolDetailCard | 代码高亮 | Low |
| MessageInputBar | 语音输入 | Low |
| SessionScreen | 双指缩放字体 | Low |

---

## 附录 B: WebChat 对标清单

### B.1 已对标功能

| WebChat 功能 | Android 实现 | 状态 |
|--------------|--------------|------|
| renderMessageGroup | MessageGroupItem | ✅ |
| renderToolCardSidebar | ToolDetailCard | ✅ |
| renderChatControls | MessageInputBar | ✅ |
| scheduleChatScroll | LazyListState | ✅ |
| message-normalizer | MessageNormalizer | ✅ |
| slash-commands | SlashCommands.kt | ✅ |
| tool-cards | ToolCardComponents | ✅ |

### B.2 差异化功能

| Android 特有 | 说明 |
|--------------|------|
| 物理返回键 | 返回导航 |
| 通知系统 | 本地通知 |
| 分享功能 | 系统分享 |
| Widget | 主屏幕小部件 |
| Wear OS | 手表客户端 |

---

## 附录 C: 测试用例

### C.1 交互测试

```kotlin
@Test
fun messageLongPress_showsContextMenu() {
  composeTestRule.setContent {
    MessageContentCard(message = testMessage)
  }
  
  composeTestRule
    .onNodeWithText(testMessage.content)
    .performTouchInput { longClick() }
  
  composeTestRule
    .onNodeWithText("复制")
    .assertIsDisplayed()
}

@Test
fun sendMessage_clearsInput() {
  val viewModel = SessionViewModel()
  composeTestRule.setContent {
    MessageInputBar(
      value = "Test message",
      onValueChange = {},
      onSend = { viewModel.sendMessage("Test message") }
    )
  }
  
  composeTestRule
    .onNodeWithContentDescription("发送")
    .performClick()
  
  assertEquals("", viewModel.state.value.inputText)
}
```

### C.2 可访问性测试

```kotlin
@Test
fun messageCard_hasCorrectSemantics() {
  composeTestRule.setContent {
    MessageContentCard(message = testMessage)
  }
  
  composeTestRule
    .onNodeWithText(testMessage.content)
    .assert(
      SemanticsMatcher.expectValue(
        SemanticsProperties.ContentDescription,
        listOf("你说于 ${formatTimestamp(testMessage.timestamp)}: ${testMessage.content}")
      )
    )
}
```

---

## 版本历史

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| 1.0 | 2026-03-22 | 初始版本，定义核心架构 |

---

**文档维护者**: UX Architect Agent  
**审核状态**: ✅ 已完成