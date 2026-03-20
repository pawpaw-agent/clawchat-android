package com.openclaw.clawchat.security

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * EncryptedStorage 单元测试
 * 
 * 测试覆盖：
 * - 设备令牌存储
 * - 设备 ID 管理
 * - Gateway 配置存储
 * - 配对状态管理
 * - Ed25519 软件密钥存储（API < 33）
 */
@RunWith(RobolectricTestRunner::class)
class EncryptedStorageTest {

    private lateinit var context: Context
    private lateinit var encryptedStorage: EncryptedStorage

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        encryptedStorage = EncryptedStorage(context)
    }

    // ==================== 设备令牌测试 ====================

    @Test
    fun `saveDeviceToken should store token`() = runTest {
        // Given
        val token = "test-device-token-123"

        // When
        encryptedStorage.saveDeviceToken(token)

        // Then
        val result = encryptedStorage.getDeviceToken()
        assertEquals(token, result)
    }

    @Test
    fun `getDeviceToken should return null when not set`() = runTest {
        // When
        val result = encryptedStorage.getDeviceToken()

        // Then
        assertNull(result)
    }

    @Test
    fun `hasDeviceToken should return true when token exists`() = runTest {
        // Given
        encryptedStorage.saveDeviceToken("test-token")

        // When
        val result = encryptedStorage.hasDeviceToken()

        // Then
        assertTrue(result)
    }

    @Test
    fun `hasDeviceToken should return false when token not set`() = runTest {
        // When
        val result = encryptedStorage.hasDeviceToken()

        // Then
        assertFalse(result)
    }

    @Test
    fun `clearDeviceToken should remove token`() = runTest {
        // Given
        encryptedStorage.saveDeviceToken("test-token")

        // When
        encryptedStorage.clearDeviceToken()

        // Then
        assertFalse(encryptedStorage.hasDeviceToken())
    }

    // ==================== 设备 ID 测试 ====================

    @Test
    fun `saveDeviceId should store device ID`() = runTest {
        // Given
        val deviceId = "abc123def456"

        // When
        encryptedStorage.saveDeviceId(deviceId)

        // Then
        val result = encryptedStorage.getDeviceId()
        assertEquals(deviceId, result)
    }

    @Test
    fun `getDeviceId should return null when not set`() = runTest {
        // When
        val result = encryptedStorage.getDeviceId()

        // Then
        assertNull(result)
    }

    // ==================== Gateway 配置测试 ====================

    @Test
    fun `saveGatewayUrl should store URL`() = runTest {
        // Given
        val url = "ws://localhost:18789"

        // When
        encryptedStorage.saveGatewayUrl(url)

        // Then
        val result = encryptedStorage.getGatewayUrl()
        assertEquals(url, result)
    }

    @Test
    fun `saveTlsFingerprint should store fingerprint`() = runTest {
        // Given
        val fingerprint = "SHA256:ABC123DEF456"

        // When
        encryptedStorage.saveTlsFingerprint(fingerprint)

        // Then
        val result = encryptedStorage.getTlsFingerprint()
        assertEquals(fingerprint, result)
    }

    @Test
    fun `getGatewayUrl should return null when not set`() = runTest {
        // When
        val result = encryptedStorage.getGatewayUrl()

        // Then
        assertNull(result)
    }

    // ==================== 配对状态测试 ====================

    @Test
    fun `savePairingStatus should store status`() = runTest {
        // Given
        val status = EncryptedStorage.PAIRING_STATUS_APPROVED

        // When
        encryptedStorage.savePairingStatus(status)

        // Then
        val result = encryptedStorage.getPairingStatus()
        assertEquals(status, result)
    }

    @Test
    fun `getPairingStatus should return NONE when not set`() = runTest {
        // When
        val result = encryptedStorage.getPairingStatus()

        // Then
        assertEquals(EncryptedStorage.PAIRING_STATUS_NONE, result)
    }

    @Test
    fun `isPaired should return true when approved and has token`() = runTest {
        // Given
        encryptedStorage.savePairingStatus(EncryptedStorage.PAIRING_STATUS_APPROVED)
        encryptedStorage.saveDeviceToken("test-token")

        // When
        val result = encryptedStorage.isPaired()

        // Then
        assertTrue(result)
    }

    @Test
    fun `isPaired should return false when not approved`() = runTest {
        // Given
        encryptedStorage.savePairingStatus(EncryptedStorage.PAIRING_STATUS_PENDING)

        // When
        val result = encryptedStorage.isPaired()

        // Then
        assertFalse(result)
    }

    @Test
    fun `isPaired should return false when no token`() = runTest {
        // Given
        encryptedStorage.savePairingStatus(EncryptedStorage.PAIRING_STATUS_APPROVED)
        // No token set

        // When
        val result = encryptedStorage.isPaired()

        // Then
        assertFalse(result)
    }

    // ==================== 连接时间戳测试 ====================

    @Test
    fun `saveLastConnectedTimestamp should store timestamp`() = runTest {
        // Given
        val timestamp = System.currentTimeMillis()

        // When
        encryptedStorage.saveLastConnectedTimestamp(timestamp)

        // Then
        val result = encryptedStorage.getLastConnectedTimestamp()
        assertEquals(timestamp, result)
    }

    @Test
    fun `getLastConnectedTimestamp should return 0 when not set`() = runTest {
        // When
        val result = encryptedStorage.getLastConnectedTimestamp()

        // Then
        assertEquals(0L, result)
    }

    // ==================== 清除操作测试 ====================

    @Test
    fun `clearAll should remove all data`() = runTest {
        // Given
        encryptedStorage.saveDeviceToken("token")
        encryptedStorage.saveDeviceId("device-id")
        encryptedStorage.saveGatewayUrl("url")
        encryptedStorage.savePairingStatus(EncryptedStorage.PAIRING_STATUS_APPROVED)

        // When
        encryptedStorage.clearAll()

        // Then
        assertNull(encryptedStorage.getDeviceToken())
        assertNull(encryptedStorage.getDeviceId())
        assertNull(encryptedStorage.getGatewayUrl())
        assertEquals(EncryptedStorage.PAIRING_STATUS_NONE, encryptedStorage.getPairingStatus())
    }

    @Test
    fun `clearPairingData should remove only pairing data`() = runTest {
        // Given
        encryptedStorage.saveDeviceToken("token")
        encryptedStorage.saveDeviceId("device-id")
        encryptedStorage.saveGatewayUrl("url")
        encryptedStorage.savePairingStatus(EncryptedStorage.PAIRING_STATUS_APPROVED)

        // When
        encryptedStorage.clearPairingData()

        // Then
        assertNull(encryptedStorage.getDeviceToken())
        assertNull(encryptedStorage.getDeviceId())
        assertEquals(EncryptedStorage.PAIRING_STATUS_NONE, encryptedStorage.getPairingStatus())
        // Gateway URL should remain
        assertEquals("url", encryptedStorage.getGatewayUrl())
    }

    @Test
    fun `clearGatewayConfig should remove only gateway config`() = runTest {
        // Given
        encryptedStorage.saveDeviceToken("token")
        encryptedStorage.saveGatewayUrl("url")
        encryptedStorage.saveTlsFingerprint("fingerprint")

        // When
        encryptedStorage.clearGatewayConfig()

        // Then
        assertNull(encryptedStorage.getGatewayUrl())
        assertNull(encryptedStorage.getTlsFingerprint())
        // Device token should remain
        assertEquals("token", encryptedStorage.getDeviceToken())
    }

    // ==================== 软件密钥存储测试 (API < 33) ====================

    @Test
    @Config(sdk = [29])
    fun `hasKeyPair should return false when not set`() = runTest {
        // When
        val result = encryptedStorage.hasKeyPair("test-key")

        // Then
        assertFalse(result)
    }

    @Test
    @Config(sdk = [29])
    fun `saveKeyPair should store key pair`() = runTest {
        // Given
        val alias = "test-key"
        val publicKey = ByteArray(32) { i -> i.toByte() }
        val privateKey = ByteArray(32) { i -> (i + 32).toByte() }

        // When
        encryptedStorage.saveKeyPair(alias, publicKey, privateKey)

        // Then
        assertTrue(encryptedStorage.hasKeyPair(alias))
    }

    @Test
    @Config(sdk = [29])
    fun `getPublicKeyEncoded should return stored key`() = runTest {
        // Given
        val alias = "test-key"
        val publicKey = ByteArray(32) { i -> i.toByte() }
        val privateKey = ByteArray(32) { i -> (i + 32).toByte() }
        encryptedStorage.saveKeyPair(alias, publicKey, privateKey)

        // When
        val result = encryptedStorage.getPublicKeyEncoded(alias)

        // Then
        assertNotNull(result)
        assertTrue(result.contentEquals(publicKey))
    }

    @Test
    @Config(sdk = [29])
    fun `getPrivateKeyEncoded should return stored key`() = runTest {
        // Given
        val alias = "test-key"
        val publicKey = ByteArray(32) { i -> i.toByte() }
        val privateKey = ByteArray(32) { i -> (i + 32).toByte() }
        encryptedStorage.saveKeyPair(alias, publicKey, privateKey)

        // When
        val result = encryptedStorage.getPrivateKeyEncoded(alias)

        // Then
        assertNotNull(result)
        assertTrue(result.contentEquals(privateKey))
    }

    @Test
    @Config(sdk = [29])
    fun `deleteKeyPair should remove key pair`() = runTest {
        // Given
        val alias = "test-key"
        val publicKey = ByteArray(32) { i -> i.toByte() }
        val privateKey = ByteArray(32) { i -> (i + 32).toByte() }
        encryptedStorage.saveKeyPair(alias, publicKey, privateKey)

        // When
        encryptedStorage.deleteKeyPair(alias)

        // Then
        assertFalse(encryptedStorage.hasKeyPair(alias))
        assertNull(encryptedStorage.getPublicKeyEncoded(alias))
        assertNull(encryptedStorage.getPrivateKeyEncoded(alias))
    }

    @Test
    @Config(sdk = [29])
    fun `usingSoftwareKeyStore should be true for API < 33`() = runTest {
        // When
        val result = encryptedStorage.usingSoftwareKeyStore

        // Then
        assertTrue(result)
    }

    @Test
    @Config(sdk = [33])
    fun `usingSoftwareKeyStore should be false for API >= 33`() = runTest {
        // When
        val result = encryptedStorage.usingSoftwareKeyStore

        // Then
        assertFalse(result)
    }

    // ==================== 高级加密操作测试 ====================

    @Test
    fun `encryptAndStore should encrypt and store data`() = runTest {
        // Given
        val key = "test-key"
        val plaintext = "sensitive-data"

        // When
        encryptedStorage.encryptAndStore(key, plaintext)

        // Then
        val result = encryptedStorage.decryptAndRead(key)
        assertEquals(plaintext, result)
    }

    @Test
    fun `decryptAndRead should return null for non-existent key`() = runTest {
        // When
        val result = encryptedStorage.decryptAndRead("non-existent")

        // Then
        assertNull(result)
    }

    @Test
    fun `encryptAndStore should handle special characters`() = runTest {
        // Given
        val key = "special-key"
        val plaintext = "特殊字符 !@#\$%^&*() 中文 🎉"

        // When
        encryptedStorage.encryptAndStore(key, plaintext)

        // Then
        val result = encryptedStorage.decryptAndRead(key)
        assertEquals(plaintext, result)
    }
}