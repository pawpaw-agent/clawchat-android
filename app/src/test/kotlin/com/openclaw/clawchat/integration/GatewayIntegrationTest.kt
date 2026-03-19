package com.openclaw.clawchat.integration

import com.openclaw.clawchat.network.protocol.ChallengeResponseAuth
import com.openclaw.clawchat.network.protocol.ConnectChallengePayload
import com.openclaw.clawchat.security.SecurityModule
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Integration tests for ClawChat Gateway Protocol v3.
 *
 * Tests the complete flow:
 * 1. WebSocket connection
 * 2. connect.challenge → Ed25519 signature → connect request
 * 3. hello-ok response with deviceToken
 * 4. sessions.list
 * 5. chat.send
 * 6. assistant response
 */
@RunWith(RobolectricTestRunner::class)
class GatewayIntegrationTest {

    private lateinit var mockGateway: MockGatewayServer
    private lateinit var securityModule: SecurityModule

    @Before
    fun setup() {
        mockGateway = MockGatewayServer(port = 0, requireAuth = false)
        securityModule = SecurityModule(RuntimeEnvironment.getApplication())
    }

    @After
    fun teardown() = runTest {
        mockGateway.stop()
    }

    @Test
    fun testFullProtocolFlow() = runTest {
        // Initialize security module (generates Ed25519 key pair)
        securityModule.initialize()

        // Start mock gateway
        val gatewayUrl = mockGateway.start()
        println("Mock Gateway URL: $gatewayUrl")

        // Run the full protocol flow (requireAuth=false so any signature is accepted)
        val result = mockGateway.connectAndTest(
            publicKey = securityModule.getPublicKeyBase64Url(),
            privateKey = null, // Not used when requireAuth=false
            gatewayUrl = gatewayUrl
        )

        // Assert all steps passed
        assertTrue("Test failed: ${result.error}", result.success)
        assertEquals("Expected 6 steps", 6, result.steps.size)

        // Verify each step
        assertTrue(result.steps[0].passed, "Step 1: WebSocket open")
        assertTrue(result.steps[1].passed, "Step 2: connect.challenge")
        assertTrue(result.steps[2].passed, "Step 3: connect accepted")
        assertTrue(result.steps[3].passed, "Step 4: sessions.list")
        assertTrue(result.steps[4].passed, "Step 5: chat.send")
        assertTrue(result.steps[5].passed, "Step 6: assistant response")

        // Verify messages were exchanged
        assertTrue("Should have received messages", mockGateway.receivedMessages.isNotEmpty())
        assertTrue("Should have sent messages", mockGateway.sentMessages.isNotEmpty())

        println("Test passed! Steps:")
        result.steps.forEach { step ->
            println("  ${if (step.passed) "✅" else "❌"} ${step.step}${step.detail?.let { " - $it" } ?: ""}")
        }
    }

    @Test
    fun testChallengeResponseAuth() = runTest {
        val gatewayUrl = mockGateway.start()

        // Initialize and get key info
        val status = securityModule.initialize()
        assertNotNull("Device ID should be generated", status.deviceId)

        val publicKey = securityModule.getPublicKeyBase64Url()
        assertNotNull("Public key should be generated", publicKey)
        assertTrue("Public key should be base64url", publicKey.matches(Regex("[A-Za-z0-9_-]+")))

        // Test v3 payload signing
        val testNonce = "test-nonce-${System.currentTimeMillis()}"
        val signedPayload = securityModule.signV3Payload(
            nonce = testNonce,
            clientId = "test-client",
            clientMode = "test",
            role = "operator",
            scopes = listOf("operator.read"),
            token = "test-token",
            platform = "test",
            deviceFamily = "test"
        )

        assertNotNull("Signed payload should not be null", signedPayload)
        assertEquals("Device ID should match", status.deviceId, signedPayload.deviceId)
        assertTrue("Signature should be base64url", signedPayload.signature.matches(Regex("[A-Za-z0-9_-]+")))
        assertTrue("Payload should start with v3", signedPayload.payload.startsWith("v3|"))

        println("Challenge-response auth test passed!")
        println("  Device ID: ${signedPayload.deviceId.take(16)}...")
        println("  Payload: ${signedPayload.payload.take(80)}...")
        println("  Signature: ${signedPayload.signature.take(30)}...")
    }

    @Test
    fun testGatewayConnectionState() = runTest {
        val gatewayUrl = mockGateway.start()

        // Verify initial state
        assertEquals(
            MockGatewayServer.ServerState.Status.Running,
            mockGateway.connectionState.value.status
        )
        assertEquals(gatewayUrl, mockGateway.connectionState.value.url)

        // Stop and verify state change
        mockGateway.stop()

        assertEquals(
            MockGatewayServer.ServerState.Status.Stopped,
            mockGateway.connectionState.value.status
        )
    }
}
