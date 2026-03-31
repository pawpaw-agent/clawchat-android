package com.openclaw.clawchat.network.protocol

/**
 * Gateway 连接相关类型定义
 * 
 * 从 GatewayConnection.kt 提取的数据类：
 * - CertificateEvent - 证书事件（TOFU 流程）
 * - ChatAttachmentData - 附件数据（chat.send）
 * - ToolStreamEvent - 工具流事件（agent.event）
 */

/**
 * 证书事件（用于 TOFU 流程）
 */
data class CertificateEvent(
    val hostname: String,
    val fingerprint: String,
    val isMismatch: Boolean,
    val storedFingerprint: String? = null
)

/**
 * 附件数据（用于 chat.send）
 */
data class ChatAttachmentData(
    val type: String = "image",
    val mimeType: String,
    val content: String   // base64 content (without data URL prefix)
)

/**
 * 工具流事件（用于 agent.event 处理）
 */
data class ToolStreamEvent(
    val toolCallId: String,
    val name: String,
    val status: String,
    val title: String? = null,
    val output: String? = null,
    val error: String? = null,
    val stream: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)