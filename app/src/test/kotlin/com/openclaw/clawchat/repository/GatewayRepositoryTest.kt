package com.openclaw.clawchat.repository

import com.openclaw.clawchat.security.EncryptedStorage
import com.openclaw.clawchat.ui.state.GatewayConfigUi
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * GatewayRepository 单元测试
 * 
 * 测试覆盖率目标：≥80%
 */
@DisplayName("GatewayRepository 测试")
class GatewayRepositoryTest {

    private lateinit var encryptedStorage: EncryptedStorage
    private lateinit var gatewayRepository: GatewayRepository

    @BeforeEach
    fun setup() {
        encryptedStorage = mockk()
        gatewayRepository = GatewayRepository(encryptedStorage)
    }

    @Nested
    @DisplayName("获取配置测试")
    inner class GetConfigTests {

        @Test
        @DisplayName("获取 Gateway 配置成功")
        fun `getConfig returns gateway configuration`() {
            // Given
            val gatewayUrl = "ws://192.168.1.100:18789"
            every { encryptedStorage.getGatewayUrl() } returns gatewayUrl

            // When
            val config = gatewayRepository.getConfig()

            // Then
            assertNotNull(config)
            assertEquals("default", config?.id)
            assertEquals("192.168.1.100", config?.host)
            assertEquals(18789, config?.port)
            assertFalse(config?.useTls == true)
        }

        @Test
        @DisplayName("获取使用 TLS 的 Gateway 配置")
        fun `getConfig returns gateway configuration with TLS`() {
            // Given
            val gatewayUrl = "wss://gateway.example.com:8443"
            every { encryptedStorage.getGatewayUrl() } returns gatewayUrl

            // When
            val config = gatewayRepository.getConfig()

            // Then
            assertNotNull(config)
            assertEquals("gateway.example.com", config?.host)
            assertEquals(8443, config?.port)
            assertTrue(config?.useTls == true)
        }

        @Test
        @DisplayName("获取不存在的配置返回 null")
        fun `getConfig returns null when no configuration exists`() {
            // Given
            every { encryptedStorage.getGatewayUrl() } returns null

            // When
            val config = gatewayRepository.getConfig()

            // Then
            assertNull(config)
        }

        @Test
        @DisplayName("获取空 URL 返回 null")
        fun `getConfig returns null when URL is empty`() {
            // Given
            every { encryptedStorage.getGatewayUrl() } returns ""

            // When
            val config = gatewayRepository.getConfig()

            // Then
            assertNull(config)
        }

        @Test
        @DisplayName("获取配置使用默认端口当端口缺失")
        fun `getConfig uses default port when port is missing from URL`() {
            // Given
            val gatewayUrl = "ws://192.168.1.100"
            every { encryptedStorage.getGatewayUrl() } returns gatewayUrl

            // When
            val config = gatewayRepository.getConfig()

            // Then
            assertNotNull(config)
            assertEquals(18789, config?.port)
        }
    }

    @Nested
    @DisplayName("保存配置测试")
    inner class SaveConfigTests {

        @Test
        @DisplayName("保存 Gateway 配置成功")
        fun `saveConfig saves gateway configuration successfully`() {
            // Given
            val config = GatewayConfigUi(
                id = "default",
                name = "My Gateway",
                host = "192.168.1.100",
                port = 18789,
                useTls = false,
                isCurrent = true
            )

            // When
            gatewayRepository.saveConfig(config)

            // Then
            verify { encryptedStorage.saveGatewayUrl("ws://192.168.1.100:18789") }
        }

        @Test
        @DisplayName("保存使用 TLS 的 Gateway 配置")
        fun `saveConfig saves gateway configuration with TLS`() {
            // Given
            val config = GatewayConfigUi(
                id = "default",
                name = "Secure Gateway",
                host = "gateway.example.com",
                port = 8443,
                useTls = true,
                isCurrent = true
            )

            // When
            gatewayRepository.saveConfig(config)

            // Then
            verify { encryptedStorage.saveGatewayUrl("wss://gateway.example.com:8443") }
        }

        @Test
        @DisplayName("保存 Gateway 使用简化方法")
        fun `saveGateway saves configuration with simplified method`() {
            // Given
            val host = "192.168.1.100"
            val port = 18789

            // When
            gatewayRepository.saveGateway(host, port, useTls = false)

            // Then
            verify { encryptedStorage.saveGatewayUrl("ws://192.168.1.100:18789") }
        }

        @Test
        @DisplayName("保存 Gateway 使用默认端口")
        fun `saveGateway uses default port when not specified`() {
            // Given
            val host = "192.168.1.100"

            // When
            gatewayRepository.saveGateway(host)

            // Then
            verify { encryptedStorage.saveGatewayUrl("ws://192.168.1.100:18789") }
        }

        @Test
        @DisplayName("保存使用 TLS 的 Gateway")
        fun `saveGateway saves with TLS enabled`() {
            // Given
            val host = "secure.example.com"
            val port = 8443

            // When
            gatewayRepository.saveGateway(host, port, useTls = true)

            // Then
            verify { encryptedStorage.saveGatewayUrl("wss://secure.example.com:8443") }
        }
    }

    @Nested
    @DisplayName("删除配置测试")
    inner class DeleteConfigTests {

        @Test
        @DisplayName("删除 Gateway 配置成功")
        fun `deleteConfig deletes gateway configuration successfully`() {
            // When
            gatewayRepository.deleteConfig()

            // Then
            verify { encryptedStorage.clearGatewayConfig() }
        }

        @Test
        @DisplayName("删除指定 ID 的 Gateway 配置")
        fun `deleteConfig deletes configuration for specified id`() {
            // When
            gatewayRepository.deleteConfig("custom-gateway")

            // Then
            verify { encryptedStorage.clearGatewayConfig() }
        }
    }

    @Nested
    @DisplayName("配置检查测试")
    inner class ConfigCheckTests {

        @Test
        @DisplayName("检查已配置的 Gateway 返回 true")
        fun `hasConfiguredGateway returns true when gateway is configured`() {
            // Given
            every { encryptedStorage.getGatewayUrl() } returns "ws://192.168.1.100:18789"

            // When
            val hasConfig = gatewayRepository.hasConfiguredGateway()

            // Then
            assertTrue(hasConfig)
        }

        @Test
        @DisplayName("检查未配置的 Gateway 返回 false")
        fun `hasConfiguredGateway returns false when gateway is not configured`() {
            // Given
            every { encryptedStorage.getGatewayUrl() } returns null

            // When
            val hasConfig = gatewayRepository.hasConfiguredGateway()

            // Then
            assertFalse(hasConfig)
        }

        @Test
        @DisplayName("检查空 URL 的 Gateway 返回 false")
        fun `hasConfiguredGateway returns false when URL is empty`() {
            // Given
            every { encryptedStorage.getGatewayUrl() } returns ""

            // When
            val hasConfig = gatewayRepository.hasConfiguredGateway()

            // Then
            assertFalse(hasConfig)
        }
    }

    @Nested
    @DisplayName("获取所有配置测试")
    inner class GetAllConfigsTests {

        @Test
        @DisplayName("获取所有配置返回单个配置列表")
        fun `getAllConfigs returns list with single config`() {
            // Given
            val gatewayUrl = "ws://192.168.1.100:18789"
            every { encryptedStorage.getGatewayUrl() } returns gatewayUrl

            // When
            val configs = gatewayRepository.getAllConfigs()

            // Then
            assertEquals(1, configs.size)
            assertEquals("192.168.1.100", configs.first().host)
        }

        @Test
        @DisplayName("获取所有配置当无配置时返回空列表")
        fun `getAllConfigs returns empty list when no config exists`() {
            // Given
            every { encryptedStorage.getGatewayUrl() } returns null

            // When
            val configs = gatewayRepository.getAllConfigs()

            // Then
            assertTrue(configs.isEmpty())
        }
    }

    @Nested
    @DisplayName("设置当前 Gateway 测试")
    inner class SetCurrentGatewayTests {

        @Test
        @DisplayName("设置当前 Gateway 不抛出异常")
        fun `setCurrentGateway does not throw exception`() {
            // When & Then
            // 当前实现只支持单个 Gateway，此方法预留用于未来多 Gateway 支持
            assertDoesNotThrow {
                gatewayRepository.setCurrentGateway("default")
            }
        }

        @Test
        @DisplayName("设置自定义 Gateway ID 不抛出异常")
        fun `setCurrentGateway with custom id does not throw exception`() {
            // When & Then
            assertDoesNotThrow {
                gatewayRepository.setCurrentGateway("custom-gateway")
            }
        }
    }

    @Nested
    @DisplayName("URL 解析测试")
    inner class UrlParsingTests {

        @Test
        @DisplayName("解析标准 WebSocket URL")
        fun `parseGatewayUrl parses standard websocket URL`() {
            // Given
            val url = "ws://192.168.1.100:18789"
            every { encryptedStorage.getGatewayUrl() } returns url

            // When
            val config = gatewayRepository.getConfig()

            // Then
            assertNotNull(config)
            assertEquals("192.168.1.100", config?.host)
            assertEquals(18789, config?.port)
            assertFalse(config?.useTls == true)
        }

        @Test
        @DisplayName("解析安全 WebSocket URL")
        fun `parseGatewayUrl parses secure websocket URL`() {
            // Given
            val url = "wss://gateway.example.com:8443"
            every { encryptedStorage.getGatewayUrl() } returns url

            // When
            val config = gatewayRepository.getConfig()

            // Then
            assertNotNull(config)
            assertEquals("gateway.example.com", config?.host)
            assertEquals(8443, config?.port)
            assertTrue(config?.useTls == true)
        }

        @Test
        @DisplayName("解析不带端口的 URL 使用默认端口")
        fun `parseGatewayUrl uses default port when port is missing`() {
            // Given
            val url = "ws://192.168.1.100"
            every { encryptedStorage.getGatewayUrl() } returns url

            // When
            val config = gatewayRepository.getConfig()

            // Then
            assertNotNull(config)
            assertEquals(18789, config?.port)
        }
    }
}
