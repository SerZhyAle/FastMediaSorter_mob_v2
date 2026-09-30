package com.sza.fastmediasorter.ui.settings.helpers

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.BufferedReader
import java.io.StringReader

class GeneralSettingsLogHelperTailTest {

    private fun reader(lineCount: Int) =
        BufferedReader(StringReader((1..lineCount).joinToString("\n") { "line $it" }))

    @Test
    fun `keeps only the last lines when input exceeds the limit`() {
        val tail = readTailLines(reader(1000), 512)

        assertEquals(512, tail.size)
        assertEquals("line 489", tail.first())
        assertEquals("line 1000", tail.last())
    }

    @Test
    fun `returns every line when input is shorter than the limit`() {
        val tail = readTailLines(reader(3), 512)

        assertEquals(listOf("line 1", "line 2", "line 3"), tail)
    }

    @Test
    fun `returns nothing for empty input`() {
        assertEquals(emptyList<String>(), readTailLines(BufferedReader(StringReader("")), 512))
    }
}
