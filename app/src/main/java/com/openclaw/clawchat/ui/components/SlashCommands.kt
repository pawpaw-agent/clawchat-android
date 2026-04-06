package com.openclaw.clawchat.ui.components

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * Slash Commands - 1:1 replica of webchat slash-commands.ts
 *
 * Supported commands:
 * - Session: /new, /reset, /compact, /stop, /clear, /focus
 * - Model: /model, /think, /verbose, /fast
 * - Tools: /help, /status, /export, /usage
 * - Agents: /agents, /kill, /skill, /steer
 */

/**
 * Slash menu state
 */
@Immutable
data class SlashMenuState(
    val isOpen: Boolean = false,
    val mode: String = "command",  // "command" or "args"
    val items: List<SlashCommandDef> = emptyList(),
    val argItems: List<String> = emptyList(),
    val selectedIndex: Int = 0,
    val command: SlashCommandDef? = null
)

/**
 * Command category
 */
enum class SlashCommandCategory {
    SESSION,
    MODEL,
    AGENTS,
    TOOLS
}

/**
 * Command definition
 */
@Serializable
data class SlashCommandDef(
    val name: String,
    val description: String,
    val args: String? = null,
    val icon: String? = null,
    val category: SlashCommandCategory = SlashCommandCategory.TOOLS,
    /** Execute locally on client (not sent to agent) */
    val executeLocal: Boolean = false,
    /** Argument options (for hints) */
    val argOptions: List<String>? = null,
    /** Shortcut hint (for display only) */
    val shortcut: String? = null
)

/**
 * Parsed slash command
 */
data class ParsedSlashCommand(
    val command: SlashCommandDef,
    val args: String
)

/**
 * 所有支持的斜杠命令
 * Note: Descriptions here are default/fallback values. Localized versions are
 * provided by SlashCommandExecutor.getLocalizedCommandDescription() using string resources.
 */
val SLASH_COMMANDS: List<SlashCommandDef> = listOf(
    // ── Session ──
    SlashCommandDef(
        name = "new",
        description = "Start a new session",
        icon = "plus",
        category = SlashCommandCategory.SESSION,
        executeLocal = true
    ),
    SlashCommandDef(
        name = "reset",
        description = "Reset current session",
        icon = "refresh",
        category = SlashCommandCategory.SESSION,
        executeLocal = true
    ),
    SlashCommandDef(
        name = "compact",
        description = "Compact session context",
        icon = "loader",
        category = SlashCommandCategory.SESSION,
        executeLocal = true
    ),
    // 停止命令（多个别名）
    SlashCommandDef(
        name = "stop",
        description = "Stop current run",
        icon = "stop",
        category = SlashCommandCategory.SESSION,
        executeLocal = true
    ),
    SlashCommandDef(
        name = "esc",
        description = "Stop current run (stop alias)",
        icon = "stop",
        category = SlashCommandCategory.SESSION,
        executeLocal = true
    ),
    SlashCommandDef(
        name = "abort",
        description = "Stop current run (stop alias)",
        icon = "stop",
        category = SlashCommandCategory.SESSION,
        executeLocal = true
    ),
    SlashCommandDef(
        name = "wait",
        description = "Stop current run (stop alias)",
        icon = "stop",
        category = SlashCommandCategory.SESSION,
        executeLocal = true
    ),
    SlashCommandDef(
        name = "exit",
        description = "Stop current run (stop alias)",
        icon = "stop",
        category = SlashCommandCategory.SESSION,
        executeLocal = true
    ),
    SlashCommandDef(
        name = "clear",
        description = "Clear chat history",
        icon = "trash",
        category = SlashCommandCategory.SESSION,
        executeLocal = true
    ),
    SlashCommandDef(
        name = "focus",
        description = "Toggle focus mode",
        icon = "eye",
        category = SlashCommandCategory.SESSION,
        executeLocal = true
    ),
    SlashCommandDef(
        name = "undo",
        description = "Undo last conversation",
        icon = "undo",
        category = SlashCommandCategory.SESSION,
        executeLocal = true
    ),

    // ── Model ──
    SlashCommandDef(
        name = "model",
        description = "Show or set model",
        args = "<name>",
        icon = "brain",
        category = SlashCommandCategory.MODEL,
        executeLocal = true
    ),
    SlashCommandDef(
        name = "think",
        description = "Set thinking level",
        args = "<level>",
        icon = "brain",
        category = SlashCommandCategory.MODEL,
        executeLocal = true,
        argOptions = listOf("off", "low", "medium", "high")
    ),
    SlashCommandDef(
        name = "verbose",
        description = "Toggle verbose mode",
        args = "<on|off|full>",
        icon = "terminal",
        category = SlashCommandCategory.MODEL,
        executeLocal = true,
        argOptions = listOf("on", "off", "full")
    ),
    SlashCommandDef(
        name = "fast",
        description = "Toggle fast mode",
        args = "<status|on|off>",
        icon = "zap",
        category = SlashCommandCategory.MODEL,
        executeLocal = true,
        argOptions = listOf("status", "on", "off")
    ),

    // ── Tools ──
    SlashCommandDef(
        name = "help",
        description = "Show available commands",
        icon = "book",
        category = SlashCommandCategory.TOOLS,
        executeLocal = true
    ),
    SlashCommandDef(
        name = "status",
        description = "Show session status",
        icon = "barChart",
        category = SlashCommandCategory.TOOLS,
        executeLocal = false
    ),
    SlashCommandDef(
        name = "export",
        description = "Export session to Markdown",
        icon = "download",
        category = SlashCommandCategory.TOOLS,
        executeLocal = true
    ),
    SlashCommandDef(
        name = "usage",
        description = "Show token usage",
        icon = "barChart",
        category = SlashCommandCategory.TOOLS,
        executeLocal = true
    ),

    // ── Agents ──
    SlashCommandDef(
        name = "agents",
        description = "List agents",
        icon = "monitor",
        category = SlashCommandCategory.AGENTS,
        executeLocal = true
    ),
    SlashCommandDef(
        name = "kill",
        description = "Terminate child agents",
        args = "<id|all>",
        icon = "x",
        category = SlashCommandCategory.AGENTS,
        executeLocal = true
    ),
    SlashCommandDef(
        name = "skill",
        description = "Run a skill",
        args = "<name>",
        icon = "zap",
        category = SlashCommandCategory.TOOLS,
        executeLocal = false
    ),
    SlashCommandDef(
        name = "steer",
        description = "Steer child agent",
        args = "<id> <msg>",
        icon = "send",
        category = SlashCommandCategory.AGENTS,
        executeLocal = false
    )
)

/**
 * Command category order
 */
val CATEGORY_ORDER: List<SlashCommandCategory> = listOf(
    SlashCommandCategory.SESSION,
    SlashCommandCategory.MODEL,
    SlashCommandCategory.TOOLS,
    SlashCommandCategory.AGENTS
)

/**
 * Get slash command completions
 *
 * @param filter Filter string (without /)
 * @return List of matching commands
 */
fun getSlashCommandCompletions(filter: String): List<SlashCommandDef> {
    val lower = filter.lowercase()

    val commands = if (lower.isNotBlank()) {
        SLASH_COMMANDS.filter { cmd ->
            cmd.name.startsWith(lower) ||
            cmd.description.lowercase().contains(lower)
        }
    } else {
        SLASH_COMMANDS
    }

    return commands.sortedWith(compareBy(
        { CATEGORY_ORDER.indexOf(it.category) },
        { if (lower.isNotBlank() && it.name.startsWith(lower)) 0 else 1 }
    ))
}

/**
 * Parse slash command
 *
 * Supported formats:
 * - /command
 * - /command args...
 * - /command: args...
 *
 * @param text Input text
 * @return Parse result, null if not a slash command
 */
fun parseSlashCommand(text: String): ParsedSlashCommand? {
    val trimmed = text.trim()
    if (!trimmed.startsWith("/")) {
        return null
    }

    val body = trimmed.drop(1)
    val firstSeparator = body.indexOfFirst { it == ' ' || it == ':' }

    val name = if (firstSeparator == -1) body else body.substring(0, firstSeparator)
    var remainder = if (firstSeparator == -1) "" else body.substring(firstSeparator).trimStart()

    // Remove leading colon
    if (remainder.startsWith(":")) {
        remainder = remainder.drop(1).trimStart()
    }

    val args = remainder.trim()

    if (name.isBlank()) {
        return null
    }

    val command = SLASH_COMMANDS.find { it.name.equals(name, ignoreCase = true) }
        ?: return null

    return ParsedSlashCommand(command = command, args = args)
}

/**
 * Check if text is a slash command prefix
 */
fun isSlashCommandPrefix(text: String): Boolean {
    return text.trim().startsWith("/")
}

/**
 * Get command prefix part (without /)
 */
fun getCommandPrefix(text: String): String {
    val trimmed = text.trim()
    if (!trimmed.startsWith("/")) return ""

    val body = trimmed.drop(1)
    val spaceIndex = body.indexOfFirst { it == ' ' || it == ':' }
    return if (spaceIndex == -1) body else body.substring(0, spaceIndex)
}