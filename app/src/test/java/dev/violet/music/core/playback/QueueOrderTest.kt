package dev.violet.music.core.playback

import org.junit.Assert.*
import org.junit.Test

class QueueOrderTest {
    @Test
    fun occurrencesMoveWithoutCollapsingDuplicateTracks() {
        assertEquals(
            listOf("a-two", "b", "a-one"),
            movedOrder(listOf("a-one", "a-two", "b"), "a-one", "b"),
        )
        assertEquals(
            listOf("b", "a-one", "a-two"),
            movedOrder(listOf("a-one", "a-two", "b"), "b", "a-one"),
        )
    }

    @Test
    fun staleIdentitiesDoNotChangeTheQueue() {
        assertNull(movedOrder(listOf("a", "b"), "missing", "a"))
        assertNull(movedOrder(listOf("a", "b"), "a", "missing"))
        assertEquals(listOf("a", "b"), movedOrder(listOf("a", "b"), "a", "a"))
    }
}
