package com.openclaw.clawchat.ui.state

import org.junit.Assert.*
import org.junit.Test

/**
 * UiState data classes tests
 */
class UiStateTest {

    // ─────────────────────────────────────────────────────────────
    // SessionUi Tests
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `SessionUi creates with required fields`() {
        val session = SessionUi(
            id = "session-1",
            label = null,
            model = null,
            status = SessionStatus.RUNNING,
            lastActivityAt = 1000L
        )

        assertEquals("session-1", session.id)
        assertNull(session.label)
        assertNull(session.model)
        assertNull(session.agentId)
        assertNull(session.agentName)
        assertNull(session.agentEmoji)
        assertEquals(SessionStatus.RUNNING, session.status)
        assertEquals(1000L, session.lastActivityAt)
        assertEquals(0, session.messageCount)
        assertNull(session.lastMessage)
        assertFalse(session.thinking)
        assertFalse(session.isPinned)
        assertFalse(session.isArchived)
    }

    @Test
    fun `SessionUi creates with all fields`() {
        val session = SessionUi(
            id = "session-2",
            label = "My Session",
            model = "claude-3",
            agentId = "agent:coder",
            agentName = "Coder Agent",
            agentEmoji = "🤖",
            status = SessionStatus.PAUSED,
            lastActivityAt = 2000L,
            messageCount = 10,
            lastMessage = "Hello",
            thinking = true,
            isPinned = true,
            isArchived = true
        )

        assertEquals("session-2", session.id)
        assertEquals("My Session", session.label)
        assertEquals("claude-3", session.model)
        assertEquals("agent:coder", session.agentId)
        assertEquals("Coder Agent", session.agentName)
        assertEquals("🤖", session.agentEmoji)
        assertEquals(SessionStatus.PAUSED, session.status)
        assertEquals(2000L, session.lastActivityAt)
        assertEquals(10, session.messageCount)
        assertEquals("Hello", session.lastMessage)
        assertTrue(session.thinking)
        assertTrue(session.isPinned)
        assertTrue(session.isArchived)
    }

    @Test
    fun `SessionUi getDisplayName returns agentName when present`() {
        val session = SessionUi(
            id = "s",
            label = "Label",
            model = "model",
            agentId = "agent:123",
            agentName = "My Agent",
            status = SessionStatus.RUNNING,
            lastActivityAt = 0
        )

        assertEquals("My Agent", session.getDisplayName())
    }

    @Test
    fun `SessionUi getDisplayName returns agentId prefix when agentName is null`() {
        val session = SessionUi(
            id = "s",
            label = "Label",
            model = "model",
            agentId = "agent:coder:123",
            status = SessionStatus.RUNNING,
            lastActivityAt = 0
        )

        assertEquals("coder", session.getDisplayName())
    }

    @Test
    fun `SessionUi getDisplayName returns label when no agent`() {
        val session = SessionUi(
            id = "s",
            label = "My Label",
            model = "model",
            status = SessionStatus.RUNNING,
            lastActivityAt = 0
        )

        assertEquals("My Label", session.getDisplayName())
    }

    @Test
    fun `SessionUi getDisplayName returns model when no label`() {
        val session = SessionUi(
            id = "s",
            label = null,
            model = "claude-3-opus",
            status = SessionStatus.RUNNING,
            lastActivityAt = 0
        )

        assertEquals("claude-3-opus", session.getDisplayName())
    }

    @Test
    fun `SessionUi getDisplayName returns default when all null`() {
        val session = SessionUi(
            id = "s",
            label = null,
            model = null,
            status = SessionStatus.RUNNING,
            lastActivityAt = 0
        )

        assertEquals("Unnamed session", session.getDisplayName())
    }

    @Test
    fun `SessionUi equality works`() {
        val session1 = SessionUi(
            id = "1", label = "Test", model = "claude",
            status = SessionStatus.RUNNING, lastActivityAt = 0
        )
        val session2 = SessionUi(
            id = "1", label = "Test", model = "claude",
            status = SessionStatus.RUNNING, lastActivityAt = 0
        )
        val session3 = SessionUi(
            id = "2", label = "Test", model = "claude",
            status = SessionStatus.RUNNING, lastActivityAt = 0
        )

        assertEquals(session1, session2)
        assertNotEquals(session1, session3)
    }

    // ─────────────────────────────────────────────────────────────
    // SessionStatus Tests
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `SessionStatus enum values`() {
        assertEquals(3, SessionStatus.entries.size)
        assertEquals(SessionStatus.RUNNING, SessionStatus.valueOf("RUNNING"))
        assertEquals(SessionStatus.PAUSED, SessionStatus.valueOf("PAUSED"))
        assertEquals(SessionStatus.TERMINATED, SessionStatus.valueOf("TERMINATED"))
    }

    // ─────────────────────────────────────────────────────────────
    // GatewayConfigUi Tests
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `GatewayConfigUi creates with required fields`() {
        val config = GatewayConfigUi(
            id = "gw-1",
            name = "Local Gateway",
            host = "localhost",
            port = 8080
        )

        assertEquals("gw-1", config.id)
        assertEquals("Local Gateway", config.name)
        assertEquals("localhost", config.host)
        assertEquals(8080, config.port)
        assertFalse(config.isCurrent)
    }

    @Test
    fun `GatewayConfigUi creates with isCurrent`() {
        val config = GatewayConfigUi(
            id = "gw-2",
            name = "Remote Gateway",
            host = "192.168.1.1",
            port = 443,
            isCurrent = true
        )

        assertTrue(config.isCurrent)
    }

    @Test
    fun `GatewayConfigUi copy preserves values`() {
        val original = GatewayConfigUi(
            id = "gw-3",
            name = "Original",
            host = "localhost",
            port = 8080
        )

        val copied = original.copy(port = 9000)

        assertEquals("gw-3", copied.id)
        assertEquals("Original", copied.name)
        assertEquals("localhost", copied.host)
        assertEquals(9000, copied.port)
    }

    // ─────────────────────────────────────────────────────────────
    // MainUiState Tests
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `MainUiState creates with default values`() {
        val state = MainUiState()

        assertEquals(ConnectionStatus.Disconnected, state.connectionStatus)
        assertTrue(state.sessions.isEmpty())
        assertNull(state.currentSession)
        assertTrue(state.gatewayConfigs.isEmpty())
        assertNull(state.currentGateway)
        assertFalse(state.isLoading)
        assertNull(state.error)
        assertNull(state.latency)
        assertNull(state.connectionError)
        assertTrue(state.agents.isEmpty())
        assertTrue(state.models.isEmpty())
        assertFalse(state.isLoadingAgentsModels)
        assertFalse(state.showCreateDialog)
    }

    @Test
    fun `MainUiState creates with custom values`() {
        val session = SessionUi(
            id = "s1", label = "Test", model = "claude",
            status = SessionStatus.RUNNING, lastActivityAt = 0
        )
        val state = MainUiState(
            connectionStatus = ConnectionStatus.Connected(),
            sessions = listOf(session),
            currentSession = session,
            isLoading = true,
            latency = 100L
        )

        assertTrue(state.connectionStatus.isConnected)
        assertEquals(1, state.sessions.size)
        assertEquals(session, state.currentSession)
        assertTrue(state.isLoading)
        assertEquals(100L, state.latency)
    }

    // ─────────────────────────────────────────────────────────────
    // AttachmentUi Tests
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `AttachmentUi creates with required fields`() {
        val uri = android.net.Uri.parse("content://test/file.pdf")
        val attachment = AttachmentUi(
            id = "att-1",
            uri = uri,
            mimeType = "application/pdf"
        )

        assertEquals("att-1", attachment.id)
        assertEquals(uri, attachment.uri)
        assertEquals("application/pdf", attachment.mimeType)
        assertNull(attachment.fileName)
        assertNull(attachment.dataUrl)
        assertFalse(attachment.isLoading)
        assertNull(attachment.error)
    }

    @Test
    fun `AttachmentUi creates with all fields`() {
        val uri = android.net.Uri.parse("content://test/image.png")
        val attachment = AttachmentUi(
            id = "att-2",
            uri = uri,
            mimeType = "image/png",
            fileName = "image.png",
            dataUrl = "data:image/png;base64,ABC123",
            isLoading = true,
            error = "Upload failed"
        )

        assertEquals("att-2", attachment.id)
        assertEquals("image.png", attachment.fileName)
        assertEquals("data:image/png;base64,ABC123", attachment.dataUrl)
        assertTrue(attachment.isLoading)
        assertEquals("Upload failed", attachment.error)
    }

    // ─────────────────────────────────────────────────────────────
    // SlashCommandCompletion Tests
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `SlashCommandCompletion creates with default values`() {
        val completion = SlashCommandCompletion()

        assertTrue(completion.commands.isEmpty())
        assertEquals(0, completion.selectedIndex)
        assertFalse(completion.visible)
    }

    @Test
    fun `SlashCommandCompletion creates with custom values`() {
        val completion = SlashCommandCompletion(
            commands = com.openclaw.clawchat.ui.components.SLASH_COMMANDS.take(3),
            selectedIndex = 1,
            visible = true
        )

        assertEquals(3, completion.commands.size)
        assertEquals(1, completion.selectedIndex)
        assertTrue(completion.visible)
    }

    // ─────────────────────────────────────────────────────────────
    // ChatQueueItem Tests
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `ChatQueueItem creates with required fields`() {
        val item = ChatQueueItem(
            id = "q-1",
            text = "Hello",
            timestamp = 1000L
        )

        assertEquals("q-1", item.id)
        assertEquals("Hello", item.text)
        assertEquals(1000L, item.timestamp)
        assertTrue(item.attachments.isEmpty())
    }

    @Test
    fun `ChatQueueItem creates with attachments`() {
        val uri = android.net.Uri.parse("content://test/file.pdf")
        val attachment = AttachmentUi(
            id = "att-1",
            uri = uri,
            mimeType = "application/pdf"
        )
        val item = ChatQueueItem(
            id = "q-2",
            text = "Message with attachment",
            timestamp = 2000L,
            attachments = listOf(attachment)
        )

        assertEquals(1, item.attachments.size)
        assertEquals(attachment, item.attachments[0])
    }

    // ─────────────────────────────────────────────────────────────
    // SessionUiState Tests
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `SessionUiState creates with default values`() {
        val state = SessionUiState()

        assertEquals(ConnectionStatus.Disconnected, state.connectionStatus)
        assertFalse(state.isLoading)
        assertFalse(state.isSending)
        assertNull(state.error)
        assertNull(state.sessionId)
        assertNull(state.session)
        assertTrue(state.chatMessages.isEmpty())
        assertNull(state.chatStream)
        assertTrue(state.chatStreamSegments.isEmpty())
        assertTrue(state.chatQueue.isEmpty())
        assertFalse(state.chatNewMessagesBelow)
        assertEquals(0, state.unreadMessageCount)
        assertTrue(state.chatUserNearBottom)
        assertFalse(state.chatHasAutoScrolled)
        assertNull(state.totalTokens)
        assertNull(state.contextTokensLimit)
        assertTrue(state.totalTokensFresh)
        assertFalse(state.compactionActive)
        assertNull(state.compactionCompletedAt)
        assertTrue(state.toolStreamById.isEmpty())
        assertTrue(state.toolStreamOrder.isEmpty())
        assertTrue(state.chatToolMessages.isEmpty())
        assertTrue(state.attachments.isEmpty())
        assertFalse(state.isUploadingAttachment)
        assertFalse(state.slashCommandCompletion.visible)
        assertEquals("", state.inputText)
        assertNull(state.editingMessageId)
        assertNull(state.editingMessageText)
        assertTrue(state.models.isEmpty())
        assertFalse(state.isLoadingModels)
    }

    @Test
    fun `SessionUiState clearSession preserves connectionStatus and sessionId`() {
        val state = SessionUiState(
            connectionStatus = ConnectionStatus.Connected(),
            sessionId = "session-123",
            chatMessages = listOf(
                MessageUi(
                    id = "msg-1",
                    content = listOf(MessageContentItem.Text("Hello")),
                    role = MessageRole.USER,
                    timestamp = 0
                )
            )
        )

        val cleared = state.clearSession()

        assertTrue(cleared.connectionStatus.isConnected)
        assertEquals("session-123", cleared.sessionId)
        assertTrue(cleared.chatMessages.isEmpty())
    }

    // ─────────────────────────────────────────────────────────────
    // StreamSegment Tests (additional from ToolCardTest)
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `StreamSegment creates correctly`() {
        val segment = StreamSegment(text = "Hello world", ts = System.currentTimeMillis())

        assertEquals("Hello world", segment.text)
        assertTrue(segment.ts > 0)
    }

    @Test
    fun `StreamSegment copy works`() {
        val original = StreamSegment(text = "Original", ts = 1000L)
        val copied = original.copy(text = "Copied")

        assertEquals("Copied", copied.text)
        assertEquals(1000L, copied.ts)
    }
}