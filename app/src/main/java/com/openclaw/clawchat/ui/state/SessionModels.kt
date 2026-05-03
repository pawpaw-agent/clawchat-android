package com.openclaw.clawchat.ui.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable

/**
 * 会话数据模型
 */
@Stable
data class SessionUi(
    val id: String,
    val label: String?,
    val model: String?,
    val agentId: String? = null,
    val agentName: String? = null,
    val agentEmoji: String? = null,
    val status: SessionStatus,
    val lastActivityAt: Long,
    val messageCount: Int = 0,
    val lastMessage: String? = null,
    val thinking: Boolean = false,
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val totalTokens: Int? = null,
    val contextTokens: Int? = null,
    val totalTokensFresh: Boolean = true
) {
    fun getDisplayName(): String {
        val baseName = when {
            !agentName.isNullOrBlank() -> agentName
            !agentId.isNullOrBlank() -> agentId.removePrefix("agent:").substringBefore(":")
            !label.isNullOrBlank() -> label
            !model.isNullOrBlank() -> model
            else -> "Session"
        }
        return if (baseName == "Session" || baseName == "Unnamed session") {
            "$baseName #${id.takeLast(4).uppercase()}"
        } else {
            baseName
        }
    }

    @Composable
    fun getDisplayNameLocalized(): String {
        return when {
            !agentName.isNullOrBlank() -> agentName
            !agentId.isNullOrBlank() -> agentId.removePrefix("agent:").substringBefore(":")
            !label.isNullOrBlank() -> label
            !model.isNullOrBlank() -> model
            else -> androidx.compose.ui.res.stringResource(com.openclaw.clawchat.R.string.session_unnamed)
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
