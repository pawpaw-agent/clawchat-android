package com.openclaw.clawchat.network.protocol

/**
 * Gateway 连接配置常量
 */
object GatewayConfig {
    // 超时配置
    const val AUTH_TIMEOUT_MS = 60_000L
    const val REQUEST_TIMEOUT_MS = 30_000L

    // Tick 监控配置（服务器每 ~30 秒发送 tick 事件）
    const val TICK_STALE_CHECK_INTERVAL_MS = 15_000L
    const val TICK_STALE_THRESHOLD_MS = 90_000L  // 2.5x tick interval

    // 重连配置
    const val INITIAL_RECONNECT_DELAY_MS = 1000L
    const val MAX_RECONNECT_DELAY_MS = 30_000L
    const val RECONNECT_BACKOFF_FACTOR = 2.0
    const val MAX_RECONNECT_ATTEMPTS = 15

    // 日志配置
    const val NONCE_LOG_PREFIX_LEN = 8
}