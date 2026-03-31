package com.openclaw.clawchat.util

import android.content.Context
import android.speech.tts.TextToSpeech
import com.openclaw.clawchat.util.AppLog
import java.util.Locale

/**
 * 消息朗读工具
 * 使用 Android TTS API 朗读消息内容
 */
object MessageSpeaker {
    private const val TAG = "MessageSpeaker"
    
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    
    /**
     * 初始化 TTS
     */
    fun init(context: Context, onReady: () -> Unit = {}) {
        if (isInitialized) {
            onReady()
            return
        }
        
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isInitialized = true
                // 设置默认语言为中文
                val result = tts?.setLanguage(Locale.CHINESE)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    // 中文不可用，使用英文
                    tts?.setLanguage(Locale.US)
                }
                onReady()
            } else {
                AppLog.e(TAG, "TTS initialization failed")
            }
        }
    }
    
    /**
     * 朗读文本
     */
    fun speak(text: String) {
        if (!isInitialized) {
            AppLog.w(TAG, "TTS not initialized, cannot speak")
            return
        }
        
        // 停止当前朗读
        stop()
        
        // 开始新朗读
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "message_speak")
    }
    
    /**
     * 停止朗读
     */
    fun stop() {
        tts?.stop()
    }
    
    /**
     * 释放资源
     */
    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }
    
    /**
     * 是否正在朗读
     */
    fun isSpeaking(): Boolean {
        return tts?.isSpeaking ?: false
    }
}