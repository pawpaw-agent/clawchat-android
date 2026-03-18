package com.openclaw.clawchat.domain.repository

import com.openclaw.clawchat.domain.model.Message
import com.openclaw.clawchat.domain.model.Session
import com.openclaw.clawchat.domain.model.Attachment
import kotlinx.coroutines.flow.Flow

/**
 * 会话仓库接口
 * 
 * 定义会话管理的核心操作，包括会话的 CRUD、消息管理和会话状态控制。
 * 该接口位于 Domain 层，不包含任何具体实现细节，由 Data 层负责实现。
 * 
 * 设计原则：
 * - 接口隔离：每个方法职责单一
 * - 依赖倒置：上层依赖此接口，而非具体实现
 * - 异步支持：使用 Flow 支持响应式数据流
 */
interface SessionRepository {
    
    // ==================== 会话管理 ====================
    
    /**
     * 观察所有会话列表
     * 
     * 返回一个 Flow，当会话列表发生变化时自动发射新数据。
     * 列表按最后活动时间降序排列。
     * 
     * @return 会话列表的 Flow
     */
    fun observeSessions(): Flow<List<Session>>
    
    /**
     * 获取单个会话详情
     * 
     * @param sessionId 会话 ID
     * @return 会话对象，不存在返回 null
     */
    suspend fun getSession(sessionId: String): Session?
    
    /**
     * 创建新会话
     * 
     * 创建一个新的会话并立即与 Gateway 建立连接。
     * 
     * @param model AI 模型标识（可选，使用默认模型）
     * @param label 会话标签（可选）
     * @param thinking 是否启用深度思考模式
     * @return 创建结果，成功返回 Session，失败返回错误信息
     */
    suspend fun createSession(
        model: String? = null,
        label: String? = null,
        thinking: Boolean = false
    ): Result<Session>
    
    /**
     * 更新会话信息
     * 
     * @param session 更新后的会话对象
     * @return 操作结果
     */
    suspend fun updateSession(session: Session): Result<Unit>
    
    /**
     * 暂停会话
     * 
     * 暂停会话的消息处理，但保持会话状态。
     * 
     * @param sessionId 会话 ID
     * @return 操作结果
     */
    suspend fun pauseSession(sessionId: String): Result<Unit>
    
    /**
     * 恢复会话
     * 
     * 恢复已暂停的会话。
     * 
     * @param sessionId 会话 ID
     * @return 操作结果
     */
    suspend fun resumeSession(sessionId: String): Result<Unit>
    
    /**
     * 终止会话
     * 
     * 结束会话，释放相关资源。终止后的会话不可恢复。
     * 
     * @param sessionId 会话 ID
     * @return 操作结果
     */
    suspend fun terminateSession(sessionId: String): Result<Unit>
    
    /**
     * 删除会话
     * 
     * 永久删除会话及其所有消息。此操作不可撤销。
     * 
     * @param sessionId 会话 ID
     * @return 操作结果
     */
    suspend fun deleteSession(sessionId: String): Result<Unit>
    
    // ==================== 消息管理 ====================
    
    /**
     * 观察会话消息列表
     * 
     * 返回一个 Flow，当消息列表发生变化时自动发射新数据。
     * 消息按时间戳升序排列。
     * 
     * @param sessionId 会话 ID
     * @return 消息列表的 Flow
     */
    fun observeMessages(sessionId: String): Flow<List<Message>>
    
    /**
     * 获取会话消息历史
     * 
     * 一次性获取指定会话的所有历史消息。
     * 
     * @param sessionId 会话 ID
     * @param limit 最大返回数量（可选，0 表示全部）
     * @return 消息列表
     */
    suspend fun getMessageHistory(
        sessionId: String,
        limit: Int = 0
    ): List<Message>
    
    /**
     * 发送消息
     * 
     * 向指定会话发送一条用户消息，并等待助手响应。
     * 
     * @param sessionId 会话 ID
     * @param content 消息内容
     * @param attachments 可选附件列表
     * @return 发送结果，成功返回发送的消息，失败返回错误信息
     */
    suspend fun sendMessage(
        sessionId: String,
        content: String,
        attachments: List<Attachment> = emptyList()
    ): Result<Message>
    
    /**
     * 重新生成响应
     * 
     * 请求助手重新生成最后一条消息的响应。
     * 
     * @param sessionId 会话 ID
     * @return 操作结果
     */
    suspend fun regenerateResponse(sessionId: String): Result<Message>
    
    // ==================== 查询操作 ====================
    
    /**
     * 获取活动会话数量
     * 
     * @return 活动会话数量
     */
    suspend fun getActiveSessionCount(): Int
    
    /**
     * 查找包含指定关键词的会话
     * 
     * @param query 搜索关键词
     * @return 匹配的会话列表
     */
    suspend fun searchSessions(query: String): List<Session>
    
    /**
     * 获取指定模型的会话列表
     * 
     * @param model 模型标识
     * @return 会话列表
     */
    suspend fun getSessionsByModel(model: String): List<Session>
}
