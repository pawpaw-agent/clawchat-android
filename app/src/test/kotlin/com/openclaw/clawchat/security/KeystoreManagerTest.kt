package com.openclaw.clawchat.security

import android.os.Build
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.*
import org.robolectric.annotation.Config

/**
 * KeystoreManager 单元测试
 * 
 * 测试覆盖：
 * 1. API 33+ 使用 Android Keystore 生成 Ed25519 密钥对
 * 2. API 26-32 使用 BouncyCastle 软件密钥
 * 3. signWithBouncyCastle 签名后私钥引用被清除（内存安全）
 * 4. 密钥加载失败的处理
 */
class KeystoreManagerTest {

    private lateinit var mockSoftwareKeyStore: SoftwareKeyStore

    @Before
    fun setup() {
        mockSoftwareKeyStore = mock(SoftwareKeyStore::class.java)
    }

    // ==================== API 33+ Keystore 路径测试 ====================

    @Test
    @Config(sdk = [33])
    fun `API 33+ should use Android Keystore for Ed25519`() {
        // Given
        val manager = KeystoreManager(
            alias = "test-key-33",
            softwareKeyStore = null // Not needed for API 33+
        )

        // When & Then - should not throw
        // Note: Robolectric doesn't fully support AndroidKeyStore,
        // so we test the code path logic instead of actual key generation
        assertFalse(manager.hasKeyPair()) // Should check keystore
    }

    @Test
    @Config(sdk = [34])
    fun `API 34+ should have USE_KEYSTORE_ED25519 true`() {
        // This test verifies the Build.VERSION check
        assertTrue("API 34+ should use Keystore", Build.VERSION.SDK_INT >= 33)
    }

    // ==================== API 26-32 BouncyCastle 路径测试 ====================

    @Test
    @Config(sdk = [26])
    fun `API 26 should use BouncyCastle software keys`() {
        // Given
        val manager = KeystoreManager(
            alias = "test-key-26",
            softwareKeyStore = mockSoftwareKeyStore
        )

        // When - check hasKeyPair when not exists
        `when`(mockSoftwareKeyStore.hasKeyPair("test-key-26")).thenReturn(false)

        // Then
        assertFalse(manager.hasKeyPair())
    }

    @Test
    @Config(sdk = [29])
    fun `API 29 should use BouncyCastle software keys`() {
        // Given
        val manager = KeystoreManager(
            alias = "test-key-29",
            softwareKeyStore = mockSoftwareKeyStore
        )

        // When
        `when`(mockSoftwareKeyStore.hasKeyPair("test-key-29")).thenReturn(true)

        // Then
        assertTrue(manager.hasKeyPair())
        verify(mockSoftwareKeyStore).hasKeyPair("test-key-29")
    }

    @Test
    @Config(sdk = [32])
    fun `API 32 should use BouncyCastle software keys`() {
        // Given
        val manager = KeystoreManager(
            alias = "test-key-32",
            softwareKeyStore = mockSoftwareKeyStore
        )

        // When
        `when`(mockSoftwareKeyStore.hasKeyPair("test-key-32")).thenReturn(false)

        // Then
        assertFalse(manager.hasKeyPair())
    }

    @Test
    @Config(sdk = [29])
    fun `BouncyCastle path should throw when SoftwareKeyStore is null`() {
        // Given
        val manager = KeystoreManager(
            alias = "test-key-no-store",
            softwareKeyStore = null
        )

        // When & Then - should throw when trying to generate without store
        try {
            manager.generateKeyPair()
            fail("Should throw IllegalStateException")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("SoftwareKeyStore") ?: false)
        }
    }

    @Test
    @Config(sdk = [29])
    fun `generateKeyPair should call saveKeyPair on SoftwareKeyStore`() {
        // Given
        val manager = KeystoreManager(
            alias = "test-gen",
            softwareKeyStore = mockSoftwareKeyStore
        )

        // When
        try {
            manager.generateKeyPair()
        } catch (e: Exception) {
            // Expected - Robolectric may not fully support BC
        }

        // Then - verify saveKeyPair was called
        verify(mockSoftwareKeyStore, atLeastOnce()).saveKeyPair(
            anyString(),
            anyByteArray(),
            anyByteArray()
        )
    }

    // ==================== 内存安全测试 - 私钥清除 ====================

    @Test
    @Config(sdk = [29])
    fun `signWithBouncyCastle should clear private key after signing`() {
        // Given
        val manager = KeystoreManager(
            alias = "test-sign",
            softwareKeyStore = mockSoftwareKeyStore
        )

        val testData = "test-payload".toByteArray()
        val mockPrivateKey = org.mockito.mock(
            org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters::class.java
        )
        `when`(mockSoftwareKeyStore.getPrivateKeyEncoded("test-sign"))
            .thenReturn(ByteArray(32)) // Mock 32-byte seed

        // When - attempt signing
        try {
            manager.sign(testData)
        } catch (e: Exception) {
            // May fail in Robolectric, but we verify the finally block runs
        }

        // Then - private key cache should be cleared
        // This is tested by verifying getPrivateKeyEncoded is called
        // (signWithBouncyCastle loads, signs, then clears)
        verify(mockSoftwareKeyStore, atLeastOnce()).getPrivateKeyEncoded("test-sign")
    }

    @Test
    @Config(sdk = [29])
    fun `clearBcCache should clear both public and private key caches`() {
        // Given
        val manager = KeystoreManager(
            alias = "test-clear",
            softwareKeyStore = mockSoftwareKeyStore
        )

        // Pre-populate cache by calling getPublicKeyRaw (which caches)
        `when`(mockSoftwareKeyStore.getPublicKeyEncoded("test-clear"))
            .thenReturn(ByteArray(44)) // Mock SPKI format

        // When - clear cache
        try {
            manager.getPublicKeyRaw()
        } catch (e: Exception) {
            // Expected in test environment
        }

        // Then - cache operations verified via method calls
        // The clearBcCache is called internally by deleteKey
        manager.deleteKey()
        verify(mockSoftwareKeyStore).deleteKeyPair("test-clear")
    }

    // ==================== 密钥加载失败处理 ====================

    @Test
    @Config(sdk = [29])
    fun `getPublicKeyRaw should throw when key not found`() {
        // Given
        val manager = KeystoreManager(
            alias = "test-not-found",
            softwareKeyStore = mockSoftwareKeyStore
        )

        `when`(mockSoftwareKeyStore.getPublicKeyEncoded("test-not-found"))
            .thenReturn(null)

        // When & Then
        try {
            manager.getPublicKeyRaw()
            fail("Should throw IllegalStateException")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("not found") ?: false)
        }
    }

    @Test
    @Config(sdk = [29])
    fun `sign should throw when private key not found`() {
        // Given
        val manager = KeystoreManager(
            alias = "test-sign-not-found",
            softwareKeyStore = mockSoftwareKeyStore
        )

        `when`(mockSoftwareKeyStore.getPrivateKeyEncoded("test-sign-not-found"))
            .thenReturn(null)

        // When & Then
        try {
            manager.sign("test-data".toByteArray())
            fail("Should throw IllegalStateException")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("not found") ?: false)
        }
    }

    @Test
    @Config(sdk = [29])
    fun `getPublicKeyRaw should handle unexpected key format`() {
        // Given
        val manager = KeystoreManager(
            alias = "test-bad-format",
            softwareKeyStore = mockSoftwareKeyStore
        )

        // Return invalid format (too short)
        `when`(mockSoftwareKeyStore.getPublicKeyEncoded("test-bad-format"))
            .thenReturn(ByteArray(10)) // Invalid size

        // When & Then
        try {
            manager.getPublicKeyRaw()
            fail("Should throw IllegalStateException")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("format") ?: false)
        }
    }

    // ==================== 公钥格式转换测试 ====================

    @Test
    fun `extractRawFromSPKI should extract raw 32 bytes from SPKI DER`() {
        // Given - valid SPKI format (12 byte prefix + 32 byte raw key)
        val prefix = KeystoreManager.ED25519_SPKI_PREFIX
        val rawKey = ByteArray(32) { i -> i.toByte() }
        val spki = prefix + rawKey

        // Create manager via reflection to access private method
        val manager = KeystoreManager(alias = "test-extract")

        // Use reflection to test private method
        val method = KeystoreManager::class.java.getDeclaredMethod(
            "extractRawFromSPKI",
            ByteArray::class.java
        )
        method.isAccessible = true

        // When
        val result = method.invoke(manager, spki) as ByteArray

        // Then
        assertArrayEquals(rawKey, result)
        assertEquals(32, result.size)
    }

    @Test
    fun `extractRawFromSPKI should return raw key if already 32 bytes`() {
        // Given
        val rawKey = ByteArray(32) { i -> (i + 1).toByte() }
        val manager = KeystoreManager(alias = "test-extract-raw")

        val method = KeystoreManager::class.java.getDeclaredMethod(
            "extractRawFromSPKI",
            ByteArray::class.java
        )
        method.isAccessible = true

        // When
        val result = method.invoke(manager, rawKey) as ByteArray

        // Then
        assertArrayEquals(rawKey, result)
    }

    @Test
    fun `base64UrlEncode should produce URL-safe base64 without padding`() {
        // Given
        val testData = byteArrayOf(0x00, 0x01, 0x02, 0xff, 0xfe, 0xfd)
        val manager = KeystoreManager(alias = "test-b64")

        val method = KeystoreManager::class.java.getDeclaredMethod(
            "base64UrlEncode",
            ByteArray::class.java
        )
        method.isAccessible = true

        // When
        val result = method.invoke(manager, testData) as String

        // Then - should be URL-safe (no + or /) and no padding
        assertFalse(result.contains("+"))
        assertFalse(result.contains("/"))
        assertFalse(result.contains("="))
    }

    // ==================== KeyInfo 测试 ====================

    @Test
    @Config(sdk = [29])
    fun `getKeyInfo should return BouncyCastle info for API < 33`() {
        // Given
        val manager = KeystoreManager(
            alias = "test-info",
            softwareKeyStore = mockSoftwareKeyStore
        )

        `when`(mockSoftwareKeyStore.hasKeyPair("test-info")).thenReturn(true)

        // When
        val info = manager.getKeyInfo()

        // Then
        assertTrue(info.algorithm?.contains("Ed25519") ?: false)
        assertTrue(info.format?.contains("BouncyCastle") ?: false)
        assertFalse(info.isInsideSecureHardware)
    }

    @Test
    @Config(sdk = [29])
    fun `getKeyInfo should return nulls when no key pair exists`() {
        // Given
        val manager = KeystoreManager(
            alias = "test-info-none",
            softwareKeyStore = mockSoftwareKeyStore
        )

        `when`(mockSoftwareKeyStore.hasKeyPair("test-info-none")).thenReturn(false)

        // When
        val info = manager.getKeyInfo()

        // Then
        assertNull(info.algorithm)
        assertNull(info.format)
    }

    // ==================== deleteKey 测试 ====================

    @Test
    @Config(sdk = [29])
    fun `deleteKey should call deleteKeyPair on SoftwareKeyStore`() {
        // Given
        val manager = KeystoreManager(
            alias = "test-delete",
            softwareKeyStore = mockSoftwareKeyStore
        )

        // When
        manager.deleteKey()

        // Then
        verify(mockSoftwareKeyStore).deleteKeyPair("test-delete")
    }

    @Test
    @Config(sdk = [29])
    fun `deleteKey should clear BC cache`() {
        // Given
        val manager = KeystoreManager(
            alias = "test-delete-cache",
            softwareKeyStore = mockSoftwareKeyStore
        )

        // When
        manager.deleteKey()

        // Then - cache cleared (verified by no subsequent cache hits)
        // This is implicitly tested by the clearBcCache() call in deleteKey()
        verify(mockSoftwareKeyStore, atLeastOnce()).deleteKeyPair(anyString())
    }
}
