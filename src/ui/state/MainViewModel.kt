package ui.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 主界面 ViewModel
 * 负责管理连接状态、会话列表和全局 UI 状态
 */
class MainViewModel : ViewModel() {
    
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()
    
    private val _events = MutableStateFlow<UiEvent?>(null)
    val events: StateFlow<UiEvent?> = _events.asStateFlow()
    
    /**
     * 连接到网关
     */
    fun connectToGateway(gatewayUrl: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            // TODO: 调用 UseCase 进行实际连接
            // 模拟连接过程
            try {
                _uiState.update { 
                    it.copy(
                        connectionStatus = ConnectionStatus.Connecting,
                        isLoading = false
                    ) 
                }
                
                // 模拟连接成功
                _uiState.update { 
                    it.copy(
                        connectionStatus = ConnectionStatus.Connected,
                        currentGateway = GatewayConfigUi(
                            id = "default",
                            name = "本地网关",
                            host = gatewayUrl,
                            port = 18789,
                            useTls = false,
                            isCurrent = true
                        )
                    ) 
                }
                
                _events.value = UiEvent.ShowSuccess("已连接到网关")
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        connectionStatus = ConnectionStatus.Error(e.message ?: "连接失败"),
                        error = e.message
                    ) 
                }
                _events.value = UiEvent.ShowError(e.message ?: "连接失败")
            }
        }
    }
    
    /**
     * 断开连接
     */
    fun disconnect() {
        viewModelScope.launch {
            _uiState.update { 
                it.copy(
                    connectionStatus = ConnectionStatus.Disconnecting,
                    currentGateway = null
                ) 
            }
            
            // TODO: 调用 UseCase 进行实际断开
            _uiState.update { 
                it.copy(
                    connectionStatus = ConnectionStatus.Disconnected
                ) 
            }
            
            _events.value = UiEvent.ShowSuccess("已断开连接")
        }
    }
    
    /**
     * 选择会话
     */
    fun selectSession(sessionId: String) {
        viewModelScope.launch {
            val session = _uiState.value.sessions.find { it.id == sessionId }
            _uiState.update { it.copy(currentSession = session) }
            _events.value = UiEvent.NavigateToSession(sessionId)
        }
    }
    
    /**
     * 创建新会话
     */
    fun createSession(model: String = "default", thinking: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            // TODO: 调用 UseCase 创建会话
            val newSession = SessionUi(
                id = "session_${System.currentTimeMillis()}",
                label = "新会话",
                model = model,
                status = SessionStatus.RUNNING,
                lastActivityAt = System.currentTimeMillis(),
                messageCount = 0
            )
            
            _uiState.update { 
                it.copy(
                    sessions = it.sessions + newSession,
                    currentSession = newSession,
                    isLoading = false
                ) 
            }
            
            _events.value = UiEvent.NavigateToSession(newSession.id)
        }
    }
    
    /**
     * 删除会话
     */
    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            _uiState.update { 
                it.copy(
                    sessions = it.sessions.filter { it.id != sessionId },
                    currentSession = if (it.currentSession?.id == sessionId) null else it.currentSession
                ) 
            }
            _events.value = UiEvent.ShowSuccess("会话已删除")
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
     * 模拟加载会话列表（用于测试）
     */
    fun loadSessions() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            // 模拟数据
            val mockSessions = listOf(
                SessionUi(
                    id = "session_1",
                    label = "项目讨论",
                    model = "qwen3.5-plus",
                    status = SessionStatus.RUNNING,
                    lastActivityAt = System.currentTimeMillis() - 60000,
                    messageCount = 15,
                    lastMessage = "好的，我来帮你实现这个功能"
                ),
                SessionUi(
                    id = "session_2",
                    label = "代码审查",
                    model = "qwen3.5-plus",
                    status = SessionStatus.RUNNING,
                    lastActivityAt = System.currentTimeMillis() - 3600000,
                    messageCount = 8,
                    lastMessage = "这段代码需要优化"
                )
            )
            
            _uiState.update { 
                it.copy(
                    sessions = mockSessions,
                    isLoading = false
                ) 
            }
        }
    }
}

/**
 * UI 事件类型
 */
sealed class UiEvent {
    data class NavigateToSession(val sessionId: String) : UiEvent()
    data class ShowError(val message: String) : UiEvent()
    data class ShowSuccess(val message: String) : UiEvent()
    data object ShowPairingDialog : UiEvent()
    data object ConnectionLost : UiEvent()
}
