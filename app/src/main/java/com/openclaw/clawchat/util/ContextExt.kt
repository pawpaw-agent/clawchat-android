package com.openclaw.clawchat.util

import android.content.Context
import android.content.Intent
import android.widget.Toast

/**
 * Context 扩展函数
 */

/**
 * 显示 Toast
 */
fun Context.showToast(message: String, duration: Int = Toast.LENGTH_SHORT) {
    Toast.makeText(this, message, duration).show()
}

/**
 * 显示长 Toast
 */
fun Context.showLongToast(message: String) {
    showToast(message, Toast.LENGTH_LONG)
}

/**
 * 发送广播
 */
fun Context.sendBroadcast(action: String) {
    sendBroadcast(Intent(action))
}

/**
 * 判断是否有网络连接
 */
fun Context.isNetworkAvailable(): Boolean {
    val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
    return connectivityManager?.activeNetworkInfo?.isConnected == true
}

/**
 * 获取应用的版本名称
 */
fun Context.getVersionName(): String {
    return try {
        packageManager.getPackageInfo(packageName, 0).versionName ?: "Unknown"
    } catch (e: Exception) {
        "Unknown"
    }
}

/**
 * 获取应用的版本号
 */
fun Context.getVersionCode(): Long {
    return try {
        packageManager.getPackageInfo(packageName, 0).longVersionCode
    } catch (e: Exception) {
        0L
    }
}