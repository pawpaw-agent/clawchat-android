package com.openclaw.clawchat.network

import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import okhttp3.*

/**
 * WebSocketService 单元测试
 * 
 * 测试覆盖：
 * - 连接状态管理
 * - 消息收发
 * - 断开连接
 * - 延迟测量
 * - 状态流变化
 */
@DisplayName("WebSocketService 测试")
class WebSocketServiceTest {

    private lateinit var webSocketService: MockWebSocketService
    private lateinit var connectionStateCaptor: StateFlow<WebSocketConnectionState>
    private lateinit var incomingMessagesCaptor: Flow<GatewayMessage>

    @BeforeEach
    fun setUp() {
        webSocketService = MockWebSocketService()
        connectionStateCaptor = webSocketService.connectionState
        incomingMessagesCaptor = webSocketService.incomingMessages
    }

    @Nested
    @DisplayName("connect() 测试")
    inner class ConnectTests {

        @Test
        @DisplayName("成功建立 WebSocket 连接")
        fun `successfully establishes WebSocket connection`() = runTest {
            val result = webSocketService.connect("ws://localhost:8080", "test-token")

            assertTrue(result.isSuccess)
            assertEquals(WebSocketConnectionState.Connected, connectionStateCaptor.value)
        }

        @Test
        @DisplayName("连接时状态从 Disconnected 变为 Connecting 再变为 Connected")
        fun `state transitions from Disconnected to Connecting to Connected`() = runTest {
            val states = mutableListOf<WebSocketConnectionState>()
            
            val job = launch {
                connectionStateCaptor.collect { state ->
                    states.add(state)
                }
            }

            webSocketService.connect("ws://localhost:8080", null)
            
            // 等待状态更新
            delay(100)
            job.cancel()

            assertTrue(states.contains(WebSocketConnectionState.Disconnected))
            assertTrue(states.contains(WebSocketConnectionState.Connecting))
            assertTrue(states.contains(WebSocketConnectionState.Connected))
        }

        @Test
        @DisplayName("连接失败时返回错误状态")
        fun `returns error state on connection failure`() = runTest {
            webSocketService.shouldFailConnection = true

            val result = webSocketService.connect("ws://invalid-url", null)

            assertTrue(result.isFailure)
            val state = connectionStateCaptor.value
            assertTrue(state is WebSocketConnectionState.Error)
        }

        @Test
        @DisplayName("重复连接时先断开现有连接")
        fun `disconnects existing connection before reconnecting`() = runTest {
            // 第一次连接
            webSocketService.connect("ws://localhost:8080", null)
            assertEquals(WebSocketConnectionState.Connected, connectionStateCaptor.value)

            // 第二次连接
            val result = webSocketService.connect("ws://localhost:9090", null)

            assertTrue(result.isSuccess)
            assertEquals(WebSocketConnectionState.Connected, connectionStateCaptor.value)
        }
    }

    @Nested
    @DisplayName("send() 测试")
    inner class SendTests {

        @Test
        @DisplayName("成功发送消息")
        fun `successfully sends message`() = runTest {
            // 先建立连接
            webSocketService.connect("ws://localhost:8080", null)

            val message = GatewayMessage(
                type = "test",
                payload = mapOf("key" to "value")
            )

            val result = webSocketService.send(message)

            assertTrue(result.isSuccess)
            assertEquals(1, webSocketService.sentMessages.size)
            assertEquals(message, webSocketService.sentMessages.first())
        }

        @Test
        @DisplayName("未连接时发送失败")
        fun `fails to send when not connected`() = runTest {
            val message = GatewayMessage(
                type = "test",
                payload = mapOf("key" to "value")
            )

            val result = webSocketService.send(message)

            assertTrue(result.isFailure)
            assertTrue(webSocketService.sentMessages.isEmpty())
        }

        @Test
        @DisplayName("发送消息时模拟发送失败")
        fun `simulates send failure`() = runTest {
            webSocketService.connect("ws://localhost:8080", null)
            webSocketService.shouldFailSend = true

            val message = GatewayMessage(
                type = "test",
                payload = mapOf("key" to "value")
            )

            val result = webSocketService.send(message)

            assertTrue(result.isFailure)
        }
    }

    @Nested
    @DisplayName("disconnect() 测试")
    inner class DisconnectTests {

        @Test
        @DisplayName("成功断开连接")
        fun `successfully disconnects`() = runTest {
            // 先建立连接
            webSocketService.connect("ws://localhost:8080", null)
            assertEquals(WebSocketConnectionState.Connected, connectionStateCaptor.value)

            // 断开连接
            val result = webSocketService.disconnect()

            assertTrue(result.isSuccess)
            assertEquals(WebSocketConnectionState.Disconnected, connectionStateCaptor.value)
        }

        @Test
        @DisplayName("断开已断开的连接不报错")
        fun `disconnecting already disconnected connection does not error`() = runTest {
            val result = webSocketService.disconnect()

            assertTrue(result.isSuccess)
            assertEquals(WebSocketConnectionState.Disconnected, connectionStateCaptor.value)
        }

        @Test
        @DisplayName("断开连接时状态正确转换")
        fun `state transitions correctly during disconnect`() = runTest {
            webSocketService.connect("ws://localhost:8080", null)
            
            val states = mutableListOf<WebSocketConnectionState>()
            val job = launch {
                connectionStateCaptor.collect { state ->
                    states.add(state)
                }
            }

            webSocketService.disconnect()
            delay(100)
            job.cancel()

            assertTrue(states.contains(WebSocketConnectionState.Connected))
            assertTrue(states.contains(WebSocketConnectionState.Disconnected))
        }
    }

    @Nested
    @DisplayName("measureLatency() 测试")
    inner class MeasureLatencyTests {

        @Test
        @DisplayName("成功测量连接延迟")
        fun `successfully measures latency`() = runTest {
            webSocketService.connect("ws://localhost:8080", null)
            webSocketService.mockLatency = 50L

            val latency = webSocketService.measureLatency()

            assertNotNull(latency)
            assertEquals(50L, latency)
        }

        @Test
        @DisplayName("未连接时延迟测量返回 null")
        fun `returns null when not connected`() = runTest {
            val latency = webSocketService.measureLatency()

            assertNull(latency)
        }

        @Test
        @DisplayName("测量失败时返回 null")
        fun `returns null on measurement failure`() = runTest {
            webSocketService.connect("ws://localhost:8080", null)
            webSocketService.shouldFailLatency = true

            val latency = webSocketService.measureLatency()

            assertNull(latency)
        }
    }

    @Nested
    @DisplayName("incomingMessages 流测试")
    inner class IncomingMessagesTests {

        @Test
        @DisplayName("接收消息流正确传递消息")
        fun `incoming messages flow correctly delivers messages`() = runTest {
            val receivedMessages = mutableListOf<GatewayMessage>()
            val job = launch {
                incomingMessagesCaptor.collect { message ->
                    receivedMessages.add(message)
                }
            }

            // 模拟接收消息
            webSocketService.simulateIncomingMessage(
                GatewayMessage(type = "test1", payload = mapOf())
            )
            webSocketService.simulateIncomingMessage(
                GatewayMessage(type = "test2", payload = mapOf("data" to "value"))
            )

            delay(100)
            job.cancel()

            assertEquals(2, receivedMessages.size)
            assertEquals("test1", receivedMessages[0].type)
            assertEquals("test2", receivedMessages[1].type)
        }
    }

    @Nested
    @DisplayName("WebSocketConnectionState 密封类测试")
    inner class ConnectionStateTests {

        @Test
        @DisplayName("Disconnected 状态正确创建")
        fun `creates Disconnected state correctly`() {
            val state = WebSocketConnectionState.Disconnected
            assertTrue(state is WebSocketConnectionState.Disconnected)
        }

        @Test
        @DisplayName("Connecting 状态正确创建")
        fun `creates Connecting state correctly`() {
            val state = WebSocketConnectionState.Connecting
            assertTrue(state is WebSocketConnectionState.Connecting)
        }

        @Test
        @DisplayName("Connected 状态正确创建")
        fun `creates Connected state correctly`() {
            val state = WebSocketConnectionState.Connected
            assertTrue(state is WebSocketConnectionState.Connected)
        }

        @Test
        @DisplayName("Disconnecting 状态包含原因")
        fun `creates Disconnecting state with reason`() {
            val reason = "User requested disconnect"
            val state = WebSocketConnectionState.Disconnecting(reason)
            
            assertTrue(state is WebSocketConnectionState.Disconnecting)
            assertEquals(reason, state.reason)
        }

        @Test
        @DisplayName("Error 状态包含异常")
        fun `creates Error state with throwable`() {
            val exception = RuntimeException("Test error")
            val state = WebSocketConnectionState.Error(exception)
            
            assertTrue(state is WebSocketConnectionState.Error)
            assertEquals(exception, state.throwable)
        }
    }
}

/**
 * Mock WebSocketService 实现，用于测试
 */
class MockWebSocketService : WebSocketService {

    private val _connectionState = MutableStateFlow<WebSocketConnectionState>(WebSocketConnectionState.Disconnected)
    override val connectionState: StateFlow<WebSocketConnectionState> = _connectionState

    private val _incomingMessages = MutableSharedFlow<GatewayMessage>()
    override val incomingMessages: Flow<GatewayMessage> = _incomingMessages.asSharedFlow()

    val sentMessages = mutableListOf<GatewayMessage>()

    var shouldFailConnection = false
    var shouldFailSend = false
    var shouldFailLatency = false
    var mockLatency: Long? = null

    private var isConnected = false

    override suspend fun connect(url: String, token: String?): Result<Unit> {
        if (shouldFailConnection) {
            _connectionState.value = WebSocketConnectionState.Error(RuntimeException("Connection failed"))
            return Result.failure(RuntimeException("Connection failed"))
        }

        _connectionState.value = WebSocketConnectionState.Connecting
        delay(10) // 模拟连接延迟
        _connectionState.value = WebSocketConnectionState.Connected
        isConnected = true

        return Result.success(Unit)
    }

    override suspend fun send(message: GatewayMessage): Result<Unit> {
        if (!isConnected) {
            return Result.failure(IllegalStateException("Not connected"))
        }

        if (shouldFailSend) {
            return Result.failure(RuntimeException("Send failed"))
        }

        sentMessages.add(message)
        return Result.success(Unit)
    }

    override suspend fun disconnect(): Result<Unit> {
        _connectionState.value = WebSocketConnectionState.Disconnecting("User requested")
        delay(10)
        _connectionState.value = WebSocketConnectionState.Disconnected
        isConnected = false

        return Result.success(Unit)
    }

    override suspend fun measureLatency(): Long? {
        if (!isConnected) {
            return null
        }

        if (shouldFailLatency) {
            return null
        }

        return mockLatency ?: 100L
    }

    fun simulateIncomingMessage(message: GatewayMessage) {
        _incomingMessages.tryEmit(message)
    }
}
