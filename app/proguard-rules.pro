# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# ─────────────────────────────────────────────────────────────
# Basic Android Settings
# ─────────────────────────────────────────────────────────────

# Keep application class
-keep class com.openclaw.clawchat.ClawChatApplication { *; }

# Keep AndroidX and Google libraries
-keep class androidx.** { *; }
-keep class com.google.** { *; }

# ─────────────────────────────────────────────────────────────
# Kotlin
# ─────────────────────────────────────────────────────────────

-keep class kotlin.** { *; }
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
-keepclassmembers class **$WhenMappings {
    <fields>;
}
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}

# Keep Kotlin Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep data classes for serialization
-keepclassmembers class com.openclaw.clawchat.** {
    *** Companion;
}
-keepclasseswithmembers class com.openclaw.clawchat.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ─────────────────────────────────────────────────────────────
# OkHttp & Networking
# ─────────────────────────────────────────────────────────────

-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-keep class okio.** { *; }
-keep interface okio.** { *; }

# ─────────────────────────────────────────────────────────────
# Hilt (Dependency Injection)
# ─────────────────────────────────────────────────────────────

# Hilt specific rules
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ComponentSupplier { *; }
-keep class * extends dagger.hilt.android.internal.lifecycle.HiltViewModelFactory { *; }

# Keep Hilt generated classes
-keep class **$$ModuleAdapter { *; }
-keep class **$$InjectAdapter { *; }
-keep class **$$StaticAdapter { *; }

# ─────────────────────────────────────────────────────────────
# Room Database
# ─────────────────────────────────────────────────────────────

-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# ─────────────────────────────────────────────────────────────
# Coroutines
# ─────────────────────────────────────────────────────────────

-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# ─────────────────────────────────────────────────────────────
# Compose
# ─────────────────────────────────────────────────────────────

-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# ─────────────────────────────────────────────────────────────
# Coil (Image Loading)
# ─────────────────────────────────────────────────────────────

-dontwarn coil.**
-dontwarn okio.**
-keep class coil.** { *; }

# ─────────────────────────────────────────────────────────────
# Security & Keystore
# ─────────────────────────────────────────────────────────────

-keep class com.openclaw.clawchat.security.** { *; }
-keepclassmembers class com.openclaw.clawchat.security.** {
    *;
}

# ─────────────────────────────────────────────────────────────
# Model Classes (Keep for JSON serialization)
# ─────────────────────────────────────────────────────────────

-keep class com.openclaw.clawchat.data.model.** { *; }
-keep class com.openclaw.clawchat.domain.model.** { *; }

# ─────────────────────────────────────────────────────────────
# Crash Reporting (if using Firebase Crashlytics)
# ─────────────────────────────────────────────────────────────

# -keep class com.google.firebase.crashlytics.** { *; }

# ─────────────────────────────────────────────────────────────
# Logging (Remove in release)
# ─────────────────────────────────────────────────────────────

-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}
