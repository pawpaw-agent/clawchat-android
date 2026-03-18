package com.openclaw.clawchat.network.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * WebSocket 协议 v3 定义
 * 
 * 遵循 OpenClaw Gateway 协议规范 v3
 * 参考：https://docs.openclaw.ai/gateway/protocol
 */
object WebSocketProtocol {
    const val VERSION = "3.0.0"
    const val PROTOCOL_HEADER = "X-ClawChat-Protocol"
    const val VERSION_HEADER = "X-ClawChat-Version"
    
    // WebSocket 路径
    const val WS_PATH = "/ws"
    
    // 认证头
    const val TIMESTAMP_HEADER = "X-ClawChat-Timestamp"
    const val NONCE_HEADER = "X-ClawChat-Nonce"
    const val SIGNATURE_HEADER = "X-ClawChat-Signature"
    const val DEVICE_ID_HEADER = "X-ClawChat-Device-Id"
    const val TOKEN_HEADER = "Authorization"
    
    // 协议版本头
    const val PROTOCOL_VERSION_HEADER = "X-ClawChat-Protocol-Version"
}

/**
 * 协议错误码
 */
enum class ProtocolErrorCode(val code: Int, val description: String) {
    // 通用错误 (1xxx)
    UNKNOWN_ERROR(1000, "未知错误"),
    PROTOCOL_VERSION_MISMATCH(1001, "协议版本不匹配"),
    INVALID_MESSAGE_FORMAT(1002, "消息格式无效"),
    MISSING_REQUIRED_FIELD(1003, "缺少必填字段"),
    
    // 认证错误 (2xxx)
    AUTH_REQUIRED(2000, "需要认证"),
    AUTH_FAILED(2001, "认证失败"),
    INVALID_SIGNATURE(2002, "签名无效"),
    EXPIRED_CHALLENGE(2003, "挑战已过期"),
    INVALID_NONCE(2004, "Nonce 无效"),
    DEVICE_NOT_PAIRED(2005, "设备未配对"),
    TOKEN_EXPIRED(2006, "Token 已过期"),
    TOKEN_REVOKED(2007, "Token 已撤销"),
    
    // 连接错误 (3xxx)
    CONNECTION_LIMIT(3000, "连接数超限"),
    RATE_LIMIT(3001, "请求频率超限"),
    SESSION_NOT_FOUND(3002, "会话不存在"),
    SESSION_TERMINATED(3003, "会话已终止"),
    
    // 消息错误 (4xxx)
    MESSAGE_TOO_LARGE(4000, "消息过大"),
    INVALID_ATTACHMENT(4001, "附件无效"),
    UNSUPPORTED_MESSAGE_TYPE(4002, "不支持的消息类型")
}

/**
 * 协议错误响应
 */
@Serializable
data class ProtocolError(
    @SerialName("code")
    val code: Int,
    @SerialName("message")
    val message: String,
    @SerialName("details")
    val details: String? = null,
    @SerialName("timestamp")
    val timestamp: Long = System.currentTimeMillis()
) {
    companion object {
        fun from(errorCode: ProtocolErrorCode, details: String? = null): ProtocolError {
            return ProtocolError(
                code = errorCode.code,
                message = errorCode.description,
                details = details
            )
        }
    }
}

/**
 * Challenge-Response 认证请求
 * 
 * 客户端发起配对或连接时发送
 */
@Serializable
data class AuthRequest(
    @SerialName("type")
    val type: String = "auth",
    @SerialName("deviceId")
    val deviceId: String,
    @SerialName("publicKey")
    val publicKey: String,
    @SerialName("clientInfo")
    val clientInfo: ClientInfo
)

/**
 * Challenge-Response 认证响应
 * 
 * 服务器返回挑战 nonce
 */
@Serializable
data class AuthChallenge(
    @SerialName("type")
    val type: String = "challenge",
    @SerialName("nonce")
    val nonce: String,
    @SerialName("expiresAt")
    val expiresAt: Long,
    @SerialName("protocolVersion")
    val protocolVersion: String = WebSocketProtocol.VERSION
)

/**
 * Challenge-Response 签名响应
 * 
 * 客户端签名服务器提供的 nonce 后返回
 */
@Serializable
data class AuthResponse(
    @SerialName("type")
    val type: String = "auth_response",
    @SerialName("nonce")
    val nonce: String,
    @SerialName("signature")
    val signature: String,
    @SerialName("deviceId")
    val deviceId: String
)

/**
 * 认证成功响应
 * 
 * 服务器验证签名后返回设备 token
 */
@Serializable
data class AuthSuccess(
    @SerialName("type")
    val type: String = "auth_success",
    @SerialName("deviceToken")
    val deviceToken: String,
    @SerialName("expiresIn")
    val expiresIn: Long,
    @SerialName("protocolVersion")
    val protocolVersion: String = WebSocketProtocol.VERSION
)

/**
 * 客户端信息
 */
@Serializable
data class ClientInfo(
    @SerialName("clientId")
    val clientId: String = "openclaw-android",
    @SerialName("clientVersion")
    val clientVersion: String,
    @SerialName("platform")
    val platform: String = "android",
    @SerialName("osVersion")
    val osVersion: String,
    @SerialName("deviceModel")
    val deviceModel: String,
    @SerialName("protocolVersion")
    val protocolVersion: String = WebSocketProtocol.VERSION
)

/**
 * WebSocket 帧类型
 */
enum class FrameType(val value: String) {
    // 认证相关
    AUTH("auth"),
    CHALLENGE("challenge"),
    AUTH_RESPONSE("auth_response"),
    AUTH_SUCCESS("auth_success"),
    
    // 消息相关
    USER_MESSAGE("userMessage"),
    ASSISTANT_MESSAGE("assistantMessage"),
    SYSTEM_EVENT("systemEvent"),
    
    // 心跳
    PING("ping"),
    PONG("pong"),
    
    // 错误
    ERROR("error"),
    
    // 会话控制
    SESSION_CREATE("session.create"),
    SESSION_TERMINATE("session.terminate"),
    SESSION_PAUSE("session.pause"),
    SESSION_RESUME("session.resume")
}

/**
 * WebSocket 帧头
 * 
 * 每个消息的标准帧头格式
 */
@Serializable
data class FrameHeader(
    @SerialName("type")
    val type: String,
    @SerialName("version")
    val version: String = WebSocketProtocol.VERSION,
    @SerialName("timestamp")
    val timestamp: Long = System.currentTimeMillis(),
    @SerialName("messageId")
    val messageId: String? = null,
    @SerialName("sessionId")
    val sessionId: String? = null
)

/**
 * 标准 WebSocket 帧
 * 
 * 协议 v3 的标准帧格式
 */
@Serializable
data class WebSocketFrame(
    @SerialName("header")
    val header: FrameHeader,
    @SerialName("payload")
    val payload: String,
    @SerialName("signature")
    val signature: String? = null
)

/**
 * 协议版本信息
 */
@Serializable
data class ProtocolVersion(
    @SerialName("major")
    val major: Int = 3,
    @SerialName("minor")
    val minor: Int = 0,
    @SerialName("patch")
    val patch: Int = 0
) {
    fun toString(): String = "$major.$minor.$patch"
    
    fun isCompatibleWith(other: ProtocolVersion): Boolean {
        // 主版本号必须匹配
        return this.major == other.major
    }
    
    companion object {
        fun fromString(version: String): ProtocolVersion {
            val parts = version.split(".")
            return ProtocolVersion(
                major = parts.getOrNull(0)?.toIntOrNull() ?: 3,
                minor = parts.getOrNull(1)?.toIntOrNull() ?: 0,
                patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
            )
        }
    }
}
