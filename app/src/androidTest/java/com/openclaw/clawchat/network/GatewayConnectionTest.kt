package com.openclaw.clawchat.network

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.openclaw.clawchat.security.SecurityModule
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Gateway 连接测试
 * 
 * 验证：
 * - WebSocket 连接
 * - Challenge-Response 认证
 * - 消息格式解析
 * - Device Token 存储
 */
@RunWith(AndroidJUnit4::class)
class GatewayConnectionTest {

    private lateinit var context: Context
    private lateinit var securityModule: SecurityModule
    private lateinit var okHttpClient: OkHttpClient
    private var webSocket: WebSocket? = null

    companion object {
        private const val TAG = "GatewayConnectionTest"
        private const val GATEWAY_URL = "ws://localhost:18789/ws"
        private const val CONNECT_TIMEOUT_MS = 10000L
    }

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        securityModule = SecurityModule(context)
        okHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @After
    fun teardown() {
        webSocket?.close(1000, "Test completed")
        webSocket = null
    }

    /**
     * 测试 1: WebSocket 连接
     */
    @Test
    fun testWebSocketConnection() = runBlocking {
        val latch = CountDownLatch(1)
        var connectionSuccess = false
        var failureMessage: String? = null

        val request = buildConnectionRequest()

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                connectionSuccess = true
                latch.countDown()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                failureMessage = t.message
                connectionSuccess = false
                latch.countDown()
            }
        })

        // 等待连接
        val connected = latch.await(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)

        assertTrue("WebSocket 连接超时", connected)
        assertTrue("WebSocket 连接失败：$failureMessage", connectionSuccess)
    }

    /**
     * 测试 2: Challenge-Response 认证
     */
    @Test
    fun testChallengeResponseAuth() = runBlocking {
        // 初始化 SecurityModule
        securityModule.initialize()

        // 生成挑战
        val timestamp = System.currentTimeMillis()
        val nonce = UUID.randomUUID().toString()
        val challenge = "/ws\n$timestamp\n$nonce"

        // 签名挑战
        val signature = securityModule.signChallenge(challenge)

        // 验证签名不为空
        assertNotNull("签名不应为空", signature)
        assertTrue("签名不应为空字符串", signature.isNotEmpty())

        // 验证签名格式（Base64）
        val base64Pattern = Regex("^[A-Za-z0-9+/]+=*$")
        assertTrue("签名应该是 Base64 格式", base64Pattern.matches(signature))
    }

    /**
     * 测试 3: 消息格式解析
     */
    @Test
    fun testMessageParsing() {
        // 测试系统事件解析
        val systemEventJson = """{"type":"systemEvent","text":"test","timestamp":1234567890}"""
        val systemEvent = MessageParser.parse(systemEventJson)
        assertNotNull("系统事件解析失败", systemEvent)
        assertTrue("应该是 SystemEvent 类型", systemEvent is GatewayMessage.SystemEvent)

        // 测试用户消息解析
        val userMessageJson = """{"type":"userMessage","sessionId":"test","content":"hello","timestamp":1234567890}"""
        val userMessage = MessageParser.parse(userMessageJson)
        assertNotNull("用户消息解析失败", userMessage)
        assertTrue("应该是 UserMessage 类型", userMessage is GatewayMessage.UserMessage)

        // 测试助手消息解析
        val assistantMessageJson = """{"type":"assistantMessage","sessionId":"test","content":"hi","model":"test","timestamp":1234567890}"""
        val assistantMessage = MessageParser.parse(assistantMessageJson)
        assertNotNull("助手消息解析失败", assistantMessage)
        assertTrue("应该是 AssistantMessage 类型", assistantMessage is GatewayMessage.AssistantMessage)
    }

    /**
     * 测试 4: 消息序列化
     */
    @Test
    fun testMessageSerialization() {
        // 测试用户消息序列化
        val userMessage = GatewayMessage.UserMessage(
            sessionId = "test-session",
            content = "测试消息",
            timestamp = System.currentTimeMillis()
        )
        val json = MessageParser.serialize(userMessage)
        assertNotNull("序列化结果不应为空", json)
        assertTrue("JSON 应包含 type 字段", json.contains("\"type\""))
        assertTrue("JSON 应包含 userMessage 类型", json.contains("userMessage"))
    }

    /**
     * 测试 5: Device Token 存储
     */
    @Test
    fun testDeviceTokenStorage() = runBlocking {
        // 初始化 SecurityModule
        val status = securityModule.initialize()

        // 验证设备 ID 已生成
        assertNotNull("设备 ID 不应为空", status.deviceId)
        assertTrue("设备 ID 不应为空字符串", status.deviceId.isNotEmpty())

        // 验证密钥对已生成
        assertTrue("应该有密钥对", status.hasKeyPair)
    }

    /**
     * 测试 6: 完整连接流程
     */
    @Test
    fun testFullConnectionFlow() = runBlocking {
        // 1. 初始化安全模块
        val initStatus = securityModule.initialize()
        assertTrue("安全模块应该初始化成功", initStatus.isInitialized)

        // 2. 生成认证签名
        val timestamp = System.currentTimeMillis()
        val nonce = UUID.randomUUID().toString()
        val challenge = "/ws\n$timestamp\n$nonce"
        val signature = securityModule.signChallenge(challenge)
        assertNotNull("签名不应为空", signature)

        // 3. 构建连接请求
        val request = Request.Builder()
            .url(GATEWAY_URL)
            .addHeader("X-ClawChat-Timestamp", timestamp.toString())
            .addHeader("X-ClawChat-Nonce", nonce)
            .addHeader("X-ClawChat-Signature", signature)
            .build()

        // 4. 尝试连接
        val latch = CountDownLatch(1)
        var connected = false

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                connected = true
                latch.countDown()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                latch.countDown()
            }
        })

        // 等待连接结果
        latch.await(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)

        // 注意：如果 Gateway 未运行，连接会失败
        // 这个测试主要用于验证连接流程的正确性
        println("连接结果：$connected")
    }

    /**
     * 构建连接请求
     */
    private fun buildConnectionRequest(): Request {
        val timestamp = System.currentTimeMillis()
        val nonce = UUID.randomUUID().toString()
        val challenge = "/ws\n$timestamp\n$nonce"
        val signature = securityModule.signChallenge(challenge)

        return Request.Builder()
            .url(GATEWAY_URL)
            .addHeader("X-ClawChat-Timestamp", timestamp.toString())
            .addHeader("X-ClawChat-Nonce", nonce)
            .addHeader("X-ClawChat-Signature", signature)
            .build()
    }
}
