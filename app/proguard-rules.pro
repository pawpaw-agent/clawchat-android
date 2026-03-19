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
# Tink / Error Prone Annotations (optional, for release builds)
# ─────────────────────────────────────────────────────────────

# These are compile-time only annotations, safe to ignore at runtime
-dontwarn com.google.errorprone.annotations.**
-dontnote com.google.errorprone.annotations.**
-keep class com.google.errorprone.annotations.** { *; }

# Google API client (used by Tink for key download, optional)
-dontwarn com.google.api.client.**
-dontnote com.google.api.client.**
-keep class com.google.api.client.** { *; }

# Joda Time (used by Tink for time operations, optional)
-dontwarn org.joda.time.**
-dontnote org.joda.time.**
-keep class org.joda.time.** { *; }

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

# ─────────────────────────────────────────────────────────────
# Kotlin Serialization (complete rules)
# ─────────────────────────────────────────────────────────────

-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# Keep serializer() companion methods
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep @Serializable classes and their generated $serializer companions
-keep,includedescriptorclasses class com.openclaw.clawchat.**$$serializer { *; }
-keepclassmembers class com.openclaw.clawchat.** {
    *** Companion;
}
-keepclasseswithmembers class com.openclaw.clawchat.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep @SerialName annotations (polymorphic dispatch depends on them)
-keepattributes RuntimeVisibleAnnotations
-keep class kotlinx.serialization.SerialName

# Keep all @Serializable-annotated classes from being renamed
-if @kotlinx.serialization.Serializable class **
-keep class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

# Also cover com.clawchat.android package (used in network layer)
-keep,includedescriptorclasses class com.clawchat.android.**$$serializer { *; }
-keepclassmembers class com.clawchat.android.** {
    *** Companion;
}
-keepclasseswithmembers class com.clawchat.android.** {
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

# ─────────────────────────────────────────────────────────────
# Hilt / Dagger (Dependency Injection)
# ─────────────────────────────────────────────────────────────

-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ComponentSupplier { *; }
-keep class * extends dagger.hilt.android.internal.lifecycle.HiltViewModelFactory { *; }

# Keep Hilt generated classes
-keep class **$$ModuleAdapter { *; }
-keep class **$$InjectAdapter { *; }
-keep class **$$StaticAdapter { *; }

# Keep @Inject annotated constructors (Dagger needs reflection on these)
-keepclasseswithmembernames class * {
    @javax.inject.Inject <init>(...);
}

# Keep @HiltViewModel annotated classes
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }

# Keep @Module and @InstallIn annotated classes
-keep @dagger.Module class * { *; }
-keep @dagger.hilt.InstallIn class * { *; }

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

# ─────────────────────────────────────────────────────────────
# Security & Keystore
# ─────────────────────────────────────────────────────────────

# Keep all security module classes (both package variants)
-keep class com.openclaw.clawchat.security.** { *; }
-keep class com.clawchat.android.security.** { *; }
-keepclassmembers class com.openclaw.clawchat.security.** { *; }
-keepclassmembers class com.clawchat.android.security.** { *; }

# Keep KeystoreManager (uses reflection for Android Keystore)
-keepnames class com.openclaw.clawchat.security.KeystoreManager
-keepnames class com.openclaw.clawchat.security.SecurityModule

# BouncyCastle Ed25519 (software key fallback for API < 33)
-keep class org.bouncycastle.jcajce.provider.asymmetric.edec.** { *; }
-keep class org.bouncycastle.jcajce.provider.asymmetric.ec.** { *; }
-keep class org.bouncycastle.crypto.signers.Ed25519Signer { *; }
-keep class org.bouncycastle.crypto.params.Ed25519** { *; }
-keep class org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator { *; }
-keep class org.bouncycastle.jce.provider.BouncyCastleProvider { *; }
-dontwarn org.bouncycastle.**

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
