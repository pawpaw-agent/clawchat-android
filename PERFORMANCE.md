# ClawChat Performance Guide

## 性能优化指南

ClawChat 经过系统性的性能优化，确保在各种设备上都能流畅运行。

---

## 启动优化

### 冷启动优化

1. **Splash Screen** - 使用 Android 12+ SplashScreen API
2. **延迟初始化** - 非关键组件延迟加载
3. **Hilt 依赖注入** - 使用 `@Singleton` 避免重复创建

### 启动时间目标

| 设备类型 | 目标时间 |
|---------|---------|
| 高端设备 | < 500ms |
| 中端设备 | < 1000ms |
| 低端设备 | < 1500ms |

---

## 内存优化

### 内存管理策略

1. **onTrimMemory 回调** - 根据系统压力释放资源
   ```kotlin
   override fun onTrimMemory(level: Int) {
       when (level) {
           TRIM_MEMORY_UI_HIDDEN -> MessageSpeaker.stop()
           TRIM_MEMORY_MODERATE -> System.gc()
           TRIM_MEMORY_RUNNING_CRITICAL -> MessageSpeaker.shutdown()
       }
   }
   ```

2. **消息缓存限制** - 每个会话最多缓存 500 条消息
3. **图片采样加载** - BitmapFactory.Options.inSampleSize
4. **StateFlow** - 响应式状态管理，避免不必要的重建

### 内存占用目标

| 场景 | 目标内存 |
|-----|---------|
| 空闲状态 | < 100MB |
| 单会话 | < 150MB |
| 多会话 | < 200MB |

---

## 渲染优化

### Compose 优化

1. **remember** - 缓存计算结果
2. **derivedStateOf** - 减少重组
3. **key** - LazyColumn 项目稳定标识
4. **contentType** - 列表项类型标记

### 动画性能

- 所有动画使用 `spring` 或 `tween` 规格
- 动画时长控制在 150-500ms
- 避免布局动画影响性能

### 帧率目标

| 场景 | 目标帧率 |
|-----|---------|
| 滚动列表 | 60fps |
| 页面切换 | 60fps |
| 动画效果 | 60fps |

---

## 网络优化

### 连接优化

1. **WebSocket 长连接** - 复用连接，减少握手
2. **心跳机制** - 30 秒心跳间隔
3. **自动重连** - 指数退避策略

### 超时配置

```kotlin
OkHttpClient.Builder()
    .connectTimeout(30, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .writeTimeout(30, TimeUnit.SECONDS)
```

### 流式响应

- 支持流式响应，实时显示 AI 输出
- 避免大文本一次性渲染

---

## 存储优化

### DataStore

- 偏好设置使用 DataStore 替代 SharedPreferences
- 异步写入，不阻塞主线程

### 消息存储

- 内存缓存 + 按需加载
- 历史消息不常驻内存

---

## 电池优化

### 后台策略

1. **WebSocket 管理** - 应用在后台时断开连接
2. **WorkManager** - 定时任务使用 WorkManager
3. **前台服务** - 仅在需要时启动

### 网络策略

- 批量请求合并
- 避免频繁轮询

---

## 性能监控

### 推荐工具

1. **Android Profiler** - CPU、内存、网络分析
2. **Systrace** - 系统级性能追踪
3. **LeakCanary** - 内存泄漏检测

### 性能指标

| 指标 | 目标值 | 监控方式 |
|-----|-------|---------|
| 启动时间 | < 1s | Profiler |
| 内存峰值 | < 200MB | Profiler |
| CPU 使用率 | < 30% | Profiler |
| 帧率 | 60fps | GPU Profiler |

---

## 性能最佳实践

### Do ✅

- 使用 `remember` 缓存 Composable 结果
- 使用 `derivedStateOf` 减少重组
- LazyColumn 使用 `key` 和 `contentType`
- 图片使用 Coil 或 BitmapFactory 采样
- 网络请求使用 OkHttp 连接池

### Don't ❌

- 不要在 Composable 中执行耗时操作
- 不要在主线程进行 I/O 操作
- 不要频繁创建大对象
- 不要忽略 `onTrimMemory` 回调
- 不要使用 `GlobalScope`

---

## 性能测试

### Benchmark

```bash
./gradlew :benchmark:benchmarkRelease
```

### 压力测试

- 快速点击测试
- 快速滑动测试
- 网络切换测试
- 内存压力测试

---

**版本**: v1.2.0  
**更新日期**: 2026-04-01