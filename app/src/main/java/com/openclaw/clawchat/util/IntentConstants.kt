package com.openclaw.clawchat.util

/**
 * Intent 常量
 */
object IntentConstants {
    
    // ─────────────────────────────────────────────────────────────
    // Action
    // ─────────────────────────────────────────────────────────────
    
    object Action {
        const val SESSION_CREATED = "com.openclaw.clawchat.SESSION_CREATED"
        const val SESSION_TERMINATED = "com.openclaw.clawchat.SESSION_TERMINATED"
        const val MESSAGE_RECEIVED = "com.openclaw.clawchat.MESSAGE_RECEIVED"
        const val CONNECTION_STATUS_CHANGED = "com.openclaw.clawchat.CONNECTION_STATUS_CHANGED"
        const val PAIRING_STATUS_CHANGED = "com.openclaw.clawchat.PAIRING_STATUS_CHANGED"
    }
    
    // ─────────────────────────────────────────────────────────────
    // Extra
    // ─────────────────────────────────────────────────────────────
    
    object Extra {
        const val SESSION_ID = "session_id"
        const val SESSION_TITLE = "session_title"
        const val MESSAGE_ID = "message_id"
        const val MESSAGE_CONTENT = "message_content"
        const val GATEWAY_ID = "gateway_id"
        const val CONNECTION_STATUS = "connection_status"
        const val PAIRING_STATUS = "pairing_status"
        const val ERROR_MESSAGE = "error_message"
    }
    
    // ─────────────────────────────────────────────────────────────
    // Request Codes
    // ─────────────────────────────────────────────────────────────
    
    object RequestCode {
        const val IMAGE_PICK = 1001
        const val PERMISSION_REQUEST = 1002
        const val NOTIFICATION_PERMISSION = 1003
    }
    
    // ─────────────────────────────────────────────────────────────
    // Result Codes
    // ─────────────────────────────────────────────────────────────
    
    object ResultCode {
        const val SUCCESS = 1
        const val CANCELLED = 0
        const val ERROR = -1
    }
    
    // ─────────────────────────────────────────────────────────────
    // Mime Types
    // ─────────────────────────────────────────────────────────────
    
    object MimeType {
        const val TEXT_PLAIN = "text/plain"
        const val IMAGE_ALL = "image/*"
        const val IMAGE_JPEG = "image/jpeg"
        const val IMAGE_PNG = "image/png"
        const val IMAGE_WEBP = "image/webp"
        const val APPLICATION_JSON = "application/json"
    }
    
    // ─────────────────────────────────────────────────────────────
    // Scheme
    // ─────────────────────────────────────────────────────────────
    
    object Scheme {
        const val HTTP = "http"
        const val HTTPS = "https"
        const val WS = "ws"
        const val WSS = "wss"
        const val CONTENT = "content"
        const val FILE = "file"
    }
}