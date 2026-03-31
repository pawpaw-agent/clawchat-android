# ClawChat v1.2.0 Release Notes

## 🎉 发布日期：2026-04-01

### 重大更新

**ClawChat v1.2.0** 是一个重大版本更新，包含 **50+ 新功能**、**15+ 新组件**、**134+ 测试用例**。

---

## ✨ 新功能

### 核心功能

#### 🔲 二维码生成
- **QRCodeUtils** - 使用 ZXing 库生成二维码
- 支持自定义尺寸和边距
- 会话分享二维码

#### 🎤 语音输入
- **VoiceInputManager** - Android SpeechRecognizer 封装
- 支持多语言（默认中文）
- 实时语音识别

#### 📝 提示词存储
- **RecentPromptsStore** - DataStore 持久化
- 最多保存 20 条最近提示词
- 快速填充功能

#### 💾 草稿自动保存
- **DraftStore** - 自动保存未发送消息
- 24 小时有效期
- 会话切换自动恢复

#### 📤 消息导出
- **ExportUtils** - 支持 JSON/Text/Image 格式
- 会话完整导出
- FileProvider 安全分享

#### 📊 消息统计
- **StatsUtils** - 字数统计
- 智能计数（中文=1，英文单词=1）
- 会话统计报告

---

### UI 组件

#### 🎨 高级组件

| 组件 | 描述 |
|------|------|
| `AnimatedFloatingActionButton` | 弹簧动画 FAB |
| `ProgressButton` | 发送进度按钮 |
| `ExpandableCard` | 可展开卡片 |
| `SwipeableMessageCard` | 滑动删除消息 |
| `BatchOperationToolbar` | 批量操作工具栏 |
| `QuotedMessageBar` | 引用消息组件 |
| `UndoSnackbar` | 撤销删除 Snackbar |
| `ThemeColorSettingItem` | 主题色选择器 |

#### 🎬 动画系统

**AnimationUtils** - 统一动画预设
- `pageTransition()` - 页面切换 crossfade
- `listItemEnter()` - 列表项 fadeIn + slideIn
- `messageSendEnter()` - 消息发送 scaleIn
- `messageDeleteExit()` - 消息删除 fadeOut + slideOut

---

### 响应式布局

**LayoutUtils** - WindowSizeClass 支持

| 类别 | 尺寸范围 | 布局 |
|------|---------|------|
| COMPACT | < 600dp | 单栏 |
| MEDIUM | 600-840dp | 双栏 |
| EXPANDED | > 840dp | 三栏 |

- 折叠屏适配（FoldingFeature）
- 横屏优化
- 大屏幕支持

---

### 交互增强

#### 👆 手势操作
- **滑动删除会话** - SwipeToDismiss
- **长按弹出菜单** - 复制/分享/删除
- **批量操作** - 多选删除/复制

#### ⌨️ 键盘快捷键

| 快捷键 | 功能 |
|--------|------|
| Ctrl+N | 新建会话 |
| Ctrl+F | 打开搜索 |
| Ctrl+Z | 撤销删除 |
| Ctrl+S | 保存草稿 |

#### 💬 引用回复
- **QuotedMessageBar** - 引用消息预览
- 快速引用回复
- 引用内容显示

---

### 主题系统

#### 🎨 主题色选择

8 种预设颜色：
- 🔵 Blue（默认）
- 🟣 Purple
- 🌊 Teal
- 🟠 Orange
- 💗 Pink
- 🟢 Green
- 🔴 Red
- 💙 Indigo

#### 📏 字体大小

三档可调：
- SMALL
- MEDIUM（默认）
- LARGE

---

## 🔧 技术改进

### 内存管理

- **onTrimMemory** 回调实现
- 根据 system 压力释放资源
- TRIM_MEMORY_UI_HIDDEN/_MODERATE/RUNNING_CRITICAL

### 网络优化

- 30 秒超时配置
- 心跳机制
- 自动重连（指数退避）

### 测试覆盖

- **134+ 测试用例**
- **15+ 测试工具**
- **测试覆盖率目标 80%+**

---

## 📦 依赖更新

| 库 | 版本 |
|----|------|
| ZXing Core | 3.5.2 |
| ZXing Android Embedded | 4.3.0 |
| DataStore Preferences | 1.0.0 |

---

## 🔐 权限新增

- `RECORD_AUDIO` - 语音输入

---

## 📊 统计

| 指标 | 数量 |
|------|------|
| 总任务 | 607 |
| 新增文件 | 30+ |
| 代码行数 | 25,000+ |
| 测试用例 | 134+ |
| 测试工具 | 15+ |

---

## 🚀 升级指南

从 v1.1.0 升级到 v1.2.0：

1. 更新依赖：`./gradlew --refresh-dependencies`
2. 清理构建：`./gradlew clean`
3. 重新构建：`./gradlew assembleDebug`

---

## 🙏 致谢

感谢所有贡献者和用户的支持！

---

**ClawChat Team**  
2026-04-01