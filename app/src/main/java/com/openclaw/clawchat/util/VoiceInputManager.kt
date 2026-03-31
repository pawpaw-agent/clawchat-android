package com.openclaw.clawchat.util

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 语音输入管理器
 * 封装 Android SpeechRecognizer
 */
class VoiceInputManager(private val context: Context) {
    
    private var speechRecognizer: SpeechRecognizer? = null
    
    private val _state = MutableStateFlow<VoiceInputState>(VoiceInputState.Idle)
    val state: StateFlow<VoiceInputState> = _state.asStateFlow()
    
    private val _partialResult = MutableStateFlow("")
    val partialResult: StateFlow<String> = _partialResult.asStateFlow()
    
    /**
     * 初始化语音识别
     */
    fun initialize() {
        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        _state.value = VoiceInputState.Listening
                        _partialResult.value = ""
                    }
                    
                    override fun onBeginningOfSpeech() {
                        AppLog.d("VoiceInput", "Speech began")
                    }
                    
                    override fun onRmsChanged(rmsdB: Float) {
                        // 音量变化
                    }
                    
                    override fun onBufferReceived(buffer: ByteArray?) {
                        // 音频数据
                    }
                    
                    override fun onEndOfSpeech() {
                        _state.value = VoiceInputState.Processing
                    }
                    
                    override fun onError(error: Int) {
                        val errorMsg = when (error) {
                            SpeechRecognizer.ERROR_AUDIO -> "音频录制错误"
                            SpeechRecognizer.ERROR_CLIENT -> "客户端错误"
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "权限不足"
                            SpeechRecognizer.ERROR_NETWORK -> "网络错误"
                            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "网络超时"
                            SpeechRecognizer.ERROR_NO_MATCH -> "未识别到语音"
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "识别器忙碌"
                            SpeechRecognizer.ERROR_SERVER -> "服务器错误"
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "语音超时"
                            else -> "未知错误 ($error)"
                        }
                        _state.value = VoiceInputState.Error(errorMsg)
                        AppLog.e("VoiceInput", "Recognition error: $errorMsg")
                    }
                    
                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val result = matches?.firstOrNull() ?: ""
                        _state.value = VoiceInputState.Success(result)
                        _partialResult.value = ""
                        AppLog.d("VoiceInput", "Recognition result: $result")
                    }
                    
                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val partial = matches?.firstOrNull() ?: ""
                        _partialResult.value = partial
                    }
                    
                    override fun onEvent(eventType: Int, params: Bundle?) {
                        AppLog.d("VoiceInput", "Event: $eventType")
                    }
                })
            }
        }
    }
    
    /**
     * 开始语音识别
     */
    fun startListening(language: String = "zh-CN") {
        if (speechRecognizer == null) {
            initialize()
        }
        
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        
        _state.value = VoiceInputState.Listening
        speechRecognizer?.startListening(intent)
    }
    
    /**
     * 停止语音识别
     */
    fun stopListening() {
        speechRecognizer?.stopListening()
    }
    
    /**
     * 取消语音识别
     */
    fun cancel() {
        speechRecognizer?.cancel()
        _state.value = VoiceInputState.Idle
        _partialResult.value = ""
    }
    
    /**
     * 销毁资源
     */
    fun destroy() {
        speechRecognizer?.destroy()
        speechRecognizer = null
        _state.value = VoiceInputState.Idle
    }
}

/**
 * 语音输入状态
 */
sealed class VoiceInputState {
    data object Idle : VoiceInputState()
    data object Listening : VoiceInputState()
    data object Processing : VoiceInputState()
    data class Success(val text: String) : VoiceInputState()
    data class Error(val message: String) : VoiceInputState()
}