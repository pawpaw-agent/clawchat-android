import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    id("jacoco")
}

// ─────────────────────────────────────────────────────────────
// Release Signing Configuration (Secure)
// ─────────────────────────────────────────────────────────────
// Reads signing config from:
// 1. Environment variables (CI/CD priority)
// 2. keystore.properties file (local development, excluded from git)
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()

if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(keystorePropertiesFile.inputStream())
}

val releaseKeystorePath = System.getenv("KEYSTORE_PATH")
    ?: keystoreProperties.getProperty("storeFile")
val releaseKeystorePassword = System.getenv("KEYSTORE_PASSWORD")
    ?: keystoreProperties.getProperty("storePassword")
val releaseKeyAlias = System.getenv("KEY_ALIAS")
    ?: keystoreProperties.getProperty("keyAlias")
val releaseKeyPassword = System.getenv("KEY_PASSWORD")
    ?: keystoreProperties.getProperty("keyPassword")

val hasReleaseSigningConfig = releaseKeystorePath != null &&
    releaseKeystorePassword != null &&
    releaseKeyAlias != null &&
    releaseKeyPassword != null

android {
    namespace = "com.openclaw.clawchat"
    compileSdk = 35

    signingConfigs {
        getByName("debug") {
            // 固定 debug keystore，确保 CI 和本地构建签名一致，支持覆盖安装
            storeFile = rootProject.file("keystores/ci-debug.keystore")
            storePassword = "clawchat-debug"
            keyAlias = "clawchat-debug"
            keyPassword = "clawchat-debug"
        }
        
        if (hasReleaseSigningConfig) {
            create("release") {
                storeFile = file(releaseKeystorePath!!)
                storePassword = releaseKeystorePassword!!
                keyAlias = releaseKeyAlias!!
                keyPassword = releaseKeyPassword!!
            }
        }
    }

    defaultConfig {
        applicationId = "com.openclaw.clawchat"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "1.0.4"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // Enable multidex for testing
        multiDexEnabled = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasReleaseSigningConfig) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"
            enableUnitTestCoverage = true
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
            freeCompilerArgs.addAll(
                "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
                "-opt-in=kotlinx.serialization.ExperimentalSerializationApi"
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/INDEX.LIST"
            excludes += "/META-INF/io.netty.versions.properties"
            excludes += "/META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = false
            isReturnDefaultValues = true
        }
    }
}

// ─────────────────────────────────────────────────────────────
// JaCoCo Configuration for Code Coverage
// ─────────────────────────────────────────────────────────────

jacoco {
    toolVersion = "0.8.12"
}

tasks.withType<Test> {
    configure<JacocoTaskExtension> {
        isIncludeNoLocationClasses = true
        excludes = listOf("jdk.internal.*")
    }
}

tasks.register<JacocoReport>("jacocoTestReport") {
    dependsOn("testDebugUnitTest")

    reports {
        xml.required.set(true)
        xml.outputLocation.set(layout.buildDirectory.file("reports/jacoco/jacocoTestReport/jacocoTestReport.xml"))
        
        html.required.set(true)
        html.outputLocation.set(layout.buildDirectory.dir("reports/jacoco/jacocoTestReport/html"))
        
        csv.required.set(false)
    }

    val fileFilter = listOf(
        // Android
        "**/R.class",
        "**/R\$*.class",
        "**/BuildConfig.*",
        "**/Manifest*.*",
        "**/*Test*.*",
        "android/**/*.*",
        "**/*\$Lambda\$*.*",
        "**/*Companion*.*",
        
        // Compose
        "**/*\$Companion*.*",
        "**/*Module.*",
        
        // Hilt
        "**/Hilt_*.*",
        "**/*_Factory.*",
        "**/*_MembersInjector.*",
        
        // Kotlin
        "**/*\$serializer*.*",
        "**/*\$lambda$*.*",
        
        // Exclude specific packages
        "**/di/*.*",
        "**/ui/theme/*.*",
        "**/ClawChatApplication.*"
    )

    val debugTree = fileTree(layout.buildDirectory.dir("tmp/kotlin-classes/debug")) {
        exclude(fileFilter)
    }

    val mainSrc = "$projectDir/src/main/java"

    sourceDirectories.setFrom(files(mainSrc))
    classDirectories.setFrom(files(debugTree))
    executionData.setFrom(fileTree(layout.buildDirectory) {
        include("outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec")
        include("jacoco/testDebugUnitTest.exec")
    })
}

// ─────────────────────────────────────────────────────────────
// Dependencies
// ─────────────────────────────────────────────────────────────

dependencies {
    // Kotlin
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    // AndroidX Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // Network
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)

    // Security
    implementation(libs.androidx.security.crypto)
    
    // BouncyCastle Ed25519 (software fallback for API < 33)
    implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.78.1")
    
    // JSON (用于协议解析)
    implementation("org.json:json:20231013")

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // Coil (Image Loading)
    implementation(libs.coil.compose)

    // Markdown Rendering
    implementation(libs.markdown.renderer)
    implementation(libs.markdown.renderer.m3)
    implementation(libs.markdown.renderer.code)
    implementation(libs.markdown.renderer.extended)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.androidx.core.testing)
    testImplementation(libs.turbine)
    testImplementation("org.robolectric:robolectric:4.11.1")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
