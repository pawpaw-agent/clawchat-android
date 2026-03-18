package com.openclaw.clawchat.domain.usecase

import com.openclaw.clawchat.domain.model.Session
import com.openclaw.clawchat.domain.repository.SessionRepository

/**
 * 创建会话用例
 * 
 * 处理创建新会话的业务逻辑。
 * 负责验证会话配置、生成会话 ID 并委托仓库创建。
 * 
 * 设计原则：
 * - 单一职责：只处理创建会话的逻辑
 * - 配置验证：确保会话配置有效
 * - 默认值处理：提供合理的默认配置
 * 
 * @param sessionRepository 会话仓库
 */
class CreateSession(
    private val sessionRepository: SessionRepository
) {
    /**
     * 执行创建会话操作
     * 
     * @param model AI 模型标识（可选，使用默认模型）
     * @param label 会话标签（可选）
     * @param thinking 是否启用深度思考模式
     * @return 创建结果，成功返回新会话
     */
    suspend operator fun invoke(
        model: String? = null,
        label: String? = null,
        thinking: Boolean = false
    ): Result<Session> {
        // 验证模型名称格式（如果提供）
        model?.let {
            if (!isValidModelName(it)) {
                return Result.failure(IllegalArgumentException(
                    "Invalid model name format: $it"
                ))
            }
        }
        
        // 验证标签长度
        label?.let {
            if (it.length > MAX_LABEL_LENGTH) {
                return Result.failure(IllegalArgumentException(
                    "Label exceeds maximum length of $MAX_LABEL_LENGTH characters"
                ))
            }
        }
        
        // 委托仓库创建会话
        return sessionRepository.createSession(model, label, thinking)
    }
    
    /**
     * 创建快速会话（使用默认配置）
     */
    suspend fun createQuick(): Result<Session> {
        return invoke()
    }
    
    /**
     * 创建带标签的会话
     */
    suspend fun createLabeled(label: String): Result<Session> {
        return invoke(label = label)
    }
    
    /**
     * 创建深度思考会话
     */
    suspend fun createThinking(label: String? = null): Result<Session> {
        return invoke(label = label, thinking = true)
    }
    
    /**
     * 验证模型名称格式
     * 
     * 支持的格式：
     * - provider/model-name (如 "aliyun/qwen3.5-plus")
     * - provider/model-name:version (如 "openai/gpt-4:latest")
     */
    private fun isValidModelName(name: String): Boolean {
        // 基本格式检查
        if (name.isBlank() || name.length > MAX_MODEL_NAME_LENGTH) {
            return false
        }
        
        // 必须包含 provider/model 格式
        if (!name.contains("/")) {
            return false
        }
        
        // 不允许特殊字符（除了 - _ : . /）
        val validPattern = Regex("^[a-zA-Z0-9/_\\-:.]+$")
        return validPattern.matches(name)
    }
    
    companion object {
        private const val MAX_LABEL_LENGTH = 100
        private const val MAX_MODEL_NAME_LENGTH = 200
    }
}
