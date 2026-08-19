package com.sza.fastmediasorter.wear.domain.calculator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WearCalculatorHistoryTest {

    @Test
    fun `entries come back newest first`() {
        val history = WearCalculatorHistory()

        history.add(WearCalculatorHistoryEntry("1 + 1", "2"))
        history.add(WearCalculatorHistoryEntry("2 + 2", "4"))

        assertEquals(listOf("2 + 2", "1 + 1"), history.entries().map { it.expression })
    }

    @Test
    fun `the cap drops the oldest entry`() {
        val history = WearCalculatorHistory(maxEntries = 2)

        history.add(WearCalculatorHistoryEntry("a", "1"))
        history.add(WearCalculatorHistoryEntry("b", "2"))
        history.add(WearCalculatorHistoryEntry("c", "3"))

        assertEquals(listOf("c", "b"), history.entries().map { it.expression })
    }

    @Test
    fun `serialize and restore preserve order and content`() {
        val history = WearCalculatorHistory()
        history.add(WearCalculatorHistoryEntry("1 + 1", "2"))
        history.add(WearCalculatorHistoryEntry("6 × 7", "42"))

        val restored = WearCalculatorHistory()
        restored.restore(history.serialize())

        assertEquals(history.entries(), restored.entries())
    }

    @Test
    fun `a malformed line is dropped without losing the rest`() {
        val history = WearCalculatorHistory()

        history.restore(listOf("no separator here", "1 + 1\u001F2"))

        assertEquals(1, history.entries().size)
        assertEquals("1 + 1", history.entries().first().expression)
    }

    @Test
    fun `restore honours the cap`() {
        val history = WearCalculatorHistory(maxEntries = 1)

        history.restore(listOf("a\u001F1", "b\u001F2"))

        assertTrue(history.entries().size == 1)
        assertEquals("a", history.entries().first().expression)
    }
}
