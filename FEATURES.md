# ClawChat Features (v1.2.0)

## 核心功能

### 消息通信
- **文本消息** - 发送和接收文本消息，支持 Markdown 格式
- **图片消息** - 发送图片附件，支持全屏查看
- **流式响应** - AI 响实时流式显示
- **继续生成** - 点击按钮继续 AI 响应

### 会话管理
- **多会话切换** - 创建和管理多个会话
- **会话搜索** - 按关键词过滤会话
- **会话置顶** - 置顶重要会话
- **会话归档** - 归档旧会话
- **会话重命名** - 长按菜单重命名
- **滑动删除** - 左滑快速删除会话
- **批量删除** - 多选批量删除会话

### 消息操作
- **消息删除** - 确认对话框防止误删
- **消息复制** - 复制消息文本
- **消息分享** - 分享消息内容
- **消息引用** - 引用消息快速回复
- **批量操作** - 多选批量删除/复制消息
- **撤销删除** - 5秒内可撤销删除

## 新增功能（v1.2.0）

### 二维码功能
- **二维码生成** - QRCodeUtils 工具类
- **ZXing 库** - Core 3.5.2 + Android Embedded 4.3.0
- **会话分享** - 生成会话分享二维码

### 语音功能
- **语音输入** - VoiceInputManager 语音识别
- **消息朗读** - MessageSpeaker TTS 朗读
- **多语言支持** - 默认中文，可配置其他语言

### 提示词功能
- **最近提示词** - RecentPromptsStore 存储
- **DataStore 持久化** - 最多保存 20 条
- **快速填充** - 点击即可使用

### 导出功能
- **JSON 导出** - exportMessagesToJson()
- **文本导出** - exportMessagesToText()
- **图片导出** - exportMessagesToBitmap()
- **会话备份** - 完整会话导出

### 统计功能
- **字数统计** - countCharacters()
- **会话统计** - MessagesStats 数据类
- **中文/英文智能计数** - 中文字符=1，英文单词=1

### 草稿功能
- **自动保存** - DraftStore 自动保存未发送消息
- **24小时有效期** - 过期自动清除
- **草稿恢复** - 切换会话时恢复草稿

## UI 组件

### 高级组件
- **AnimatedFloatingActionButton** - 弹簧动画 FAB
- **ProgressButton** - 发送进度按钮
- **ExpandableCard** - 可展开卡片
- **SwipeableMessageCard** - 滑动删除消息

### 批量操作
- **BatchOperationToolbar** - 批量操作工具栏
- **SelectionState** - 选择状态管理
- **全选/取消** - 一键操作

### 引用回复
- **QuotedMessageBar** - 引用消息显示
- **MessageQuoteReference** - 引用内容预览
- **快速跳转** - 点击引用跳转到原消息

### 撤销功能
- **UndoSnackbar** - 撤销 Snackbar
- **UndoQueue** - 删除队列管理
- **5秒超时** - 自动消失

## 主题系统

### 主题色选择
- **8 种预设颜色** - Blue/Purple/Teal/Orange/Pink/Green/Red/Indigo
- **ColorUtils** - 颜色工具类
- **ThemePreferencesStore** - DataStore 持久化

### 主题模式
- **跟随系统** - 自动跟随系统深色模式
- **浅色模式** - 强制浅色
- **深色模式** - 强制深色

### 字体大小
- **SMALL/MEDIUM/LARGE** - 三档字体大小
- **全局应用** - 所有消息统一调整

## 响应式布局

### WindowSizeClass
- **COMPACT** - < 600dp（手机）
- **MEDIUM** - 600-840dp（手机横屏/小平板）
- **EXPANDED** - > 840dp（大平板）

### 折叠屏适配
- **FoldingFeature** - 折叠屏状态检测
- **动态布局** - 根据折叠状态调整
- **横屏优化** - 双栏显示

### 大屏幕支持
- **三栏布局** - 会话列表 + 消息 + 预览
- **消息预览** - 大屏幕消息预览面板

## 动画系统

### 入场动画
- **pageTransition()** - 页面切换 crossfade
- **listItemEnter()** - 列表项 fadeIn + slideIn
- **messageSendEnter()** - 消息发送 scaleIn

### 出场动画
- **messageDeleteExit()** - 消息删除 fadeOut + slideOut
- **cardCollapse()** - 卡片收起动画

### 特殊动画
- **toastEnter/Exit()** - Toast 提示动画
- **dialogEnter/Exit()** - 对话框动画

## 键盘快捷键

### 全局快捷键
- **Ctrl+N** - 新建会话
- **Ctrl+F** - 打开搜索
- **Ctrl+Z** - 撤销删除
- **Ctrl+S** - 保存草稿

### 输入快捷键
- **Enter** - 发送消息
- **Shift+Enter** - 换行

## 拖拽排序

### 会话拖拽
- **ReorderableList** - 拖拽排序工具
- **DragState** - 拖拽状态管理
- **触觉反馈** - 拖拽时触觉反馈

### 消息拖拽
- **手势检测** - detectDragGesturesAfterLongPress
- **位置计算** - calculateNewPosition()
- **吸附效果** - 拖拽结束吸附到位置

## 安全与权限

### 网络安全
- **HTTPS** - 强制 HTTPS 连接
- **证书验证** - 系统证书信任
- **明文限制** - 仅 Gateway 允许 ws://

### 数据安全
- **EncryptedSharedPreferences** - 加密存储敏感数据
- **SQLCipher** - 可选数据库加密
- **DataStore** - 偏好设置安全存储

### 权限使用
- **INTERNET** - 网络连接
- **RECORD_AUDIO** - 语音输入
- **POST_NOTIFICATIONS** - 消息通知
- **最小权限原则** - 仅请求必要权限

## 国际化

### 支持语言
- **中文** - 简体中文 (zh-CN)
- **英文** - English (en-US)

### 字符串资源
- **184+ 字符串** - 完整国际化覆盖
- **动态切换** - 语言实时切换

---

**版本**: v1.2.0  
**发布日期**: 2026-04-01  
**总功能数**: 50+