# ClawChat Android 设计系统

> 基于 WebChat 设计语言提取，用于 Android 端统一视觉规范
> 提取时间：2026-03-22
> 源文件：/tmp/openclaw-src/ui/src/styles/base.css

---

## 1. 颜色系统

### 1.1 深色主题 (默认)

| 语义名称 | HEX 值 | 用途 |
|---------|--------|------|
| **背景层** |||
| bg | `#0e1015` | 主背景 |
| bg-accent | `#13151b` | 次级背景 |
| bg-elevated | `#191c24` | 浮层背景 |
| bg-hover | `#1f2330` | 悬停状态 |
| **卡片/表面** |||
| card | `#161920` | 卡片背景 |
| popover | `#191c24` | 弹出菜单 |
| panel | `#0e1015` | 面板背景 |
| **文字** |||
| text | `#d4d4d8` | 正文文字 |
| text-strong | `#f4f4f5` | 强调文字 |
| muted | `#838387` | 次要文字 |
| **边框** |||
| border | `#1e2028` | 默认边框 |
| border-strong | `#2e3040` | 强调边框 |
| border-hover | `#3e4050` | 悬停边框 |

### 1.2 强调色

| 语义名称 | HEX 值 | 用途 |
|---------|--------|------|
| **主强调色** |||
| accent | `#ff5c5c` | 主按钮、链接、重点 |
| accent-hover | `#ff7070` | 悬停状态 |
| accent-subtle | `rgba(255, 92, 92, 0.1)` | 强调背景 |
| accent-glow | `rgba(255, 92, 92, 0.2)` | 光晕效果 |
| **次强调色** |||
| accent-2 | `#14b8a6` | 次要强调 (青色) |

### 1.3 语义色

| 语义 | HEX 值 | subtle 背景 | 用途 |
|------|--------|------------|------|
| **成功 (ok)** | `#22c55e` | `rgba(34, 197, 94, 0.08)` | 成功状态、在线指示 |
| **警告 (warn)** | `#f59e0b` | `rgba(245, 158, 11, 0.08)` | 警告提示 |
| **错误 (danger)** | `#ef4444` | `rgba(239, 68, 68, 0.08)` | 错误、删除 |
| **信息 (info)** | `#3b82f6` | `rgba(59, 130, 246, 0.08)` | 信息提示 |

### 1.4 亮色主题

| 语义名称 | HEX 值 |
|---------|--------|
| bg | `#f8f9fa` |
| card | `#ffffff` |
| text | `#3c3c43` |
| border | `#e5e5ea` |
| accent | `#dc2626` |

### 1.5 主题变体

WebChat 支持多主题变体，Android 应至少支持：

1. **openclaw** (默认) - 红色强调 `#ff5c5c`
2. **openknot** - 蓝色强调 `#4f8ff7`
3. **dash** - 琥珀色强调 `#d4915c`，可可色调背景

---

## 2. 排版系统

### 2.1 字体族

```
正文字体: Inter, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif
等宽字体: "JetBrains Mono", ui-monospace, SFMono-Regular, "SF Mono", Menlo, Monaco, Consolas, monospace
```

### 2.2 字号层级

| 级别 | 大小 | 行高 | 字重 | 用途 |
|------|------|------|------|------|
| xs | 11px | 1.4 | 500 | 标签、时间戳 |
| sm | 12px | 1.45 | 500 | 次要文字、标签 |
| base | 13.5px | 1.55 | 400 | 正文 |
| md | 14px | 1.5 | 400 | 聊天文字 |
| lg | 15px | 1.5 | 600 | 卡片标题 |
| xl | 22px | 1.15 | 700 | 大标题 |
| stat | 24px | 1.1 | 700 | 统计数字 |

### 2.3 字重

| 名称 | 值 | 用途 |
|------|-----|------|
| regular | 400 | 正文 |
| medium | 500 | 标签、按钮 |
| semibold | 600 | 标题、强调 |
| bold | 700 | 大标题、统计 |

### 2.4 字间距

```css
letter-spacing: -0.01em;  /* 全局 */
letter-spacing: -0.02em;  /* 标题 */
letter-spacing: 0.04em;   /* 大写标签 */
```

---

## 3. 间距系统

### 3.1 圆角

| 名称 | 值 | 用途 |
|------|-----|------|
| radius-sm | 6px | 小元素、标签 |
| radius-md | 10px | 按钮、输入框、卡片内元素 |
| radius-lg | 14px | 卡片、模态框 |
| radius-xl | 20px | 大卡片 |
| radius-full | 9999px | 圆形、药丸按钮 |

### 3.2 内边距参考

| 元素 | 内边距 |
|------|--------|
| 按钮 (默认) | `8px 14px` |
| 按钮 (小) | `6px 10px` |
| 卡片 | `18px` |
| 输入框 | `8px 12px` |
| 聊天气泡 | `10px 14px` |
| 标签/药丸 | `5px 11px` |

### 3.3 外边距/间距

```
基础间距单位: 4px
常用间距: 4px, 8px, 12px, 14px, 16px, 18px, 24px
组件间隙: 8px (列表项), 12px (表单字段), 16px (区块)
```

---

## 4. 组件规范

### 4.1 按钮

#### 主按钮 (Primary)
```css
background: var(--accent);           /* #ff5c5c */
color: #ffffff;
border: 1px solid var(--accent);
border-radius: 10px;
padding: 8px 14px;
font-size: 13px;
font-weight: 500;
box-shadow: 0 1px 3px rgba(255, 92, 92, 0.25);
```

**悬停状态：**
```css
background: var(--accent-hover);
box-shadow: 0 2px 12px rgba(255, 92, 92, 0.3);
```

#### 次要按钮
```css
background: var(--bg-elevated);
color: var(--text);
border: 1px solid var(--border);
```

#### 图标按钮
```css
width: 36px;
height: 36px;
padding: 8px;
border-radius: 10px;
background: rgba(255, 255, 255, 0.06);
```

#### 危险按钮
```css
background: var(--danger-subtle);
color: var(--danger);
border: transparent;
```

### 4.2 卡片

```css
background: var(--card);
border: 1px solid var(--border);
border-radius: 14px;
padding: 18px;
```

**悬停状态：**
```css
border-color: var(--border-strong);
box-shadow: 0 1px 2px rgba(0, 0, 0, 0.25);
```

### 4.3 输入框

```css
background: var(--card);
border: 1px solid var(--input);
border-radius: 10px;
padding: 8px 12px;
font-size: 14px;
box-shadow: inset 0 1px 0 var(--card-highlight);
```

**聚焦状态：**
```css
border-color: var(--ring);
box-shadow: 0 0 0 2px var(--bg), 0 0 0 3px color-mix(in srgb, var(--ring) 60%, transparent);
```

### 4.4 聊天消息气泡

#### 用户消息
```css
/* 深色主题 */
background: var(--accent-subtle);    /* rgba(255, 92, 92, 0.1) */
border: transparent;
border-radius: 14px;
padding: 10px 14px;
max-width: 82%;

/* 亮色主题 */
background: rgba(251, 146, 60, 0.12);
border: 1px solid rgba(234, 88, 12, 0.2);
```

#### 助手消息
```css
/* 深色主题 */
background: var(--secondary);        /* #161920 */
border: transparent;

/* 亮色主题 */
background: var(--bg-muted);
border: 1px solid var(--border);
```

#### 流式输出状态
```css
animation: chatStreamPulse 1.5s ease-in-out infinite;

@keyframes chatStreamPulse {
  0%, 100% { border-color: var(--border); }
  50% { border-color: var(--accent); }
}
```

### 4.5 标签/药丸

```css
background: var(--secondary);
border: 1px solid var(--border);
border-radius: 9999px;
padding: 5px 11px;
font-size: 12px;
font-weight: 500;
```

#### 状态标签
```css
/* 成功 */
.chip-ok { color: var(--ok); border-color: rgba(34, 197, 94, 0.3); background: var(--ok-subtle); }

/* 警告 */
.chip-warn { color: var(--warn); border-color: rgba(245, 158, 11, 0.3); background: var(--warn-subtle); }

/* 错误 */
.chip-danger { color: var(--danger); border-color: rgba(239, 68, 68, 0.3); background: var(--danger-subtle); }
```

### 4.6 状态指示点

```css
width: 8px;
height: 8px;
border-radius: 50%;
background: var(--danger);
box-shadow: 0 0 8px rgba(239, 68, 68, 0.5);
animation: pulse-subtle 2s ease-in-out infinite;
```

**在线状态：**
```css
background: var(--ok);
box-shadow: 0 0 8px rgba(34, 197, 94, 0.5);
animation: none;
```

---

## 5. 阴影系统

### 5.1 深色主题阴影

| 名称 | 值 | 用途 |
|------|-----|------|
| shadow-sm | `0 1px 2px rgba(0, 0, 0, 0.25)` | 轻微提升 |
| shadow-md | `0 4px 16px rgba(0, 0, 0, 0.3)` | 卡片、下拉 |
| shadow-lg | `0 12px 32px rgba(0, 0, 0, 0.4)` | 模态框 |
| shadow-xl | `0 24px 48px rgba(0, 0, 0, 0.5)` | 大模态框 |
| shadow-glow | `0 0 24px var(--accent-glow)` | 强调光晕 |

### 5.2 亮色主题阴影

```css
--shadow-sm: 0 1px 2px rgba(0, 0, 0, 0.04);
--shadow-md: 0 4px 12px rgba(0, 0, 0, 0.06);
--shadow-lg: 0 12px 28px rgba(0, 0, 0, 0.08);
--shadow-xl: 0 24px 48px rgba(0, 0, 0, 0.1);
```

---

## 6. 动效规范

### 6.1 缓动曲线

| 名称 | 值 | 用途 |
|------|-----|------|
| ease-out | `cubic-bezier(0.16, 1, 0.3, 1)` | 淡入、滑入 |
| ease-in-out | `cubic-bezier(0.4, 0, 0.2, 1)` | 状态切换 |
| ease-spring | `cubic-bezier(0.34, 1.56, 0.64, 1)` | 弹性动画 |

### 6.2 时长

| 名称 | 值 | 用途 |
|------|-----|------|
| duration-fast | 100ms | 悬停、颜色变化 |
| duration-normal | 180ms | 状态过渡、展开 |
| duration-slow | 300ms | 页面切换、大动画 |

### 6.3 常用动画

#### 淡入上升
```css
@keyframes rise {
  from { opacity: 0; transform: translateY(8px); }
  to { opacity: 1; transform: translateY(0); }
}
```

#### 缩放淡入
```css
@keyframes scale-in {
  from { opacity: 0; transform: scale(0.95); }
  to { opacity: 1; transform: scale(1); }
}
```

#### 交错延迟
```css
.stagger-1 { animation-delay: 0ms; }
.stagger-2 { animation-delay: 50ms; }
.stagger-3 { animation-delay: 100ms; }
.stagger-4 { animation-delay: 150ms; }
.stagger-5 { animation-delay: 200ms; }
```

### 6.4 减弱动效

```css
@media (prefers-reduced-motion: reduce) {
  * { animation-duration: 0.01ms !important; }
}
```

---

## 7. 响应式断点

参考 WebChat 布局：

| 断点 | 宽度 | 用途 |
|------|------|------|
| mobile | < 600px | 单列布局 |
| tablet | 600px - 900px | 双列布局 |
| desktop | > 900px | 完整布局 |
| wide | > 1100px | 扩展布局 |

---

## 8. Android 实现建议

### 8.1 颜色资源 (colors.xml)

```xml
<!-- 深色主题 -->
<color name="bg">#0e1015</color>
<color name="bg_elevated">#191c24</color>
<color name="card">#161920</color>
<color name="text">#d4d4d8</color>
<color name="text_strong">#f4f4f5</color>
<color name="muted">#838387</color>
<color name="border">#1e2028</color>
<color name="border_strong">#2e3040</color>

<!-- 强调色 -->
<color name="accent">#ff5c5c</color>
<color name="accent_hover">#ff7070</color>
<color name="accent_subtle">#1aff5c5c</color>

<!-- 语义色 -->
<color name="success">#22c55e</color>
<color name="warning">#f59e0b</color>
<color name="error">#ef4444</color>
<color name="info">#3b82f6</color>
```

### 8.2 尺寸资源 (dimens.xml)

```xml
<!-- 圆角 -->
<dimen name="radius_sm">6dp</dimen>
<dimen name="radius_md">10dp</dimen>
<dimen name="radius_lg">14dp</dimen>
<dimen name="radius_xl">20dp</dimen>

<!-- 间距 -->
<dimen name="spacing_xs">4dp</dimen>
<dimen name="spacing_sm">8dp</dimen>
<dimen name="spacing_md">12dp</dimen>
<dimen name="spacing_lg">16dp</dimen>
<dimen name="spacing_xl">24dp</dimen>

<!-- 字号 -->
<dimen name="font_xs">11sp</dimen>
<dimen name="font_sm">12sp</dimen>
<dimen name="font_base">13.5sp</dimen>
<dimen name="font_md">14sp</dimen>
<dimen name="font_lg">15sp</dimen>
<dimen name="font_xl">22sp</dimen>
```

### 8.3 动画时长 (integers.xml)

```xml
<integer name="anim_duration_fast">100</integer>
<integer name="anim_duration_normal">180</integer>
<integer name="anim_duration_slow">300</integer>
```

---

## 9. 设计原则

1. **深度层次** - 通过背景色深浅区分层级，非阴影
2. **微妙边框** - 边框几乎不可见，仅在悬停时加深
3. **流畅动效** - 使用 ease-out 曲线，避免生硬
4. **语义清晰** - 颜色与状态严格对应
5. **主题支持** - 深色优先，亮色适配

---

*文档由 UI Designer Agent 生成*