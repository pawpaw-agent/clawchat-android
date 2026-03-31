package com.openclaw.clawchat.util

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry

/**
 * 测试用 LifecycleOwner
 * 提供可控的生命周期
 */
class TestLifecycleOwner : LifecycleOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    
    override val lifecycle: Lifecycle
        get() = lifecycleRegistry
    
    /**
     * 设置生命周期状态
     */
    fun setState(state: Lifecycle.State) {
        lifecycleRegistry.currentState = state
    }
    
    /**
     * 模拟 onCreate
     */
    fun create() {
        setState(Lifecycle.State.CREATED)
    }
    
    /**
     * 模拟 onStart
     */
    fun start() {
        setState(Lifecycle.State.STARTED)
    }
    
    /**
     * 模拟 onResume
     */
    fun resume() {
        setState(Lifecycle.State.RESUMED)
    }
    
    /**
     * 模拟 onPause
     */
    fun pause() {
        setState(Lifecycle.State.STARTED)
    }
    
    /**
     * 模拟 onStop
     */
    fun stop() {
        setState(Lifecycle.State.CREATED)
    }
    
    /**
     * 模拟 onDestroy
     */
    fun destroy() {
        setState(Lifecycle.State.DESTROYED)
    }
    
    companion object {
        /**
         * 创建已初始化的 LifecycleOwner
         */
        fun createInitialized(): TestLifecycleOwner {
            return TestLifecycleOwner().apply {
                create()
                start()
                resume()
            }
        }
        
        /**
         * 创建已销毁的 LifecycleOwner
         */
        fun createDestroyed(): TestLifecycleOwner {
            return TestLifecycleOwner().apply {
                create()
                destroy()
            }
        }
    }
}