package com.sza.fastmediasorter.core.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.StringReader

/**
 * S3842 - the streaming counter must give the same numbers as the whole-String formula it replaced.
 */
class TextStatsCounterTest {

    private fun legacy(text: String) = TextStatsCounter.TextStats(
        lines = text.lines().size,
        words = text.split(Regex("\\s+")).filter { it.isNotBlank() }.size,
        chars = text.length
    )

    @Test
    fun `matches the former whole-string counts`() {
        listOf(
            "",
            "one",
            "  leading and trailing  ",
            "a\nb\r\nc\rd\n",
            "tabs\tand\u000Bvertical\u000Cfeed",
            "\r\n\r\n",
            "word\n\nword"
        ).forEach { text ->
            assertEquals(text, legacy(text), TextStatsCounter.count(StringReader(text)))
        }
    }

    @Test
    fun `counts across buffer boundaries`() {
        val text = "ab\r\n".repeat(5000) + "tail"

        assertEquals(legacy(text), TextStatsCounter.count(StringReader(text)))
    }
}
