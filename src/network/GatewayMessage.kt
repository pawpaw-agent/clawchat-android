package com.clawchat.android.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Gateway 消息格式定义
 * 
 * 用于客户端与 OpenClaw Gateway 之间的通信
 */
@Serializable
sealed class GatewayMessage {
    @SerialName("type")
    abstract val type: String
    
    /**
     * 系统事件消息
     * 
     * 由 Gateway 推送的系统级事件（如 cron 提醒、状态变更）
     */
    @Serializable
    @SerialName("systemEvent")
    data class SystemEvent(
        val text: String,
        val timestamp: Long = System.currentTimeMillis()
    ) : GatewayMessage() {
        override val type = "systemEvent"
    }
    
    /**
     * 用户消息
     * 
     * 用户发送的消息，包含会话 ID、内容和可选附件
     */
    @Serializable
    @SerialName("userMessage")
    data class UserMessage(
        val sessionId: String,
        val content: String,
        val attachments: List<AttachmentDto> = emptyList(),
        val timestamp: Long = System.currentTimeMillis()
    ) : GatewayMessage() {
        override val type = "userMessage"
    }
    
    /**
     * 助手消息
     * 
     * Agent/助手返回的响应消息
     */
    @Serializable
    @SerialName("assistantMessage")
    data class AssistantMessage(
        val sessionId: String,
        val content: String,
        val model: String?,
        val timestamp: Long = System.currentTimeMillis()
    ) : GatewayMessage() {
        override val type = "assistantMessage"
    }
    
    /**
     * 心跳请求
     * 
     * 用于测量延迟和保持连接活跃
     */
    @Serializable
    @SerialName("ping")
    data class Ping(
        val timestamp: Long
    ) : GatewayMessage() {
        override val type = "ping"
    }
    
    /**
     * 心跳响应
     * 
     * 对 Ping 的响应，包含延迟信息
     */
    @Serializable
    @SerialName("pong")
    data class Pong(
        val timestamp: Long,
        val latency: Long
    ) : GatewayMessage() {
        override val type = "pong"
    }
    
    /**
     * 错误消息
     * 
     * 服务器返回的错误信息
     */
    @Serializable
    @SerialName("error")
    data class Error(
        val code: String,
        val message: String,
        val details: String? = null,
        val timestamp: Long = System.currentTimeMillis()
    ) : GatewayMessage() {
        override val type = "error"
    }
}

/**
 * 附件数据传输对象
 * 
 * 支持通过 URL 或 Base64 内联传输
 */
@Serializable
data class AttachmentDto(
    val id: String,
    val name: String,
    val mimeType: String,
    val size: Long,
    val url: String? = null,
    val base64: String? = null
)

/**
 * 消息角色枚举
 */
enum class MessageRole {
    USER,
    ASSISTANT,
    SYSTEM;
    
    companion object {
        fun fromString(value: String): MessageRole {
            return when (value.lowercase()) {
                "user" -> USER
                "assistant" -> ASSISTANT
                "system" -> SYSTEM
                else -> throw IllegalArgumentException("Unknown role: $value")
            }
        }
    }
}

/**
 * 消息解析器
 * 
 * 负责将原始 JSON 字符串解析为 GatewayMessage 对象
 */
object MessageParser {
    private val json = kotlinx.serialization.json.Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = false
    }
    
    /**
     * 解析 incoming 消息
     * 
     * @param text 原始 JSON 字符串
     * @return 解析后的消息对象，失败返回 null
     */
    fun parse(text: String): GatewayMessage? {
        return try {
            json.decodeFromString<GatewayMessage>(text)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * 序列化为 JSON 字符串
     * 
     * @param message 消息对象
     * @return JSON 字符串
     */
    fun serialize(message: GatewayMessage): String {
        return json.encodeToString(GatewayMessage.serializer(), message)
    }
}
