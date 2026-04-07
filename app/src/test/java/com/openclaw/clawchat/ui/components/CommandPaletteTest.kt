package com.openclaw.clawchat.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Terminal
import org.junit.Assert.*
import org.junit.Test

/**
 * CommandPaletteItem and related data class tests
 */
class CommandPaletteTest {

    // ─────────────────────────────────────────────────────────────
    // CommandPaletteItem.SessionItem Tests
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `SessionItem creates with required fields`() {
        val session = CommandPaletteItem.SessionItem(
            id = "session-1",
            title = "Test Session"
        )

        assertEquals("session-1", session.id)
        assertEquals("Test Session", session.title)
        assertEquals(Icons.Default.Chat, session.icon)
        assertNull(session.lastMessage)
        assertNull(session.timestamp)
    }

    @Test
    fun `SessionItem creates with all fields`() {
        val session = CommandPaletteItem.SessionItem(
            id = "session-2",
            title = "Full Session",
            lastMessage = "Last message preview",
            timestamp = 1234567890L
        )

        assertEquals("session-2", session.id)
        assertEquals("Full Session", session.title)
        assertEquals("Last message preview", session.lastMessage)
        assertEquals(1234567890L, session.timestamp)
    }

    @Test
    fun `SessionItem copy preserves values`() {
        val original = CommandPaletteItem.SessionItem(
            id = "session-3",
            title = "Original",
            lastMessage = "Message 1"
        )

        val copied = original.copy(lastMessage = "Message 2")

        assertEquals("session-3", copied.id)
        assertEquals("Original", copied.title)
        assertEquals("Message 2", copied.lastMessage)
    }

    @Test
    fun `SessionItem equality works`() {
        val session1 = CommandPaletteItem.SessionItem(id = "1", title = "Test")
        val session2 = CommandPaletteItem.SessionItem(id = "1", title = "Test")
        val session3 = CommandPaletteItem.SessionItem(id = "2", title = "Test")

        assertEquals(session1, session2)
        assertNotEquals(session1, session3)
    }

    // ─────────────────────────────────────────────────────────────
    // CommandPaletteItem.CommandItem Tests
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `CommandItem creates with required fields`() {
        val command = CommandPaletteItem.CommandItem(
            id = "cmd-1",
            title = "Test Command"
        )

        assertEquals("cmd-1", command.id)
        assertEquals("Test Command", command.title)
        assertEquals(Icons.Default.Terminal, command.icon)
        assertNull(command.description)
    }

    @Test
    fun `CommandItem creates with all fields`() {
        val command = CommandPaletteItem.CommandItem(
            id = "cmd-2",
            title = "Full Command",
            description = "A detailed description"
        )

        assertEquals("cmd-2", command.id)
        assertEquals("Full Command", command.title)
        assertEquals("A detailed description", command.description)
    }

    @Test
    fun `CommandItem copy preserves values`() {
        val original = CommandPaletteItem.CommandItem(
            id = "cmd-3",
            title = "Original",
            description = "Desc 1"
        )

        val copied = original.copy(description = "Desc 2")

        assertEquals("cmd-3", copied.id)
        assertEquals("Original", copied.title)
        assertEquals("Desc 2", copied.description)
    }

    @Test
    fun `CommandItem equality works`() {
        val cmd1 = CommandPaletteItem.CommandItem(id = "1", title = "Test")
        val cmd2 = CommandPaletteItem.CommandItem(id = "1", title = "Test")
        val cmd3 = CommandPaletteItem.CommandItem(id = "2", title = "Test")

        assertEquals(cmd1, cmd2)
        assertNotEquals(cmd1, cmd3)
    }

    // ─────────────────────────────────────────────────────────────
    // CommandPaletteItem Sealed Class Tests
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `CommandPaletteItem sealed class has correct subclasses`() {
        val session: CommandPaletteItem = CommandPaletteItem.SessionItem(id = "s", title = "Session")
        val command: CommandPaletteItem = CommandPaletteItem.CommandItem(id = "c", title = "Command")

        assertTrue(session is CommandPaletteItem.SessionItem)
        assertTrue(command is CommandPaletteItem.CommandItem)
    }

    @Test
    fun `CommandPaletteItem id and title are accessible`() {
        val items: List<CommandPaletteItem> = listOf(
            CommandPaletteItem.SessionItem(id = "1", title = "Session 1"),
            CommandPaletteItem.CommandItem(id = "2", title = "Command 1")
        )

        assertEquals("1", items[0].id)
        assertEquals("Session 1", items[0].title)
        assertEquals("2", items[1].id)
        assertEquals("Command 1", items[1].title)
    }

    @Test
    fun `CommandPaletteItem icon is accessible`() {
        val session = CommandPaletteItem.SessionItem(id = "s", title = "Session")
        val command = CommandPaletteItem.CommandItem(id = "c", title = "Command")

        assertNotNull(session.icon)
        assertNotNull(command.icon)
    }

    // ─────────────────────────────────────────────────────────────
    // Type-specific properties Tests
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `SessionItem has timestamp and lastMessage`() {
        val session = CommandPaletteItem.SessionItem(
            id = "s",
            title = "Session",
            lastMessage = "Hello",
            timestamp = 1000L
        )

        // Access type-specific properties via cast
        val typedSession = session as CommandPaletteItem.SessionItem
        assertEquals("Hello", typedSession.lastMessage)
        assertEquals(1000L, typedSession.timestamp)
    }

    @Test
    fun `CommandItem has description`() {
        val command = CommandPaletteItem.CommandItem(
            id = "c",
            title = "Command",
            description = "Description"
        )

        // Access type-specific properties via cast
        val typedCommand = command as CommandPaletteItem.CommandItem
        assertEquals("Description", typedCommand.description)
    }
}