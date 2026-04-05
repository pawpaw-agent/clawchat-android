# Firebase 集成指南

## 概述

ClawChat 支持 Firebase 集成，提供以下功能：
- **Crashlytics** - 崩溃报告和分析
- **Analytics** - 用户行为分析
- **Performance** - 性能监控

## 设置步骤

### 1. 创建 Firebase 项目

1. 访问 [Firebase Console](https://console.firebase.google.com/)
2. 点击"添加项目"
3. 输入项目名称（如：ClawChat）
4. 按照向导完成创建

### 2. 添加 Android 应用

1. 在 Firebase Console 中，点击"添加应用" → Android
2. 输入包名：`com.openclaw.clawchat`
3. 输入应用昵称：ClawChat
4. 下载 `google-services.json` 文件

### 3. 配置项目

1. 将 `google-services.json` 放到 `app/` 目录下

2. 取消 `app/build.gradle.kts` 中的 Firebase 注释：

```kotlin
plugins {
    // ...
    id("com.google.gms.google-services")
}

dependencies {
    // ...
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-crashlytics")
    implementation("com.google.firebase:firebase-perf")
}
```

3. 在 `gradle/libs.versions.toml` 中添加插件：

```toml
[plugins]
# ...
google-services = { id = "com.google.gms.google-services", version = "4.4.2" }
firebase-crashlytics = { id = "com.google.firebase.crashlytics", version = "3.0.2" }
firebase-perf = { id = "com.google.firebase.firebase-perf", version = "1.4.2" }
```

### 4. 初始化 Firebase

在 `ClawChatApplication.kt` 中添加：

```kotlin
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.perf.FirebasePerformance

@HiltAndroidApp
class ClawChatApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // 初始化 CrashHandler
        CrashHandler.init(this)
        
        // Firebase 初始化（自动，但可以配置）
        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(!BuildConfig.DEBUG)
        FirebasePerformance.getInstance().isPerformanceCollectionEnabled = !BuildConfig.DEBUG
    }
}
```

## 功能说明

### Crashlytics

自动收集崩溃报告，包括：
- 崩溃堆栈跟踪
- 设备信息
- 自定义键值对
- 非致命错误日志

### Analytics

自动收集以下事件：
- 应用启动
- 屏幕浏览
- 用户参与度

可手动记录自定义事件：

```kotlin
FirebaseAnalytics.getInstance(context).logEvent("session_created", bundleOf(
    "model" to modelName
))
```

### Performance

自动监控：
- 应用启动时间
- 网络请求
- 屏幕渲染

可手动添加性能追踪：

```kotlin
val trace = FirebasePerformance.getInstance().newTrace("message_send")
trace.start()
// ... 发送消息
trace.stop()
```

## 注意事项

1. **隐私合规**：确保在隐私政策中披露数据收集行为
2. **Debug 构建**：建议在 Debug 构建中禁用数据收集
3. **ProGuard**：Firebase 已配置 ProGuard 规则，无需额外配置

## 故障排除

### google-services.json 未找到

错误：`File google-services.json is missing`

解决：确保 `google-services.json` 位于 `app/` 目录下

### 插件未找到

错误：`Plugin with id 'com.google.gms.google-services' not found`

解决：检查 `settings.gradle.kts` 和 `libs.versions.toml` 配置

### 崩溃报告未显示

1. 检查 Crashlytics 是否初始化
2. 等待几分钟让报告上传
3. 在 Firebase Console 中查看

## 相关链接

- [Firebase 官方文档](https://firebase.google.com/docs/android/setup)
- [Crashlytics 文档](https://firebase.google.com/docs/crashlytics)
- [Analytics 文档](https://firebase.google.com/docs/analytics)
- [Performance 文档](https://firebase.google.com/docs/perf-mon)