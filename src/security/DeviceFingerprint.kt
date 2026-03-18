package com.openclaw.clawchat.security

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Base64
import android.util.Log
import java.util.UUID

/**
 * DeviceFingerprint - 设备指纹生成器（隐私合规版本）
 * 
 * 负责：
 * - 生成唯一的设备指纹 ID
 * - 基于随机 UUID（首次安装时生成）
 * - 支持 Android 6.0 (API 26+) 
 * - 生成结果稳定（应用生命周期内不变）
 * 
 * 指纹用途：
 * - 设备身份识别（Pairing 流程）
 * - 防重复注册
 * - 设备管理/撤销
 * 
 * 🔐 隐私合规说明：
 * - 不使用任何硬件标识符（IMEI、Android ID、序列号等）
 * - 不请求敏感权限（READ_PHONE_STATE 等）
 * - 应用卸载后自动重置（用户可控制）
 * - 符合 Google Play 开发者政策
 * 
 * @see <a href="https://developer.android.com/training/articles/user-data-ids">Android 用户数据 ID 最佳实践</a>
 */
class DeviceFingerprint(private val context: Context) {
    
    companion object {
        private const val TAG = "DeviceFingerprint"
        
        // 指纹格式版本（用于未来升级）
        private const val FINGERPRINT_VERSION = "v2"
        
        // SharedPreferences 文件名
        private const val PREFS_NAME = "clawchat_device_fingerprint"
        
        // 存储键名
        private const val KEY_DEVICE_ID = "device_id"
    }
    
    // 私有 SharedPreferences（仅本模块可访问）
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    /**
     * 生成/获取设备指纹 ID
     * 
     * 首次调用时生成随机 UUID 并存储，后续调用返回相同值。
     * 应用卸载后，SharedPreferences 被清除，下次安装会生成新的 ID。
     * 
     * @return Base64 编码的设备指纹（URL-safe）
     */
    fun generateDeviceId(): String {
        // 检查是否已有存储的 ID
        val existingId = prefs.getString(KEY_DEVICE_ID, null)
        if (existingId != null) {
            Log.d(TAG, "Returning existing device fingerprint")
            return existingId
        }
        
        // 生成新的随机 UUID
        val uuid = UUID.randomUUID()
        val deviceId = "$FINGERPRINT_VERSION:${Base64.encodeToString(uuidToBytes(uuid), Base64.URL_SAFE or Base64.NO_WRAP)}"
        
        // 存储到 SharedPreferences
        prefs.edit().putString(KEY_DEVICE_ID, deviceId).apply()
        
        Log.i(TAG, "Generated new device fingerprint: $deviceId")
        return deviceId
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
    
    /**
     * 重置设备指纹
     * 
     * 清除当前存储的 ID，下次调用 generateDeviceId() 会生成新的 ID。
     * 用于用户主动重置设备身份或重新配对场景。
     */
    fun resetDeviceId() {
        prefs.edit().remove(KEY_DEVICE_ID).apply()
        Log.i(TAG, "Device fingerprint reset")
    }
    
    /**
     * 检查是否已有设备指纹
     */
    fun hasDeviceId(): Boolean {
        return prefs.contains(KEY_DEVICE_ID)
    }
    
    // ==================== 内部方法 ====================
    
    /**
     * UUID 转字节数组
     */
    private fun uuidToBytes(uuid: UUID): ByteArray {
        val bytes = ByteArray(16)
        var i = 0
        
        // Most significant bits (8 bytes)
        for (shift in listOf(56, 48, 40, 32, 24, 16, 8, 0)) {
            bytes[i++] = ((uuid.mostSignificantBits ushr shift) and 0xFF).toByte()
        }
        
        // Least significant bits (8 bytes)
        for (shift in listOf(56, 48, 40, 32, 24, 16, 8, 0)) {
            bytes[i++] = ((uuid.leastSignificantBits ushr shift) and 0xFF).toByte()
        }
        
        return bytes
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
        val bytes = uuidToBytes(uuid)
        return "test:${Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP)}"
    }
    
    /**
     * 从种子生成确定性设备 ID
     * 
     * 用于测试场景，需要可重复的指纹。
     */
    fun generateFromSeed(seed: String): String {
        val uuid = UUID.nameUUIDFromBytes(seed.toByteArray(Charsets.UTF_8))
        val bytes = uuidToBytes(uuid)
        return "seed:${Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP)}"
    }
    
    /**
     * UUID 转字节数组
     */
    private fun uuidToBytes(uuid: UUID): ByteArray {
        val bytes = ByteArray(16)
        var i = 0
        
        for (shift in listOf(56, 48, 40, 32, 24, 16, 8, 0)) {
            bytes[i++] = ((uuid.mostSignificantBits ushr shift) and 0xFF).toByte()
        }
        
        for (shift in listOf(56, 48, 40, 32, 24, 16, 8, 0)) {
            bytes[i++] = ((uuid.leastSignificantBits ushr shift) and 0xFF).toByte()
        }
        
        return bytes
    }
}

/**
 * 使用示例：
 * 
 * // 1. 生成设备指纹（首次启动时）
 * val fingerprint = DeviceFingerprint(context)
 * val deviceId = fingerprint.generateDeviceId()
 * 
 * // 2. 存储到 EncryptedStorage（可选，用于备份/同步）
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
 * // 5. 重置设备指纹（用户主动重置）
 * fingerprint.resetDeviceId()
 * 
 * // 6. 测试环境使用
 * val testId = SimpleDeviceFingerprint.generateTestId()
 * val deterministicId = SimpleDeviceFingerprint.generateFromSeed("fixed_seed")
 */
