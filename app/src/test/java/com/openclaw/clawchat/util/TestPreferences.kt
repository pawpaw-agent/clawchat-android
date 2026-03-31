package com.openclaw.clawchat.util

import android.content.Context
import android.content.SharedPreferences
import org.mockito.Mockito

/**
 * 测试用 Context 模拟
 */
object TestContext {
    
    /**
     * 创建模拟 Context
     */
    fun createMockContext(): Context {
        return Mockito.mock(Context::class.java)
    }
    
    /**
     * 创建模拟 SharedPreferences
     */
    fun createMockSharedPreferences(): SharedPreferences {
        return Mockito.mock(SharedPreferences::class.java)
    }
}

/**
 * 测试用 Preferences
 * 提供内存存储
 */
class TestPreferences : SharedPreferences {
    private val data = mutableMapOf<String, Any?>()
    private val listeners = mutableListOf<SharedPreferences.OnSharedPreferenceChangeListener>()
    
    override fun getAll(): Map<String, *> = data.toMap()
    
    override fun getString(key: String, defValue: String?): String? {
        return data[key] as? String ?: defValue
    }
    
    override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? {
        @Suppress("UNCHECKED_CAST")
        return data[key] as? Set<String> ?: defValues
    }
    
    override fun getInt(key: String, defValue: Int): Int {
        return data[key] as? Int ?: defValue
    }
    
    override fun getLong(key: String, defValue: Long): Long {
        return data[key] as? Long ?: defValue
    }
    
    override fun getFloat(key: String, defValue: Float): Float {
        return data[key] as? Float ?: defValue
    }
    
    override fun getBoolean(key: String, defValue: Boolean): Boolean {
        return data[key] as? Boolean ?: defValue
    }
    
    override fun contains(key: String): Boolean = data.containsKey(key)
    
    override fun edit(): SharedPreferences.Editor {
        return TestEditor(data, listeners)
    }
    
    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener
    ) {
        listeners.add(listener)
    }
    
    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener
    ) {
        listeners.remove(listener)
    }
    
    /**
     * 测试用 Editor
     */
    private class TestEditor(
        private val data: MutableMap<String, Any?>,
        private val listeners: MutableList<SharedPreferences.OnSharedPreferenceChangeListener>
    ) : SharedPreferences.Editor {
        
        private val changes = mutableMapOf<String, Any?>()
        private val removes = mutableSetOf<String>()
        private var clearAll = false
        
        override fun putString(key: String, value: String?): SharedPreferences.Editor {
            changes[key] = value
            return this
        }
        
        override fun putStringSet(
            key: String,
            values: Set<String>?
        ): SharedPreferences.Editor {
            changes[key] = values
            return this
        }
        
        override fun putInt(key: String, value: Int): SharedPreferences.Editor {
            changes[key] = value
            return this
        }
        
        override fun putLong(key: String, value: Long): SharedPreferences.Editor {
            changes[key] = value
            return this
        }
        
        override fun putFloat(key: String, value: Float): SharedPreferences.Editor {
            changes[key] = value
            return this
        }
        
        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor {
            changes[key] = value
            return this
        }
        
        override fun remove(key: String): SharedPreferences.Editor {
            removes.add(key)
            return this
        }
        
        override fun clear(): SharedPreferences.Editor {
            clearAll = true
            return this
        }
        
        override fun commit(): Boolean {
            applyChanges()
            return true
        }
        
        override fun apply() {
            applyChanges()
        }
        
        private fun applyChanges() {
            if (clearAll) {
                data.clear()
            }
            
            removes.forEach { key ->
                data.remove(key)
            }
            
            data.putAll(changes)
            
            // 通知监听器
            changes.keys.forEach { key ->
                listeners.forEach { listener ->
                    listener.onSharedPreferenceChanged(null, key)
                }
            }
        }
    }
}