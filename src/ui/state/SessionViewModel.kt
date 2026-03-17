package ui.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ui.components.MessageUi
import ui.components.MessageRole

/**
 * 会话界面 ViewModel
 * 负责管理单个会话的消息列表和输入状态
 */
class SessionViewModel(
    private val sessionId: String
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(SessionUiState(sessionId = sessionId))
    val uiState: StateFlow<SessionUiState> = _uiState.asStateFlow()
    
    private val _events = MutableStateFlow<SessionUiEvent?>(null)
    val events: StateFlow<SessionUiEvent?> = _events.asStateFlow()
    
    init {
        loadMessages()
    }
    
    /**
     * 加载消息历史
     */
    private fun loadMessages() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            // TODO: 调用 UseCase 加载消息
            // 模拟加载
            val mockMessages = listOf(
                MessageUi(
                    id = "msg_1",
                    content = "你好！有什么我可以帮助你的吗？",
                    role = MessageRole.ASSISTANT,
                    timestamp = System.currentTimeMillis() - 600000
                ),
                MessageUi(
                    id = "msg_2",
                    content = "帮我创建一个 Android UI 框架",
                    role = MessageRole.USER,
                    timestamp = System.currentTimeMillis() - 580000
                ),
                MessageUi(
                    id = "msg_3",
                    content = "好的，我来帮你实现这个功能。我会创建主题系统、基础组件和状态管理。",
                    role = MessageRole.ASSISTANT,
                    timestamp = System.currentTimeMillis() - 560000
                )
            )
            
            _uiState.update { 
                it.copy(
                    messages = mockMessages,
                    isLoading = false,
                    session = SessionUi(
                        id = sessionId,
                        label = "新会话",
                        model = "qwen3.5-plus",
                        status = SessionStatus.RUNNING,
                        lastActivityAt = System.currentTimeMillis(),
                        messageCount = mockMessages.size
                    )
                ) 
            }
        }
    }
    
    /**
     * 发送消息
     */
    fun sendMessage(content: String) {
        if (content.isBlank()) return
        
        viewModelScope.launch {
            _uiState.update { 
                it.copy(
                    inputText = "",
                    isSending = true
                ) 
            }
            
            // 添加用户消息到列表
            val userMessage = MessageUi(
                id = "msg_${System.currentTimeMillis()}",
                content = content,
                role = MessageRole.USER,
                timestamp = System.currentTimeMillis()
            )
            
            _uiState.update { 
                it.copy(
                    messages = it.messages + userMessage
                ) 
            }
            
            // TODO: 调用 UseCase 发送消息
            // 模拟发送和响应
            try {
                // 模拟网络延迟
                kotlinx.coroutines.delay(1000)
                
                // 添加助手响应（模拟）
                val assistantMessage = MessageUi(
                    id = "msg_${System.currentTimeMillis() + 1}",
                    content = "收到你的消息：$content",
                    role = MessageRole.ASSISTANT,
                    timestamp = System.currentTimeMillis(),
                    isLoading = false
                )
                
                _uiState.update { 
                    it.copy(
                        messages = it.messages + assistantMessage,
                        isSending = false
                    ) 
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isSending = false,
                        error = e.message
                    ) 
                }
                _events.value = SessionUiEvent.ShowError(e.message ?: "发送失败")
            }
        }
    }
    
    /**
     * 更新输入文本
     */
    fun updateInputText(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }
    
    /**
     * 重新生成最后一条消息
     */
    fun regenerateLastMessage() {
        viewModelScope.launch {
            val lastUserMessage = _uiState.value.messages.lastOrNull { it.role == MessageRole.USER }
            if (lastUserMessage != null) {
                // 移除最后一条助手消息
                val messagesWithoutLastAssistant = _uiState.value.messages.dropLastWhile { 
                    it.role == MessageRole.ASSISTANT 
                }
                
                _uiState.update { 
                    it.copy(
                        messages = messagesWithoutLastAssistant
                    ) 
                }
                
                // 重新发送
                sendMessage(lastUserMessage.content)
            }
        }
    }
    
    /**
     * 清除错误
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
    
    /**
     * 清除事件
     */
    fun consumeEvent() {
        _events.value = null
    }
    
    /**
     * 滚动到底部
     */
    fun scrollToBottom() {
        _events.value = SessionUiEvent.ScrollToBottom
    }
}

/**
 * 会话 UI 事件
 */
sealed class SessionUiEvent {
    data class ShowError(val message: String) : SessionUiEvent()
    data object ScrollToBottom : SessionUiEvent()
    data object MessageSent : SessionUiEvent()
}
