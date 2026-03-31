package com.openclaw.clawchat.util

/**
 * 集合工具类
 * 提供集合处理方法
 */
object CollectionUtils {
    
    /**
     * 判断集合是否为空
     */
    fun <T> isEmpty(collection: Collection<T>?): Boolean {
        return collection.isNullOrEmpty()
    }
    
    /**
     * 判断集合是否不为空
     */
    fun <T> isNotEmpty(collection: Collection<T>?): Boolean {
        return !isEmpty(collection)
    }
    
    /**
     * 安全获取列表元素
     * @param list 列表
     * @param index 索引
     * @param defaultValue 默认值
     * @return 元素或默认值
     */
    fun <T> getOrNull(list: List<T>?, index: Int, defaultValue: T? = null): T? {
        if (list == null || index < 0 || index >= list.size) return defaultValue
        return list[index]
    }
    
    /**
     * 安全获取列表第一个元素
     */
    fun <T> firstOrNull(list: List<T>?): T? {
        return list?.firstOrNull()
    }
    
    /**
     * 安全获取列表最后一个元素
     */
    fun <T> lastOrNull(list: List<T>?): T? {
        return list?.lastOrNull()
    }
    
    /**
     * 分批处理
     * @param list 列表
     * @param batchSize 每批大小
     * @return 分批后的列表
     */
    fun <T> chunked(list: List<T>, batchSize: Int): List<List<T>> {
        if (batchSize <= 0) return listOf(list)
        return list.chunked(batchSize)
    }
    
    /**
     * 列表去重（保留顺序）
     */
    fun <T> distinct(list: List<T>): List<T> {
        return list.distinct()
    }
    
    /**
     * 根据 key 去重
     */
    fun <T, K> distinctBy(list: List<T>, selector: (T) -> K): List<T> {
        return list.distinctBy(selector)
    }
    
    /**
     * 过滤空元素
     */
    fun <T : Any> filterNotNull(list: List<T?>): List<T> {
        return list.filterNotNull()
    }
    
    /**
     * 连接为字符串
     */
    fun <T> joinToString(
        collection: Collection<T>?,
        separator: String = ", ",
        prefix: String = "",
        postfix: String = "",
        transform: ((T) -> String)? = null
    ): String {
        return collection?.joinToString(separator, prefix, postfix, transform = transform) ?: ""
    }
}