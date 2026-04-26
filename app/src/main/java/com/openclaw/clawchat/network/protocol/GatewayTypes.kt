package com.openclaw.clawchat.network.protocol

/**
 * Gateway 连接相关类型定义
 *
 * 从 GatewayConnection.kt 提取的数据类：
 * - CertificateEvent - 证书事件（TOFU 流程）
 * - ChatAttachmentData - 附件数据（chat.send）
 * - ToolStreamEvent - 工具流事件（agent.event）
 * - PlanStreamEvent - 计划流事件（agent.event, v2026.4.24+）
 * - ItemStreamEvent - 项目流事件（agent.event, v2026.4.24+）
 * - PatchStreamEvent - 补丁流事件（agent.event, v2026.4.24+）
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

/**
 * Plan stream event (for agent.event with stream: "plan")
 * OpenClaw v2026.4.24+: shows plan steps and progress during multi-step work
 */
data class PlanStreamEvent(
    val phase: String,        // "update", "result"
    val title: String,        // "Assistant proposed a plan"
    val explanation: String? = null,
    val steps: List<String>? = null,  // plan steps list
    val source: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Item stream event (for agent.event with stream: "item")
 * OpenClaw v2026.4.24+: represents work items, tasks, or checkpoints
 */
data class ItemStreamEvent(
    val itemId: String,
    val kind: String? = null,
    val title: String? = null,
    val name: String? = null,
    val phase: String? = null,
    val status: String? = null,
    val summary: String? = null,
    val progressText: String? = null,
    val approvalId: String? = null,
    val approvalSlug: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Patch stream event (for agent.event with stream: "patch")
 * OpenClaw v2026.4.24+: shows changes to context/session state
 */
data class PatchStreamEvent(
    val itemId: String,
    val phase: String? = null,
    val title: String? = null,
    val toolCallId: String? = null,
    val name: String? = null,
    val added: List<String>? = null,
    val modified: List<String>? = null,
    val deleted: List<String>? = null,
    val summary: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)