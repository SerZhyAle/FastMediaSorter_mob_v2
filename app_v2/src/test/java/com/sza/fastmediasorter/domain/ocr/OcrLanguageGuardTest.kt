package com.sza.fastmediasorter.domain.ocr

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The guard decides whether a whole overlay is withheld, so each threshold edge is pinned: a guard that fires
 * too eagerly hides every overlay under `auto`, one that never fires paints debris over Cyrillic and CJK text.
 */
class OcrLanguageGuardTest {

    private val debris = "Katanoru nonyyarenn xqzt".repeat(3)

    @Test
    fun `an explicitly chosen language is never refused`() {
        assertFalse(OcrLanguageGuard.shouldRefuse(false, emptyList(), listOf(debris, debris)))
    }

    @Test
    fun `nothing refused is never a language failure`() {
        assertFalse(OcrLanguageGuard.shouldRefuse(true, listOf("Hello world"), emptyList()))
        assertFalse(OcrLanguageGuard.shouldRefuse(true, emptyList(), emptyList()))
    }

    @Test
    fun `a nearly blank picture stays below the char floor`() {
        assertFalse(OcrLanguageGuard.shouldRefuse(true, emptyList(), listOf("abc12", "x#y")))
    }

    @Test
    fun `a page mostly kept is not refused`() {
        val kept = listOf("The quick brown fox jumps over the lazy dog".repeat(3))
        assertFalse(OcrLanguageGuard.shouldRefuse(true, kept, listOf(debris)))
    }

    @Test
    fun `an assumed language whose reading was almost all refused is refused`() {
        assertTrue(OcrLanguageGuard.shouldRefuse(true, listOf("Ok"), listOf(debris, debris)))
    }

    @Test
    fun `punctuation does not count toward either side`() {
        val refusedLetters = "a".repeat(OcrLanguageGuard.MIN_REFUSED_CHARS - 1) + "!!!???...,,,"
        assertFalse(OcrLanguageGuard.shouldRefuse(true, emptyList(), listOf(refusedLetters)))
    }
}
