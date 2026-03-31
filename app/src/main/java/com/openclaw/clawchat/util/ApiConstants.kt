package com.openclaw.clawchat.util

/**
 * API 常量
 */
object ApiConstants {
    
    // ─────────────────────────────────────────────────────────────
    // 超时设置
    // ─────────────────────────────────────────────────────────────
    
    const val CONNECT_TIMEOUT_SECONDS = 30L
    const val READ_TIMEOUT_SECONDS = 30L
    const val WRITE_TIMEOUT_SECONDS = 30L
    const val HEARTBEAT_INTERVAL_SECONDS = 30L
    
    // ─────────────────────────────────────────────────────────────
    // WebSocket
    // ─────────────────────────────────────────────────────────────
    
    const val WS_RECONNECT_DELAY_MS = 1000L
    const val WS_MAX_RECONNECT_ATTEMPTS = 5
    const val WS_PING_INTERVAL_MS = 30_000L
    
    // ─────────────────────────────────────────────────────────────
    // Gateway 协议
    // ─────────────────────────────────────────────────────────────
    
    const val GATEWAY_PROTOCOL_VERSION = 3
    const val GATEWAY_PROTOCOL_PREFIX = "v3"
    
    // 消息类型
    object MessageType {
        const val MESSAGE = "message"
        const val COMMAND = "command"
        const val EVENT = "event"
        const val ERROR = "error"
        const val HEARTBEAT = "heartbeat"
    }
    
    // 事件类型
    object EventType {
        const val SESSION_CREATED = "session.created"
        const val SESSION_TERMINATED = "session.terminated"
        const val MESSAGE_RECEIVED = "message.received"
        const val MESSAGE_SENT = "message.sent"
        const val MESSAGE_STREAMING = "message.streaming"
        const val MESSAGE_COMPLETE = "message.complete"
        const val CONNECTION_READY = "connection.ready"
        const val CONNECTION_CLOSED = "connection.closed"
    }
    
    // 命令类型
    object CommandType {
        const val CREATE_SESSION = "session.create"
        const val TERMINATE_SESSION = "session.terminate"
        const val SEND_MESSAGE = "message.send"
        const val STEER_SESSION = "session.steer"
    }
    
    // ─────────────────────────────────────────────────────────────
    // HTTP Headers
    // ─────────────────────────────────────────────────────────────
    
    object Header {
        const val CONTENT_TYPE = "Content-Type"
        const val AUTHORIZATION = "Authorization"
        const val X_DEVICE_ID = "X-Device-Id"
        const val X_CLIENT_ID = "X-Client-Id"
        const val X_SIGNATURE = "X-Signature"
    }
    
    // ─────────────────────────────────────────────────────────────
    // Content Types
    // ─────────────────────────────────────────────────────────────
    
    object ContentType {
        const val JSON = "application/json"
        const val TEXT = "text/plain"
        const val IMAGE_JPEG = "image/jpeg"
        const val IMAGE_PNG = "image/png"
        const val IMAGE_WEBP = "image/webp"
    }
}