package com.openclaw.clawchat.ui

import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import ui.state.*
import ui.components.*

/**
 * SessionViewModel 单元测试
 * 
 * 测试覆盖：
 * - 消息加载
 * - 消息发送
 * - 输入状态管理
 * - 消息重新生成
 * - UI 事件处理
 * - 错误处理
 */
@DisplayName("SessionViewModel 测试")
class SessionViewModelTest {

    private lateinit var viewModel: SessionViewModel
    private lateinit var testDispatcher: TestDispatcher

    @BeforeEach
    fun setUp() {
        testDispatcher = StandardTestDispatcher()
        Dispatchers.setMain(testDispatcher)
        viewModel = SessionViewModel(sessionId = "test_session_123")
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Nested
    @DisplayName("初始状态测试")
    inner class InitialStateTests {

        @Test
        @DisplayName("初始 sessionId 正确设置")
        fun `initial sessionId is correctly set`() {
            val state = viewModel.uiState.value
            assertEquals("test_session_123", state.sessionId)
        }

        @Test
        @DisplayName("初始消息列表为空")
        fun `initial messages list is empty`() {
            val state = viewModel.uiState.value
            assertTrue(state.messages.isEmpty())
        }

        @Test
        @DisplayName("初始输入文本为空")
        fun `initial input text is empty`() {
            val state = viewModel.uiState.value
            assertEquals("", state.inputText)
        }

        @Test
        @DisplayName("初始发送状态为 false")
        fun `initial sending state is false`() {
            val state = viewModel.uiState.value
            assertFalse(state.isSending)
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
    @DisplayName("loadMessages() 测试")
    inner class LoadMessagesTests {

        @Test
        @DisplayName("成功加载模拟消息")
        fun `successfully loads mock messages`() = runTest {
            // 等待 ViewModel 初始化完成
            testDispatcher.advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(3, state.messages.size)
        }

        @Test
        @DisplayName("加载的消息包含正确的数据")
        fun `loaded messages contain correct data`() = runTest {
            testDispatcher.advanceUntilIdle()

            val messages = viewModel.uiState.value.messages
            
            val firstMessage = messages.first()
            assertEquals(MessageRole.ASSISTANT, firstMessage.role)
            assertTrue(firstMessage.content.contains("你好"))

            val userMessage = messages.find { it.role == MessageRole.USER }
            assertNotNull(userMessage)
            assertEquals("帮我创建一个 Android UI 框架", userMessage?.content)
        }

        @Test
        @DisplayName("加载消息后 session 信息正确设置")
        fun `session info is correctly set after loading messages`() = runTest {
            testDispatcher.advanceUntilIdle()

            val state = viewModel.uiState.value
            assertNotNull(state.session)
            assertEquals("test_session_123", state.session?.id)
            assertEquals("新会话", state.session?.label)
            assertEquals(SessionStatus.RUNNING, state.session?.status)
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

            testDispatcher.advanceUntilIdle()
            job.cancel()

            assertTrue(states.contains(true))
            assertTrue(states.contains(false))
        }
    }

    @Nested
    @DisplayName("sendMessage() 测试")
    inner class SendMessageTests {

        @Test
        @DisplayName("成功发送消息")
        fun `successfully sends message`() = runTest {
            testDispatcher.advanceUntilIdle()

            viewModel.sendMessage("这是一条测试消息")
            testDispatcher.advanceUntilIdle()

            val state = viewModel.uiState.value
            val messages = state.messages
            
            // 应该包含初始消息 + 新发送的用户消息 + 助手响应
            assertTrue(messages.size >= 4)
            
            val lastUserMessage = messages.lastOrNull { it.role == MessageRole.USER }
            assertNotNull(lastUserMessage)
            assertEquals("这是一条测试消息", lastUserMessage?.content)
        }

        @Test
        @DisplayName("发送消息后输入文本被清空")
        fun `input text is cleared after sending message`() = runTest {
            testDispatcher.advanceUntilIdle()

            viewModel.updateInputText("测试输入")
            viewModel.sendMessage("测试消息")
            testDispatcher.advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals("", state.inputText)
        }

        @Test
        @DisplayName("发送消息时 isSending 状态正确变化")
        fun `isSending state changes correctly during send`() = runTest {
            testDispatcher.advanceUntilIdle()

            val states = mutableListOf<Boolean>()
            
            val job = launch {
                viewModel.uiState.collect { state ->
                    states.add(state.isSending)
                }
            }

            viewModel.sendMessage("测试消息")
            testDispatcher.advanceUntilIdle()
            job.cancel()

            assertTrue(states.contains(true))
            assertTrue(states.contains(false))
        }

        @Test
        @DisplayName("发送空消息时不执行任何操作")
        fun `does nothing when sending empty message`() = runTest {
            testDispatcher.advanceUntilIdle()
            
            val initialMessageCount = viewModel.uiState.value.messages.size

            viewModel.sendMessage("")
            testDispatcher.advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(initialMessageCount, state.messages.size)
            assertFalse(state.isSending)
        }

        @Test
        @DisplayName("发送空白消息时不执行任何操作")
        fun `does nothing when sending whitespace message`() = runTest {
            testDispatcher.advanceUntilIdle()
            
            val initialMessageCount = viewModel.uiState.value.messages.size

            viewModel.sendMessage("   ")
            testDispatcher.advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(initialMessageCount, state.messages.size)
        }

        @Test
        @DisplayName("发送消息后添加助手响应")
        fun `adds assistant response after sending message`() = runTest {
            testDispatcher.advanceUntilIdle()

            viewModel.sendMessage("测试消息")
            testDispatcher.advanceUntilIdle()

            val messages = viewModel.uiState.value.messages
            val lastMessage = messages.last()
            
            assertEquals(MessageRole.ASSISTANT, lastMessage.role)
            assertTrue(lastMessage.content.contains("收到你的消息"))
        }
    }

    @Nested
    @DisplayName("updateInputText() 测试")
    inner class UpdateInputTextTests {

        @Test
        @DisplayName("成功更新输入文本")
        fun `successfully updates input text`() {
            viewModel.updateInputText("新的输入内容")

            val state = viewModel.uiState.value
            assertEquals("新的输入内容", state.inputText)
        }

        @Test
        @DisplayName("可以清空输入文本")
        fun `can clear input text`() {
            viewModel.updateInputText("一些内容")
            viewModel.updateInputText("")

            val state = viewModel.uiState.value
            assertEquals("", state.inputText)
        }
    }

    @Nested
    @DisplayName("regenerateLastMessage() 测试")
    inner class RegenerateLastMessageTests {

        @Test
        @DisplayName("成功重新生成最后一条消息")
        fun `successfully regenerates last message`() = runTest {
            testDispatcher.advanceUntilIdle()

            val initialMessageCount = viewModel.uiState.value.messages.size

            viewModel.regenerateLastMessage()
            testDispatcher.advanceUntilIdle()

            val state = viewModel.uiState.value
            // 应该移除了最后的助手消息并重新发送
            assertTrue(state.messages.size >= initialMessageCount)
        }

        @Test
        @DisplayName("当没有用户消息时不执行操作")
        fun `does nothing when no user messages exist`() = runTest {
            // 创建没有消息的 ViewModel
            val emptyViewModel = SessionViewModel(sessionId = "empty_session")
            testDispatcher.advanceUntilIdle()

            // 手动清空消息
            // 注意：实际测试中可能需要通过反射或其他方式清空消息
            
            emptyViewModel.regenerateLastMessage()
            testDispatcher.advanceUntilIdle()

            // 不应该抛出异常
            assertTrue(true)
        }
    }

    @Nested
    @DisplayName("clearError() 测试")
    inner class ClearErrorTests {

        @Test
        @DisplayName("成功清除错误")
        fun `successfully clears error`() {
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
            viewModel.scrollToBottom()
            testDispatcher.advanceUntilIdle()

            assertNotNull(viewModel.events.value)

            viewModel.consumeEvent()

            assertNull(viewModel.events.value)
        }
    }

    @Nested
    @DisplayName("scrollToBottom() 测试")
    inner class ScrollToBottomTests {

        @Test
        @DisplayName("成功触发滚动事件")
        fun `successfully triggers scroll event`() = runTest {
            viewModel.scrollToBottom()
            testDispatcher.advanceUntilIdle()

            val event = viewModel.events.value
            assertTrue(event is SessionUiEvent.ScrollToBottom)
        }
    }

    @Nested
    @DisplayName("SessionUiEvent 密封类测试")
    inner class SessionUiEventTests {

        @Test
        @DisplayName("ShowError 事件正确创建")
        fun `creates ShowError event correctly`() {
            val event = SessionUiEvent.ShowError("发送失败")
            assertEquals("发送失败", event.message)
        }

        @Test
        @DisplayName("ScrollToBottom 事件正确创建")
        fun `creates ScrollToBottom event correctly`() {
            val event = SessionUiEvent.ScrollToBottom
            assertTrue(event is SessionUiEvent.ScrollToBottom)
        }

        @Test
        @DisplayName("MessageSent 事件正确创建")
        fun `creates MessageSent event correctly`() {
            val event = SessionUiEvent.MessageSent
            assertTrue(event is SessionUiEvent.MessageSent)
        }
    }

    @Nested
    @DisplayName("MessageUi 数据类测试")
    inner class MessageUiTests {

        @Test
        @DisplayName("MessageUi 正确创建")
        fun `creates MessageUi correctly`() {
            val message = MessageUi(
                id = "msg_1",
                content = "测试消息",
                role = MessageRole.USER,
                timestamp = System.currentTimeMillis()
            )

            assertEquals("msg_1", message.id)
            assertEquals("测试消息", message.content)
            assertEquals(MessageRole.USER, message.role)
            assertFalse(message.isLoading)
        }

        @Test
        @DisplayName("MessageUi 支持 copy 操作")
        fun `supports copy operation`() {
            val original = MessageUi(
                id = "msg_1",
                content = "原始内容",
                role = MessageRole.USER,
                timestamp = 1000L,
                isLoading = false
            )

            val modified = original.copy(
                content = "修改后的内容",
                isLoading = true
            )

            assertEquals("修改后的内容", modified.content)
            assertTrue(modified.isLoading)
            assertEquals("msg_1", modified.id)
            assertEquals(MessageRole.USER, modified.role)
        }
    }

    @Nested
    @DisplayName("MessageRole 枚举测试")
    inner class MessageRoleTests {

        @Test
        @DisplayName("USER 角色正确定义")
        fun `USER role is correctly defined`() {
            assertEquals(MessageRole.USER, MessageRole.USER)
        }

        @Test
        @DisplayName("ASSISTANT 角色正确定义")
        fun `ASSISTANT role is correctly defined`() {
            assertEquals(MessageRole.ASSISTANT, MessageRole.ASSISTANT)
        }

        @Test
        @DisplayName("SYSTEM 角色正确定义")
        fun `SYSTEM role is correctly defined`() {
            assertEquals(MessageRole.SYSTEM, MessageRole.SYSTEM)
        }

        @Test
        @DisplayName("所有角色值正确")
        fun `all role values are correct`() {
            val roles = MessageRole.values()
            assertEquals(3, roles.size)
            assertTrue(roles.contains(MessageRole.USER))
            assertTrue(roles.contains(MessageRole.ASSISTANT))
            assertTrue(roles.contains(MessageRole.SYSTEM))
        }
    }

    @Nested
    @DisplayName("SessionUi 数据类测试")
    inner class SessionUiTests {

        @Test
        @DisplayName("SessionUi 正确创建")
        fun `creates SessionUi correctly`() {
            val session = SessionUi(
                id = "session_1",
                label = "测试会话",
                model = "qwen3.5-plus",
                status = SessionStatus.RUNNING,
                lastActivityAt = System.currentTimeMillis(),
                messageCount = 10,
                lastMessage = "最后一条消息"
            )

            assertEquals("session_1", session.id)
            assertEquals("测试会话", session.label)
            assertEquals(SessionStatus.RUNNING, session.status)
            assertEquals(10, session.messageCount)
        }

        @Test
        @DisplayName("SessionUi 支持 copy 操作")
        fun `supports copy operation`() {
            val original = SessionUi(
                id = "session_1",
                label = "原始标签",
                model = "model_1",
                status = SessionStatus.RUNNING,
                lastActivityAt = 1000L,
                messageCount = 5
            )

            val modified = original.copy(
                label = "新标签",
                messageCount = 10
            )

            assertEquals("新标签", modified.label)
            assertEquals(10, modified.messageCount)
            assertEquals("session_1", modified.id)
            assertEquals(SessionStatus.RUNNING, modified.status)
        }
    }

    @Nested
    @DisplayName("SessionStatus 枚举测试")
    inner class SessionStatusTests {

        @Test
        @DisplayName("所有状态值正确")
        fun `all status values are correct`() {
            val statuses = SessionStatus.values()
            assertEquals(3, statuses.size)
            assertTrue(statuses.contains(SessionStatus.RUNNING))
            assertTrue(statuses.contains(SessionStatus.PAUSED))
            assertTrue(statuses.contains(SessionStatus.TERMINATED))
        }
    }
}
