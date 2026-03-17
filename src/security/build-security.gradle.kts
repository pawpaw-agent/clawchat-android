// ClawChat Security Module - Gradle 依赖配置
// 将此内容合并到 app/build.gradle.kts 的 dependencies 块中

dependencies {
    // ==================== 安全加密 ====================
    // AndroidX Security Crypto - EncryptedSharedPreferences
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    
    // ==================== 协程（用于 IO 操作） ====================
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    
    // ==================== JSON 处理 ====================
    // 或使用 Kotlinx Serialization（如果项目已配置）
    // implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    
    // ==================== 可选：Room 数据库加密 ====================
    // 如果需要加密消息缓存，添加 SQLCipher
    // implementation("net.zetetic:android-database-sqlcipher:4.5.4")
    // implementation("androidx.room:room-runtime:2.6.1")
}

// ==================== ProGuard 规则 ====================
// 添加到 proguard-rules.pro：

/*
# ClawChat Security Module
-keep class com.openclaw.clawchat.security.** { *; }

# Android Keystore
-keep class android.security.keystore.** { *; }
-keepclassmembers class android.security.keystore.KeyGenParameterSpec$Builder { *; }

# EncryptedSharedPreferences
-keep class androidx.security.crypto.** { *; }
-dontwarn androidx.security.crypto.**

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# JSON (如果使用 org.json 可省略)
-dontwarn org.json.**
*/

// ==================== 编译选项 ====================
// 确保 Java 版本兼容性

android {
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    
    kotlinOptions {
        jvmTarget = "17"
    }
}
