package com.openclaw.clawchat.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject

private val Context.draftDataStore: DataStore<Preferences> by preferencesDataStore(name = "message_drafts")

/**
 * 消息草稿存储
 * 自动保存和恢复未发送的消息
 */
class DraftStore(private val context: Context) {
    
    companion object {
        // 草稿数据键前缀
        private fun draftKey(sessionId: String) = stringPreferencesKey("draft_$sessionId")
        private fun draftTimeKey(sessionId: String) = longPreferencesKey("draft_time_$sessionId")
        
        // 草稿过期时间（24小时）
        private const val DRAFT_EXPIRY_MS = 24 * 60 * 60 * 1000L
    }
    
    /**
     * 获取会话草稿
     */
    fun getDraft(sessionId: String): Flow<DraftData?> = context.draftDataStore.data
        .map { preferences ->
            val content = preferences[draftKey(sessionId)]
            val timestamp = preferences[draftTimeKey(sessionId)]
            
            if (content != null && timestamp != null) {
                // 检查是否过期
                val now = System.currentTimeMillis()
                if (now - timestamp < DRAFT_EXPIRY_MS) {
                    try {
                        val json = JSONObject(content)
                        DraftData(
                            sessionId = sessionId,
                            text = json.optString("text", ""),
                            attachments = json.optString("attachments", ""),
                            timestamp = timestamp
                        )
                    } catch (e: Exception) {
                        null
                    }
                } else {
                    null
                }
            } else {
                null
            }
        }
    
    /**
     * 保存草稿
     */
    suspend fun saveDraft(sessionId: String, text: String, attachments: String = "") {
        if (text.isBlank() && attachments.isBlank()) {
            // 清空草稿
            clearDraft(sessionId)
            return
        }
        
        context.draftDataStore.edit { preferences ->
            val json = JSONObject()
            json.put("text", text)
            json.put("attachments", attachments)
            
            preferences[draftKey(sessionId)] = json.toString()
            preferences[draftTimeKey(sessionId)] = System.currentTimeMillis()
        }
    }
    
    /**
     * 清空草稿
     */
    suspend fun clearDraft(sessionId: String) {
        context.draftDataStore.edit { preferences ->
            preferences.remove(draftKey(sessionId))
            preferences.remove(draftTimeKey(sessionId))
        }
    }
    
    /**
     * 清空所有过期草稿
     */
    suspend fun clearExpiredDrafts() {
        val now = System.currentTimeMillis()
        context.draftDataStore.edit { preferences ->
            preferences.asMap().forEach { (key, value) ->
                if (key is longPreferencesKey && key.name.startsWith("draft_time_")) {
                    val timestamp = value as Long
                    if (now - timestamp >= DRAFT_EXPIRY_MS) {
                        val sessionId = key.name.removePrefix("draft_time_")
                        preferences.remove(draftKey(sessionId))
                        preferences.remove(draftTimeKey(sessionId))
                    }
                }
            }
        }
    }
    
    /**
     * 获取所有草稿数量
     */
    fun getDraftCount(): Flow<Int> = context.draftDataStore.data
        .map { preferences ->
            preferences.asMap().keys
                .count { it.name.startsWith("draft_") && !it.name.startsWith("draft_time_") }
        }
}

/**
 * 草稿数据
 */
data class DraftData(
    val sessionId: String,
    val text: String,
    val attachments: String,
    val timestamp: Long
) {
    fun formatTime(): String {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(timestamp))
    }
}