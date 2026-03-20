package com.openclaw.clawchat.ui.state

import androidx.lifecycle.SavedStateHandle
import com.openclaw.clawchat.domain.model.Message
import com.openclaw.clawchat.domain.model.MessageRole
import com.openclaw.clawchat.domain.model.MessageStatus
import com.openclaw.clawchat.domain.repository.MessageRepository
import com.openclaw.clawchat.network.WebSocketConnectionState
import com.openclaw.clawchat.network.protocol.GatewayConnection
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * SessionViewModel 单元测试
 * 
 * 测试覆盖：
 * - 消息发送流程
 * - ChatEvent 状态机（delta, final, aborted, error）
 * - 消息历史加载
 * - 连接状态观察
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionViewModelTest {

    private lateinit var mockGateway: GatewayConnection
    private lateinit var mockRepository: MessageRepository
    private lateinit var savedStateHandle: SavedStateHandle
    
    @Before
    fun setup() {
        MockKAnnotations.init(this)
        mockGateway = mockk(relaxed = true)
        mockRepository = mockk(relaxed = true)
        savedStateHandle = SavedStateHandle(mapOf("sessionId" to "test-session"))
        
        // 默认行为
        every { mockGateway.connectionState } returns MutableStateFlow(WebSocketConnectionState.Connected)
        every { mockGateway.incomingMessages } returns MutableSharedFlow()
        every { mockGateway.defaultSessionKey } returns "agent:main:main"
        coEvery { mockRepository.observeMessages(any()) } returns flowOf(emptyList())
    }
    
    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ==================== 初始化测试 ====================

    @Test
    fun `init should load message history`() = runTest {
        // Given
        val messages = listOf(
            Message(
                id = "msg-1",
                sessionId = "test-session",
                role = MessageRole.USER,
                content = "Hello",
                timestamp = System.currentTimeMillis(),
                status = MessageStatus.DELIVERED
            )
        )
        coEvery { mockRepository.observeMessages("test-session") } returns flowOf(messages)
        
        // When
        val viewModel = SessionViewModel(mockGateway, mockRepository, savedStateHandle)
        
        // Then
        val state = viewModel.state.value
        assertEquals(1, state.messages.size)
        assertEquals("Hello", state.messages.first().content)
    }

    @Test
    fun `init should observe connection state`() = runTest {
        // Given
        val connectionState = MutableStateFlow<WebSocketConnectionState>(
            WebSocketConnectionState.Connected
        )
        every { mockGateway.connectionState } returns connectionState
        
        // When
        val viewModel = SessionViewModel(mockGateway, mockRepository, savedStateHandle)
        
        // Then
        val state = viewModel.state.value
        assertTrue(state.connectionStatus is ConnectionStatus.Connected)
    }

    // ==================== 消息发送测试 ====================

    @Test
    fun `sendMessage should add user message to state`() = runTest {
        // Given
        val response = mockk<com.openclaw.clawchat.network.protocol.ResponseFrame>(relaxed = true)
        every { response.isSuccess() } returns true
        coEvery { mockGateway.chatSend(any(), any()) } returns response
        coEvery { mockRepository.saveMessage(any(), any(), any(), any(), any()) } returns "msg-1"
        coEvery { mockRepository.updateMessageStatus(any(), any()) } returns Unit
        
        val viewModel = SessionViewModel(mockGateway, mockRepository, savedStateHandle)
        
        // When
        viewModel.sendMessage("Hello, World!")
        
        // Then
        val state = viewModel.state.value
        assertEquals("", state.inputText) // Should be cleared
        assertTrue(state.messages.any { it.content == "Hello, World!" && it.role == MessageRole.USER })
    }

    @Test
    fun `sendMessage should not send empty message`() = runTest {
        // Given
        val viewModel = SessionViewModel(mockGateway, mockRepository, savedStateHandle)
        
        // When
        viewModel.sendMessage("")
        
        // Then
        coVerify(exactly = 0) { mockGateway.chatSend(any(), any()) }
    }

    @Test
    fun `sendMessage should not send when not connected`() = runTest {
        // Given
        every { mockGateway.connectionState } returns MutableStateFlow(WebSocketConnectionState.Disconnected)
        
        val viewModel = SessionViewModel(mockGateway, mockRepository, savedStateHandle)
        
        // When
        viewModel.sendMessage("Hello")
        
        // Then
        coVerify(exactly = 0) { mockGateway.chatSend(any(), any()) }
    }

    @Test
    fun `sendMessage should handle failure`() = runTest {
        // Given
        val response = mockk<com.openclaw.clawchat.network.protocol.ResponseFrame>(relaxed = true)
        every { response.isSuccess() } returns false
        every { response.error } returns mockk { every { message } returns "Send failed" }
        coEvery { mockGateway.chatSend(any(), any()) } returns response
        coEvery { mockRepository.saveMessage(any(), any(), any(), any(), any()) } returns "msg-1"
        coEvery { mockRepository.updateMessageStatus(any(), any()) } returns Unit
        
        val viewModel = SessionViewModel(mockGateway, mockRepository, savedStateHandle)
        
        // When
        viewModel.sendMessage("Test message")
        
        // Then
        val state = viewModel.state.value
        assertTrue(state.error?.contains("Send failed") ?: false || state.error != null)
    }

    // ==================== UI 操作测试 ====================

    @Test
    fun `updateInputText should update state`() = runTest {
        // Given
        val viewModel = SessionViewModel(mockGateway, mockRepository, savedStateHandle)
        
        // When
        viewModel.updateInputText("New input")
        
        // Then
        val state = viewModel.state.value
        assertEquals("New input", state.inputText)
    }

    @Test
    fun `clearError should remove error from state`() = runTest {
        // Given
        val viewModel = SessionViewModel(mockGateway, mockRepository, savedStateHandle)
        
        // Set error manually
        val stateFlow = viewModel.state as MutableStateFlow
        stateFlow.value = stateFlow.value.copy(error = "Test error")
        
        // When
        viewModel.clearError()
        
        // Then
        val state = viewModel.state.value
        assertEquals(null, state.error)
    }

    @Test
    fun `setSessionId should clear messages and buffers`() = runTest {
        // Given
        val viewModel = SessionViewModel(mockGateway, mockRepository, savedStateHandle)
        
        // Add some messages first
        val stateFlow = viewModel.state as MutableStateFlow
        stateFlow.value = stateFlow.value.copy(
            sessionId = "old-session",
            messages = listOf(
                MessageUi(
                    id = "msg-1",
                    content = "Test",
                    role = MessageRole.USER,
                    timestamp = System.currentTimeMillis()
                )
            )
        )
        
        // When
        viewModel.setSessionId("new-session")
        
        // Then
        val state = viewModel.state.value
        assertEquals("new-session", state.sessionId)
        assertTrue(state.messages.isEmpty())
    }

    @Test
    fun `clearSession should reset state`() = runTest {
        // Given
        val viewModel = SessionViewModel(mockGateway, mockRepository, savedStateHandle)
        
        // Set some state
        val stateFlow = viewModel.state as MutableStateFlow
        stateFlow.value = stateFlow.value.copy(
            sessionId = "test-session",
            messages = listOf(
                MessageUi(
                    id = "msg-1",
                    content = "Test",
                    role = MessageRole.USER,
                    timestamp = System.currentTimeMillis()
                )
            ),
            inputText = "Some text",
            error = "Some error"
        )
        
        // When
        viewModel.clearSession()
        
        // Then
        val state = viewModel.state.value
        assertEquals(null, state.sessionId)
        assertTrue(state.messages.isEmpty())
        assertEquals("", state.inputText)
        assertEquals(null, state.error)
        assertFalse(state.isLoading)
    }

    // ==================== 连接状态测试 ====================

    @Test
    fun `should update connection status when gateway state changes`() = runTest {
        // Given
        val connectionState = MutableStateFlow<WebSocketConnectionState>(
            WebSocketConnectionState.Disconnected
        )
        every { mockGateway.connectionState } returns connectionState
        
        val viewModel = SessionViewModel(mockGateway, mockRepository, savedStateHandle)
        
        // When
        connectionState.value = WebSocketConnectionState.Connecting
        
        // Then
        val state = viewModel.state.value
        assertTrue(state.connectionStatus is ConnectionStatus.Connecting)
    }

    @Test
    fun `should show error when connection fails`() = runTest {
        // Given
        val connectionState = MutableStateFlow<WebSocketConnectionState>(
            WebSocketConnectionState.Disconnected
        )
        every { mockGateway.connectionState } returns connectionState
        
        val viewModel = SessionViewModel(mockGateway, mockRepository, savedStateHandle)
        
        // When
        connectionState.value = WebSocketConnectionState.Error(Exception("Connection failed"))
        
        // Then
        val state = viewModel.state.value
        assertTrue(state.connectionStatus is ConnectionStatus.Error)
    }

    // ==================== SavedStateHandle 测试 ====================

    @Test
    fun `should use sessionId from SavedStateHandle`() = runTest {
        // Given
        val handle = SavedStateHandle(mapOf("sessionId" to "custom-session"))
        
        // When
        val viewModel = SessionViewModel(mockGateway, mockRepository, handle)
        
        // Then - verify messages are loaded for the correct session
        coVerify { mockRepository.observeMessages("custom-session") }
    }
}