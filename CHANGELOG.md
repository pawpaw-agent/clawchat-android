# Changelog

所有重要的项目变更都将记录在此文件中。

格式基于 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.0.0/)，项目遵循 [Semantic Versioning](https://semver.org/lang/zh-CN/)。

---

## [1.0.0] - 2026-03-28

首个正式发布版本！

### 核心功能

- **消息收发** - 与 OpenClaw Gateway 实时通信，支持文本、图片、代码块
- **会话管理** - 多会话切换、搜索、删除
- **Markdown 渲染** - 代码语法高亮、表格、引用、链接可点击
- **代码块复制** - 一键复制代码内容
- **图片查看** - 全屏查看、缩放、手势支持
- **TTS 朗读** - 消息语音朗读
- **国际化** - 中英文支持
- **消息时间戳** - 相对时间（刚刚、X分钟前）和绝对时间
- **会话日期分组** - 今天、昨天、本周、更早
- **消息删除确认** - 防止误删
- **会话搜索** - 按关键词过滤

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

### 性能优化

- **@Stable 注解** - MessageGroup、ToolCard、StreamSegment、ToolStreamEntry
- **图片解码优化** - 采样率计算，限制最大尺寸，减少内存占用

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

*最后更新：2026-03-28*