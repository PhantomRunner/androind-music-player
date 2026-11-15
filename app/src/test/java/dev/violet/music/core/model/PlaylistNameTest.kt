package dev.violet.music.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PlaylistNameTest {
    @Test
    fun `names preserve Unicode and strip surrounding whitespace`() {
        assertEquals("Моя музыка 夜", validatedPlaylistName("  Моя музыка 夜  "))
    }
    @Test
    fun `blank and overlong names are rejected without truncation`() {
        assertThrows(IllegalArgumentException::class.java) { validatedPlaylistName("  ") }
        assertThrows(IllegalArgumentException::class.java) { validatedPlaylistName("a".repeat(121)) }
        assertEquals(120, validatedPlaylistName("a".repeat(120)).length)
    }
}
