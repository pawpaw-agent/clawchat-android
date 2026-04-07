package com.openclaw.clawchat.ui.components

import org.junit.Assert.*
import org.junit.Test

/**
 * SlashCommands 单元测试
 */
class SlashCommandsTest {

    // ─────────────────────────────────────────────────────────────
    // SlashMenuState Tests
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `SlashMenuState creates with default values`() {
        val state = SlashMenuState()

        assertFalse(state.isOpen)
        assertEquals("command", state.mode)
        assertTrue(state.items.isEmpty())
        assertTrue(state.argItems.isEmpty())
        assertEquals(0, state.selectedIndex)
        assertNull(state.command)
    }

    @Test
    fun `SlashMenuState creates with custom values`() {
        val cmd = SLASH_COMMANDS.first()
        val state = SlashMenuState(
            isOpen = true,
            mode = "args",
            items = SLASH_COMMANDS,
            argItems = listOf("on", "off"),
            selectedIndex = 2,
            command = cmd
        )

        assertTrue(state.isOpen)
        assertEquals("args", state.mode)
        assertEquals(SLASH_COMMANDS.size, state.items.size)
        assertEquals(2, state.argItems.size)
        assertEquals(2, state.selectedIndex)
        assertEquals(cmd, state.command)
    }

    // ─────────────────────────────────────────────────────────────
    // SlashCommandDef Tests
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `SlashCommandDef has correct structure`() {
        val cmd = SlashCommandDef(
            name = "test",
            description = "Test command",
            args = "<param>",
            icon = "star",
            category = SlashCommandCategory.TOOLS,
            executeLocal = true,
            argOptions = listOf("a", "b"),
            shortcut = "Ctrl+T"
        )

        assertEquals("test", cmd.name)
        assertEquals("Test command", cmd.description)
        assertEquals("<param>", cmd.args)
        assertEquals("star", cmd.icon)
        assertEquals(SlashCommandCategory.TOOLS, cmd.category)
        assertTrue(cmd.executeLocal)
        assertEquals(2, cmd.argOptions!!.size)
        assertEquals("Ctrl+T", cmd.shortcut)
    }

    @Test
    fun `SlashCommandDef equality works`() {
        val cmd1 = SlashCommandDef(name = "test", description = "Test", category = SlashCommandCategory.SESSION)
        val cmd2 = SlashCommandDef(name = "test", description = "Test", category = SlashCommandCategory.SESSION)
        val cmd3 = SlashCommandDef(name = "other", description = "Test", category = SlashCommandCategory.SESSION)

        assertEquals(cmd1, cmd2)
        assertNotEquals(cmd1, cmd3)
    }

    // ─────────────────────────────────────────────────────────────
    // SlashCommandCategory Tests
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `SlashCommandCategory enum values`() {
        assertEquals(4, SlashCommandCategory.entries.size)
        assertEquals(SlashCommandCategory.SESSION, SlashCommandCategory.valueOf("SESSION"))
        assertEquals(SlashCommandCategory.MODEL, SlashCommandCategory.valueOf("MODEL"))
        assertEquals(SlashCommandCategory.TOOLS, SlashCommandCategory.valueOf("TOOLS"))
        assertEquals(SlashCommandCategory.AGENTS, SlashCommandCategory.valueOf("AGENTS"))
    }

    // ─────────────────────────────────────────────────────────────
    // ParsedSlashCommand Tests
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `ParsedSlashCommand creates correctly`() {
        val cmd = SLASH_COMMANDS.first()
        val parsed = ParsedSlashCommand(command = cmd, args = "arg1 arg2")

        assertEquals(cmd, parsed.command)
        assertEquals("arg1 arg2", parsed.args)
    }

    // ─────────────────────────────────────────────────────────────
    // SLASH_COMMANDS List Tests
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `SLASH_COMMANDS has expected commands`() {
        val names = SLASH_COMMANDS.map { it.name }

        // Session commands
        assertTrue(names.contains("new"))
        assertTrue(names.contains("reset"))
        assertTrue(names.contains("compact"))
        assertTrue(names.contains("stop"))
        assertTrue(names.contains("clear"))
        assertTrue(names.contains("focus"))
        assertTrue(names.contains("undo"))

        // Model commands
        assertTrue(names.contains("model"))
        assertTrue(names.contains("think"))
        assertTrue(names.contains("verbose"))
        assertTrue(names.contains("fast"))

        // Tools commands
        assertTrue(names.contains("help"))
        assertTrue(names.contains("status"))
        assertTrue(names.contains("export"))
        assertTrue(names.contains("usage"))

        // Agents commands
        assertTrue(names.contains("agents"))
        assertTrue(names.contains("kill"))
        assertTrue(names.contains("skill"))
        assertTrue(names.contains("steer"))
    }

    @Test
    fun `SLASH_COMMANDS stop aliases exist`() {
        val stopAliases = SLASH_COMMANDS.filter { it.description.contains("stop alias") }
        assertTrue(stopAliases.size >= 4) // esc, abort, wait, exit
    }

    @Test
    fun `SLASH_COMMANDS category distribution`() {
        val sessionCount = SLASH_COMMANDS.count { it.category == SlashCommandCategory.SESSION }
        val modelCount = SLASH_COMMANDS.count { it.category == SlashCommandCategory.MODEL }
        val toolsCount = SLASH_COMMANDS.count { it.category == SlashCommandCategory.TOOLS }
        val agentsCount = SLASH_COMMANDS.count { it.category == SlashCommandCategory.AGENTS }

        assertTrue(sessionCount >= 10) // new, reset, compact, stop+aliases, clear, focus, undo
        assertTrue(modelCount >= 4)    // model, think, verbose, fast
        assertTrue(toolsCount >= 5)    // help, status, export, usage, skill
        assertTrue(agentsCount >= 2)   // agents, kill, steer (skill is tools)
    }

    // ─────────────────────────────────────────────────────────────
    // getSlashCommandCompletions Tests
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `getSlashCommandCompletions with empty filter returns all`() {
        val completions = getSlashCommandCompletions("")
        assertEquals(SLASH_COMMANDS.size, completions.size)
    }

    @Test
    fun `getSlashCommandCompletions with blank filter returns all`() {
        val completions = getSlashCommandCompletions("   ")
        assertEquals(SLASH_COMMANDS.size, completions.size)
    }

    @Test
    fun `getSlashCommandCompletions filters by name prefix`() {
        val completions = getSlashCommandCompletions("st")
        val names = completions.map { it.name }

        assertTrue(names.contains("stop"))
        assertTrue(names.contains("status"))
        assertTrue(names.contains("steer"))
    }

    @Test
    fun `getSlashCommandCompletions filters by description`() {
        val completions = getSlashCommandCompletions("new")
        assertTrue(completions.any { it.name == "new" })
    }

    @Test
    fun `getSlashCommandCompletions returns empty for no match`() {
        val completions = getSlashCommandCompletions("xyz123")
        assertTrue(completions.isEmpty())
    }

    @Test
    fun `getSlashCommandCompletions is case insensitive`() {
        val completionsLower = getSlashCommandCompletions("HELP")
        val completionsUpper = getSlashCommandCompletions("help")

        assertTrue(completionsLower.any { it.name == "help" })
        assertTrue(completionsUpper.any { it.name == "help" })
    }

    // ─────────────────────────────────────────────────────────────
    // parseSlashCommand Tests
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `parseSlashCommand parses simple command`() {
        val result = parseSlashCommand("/help")

        assertNotNull(result)
        assertEquals("help", result!!.command.name)
        assertEquals("", result.args)
    }

    @Test
    fun `parseSlashCommand parses command with args`() {
        val result = parseSlashCommand("/model claude-3")

        assertNotNull(result)
        assertEquals("model", result!!.command.name)
        assertEquals("claude-3", result.args)
    }

    @Test
    fun `parseSlashCommand parses command with colon separator`() {
        val result = parseSlashCommand("/think: high")

        assertNotNull(result)
        assertEquals("think", result!!.command.name)
        assertEquals("high", result.args)
    }

    @Test
    fun `parseSlashCommand handles extra spaces`() {
        val result = parseSlashCommand("/model   claude-3-opus  ")

        assertNotNull(result)
        assertEquals("model", result!!.command.name)
        assertEquals("claude-3-opus", result.args)
    }

    @Test
    fun `parseSlashCommand returns null for non-slash text`() {
        val result = parseSlashCommand("hello world")

        assertNull(result)
    }

    @Test
    fun `parseSlashCommand returns null for unknown command`() {
        val result = parseSlashCommand("/unknowncommand")

        assertNull(result)
    }

    @Test
    fun `parseSlashCommand returns null for empty slash`() {
        val result = parseSlashCommand("/")

        assertNull(result)
    }

    @Test
    fun `parseSlashCommand returns null for slash with only spaces`() {
        val result = parseSlashCommand("/   ")

        assertNull(result)
    }

    @Test
    fun `parseSlashCommand is case insensitive for command name`() {
        val resultUpper = parseSlashCommand("/HELP")
        val resultLower = parseSlashCommand("/help")

        assertNotNull(resultUpper)
        assertNotNull(resultLower)
        assertEquals(resultUpper!!.command.name, resultLower!!.command.name)
    }

    @Test
    fun `parseSlashCommand parses stop aliases`() {
        val aliases = listOf("esc", "abort", "wait", "exit")

        for (alias in aliases) {
            val result = parseSlashCommand("/$alias")
            assertNotNull(result)
            assertTrue(result!!.command.description.contains("stop alias"))
        }
    }

    // ─────────────────────────────────────────────────────────────
    // isSlashCommandPrefix Tests
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `isSlashCommandPrefix returns true for slash prefix`() {
        assertTrue(isSlashCommandPrefix("/help"))
        assertTrue(isSlashCommandPrefix("/model claude"))
        assertTrue(isSlashCommandPrefix("/"))
    }

    @Test
    fun `isSlashCommandPrefix returns false for non-slash`() {
        assertFalse(isSlashCommandPrefix("help"))
        assertFalse(isSlashCommandPrefix(" /help"))
        assertFalse(isSlashCommandPrefix(""))
    }

    // ─────────────────────────────────────────────────────────────
    // getCommandPrefix Tests
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `getCommandPrefix extracts simple prefix`() {
        assertEquals("help", getCommandPrefix("/help"))
    }

    @Test
    fun `getCommandPrefix extracts prefix with args`() {
        assertEquals("model", getCommandPrefix("/model claude-3"))
    }

    @Test
    fun `getCommandPrefix extracts prefix with colon`() {
        assertEquals("think", getCommandPrefix("/think: high"))
    }

    @Test
    fun `getCommandPrefix returns empty for non-slash`() {
        assertEquals("", getCommandPrefix("help"))
        assertEquals("", getCommandPrefix(""))
    }

    @Test
    fun `getCommandPrefix handles trailing spaces`() {
        assertEquals("help", getCommandPrefix("/help   "))
    }

    // ─────────────────────────────────────────────────────────────
    // CATEGORY_ORDER Tests
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `CATEGORY_ORDER has correct sequence`() {
        assertEquals(4, CATEGORY_ORDER.size)
        assertEquals(SlashCommandCategory.SESSION, CATEGORY_ORDER[0])
        assertEquals(SlashCommandCategory.MODEL, CATEGORY_ORDER[1])
        assertEquals(SlashCommandCategory.TOOLS, CATEGORY_ORDER[2])
        assertEquals(SlashCommandCategory.AGENTS, CATEGORY_ORDER[3])
    }
}