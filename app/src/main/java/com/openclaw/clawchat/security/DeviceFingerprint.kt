package com.openclaw.clawchat.security

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.telephony.TelephonyManager
import android.util.Base64
import android.util.Log
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.UUID

/**
 * DeviceFingerprint - 设备指纹生成器
 * 
 * 负责：
 * - 生成唯一的设备指纹 ID
 * - 基于多硬件标识符组合
 * - 支持 Android 6.0 (API 26+) 
 * - 生成结果稳定（应用重装不变）
 * 
 * 指纹用途：
 * - 设备身份识别（Pairing 流程）
 * - 防重复注册
 * - 设备管理/撤销
 * 
 * 注意：不使用不可重置的硬件 ID（如 IMEI），
 * 尊重用户隐私，遵循 Google 政策。
 */
class DeviceFingerprint(private val context: Context) {
    
    companion object {
        private const val TAG = "DeviceFingerprint"
        
        // 指纹格式版本（用于未来升级）
        private const val FINGERPRINT_VERSION = "v1"
    }
    
    /**
     * 生成设备指纹 ID
     * 
     * 组合多个设备标识符，生成 SHA256 哈希值。
     * 结果稳定、唯一、不可逆。
     * 
     * @return Base64 编码的设备指纹（URL-safe）
     */
    @SuppressLint("HardwareIds")
    fun generateDeviceId(): String {
        val components = mutableListOf<String>()
        
        // 1. Android ID (主要标识符，应用卸载后重置)
        val androidId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        )
        components.add("android_id:$androidId")
        
        // 2. Build 信息（设备型号、厂商等）
        components.add("brand:${Build.BRAND}")
        components.add("model:${Build.MODEL}")
        components.add("manufacturer:${Build.MANUFACTURER}")
        components.add("product:${Build.PRODUCT}")
        
        // 3. 硬件信息（稳定且不可重置）
        components.add("fingerprint:${Build.FINGERPRINT}")
        components.add("hardware:${Build.HARDWARE}")
        components.add("board:${Build.BOARD}")
        
        // 4. 系统版本（区分不同 Android 版本）
        components.add("sdk:${Build.VERSION.SDK_INT}")
        components.add("release:${Build.VERSION.RELEASE}")
        
        // 5. 应用安装 ID（首次安装生成，卸载后重置）
        val installationId = getOrCreateInstallationId()
        components.add("installation:$installationId")
        
        // 6. 可选：SIM 信息（如果有）
        getSimInfo()?.let { simInfo ->
            components.add("sim:$simInfo")
        }
        
        // 生成 SHA256 哈希
        val combined = components.joinToString("|")
        val hash = sha256(combined)
        
        // 添加版本前缀，返回 Base64 URL-safe 编码
        val fingerprint = "$FINGERPRINT_VERSION:${Base64.encodeToString(hash, Base64.URL_SAFE or Base64.NO_WRAP)}"
        
        Log.d(TAG, "Generated device fingerprint: $fingerprint")
        return fingerprint
    }
    
    /**
     * 获取设备信息（用于显示给用户）
     * 
     * @return 人类可读的设备描述
     */
    fun getDeviceDescription(): String {
        return "${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})"
    }
    
    /**
     * 获取设备平台标识
     */
    fun getPlatformInfo(): PlatformInfo {
        return PlatformInfo(
            platform = "android",
            osVersion = Build.VERSION.RELEASE,
            sdkVersion = Build.VERSION.SDK_INT,
            deviceModel = Build.MODEL,
            deviceManufacturer = Build.MANUFACTURER,
            appVersion = getAppVersion()
        )
    }
    
    // ==================== 内部方法 ====================
    
    /**
     * 获取或创建安装 ID
     * 
     * 存储在 SharedPreferences 中，应用卸载后重置。
     * 用于区分同一设备上的不同安装。
     */
    private fun getOrCreateInstallationId(): String {
        val prefs = context.getSharedPreferences("clawchat_installation", Context.MODE_PRIVATE)
        val existing = prefs.getString("installation_id", null)
        if (existing != null) {
            return existing
        }
        
        // 生成新的 UUID
        val newId = UUID.randomUUID().toString()
        prefs.edit().putString("installation_id", newId).apply()
        return newId
    }
    
    /**
     * 获取 SIM 信息（如果可用）
     */
    @SuppressLint("MissingPermission")
    private fun getSimInfo(): String? {
        return try {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                ?: return null
            
            // 使用 SIM 序列号（如果可用）
            val simSerial = telephonyManager.simSerialNumber
            if (!simSerial.isNullOrEmpty()) {
                return "serial:$simSerial"
            }
            
            // 或使用 SIM 运营商
            val simOperator = telephonyManager.simOperator
            if (!simOperator.isNullOrEmpty()) {
                return "operator:$simOperator"
            }
            
            null
        } catch (e: SecurityException) {
            // 没有电话权限，跳过 SIM 信息
            Log.w(TAG, "No permission to access SIM info", e)
            null
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get SIM info", e)
            null
        }
    }
    
    /**
     * SHA256 哈希
     */
    private fun sha256(input: String): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(input.toByteArray(Charsets.UTF_8))
    }
    
    /**
     * 获取应用版本
     */
    private fun getAppVersion(): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }
    }
    
    /**
     * 平台信息数据类
     */
    data class PlatformInfo(
        val platform: String,
        val osVersion: String,
        val sdkVersion: Int,
        val deviceModel: String,
        val deviceManufacturer: String,
        val appVersion: String
    )
}

/**
 * 设备指纹生成器（简化版 - 用于测试）
 * 
 * 不依赖 Android Context，使用纯 Kotlin 实现。
 * 适用于单元测试或非 Android 环境。
 */
object SimpleDeviceFingerprint {
    
    /**
     * 生成基于随机 UUID 的设备 ID
     * 
     * 注意：这不是真正的设备指纹，仅用于测试。
     * 每次调用都会生成不同的 ID。
     */
    fun generateTestId(): String {
        val uuid = UUID.randomUUID()
        val bytes = ByteBuffer.allocate(16)
            .putLong(uuid.mostSignificantBits)
            .putLong(uuid.leastSignificantBits)
            .array()
        
        return "test:${Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP)}"
    }
    
    /**
     * 从种子生成确定性设备 ID
     * 
     * 用于测试场景，需要可重复的指纹。
     */
    fun generateFromSeed(seed: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(seed.toByteArray(Charsets.UTF_8))
        return "seed:${Base64.encodeToString(hash, Base64.URL_SAFE or Base64.NO_WRAP)}"
    }
}

/**
 * 使用示例：
 * 
 * // 1. 生成设备指纹（首次启动时）
 * val fingerprint = DeviceFingerprint(context)
 * val deviceId = fingerprint.generateDeviceId()
 * 
 * // 2. 存储到 EncryptedStorage
 * val storage = EncryptedStorage(context)
 * storage.saveDeviceId(deviceId)
 * 
 * // 3. 获取设备信息用于配对请求
 * val platformInfo = fingerprint.getPlatformInfo()
 * val deviceDescription = fingerprint.getDeviceDescription()
 * 
 * // 4. 构建配对请求
 * val pairingPayload = """
 *     {
 *         "device": {
 *             "id": "$deviceId",
 *             "publicKey": "$publicKeyPem"
 *         },
 *         "client": {
 *             "id": "openclaw-android",
 *             "version": "${platformInfo.appVersion}",
 *             "platform": "${platformInfo.platform}"
 *         },
 *         "deviceDescription": "$deviceDescription"
 *     }
 * """.trimIndent()
 * 
 * // 5. 测试环境使用
 * val testId = SimpleDeviceFingerprint.generateTestId()
 * val deterministicId = SimpleDeviceFingerprint.generateFromSeed("fixed_seed")
 */
