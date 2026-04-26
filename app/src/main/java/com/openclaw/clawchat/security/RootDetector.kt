package com.openclaw.clawchat.security

import android.content.Context
import com.openclaw.clawchat.util.AppLog
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

/**
 * Root 检测工具
 *
 * 功能：
 * - 检测设备是否被 Root
 * - 检测常见的 Root 管理应用
 * - 检测 su 二进制文件
 * - 检测危险属性
 *
 * 注意：Root 检测不是 100% 可靠，高级用户可能绕过
 */
object RootDetector {

    private const val TAG = "RootDetector"

    /**
     * Root 检测结果
     */
    data class RootCheckResult(
        val isRooted: Boolean,
        val rootIndicators: List<String>,
        val riskLevel: RiskLevel
    ) {
        enum class RiskLevel {
            NONE,       // 未检测到 Root
            LOW,        // 可能是误报
            MEDIUM,     // 有 Root 迹象
            HIGH        // 确定 Root
        }
    }

    /**
     * 常见的 su 二进制文件路径
     */
    private val SU_PATHS = arrayOf(
        "/system/bin/su",
        "/system/xbin/su",
        "/system/sbin/su",
        "/sbin/su",
        "/vendor/bin/su",
        "/su/bin/su",
        "/data/local/xbin/su",
        "/data/local/bin/su",
        "/data/local/su",
        "/su/bin/su",
        "/magisk/.core/bin/su"
    )

    /**
     * 常见的 Root 管理应用包名
     */
    private val ROOT_PACKAGES = arrayOf(
        "com.koushikdutta.superuser",
        "com.thirdparty.superuser",
        "eu.chainfire.supersu",
        "com.noshufou.android.su",
        "com.topjohnwu.magisk",
        "me.phh.superuser",
        "com.kingouser.com",
        "com.android.vending.billing.InAppBillingService.COIN"
    )

    /**
     * 危险的系统属性
     */
    private val DANGEROUS_PROPS = mapOf(
        "ro.debuggable" to "1",
        "ro.secure" to "0"
    )

    /**
     * 执行完整的 Root 检测
     */
    fun checkRoot(context: Context): RootCheckResult {
        val indicators = mutableListOf<String>()
        var maxRiskLevel = RootCheckResult.RiskLevel.NONE

        // 1. 检查 su 二进制文件
        val suCheck = checkSuBinary()
        if (suCheck.first) {
            indicators.add("su binary found: ${suCheck.second}")
            maxRiskLevel = maxOf(maxRiskLevel, RootCheckResult.RiskLevel.HIGH)
        }

        // 2. 检查 Root 管理应用
        val packageCheck = checkRootPackages(context)
        if (packageCheck.first) {
            indicators.add("Root app found: ${packageCheck.second}")
            maxRiskLevel = maxOf(maxRiskLevel, RootCheckResult.RiskLevel.HIGH)
        }

        // 3. 检查危险属性
        val propCheck = checkDangerousProps()
        if (propCheck.first) {
            indicators.add("Dangerous prop: ${propCheck.second}")
            maxRiskLevel = maxOf(maxRiskLevel, RootCheckResult.RiskLevel.MEDIUM)
        }

        // 4. 检查 RW 路径
        val rwCheck = checkRWPaths()
        if (rwCheck.first) {
            indicators.add("RW path found: ${rwCheck.second}")
            maxRiskLevel = maxOf(maxRiskLevel, RootCheckResult.RiskLevel.MEDIUM)
        }

        // 5. 检查 Busybox
        val busyboxCheck = checkBusybox()
        if (busyboxCheck.first) {
            indicators.add("Busybox found")
            maxRiskLevel = maxOf(maxRiskLevel, RootCheckResult.RiskLevel.LOW)
        }

        // 6. 检查 Magisk
        val magiskCheck = checkMagisk()
        if (magiskCheck.first) {
            indicators.add("Magisk detected")
            maxRiskLevel = maxOf(maxRiskLevel, RootCheckResult.RiskLevel.HIGH)
        }

        val isRooted = indicators.isNotEmpty()

        AppLog.i(TAG, "Root check result: isRooted=$isRooted, indicators=${indicators.size}, riskLevel=$maxRiskLevel")

        return RootCheckResult(
            isRooted = isRooted,
            rootIndicators = indicators,
            riskLevel = maxRiskLevel
        )
    }

    /**
     * 检查 su 二进制文件
     */
    private fun checkSuBinary(): Pair<Boolean, String> {
        for (path in SU_PATHS) {
            if (File(path).exists()) {
                return Pair(true, path)
            }
        }
        return Pair(false, "")
    }

    /**
     * 检查 Root 管理应用
     */
    private fun checkRootPackages(context: Context): Pair<Boolean, String> {
        val pm = context.packageManager
        for (pkg in ROOT_PACKAGES) {
            try {
                pm.getPackageInfo(pkg, 0)
                return Pair(true, pkg)
            } catch (e: Exception) {
                // 包不存在
            }
        }
        return Pair(false, "")
    }

    /**
     * 检查危险系统属性
     */
    private fun checkDangerousProps(): Pair<Boolean, String> {
        for ((prop, expectedValue) in DANGEROUS_PROPS) {
            try {
                val process = Runtime.getRuntime().exec("getprop $prop")
                val value = process.inputStream.use { stream ->
                    BufferedReader(InputStreamReader(stream)).use { reader ->
                        reader.readLine()?.trim()
                    }
                }
                process.destroy()

                if (value == expectedValue) {
                    return Pair(true, "$prop=$value")
                }
            } catch (e: Exception) {
                AppLog.w(TAG, "Failed to check prop: $prop")
            }
        }
        return Pair(false, "")
    }

    /**
     * 检查可读写路径
     */
    private fun checkRWPaths(): Pair<Boolean, String> {
        val paths = arrayOf(
            "/system",
            "/system/bin",
            "/system/sbin",
            "/system/xbin",
            "/vendor/bin",
            "/sbin",
            "/etc",
            "/sys",
            "/proc"
        )

        for (path in paths) {
            val file = File(path)
            if (file.exists() && file.canWrite()) {
                return Pair(true, path)
            }
        }
        return Pair(false, "")
    }

    /**
     * 检查 Busybox
     */
    private fun checkBusybox(): Pair<Boolean, String> {
        val paths = arrayOf(
            "/system/xbin/busybox",
            "/system/bin/busybox",
            "/sbin/busybox",
            "/data/local/xbin/busybox",
            "/data/local/bin/busybox"
        )

        for (path in paths) {
            if (File(path).exists()) {
                return Pair(true, path)
            }
        }
        return Pair(false, "")
    }

    /**
     * 检查 Magisk
     */
    private fun checkMagisk(): Pair<Boolean, String> {
        val magiskPaths = arrayOf(
            "/sbin/.magisk",
            "/cache/.magisk",
            "/data/adb/magisk",
            "/data/adb/ksu",
            "/data/adb/ksud"
        )

        for (path in magiskPaths) {
            if (File(path).exists()) {
                return Pair(true, path)
            }
        }
        return Pair(false, "")
    }

    /**
     * 快速检查是否可能 Root（用于性能敏感场景）
     */
    fun quickCheck(context: Context): Boolean {
        // 只检查最可靠的指标
        for (path in SU_PATHS) {
            if (File(path).exists()) return true
        }

        val pm = context.packageManager
        for (pkg in ROOT_PACKAGES) {
            try {
                pm.getPackageInfo(pkg, 0)
                return true
            } catch (e: Exception) {
                // 包不存在
            }
        }

        return false
    }
}