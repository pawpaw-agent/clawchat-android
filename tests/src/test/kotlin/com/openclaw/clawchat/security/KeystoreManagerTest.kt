package com.openclaw.clawchat.security

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.security.*
import java.security.cert.Certificate
import java.util.Date

/**
 * KeystoreManager 单元测试
 * 
 * 测试覆盖：
 * - 密钥对生成
 * - 公钥获取
 * - 挑战签名
 * - 密钥删除
 * - 密钥信息查询
 */
@DisplayName("KeystoreManager 测试")
class KeystoreManagerTest {

    private lateinit var keystoreManager: KeystoreManager
    private lateinit var mockKeyStore: KeyStore
    private lateinit var mockPrivateKeyEntry: KeyStore.PrivateKeyEntry
    private lateinit var mockCertificate: Certificate
    private lateinit var mockPublicKey: PublicKey
    private lateinit var mockPrivateKey: PrivateKey

    private val testAlias = "test_clawchat_key"

    @BeforeEach
    fun setUp() {
        // 模拟 Android Keystore 静态方法
        mockkStatic(KeyStore::class)
        
        mockKeyStore = mockk()
        mockPrivateKeyEntry = mockk()
        mockCertificate = mockk()
        mockPublicKey = mockk()
        mockPrivateKey = mockk()

        // 模拟 KeyStore 实例创建
        every { KeyStore.getInstance("AndroidKeyStore") } returns mockKeyStore
        every { mockKeyStore.load(null) } returns Unit

        keystoreManager = KeystoreManager(alias = testAlias)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Nested
    @DisplayName("hasKeyPair() 测试")
    inner class HasKeyPairTests {

        @Test
        @DisplayName("当密钥存在时返回 true")
        fun `returns true when key pair exists`() {
            every { mockKeyStore.containsAlias(testAlias) } returns true

            val result = keystoreManager.hasKeyPair()

            assertTrue(result)
            verify { mockKeyStore.containsAlias(testAlias) }
        }

        @Test
        @DisplayName("当密钥不存在时返回 false")
        fun `returns false when key pair does not exist`() {
            every { mockKeyStore.containsAlias(testAlias) } returns false

            val result = keystoreManager.hasKeyPair()

            assertFalse(result)
            verify { mockKeyStore.containsAlias(testAlias) }
        }
    }

    @Nested
    @DisplayName("getPublicKeyPem() 测试")
    inner class GetPublicKeyPemTests {

        @BeforeEach
        fun setupPublicKeyMocks() {
            every { mockKeyStore.getEntry(testAlias, null) } returns mockPrivateKeyEntry
            every { mockPrivateKeyEntry.certificate } returns mockCertificate
            every { mockCertificate.publicKey } returns mockPublicKey
            every { mockPublicKey.encoded } returns byteArrayOf(1, 2, 3, 4, 5)
            every { mockPublicKey.algorithm } returns "EC"
            every { mockPublicKey.format } returns "X.509"
        }

        @Test
        @DisplayName("成功获取 PEM 格式公钥")
        fun `returns PEM formatted public key`() {
            val result = keystoreManager.getPublicKeyPem()

            assertTrue(result.startsWith("-----BEGIN PUBLIC KEY-----"))
            assertTrue(result.endsWith("-----END PUBLIC KEY-----"))
            assertTrue(result.contains("AQIDBAU")) // Base64 编码的 [1,2,3,4,5]
        }

        @Test
        @DisplayName("当密钥不存在时抛出 IllegalStateException")
        fun `throws exception when key pair does not exist`() {
            every { mockKeyStore.getEntry(testAlias, null) } returns null

            assertThrows<IllegalStateException> {
                keystoreManager.getPublicKeyPem()
            }
        }
    }

    @Nested
    @DisplayName("signChallenge() 测试")
    inner class SignChallengeTests {

        @BeforeEach
        fun setupSigningMocks() {
            every { mockKeyStore.getEntry(testAlias, null) } returns mockPrivateKeyEntry
            every { mockPrivateKeyEntry.certificate } returns mockCertificate
            every { mockCertificate.publicKey } returns mockPublicKey
            every { mockPrivateKeyEntry.privateKey } returns mockPrivateKey
        }

        @Test
        @DisplayName("使用 ByteArray 挑战成功签名")
        fun `signs challenge with ByteArray input`() {
            val challenge = "test_challenge".toByteArray()
            val expectedSignature = byteArrayOf(10, 20, 30, 40)

            mockkStatic(Signature::class)
            val mockSignature = mockk<Signature>()
            every { Signature.getInstance("SHA256withECDSA") } returns mockSignature
            every { mockSignature.initSign(mockPrivateKey) } returns Unit
            every { mockSignature.update(challenge) } returns Unit
            every { mockSignature.sign() } returns expectedSignature

            val result = keystoreManager.signChallenge(challenge)

            assertArrayEquals(expectedSignature, result)
            verify { mockSignature.initSign(mockPrivateKey) }
            verify { mockSignature.update(challenge) }
            verify { mockSignature.sign() }
        }

        @Test
        @DisplayName("使用 String 挑战成功签名并返回 Base64")
        fun `signs challenge with String input and returns Base64`() {
            val challenge = "test_challenge_string"
            val signatureBytes = byteArrayOf(10, 20, 30, 40)

            mockkStatic(Signature::class)
            mockkStatic(android.util.Base64::class)
            
            val mockSignature = mockk<Signature>()
            every { Signature.getInstance("SHA256withECDSA") } returns mockSignature
            every { mockSignature.initSign(mockPrivateKey) } returns Unit
            every { mockSignature.update(any<ByteArray>()) } returns Unit
            every { mockSignature.sign() } returns signatureBytes
            every { android.util.Base64.encodeToString(signatureBytes, android.util.Base64.NO_WRAP) } 
                returns "Cgw0JA=="

            val result = keystoreManager.signChallenge(challenge)

            assertEquals("Cgw0JA==", result)
        }

        @Test
        @DisplayName("当密钥不存在时抛出 IllegalStateException")
        fun `throws exception when key pair does not exist for signing`() {
            every { mockKeyStore.getEntry(testAlias, null) } returns null

            assertThrows<IllegalStateException> {
                keystoreManager.signChallenge("test".toByteArray())
            }
        }
    }

    @Nested
    @DisplayName("deleteKey() 测试")
    inner class DeleteKeyTests {

        @Test
        @DisplayName("成功删除存在的密钥")
        fun `deletes existing key`() {
            every { mockKeyStore.containsAlias(testAlias) } returnsMany listOf(true, false)
            every { mockKeyStore.deleteEntry(testAlias) } returns Unit

            keystoreManager.deleteKey()

            verify { mockKeyStore.deleteEntry(testAlias) }
        }

        @Test
        @DisplayName("当密钥不存在时不执行删除操作")
        fun `does nothing when key does not exist`() {
            every { mockKeyStore.containsAlias(testAlias) } returns false

            keystoreManager.deleteKey()

            verify(exactly = 0) { mockKeyStore.deleteEntry(any()) }
        }
    }

    @Nested
    @DisplayName("getKeyInfo() 测试")
    inner class GetKeyInfoTests {

        @Test
        @DisplayName("成功获取密钥信息")
        fun `returns key info when key exists`() {
            val notBefore = Date(1609459200000) // 2021-01-01
            val notAfter = Date(1640995200000)  // 2022-01-01

            every { mockKeyStore.getEntry(testAlias, null) } returns mockPrivateKeyEntry
            every { mockPrivateKeyEntry.certificate } returns mockCertificate
            every { mockPrivateKeyEntry.isInsideSecureHardware } returns true
            every { mockCertificate.publicKey } returns mockPublicKey
            every { mockPublicKey.algorithm } returns "EC"
            every { mockPublicKey.format } returns "X.509"
            every { mockCertificate.notBefore } returns notBefore
            every { mockCertificate.notAfter } returns notAfter

            val info = keystoreManager.getKeyInfo()

            assertEquals("EC", info.algorithm)
            assertEquals("X.509", info.format)
            assertTrue(info.isInsideSecureHardware)
            assertEquals(notBefore, info.certificateNotBefore)
            assertEquals(notAfter, info.certificateNotAfter)
        }

        @Test
        @DisplayName("当密钥不存在时返回默认 KeyInfo")
        fun `returns default KeyInfo when key does not exist`() {
            every { mockKeyStore.getEntry(testAlias, null) } returns null

            val info = keystoreManager.getKeyInfo()

            assertNull(info.algorithm)
            assertNull(info.format)
            assertFalse(info.isInsideSecureHardware)
            assertNull(info.certificateNotBefore)
            assertNull(info.certificateNotAfter)
        }
    }

    @Nested
    @DisplayName("KeyInfo 数据类测试")
    inner class KeyInfoTests {

        @Test
        @DisplayName("KeyInfo 数据类正确创建")
        fun `creates KeyInfo data class correctly`() {
            val notBefore = Date()
            val notAfter = Date()

            val keyInfo = KeystoreManager.KeyInfo(
                algorithm = "EC",
                format = "X.509",
                isInsideSecureHardware = true,
                certificateNotBefore = notBefore,
                certificateNotAfter = notAfter
            )

            assertEquals("EC", keyInfo.algorithm)
            assertEquals("X.509", keyInfo.format)
            assertTrue(keyInfo.isInsideSecureHardware)
            assertEquals(notBefore, keyInfo.certificateNotBefore)
            assertEquals(notAfter, keyInfo.certificateNotAfter)
        }

        @Test
        @DisplayName("KeyInfo 数据类支持 copy 操作")
        fun `supports copy operation`() {
            val original = KeystoreManager.KeyInfo(
                algorithm = "EC",
                format = "X.509",
                isInsideSecureHardware = false,
                certificateNotBefore = null,
                certificateNotAfter = null
            )

            val modified = original.copy(isInsideSecureHardware = true, algorithm = "RSA")

            assertEquals("RSA", modified.algorithm)
            assertTrue(modified.isInsideSecureHardware)
            assertEquals("X.509", modified.format) // 保持不变
        }
    }
}
