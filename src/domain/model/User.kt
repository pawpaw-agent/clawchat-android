package com.openclaw.clawchat.domain.model

/**
 * 用户信息领域模型
 * 
 * 表示 OpenClaw 系统的用户实体，包含用户的基本信息和权限范围。
 * 该模型为纯 Kotlin 实现，无 Android 依赖，可在多平台复用。
 * 
 * @property id 用户唯一标识符
 * @property name 用户显示名称
 * @property email 用户邮箱（可选）
 * @property role 用户角色（admin/operator/viewer）
 * @property scopes 权限范围列表
 * @property createdAt 账户创建时间戳（毫秒）
 * @property lastActiveAt 最后活跃时间戳（毫秒）
 */
data class User(
    val id: String,
    val name: String,
    val email: String? = null,
    val role: UserRole = UserRole.OPERATOR,
    val scopes: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val lastActiveAt: Long? = null
) {
    /**
     * 检查用户是否拥有指定权限
     * 
     * @param scope 权限字符串，如 "operator.read", "operator.write"
     * @return 是否拥有该权限
     */
    fun hasScope(scope: String): Boolean = scope in scopes
    
    /**
     * 检查用户是否为管理员
     */
    fun isAdmin(): Boolean = role == UserRole.ADMIN
    
    /**
     * 检查用户是否可执行写操作
     */
    fun canWrite(): Boolean = role == UserRole.ADMIN || role == UserRole.OPERATOR
}

/**
 * 用户角色枚举
 * 
 * 定义用户在系统中的角色等级，影响可执行的操作范围。
 */
enum class UserRole {
    /**
     * 管理员 - 完全访问权限，可管理设备配对、用户权限等
     */
    ADMIN,
    
    /**
     * 操作员 - 可执行读写操作，发送消息、管理会话
     */
    OPERATOR,
    
    /**
     * 观察者 - 只读权限，仅可查看会话和消息
     */
    VIEWER;
    
    companion object {
        /**
         * 从字符串解析用户角色
         * 
         * @param value 角色字符串（不区分大小写）
         * @return 对应的 UserRole 枚举值
         * @throws IllegalArgumentException 当输入不是有效角色时
         */
        fun fromString(value: String): UserRole {
            return when (value.lowercase()) {
                "admin", "administrator" -> ADMIN
                "operator", "op" -> OPERATOR
                "viewer", "reader" -> VIEWER
                else -> throw IllegalArgumentException("Unknown user role: $value")
            }
        }
    }
}
