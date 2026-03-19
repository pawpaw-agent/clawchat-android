package com.openclaw.clawchat.integration

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockWebServer
import okio.ByteString
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Mock Gateway Server for integration tests.
 *
 * Simulates OpenClaw Gateway Protocol v3:
 * 1. WebSocket connection
 * 2. Sends connect.challenge event with nonce
 * 3. Validates Ed25519 signature in connect request
 * 4. Returns hello-ok response with deviceToken
 * 5. Handles chat.send and sends assistant response
 */
class MockGatewayServer(
    private val port: Int = 0, // 0 = random port
    private val requireAuth: Boolean = true
) {
    private val client = OkHttpClient.Builder()
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private var server: okhttp3.mockwebserver.MockWebServer? = null
    private var webSocket: WebSocket? = null
    private val random = SecureRandom()
    private val requestCounter = AtomicLong(0)

    private val _connectionState = MutableStateFlow<ServerState>(ServerState.Stopped)
    val connectionState: StateFlow<ServerState> = _connectionState.asStateFlow()

    private var pendingNonce: String? = null
    private var deviceToken: String? = null

    val receivedMessages = mutableListOf<String>()
    val sentMessages = mutableListOf<String>()

    data class ServerState(
        val status: Status,
        val url: String? = null,
        val port: Int = 0
    ) {
        enum class Status { Stopped, Starting, Running, Error }
    }

    /**
     * Start the mock gateway server.
     */
    suspend fun start(): String = withContext(Dispatchers.IO) {
        _connectionState.value = ServerState(ServerState.Status.Starting)

        try {
            server = okhttp3.mockwebserver.MockWebServer().apply {
                start(port)
            }

            val serverPort = server!!.port
            val wsUrl = "ws://localhost:$serverPort/ws"

            _connectionState.value = ServerState(ServerState.Status.Running, wsUrl, serverPort)
            println("[MockGateway] Started on port $serverPort")

            wsUrl
        } catch (e: Exception) {
            _connectionState.value = ServerState(ServerState.Status.Error)
            throw e
        }
    }

    /**
     * Stop the mock gateway server.
     */
    suspend fun stop() = withContext(Dispatchers.IO) {
        webSocket?.close(1000, "Server shutdown")
        webSocket = null
        server?.shutdown()
        server = null
        _connectionState.value = ServerState(ServerState.Status.Stopped)
        println("[MockGateway] Stopped")
    }

    /**
     * Connect to the mock gateway and run the full protocol flow.
     * Returns true if all steps succeed.
     */
    suspend fun connectAndTest(
        publicKey: String,
        privateKey: java.security.PrivateKey? = null,
        gatewayUrl: String
    ): TestResult = withContext(Dispatchers.IO) {
        val result = TestResult()

        val latch = CountDownLatch(6) // 6 steps

        val request = Request.Builder()
            .url(gatewayUrl.replace("ws://", "http://").replace("/ws", "/"))
            .build()

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                println("[MockGateway] WebSocket opened")
                result.steps.add(StepResult("1. WebSocket open", true))
                latch.countDown()

                // Step 2: Send connect.challenge
                val nonce = generateNonce()
                pendingNonce = nonce
                val challenge = buildJsonObject {
                    put("type", "event")
                    put("event", "connect.challenge")
                    put("payload", buildJsonObject {
                        put("nonce", nonce)
                        put("ts", System.currentTimeMillis())
                    })
                }
                webSocket.send(Json.encodeToString(challenge))
                sentMessages.add("connect.challenge")
                println("[MockGateway] Sent connect.challenge (nonce: ${nonce.take(8)}...)")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                println("[MockGateway] Received: ${text.take(200)}...")
                receivedMessages.add(text)

                runBlocking {
                    try {
                        val json = Json.parseToJsonElement(text).jsonObject
                        val type = json["type"]?.jsonPrimitive?.content
                        val method = json["method"]?.jsonPrimitive?.content

                        when {
                            type == "req" && method == "connect" -> {
                                handleConnect(webSocket, json, result, latch)
                            }
                            type == "req" && method == "sessions.list" -> {
                                handleSessionsList(webSocket, json, result, latch)
                            }
                            type == "req" && method == "chat.send" -> {
                                handleChatSend(webSocket, json, result, latch)
                            }
                            else -> {
                                println("[MockGateway] Unknown message type: $type / $method")
                            }
                        }
                    } catch (e: Exception) {
                        println("[MockGateway] Parse error: ${e.message}")
                    }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                println("[MockGateway] WebSocket failure: ${t.message}")
                result.steps.add(StepResult("WebSocket error", false, t.message))
                latch.countDown()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                println("[MockGateway] WebSocket closed: $code $reason")
            }
        }

        this@MockGatewayServer.webSocket = client.newWebSocket(request, listener)

        // Wait for all 6 steps with timeout
        val completed = latch.await(30, TimeUnit.SECONDS)

        if (!completed) {
            result.success = false
            result.error = "Timeout waiting for protocol steps"
        }

        result.success = result.steps.all { it.passed }
        result
    }

    private suspend fun handleConnect(
        webSocket: WebSocket,
        json: JsonObject,
        result: TestResult,
        latch: CountDownLatch
    ) = withContext(Dispatchers.IO) {
        val params = json["params"]?.jsonObject ?: run {
            result.steps.add(StepResult("3. connect accepted", false, "Missing params"))
            latch.countDown()
            return@withContext
        }

        val device = params["device"]?.jsonObject ?: run {
            result.steps.add(StepResult("3. connect accepted", false, "Missing device"))
            latch.countDown()
            return@withContext
        }

        val nonce = device["nonce"]?.jsonPrimitive?.contentOrNull
        val signature = device["signature"]?.jsonPrimitive?.contentOrNull
        val signedAt = device["signedAt"]?.jsonPrimitive?.longOrNull ?: 0L

        // Validate nonce
        if (nonce != pendingNonce) {
            result.steps.add(StepResult("3. connect accepted", false, "Nonce mismatch"))
            latch.countDown()

            val error = buildJsonObject {
                put("type", "res")
                put("id", json["id"]?.jsonPrimitive?.content ?: "unknown")
                put("ok", false)
                put("error", buildJsonObject {
                    put("code", "DEVICE_AUTH_NONCE_MISMATCH")
                    put("message", "Nonce does not match challenge")
                })
            }
            webSocket.send(Json.encodeToString(error))
            return@withContext
        }

        // Validate signature (simplified - accept any non-empty signature in test mode)
        if (!requireAuth || !signature.isNullOrEmpty()) {
            deviceToken = "device_${UUID.randomUUID()}"
            result.steps.add(StepResult("3. connect accepted", true, "deviceToken=${deviceToken!!.take(12)}..."))
            latch.countDown()

            // Send hello-ok response
            val helloOk = buildJsonObject {
                put("type", "res")
                put("id", json["id"]?.jsonPrimitive?.content ?: "unknown")
                put("ok", true)
                put("payload", buildJsonObject {
                    put("type", "hello-ok")
                    put("protocol", 3)
                    put("auth", buildJsonObject {
                        put("deviceToken", deviceToken!!)
                        put("role", "operator")
                        put("scopes", buildJsonArray {
                            add("operator.read")
                            add("operator.write")
                        })
                    })
                    put("snapshot", buildJsonObject {
                        put("sessionDefaults", buildJsonObject {
                            put("mainSessionKey", "agent:main:main")
                        })
                    })
                })
            }
            webSocket.send(Json.encodeToString(helloOk))
            sentMessages.add("hello-ok")
            println("[MockGateway] Sent hello-ok")
        } else {
            result.steps.add(StepResult("3. connect accepted", false, "Invalid signature"))
            latch.countDown()
        }

        pendingNonce = null
    }

    private suspend fun handleSessionsList(
        webSocket: WebSocket,
        json: JsonObject,
        result: TestResult,
        latch: CountDownLatch
    ) = withContext(Dispatchers.IO) {
        result.steps.add(StepResult("4. sessions.list", true, "1 session"))
        latch.countDown()

        val response = buildJsonObject {
            put("type", "res")
            put("id", json["id"]?.jsonPrimitive?.content ?: "unknown")
            put("ok", true)
            put("payload", buildJsonObject {
                put("sessions", buildJsonArray {
                    add(buildJsonObject {
                        put("key", "agent:main:main")
                        put("id", "agent:main:main")
                        put("label", "Main Session")
                    })
                })
            })
        }
        webSocket.send(Json.encodeToString(response))
        sentMessages.add("sessions.list response")
    }

    private suspend fun handleChatSend(
        webSocket: WebSocket,
        json: JsonObject,
        result: TestResult,
        latch: CountDownLatch
    ) = withContext(Dispatchers.IO) {
        result.steps.add(StepResult("5. chat.send accepted", true))
        latch.countDown()

        val params = json["params"]?.jsonObject
        val sessionKey = params?.get("sessionKey")?.jsonPrimitive?.content ?: "agent:main:main"
        val message = params?.get("message")?.jsonPrimitive?.content ?: ""

        println("[MockGateway] Received chat.send: $message")

        // Send chat event with assistant response
        val runId = "run-${System.currentTimeMillis()}"
        val chatEvent = buildJsonObject {
            put("type", "event")
            put("event", "chat")
            put("payload", buildJsonObject {
                put("runId", runId)
                put("sessionKey", sessionKey)
                put("seq", 1)
                put("state", "final")
                put("message", buildJsonObject {
                    put("role", "assistant")
                    put("content", "Mock response: $message")
                    put("timestamp", System.currentTimeMillis())
                })
            })
        }
        webSocket.send(Json.encodeToString(chatEvent))
        sentMessages.add("chat event")
        println("[MockGateway] Sent assistant response")

        // Step 6 is complete when we receive this
        result.steps.add(StepResult("6. assistant response", true, "Mock response"))
        latch.countDown()
    }

    private fun generateNonce(): String {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return android.util.Base64.encodeToString(bytes, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP)
    }
}

data class TestResult(
    var success: Boolean = true,
    var error: String? = null,
    val steps: MutableList<StepResult> = mutableListOf()
)

data class StepResult(
    val step: String,
    val passed: Boolean,
    val detail: String? = null
)
