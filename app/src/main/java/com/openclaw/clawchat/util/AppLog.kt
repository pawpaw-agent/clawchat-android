package com.openclaw.clawchat.util

import android.util.Log
import com.openclaw.clawchat.BuildConfig

/**
 * 统一日志工具
 * 
 * DEBUG 级别日志仅在 Debug 构建中输出
 * 生产环境自动禁用 DEBUG/VERBOSE 日志
 */
object AppLog {
    
    private const val MAX_LOG_LENGTH = 4000
    
    /**
     * Debug 日志 - 仅在 Debug 构建中输出
     */
    fun d(tag: String, message: String) {
        if (BuildConfig.DEBUG) {
            // 分割超长日志
            if (message.length > MAX_LOG_LENGTH) {
                Log.d(tag, message.substring(0, MAX_LOG_LENGTH))
                d(tag, message.substring(MAX_LOG_LENGTH))
            } else {
                Log.d(tag, message)
            }
        }
    }
    
    /**
     * Verbose 日志 - 仅在 Debug 构建中输出
     */
    fun v(tag: String, message: String) {
        if (BuildConfig.DEBUG) {
            Log.v(tag, message)
        }
    }
    
    /**
     * Info 日志 - 始终输出
     */
    fun i(tag: String, message: String) {
        Log.i(tag, message)
    }
    
    /**
     * Warning 日志 - 始终输出
     */
    fun w(tag: String, message: String) {
        Log.w(tag, message)
    }
    
    /**
     * Warning 日志（带异常）- 始终输出
     */
    fun w(tag: String, message: String, throwable: Throwable) {
        Log.w(tag, message, throwable)
    }
    
    /**
     * Error 日志 - 始终输出
     */
    fun e(tag: String, message: String) {
        Log.e(tag, message)
    }
    
    /**
     * Error 日志（带异常）- 始终输出
     */
    fun e(tag: String, message: String, throwable: Throwable) {
        Log.e(tag, message, throwable)
    }
}