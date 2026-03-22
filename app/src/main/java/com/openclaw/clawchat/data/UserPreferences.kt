package com.openclaw.clawchat.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

/**
 * 字体大小枚举
 */
enum class FontSize(val value: Int, val displayName: String) {
    SMALL(0, "小"),
    MEDIUM(1, "中"),
    LARGE(2, "大");
    
    companion object {
        fun fromValue(value: Int): FontSize {
            return values().find { it.value == value } ?: MEDIUM
        }
    }
}

/**
 * 用户偏好设置管理
 */
@Singleton
class UserPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val MESSAGE_FONT_SIZE = intPreferencesKey("message_font_size")
    }
    
    /**
     * 消息字体大小（统一设置）
     */
    val messageFontSize: Flow<FontSize> = context.dataStore.data
        .map { preferences ->
            val value = preferences[MESSAGE_FONT_SIZE] ?: FontSize.MEDIUM.value
            FontSize.fromValue(value)
        }
    
    /**
     * 设置消息字体大小
     */
    suspend fun setMessageFontSize(fontSize: FontSize) {
        context.dataStore.edit { preferences ->
            preferences[MESSAGE_FONT_SIZE] = fontSize.value
        }
    }
}