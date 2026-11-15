package dev.violet.music.core.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchPatternTest {
    @Test
    fun `blank query uses unfiltered catalogue`() {
        assertEquals("", searchPattern("  "))
    }

    @Test
    fun `SQL wildcard characters and escape character are treated literally`() {
        assertEquals("%100!%!_mix\\live%", searchPattern(" 100%_mix\\live "))
        assertEquals("%Hello!!%", searchPattern("Hello!"))
        assertEquals("%O'Connor%", searchPattern("O'Connor"))
    }
}
