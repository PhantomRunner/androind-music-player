package dev.violet.music.core.playback

import org.junit.Assert.*
import org.junit.Test

class ListeningAccumulatorTest {
    @Test
    fun longTracksQualifyAtThirtySecondsOnly() {
        val attempt = ListeningAccumulator("fixture", 1000)
        attempt.advance(0, 1000, true, 180_000)
        attempt.advance(29_999, 30_999, true, 180_000)
        assertFalse(attempt.isQualified)
        attempt.advance(30_000, 31_000, false, 180_000)
        assertTrue(attempt.isQualified)
        assertEquals(30_000, attempt.listenedMs)
    }

    @Test
    fun shortTracksQualifyAtHalfTheirDuration() {
        val attempt = ListeningAccumulator("fixture", 0)
        attempt.advance(0, 0, true, 20_000)
        attempt.advance(10_000, 10_000, false, 20_000)
        assertTrue(attempt.isQualified)
    }

    @Test
    fun pauseBufferingAndSuppressionAddNoTime() {
        val attempt = ListeningAccumulator("fixture", 0)
        attempt.advance(0, 0, true, 60_000)
        attempt.advance(5_000, 5_000, false, 60_000)
        attempt.advance(500_000, 500_000, true, 60_000)
        attempt.advance(502_000, 502_000, false, 60_000)
        assertEquals(7_000, attempt.listenedMs)
        assertFalse(attempt.isQualified)
    }

    @Test
    fun checkpointDeltasAreNotRepeatedAndFinalizationStopsCounting() {
        val attempt = ListeningAccumulator("fixture", 0, "attempt")
        attempt.advance(0, 0, true, 60_000)
        attempt.advance(10_000, 10_000, true, 60_000)
        val first = attempt.checkpoint(10_000)
        attempt.advance(20_000, 20_000, false, 60_000)
        val last = attempt.checkpoint(20_000, complete = true, finalize = true)
        attempt.advance(100_000, 100_000, true, 60_000)
        assertEquals(10_000, first.segments.sumOf { it.listenedMs })
        assertEquals(10_000, last.segments.sumOf { it.listenedMs })
        assertEquals(20_000, last.event.listenedMs)
        assertEquals(20_000, attempt.listenedMs)
        assertTrue(last.event.completed)
        assertTrue(last.event.finalized)
        assertNotEquals(first.segments.single().sequence, last.segments.single().sequence)
    }

    @Test
    fun clockChangeDoesNotAddSkippedWallClockTime() {
        val attempt = ListeningAccumulator("fixture", 0)
        attempt.advance(0, 0, true, 0)
        attempt.advance(5_000, 3_605_000, true, 0)
        attempt.advance(10_000, 3_610_000, false, 0)
        val snapshot = attempt.checkpoint(3_610_000)
        assertEquals(10_000, snapshot.event.listenedMs)
        assertEquals(listOf(0L, 3_605_000L), snapshot.segments.map { it.startedAt })
        assertFalse(snapshot.event.qualified)
    }

    @Test
    fun intentionalRepeatedAttemptsHaveDifferentIdentities() {
        val first = ListeningAccumulator("same-track", 0).checkpoint(0)
        val second = ListeningAccumulator("same-track", 100).checkpoint(100)
        assertNotEquals(first.event.id, second.event.id)
    }
}
