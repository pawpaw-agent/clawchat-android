package com.openclaw.clawchat.ui.state

/**
 * 斜杠命令定义（1:1 对应 webchat slash-commands.ts）
 */
data class SlashCommandDef(
    val name: String,
    val description: String,
    val args: String? = null,
    val icon: String = "terminal",
    val category: SlashCommandCategory = SlashCommandCategory.TOOLS,
    val executeLocal: Boolean = false,
    val argOptions: List<String> = emptyList()
)

enum class SlashCommandCategory {
    SESSION,
    MODEL,
    AGENTS,
    TOOLS
}

val SLASH_COMMANDS = listOf(
    // ── Session ──
    SlashCommandDef(
        name = "new",
        description = "开始新会话",
        icon = "plus",
        category = SlashCommandCategory.SESSION,
        executeLocal = true
    ),
    SlashCommandDef(
        name = "reset",
        description = "重置当前会话",
        icon = "refresh",
        category = SlashCommandCategory.SESSION,
        executeLocal = true
    ),
    SlashCommandDef(
        name = "compact",
        description = "压缩会话上下文",
        icon = "loader",
        category = SlashCommandCategory.SESSION,
        executeLocal = true
    ),
    SlashCommandDef(
        name = "stop",
        description = "停止当前运行",
        icon = "stop",
        category = SlashCommandCategory.SESSION,
        executeLocal = true
    ),
    SlashCommandDef(
        name = "clear",
        description = "清空聊天历史",
        icon = "trash",
        category = SlashCommandCategory.SESSION,
        executeLocal = true
    ),
    SlashCommandDef(
        name = "focus",
        description = "切换专注模式",
        icon = "eye",
        category = SlashCommandCategory.SESSION,
        executeLocal = true
    ),

    // ── Model ──
    SlashCommandDef(
        name = "model",
        description = "显示或设置模型",
        args = "<name>",
        icon = "brain",
        category = SlashCommandCategory.MODEL,
        executeLocal = true
    ),
    SlashCommandDef(
        name = "think",
        description = "设置思考级别",
        args = "<level>",
        icon = "brain",
        category = SlashCommandCategory.MODEL,
        executeLocal = true,
        argOptions = listOf("off", "low", "medium", "high")
    ),
    SlashCommandDef(
        name = "verbose",
        description = "切换详细模式",
        args = "<on|off|full>",
        icon = "terminal",
        category = SlashCommandCategory.MODEL,
        executeLocal = true,
        argOptions = listOf("on", "off", "full")
    ),
    SlashCommandDef(
        name = "fast",
        description = "切换快速模式",
        args = "<status|on|off>",
        icon = "zap",
        category = SlashCommandCategory.MODEL,
        executeLocal = true,
        argOptions = listOf("status", "on", "off")
    ),

    // ── Tools ──
    SlashCommandDef(
        name = "help",
        description = "显示可用命令",
        icon = "book",
        category = SlashCommandCategory.TOOLS,
        executeLocal = true
    ),
    SlashCommandDef(
        name = "status",
        description = "显示会话状态",
        icon = "barChart",
        category = SlashCommandCategory.TOOLS
    ),
    SlashCommandDef(
        name = "export",
        description = "导出会话为 Markdown",
        icon = "download",
        category = SlashCommandCategory.TOOLS,
        executeLocal = true
    ),
    SlashCommandDef(
        name = "usage",
        description = "显示 token 使用量",
        icon = "barChart",
        category = SlashCommandCategory.TOOLS,
        executeLocal = true
    ),

    // ── Agents ──
    SlashCommandDef(
        name = "agents",
        description = "列出所有 agents",
        icon = "monitor",
        category = SlashCommandCategory.AGENTS,
        executeLocal = true
    ),
    SlashCommandDef(
        name = "kill",
        description = "中止子 agents",
        args = "<id|all>",
        icon = "x",
        category = SlashCommandCategory.AGENTS,
        executeLocal = true
    ),
    SlashCommandDef(
        name = "skill",
        description = "运行一个技能",
        args = "<name>",
        icon = "zap",
        category = SlashCommandCategory.TOOLS
    ),
    SlashCommandDef(
        name = "steer",
        description = "引导子 agent",
        args = "<id> <msg>",
        icon = "send",
        category = SlashCommandCategory.AGENTS
    )
)

/**
 * 解析斜杠命令
 * 支持 /command, /command args..., /command: args...
 */
data class ParsedSlashCommand(
    val command: SlashCommandDef,
    val args: String
)

fun parseSlashCommand(text: String): ParsedSlashCommand? {
    val trimmed = text.trim()
    if (!trimmed.startsWith("/")) return null
    
    val body = trimmed.drop(1)
    val firstSeparator = body.indexOfFirst { it == ' ' || it == ':' }
    
    val name = if (firstSeparator == -1) body else body.substring(0, firstSeparator)
    var remainder = if (firstSeparator == -1) "" else body.substring(firstSeparator).trimStart()
    if (remainder.startsWith(":")) {
        remainder = remainder.drop(1).trimStart()
    }
    val args = remainder.trim()
    
    if (name.isBlank()) return null
    
    val command = SLASH_COMMANDS.find { it.name.equals(name, ignoreCase = true) }
        ?: return null
    
    return ParsedSlashCommand(command, args)
}

/**
 * 获取斜杠命令补全列表
 */
fun getSlashCommandCompletions(filter: String): List<SlashCommandDef> {
    val lower = filter.lowercase()
    val commands = if (lower.isNotEmpty()) {
        SLASH_COMMANDS.filter { cmd ->
            cmd.name.startsWith(lower) || cmd.description.lowercase().contains(lower)
        }
    } else {
        SLASH_COMMANDS
    }
    
    val categoryOrder = listOf(
        SlashCommandCategory.SESSION,
        SlashCommandCategory.MODEL,
        SlashCommandCategory.TOOLS,
        SlashCommandCategory.AGENTS
    )
    
    return commands.sortedWith(compareBy { categoryOrder.indexOf(it.category) })
}

/**
 * 检查输入是否是斜杠命令模式
 */
fun isSlashCommandMode(text: String): Boolean {
    val trimmed = text.trim()
    return trimmed.startsWith("/") && trimmed.count { it == '/' } == 1
}

/**
 * 获取斜杠命令的过滤部分
 */
fun getSlashCommandFilter(text: String): String {
    val trimmed = text.trim()
    if (!trimmed.startsWith("/")) return ""
    
    val body = trimmed.drop(1)
    val spaceIndex = body.indexOf(' ')
    return if (spaceIndex == -1) body else body.substring(0, spaceIndex)
}