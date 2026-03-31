package com.openclaw.clawchat.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.themeDataStore: DataStore<Preferences> by preferencesDataStore(name = "theme_preferences")

/**
 * 主题偏好存储
 * 使用 DataStore 持久化主题配置
 */
class ThemePreferencesStore(private val context: Context) {
    
    companion object {
        private val THEME_COLOR_KEY = intPreferencesKey("theme_color_index")
        private val DARK_MODE_KEY = booleanPreferencesKey("dark_mode")
        private val USE_SYSTEM_THEME_KEY = booleanPreferencesKey("use_system_theme")
        
        // 默认主题色索引
        const val DEFAULT_THEME_COLOR = 0  // Blue
        
        // 主题色数量
        const val THEME_COLOR_COUNT = 8
    }
    
    /**
     * 获取主题色索引
     */
    val themeColorIndex: Flow<Int> = context.themeDataStore.data
        .map { preferences ->
            preferences[THEME_COLOR_KEY] ?: DEFAULT_THEME_COLOR
        }
    
    /**
     * 获取是否深色模式
     */
    val isDarkMode: Flow<Boolean> = context.themeDataStore.data
        .map { preferences ->
            preferences[DARK_MODE_KEY] ?: false
        }
    
    /**
     * 获取是否跟随系统主题
     */
    val useSystemTheme: Flow<Boolean> = context.themeDataStore.data
        .map { preferences ->
            preferences[USE_SYSTEM_THEME_KEY] ?: true
        }
    
    /**
     * 设置主题色
     */
    suspend fun setThemeColor(index: Int) {
        val safeIndex = index.coerceIn(0, THEME_COLOR_COUNT - 1)
        context.themeDataStore.edit { preferences ->
            preferences[THEME_COLOR_KEY] = safeIndex
        }
    }
    
    /**
     * 设置深色模式
     */
    suspend fun setDarkMode(enabled: Boolean) {
        context.themeDataStore.edit { preferences ->
            preferences[DARK_MODE_KEY] = enabled
        }
    }
    
    /**
     * 设置是否跟随系统主题
     */
    suspend fun setUseSystemTheme(useSystem: Boolean) {
        context.themeDataStore.edit { preferences ->
            preferences[USE_SYSTEM_THEME_KEY] = useSystem
        }
    }
    
    /**
     * 清除所有主题偏好（恢复默认）
     */
    suspend fun clearAll() {
        context.themeDataStore.edit { preferences ->
            preferences.remove(THEME_COLOR_KEY)
            preferences.remove(DARK_MODE_KEY)
            preferences.remove(USE_SYSTEM_THEME_KEY)
        }
    }
}