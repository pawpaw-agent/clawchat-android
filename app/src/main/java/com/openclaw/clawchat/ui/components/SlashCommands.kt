package com.openclaw.clawchat.ui.components

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * 斜杠命令 - 1:1 复刻 webchat slash-commands.ts
 *
 * 支持的命令：
 * - Session: /new, /reset, /compact, /stop, /clear, /focus
 * - Model: /model, /think, /verbose, /fast
 * - Tools: /help, /status, /export, /usage
 * - Agents: /agents, /kill, /skill, /steer
 */

/**
 * 斜杠菜单状态
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
 * 命令类别
 */
enum class SlashCommandCategory {
    SESSION,
    MODEL,
    AGENTS,
    TOOLS
}

/**
 * 命令定义
 */
@Serializable
data class SlashCommandDef(
    val name: String,
    val description: String,
    val args: String? = null,
    val icon: String? = null,
    val category: SlashCommandCategory = SlashCommandCategory.TOOLS,
    /** 是否在客户端本地执行（而不是发送给 agent） */
    val executeLocal: Boolean = false,
    /** 参数选项（用于提示） */
    val argOptions: List<String>? = null,
    /** 快捷键提示（仅显示） */
    val shortcut: String? = null
)

/**
 * 解析后的斜杠命令
 */
data class ParsedSlashCommand(
    val command: SlashCommandDef,
    val args: String
)

/**
 * 所有支持的斜杠命令
 */
val SLASH_COMMANDS: List<SlashCommandDef> = listOf(
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
    // 停止命令（多个别名）
    SlashCommandDef(
        name = "stop",
        description = "停止当前运行",
        icon = "stop",
        category = SlashCommandCategory.SESSION,
        executeLocal = true
    ),
    SlashCommandDef(
        name = "esc",
        description = "停止当前运行（stop 别名）",
        icon = "stop",
        category = SlashCommandCategory.SESSION,
        executeLocal = true
    ),
    SlashCommandDef(
        name = "abort",
        description = "停止当前运行（stop 别名）",
        icon = "stop",
        category = SlashCommandCategory.SESSION,
        executeLocal = true
    ),
    SlashCommandDef(
        name = "wait",
        description = "停止当前运行（stop 别名）",
        icon = "stop",
        category = SlashCommandCategory.SESSION,
        executeLocal = true
    ),
    SlashCommandDef(
        name = "exit",
        description = "停止当前运行（stop 别名）",
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
    SlashCommandDef(
        name = "undo",
        description = "撤销上一轮对话",
        icon = "undo",
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
        category = SlashCommandCategory.TOOLS,
        executeLocal = false
    ),
    SlashCommandDef(
        name = "export",
        description = "导出会话到 Markdown",
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
        description = "列出 agents",
        icon = "monitor",
        category = SlashCommandCategory.AGENTS,
        executeLocal = true
    ),
    SlashCommandDef(
        name = "kill",
        description = "终止子 agents",
        args = "<id|all>",
        icon = "x",
        category = SlashCommandCategory.AGENTS,
        executeLocal = true
    ),
    SlashCommandDef(
        name = "skill",
        description = "运行一个 skill",
        args = "<name>",
        icon = "zap",
        category = SlashCommandCategory.TOOLS,
        executeLocal = false
    ),
    SlashCommandDef(
        name = "steer",
        description = "引导子 agent",
        args = "<id> <msg>",
        icon = "send",
        category = SlashCommandCategory.AGENTS,
        executeLocal = false
    )
)

/**
 * 命令类别排序
 */
val CATEGORY_ORDER: List<SlashCommandCategory> = listOf(
    SlashCommandCategory.SESSION,
    SlashCommandCategory.MODEL,
    SlashCommandCategory.TOOLS,
    SlashCommandCategory.AGENTS
)

/**
 * 获取斜杠命令补全
 * 
 * @param filter 过滤字符串（不含 /）
 * @return 匹配的命令列表
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
 * 解析斜杠命令
 * 
 * 支持格式：
 * - /command
 * - /command args...
 * - /command: args...
 * 
 * @param text 输入文本
 * @return 解析结果，如果不是斜杠命令则返回 null
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
    
    // 去掉开头的冒号
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
 * 检查文本是否是斜杠命令开头
 */
fun isSlashCommandPrefix(text: String): Boolean {
    return text.trim().startsWith("/")
}

/**
 * 获取命令前缀部分（不含 /）
 */
fun getCommandPrefix(text: String): String {
    val trimmed = text.trim()
    if (!trimmed.startsWith("/")) return ""
    
    val body = trimmed.drop(1)
    val spaceIndex = body.indexOfFirst { it == ' ' || it == ':' }
    return if (spaceIndex == -1) body else body.substring(0, spaceIndex)
}