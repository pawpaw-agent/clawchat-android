package com.openclaw.clawchat.network.protocol

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.openclaw.clawchat.security.SecurityModule
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Challenge-Response 认证集成测试
 * 
 * 测试完整的认证流程：
 * 1. 构建认证请求
 * 2. 处理服务器挑战
 * 3. 签名并构建响应
 * 4. 处理认证成功
 */
@RunWith(RobolectricTestRunner::class)
class ChallengeResponseAuthTest {
    
    private lateinit var context: Context
    private lateinit var securityModule: SecurityModule
    private lateinit var authHandler: ChallengeResponseAuth
    
    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        securityModule = SecurityModule(context)
        authHandler = ChallengeResponseAuth(securityModule)
    }
    
    // ==================== 步骤 1: 认证请求测试 ====================
    
    @Test
    fun testBuildAuthRequest() = runBlocking {
        // 初始化安全模块
        securityModule.initialize()
        
        // 构建认证请求
        val authRequest = authHandler.buildAuthRequest()
        
        // 验证请求内容
        assertNotNull("deviceId 不应为空", authRequest.deviceId)
        assertTrue("deviceId 不应为空字符串", authRequest.deviceId.isNotEmpty())
        
        assertNotNull("publicKey 不应为空", authRequest.publicKey)
        assertTrue("publicKey 应包含 BEGIN PUBLIC KEY", authRequest.publicKey.contains("BEGIN PUBLIC KEY"))
        
        assertNotNull("clientInfo 不应为空", authRequest.clientInfo)
        assertEquals("openclaw-android", authRequest.clientInfo.clientId)
        assertEquals("android", authHandler.buildAuthRequest().clientInfo.platform)
        assertEquals("3.0.0", authRequest.clientInfo.protocolVersion)
    }
    
    // ==================== 步骤 2: 挑战处理测试 ====================
    
    @Test
    fun testHandleChallenge() = runBlocking {
        // 初始化
        securityModule.initialize()
        
        // 构建有效的挑战
        val challenge = AuthChallenge(
            type = "challenge",
            nonce = "550e8400-e29b-41d4-a716-446655440000",
            expiresAt = System.currentTimeMillis() + 300000, // 5 分钟后过期
            protocolVersion = "3.0.0"
        )
        
        // 处理挑战
        val result = authHandler.handleChallenge(challenge)
        
        // 验证结果
        assertTrue("挑战处理应该成功", result.isSuccess)
    }
    
    @Test
    fun testHandleExpiredChallenge() = runBlocking {
        // 初始化
        securityModule.initialize()
        
        // 构建已过期的挑战
        val challenge = AuthChallenge(
            type = "challenge",
            nonce = "550e8400-e29b-41d4-a716-446655440000",
            expiresAt = System.currentTimeMillis() - 1000, // 已过期
            protocolVersion = "3.0.0"
        )
        
        // 处理挑战
        val result = authHandler.handleChallenge(challenge)
        
        // 验证结果
        assertTrue("过期挑战应该失败", result.isFailure)
    }
    
    @Test
    fun testHandleInvalidProtocolVersion() = runBlocking {
        // 初始化
        securityModule.initialize()
        
        // 构建不兼容协议版本的挑战
        val challenge = AuthChallenge(
            type = "challenge",
            nonce = "550e8400-e29b-41d4-a716-446655440000",
            expiresAt = System.currentTimeMillis() + 300000,
            protocolVersion = "1.0.0" // 不兼容的版本
        )
        
        // 处理挑战
        val result = authHandler.handleChallenge(challenge)
        
        // 验证结果
        assertTrue("不兼容协议版本应该失败", result.isFailure)
    }
    
    // ==================== 步骤 3: 构建响应测试 ====================
    
    @Test
    fun testBuildAuthResponse() = runBlocking {
        // 初始化
        securityModule.initialize()
        
        // 先处理挑战
        val challenge = AuthChallenge(
            type = "challenge",
            nonce = "550e8400-e29b-41d4-a716-446655440000",
            expiresAt = System.currentTimeMillis() + 300000,
            protocolVersion = "3.0.0"
        )
        authHandler.handleChallenge(challenge)
        
        // 构建响应
        val authResponse = authHandler.buildAuthResponse()
        
        // 验证响应内容
        assertEquals("nonce 应该匹配", challenge.nonce, authResponse.nonce)
        assertNotNull("signature 不应为空", authResponse.signature)
        assertTrue("signature 不应为空字符串", authResponse.signature.isNotEmpty())
        assertNotNull("deviceId 不应为空", authResponse.deviceId)
    }
    
    @Test(expected = IllegalStateException::class)
    fun testBuildAuthResponseWithoutChallenge() = runBlocking {
        // 初始化
        securityModule.initialize()
        
        // 没有处理挑战就直接构建响应，应该抛出异常
        authHandler.buildAuthResponse()
    }
    
    @Test(expected = IllegalStateException::class)
    fun testBuildAuthResponseWithExpiredChallenge() = runBlocking {
        // 初始化
        securityModule.initialize()
        
        // 处理一个立即过期的挑战
        val challenge = AuthChallenge(
            type = "challenge",
            nonce = "550e8400-e29b-41d4-a716-446655440000",
            expiresAt = System.currentTimeMillis() + 100, // 100ms 后过期
            protocolVersion = "3.0.0"
        )
        authHandler.handleChallenge(challenge)
        
        // 等待挑战过期
        kotlinx.coroutines.delay(200)
        
        // 构建响应应该失败
        authHandler.buildAuthResponse()
    }
    
    // ==================== 步骤 4: 认证成功处理测试 ====================
    
    @Test
    fun testHandleAuthSuccess() = runBlocking {
        // 初始化
        securityModule.initialize()
        
        // 构建认证成功响应
        val success = AuthSuccess(
            type = "auth_success",
            deviceToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.test",
            expiresIn = 86400,
            protocolVersion = "3.0.0"
        )
        
        // 处理认证成功
        val result = authHandler.handleAuthSuccess(success)
        
        // 验证结果
        assertTrue("认证成功应该返回 success=true", result.success)
        assertNotNull("deviceToken 不应为空", result.deviceToken)
        
        // 验证 token 已存储
        val storedToken = securityModule.getAuthToken()
        assertNotNull("token 应该已存储", storedToken)
        assertEquals("stored token should match", success.deviceToken, storedToken)
    }
    
    @Test
    fun testHandleAuthSuccessWithInvalidProtocolVersion() = runBlocking {
        // 初始化
        securityModule.initialize()
        
        // 构建不兼容协议版本的认证成功
        val success = AuthSuccess(
            type = "auth_success",
            deviceToken = "test-token",
            expiresIn = 86400,
            protocolVersion = "1.0.0" // 不兼容
        )
        
        // 处理认证成功
        val result = authHandler.handleAuthSuccess(success)
        
        // 验证结果
        assertFalse("不兼容协议版本应该返回 success=false", result.success)
        assertEquals("错误码应该是 PROTOCOL_VERSION_MISMATCH", 
            ProtocolErrorCode.PROTOCOL_VERSION_MISMATCH, result.errorCode)
    }
    
    @Test
    fun testHandleAuthSuccessWithEmptyToken() = runBlocking {
        // 初始化
        securityModule.initialize()
        
        // 构建空 token 的认证成功
        val success = AuthSuccess(
            type = "auth_success",
            deviceToken = "", // 空 token
            expiresIn = 86400,
            protocolVersion = "3.0.0"
        )
        
        // 处理认证成功
        val result = authHandler.handleAuthSuccess(success)
        
        // 验证结果
        assertFalse("空 token 应该返回 success=false", result.success)
        assertEquals("错误码应该是 AUTH_FAILED", 
            ProtocolErrorCode.AUTH_FAILED, result.errorCode)
    }
    
    // ==================== 错误处理测试 ====================
    
    @Test
    fun testHandleAuthError() = runBlocking {
        // 初始化
        securityModule.initialize()
        
        // 构建认证错误
        val error = ProtocolError(
            code = 2002,
            message = "Invalid signature",
            details = "Signature verification failed"
        )
        
        // 处理错误
        val result = authHandler.handleAuthError(error)
        
        // 验证结果
        assertFalse("错误处理应该返回 success=false", result.success)
        assertEquals("错误消息应该匹配", "Invalid signature", result.error)
        assertEquals("错误码应该匹配", ProtocolErrorCode.INVALID_SIGNATURE, result.errorCode)
    }
    
    @Test
    fun testHandleAuthErrorWithUnknownCode() = runBlocking {
        // 初始化
        securityModule.initialize()
        
        // 构建未知错误码
        val error = ProtocolError(
            code = 9999,
            message = "Unknown error"
        )
        
        // 处理错误
        val result = authHandler.handleAuthError(error)
        
        // 验证结果
        assertFalse("错误处理应该返回 success=false", result.success)
        assertEquals("未知错误码应该映射到 UNKNOWN_ERROR", 
            ProtocolErrorCode.UNKNOWN_ERROR, result.errorCode)
    }
    
    // ==================== 重置测试 ====================
    
    @Test
    fun testReset() = runBlocking {
        // 初始化
        securityModule.initialize()
        
        // 处理挑战
        val challenge = AuthChallenge(
            type = "challenge",
            nonce = "550e8400-e29b-41d4-a716-446655440000",
            expiresAt = System.currentTimeMillis() + 300000,
            protocolVersion = "3.0.0"
        )
        authHandler.handleChallenge(challenge)
        
        // 重置
        authHandler.reset()
        
        // 验证重置后无法构建响应
        try {
            authHandler.buildAuthResponse()
            fail("重置后应该抛出 IllegalStateException")
        } catch (e: IllegalStateException) {
            // 预期行为
        }
    }
    
    // ==================== 签名验证测试 ====================
    
    @Test
    fun testSignatureFormat() = runBlocking {
        // 初始化
        securityModule.initialize()
        
        // 处理挑战
        val challenge = AuthChallenge(
            type = "challenge",
            nonce = "550e8400-e29b-41d4-a716-446655440000",
            expiresAt = System.currentTimeMillis() + 300000,
            protocolVersion = "3.0.0"
        )
        authHandler.handleChallenge(challenge)
        
        // 构建响应
        val authResponse = authHandler.buildAuthResponse()
        
        // 验证签名格式（应该是 Base64）
        val signature = authResponse.signature
        assertNotNull("签名不应为空", signature)
        
        // 验证 Base64 格式
        try {
            android.util.Base64.decode(signature, android.util.Base64.DEFAULT)
            // 解码成功，说明是有效的 Base64
        } catch (e: Exception) {
            fail("签名应该是有效的 Base64 格式")
        }
    }
}
