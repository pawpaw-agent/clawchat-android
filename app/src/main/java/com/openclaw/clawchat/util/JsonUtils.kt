package com.openclaw.clawchat.util

import kotlinx.serialization.json.Json

/**
 * 统一 Json 配置
 * 
 * 避免在多个地方重复创建 Json 实例
 */
object JsonUtils {
    
    /**
     * 标准 Json 配置
     * 
     * - ignoreUnknownKeys: 忽略未知字段（向后兼容）
     * - isLenient: 宽松解析（允许非标准 JSON）
     */
    val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    /**
     * 带 encodeDefaults 的 Json 配置
     * 
     * 用于需要序列化默认值的场景（如 Gateway 协议）
     */
    val jsonWithDefaults: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }
}