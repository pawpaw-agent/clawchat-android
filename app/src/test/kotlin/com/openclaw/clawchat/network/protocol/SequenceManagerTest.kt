package com.openclaw.clawchat.network.protocol

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * SequenceManager 单元测试
 * 
 * 测试覆盖：
 * 1. EventDeduplicator.isAlreadySeen() 正确识别重复事件
 * 2. 序列号验证逻辑（正常/重复/过时/间隙）
 * 3. fallbackToDestructiveMigration 的 UUID 去重
 */
class SequenceManagerTest {

    private lateinit var sequenceManager: SequenceManager
    private lateinit var eventDeduplicator: EventDeduplicator

    @Before
    fun setup() {
        sequenceManager = SequenceManager(maxGapSize = 100)
        eventDeduplicator = EventDeduplicator(maxHistorySize = 1000)
    }

    // ==================== EventDeduplicator 测试 ====================

    @Test
    fun `isAlreadySeen should return false for new event with eventId`() = runBlocking {
        // Given
        val eventId = "event-123"

        // When
        val first = eventDeduplicator.isAlreadySeen(eventId, null)
        val second = eventDeduplicator.isAlreadySeen(eventId, null)

        // Then
        assertFalse("First check should return false (new event)", first)
        assertTrue("Second check should return true (duplicate)", second)
    }

    @Test
    fun `isAlreadySeen should return false for new event with seq`() = runBlocking {
        // Given
        val seq = 42

        // When
        val first = eventDeduplicator.isAlreadySeen(null, seq)
        val second = eventDeduplicator.isAlreadySeen(null, seq)

        // Then
        assertFalse("First check should return false (new event)", first)
        assertTrue("Second check should return true (duplicate)", second)
    }

    @Test
    fun `isAlreadySeen should return false for new event with both eventId and seq`() = runBlocking {
        // Given
        val eventId = "event-456"
        val seq = 100

        // When
        val first = eventDeduplicator.isAlreadySeen(eventId, seq)
        val second = eventDeduplicator.isAlreadySeen(eventId, seq)

        // Then
        assertFalse("First check should return false", first)
        assertTrue("Second check should return true (duplicate)", second)
    }

    @Test
    fun `isAlreadySeen should return false when both eventId and seq are null`() = runBlocking {
        // Given & When
        val result = eventDeduplicator.isAlreadySeen(null, null)

        // Then - null identifiers are not tracked, always return false
        assertFalse("Null identifiers should not be tracked", result)
    }

    @Test
    fun `isAlreadySeen should track eventId and seq independently`() = runBlocking {
        // Given
        val eventId = "event-789"
        val seq1 = 10
        val seq2 = 20

        // When - track eventId
        eventDeduplicator.isAlreadySeen(eventId, seq1)

        // Then - same eventId with different seq is duplicate
        assertTrue("Same eventId should be duplicate", 
            eventDeduplicator.isAlreadySeen(eventId, seq2))
    }

    @Test
    fun `isAlreadySeen should handle history cleanup when exceeding maxHistorySize`() = runBlocking {
        // Given
        val deduplicator = EventDeduplicator(maxHistorySize = 10)

        // When - add more than maxHistorySize events
        for (i in 1..20) {
            deduplicator.isAlreadySeen("event-$i", i)
        }

        // Then - should still work (cleanup happened)
        val result = deduplicator.isAlreadySeen("event-21", 21)
        assertFalse("Should accept new events after cleanup", result)
    }

    @Test
    fun `clear should remove all tracked events`() = runBlocking {
        // Given
        eventDeduplicator.isAlreadySeen("event-1", 1)
        eventDeduplicator.isAlreadySeen("event-2", 2)

        // When
        eventDeduplicator.clear()

        // Then - events should be forgotten
        assertFalse("After clear, event-1 should not be seen", 
            eventDeduplicator.isAlreadySeen("event-1", 1))
        assertFalse("After clear, event-2 should not be seen", 
            eventDeduplicator.isAlreadySeen("event-2", 2))
    }

    // ==================== SequenceManager 序列号验证测试 ====================

    @Test
    fun `checkSequence should return Ok for null seq`() = runBlocking {
        // When
        val result = sequenceManager.checkSequence(null)

        // Then
        assertTrue("Null seq should return Ok", result is SequenceManager.SequenceResult.Ok)
    }

    @Test
    fun `checkSequence should return Ok for first sequence`() = runBlocking {
        // When
        val result = sequenceManager.checkSequence(1)

        // Then
        assertTrue("First sequence should return Ok", result is SequenceManager.SequenceResult.Ok)
    }

    @Test
    fun `checkSequence should return Duplicate for already acknowledged seq`() = runBlocking {
        // Given
        sequenceManager.acknowledge(5)

        // When
        val result = sequenceManager.checkSequence(5)

        // Then
        assertTrue("Acknowledged seq should return Duplicate", 
            result is SequenceManager.SequenceResult.Duplicate)
    }

    @Test
    fun `checkSequence should return Old for seq less than current`() = runBlocking {
        // Given
        sequenceManager.acknowledge(10)

        // When
        val result = sequenceManager.checkSequence(5)

        // Then
        assertTrue("Older seq should return Old", result is SequenceManager.SequenceResult.Old)
        assertEquals(5, (result as SequenceManager.SequenceResult.Old).received)
        assertEquals(10, result.current)
    }

    @Test
    fun `checkSequence should return Gap for seq with missing events`() = runBlocking {
        // Given
        sequenceManager.acknowledge(5)

        // When - receive seq 8 (missing 6, 7)
        val result = sequenceManager.checkSequence(8)

        // Then
        assertTrue("Gap should be detected", result is SequenceManager.SequenceResult.Gap)
        val gap = result as SequenceManager.SequenceResult.Gap
        assertEquals(6, gap.expected)
        assertEquals(8, gap.received)
        assertEquals(listOf(6, 7), gap.missing)
    }

    @Test
    fun `checkSequence should return Ok for consecutive seq`() = runBlocking {
        // Given
        sequenceManager.acknowledge(5)

        // When - receive next consecutive seq
        val result = sequenceManager.checkSequence(6)

        // Then
        assertTrue("Consecutive seq should return Ok", result is SequenceManager.SequenceResult.Ok)
    }

    @Test
    fun `checkSequence should reset for gap larger than maxGapSize`() = runBlocking {
        // Given
        val manager = SequenceManager(maxGapSize = 10)
        manager.acknowledge(5)

        // When - receive seq 100 (gap of 94, larger than maxGapSize 10)
        val result = manager.checkSequence(100)

        // Then
        assertTrue("Large gap should return Ok (reset)", result is SequenceManager.SequenceResult.Ok)
        assertEquals(100, manager.getCurrentSeq())
    }

    @Test
    fun `acknowledge should update currentSeq`() = runBlocking {
        // Given & When
        sequenceManager.acknowledge(50)

        // Then
        assertEquals(50, sequenceManager.getCurrentSeq())
    }

    @Test
    fun `acknowledge should not decrease currentSeq`() = runBlocking {
        // Given
        sequenceManager.acknowledge(50)

        // When - acknowledge older seq
        sequenceManager.acknowledge(30)

        // Then
        assertEquals(50, sequenceManager.getCurrentSeq())
    }

    @Test
    fun `acknowledge should add seq to acknowledged set`() = runBlocking {
        // Given & When
        sequenceManager.acknowledge(25)

        // Then - next check should return Duplicate
        val result = sequenceManager.checkSequence(25)
        assertTrue("Acknowledged seq should be duplicate", 
            result is SequenceManager.SequenceResult.Duplicate)
    }

    @Test
    fun `acknowledge should clean up old seqs when exceeding 1000`() = runBlocking {
        // Given & When - acknowledge 1005 seqs
        for (i in 1..1005) {
            sequenceManager.acknowledge(i)
        }

        // Then - old seqs should be cleaned up
        // Seq 1 should no longer be in acknowledged set (cleaned up)
        val result = sequenceManager.checkSequence(1)
        // After cleanup, seq 1 is old (< currentSeq - 1000), so should return Old
        assertTrue("Old seq after cleanup should return Old", 
            result is SequenceManager.SequenceResult.Old)
    }

    @Test
    fun `reset should clear acknowledged seqs`() = runBlocking {
        // Given
        sequenceManager.acknowledge(10)
        sequenceManager.acknowledge(20)
        sequenceManager.acknowledge(30)

        // When
        sequenceManager.reset()

        // Then - all seqs should be forgotten
        val result = sequenceManager.checkSequence(10)
        assertTrue("After reset, old seq should be Ok (not duplicate)", 
            result is SequenceManager.SequenceResult.Ok)
    }

    @Test
    fun `reset should update currentSeq to newSeq`() = runBlocking {
        // Given & When
        sequenceManager.reset(100)

        // Then
        assertEquals(100, sequenceManager.getCurrentSeq())
    }

    // ==================== SequenceListener 测试 ====================

    @Test
    fun `should notify listener on sequence gap`() = runBlocking {
        // Given
        var gapNotified = false
        var notifiedExpected = 0
        var notifiedReceived = 0
        var notifiedMissing: List<Int> = emptyList()

        val listener = object : SequenceManager.SequenceListener {
            override fun onSequenceGap(expected: Int, received: Int, missing: List<Int>) {
                gapNotified = true
                notifiedExpected = expected
                notifiedReceived = received
                notifiedMissing = missing
            }
            override fun onSequenceReset(newSeq: Int) {}
        }
        sequenceManager.addListener(listener)

        // When
        sequenceManager.acknowledge(5)
        sequenceManager.checkSequence(8)

        // Then
        assertTrue("Listener should be notified of gap", gapNotified)
        assertEquals(6, notifiedExpected)
        assertEquals(8, notifiedReceived)
        assertEquals(listOf(6, 7), notifiedMissing)
    }

    @Test
    fun `should notify listener on sequence reset`() = runBlocking {
        // Given
        var resetNotified = false
        var notifiedNewSeq = 0

        val listener = object : SequenceManager.SequenceListener {
            override fun onSequenceGap(expected: Int, received: Int, missing: List<Int>) {}
            override fun onSequenceReset(newSeq: Int) {
                resetNotified = true
                notifiedNewSeq = newSeq
            }
        }
        sequenceManager.addListener(listener)

        // When - trigger reset with large gap
        sequenceManager.acknowledge(5)
        sequenceManager.checkSequence(200) // Gap > maxGapSize (100)

        // Then
        assertTrue("Listener should be notified of reset", resetNotified)
        assertEquals(200, notifiedNewSeq)
    }

    @Test
    fun `removeListener should stop notifications`() = runBlocking {
        // Given
        var notified = false
        val listener = object : SequenceManager.SequenceListener {
            override fun onSequenceGap(expected: Int, received: Int, missing: List<Int>) {
                notified = true
            }
            override fun onSequenceReset(newSeq: Int) {}
        }
        sequenceManager.addListener(listener)
        sequenceManager.removeListener(listener)

        // When
        sequenceManager.acknowledge(5)
        sequenceManager.checkSequence(10)

        // Then
        assertFalse("Removed listener should not be notified", notified)
    }

    @Test
    fun `clearListeners should remove all listeners`() = runBlocking {
        // Given
        var notified = false
        val listener = object : SequenceManager.SequenceListener {
            override fun onSequenceGap(expected: Int, received: Int, missing: List<Int>) {
                notified = true
            }
            override fun onSequenceReset(newSeq: Int) {}
        }
        sequenceManager.addListener(listener)
        sequenceManager.clearListeners()

        // When
        sequenceManager.acknowledge(5)
        sequenceManager.checkSequence(10)

        // Then
        assertFalse("Cleared listeners should not be notified", notified)
    }

    // ==================== EventBuffer 测试 ====================

    @Test
    fun `EventBuffer add should return empty list when buffer not full`() = runBlocking {
        // Given
        val buffer = EventBuffer(maxSize = 10)
        val event = EventFrame(event = "test", seq = 1)

        // When
        val result = buffer.add(event)

        // Then
        assertTrue("Should return empty list when not full", result.isEmpty())
    }

    @Test
    fun `EventBuffer flush should return consecutive events`() = runBlocking {
        // Given
        val buffer = EventBuffer(maxSize = 10)
        val event1 = EventFrame(event = "test", seq = 1)
        val event2 = EventFrame(event = "test", seq = 2)
        val event3 = EventFrame(event = "test", seq = 3)

        buffer.add(event1)
        buffer.add(event2)
        buffer.add(event3)

        // When
        val result = buffer.flush(1)

        // Then
        assertEquals(3, result.size)
        assertEquals(1, result[0].seq)
        assertEquals(2, result[1].seq)
        assertEquals(3, result[2].seq)
    }

    @Test
    fun `EventBuffer flush should stop at gap`() = runBlocking {
        // Given
        val buffer = EventBuffer(maxSize = 10)
        val event1 = EventFrame(event = "test", seq = 1)
        val event3 = EventFrame(event = "test", seq = 3)

        buffer.add(event1)
        buffer.add(event3)

        // When
        val result = buffer.flush(1)

        // Then - should only return event1, stop at gap (missing seq 2)
        assertEquals(1, result.size)
        assertEquals(1, result[0].seq)
    }

    @Test
    fun `EventBuffer clear should remove all events`() = runBlocking {
        // Given
        val buffer = EventBuffer(maxSize = 10)
        buffer.add(EventFrame(event = "test", seq = 1))
        buffer.add(EventFrame(event = "test", seq = 2))

        // When
        buffer.clear()

        // Then
        assertEquals(0, buffer.size())
    }
}
