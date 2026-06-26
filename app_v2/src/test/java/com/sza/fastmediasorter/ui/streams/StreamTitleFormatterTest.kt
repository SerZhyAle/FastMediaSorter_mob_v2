package com.sza.fastmediasorter.ui.streams

import org.junit.Assert.assertEquals
import org.junit.Test

/** S0691: only a whole-head duplicate parenthetical is suppressed; distinguishing suffixes are kept. */
class StreamTitleFormatterTest {

    @Test
    fun `suppresses identical parenthetical`() {
        assertEquals("1001 Noites", StreamTitleFormatter.display("1001 Noites (1001 Noites)"))
    }

    @Test
    fun `suppression is case-insensitive and trims`() {
        assertEquals("Radio X", StreamTitleFormatter.display("Radio X (radio x)"))
        assertEquals("Radio X", StreamTitleFormatter.display("Radio X  ( Radio X )"))
    }

    @Test
    fun `keeps distinguishing parenthetical`() {
        assertEquals("Euronews (FR)", StreamTitleFormatter.display("Euronews (FR)"))
        assertEquals("BBC (News)", StreamTitleFormatter.display("BBC (News)"))
    }

    @Test
    fun `keeps title without parenthetical`() {
        assertEquals("Jazz FM", StreamTitleFormatter.display("Jazz FM"))
    }

    @Test
    fun `only a whole-head duplicate is suppressed`() {
        // head = "Foo (Bar)" != trailing paren "Foo" -> unchanged
        assertEquals("Foo (Bar) (Foo)", StreamTitleFormatter.display("Foo (Bar) (Foo)"))
        // nested parentheses are left untouched (conservative: only a clean `head (head)` collapses)
        assertEquals("Foo (Bar) (Foo (Bar))", StreamTitleFormatter.display("Foo (Bar) (Foo (Bar))"))
    }
}
