# Changelog

所有重要的项目变更都将记录在此文件中。

格式基于 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.0.0/)，项目遵循 [Semantic Versioning](https://semver.org/lang/zh-CN/)。

---

## [1.3.0] - 2026-04-06

### 新增功能

**安全功能**
- **Certificate Pinning** - 防止中间人攻击，支持 TOFU 模式
- **Root 检测** - 检测设备 Root 状态，支持风险等级评估

**日志系统**
- **日志级别控制** - 支持 VERBOSE/DEBUG/INFO/WARNING/ERROR/NONE 级别
- **日志文件持久化** - 自动写入文件，支持轮转和清理

**国际化**
- **西班牙语支持** - 完整翻译所有 UI 字符串
- **葡萄牙语支持** - 完整翻译所有 UI 字符串

**开发者功能**
- **Firebase 集成准备** - 支持 Crashlytics、Analytics、Performance（需配置 google-services.json）
- **Baseline Profile** - 添加预编译类配置，优化启动速度

### 改进

**构建优化**
- 启用 Gradle 配置缓存
- 启用并行构建和增量编译
- 优化 JVM 参数（4GB 内存、ParallelGC）
- 优化 APK 打包，排除不必要的 META-INF 文件

**测试**
- 添加 JsonUtils 单元测试
- 添加 FileUtils 单元测试
- 添加 AppConstants 单元测试

**依赖更新**
- 添加 LeakCanary 内存泄漏检测（仅 Debug 构建）

### 技术细节

- 现已支持 8 种语言：中文、英文、德语、法语、日语、韩语、西班牙语、葡萄牙语
- 日志系统支持运行时切换级别
- Root 检测覆盖常见 Root 工具（Magisk、SuperSU 等）

---

## [1.2.1] - 2026-04-03

### 修复

- **重命名对话框** - 实现会话重命名功能，支持长按菜单触发
- **置顶功能** - 实现会话置顶/取消置顶，置顶会话自动排序到顶部
- **消息删除同步** - deleteMessage 现在同步到 MessageRepository
- **`/new` 命令** - 实现导航回主界面功能
- **关于对话框版本** - 使用 BuildConfig.VERSION_NAME 替代硬编码版本
- **Toast 国际化** - 代码复制提示使用字符串资源

### 改进

- 清理所有 TODO 注释
- SlashCommandExecutor 支持 onNavigateBack 回调
- SessionListContent 支持重命名和置顶回调

---

## [1.2.0] - 2026-04-01

### 新增功能

**核心功能**
- 二维码生成 (ZXing) - QRCodeUtils 工具类
- 语音输入 (SpeechRecognizer) - VoiceInputManager 语音识别
- 提示词存储 (DataStore) - RecentPromptsStore 最近提示词
- 草稿自动保存 - DraftStore 24小时有效期
- 消息导出 - ExportUtils JSON/Text/Image 格式
- 消息统计 - StatsUtils 字数统计（中文=1，英文单词=1）

**UI 组件**
- AnimatedFloatingActionButton - 弹簧动画 FAB
- ProgressButton - 发送进度按钮
- ExpandableCard - 可展开卡片
- SwipeableMessageCard - 滑动删除消息
- BatchOperationToolbar - 批量操作工具栏
- QuotedMessageBar - 引用消息组件
- UndoSnackbar - 撤销删除 Snackbar
- ThemeColorSettingItem - 主题色选择器（8 种预设）

**动画系统**
- AnimationUtils - 统一动画预设
- pageTransition() - 页面切换动画
- listItemEnter() - 列表项入场
- messageSendEnter() - 消息发送动画
- messageDeleteExit() - 消息删除动画

**响应式布局**
- LayoutUtils - WindowSizeClass (COMPACT/MEDIUM/EXPANDED)
- 折叠屏适配 - FoldingFeature 支持
- 横屏优化 - 双栏布局
- 大屏幕支持 - 三栏布局

**交互增强**
- 滑动删除会话 - SwipeToDismiss
- 键盘快捷键 - Ctrl+N/F/Z/S
- Slash 命令自动补全 - 已实现
- 长按弹出菜单 - 复制/分享/删除
- 消息引用回复 - QuotedMessageBar

**拖拽排序**
- ReorderableList - 拖拽排序工具类
- DragState - 拖拽状态管理
- dragReorderModifier - 手势检测

**撤销功能**
- UndoQueue - 删除队列管理器
- UndoSnackbar - 5秒撤销超时
- DeletedSession/DeletedMessage - 删除数据类

**批量操作**
- SelectionState - 选择状态管理
- 批量删除 - 多选删除
- 批量复制 - 复制多条消息
- 全选 - 一键全选

### 依赖更新
- ZXing Core 3.5.2
- ZXing Android Embedded 4.3.0
- DataStore Preferences 1.0.0

### 权限新增
- RECORD_AUDIO - 语音输入权限

### 代码统计
- 总任务完成: 343
- 新增文件: 15+
- 代码行数: 20,000+

## [1.1.0] - 2026-04-01

### 新增功能

**API 支持**
- 会话引导 (sessions.steer) - 向运行中会话发送引导消息
- 定时任务管理 (cron.*) - cronList, cronAdd, cronRemove, cronRun

**交互优化**
- 命令面板 (⌘K) - 快速搜索会话和命令
- 批量操作 - 多选会话批量删除
- 消息反馈 - 点赞/点踩按钮（本地存储，待后端支持）
- 继续生成 - 点击按钮继续助手响应
- 会话置顶/归档 - 置顶重要会话，归档旧会话
- 输入框粘贴图片 - 从剪贴板快速添加图片
- 会话重命名 - 长按菜单重命名会话

**UI/UX 改进**
- 发送状态动画 - 旋转发送中图标
- 输入框提示 - Shift+Enter 换行
- 错误提示改进 - 可操作的错误建议

### 性能优化
- LazyColumn contentType 优化
- StateFlow update {} 原子操作
- derivedStateOf 状态计算

### Bug 修复
- 会话进入滚动到底部
- 状态更新竞态条件修复
- 编译错误修复

### 代码质量
- SessionViewModel 拆分（4 个文件）
- GatewayConnection 提取工具函数
- MarkdownParser 独立文件
- 添加单元测试

## [1.0.0] - 2026-03-30

首个正式发布版本！包含所有核心功能和最新优化。

### 核心功能

- **消息收发** - 与 OpenClaw Gateway 实时通信，支持文本、图片、代码块
- **会话管理** - 多会话切换、搜索、删除
- **Markdown 渲染** - 代码语法高亮、表格、引用、链接可点击
- **代码块复制** - 一键复制代码内容
- **图片查看** - 全屏查看、缩放、手势支持
- **TTS 朗读** - 消息语音朗读
- **国际化** - 中英文支持 (123+ 字符串资源)
- **消息时间戳** - 相对时间（刚刚、X分钟前）和绝对时间
- **会话日期分组** - 今天、昨天、本周、更早
- **消息删除确认** - 防止误删
- **会话搜索** - 按关键词过滤
- **消息重试** - 失败消息可重新发送

### 连接方式

- **Token 模式** - 使用 `OPENCLAW_GATEWAY_TOKEN` 远程连接，与 webchat 体验一致
- **配对模式** - Ed25519 签名认证，管理员批准后连接
- **首次使用引导页** - 简洁的单页面配置流程
- **Token 自动保存** - 连接成功后自动保存，下次无需重新输入

### 界面特性

- **键盘适配** - 输入法弹出时消息列表同步上移（adjustPan）
- **新消息按钮** - 快速滚动到最新消息
- **深色模式** - 完整的 Material 3 深色主题支持
- **流式光标动画** - 打字机效果
- **工具卡片展开动画** - 平滑过渡
- **消息项动画** - 入场动画
- **触觉反馈** - 发送按钮振动反馈
- **工具卡片尺寸优化** - 更紧凑的工具调用展示

### 流式响应优化

- **reverseLayout=true** - 最优雅的流式滚动方案，无需手动滚动逻辑
- **工具卡片流式更新** - 正确追加 stream 内容而非替换
- **进入会话自动滚动** - 自动跳转到最后一条消息
- **循环滚动修复** - 防止流式响应期间的滚动循环问题

### 性能优化

- **@Stable 注解** - MessageGroup、ToolCard、StreamSegment、ToolStreamEntry
- **图片解码优化** - 采样率计算，限制最大尺寸，减少内存占用
- **流式滚动优化** - 移除高频滚动逻辑，使用 LazyColumn 原生能力

### Bug 修复

- **连接超时状态** - 正确更新为 Error 状态而非卡在 Connecting
- **工具卡片流式** - stream 字段正确追加而非替换
- **重复字符串资源** - 移除 app_name、about_app_name 等重复定义
- **DebugViewModel crash** - 修复 NullPointerException，正确注入 ApplicationContext
- **编译错误修复** - 修复 avatar 移除后的残留引用

### 技术细节

- WebSocket 连接添加 Origin header，满足 Gateway origin 检查
- `client.id` 使用 `openclaw-control-ui`，Gateway 正确识别为 Control UI 客户端
- 从 URL 自动提取 origin（支持自定义端口）

### 技术栈

- Jetpack Compose + Material Design 3
- Hilt 依赖注入
- Room 数据库
- OkHttp WebSocket
- Kotlin Coroutines + Flow

---

## 约定

### 版本格式

- **主版本号** - 不兼容的 API 变更
- **次版本号** - 向后兼容的新功能
- **修订号** - 向后兼容的问题修复

---

*最后更新：2026-03-30*