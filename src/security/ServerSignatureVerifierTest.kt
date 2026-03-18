package com.openclaw.clawchat.security

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.security.KeyPairGenerator
import java.security.Signature

/**
 * ServerSignatureVerifier 单元测试
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ServerSignatureVerifierTest {
    
    private lateinit var context: Context
    private lateinit var publicKeyManager: ServerPublicKeyManager
    private lateinit var verifier: ServerSignatureVerifier
    private lateinit var testKeyPair: java.security.KeyPair
    
    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        publicKeyManager = ServerPublicKeyManager(context)
        verifier = ServerSignatureVerifier(publicKeyManager)
        
        // 生成测试密钥对
        val keyGen = KeyPairGenerator.getInstance("EC")
        keyGen.initialize(256)
        testKeyPair = keyGen.generateKeyPair()
        
        // 保存公钥到管理器
        val publicKeyPem = publicKeyToPem(testKeyPair.public)
        publicKeyManager.savePrimaryPublicKey(publicKeyPem)
    }
    
    @Test
    fun `verify should succeed with valid signature`() {
        // Given
        val timestamp = System.currentTimeMillis()
        val nonce = "test-nonce-${System.nanoTime()}"
        val body = "test message body"
        val dataToSign = "POST\n/api/test\n$timestamp\n$nonce\n${body.sha256()}"
        val signature = signData(dataToSign)
        
        // When
        val result = verifier.verify(
            method = "POST",
            path = "/api/test",
            body = body,
            timestamp = timestamp,
            nonce = nonce,
            signature = signature
        )
        
        // Then
        assertTrue("Verification should succeed", result.isSuccess())
    }
    
    @Test
    fun `verify should fail with expired timestamp`() {
        // Given
        val timestamp = System.currentTimeMillis() - (10 * 60 * 1000L) // 10 分钟前
        val nonce = "test-nonce-${System.nanoTime()}"
        val body = "test message body"
        val dataToSign = "POST\n/api/test\n$timestamp\n$nonce\n${body.sha256()}"
        val signature = signData(dataToSign)
        
        // When
        val result = verifier.verify(
            method = "POST",
            path = "/api/test",
            body = body,
            timestamp = timestamp,
            nonce = nonce,
            signature = signature
        )
        
        // Then
        assertFalse("Verification should fail", result.isSuccess())
        assertTrue(result is SignatureVerificationResult.TimestampExpired)
    }
    
    @Test
    fun `verify should fail with duplicate nonce`() {
        // Given
        val timestamp = System.currentTimeMillis()
        val nonce = "duplicate-nonce-${System.nanoTime()}"
        val body1 = "first message"
        val body2 = "second message"
        
        val dataToSign1 = "POST\n/api/test\n$timestamp\n$nonce\n${body1.sha256()}"
        val signature1 = signData(dataToSign1)
        
        val dataToSign2 = "POST\n/api/test\n$timestamp\n$nonce\n${body2.sha256()}"
        val signature2 = signData(dataToSign2)
        
        // When - First verification should succeed
        val result1 = verifier.verify(
            method = "POST",
            path = "/api/test",
            body = body1,
            timestamp = timestamp,
            nonce = nonce,
            signature = signature1
        )
        
        // Second verification should fail (duplicate nonce)
        val result2 = verifier.verify(
            method = "POST",
            path = "/api/test",
            body = body2,
            timestamp = timestamp,
            nonce = nonce,
            signature = signature2
        )
        
        // Then
        assertTrue("First verification should succeed", result1.isSuccess())
        assertFalse("Second verification should fail", result2.isSuccess())
        assertTrue(result2 is SignatureVerificationResult.NonceInvalid)
    }
    
    @Test
    fun `verify should fail with invalid signature`() {
        // Given
        val timestamp = System.currentTimeMillis()
        val nonce = "test-nonce-${System.nanoTime()}"
        val body = "test message body"
        val invalidSignature = "invalid_signature_base64"
        
        // When
        val result = verifier.verify(
            method = "POST",
            path = "/api/test",
            body = body,
            timestamp = timestamp,
            nonce = nonce,
            signature = invalidSignature
        )
        
        // Then
        assertFalse("Verification should fail", result.isSuccess())
        assertTrue(result is SignatureVerificationResult.SignatureMismatch || 
                   result is SignatureVerificationResult.SignatureDecodeError)
    }
    
    @Test
    fun `verifyWebSocketMessage should succeed with valid signature`() {
        // Given
        val timestamp = System.currentTimeMillis()
        val nonce = "ws-nonce-${System.nanoTime()}"
        val messageType = "assistantMessage"
        val sessionId = "test-session-123"
        val content = "Hello from assistant"
        
        val dataToSign = "$messageType\n$sessionId\n$timestamp\n$nonce\n${content.sha256()}"
        val signature = signData(dataToSign)
        
        // When
        val result = verifier.verifyWebSocketMessage(
            messageType = messageType,
            sessionId = sessionId,
            content = content,
            timestamp = timestamp,
            nonce = nonce,
            signature = signature
        )
        
        // Then
        assertTrue("WebSocket verification should succeed", result.isSuccess())
    }
    
    @Test
    fun `verify should succeed with backup public key`() {
        // Given
        // Generate a new key pair for backup
        val keyGen = KeyPairGenerator.getInstance("EC")
        keyGen.initialize(256)
        val backupKeyPair = keyGen.generateKeyPair()
        
        val backupPublicKeyPem = publicKeyToPem(backupKeyPair.public)
        publicKeyManager.saveBackupPublicKey(backupPublicKeyPem)
        
        val timestamp = System.currentTimeMillis()
        val nonce = "backup-nonce-${System.nanoTime()}"
        val body = "test with backup key"
        val dataToSign = "POST\n/api/test\n$timestamp\n$nonce\n${body.sha256()}"
        
        // Sign with backup key
        val sig = Signature.getInstance("SHA256withECDSA")
        sig.initSign(backupKeyPair.private)
        sig.update(dataToSign.toByteArray(Charsets.UTF_8))
        val signature = android.util.Base64.encodeToString(sig.sign(), android.util.Base64.NO_WRAP)
        
        // When
        val result = verifier.verify(
            method = "POST",
            path = "/api/test",
            body = body,
            timestamp = timestamp,
            nonce = nonce,
            signature = signature
        )
        
        // Then
        assertTrue("Verification with backup key should succeed", result.isSuccess())
    }
    
    // ==================== 辅助方法 ====================
    
    private fun signData(data: String): String {
        val sig = Signature.getInstance("SHA256withECDSA")
        sig.initSign(testKeyPair.private)
        sig.update(data.toByteArray(Charsets.UTF_8))
        return android.util.Base64.encodeToString(sig.sign(), android.util.Base64.NO_WRAP)
    }
    
    private fun String.sha256(): String {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        val digest = md.digest(this.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
    
    private fun publicKeyToPem(publicKey: java.security.PublicKey): String {
        val encoded = android.util.Base64.encodeToString(publicKey.encoded, android.util.Base64.NO_WRAP)
        return buildString {
            appendLine("-----BEGIN PUBLIC KEY-----")
            appendLine(encoded)
            append("-----END PUBLIC KEY-----")
        }
    }
}

/**
 * ServerPublicKeyManager 单元测试
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ServerPublicKeyManagerTest {
    
    private lateinit var context: Context
    private lateinit var publicKeyManager: ServerPublicKeyManager
    
    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        publicKeyManager = ServerPublicKeyManager(context)
        
        // 清理之前的测试数据
        publicKeyManager.clearAllKeys()
    }
    
    @Test
    fun `save and retrieve primary public key`() {
        // Given
        val keyGen = KeyPairGenerator.getInstance("EC")
        keyGen.initialize(256)
        val keyPair = keyGen.generateKeyPair()
        val publicKeyPem = publicKeyToPem(keyPair.public)
        
        // When
        val fingerprint = publicKeyManager.savePrimaryPublicKey(publicKeyPem)
        val retrieved = publicKeyManager.getPrimaryPublicKey()
        
        // Then
        assertNotNull("Retrieved key should not be null", retrieved)
        assertEquals("Retrieved key should match", publicKeyPem, retrieved)
        assertNotNull("Fingerprint should not be null", fingerprint)
    }
    
    @Test
    fun `verify fingerprint should match`() {
        // Given
        val keyGen = KeyPairGenerator.getInstance("EC")
        keyGen.initialize(256)
        val keyPair = keyPair.generateKeyPair()
        val publicKeyPem = publicKeyToPem(keyPair.public)
        val expectedFingerprint = publicKeyManager.savePrimaryPublicKey(publicKeyPem)
        
        // When
        val isValid = publicKeyManager.verifyFingerprint(expectedFingerprint)
        
        // Then
        assertTrue("Fingerprint should match", isValid)
    }
    
    @Test
    fun `verify fingerprint should not match`() {
        // Given
        val keyGen = KeyPairGenerator.getInstance("EC")
        keyGen.initialize(256)
        val keyPair = keyPair.generateKeyPair()
        val publicKeyPem = publicKeyToPem(keyPair.public)
        publicKeyManager.savePrimaryPublicKey(publicKeyPem)
        
        // When
        val isValid = publicKeyManager.verifyFingerprint("wrong:fingerprint")
        
        // Then
        assertFalse("Fingerprint should not match", isValid)
    }
    
    @Test
    fun `hasValidPublicKey should return true after save`() {
        // Given
        val keyGen = KeyPairGenerator.getInstance("EC")
        keyGen.initialize(256)
        val keyPair = keyPair.generateKeyPair()
        val publicKeyPem = publicKeyToPem(keyPair.public)
        
        // When
        publicKeyManager.savePrimaryPublicKey(publicKeyPem)
        val hasValid = publicKeyManager.hasValidPublicKey()
        
        // Then
        assertTrue("Should have valid public key", hasValid)
    }
    
    @Test
    fun `clearAllKeys should remove all keys`() {
        // Given
        val keyGen = KeyPairGenerator.getInstance("EC")
        keyGen.initialize(256)
        val keyPair = keyPair.generateKeyPair()
        val publicKeyPem = publicKeyToPem(keyPair.public)
        publicKeyManager.savePrimaryPublicKey(publicKeyPem)
        
        // When
        publicKeyManager.clearAllKeys()
        val retrieved = publicKeyManager.getPrimaryPublicKey()
        
        // Then
        assertNull("Key should be cleared", retrieved)
    }
    
    private fun publicKeyToPem(publicKey: java.security.PublicKey): String {
        val encoded = android.util.Base64.encodeToString(publicKey.encoded, android.util.Base64.NO_WRAP)
        return buildString {
            appendLine("-----BEGIN PUBLIC KEY-----")
            appendLine(encoded)
            append("-----END PUBLIC KEY-----")
        }
    }
}

/**
 * NonceCache 单元测试
 */
class NonceCacheTest {
    
    private lateinit var nonceCache: NonceCache
    
    @Before
    fun setup() {
        nonceCache = NonceCache()
    }
    
    @Test
    fun `checkAndAdd should succeed for unique nonce`() {
        // Given
        val nonce = "unique-nonce-${System.nanoTime()}"
        val timestamp = System.currentTimeMillis()
        
        // When
        val result = nonceCache.checkAndAdd(nonce, timestamp)
        
        // Then
        assertTrue("Unique nonce should be accepted", result)
    }
    
    @Test
    fun `checkAndAdd should fail for duplicate nonce`() {
        // Given
        val nonce = "duplicate-nonce-${System.nanoTime()}"
        val timestamp = System.currentTimeMillis()
        
        // When
        val result1 = nonceCache.checkAndAdd(nonce, timestamp)
        val result2 = nonceCache.checkAndAdd(nonce, timestamp)
        
        // Then
        assertTrue("First check should succeed", result1)
        assertFalse("Second check should fail", result2)
    }
    
    @Test
    fun `checkAndAdd should fail for old timestamp`() {
        // Given
        val nonce = "old-nonce-${System.nanoTime()}"
        val oldTimestamp = System.currentTimeMillis() - (10 * 60 * 1000L) // 10 分钟前
        
        // When
        val result = nonceCache.checkAndAdd(nonce, oldTimestamp)
        
        // Then
        assertFalse("Old timestamp should be rejected", result)
    }
    
    @Test
    fun `isValid should return false for existing nonce`() {
        // Given
        val nonce = "existing-nonce-${System.nanoTime()}"
        val timestamp = System.currentTimeMillis()
        nonceCache.checkAndAdd(nonce, timestamp)
        
        // When
        val isValid = nonceCache.isValid(nonce, timestamp)
        
        // Then
        assertFalse("Existing nonce should be invalid", isValid)
    }
    
    @Test
    fun `clear should remove all nonces`() {
        // Given
        val nonce1 = "nonce1-${System.nanoTime()}"
        val nonce2 = "nonce2-${System.nanoTime()}"
        val timestamp = System.currentTimeMillis()
        
        nonceCache.checkAndAdd(nonce1, timestamp)
        nonceCache.checkAndAdd(nonce2, timestamp)
        
        // When
        nonceCache.clear()
        val result = nonceCache.checkAndAdd(nonce1, timestamp)
        
        // Then
        assertTrue("Nonce should be accepted after clear", result)
    }
}
