package com.openclaw.clawchat.network

import android.util.Log
import com.openclaw.clawchat.network.protocol.ChallengeResponseAuth
import com.openclaw.clawchat.network.protocol.ConnectChallengePayload
import com.openclaw.clawchat.network.protocol.ConnectOkPayload
import com.openclaw.clawchat.network.protocol.RequestFrame
import com.openclaw.clawchat.network.protocol.ResponseFrame
import com.openclaw.clawchat.network.protocol.WebSocketProtocol
import com.openclaw.clawchat.security.SecurityModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * OkHttp WebSocket 服务实现
 *
 * 基于 OkHttp 的 WebSocket 实现，支持：
 * - 协议 v3 Challenge-Response 认证
 * - 原始 JSON 帧收发
 * - 自动重连（指数退避）
 * - 延迟监控
 *
 * 连接流程 (协议 v3):
 * 1. 建立 WebSocket 连接（仅带协议版本头）
 * 2. 等待 connect.challenge 事件（包含服务器 nonce）
 * 3. 签名服务器 nonce，发送 connect 请求帧
 * 4. 收到 connect.ok 事件 → 连接就绪
 */
class OkHttpWebSocketService @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val securityModule: SecurityModule,
    private val appScope: CoroutineScope
) : WebSocketService {

    companion object {
        private const val TAG = "WebSocketService"

        // 重连配置
        private const val INITIAL_RECONNECT_DELAY_MS = 1000L
        private const val MAX_RECONNECT_DELAY_MS = 30000L
        private const val RECONNECT_BACKOFF_FACTOR = 2.0

        // 延迟监控配置
        private const val LATENCY_CHECK_INTERVAL_MS = 60000L
        private const val LATENCY_SAMPLES_MAX = 10

        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
    }

    private val _connectionState = MutableStateFlow<WebSocketConnectionState>(WebSocketConnectionState.Disconnected)
    override val connectionState: StateFlow<WebSocketConnectionState> = _connectionState.asStateFlow()

    private val _incomingMessages = MutableSharedFlow<String>(replay = 0)
    override val incomingMessages: SharedFlow<String> = _incomingMessages.asSharedFlow()

    private var webSocket: WebSocket? = null
    private var reconnectJob: Job? = null
    private var currentUrl: String? = null
    private var currentToken: String? = null
    private var reconnectAttempt = 0

    // Challenge-Response 认证器
    private val authHandler = ChallengeResponseAuth(securityModule)

    // 请求-响应追踪：requestId → callback
    private val pendingRequests = mutableMapOf<String, (ResponseFrame) -> Unit>()

    private val latencyMeasurements = mutableListOf<Long>()
    private var latencyMonitorJob: Job? = null

    /**
     * 建立 WebSocket 连接
     *
     * 连接后不会立即变为 Connected，而是等待 connect.challenge → 签名 →
     * 发送 connect 请求 → 收到 connect.ok 后才转为 Connected。
     */
    override suspend fun connect(url: String, token: String?): Result<Unit> {
        if (_connectionState.value is WebSocketConnectionState.Connected) {
            Log.d(TAG, "Already connected, skipping connect")
            return Result.success(Unit)
        }

        if (_connectionState.value is WebSocketConnectionState.Connecting) {
            Log.d(TAG, "Already connecting, skipping")
            return Result.success(Unit)
        }

        _connectionState.value = WebSocketConnectionState.Connecting
        currentUrl = url
        currentToken = token
        reconnectAttempt = 0
        authHandler.reset()

        return try {
            val request = buildWebSocketRequest(url, token)

            webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    Log.i(TAG, "WebSocket opened, waiting for connect.challenge...")
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    Log.d(TAG, "Received message: ${text.take(200)}...")
                    processIncomingMessage(text)
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    Log.e(TAG, "WebSocket failure: ${t.message}", t)
                    _connectionState.value = WebSocketConnectionState.Error(t)

                    val url = currentUrl
                    val token = currentToken
                    if (url != null) {
                        scheduleReconnect(url, token)
                    }
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    Log.i(TAG, "WebSocket closed: $code - $reason")
                    _connectionState.value = WebSocketConnectionState.Disconnecting(reason)
                    this@OkHttpWebSocketService.webSocket = null
                    latencyMonitorJob?.cancel()
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    Log.i(TAG, "WebSocket closing: $code - $reason")
                    webSocket.close(code, reason)
                }
            })

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect: ${e.message}", e)
            _connectionState.value = WebSocketConnectionState.Error(e)
            Result.failure(e)
        }
    }

    /**
     * 构建 WebSocket 请求
     */
    private fun buildWebSocketRequest(url: String, token: String?): Request {
        val builder = Request.Builder()
            .url(url)
            .addHeader("X-ClawChat-Protocol-Version", WebSocketProtocol.PROTOCOL_VERSION.toString())

        val authToken = token ?: securityModule.getAuthToken()
        authToken?.let {
            builder.addHeader("Authorization", "Bearer $it")
        }

        return builder.build()
    }

    /**
     * 处理接收到的消息
     *
     * 根据帧 type 字段分发：
     * - "event"  → 协议事件（connect.* / session.message / system.* / error）
     * - "res"    → 响应帧，匹配 pendingRequests 中的请求 ID
     * - 其他     → 透传到 incomingMessages 流
     *
     * 所有原始 JSON 均透传到 incomingMessages，上层自行解析。
     */
    private fun processIncomingMessage(text: String) {
        try {
            val jsonElement = json.parseToJsonElement(text)
            val type = jsonElement.jsonObject["type"]?.jsonPrimitive?.content

            when (type) {
                "event" -> handleEvent(jsonElement.jsonObject, text)
                "res" -> handleResponse(text)
                else -> {
                    // 透传未知帧
                    appScope.launch { _incomingMessages.emit(text) }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse message: ${text.take(200)}")
            // 仍然透传
            appScope.launch { _incomingMessages.emit(text) }
        }
    }

    /**
     * 处理协议 v3 事件帧
     *
     * 认证相关事件在此处理；其余事件透传到 incomingMessages。
     */
    private fun handleEvent(obj: JsonObject, rawText: String) {
        val event = obj["event"]?.jsonPrimitive?.content ?: return

        when (event) {
            // 认证流程（内部处理，不透传）
            "connect.challenge" -> handleConnectChallenge(obj)
            "connect.ok" -> handleConnectOk(obj)
            "connect.error" -> handleConnectError(obj)

            // 所有其他事件透传给上层
            else -> {
                appScope.launch { _incomingMessages.emit(rawText) }
            }
        }
    }

    /**
     * 处理响应帧 (type=res)：按 id 匹配 pendingRequests 回调，同时透传
     */
    private fun handleResponse(text: String) {
        try {
            val response = json.decodeFromString(ResponseFrame.serializer(), text)
            val callback = synchronized(pendingRequests) { pendingRequests.remove(response.id) }
            if (callback != null) {
                callback(response)
                Log.d(TAG, "Response matched: id=${response.id}, ok=${response.ok}")
            } else {
                Log.w(TAG, "Unmatched response id=${response.id}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse response frame: ${e.message}")
        }
        // 也透传原始 JSON
        appScope.launch { _incomingMessages.emit(text) }
    }

    /**
     * 处理 connect.challenge 事件
     */
    private fun handleConnectChallenge(obj: JsonObject) {
        appScope.launch {
            try {
                val payloadObj = obj["payload"]?.jsonObject
                if (payloadObj == null) {
                    Log.e(TAG, "connect.challenge missing payload")
                    return@launch
                }

                val nonce = payloadObj["nonce"]?.jsonPrimitive?.content
                val ts = payloadObj["ts"]?.jsonPrimitive?.content?.toLongOrNull()
                    ?: System.currentTimeMillis()

                if (nonce.isNullOrBlank()) {
                    Log.e(TAG, "connect.challenge has empty nonce")
                    return@launch
                }

                Log.i(TAG, "Received connect.challenge, nonce=${nonce.take(8)}...")

                val challenge = ConnectChallengePayload(nonce = nonce, timestamp = ts)
                authHandler.handleChallenge(challenge)

                val connectRequest = authHandler.buildConnectRequest()

                val params = mapOf(
                    "device" to JsonObject(mapOf(
                        "id" to JsonPrimitive(connectRequest.device.id),
                        "publicKey" to JsonPrimitive(connectRequest.device.publicKey),
                        "signature" to JsonPrimitive(connectRequest.signature),
                        "signedAt" to JsonPrimitive(System.currentTimeMillis()),
                        "nonce" to JsonPrimitive(connectRequest.nonce)
                    )),
                    "client" to JsonObject(mapOf(
                        "id" to JsonPrimitive(connectRequest.client.clientId),
                        "version" to JsonPrimitive(connectRequest.client.clientVersion),
                        "platform" to JsonPrimitive(connectRequest.client.platform)
                    )),
                    "minProtocol" to JsonPrimitive(3),
                    "maxProtocol" to JsonPrimitive(3)
                )

                val requestFrame = RequestFrame(
                    id = "auth-${System.currentTimeMillis()}",
                    method = "connect",
                    params = params
                )

                val frameJson = json.encodeToString(RequestFrame.serializer(), requestFrame)
                val sent = webSocket?.send(frameJson) ?: false
                if (sent) {
                    Log.i(TAG, "Sent connect request, waiting for connect.ok...")
                } else {
                    Log.e(TAG, "Failed to send connect request (WebSocket closed)")
                    _connectionState.value = WebSocketConnectionState.Error(
                        IllegalStateException("WebSocket closed during auth"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to handle connect.challenge: ${e.message}", e)
                _connectionState.value = WebSocketConnectionState.Error(e)
            }
        }
    }

    /**
     * 处理 connect.ok 事件 → 认证完成
     */
    private fun handleConnectOk(obj: JsonObject) {
        try {
            val payloadObj = obj["payload"]?.jsonObject
            val deviceToken = payloadObj?.get("deviceToken")?.jsonPrimitive?.content

            if (!deviceToken.isNullOrBlank()) {
                val connectOk = ConnectOkPayload(
                    deviceToken = deviceToken,
                    timestamp = payloadObj["ts"]?.jsonPrimitive?.content?.toLongOrNull()
                        ?: System.currentTimeMillis()
                )
                authHandler.handleConnectOk(connectOk)
                Log.i(TAG, "deviceToken stored")
            }

            _connectionState.value = WebSocketConnectionState.Connected
            reconnectAttempt = 0
            startLatencyMonitoring()
            Log.i(TAG, "connect.ok received — connection ready")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to handle connect.ok: ${e.message}", e)
        }
    }

    /**
     * 处理 connect.error 事件
     */
    private fun handleConnectError(obj: JsonObject) {
        val payloadObj = obj["payload"]?.jsonObject
        val code = payloadObj?.get("code")?.jsonPrimitive?.content ?: "UNKNOWN"
        val message = payloadObj?.get("message")?.jsonPrimitive?.content ?: "Auth failed"

        Log.e(TAG, "connect.error: $code - $message")
        _connectionState.value = WebSocketConnectionState.Error(
            IllegalStateException("$code: $message"))
    }

    /**
     * 发送原始 JSON 帧
     */
    override suspend fun sendRaw(json: String): Result<Unit> {
        val ws = webSocket
            ?: return Result.failure(IllegalStateException("WebSocket not connected"))

        return withContext(Dispatchers.IO) {
            try {
                val success = ws.send(json)
                if (success) {
                    Result.success(Unit)
                } else {
                    Result.failure(IllegalStateException("Failed to send frame"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Send failed: ${e.message}", e)
                Result.failure(e)
            }
        }
    }

    /**
     * 断开连接
     */
    override suspend fun disconnect(): Result<Unit> {
        Log.i(TAG, "Disconnecting...")

        reconnectJob?.cancel()
        reconnectJob = null
        latencyMonitorJob?.cancel()
        latencyMonitorJob = null

        authHandler.reset()

        webSocket?.close(1000, "User requested disconnect")
        this.webSocket = null

        _connectionState.value = WebSocketConnectionState.Disconnected
        currentUrl = null
        currentToken = null

        Log.i(TAG, "Disconnected")
        return Result.success(Unit)
    }

    /**
     * 调度重连（指数退避）
     */
    private fun scheduleReconnect(url: String, token: String?) {
        reconnectJob?.cancel()

        val delayMs = calculateBackoffDelay()
        Log.i(TAG, "Scheduling reconnect in ${delayMs}ms (attempt ${reconnectAttempt + 1})")

        reconnectJob = appScope.launch {
            delay(delayMs)
            if (reconnectJob?.isActive == true && _connectionState.value !is WebSocketConnectionState.Connected) {
                Log.i(TAG, "Attempting reconnect...")
                connect(url, token)
            }
        }
    }

    /**
     * 计算指数退避延迟
     */
    private fun calculateBackoffDelay(): Long {
        val delay = (INITIAL_RECONNECT_DELAY_MS * Math.pow(RECONNECT_BACKOFF_FACTOR, reconnectAttempt.toDouble())).toLong()
        reconnectAttempt++
        return delay.coerceAtMost(MAX_RECONNECT_DELAY_MS)
    }

    /**
     * 启动延迟监控
     */
    private fun startLatencyMonitoring() {
        latencyMonitorJob?.cancel()

        latencyMonitorJob = appScope.launch {
            while (_connectionState.value is WebSocketConnectionState.Connected) {
                val latency = measureLatency()
                latency?.let {
                    latencyMeasurements.add(it)
                    if (latencyMeasurements.size > LATENCY_SAMPLES_MAX) {
                        latencyMeasurements.removeAt(0)
                    }
                    Log.d(TAG, "Latency: ${it}ms (avg: ${getAverageLatency()}ms)")
                }
                delay(LATENCY_CHECK_INTERVAL_MS)
            }
        }
    }

    /**
     * 测量连接延迟
     */
    override suspend fun measureLatency(): Long? {
        val ws = webSocket ?: return null
        val start = System.currentTimeMillis()
        return try {
            val pingFrame = """{"type":"req","id":"ping-$start","method":"ping","params":{"timestamp":$start}}"""
            ws.send(pingFrame)
            delay(100)
            System.currentTimeMillis() - start
        } catch (e: Exception) {
            Log.w(TAG, "Latency measurement failed: ${e.message}")
            null
        }
    }

    /**
     * 获取平均延迟
     */
    fun getAverageLatency(): Long {
        return if (latencyMeasurements.isEmpty()) 0
        else latencyMeasurements.average().toLong()
    }
}
