package com.openclaw.clawchat.network

import com.openclaw.clawchat.security.SecurityModule
import okhttp3.Interceptor
import okhttp3.Response
import java.util.UUID

/**
 * 签名拦截器
 * 
 * 为每个请求添加签名头：
 * - X-ClawChat-Timestamp: 当前时间戳
 * - X-ClawChat-Nonce: 随机数（防重放）
 * - X-ClawChat-Signature: 请求签名
 */
class SignatureInterceptor(
    private val securityModule: SecurityModule
) : Interceptor {
    
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val timestamp = System.currentTimeMillis()
        val nonce = generateNonce()
        
        // 构建签名字符串：路径 + 时间戳 + 随机数
        val dataToSign = "${request.url.encodedPath}\n$timestamp\n$nonce"
        // signChallenge(String) 返回 Base64 编码的签名字符串
        val signature = securityModule.signChallenge(dataToSign)
        
        // 添加签名头
        val signedRequest = request.newBuilder()
            .addHeader("X-ClawChat-Timestamp", timestamp.toString())
            .addHeader("X-ClawChat-Nonce", nonce)
            .addHeader("X-ClawChat-Signature", signature)
            .build()
        
        return chain.proceed(signedRequest)
    }
    
    /**
     * 生成随机 Nonce
     */
    private fun generateNonce(): String {
        return UUID.randomUUID().toString()
    }
}
