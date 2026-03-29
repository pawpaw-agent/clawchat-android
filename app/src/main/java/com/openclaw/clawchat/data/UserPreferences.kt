package com.openclaw.clawchat.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
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
 * 主题模式枚举
 */
enum class ThemeMode(val value: Int, val displayName: String) {
    LIGHT(0, "浅色"),
    DARK(1, "深色"),
    SYSTEM(2, "跟随系统");

    companion object {
        fun fromValue(value: Int): ThemeMode {
            return values().find { it.value == value } ?: SYSTEM
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
        private val THEME_MODE = intPreferencesKey("theme_mode")
        private val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
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

    /**
     * 动态颜色（Android 12+ Material You）
     */
    val dynamicColor: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[DYNAMIC_COLOR] ?: true // 默认开启
        }
    
    /**
     * 设置动态颜色
     */
    suspend fun setDynamicColor(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[DYNAMIC_COLOR] = enabled
        }
    }

    /**
     * 主题模式
     */
    val themeMode: Flow<ThemeMode> = context.dataStore.data
        .map { preferences ->
            val value = preferences[THEME_MODE] ?: ThemeMode.SYSTEM.value
            ThemeMode.fromValue(value)
        }

    /**
     * 设置主题模式
     */
    suspend fun setThemeMode(themeMode: ThemeMode) {
        context.dataStore.edit { preferences ->
            preferences[THEME_MODE] = themeMode.value
        }
    }
}