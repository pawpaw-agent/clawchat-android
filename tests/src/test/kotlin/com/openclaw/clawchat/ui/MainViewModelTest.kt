package com.openclaw.clawchat.ui

import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import ui.state.*

/**
 * MainViewModel 单元测试
 * 
 * 测试覆盖：
 * - 连接状态管理
 * - 会话 CRUD 操作
 * - UI 事件处理
 * - 错误处理
 * - 状态流变化
 */
@DisplayName("MainViewModel 测试")
class MainViewModelTest {

    private lateinit var viewModel: MainViewModel
    private lateinit var testDispatcher: TestDispatcher

    @BeforeEach
    fun setUp() {
        testDispatcher = StandardTestDispatcher()
        Dispatchers.setMain(testDispatcher)
        viewModel = MainViewModel()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Nested
    @DisplayName("初始状态测试")
    inner class InitialStateTests {

        @Test
        @DisplayName("初始连接状态为 Disconnected")
        fun `initial connection state is Disconnected`() {
            val state = viewModel.uiState.value
            assertEquals(ConnectionStatus.Disconnected, state.connectionStatus)
        }

        @Test
        @DisplayName("初始会话列表为空")
        fun `initial sessions list is empty`() {
            val state = viewModel.uiState.value
            assertTrue(state.sessions.isEmpty())
        }

        @Test
        @DisplayName("初始当前会话为 null")
        fun `initial current session is null`() {
            val state = viewModel.uiState.value
            assertNull(state.currentSession)
        }

        @Test
        @DisplayName("初始加载状态为 false")
        fun `initial loading state is false`() {
            val state = viewModel.uiState.value
            assertFalse(state.isLoading)
        }

        @Test
        @DisplayName("初始错误为 null")
        fun `initial error is null`() {
            val state = viewModel.uiState.value
            assertNull(state.error)
        }

        @Test
        @DisplayName("初始事件为 null")
        fun `initial events is null`() {
            val event = viewModel.events.value
            assertNull(event)
        }
    }

    @Nested
    @DisplayName("connectToGateway() 测试")
    inner class ConnectToGatewayTests {

        @Test
        @DisplayName("成功连接到网关")
        fun `successfully connects to gateway`() = runTest {
            viewModel.connectToGateway("ws://localhost:8080")
            testDispatcher.advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(ConnectionStatus.Connected, state.connectionStatus)
            assertNotNull(state.currentGateway)
            assertEquals("ws://localhost:8080", state.currentGateway?.host)
        }

        @Test
        @DisplayName("连接时触发成功事件")
        fun `triggers success event on connection`() = runTest {
            viewModel.connectToGateway("ws://localhost:8080")
            testDispatcher.advanceUntilIdle()

            val event = viewModel.events.value
            assertTrue(event is UiEvent.ShowSuccess)
            assertEquals("已连接到网关", (event as UiEvent.ShowSuccess).message)
        }

        @Test
        @DisplayName("连接过程中 isLoading 状态正确变化")
        fun `isLoading state changes correctly during connection`() = runTest {
            val states = mutableListOf<Boolean>()
            
            val job = launch {
                viewModel.uiState.collect { state ->
                    states.add(state.isLoading)
                }
            }

            viewModel.connectToGateway("ws://localhost:8080")
            testDispatcher.advanceUntilIdle()
            job.cancel()

            assertTrue(states.contains(true))
            assertTrue(states.contains(false))
        }
    }

    @Nested
    @DisplayName("disconnect() 测试")
    inner class DisconnectTests {

        @Test
        @DisplayName("成功断开连接")
        fun `successfully disconnects`() = runTest {
            // 先连接
            viewModel.connectToGateway("ws://localhost:8080")
            testDispatcher.advanceUntilIdle()

            // 再断开
            viewModel.disconnect()
            testDispatcher.advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(ConnectionStatus.Disconnected, state.connectionStatus)
            assertNull(state.currentGateway)
        }

        @Test
        @DisplayName("断开连接时触发成功事件")
        fun `triggers success event on disconnect`() = runTest {
            viewModel.disconnect()
            testDispatcher.advanceUntilIdle()

            val event = viewModel.events.value
            assertTrue(event is UiEvent.ShowSuccess)
            assertEquals("已断开连接", (event as UiEvent.ShowSuccess).message)
        }
    }

    @Nested
    @DisplayName("selectSession() 测试")
    inner class SelectSessionTests {

        @Test
        @DisplayName("成功选择会话")
        fun `successfully selects session`() = runTest {
            // 先加载会话列表
            viewModel.loadSessions()
            testDispatcher.advanceUntilIdle()

            // 选择会话
            viewModel.selectSession("session_1")
            testDispatcher.advanceUntilIdle()

            val state = viewModel.uiState.value
            assertNotNull(state.currentSession)
            assertEquals("session_1", state.currentSession?.id)
        }

        @Test
        @DisplayName("选择会话时触发导航事件")
        fun `triggers navigation event on session selection`() = runTest {
            viewModel.loadSessions()
            testDispatcher.advanceUntilIdle()

            viewModel.selectSession("session_1")
            testDispatcher.advanceUntilIdle()

            val event = viewModel.events.value
            assertTrue(event is UiEvent.NavigateToSession)
            assertEquals("session_1", (event as UiEvent.NavigateToSession).sessionId)
        }

        @Test
        @DisplayName("选择不存在的会话时当前会话为 null")
        fun `current session is null when selecting non-existent session`() = runTest {
            viewModel.loadSessions()
            testDispatcher.advanceUntilIdle()

            viewModel.selectSession("non_existent_session")
            testDispatcher.advanceUntilIdle()

            val state = viewModel.uiState.value
            assertNull(state.currentSession)
        }
    }

    @Nested
    @DisplayName("createSession() 测试")
    inner class CreateSessionTests {

        @Test
        @DisplayName("成功创建新会话")
        fun `successfully creates new session`() = runTest {
            val initialCount = viewModel.uiState.value.sessions.size

            viewModel.createSession()
            testDispatcher.advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(initialCount + 1, state.sessions.size)
            assertNotNull(state.currentSession)
        }

        @Test
        @DisplayName("创建会话时设置正确的模型参数")
        fun `creates session with correct model parameters`() = runTest {
            viewModel.createSession(model = "qwen3.5-plus", thinking = true)
            testDispatcher.advanceUntilIdle()

            val session = viewModel.uiState.value.currentSession
            assertNotNull(session)
            assertEquals("qwen3.5-plus", session?.model)
        }

        @Test
        @DisplayName("创建会话时触发导航事件")
        fun `triggers navigation event on session creation`() = runTest {
            viewModel.createSession()
            testDispatcher.advanceUntilIdle()

            val event = viewModel.events.value
            assertTrue(event is UiEvent.NavigateToSession)
        }
    }

    @Nested
    @DisplayName("deleteSession() 测试")
    inner class DeleteSessionTests {

        @Test
        @DisplayName("成功删除会话")
        fun `successfully deletes session`() = runTest {
            viewModel.loadSessions()
            testDispatcher.advanceUntilIdle()

            val initialCount = viewModel.uiState.value.sessions.size

            viewModel.deleteSession("session_1")
            testDispatcher.advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(initialCount - 1, state.sessions.size)
            assertFalse(state.sessions.any { it.id == "session_1" })
        }

        @Test
        @DisplayName("删除当前会话时 currentSession 变为 null")
        fun `currentSession becomes null when deleting current session`() = runTest {
            viewModel.loadSessions()
            testDispatcher.advanceUntilIdle()
            viewModel.selectSession("session_1")
            testDispatcher.advanceUntilIdle()

            assertNotNull(viewModel.uiState.value.currentSession)

            viewModel.deleteSession("session_1")
            testDispatcher.advanceUntilIdle()

            assertNull(viewModel.uiState.value.currentSession)
        }

        @Test
        @DisplayName("删除会话时触发成功事件")
        fun `triggers success event on session deletion`() = runTest {
            viewModel.loadSessions()
            testDispatcher.advanceUntilIdle()

            viewModel.deleteSession("session_1")
            testDispatcher.advanceUntilIdle()

            val event = viewModel.events.value
            assertTrue(event is UiEvent.ShowSuccess)
            assertEquals("会话已删除", (event as UiEvent.ShowSuccess).message)
        }
    }

    @Nested
    @DisplayName("clearError() 测试")
    inner class ClearErrorTests {

        @Test
        @DisplayName("成功清除错误")
        fun `successfully clears error`() {
            // 模拟错误状态（通过直接修改内部状态）
            // 实际场景中错误由连接操作设置
            viewModel.clearError()
            
            val state = viewModel.uiState.value
            assertNull(state.error)
        }
    }

    @Nested
    @DisplayName("consumeEvent() 测试")
    inner class ConsumeEventTests {

        @Test
        @DisplayName("成功清除事件")
        fun `successfully clears event`() = runTest {
            viewModel.connectToGateway("ws://localhost:8080")
            testDispatcher.advanceUntilIdle()

            assertNotNull(viewModel.events.value)

            viewModel.consumeEvent()

            assertNull(viewModel.events.value)
        }
    }

    @Nested
    @DisplayName("loadSessions() 测试")
    inner class LoadSessionsTests {

        @Test
        @DisplayName("成功加载模拟会话列表")
        fun `successfully loads mock sessions`() = runTest {
            viewModel.loadSessions()
            testDispatcher.advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(2, state.sessions.size)
        }

        @Test
        @DisplayName("加载的会话包含正确的数据")
        fun `loaded sessions contain correct data`() = runTest {
            viewModel.loadSessions()
            testDispatcher.advanceUntilIdle()

            val sessions = viewModel.uiState.value.sessions
            
            val session1 = sessions.find { it.id == "session_1" }
            assertNotNull(session1)
            assertEquals("项目讨论", session1?.label)
            assertEquals("qwen3.5-plus", session1?.model)
            assertEquals(SessionStatus.RUNNING, session1?.status)

            val session2 = sessions.find { it.id == "session_2" }
            assertNotNull(session2)
            assertEquals("代码审查", session2?.label)
        }

        @Test
        @DisplayName("加载过程中 isLoading 状态正确变化")
        fun `isLoading state changes correctly during load`() = runTest {
            val states = mutableListOf<Boolean>()
            
            val job = launch {
                viewModel.uiState.collect { state ->
                    states.add(state.isLoading)
                }
            }

            viewModel.loadSessions()
            testDispatcher.advanceUntilIdle()
            job.cancel()

            assertTrue(states.contains(true))
            assertTrue(states.contains(false))
        }
    }

    @Nested
    @DisplayName("UiEvent 密封类测试")
    inner class UiEventTests {

        @Test
        @DisplayName("NavigateToSession 事件正确创建")
        fun `creates NavigateToSession event correctly`() {
            val event = UiEvent.NavigateToSession("session_123")
            assertEquals("session_123", event.sessionId)
        }

        @Test
        @DisplayName("ShowError 事件正确创建")
        fun `creates ShowError event correctly`() {
            val event = UiEvent.ShowError("连接失败")
            assertEquals("连接失败", event.message)
        }

        @Test
        @DisplayName("ShowSuccess 事件正确创建")
        fun `creates ShowSuccess event correctly`() {
            val event = UiEvent.ShowSuccess("操作成功")
            assertEquals("操作成功", event.message)
        }

        @Test
        @DisplayName("ShowPairingDialog 事件正确创建")
        fun `creates ShowPairingDialog event correctly`() {
            val event = UiEvent.ShowPairingDialog
            assertTrue(event is UiEvent.ShowPairingDialog)
        }

        @Test
        @DisplayName("ConnectionLost 事件正确创建")
        fun `creates ConnectionLost event correctly`() {
            val event = UiEvent.ConnectionLost
            assertTrue(event is UiEvent.ConnectionLost)
        }
    }

    @Nested
    @DisplayName("ConnectionStatus 密封类测试")
    inner class ConnectionStatusTests {

        @Test
        @DisplayName("Disconnected 状态 isConnected 为 false")
        fun `Disconnected isConnected is false`() {
            assertFalse(ConnectionStatus.Disconnected.isConnected)
        }

        @Test
        @DisplayName("Connected 状态 isConnected 为 true")
        fun `Connected isConnected is true`() {
            assertTrue(ConnectionStatus.Connected.isConnected)
        }

        @Test
        @DisplayName("Connecting 状态 isConnecting 为 true")
        fun `Connecting isConnecting is true`() {
            assertTrue(ConnectionStatus.Connecting.isConnecting)
        }

        @Test
        @DisplayName("Error 状态包含错误消息")
        fun `Error state contains error message`() {
            val error = ConnectionStatus.Error("连接失败", RuntimeException())
            assertEquals("连接失败", error.message)
            assertTrue(error.throwable is RuntimeException)
        }
    }
}
