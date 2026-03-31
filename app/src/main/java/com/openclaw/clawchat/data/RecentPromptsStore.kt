package com.openclaw.clawchat.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray

private val Context.promptDataStore: DataStore<Preferences> by preferencesDataStore(name = "recent_prompts")

/**
 * 最近提示词存储
 * 使用 DataStore 持久化用户最近使用的提示词
 */
class RecentPromptsStore(private val context: Context) {
    
    companion object {
        private val RECENT_PROMPTS_KEY = stringPreferencesKey("recent_prompts")
        private const val MAX_PROMPTS = 20  // 最多保存 20 条
    }
    
    /**
     * 获取最近提示词列表
     */
    val recentPrompts: Flow<List<String>> = context.promptDataStore.data
        .map { preferences ->
            val json = preferences[RECENT_PROMPTS_KEY] ?: "[]"
            try {
                val jsonArray = JSONArray(json)
                (0 until jsonArray.length()).map { jsonArray.getString(it) }
            } catch (e: Exception) {
                emptyList()
            }
        }
    
    /**
     * 添加提示词到最近列表
     */
    suspend fun addPrompt(prompt: String) {
        if (prompt.isBlank()) return
        
        context.promptDataStore.edit { preferences ->
            val currentList = preferences[RECENT_PROMPTS_KEY]?.let { json ->
                try {
                    val jsonArray = JSONArray(json)
                    (0 until jsonArray.length()).map { jsonArray.getString(it) }
                } catch (e: Exception) {
                    emptyList()
                }
            } ?: emptyList()
            
            // 移除重复项，添加到开头
            val newList = (listOf(prompt) + currentList.filter { it != prompt })
                .take(MAX_PROMPTS)
            
            val jsonArray = JSONArray(newList)
            preferences[RECENT_PROMPTS_KEY] = jsonArray.toString()
        }
    }
    
    /**
     * 删除提示词
     */
    suspend fun removePrompt(prompt: String) {
        context.promptDataStore.edit { preferences ->
            val currentList = preferences[RECENT_PROMPTS_KEY]?.let { json ->
                try {
                    val jsonArray = JSONArray(json)
                    (0 until jsonArray.length()).map { jsonArray.getString(it) }
                } catch (e: Exception) {
                    emptyList()
                }
            } ?: emptyList()
            
            val newList = currentList.filter { it != prompt }
            val jsonArray = JSONArray(newList)
            preferences[RECENT_PROMPTS_KEY] = jsonArray.toString()
        }
    }
    
    /**
     * 清空所有提示词
     */
    suspend fun clearAll() {
        context.promptDataStore.edit { preferences ->
            preferences[RECENT_PROMPTS_KEY] = "[]"
        }
    }
}