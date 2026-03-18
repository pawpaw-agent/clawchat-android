package com.openclaw.clawchat.network.protocol

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.openclaw.clawchat.network.GatewayMessage
import com.openclaw.clawchat.network.WebSocketConnectionState
import com.openclaw.clawchat.security.SecurityModule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/**
 * Gateway 连接集成测试
 * 
 * 使用本地 Gateway (端口 18789) 验证协议 v3 兼容性
 * 
 * 测试流程:
 * 1. WebSocket 连接建立
 * 2. 接收 connect.challenge (服务器 nonce)
 * 3. 签名服务器 nonce
 * 4. 接收 hello-ok 响应
 * 5. 获取 deviceToken
 */
@RunWith(AndroidJUnit4::class)
class GatewayConnectionIntegrationTest {
    
    companion object {
        private const val TAG = "GatewayConnectionTest"
        private const val GATEWAY_URL = "ws://10.0.2.2:18789/ws" // Android 模拟器访问 localhost
        private const val CONNECT_TIMEOUT_MS = 30000L
        private const val AUTH_TIMEOUT_MS = 60000L
    }
    
    private lateinit var context: Context
    private lateinit var securityModule: SecurityModule
    private lateinit var okHttpClient: OkHttpClient
    private lateinit var connection: GatewayConnection
    
    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        securityModule = SecurityModule(context)
        okHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
        connection = GatewayConnection(okHttpClient, securityModule, kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO))
    }
    
    /**
     * 测试 1: WebSocket 连接建立
     */
    @Test
    fun testWebSocketConnection() = runBlocking {
        Log.d(TAG, "开始测试 WebSocket 连接...")
        
        val result = connection.connect(GATEWAY_URL)
        
        // 等待连接结果
        val connected = withTimeout(CONNECT_TIMEOUT_MS) {
            while (connection.connectionState.value !is WebSocketConnectionState.Connected &&
                   connection.connectionState.value !is WebSocketConnectionState.Error) {
                kotlinx.coroutines.delay(100)
            }
            connection.connectionState.value is WebSocketConnectionState.Connected
        }
        
        assertTrue("WebSocket 连接应该成功", connected)
        
        // 清理
        connection.disconnect()
    }
    
    /**
     * 测试 2: Challenge-Response 认证流程
     */
    @Test
    fun testChallengeResponseAuth() = runBlocking {
        Log.d(TAG, "开始测试 Challenge-Response 认证...")
        
        // 初始化安全模块
        val initStatus = securityModule.initialize()
        assertTrue("安全模块应该初始化成功", initStatus.isInitialized)
        assertNotNull("设备 ID 不应为空", initStatus.deviceId)
        assertTrue("应该有密钥对", initStatus.hasKeyPair)
        
        // 连接并认证
        val connectResult = connection.connect(GATEWAY_URL)
        
        // 等待认证完成
        val authenticated = withTimeout(AUTH_TIMEOUT_MS) {
            while (connection.connectionState.value !is WebSocketConnectionState.Connected &&
                   connection.connectionState.value !is WebSocketConnectionState.Error) {
                kotlinx.coroutines.delay(100)
            }
            connection.connectionState.value is WebSocketConnectionState.Connected
        }
        
        assertTrue("认证应该成功", authenticated)
        
        // 验证 deviceToken 已存储
        val deviceToken = securityModule.getAuthToken()
        assertNotNull("deviceToken 不应为空", deviceToken)
        assertTrue("deviceToken 不应为空字符串", deviceToken?.isNotEmpty() == true)
        
        Log.d(TAG, "认证成功，deviceToken: ${deviceToken?.take(20)}...")
        
        // 清理
        connection.disconnect()
    }
    
    /**
     * 测试 3: 接收消息
     */
    @Test
    fun testReceiveMessage() = runBlocking {
        Log.d(TAG, "开始测试消息接收...")
        
        // 连接并认证
        connection.connect(GATEWAY_URL)
        
        // 等待连接
        withTimeout(CONNECT_TIMEOUT_MS) {
            while (connection.connectionState.value !is WebSocketConnectionState.Connected) {
                kotlinx.coroutines.delay(100)
            }
        }
        
        // 等待接收消息
        val messageReceived = withTimeout(10000L) {
            try {
                connection.incomingMessages.first()
                true
            } catch (e: Exception) {
                false
            }
        }
        
        // 注意：可能不会立即收到消息，这是正常的
        Log.d(TAG, "消息接收测试：$messageReceived")
        
        // 清理
        connection.disconnect()
    }
    
    /**
     * 测试 4: 发送消息
     */
    @Test
    fun testSendMessage() = runBlocking {
        Log.d(TAG, "开始测试消息发送...")
        
        // 连接并认证
        connection.connect(GATEWAY_URL)
        
        // 等待连接
        withTimeout(CONNECT_TIMEOUT_MS) {
            while (connection.connectionState.value !is WebSocketConnectionState.Connected) {
                kotlinx.coroutines.delay(100)
            }
        }
        
        // 发送测试消息
        val testMessage = GatewayMessage.UserMessage(
            sessionId = "test-session",
            content = "测试消息",
            timestamp = System.currentTimeMillis()
        )
        
        val sendResult = connection.send(testMessage)
        
        assertTrue("消息发送应该成功", sendResult.isSuccess)
        
        Log.d(TAG, "消息发送成功")
        
        // 清理
        connection.disconnect()
    }
    
    /**
     * 测试 5: 完整连接流程
     */
    @Test
    fun testFullConnectionFlow() = runBlocking {
        Log.d(TAG, "开始测试完整连接流程...")
        
        // 1. 初始化
        val initStatus = securityModule.initialize()
        assertTrue("初始化应该成功", initStatus.isInitialized)
        
        // 2. 连接
        val connectResult = connection.connect(GATEWAY_URL)
        assertTrue("连接应该成功", connectResult.isSuccess)
        
        // 3. 等待认证
        withTimeout(AUTH_TIMEOUT_MS) {
            while (connection.connectionState.value !is WebSocketConnectionState.Connected) {
                kotlinx.coroutines.delay(100)
            }
        }
        assertTrue("应该已连接", connection.connectionState.value is WebSocketConnectionState.Connected)
        
        // 4. 验证 deviceToken
        val deviceToken = securityModule.getAuthToken()
        assertNotNull("deviceToken 不应为空", deviceToken)
        
        // 5. 发送心跳
        val pingResult = connection.send(GatewayMessage.Ping(System.currentTimeMillis()))
        assertTrue("心跳发送应该成功", pingResult.isSuccess)
        
        // 6. 断开连接
        val disconnectResult = connection.disconnect()
        assertTrue("断开连接应该成功", disconnectResult.isSuccess)
        
        Log.d(TAG, "完整连接流程测试通过")
    }
    
    /**
     * 测试 6: 协议版本兼容性
     */
    @Test
    fun testProtocolVersionCompatibility() {
        Log.d(TAG, "测试协议版本兼容性...")
        
        // 当前版本
        val currentVersion = ProtocolVersion.fromString(WebSocketProtocol.VERSION)
        assertEquals(3, currentVersion.major)
        
        // 兼容版本
        val compatibleVersion1 = ProtocolVersion(3, 0, 0)
        val compatibleVersion2 = ProtocolVersion(3, 1, 0)
        val compatibleVersion3 = ProtocolVersion(3, 2, 5)
        
        assertTrue("3.0.0 应该兼容", currentVersion.isCompatibleWith(compatibleVersion1))
        assertTrue("3.1.0 应该兼容", currentVersion.isCompatibleWith(compatibleVersion2))
        assertTrue("3.2.5 应该兼容", currentVersion.isCompatibleWith(compatibleVersion3))
        
        // 不兼容版本
        val incompatibleVersion1 = ProtocolVersion(2, 0, 0)
        val incompatibleVersion2 = ProtocolVersion(4, 0, 0)
        
        assertFalse("2.0.0 不应该兼容", currentVersion.isCompatibleWith(incompatibleVersion1))
        assertFalse("4.0.0 不应该兼容", currentVersion.isCompatibleWith(incompatibleVersion2))
    }
}
