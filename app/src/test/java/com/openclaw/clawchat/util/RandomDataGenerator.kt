package com.openclaw.clawchat.util

import kotlin.random.Random

/**
 * 随机数据生成器
 * 用于生成测试数据
 */
object RandomDataGenerator {
    
    private val random = Random.Default
    
    /**
     * 生成随机字符串
     */
    fun randomString(length: Int = 10): String {
        val chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return (1..length)
            .map { chars.random(random) }
            .joinToString("")
    }
    
    /**
     * 生成随机中文
     */
    fun randomChinese(length: Int = 10): String {
        return (1..length)
            .map { random.nextInt(0x4E00, 0x9FFF).toChar() }
            .joinToString("")
    }
    
    /**
     * 生成随机整数
     */
    fun randomInt(min: Int = Int.MIN_VALUE, max: Int = Int.MAX_VALUE): Int {
        return random.nextInt(min, max)
    }
    
    /**
     * 生成随机长整数
     */
    fun randomLong(min: Long = Long.MIN_VALUE, max: Long = Long.MAX_VALUE): Long {
        return random.nextLong(min, max)
    }
    
    /**
     * 生成随机浮点数
     */
    fun randomFloat(min: Float = 0f, max: Float = 1f): Float {
        return random.nextFloat() * (max - min) + min
    }
    
    /**
     * 生成随机双精度
     */
    fun randomDouble(min: Double = 0.0, max: Double = 1.0): Double {
        return random.nextDouble(min, max)
    }
    
    /**
     * 生成随机布尔值
     */
    fun randomBoolean(): Boolean = random.nextBoolean()
    
    /**
     * 生成随机邮箱
     */
    fun randomEmail(): String {
        val domains = listOf("gmail.com", "yahoo.com", "outlook.com", "example.com")
        return "${randomString(8).lowercase()}@${domains.random()}"
    }
    
    /**
     * 生成随机手机号（中国大陆）
     */
    fun randomPhone(): String {
        val prefixes = listOf("138", "139", "150", "151", "152", "188", "189")
        val suffix = (0..8).map { random.nextInt(0, 10) }.joinToString("")
        return "${prefixes.random()}$suffix"
    }
    
    /**
     * 生成随机 IP 地址
     */
    fun randomIp(): String {
        return "${randomInt(1, 255)}.${randomInt(0, 255)}." +
               "${randomInt(0, 255)}.${randomInt(1, 255)}"
    }
    
    /**
     * 生成随机端口
     */
    fun randomPort(): Int {
        return randomInt(1024, 65535)
    }
    
    /**
     * 生成随机 URL
     */
    fun randomUrl(): String {
        return "https://${randomString(8)}.com/${randomString(5)}"
    }
    
    /**
     * 生成随机 UUID
     */
    fun randomUuid(): String {
        return java.util.UUID.randomUUID().toString()
    }
    
    /**
     * 从列表中随机选择
     */
    fun <T> randomFrom(list: List<T>): T {
        return list.random(random)
    }
    
    /**
     * 生成随机列表
     */
    fun <T> randomList(size: Int = 10, generator: () -> T): List<T> {
        return List(size) { generator() }
    }
    
    /**
     * 随机打乱列表
     */
    fun <T> shuffle(list: List<T>): List<T> {
        return list.shuffled(random)
    }
}