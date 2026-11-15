package dev.violet.music.core.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class DurationTest {
    @Test
    fun `long recordings show hours and malformed durations do not show negatives`() {
        assertEquals("1:01:01", formatDuration(3_661_000))
        assertEquals("3:07", formatDuration(187_100))
        assertEquals("0:00", formatDuration(-1))
    }
}
