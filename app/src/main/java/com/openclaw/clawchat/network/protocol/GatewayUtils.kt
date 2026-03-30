package com.openclaw.clawchat.network.protocol

import com.openclaw.clawchat.network.CertificateExceptionFirstTime
import com.openclaw.clawchat.network.CertificateExceptionMismatch
import java.security.cert.CertificateException

/**
 * Gateway 连接工具函数
 */

/**
 * 从 WebSocket URL 提取 origin
 * 例如: ws://192.168.0.213:18789/ws -> http://192.168.0.213:18789
 *       wss://example.com/ws -> https://example.com
 */
fun extractOrigin(wsUrl: String): String? {
    return try {
        val uri = java.net.URI(wsUrl)
        val scheme = when (uri.scheme) {
            "wss" -> "https"
            "ws" -> "http"
            else -> return null
        }
        val port = uri.port
        val host = uri.host ?: return null
        
        if (port > 0 && port != when (uri.scheme) {
            "wss" -> 443
            "ws" -> 80
            else -> port
        }) {
            "$scheme://$host:$port"
        } else {
            "$scheme://$host"
        }
    } catch (e: Exception) {
        null
    }
}

/**
 * 递归查找证书异常
 */
fun findCertificateException(t: Throwable): CertificateException? {
    var current: Throwable? = t
    while (current != null) {
        if (current is CertificateExceptionFirstTime || current is CertificateExceptionMismatch) {
            return current as CertificateException
        }
        current = current.cause
    }
    return null
}