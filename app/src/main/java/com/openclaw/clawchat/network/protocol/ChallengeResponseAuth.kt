package com.openclaw.clawchat.network.protocol

import android.util.Log
import com.openclaw.clawchat.security.SecurityModule
import com.openclaw.clawchat.security.SignedPayload
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 连接请求参数
 */
data class ConnectRequest(
    val device: DeviceInfo,
    val client: ClientInfo,
    val nonce: String,
    val signature: String,
    val signedAt: Long,
    val role: String,
    val scopes: List<String>,
    val token: String? = null
)

/**
 * 设备信息
 */
data class DeviceInfo(
    val id: String,
    val publicKey: String,  // raw 32 bytes base64url
    val signature: String,
    val signedAt: Long,
    val nonce: String
)

/**
 * Challenge-Response 认证实现 (Gateway 协议 v3)
 *
 * 认证流程:
 * 1. 建立 WebSocket 连接
 * 2. 接收 connect.challenge 事件 (包含服务器 nonce)
 * 3. 构建 v3 签名 payload → Ed25519 签名 → 发送 connect 请求
 * 4. 接收 connect 响应 (type=res, ok=true, 包含 deviceToken)
 *
 * 签名规范 (v3):
 * - payload = "v3|{deviceId}|{clientId}|{clientMode}|{role}|{scopes}|{signedAtMs}|{token}|{nonce}|{platform}|{deviceFamily}"
 * - 使用 Ed25519 签名（不是 ECDSA）
 * - 公钥: raw 32 bytes base64url（不是 SPKI DER）
 * - deviceId: sha256(raw public key).hex()
 */
class ChallengeResponseAuth(
    private val securityModule: SecurityModule,
    private val gatewayToken: String? = null,
    private val role: String = "operator",
    private val scopes: List<String> = listOf("operator.read", "operator.write")
) {
    companion object {
        private const val TAG = "ChallengeResponseAuth"
        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
    }

    // 当前待处理的挑战
    private var pendingChallenge: PendingChallenge? = null

    @Serializable
    data class PendingChallenge(
        val nonce: String,
        val timestamp: Long,
        val requestId: String
    )

    sealed class AuthState {
        data object Initial : AuthState()
        data object Connected : AuthState()
        data object ChallengeReceived : AuthState()
        data object RequestSent : AuthState()
        data class Success(val deviceToken: String) : AuthState()
        data class Error(val message: String, val code: String? = null) : AuthState()
    }

    data class AuthResult(
        val success: Boolean,
        val deviceToken: String? = null,
        val error: String? = null,
        val errorCode: String? = null
    )

    /**
     * 步骤 1: 处理 connect.challenge 事件
     *
     * Gateway 在 WebSocket 连接后自动发送:
     * { "type": "event", "event": "connect.challenge", "payload": { "nonce": "...", "ts": ... } }
     */
    fun handleChallenge(challenge: ConnectChallengePayload): Result<String> {
        Log.d(TAG, "收到挑战：nonce=${challenge.nonce.take(8)}..., ts=${challenge.timestamp}")

        if (challenge.nonce.isBlank()) {
            Log.e(TAG, "无效的 nonce")
            return Result.failure(IllegalStateException("Invalid nonce"))
        }

        val requestId = "auth-${System.currentTimeMillis()}"

        pendingChallenge = PendingChallenge(
            nonce = challenge.nonce,
            timestamp = challenge.timestamp,
            requestId = requestId
        )

        Log.d(TAG, "挑战已存储，requestId=$requestId")
        return Result.success(requestId)
    }

    /**
     * 步骤 2: 构建 connect 请求
     *
     * 使用 SecurityModule.signV3Payload() 构建完整 v3 签名:
     * payload = "v3|deviceId|clientId|clientMode|role|scopes|signedAtMs|token|nonce|platform|deviceFamily"
     */
    suspend fun buildConnectRequest(): ConnectRequest {
        val challenge = pendingChallenge
            ?: throw IllegalStateException("No pending challenge. Call handleChallenge() first.")

        // 初始化安全模块
        securityModule.initialize()

        val signedAtMs = System.currentTimeMillis()

        // 使用 v3 签名 payload
        val signed: SignedPayload = securityModule.signV3Payload(
            nonce = challenge.nonce,
            signedAtMs = signedAtMs,
            clientId = "openclaw-android",
            clientMode = "ui",
            role = role,
            scopes = scopes,
            token = gatewayToken ?: "",
            platform = "android",
            deviceFamily = "phone"
        )

        Log.d(TAG, "v3 签名完成, deviceId=${signed.deviceId.take(16)}...")

        val clientInfo = buildClientInfo()

        // 清除待处理的挑战
        pendingChallenge = null

        return ConnectRequest(
            device = DeviceInfo(
                id = signed.deviceId,
                publicKey = signed.publicKeyBase64Url,  // raw 32 bytes base64url
                signature = signed.signature,
                signedAt = signedAtMs,
                nonce = challenge.nonce
            ),
            client = clientInfo,
            nonce = challenge.nonce,
            signature = signed.signature,
            signedAt = signedAtMs,
            role = role,
            scopes = scopes,
            token = securityModule.getAuthToken()
        )
    }

    /**
     * 步骤 3: 处理 connect 响应
     *
     * 成功响应:
     * { "type": "res", "id": "...", "ok": true, "payload": { "type": "hello-ok", ..., "auth": { "deviceToken": "..." } } }
     */
    fun handleConnectOk(connectOk: ConnectOkPayload): AuthResult {
        Log.d(TAG, "认证成功，收到 deviceToken")

        if (connectOk.deviceToken.isBlank()) {
            return AuthResult(success = false, error = "Empty device token")
        }

        securityModule.completePairing(connectOk.deviceToken)
        Log.d(TAG, "deviceToken 已存储")

        return AuthResult(success = true, deviceToken = connectOk.deviceToken)
    }

    /**
     * 处理错误
     */
    fun handleErrorEvent(error: ErrorPayload): AuthResult {
        Log.e(TAG, "认证错误：${error.code} - ${error.message}")
        return AuthResult(success = false, error = error.message, errorCode = error.code)
    }

    fun reset() {
        pendingChallenge = null
    }

    // ==================== 内部方法 ====================

    private fun buildClientInfo(): ClientInfo {
        return ClientInfo(
            clientId = "openclaw-android",
            clientVersion = getClientVersion(),
            platform = "android",
            osVersion = android.os.Build.VERSION.RELEASE,
            deviceModel = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"
        )
    }

    private fun getClientVersion(): String {
        return try {
            val clazz = Class.forName("com.openclaw.clawchat.BuildConfig")
            clazz.getField("VERSION_NAME").get(null) as String
        } catch (e: Exception) {
            "1.0.0"
        }
    }
}

/**
 * 从 JSON 解析 ConnectChallengePayload
 */
fun String.toConnectChallenge(): ConnectChallengePayload? {
    return try {
        Json { ignoreUnknownKeys = true }.decodeFromString(this)
    } catch (e: Exception) {
        null
    }
}

/**
 * 从 JSON 解析 ConnectOkPayload
 */
fun String.toConnectOk(): ConnectOkPayload? {
    return try {
        Json { ignoreUnknownKeys = true }.decodeFromString(this)
    } catch (e: Exception) {
        null
    }
}
