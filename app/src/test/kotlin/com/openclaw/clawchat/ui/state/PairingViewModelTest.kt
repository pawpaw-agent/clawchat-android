package com.openclaw.clawchat.ui.state

import com.openclaw.clawchat.network.protocol.GatewayConnection
import com.openclaw.clawchat.security.SecurityModule
import com.openclaw.clawchat.security.SecurityStatus
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * PairingViewModel 单元测试
 * 
 * 测试覆盖：
 * - Token 直连模式
 * - 设备配对模式
 * - 状态管理
 * - 错误处理
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PairingViewModelTest {

    private lateinit var mockSecurityModule: SecurityModule
    private lateinit var mockGateway: GatewayConnection
    
    @Before
    fun setup() {
        MockKAnnotations.init(this)
        mockSecurityModule = mockk(relaxed = true)
        mockGateway = mockk(relaxed = true)
        
        // 默认行为
        coEvery { mockSecurityModule.initialize() } returns SecurityStatus(
            isInitialized = true,
            isPaired = false,
            hasKeyPair = true,
            deviceId = "test-device-id"
        )
        every { mockSecurityModule.getDeviceId() } returns "test-device-id"
        every { mockSecurityModule.getPublicKeyBase64Url() } returns "test-public-key"
        every { mockSecurityModule.getSecurityStatus() } returns SecurityStatus(
            isInitialized = true,
            isPaired = false,
            hasKeyPair = true,
            deviceId = "test-device-id"
        )
    }
    
    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ==================== 初始化测试 ====================

    @Test
    fun `initial state should have default values`() = runTest {
        // When
        val viewModel = PairingViewModel(mockSecurityModule, mockGateway)
        
        // Then
        val state = viewModel.state.value
        assertEquals(ConnectMode.TOKEN, state.connectMode)
        assertEquals("", state.gatewayUrl)
        assertEquals("", state.token)
        assertFalse(state.isPairing)
        assertFalse(state.isInitializing)
    }

    // ==================== 连接模式测试 ====================

    @Test
    fun `setConnectMode should update state`() = runTest {
        // Given
        val viewModel = PairingViewModel(mockSecurityModule, mockGateway)
        
        // When
        viewModel.setConnectMode(ConnectMode.PAIRING)
        
        // Then
        val state = viewModel.state.value
        assertEquals(ConnectMode.PAIRING, state.connectMode)
    }

    @Test
    fun `setGatewayUrl should update state`() = runTest {
        // Given
        val viewModel = PairingViewModel(mockSecurityModule, mockGateway)
        
        // When
        viewModel.setGatewayUrl("ws://localhost:18789")
        
        // Then
        val state = viewModel.state.value
        assertEquals("ws://localhost:18789", state.gatewayUrl)
    }

    @Test
    fun `setToken should update state`() = runTest {
        // Given
        val viewModel = PairingViewModel(mockSecurityModule, mockGateway)
        
        // When
        viewModel.setToken("test-token-123")
        
        // Then
        val state = viewModel.state.value
        assertEquals("test-token-123", state.token)
    }

    // ==================== Token 直连测试 ====================

    @Test
    fun `connectWithToken should fail with empty URL`() = runTest {
        // Given
        val viewModel = PairingViewModel(mockSecurityModule, mockGateway)
        viewModel.setToken("test-token")
        
        // When
        viewModel.connectWithToken()
        
        // Then
        val state = viewModel.state.value
        assertFalse(state.isPairing)
        // Error should be emitted via events
    }

    @Test
    fun `connectWithToken should fail with empty token`() = runTest {
        // Given
        val viewModel = PairingViewModel(mockSecurityModule, mockGateway)
        viewModel.setGatewayUrl("ws://localhost:18789")
        
        // When
        viewModel.connectWithToken()
        
        // Then
        val state = viewModel.state.value
        assertFalse(state.isPairing)
    }

    @Test
    fun `connectWithToken should initialize security module`() = runTest {
        // Given
        viewModel.setGatewayUrl("ws://localhost:18789")
        viewModel.setToken("test-token")
        coEvery { mockGateway.connect(any(), any()) } returns Result.success(Unit)
        
        // When
        viewModel.connectWithToken()
        
        // Then
        coVerify { mockSecurityModule.initialize() }
    }

    @Test
    fun `connectWithToken should update state on success`() = runTest {
        // Given
        coEvery { mockGateway.connect(any(), any()) } returns Result.success(Unit)
        
        val viewModel = PairingViewModel(mockSecurityModule, mockGateway)
        viewModel.setGatewayUrl("ws://localhost:18789")
        viewModel.setToken("test-token")
        
        // When
        viewModel.connectWithToken()
        
        // Then
        val state = viewModel.state.value
        assertFalse(state.isPairing)
        assertTrue(state.status is PairingStatus.Approved)
    }

    @Test
    fun `connectWithToken should update state on failure`() = runTest {
        // Given
        val error = Exception("Connection refused")
        coEvery { mockGateway.connect(any(), any()) } returns Result.failure(error)
        
        val viewModel = PairingViewModel(mockSecurityModule, mockGateway)
        viewModel.setGatewayUrl("ws://localhost:18789")
        viewModel.setToken("test-token")
        
        // When
        viewModel.connectWithToken()
        
        // Then
        val state = viewModel.state.value
        assertFalse(state.isPairing)
        assertTrue(state.status is PairingStatus.Error)
    }

    // ==================== 设备配对测试 ====================

    @Test
    fun `initializePairing should update state with device info`() = runTest {
        // Given
        val viewModel = PairingViewModel(mockSecurityModule, mockGateway)
        
        // When
        viewModel.initializePairing()
        
        // Then
        val state = viewModel.state.value
        assertEquals("test-device-id", state.deviceId)
        assertEquals("test-public-key", state.publicKey)
        assertFalse(state.isInitializing)
        assertTrue(state.status is PairingStatus.WaitingForApproval)
    }

    @Test
    fun `initializePairing should handle failure`() = runTest {
        // Given
        coEvery { mockSecurityModule.initialize() } throws Exception("Init failed")
        
        val viewModel = PairingViewModel(mockSecurityModule, mockGateway)
        
        // When
        viewModel.initializePairing()
        
        // Then
        val state = viewModel.state.value
        assertFalse(state.isInitializing)
        assertTrue(state.status is PairingStatus.Error)
    }

    @Test
    fun `startPairing should fail with empty URL`() = runTest {
        // Given
        val viewModel = PairingViewModel(mockSecurityModule, mockGateway)
        
        // When
        viewModel.startPairing()
        
        // Then
        val state = viewModel.state.value
        assertFalse(state.isPairing)
    }

    @Test
    fun `startPairing should connect to gateway`() = runTest {
        // Given
        coEvery { mockGateway.connect(any(), any()) } returns Result.success(Unit)
        
        val viewModel = PairingViewModel(mockSecurityModule, mockGateway)
        viewModel.setGatewayUrl("ws://localhost:18789")
        
        // When
        viewModel.startPairing()
        
        // Then
        coVerify { mockGateway.connect(any(), null) }
    }

    @Test
    fun `startPairing should update state on success`() = runTest {
        // Given
        coEvery { mockGateway.connect(any(), any()) } returns Result.success(Unit)
        
        val viewModel = PairingViewModel(mockSecurityModule, mockGateway)
        viewModel.setGatewayUrl("ws://localhost:18789")
        
        // When
        viewModel.startPairing()
        
        // Then
        val state = viewModel.state.value
        assertFalse(state.isPairing)
        assertTrue(state.status is PairingStatus.WaitingForApproval)
    }

    @Test
    fun `startPairing should update state on failure`() = runTest {
        // Given
        coEvery { mockGateway.connect(any(), any()) } returns Result.failure(Exception("Failed"))
        
        val viewModel = PairingViewModel(mockSecurityModule, mockGateway)
        viewModel.setGatewayUrl("ws://localhost:18789")
        
        // When
        viewModel.startPairing()
        
        // Then
        val state = viewModel.state.value
        assertFalse(state.isPairing)
        assertTrue(state.status is PairingStatus.Error)
    }

    // ==================== 取消配对测试 ====================

    @Test
    fun `cancelPairing should disconnect gateway`() = runTest {
        // Given
        val viewModel = PairingViewModel(mockSecurityModule, mockGateway)
        
        // When
        viewModel.cancelPairing()
        
        // Then
        coVerify { mockGateway.disconnect() }
    }

    @Test
    fun `cancelPairing should reset state`() = runTest {
        // Given
        val viewModel = PairingViewModel(mockSecurityModule, mockGateway)
        
        // Set some state first
        viewModel.setGatewayUrl("ws://localhost:18789")
        viewModel.setConnectMode(ConnectMode.PAIRING)
        
        // When
        viewModel.cancelPairing()
        
        // Then
        val state = viewModel.state.value
        assertFalse(state.isPairing)
        assertTrue(state.status is PairingStatus.Initializing)
    }

    // ==================== 证书确认测试 ====================

    @Test
    fun `confirmCertificateTrust should clear certificate event`() = runTest {
        // Given
        val viewModel = PairingViewModel(mockSecurityModule, mockGateway)
        
        // Set certificate event
        val stateFlow = viewModel.state as MutableStateFlow
        stateFlow.value = stateFlow.value.copy(
            certificateEvent = CertificateEvent(
                hostname = "localhost",
                fingerprint = "abc123",
                isMismatch = false
            )
        )
        
        // When
        viewModel.confirmCertificateTrust()
        
        // Then
        val state = viewModel.state.value
        assertEquals(null, state.certificateEvent)
    }

    @Test
    fun `rejectCertificate should clear certificate event and disconnect`() = runTest {
        // Given
        val viewModel = PairingViewModel(mockSecurityModule, mockGateway)
        
        // Set certificate event
        val stateFlow = viewModel.state as MutableStateFlow
        stateFlow.value = stateFlow.value.copy(
            certificateEvent = CertificateEvent(
                hostname = "localhost",
                fingerprint = "abc123",
                isMismatch = true
            )
        )
        
        // When
        viewModel.rejectCertificate()
        
        // Then
        val state = viewModel.state.value
        assertEquals(null, state.certificateEvent)
        assertTrue(state.status is PairingStatus.Error)
        coVerify { mockGateway.disconnect() }
    }

    // ==================== 状态转换测试 ====================

    @Test
    fun `state should transition correctly during pairing flow`() = runTest {
        // Given
        coEvery { mockGateway.connect(any(), any()) } returns Result.success(Unit)
        
        val viewModel = PairingViewModel(mockSecurityModule, mockGateway)
        
        // Initial state
        assertTrue(viewModel.state.value.status is PairingStatus.Initializing)
        
        // Set URL
        viewModel.setGatewayUrl("ws://localhost:18789")
        
        // Start pairing
        viewModel.startPairing()
        
        // After pairing
        assertTrue(viewModel.state.value.status is PairingStatus.WaitingForApproval)
    }
}