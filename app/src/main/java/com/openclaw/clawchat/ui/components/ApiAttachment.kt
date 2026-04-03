package com.openclaw.clawchat.ui.components

/**
 * API 附件数据结构
 * 参考 webchat chat.ts sendChatMessage
 */
data class ApiAttachment(
    val type: String,      // "url" | "base64"
    val mimeType: String,  // MIME 类型
    val content: String    // URL 或 base64 数据
)