package com.sza.fastmediasorter.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class LocaleNameCollatorTest {

    @Test
    fun `russian ё sorts beside е not after я`() {
        val sorted = listOf("яблоко", "ёлка", "естественный", "жираф").sortedWith(
            LocaleNameCollator.comparator(Locale("ru"))
        )
        // The JVM ru collation keeps ё adjacent to е (on either side of it); the defect this test
        // pins is the root-locale code-point order that put ё after я.
        val initials = sorted.map { it[0].lowercaseChar() }
        assertTrue("ё must not sort after я: $sorted", initials.indexOf('ё') < initials.indexOf('я'))
        assertTrue("ё must stay beside е: $sorted", (initials.indexOf('ё') - initials.indexOf('е')).abs() == 1)
    }

    @Test
    fun `case difference does not order`() {
        val comparator = LocaleNameCollator.comparator(Locale.ENGLISH)
        assertEquals(0, comparator.compare("Alpha", "alpha"))
    }

    @Test
    fun `accented latin stays beside its base letter`() {
        val comparator = LocaleNameCollator.comparator(Locale.FRENCH)
        val sorted = listOf("zebre", "Éclair", "zoo").sortedWith(comparator)
        assertEquals(listOf("Éclair", "zebre", "zoo"), sorted)
    }

    @Test
    fun `descending is the reverse of ascending`() {
        val names = listOf("ёжик", "Ель", "мак", "Ёлка2")
        val comparator = LocaleNameCollator.comparator(Locale("ru"))
        val ascending = names.sortedWith(comparator)
        val descending = names.sortedWith(comparator.reversed())
        assertEquals(ascending.reversed(), descending)
    }
}

private fun Int.abs(): Int = if (this < 0) -this else this
