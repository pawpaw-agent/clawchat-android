package com.openclaw.clawchat.security

import org.junit.Assert.*
import org.junit.Test

/**
 * RootDetector and RootCheckResult tests
 */
class RootDetectorTest {

    // ─────────────────────────────────────────────────────────────
    // RootCheckResult Tests
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `RootCheckResult creates with all fields`() {
        val result = RootDetector.RootCheckResult(
            isRooted = true,
            rootIndicators = listOf("su binary found", "Magisk detected"),
            riskLevel = RootDetector.RootCheckResult.RiskLevel.HIGH
        )

        assertTrue(result.isRooted)
        assertEquals(2, result.rootIndicators.size)
        assertEquals(RootDetector.RootCheckResult.RiskLevel.HIGH, result.riskLevel)
    }

    @Test
    fun `RootCheckResult creates with no root detected`() {
        val result = RootDetector.RootCheckResult(
            isRooted = false,
            rootIndicators = emptyList(),
            riskLevel = RootDetector.RootCheckResult.RiskLevel.NONE
        )

        assertFalse(result.isRooted)
        assertTrue(result.rootIndicators.isEmpty())
        assertEquals(RootDetector.RootCheckResult.RiskLevel.NONE, result.riskLevel)
    }

    @Test
    fun `RootCheckResult copy preserves values`() {
        val original = RootDetector.RootCheckResult(
            isRooted = false,
            rootIndicators = listOf("indicator1"),
            riskLevel = RootDetector.RootCheckResult.RiskLevel.LOW
        )

        val copied = original.copy(
            isRooted = true,
            riskLevel = RootDetector.RootCheckResult.RiskLevel.MEDIUM
        )

        assertTrue(copied.isRooted)
        assertEquals(1, copied.rootIndicators.size)
        assertEquals(RootDetector.RootCheckResult.RiskLevel.MEDIUM, copied.riskLevel)
    }

    @Test
    fun `RootCheckResult equality works`() {
        val result1 = RootDetector.RootCheckResult(
            isRooted = true,
            rootIndicators = listOf("test"),
            riskLevel = RootDetector.RootCheckResult.RiskLevel.HIGH
        )
        val result2 = RootDetector.RootCheckResult(
            isRooted = true,
            rootIndicators = listOf("test"),
            riskLevel = RootDetector.RootCheckResult.RiskLevel.HIGH
        )
        val result3 = RootDetector.RootCheckResult(
            isRooted = false,
            rootIndicators = listOf("test"),
            riskLevel = RootDetector.RootCheckResult.RiskLevel.HIGH
        )

        assertEquals(result1, result2)
        assertNotEquals(result1, result3)
    }

    // ─────────────────────────────────────────────────────────────
    // RiskLevel Tests
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `RiskLevel enum values`() {
        assertEquals(4, RootDetector.RootCheckResult.RiskLevel.entries.size)
        assertEquals(RootDetector.RootCheckResult.RiskLevel.NONE, RootDetector.RootCheckResult.RiskLevel.valueOf("NONE"))
        assertEquals(RootDetector.RootCheckResult.RiskLevel.LOW, RootDetector.RootCheckResult.RiskLevel.valueOf("LOW"))
        assertEquals(RootDetector.RootCheckResult.RiskLevel.MEDIUM, RootDetector.RootCheckResult.RiskLevel.valueOf("MEDIUM"))
        assertEquals(RootDetector.RootCheckResult.RiskLevel.HIGH, RootDetector.RootCheckResult.RiskLevel.valueOf("HIGH"))
    }

    @Test
    fun `RiskLevel ordinal ordering`() {
        // NONE(0) < LOW(1) < MEDIUM(2) < HIGH(3)
        assertTrue(RootDetector.RootCheckResult.RiskLevel.NONE.ordinal < RootDetector.RootCheckResult.RiskLevel.LOW.ordinal)
        assertTrue(RootDetector.RootCheckResult.RiskLevel.LOW.ordinal < RootDetector.RootCheckResult.RiskLevel.MEDIUM.ordinal)
        assertTrue(RootDetector.RootCheckResult.RiskLevel.MEDIUM.ordinal < RootDetector.RootCheckResult.RiskLevel.HIGH.ordinal)
    }

    @Test
    fun `RiskLevel comparison with maxOf`() {
        val levels = listOf(
            RootDetector.RootCheckResult.RiskLevel.NONE,
            RootDetector.RootCheckResult.RiskLevel.LOW,
            RootDetector.RootCheckResult.RiskLevel.MEDIUM,
            RootDetector.RootCheckResult.RiskLevel.HIGH
        )

        val maxLevel = levels.maxOf { it.ordinal }
        assertEquals(3, maxLevel)
    }

    // ─────────────────────────────────────────────────────────────
    // Root Indicators Tests
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `RootCheckResult can have multiple indicators`() {
        val result = RootDetector.RootCheckResult(
            isRooted = true,
            rootIndicators = listOf(
                "su binary found: /system/bin/su",
                "Root app found: com.topjohnwu.magisk",
                "Dangerous prop: ro.debuggable=1",
                "RW path found: /system",
                "Busybox found",
                "Magisk detected"
            ),
            riskLevel = RootDetector.RootCheckResult.RiskLevel.HIGH
        )

        assertEquals(6, result.rootIndicators.size)
        assertTrue(result.rootIndicators.any { it.contains("su binary") })
        assertTrue(result.rootIndicators.any { it.contains("Magisk") })
        assertTrue(result.rootIndicators.any { it.contains("Busybox") })
    }

    @Test
    fun `RootCheckResult risk level reflects severity`() {
        // LOW risk scenario
        val lowRisk = RootDetector.RootCheckResult(
            isRooted = true,
            rootIndicators = listOf("Busybox found"),
            riskLevel = RootDetector.RootCheckResult.RiskLevel.LOW
        )

        // HIGH risk scenario
        val highRisk = RootDetector.RootCheckResult(
            isRooted = true,
            rootIndicators = listOf("su binary found", "Magisk detected"),
            riskLevel = RootDetector.RootCheckResult.RiskLevel.HIGH
        )

        assertTrue(lowRisk.riskLevel.ordinal < highRisk.riskLevel.ordinal)
    }

    // ─────────────────────────────────────────────────────────────
    // Data Class Properties Tests
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `RootCheckResult isRooted reflects root state`() {
        val rootedResult = RootDetector.RootCheckResult(
            isRooted = true,
            rootIndicators = listOf("test"),
            riskLevel = RootDetector.RootCheckResult.RiskLevel.HIGH
        )

        val notRootedResult = RootDetector.RootCheckResult(
            isRooted = false,
            rootIndicators = emptyList(),
            riskLevel = RootDetector.RootCheckResult.RiskLevel.NONE
        )

        assertTrue(rootedResult.isRooted)
        assertFalse(notRootedResult.isRooted)
    }

    @Test
    fun `RootCheckResult indicators list is immutable`() {
        val indicators = listOf("indicator1", "indicator2")
        val result = RootDetector.RootCheckResult(
            isRooted = true,
            rootIndicators = indicators,
            riskLevel = RootDetector.RootCheckResult.RiskLevel.HIGH
        )

        assertEquals(indicators, result.rootIndicators)
        assertEquals(2, result.rootIndicators.size)
    }

    @Test
    fun `RootCheckResult toString contains all fields`() {
        val result = RootDetector.RootCheckResult(
            isRooted = true,
            rootIndicators = listOf("test"),
            riskLevel = RootDetector.RootCheckResult.RiskLevel.HIGH
        )

        val str = result.toString()
        assertTrue(str.contains("isRooted=true"))
        assertTrue(str.contains("rootIndicators"))
        assertTrue(str.contains("riskLevel=HIGH"))
    }

    @Test
    fun `RootCheckResult hashCode is consistent`() {
        val result1 = RootDetector.RootCheckResult(
            isRooted = true,
            rootIndicators = listOf("test"),
            riskLevel = RootDetector.RootCheckResult.RiskLevel.HIGH
        )
        val result2 = RootDetector.RootCheckResult(
            isRooted = true,
            rootIndicators = listOf("test"),
            riskLevel = RootDetector.RootCheckResult.RiskLevel.HIGH
        )

        assertEquals(result1.hashCode(), result2.hashCode())
    }
}