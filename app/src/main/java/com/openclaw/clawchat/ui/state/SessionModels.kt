package com.openclaw.clawchat.ui.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable

/**
 * 会话数据模型 - 对齐 OpenClaw v2026.4.29 GatewaySessionRow (31 fields)
 *
 * Required fields:
 * - key: string (唯一标识)
 * - kind: "direct" | "group" | "global" | "unknown"
 * - updatedAt: number | null
 *
 * Optional fields (部分):
 * - label, displayName, surface, subject, room, space
 * - sessionId, systemSent, abortedLastRun
 * - thinkingLevel, thinkingLevels, thinkingOptions, thinkingDefault
 * - fastMode, verboseLevel, reasoningLevel, elevatedLevel
 * - inputTokens, outputTokens, totalTokens, totalTokensFresh
 * - status, subagentRunState, hasActiveSubagentRun
 * - startedAt, endedAt, runtimeMs
 * - childSessions, model, modelProvider
 * - agentRuntime, contextTokens
 * - compactionCheckpointCount, latestCompactionCheckpoint
 */
@Stable
data class SessionUi(
    val key: String,
    val kind: String = "unknown",
    val label: String? = null,
    val displayName: String? = null,
    val surface: String? = null,
    val subject: String? = null,
    val room: String? = null,
    val space: String? = null,
    val updatedAt: Long? = null,
    val sessionId: String? = null,
    val systemSent: Boolean? = null,
    val abortedLastRun: Boolean? = null,
    val thinkingLevel: String? = null,
    val thinkingDefault: String? = null,
    val fastMode: Boolean? = null,
    val verboseLevel: String? = null,
    val reasoningLevel: String? = null,
    val elevatedLevel: String? = null,
    val inputTokens: Int? = null,
    val outputTokens: Int? = null,
    val totalTokens: Int? = null,
    val totalTokensFresh: Boolean? = null,
    val status: SessionStatus? = null,
    val startedAt: Long? = null,
    val endedAt: Long? = null,
    val runtimeMs: Long? = null,
    val childSessions: List<String>? = null,
    val model: String? = null,
    val modelProvider: String? = null,
    val contextTokens: Int? = null,
    val lastActivityAt: Long = System.currentTimeMillis(),
    val messageCount: Int = 0,
    val lastMessage: String? = null,
    val thinking: Boolean = false,
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    // Agent info (from agent runtime)
    val agentId: String? = null,
    val agentName: String? = null,
    val agentEmoji: String? = null
) {
    fun computeDisplayName(): String {
        return when {
            !displayName.isNullOrBlank() -> displayName
            !label.isNullOrBlank() -> label
            !subject.isNullOrBlank() -> subject
            !agentName.isNullOrBlank() -> agentName
            !model.isNullOrBlank() -> model
            else -> "Unnamed session"
        }
    }

    @Composable
    fun getDisplayNameLocalized(): String {
        return when {
            !displayName.isNullOrBlank() -> displayName
            !label.isNullOrBlank() -> label
            !subject.isNullOrBlank() -> subject
            !agentName.isNullOrBlank() -> agentName
            !model.isNullOrBlank() -> model
            else -> androidx.compose.ui.res.stringResource(com.openclaw.clawchat.R.string.session_unnamed)
        }
    }

    /**
     * OpenClaw v2026.4.29 显示名称优先级：
     * agentName > displayName > label > subject > model > "Unnamed session"
     */
    fun getOpenClawDisplayName(): String {
        return displayName
            ?: label
            ?: subject
            ?: model
            ?: "Unnamed session"
    }

    /**
     * 获取会话类型的本地化描述
     */
    fun getKindDescription(): String {
        return when (kind) {
            "direct" -> "Direct"
            "group" -> "Group"
            "global" -> "Global"
            else -> "Unknown"
        }
    }

    /**
     * 获取运行时长描述
     */
    fun getRuntimeDescription(): String? {
        val ms = runtimeMs ?: return null
        return when {
            ms < 60_000 -> "${ms / 1000}s"
            ms < 3_600_000 -> "${ms / 60_000}min"
            ms < 86_400_000 -> "${ms / 3_600_000}h"
            else -> "${ms / 86_400_000}d"
        }
    }
}

enum class SessionStatus {
    RUNNING,
    PAUSED,
    TERMINATED
}

@Stable
data class GatewayConfigUi(
    val id: String,
    val name: String,
    val host: String,
    val port: Int,
    val isCurrent: Boolean = false
)

data class GatewayConfigInput(
    val name: String = "",
    val host: String = "",
    val port: Int = 18789
)
