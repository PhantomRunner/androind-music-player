package dev.violet.music.core.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MetadataTest {
    @Test
    fun `absent and platform unknown tags use fallback`() {
        listOf(null, "", "  ", "<unknown>", " <UNKNOWN> ").forEach { assertNull(normalizeMetadata(it)) }
    }

    @Test
    fun `real tags keep punctuation and non Latin text`() {
        assertEquals("Музыка / 夜", normalizeMetadata("  Музыка / 夜  "))
    }
}
