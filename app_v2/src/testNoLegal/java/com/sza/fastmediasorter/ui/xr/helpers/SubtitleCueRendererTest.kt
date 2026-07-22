package com.sza.fastmediasorter.ui.xr.helpers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S0986: unit coverage for the pure subtitle line-builder ([buildLines]). This is the one piece of
 * the immersive subtitle feature verifiable without a Quest - the OpenXR quad render is on-device
 * only (spec S0986 section 4). Runs under testNoLegal (the flavor that mounts src/vr).
 */
class SubtitleCueRendererTest {

    private val maxChars = SubtitleCueRenderer.MAX_CHARS_PER_LINE

    @Test
    fun empty_text_yields_no_lines() {
        assertEquals(emptyList<String>(), buildLines("", maxChars))
    }

    @Test
    fun whitespace_only_yields_no_lines() {
        assertEquals(emptyList<String>(), buildLines("   \n  ", maxChars))
    }

    @Test
    fun short_text_stays_one_line() {
        assertEquals(listOf("Hello world"), buildLines("Hello world", maxChars))
    }

    @Test
    fun explicit_two_lines_are_preserved_trimmed() {
        assertEquals(listOf("Line one", "Line two"), buildLines("  Line one \n Line two ", maxChars))
    }

    @Test
    fun long_line_wraps_into_two_lines() {
        val text = (1..8).joinToString(" ") { "aaaa" } // 39 chars, one line > 32
        val lines = buildLines(text, maxChars)
        assertEquals(2, lines.size)
        assertTrue(lines.all { it.length <= maxChars })
    }

    @Test
    fun overflow_clamps_to_two_lines_with_ellipsis() {
        val text = (1..40).joinToString(" ") { "word" } // far more than two lines
        val lines = buildLines(text, maxChars)
        assertEquals(SubtitleCueRenderer.MAX_LINES, lines.size)
        assertTrue(lines.last().endsWith(".."))
        assertTrue(lines.all { it.length <= maxChars })
    }

    @Test
    fun over_long_single_word_is_hard_broken_within_budget() {
        val text = "x".repeat(maxChars + 8)
        val lines = buildLines(text, maxChars)
        assertTrue(lines.isNotEmpty())
        assertTrue(lines.all { it.length <= maxChars })
    }
}
