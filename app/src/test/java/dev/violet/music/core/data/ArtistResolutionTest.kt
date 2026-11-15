package dev.violet.music.core.data

import dev.violet.music.core.model.CreditSource
import org.junit.Assert.*
import org.junit.Test

class ArtistResolutionTest {
    @Test
    fun ambiguousPunctuationRemainsIntactWithoutEvidence() {
        for (name in
            listOf(
                "Tyler, The Creator",
                "Earth, Wind & Fire",
                "Simon & Garfunkel",
                "Alan Walker, Ava Max",
            )) {
            assertEquals(listOf(name), resolveArtistCredit(name, emptyList(), emptySet()).names)
        }
    }

    @Test
    fun structuredCreditsHaveMetadataProvenance() {
        val resolved = resolveArtistCredit("original", listOf("Alan Walker", "Ava Max"), emptySet())
        assertEquals(listOf("Alan Walker", "Ava Max"), resolved.names)
        assertEquals(CreditSource.METADATA, resolved.source)
    }

    @Test
    fun supportedDisplaySplitsHaveInferredProvenance() {
        val resolved =
            resolveArtistCredit(
                "Alan Walker, Ava Max",
                listOf("Alan Walker, Ava Max"),
                setOf("alan walker", "ava max"),
            )
        assertEquals(2, resolved.names.size)
        assertEquals(CreditSource.INFERRED, resolved.source)
    }

    @Test
    fun missingConstituentEvidenceDoesNotSplit() {
        assertEquals(
            1,
            resolveArtistCredit("Alan Walker, Ava Max", emptyList(), setOf("alan walker"))
                .names
                .size,
        )
    }

    @Test
    fun normalizationPreservesMeaningfulPunctuation() {
        assertEquals("alan walker", artistKey("  ALAN   WALKER "))
        assertNotEquals(artistKey("AC/DC"), artistKey("ACDC"))
    }
}
