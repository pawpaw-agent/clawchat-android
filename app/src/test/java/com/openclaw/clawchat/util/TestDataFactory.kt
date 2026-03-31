package com.openclaw.clawchat.util

import com.openclaw.clawchat.ui.state.*
import java.util.UUID

/**
 * 测试数据工厂
 * 提供一致的测试数据生成
 */
object TestDataFactory {
    
    // ─────────────────────────────────────────────────────────────
    // 会话数据
    // ─────────────────────────────────────────────────────────────
    
    fun createTestSession(
        id: String = UUID.randomUUID().toString(),
        title: String = "Test Session",
        timestamp: Long = System.currentTimeMillis(),
        isPinned: Boolean = false,
        isArchived: Boolean = false
    ): SessionUi {
        return SessionUi(
            id = id,
            title = title,
            timestamp = timestamp,
            isPinned = isPinned,
            isArchived = isArchived
        )
    }
    
    fun createTestSessions(count: Int): List<SessionUi> {
        return (1..count).map { index ->
            createTestSession(
                id = "session-$index",
                title = "Session $index",
                timestamp = System.currentTimeMillis() - index * 60000
            )
        }
    }
    
    // ─────────────────────────────────────────────────────────────
    // 消息数据
    // ─────────────────────────────────────────────────────────────
    
    fun createTestMessage(
        id: String = UUID.randomUUID().toString(),
        content: String = "Test message content",
        role: MessageRole = MessageRole.USER,
        timestamp: Long = System.currentTimeMillis()
    ): MessageUi {
        return MessageUi(
            id = id,
            role = role,
            timestamp = timestamp,
            content = listOf(MessageContentItem.Text(content))
        )
    }
    
    fun createTestMessages(count: Int, role: MessageRole = MessageRole.USER): List<MessageUi> {
        return (1..count).map { index ->
            createTestMessage(
                id = "message-$index",
                content = "Message $index",
                role = if (index % 2 == 0) MessageRole.USER else MessageRole.ASSISTANT,
                timestamp = System.currentTimeMillis() - index * 1000
            )
        }
    }
    
    // ─────────────────────────────────────────────────────────────
    // Gateway 数据
    // ─────────────────────────────────────────────────────────────
    
    fun createTestGateway(
        id: String = UUID.randomUUID().toString(),
        name: String = "Test Gateway",
        host: String = "192.168.1.1",
        port: Int = 18789
    ): GatewayConfigUi {
        return GatewayConfigUi(
            id = id,
            name = name,
            host = host,
            port = port
        )
    }
    
    fun createTestGateways(count: Int): List<GatewayConfigUi> {
        return (1..count).map { index ->
            createTestGateway(
                id = "gateway-$index",
                name = "Gateway $index",
                host = "192.168.1.$index"
            )
        }
    }
    
    // ─────────────────────────────────────────────────────────────
    // UI State 数据
    // ─────────────────────────────────────────────────────────────
    
    fun createTestMainUiState(
        sessions: List<SessionUi> = emptyList(),
        isLoading: Boolean = false,
        error: String? = null
    ): MainUiState {
        return MainUiState(
            sessions = sessions,
            isLoading = isLoading,
            error = error
        )
    }
    
    fun createTestSessionUiState(
        messages: List<MessageUi> = emptyList(),
        isLoading: Boolean = false,
        isSending: Boolean = false,
        error: String? = null
    ): SessionUiState {
        return SessionUiState(
            chatMessages = messages,
            isLoading = isLoading,
            isSending = isSending,
            error = error
        )
    }
    
    // ─────────────────────────────────────────────────────────────
    // 附件数据
    // ─────────────────────────────────────────────────────────────
    
    fun createTestAttachment(
        id: String = UUID.randomUUID().toString(),
        fileName: String = "test.jpg",
        mimeType: String = "image/jpeg"
    ): AttachmentUi {
        return AttachmentUi(
            id = id,
            fileName = fileName,
            mimeType = mimeType
        )
    }
    
    fun createTestAttachments(count: Int): List<AttachmentUi> {
        return (1..count).map { index ->
            createTestAttachment(
                id = "attachment-$index",
                fileName = "image$index.jpg"
            )
        }
    }
    
    // ─────────────────────────────────────────────────────────────
    // 边界数据
    // ─────────────────────────────────────────────────────────────
    
    fun createEmptySession(): SessionUi {
        return createTestSession(title = "")
    }
    
    fun createLongTitleSession(): SessionUi {
        return createTestSession(title = "A".repeat(1000))
    }
    
    fun createSpecialCharsSession(): SessionUi {
        return createTestSession(title = "Test <>&\"'特殊字符")
    }
    
    fun createEmptyMessage(): MessageUi {
        return createTestMessage(content = "")
    }
    
    fun createLongMessage(): MessageUi {
        return createTestMessage(content = "A".repeat(10000))
    }
    
    fun createSpecialCharsMessage(): MessageUi {
        return createTestMessage(content = "Test <>&\"'特殊字符\n换行\t制表符")
    }
}