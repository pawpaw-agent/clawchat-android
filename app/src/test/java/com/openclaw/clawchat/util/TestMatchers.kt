package com.openclaw.clawchat.util

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTag

/**
 * 自定义 SemanticsMatchers
 * 用于 Compose UI 测试
 */
object CustomSemanticsMatchers {
    
    /**
     * 匹配包含文本的元素
     */
    fun hasTextContaining(text: String, substring: Boolean = true): SemanticsMatcher {
        return if (substring) {
            SemanticsMatcher("has text containing '$text'") { node ->
                node.config.find {
                    it.name == "Text"
                }?.value?.toString()?.contains(text) == true
            }
        } else {
            hasText(text)
        }
    }
    
    /**
     * 匹配启用的元素
     */
    fun isEnabled(): SemanticsMatcher {
        return SemanticsMatcher("is enabled") { node ->
            node.config.find {
                it.name == "Enabled"
            }?.value == true
        }
    }
    
    /**
     * 匹配禁用的元素
     */
    fun isDisabled(): SemanticsMatcher {
        return SemanticsMatcher("is disabled") { node ->
            node.config.find {
                it.name == "Enabled"
            }?.value == false
        }
    }
    
    /**
     * 匹配选中状态的元素
     */
    fun isSelected(): SemanticsMatcher {
        return SemanticsMatcher("is selected") { node ->
            node.config.find {
                it.name == "Selected"
            }?.value == true
        }
    }
    
    /**
     * 匹配可见的元素
     */
    fun isVisible(): SemanticsMatcher {
        return SemanticsMatcher("is visible") { node ->
            node.config.find {
                it.name == "Visible"
            }?.value != false
        }
    }
    
    /**
     * 匹配有占位符文本的元素
     */
    fun hasPlaceholder(text: String): SemanticsMatcher {
        return SemanticsMatcher("has placeholder '$text'") { node ->
            node.config.find {
                it.name == "Placeholder"
            }?.value?.toString()?.contains(text) == true
        }
    }
    
    /**
     * 匹配有错误状态的元素
     */
    fun hasError(): SemanticsMatcher {
        return SemanticsMatcher("has error") { node ->
            node.config.find {
                it.name == "Error"
            }?.value != null
        }
    }
}

/**
 * 测试标签管理
 */
object TestTags {
    // 会话列表
    const val SESSION_LIST = "session_list"
    const val SESSION_ITEM = "session_item"
    const val SESSION_TITLE = "session_title"
    const val SESSION_TIME = "session_time"
    
    // 消息
    const val MESSAGE_LIST = "message_list"
    const val MESSAGE_ITEM = "message_item"
    const val MESSAGE_CONTENT = "message_content"
    const val MESSAGE_TIME = "message_time"
    
    // 输入
    const val INPUT_BAR = "input_bar"
    const val INPUT_FIELD = "input_field"
    const val SEND_BUTTON = "send_button"
    const val ATTACH_BUTTON = "attach_button"
    const val VOICE_BUTTON = "voice_button"
    
    // 按钮
    const val DELETE_BUTTON = "delete_button"
    const val CANCEL_BUTTON = "cancel_button"
    const val CONFIRM_BUTTON = "confirm_button"
    const val BACK_BUTTON = "back_button"
    
    // 状态
    const val LOADING_INDICATOR = "loading_indicator"
    const val ERROR_MESSAGE = "error_message"
    const val EMPTY_STATE = "empty_state"
    
    // 对话框
    const val DIALOG = "dialog"
    const val DIALOG_TITLE = "dialog_title"
    const val DIALOG_MESSAGE = "dialog_message"
}