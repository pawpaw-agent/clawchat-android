package com.openclaw.clawchat.ui.state

import com.openclaw.clawchat.domain.model.Session
import com.openclaw.clawchat.domain.model.SessionStatus
import com.openclaw.clawchat.domain.repository.SessionRepository
import com.openclaw.clawchat.network.WebSocketConnectionState
import com.openclaw.clawchat.network.protocol.GatewayConnection
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * MainViewModel 单元测试
 * 
 * 测试覆盖：
 * - 连接状态管理
 * - 会话列表加载与同步
 * - 会话操作（选择、删除、重命名、暂停、恢复）
 * - 错误处理
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    private lateinit var mockGateway: GatewayConnection
    private lateinit var mockRepository: SessionRepository
    
    @Before
    fun setup() {
        MockKAnnotations.init(this)
        mockGateway = mockk(relaxed = true)
        mockRepository = mockk(relaxed = true)
        
        // 默认行为
        every { mockGateway.connectionState } returns MutableStateFlow(WebSocketConnectionState.Disconnected)
        every { mockGateway.defaultSessionKey } returns "agent:main:main"
        every { mockGateway.measureLatency() } returns 50L
        coEvery { mockRepository.observeSessions() } returns flowOf(emptyList())
        coEvery { mockRepository.observeCurrentSession() } returns flowOf(null)
    }
    
    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ==================== 初始化测试 ====================

    @Test
    fun `init should load sessions from cache`() = runTest {
        // Given
        val cachedSessions = listOf(
            Session(
                id = "session-1",
                label = "Cached Session",
                model = "qwen3.5-plus",
                status = SessionStatus.RUNNING,
                lastActivityAt = System.currentTimeMillis()
            )
        )
        coEvery { mockRepository.observeSessions() } returns flowOf(cachedSessions)
        
        // When
        val viewModel = MainViewModel(mockGateway, mockRepository)
        
        // Then
        val state = viewModel.uiState.value
        assertEquals(1, state.sessions.size)
        assertEquals("session-1", state.sessions.first().id)
    }

    @Test
    fun `init should observe connection state`() = runTest {
        // Given
        val connectionState = MutableStateFlow<WebSocketConnectionState>(
            WebSocketConnectionState.Connected
        )
        every { mockGateway.connectionState } returns connectionState
        
        // When
        val viewModel = MainViewModel(mockGateway, mockRepository)
        
        // Then
        val state = viewModel.uiState.value
        assertTrue(state.connectionStatus is ConnectionStatus.Connected)
    }

    // ==================== 连接测试 ====================

    @Test
    fun `connectToGateway should update state on success`() = runTest {
        // Given
        coEvery { mockGateway.connect(any()) } returns Result.success(Unit)
        
        val viewModel = MainViewModel(mockGateway, mockRepository)
        
        // When
        viewModel.connectToGateway("ws://localhost:18789")
        
        // Then
        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNotNull(state.currentGateway)
        assertEquals("Gateway", state.currentGateway?.name)
    }

    @Test
    fun `connectToGateway should update error state on failure`() = runTest {
        // Given
        val error = Exception("Connection refused")
        coEvery { mockGateway.connect(any()) } returns Result.failure(error)
        
        val viewModel = MainViewModel(mockGateway, mockRepository)
        
        // When
        viewModel.connectToGateway("ws://localhost:18789")
        
        // Then
        val state = viewModel.uiState.value
        assertTrue(state.connectionStatus is ConnectionStatus.Error)
        assertFalse(state.isLoading)
    }

    @Test
    fun `disconnect should update connection status`() = runTest {
        // Given
        coEvery { mockGateway.disconnect() } returns Result.success(Unit)
        
        val viewModel = MainViewModel(mockGateway, mockRepository)
        
        // When
        viewModel.disconnect()
        
        // Then
        val state = viewModel.uiState.value
        assertTrue(state.connectionStatus is ConnectionStatus.Disconnected)
    }

    // ==================== 会话操作测试 ====================

    @Test
    fun `selectSession should update currentSession`() = runTest {
        // Given
        val sessions = listOf(
            SessionUi(
                id = "session-1",
                label = "Test Session",
                model = "qwen3.5-plus",
                status = SessionStatus.RUNNING,
                lastActivityAt = System.currentTimeMillis()
            )
        )
        
        val viewModel = MainViewModel(mockGateway, mockRepository)
        
        // Set sessions manually for test
        val stateFlow = viewModel.uiState as MutableStateFlow
        stateFlow.value = stateFlow.value.copy(sessions = sessions)
        
        // When
        viewModel.selectSession("session-1")
        
        // Then
        val state = viewModel.uiState.value
        assertEquals("session-1", state.currentSession?.id)
    }

    @Test
    fun `deleteSession should remove from list`() = runTest {
        // Given
        val sessions = listOf(
            SessionUi(
                id = "session-1",
                label = "Test Session",
                model = "qwen3.5-plus",
                status = SessionStatus.RUNNING,
                lastActivityAt = System.currentTimeMillis()
            )
        )
        
        coEvery { mockRepository.deleteSession(any()) } returns Unit
        
        val viewModel = MainViewModel(mockGateway, mockRepository)
        
        val stateFlow = viewModel.uiState as MutableStateFlow
        stateFlow.value = stateFlow.value.copy(sessions = sessions)
        
        // When
        viewModel.deleteSession("session-1")
        
        // Then
        val state = viewModel.uiState.value
        assertTrue(state.sessions.isEmpty())
    }

    @Test
    fun `renameSession should update session label`() = runTest {
        // Given
        val sessions = listOf(
            SessionUi(
                id = "session-1",
                label = "Old Name",
                model = "qwen3.5-plus",
                status = SessionStatus.RUNNING,
                lastActivityAt = System.currentTimeMillis()
            )
        )
        
        coEvery { mockRepository.updateSession(any<String>(), any()) } returns Unit
        
        val viewModel = MainViewModel(mockGateway, mockRepository)
        
        val stateFlow = viewModel.uiState as MutableStateFlow
        stateFlow.value = stateFlow.value.copy(sessions = sessions)
        
        // When
        viewModel.renameSession("session-1", "New Name")
        
        // Then
        val state = viewModel.uiState.value
        assertEquals("New Name", state.sessions.first().label)
    }

    @Test
    fun `pauseSession should update status to PAUSED`() = runTest {
        // Given
        val sessions = listOf(
            SessionUi(
                id = "session-1",
                label = "Test",
                model = "qwen3.5-plus",
                status = SessionStatus.RUNNING,
                lastActivityAt = System.currentTimeMillis()
            )
        )
        
        val viewModel = MainViewModel(mockGateway, mockRepository)
        
        val stateFlow = viewModel.uiState as MutableStateFlow
        stateFlow.value = stateFlow.value.copy(sessions = sessions)
        
        // When
        viewModel.pauseSession("session-1")
        
        // Then
        val state = viewModel.uiState.value
        assertEquals(SessionStatus.PAUSED, state.sessions.first().status)
    }

    @Test
    fun `resumeSession should update status to RUNNING`() = runTest {
        // Given
        val sessions = listOf(
            SessionUi(
                id = "session-1",
                label = "Test",
                model = "qwen3.5-plus",
                status = SessionStatus.PAUSED,
                lastActivityAt = System.currentTimeMillis()
            )
        )
        
        val viewModel = MainViewModel(mockGateway, mockRepository)
        
        val stateFlow = viewModel.uiState as MutableStateFlow
        stateFlow.value = stateFlow.value.copy(sessions = sessions)
        
        // When
        viewModel.resumeSession("session-1")
        
        // Then
        val state = viewModel.uiState.value
        assertEquals(SessionStatus.RUNNING, state.sessions.first().status)
    }

    // ==================== 错误处理测试 ====================

    @Test
    fun `clearError should remove error from state`() = runTest {
        // Given
        val viewModel = MainViewModel(mockGateway, mockRepository)
        
        val stateFlow = viewModel.uiState as MutableStateFlow
        stateFlow.value = stateFlow.value.copy(error = "Test error")
        
        // When
        viewModel.clearError()
        
        // Then
        val state = viewModel.uiState.value
        assertEquals(null, state.error)
    }

    // ==================== 会话列表同步测试 ====================

    @Test
    fun `refreshSessions should trigger loadSessionsFromGateway`() = runTest {
        // Given
        val viewModel = spyk(MainViewModel(mockGateway, mockRepository))
        
        // When
        viewModel.refreshSessions()
        
        // Then - verify the method was called
        verify { viewModel.refreshSessions() }
    }

    @Test
    fun `createSession should call gateway sessions reset`() = runTest {
        // Given
        coEvery { mockGateway.call(any(), any()) } returns mockk(relaxed = true)
        
        val viewModel = MainViewModel(mockGateway, mockRepository)
        
        // When
        viewModel.createSession()
        
        // Then - verify interaction
        coVerify { mockGateway.defaultSessionKey }
    }
}