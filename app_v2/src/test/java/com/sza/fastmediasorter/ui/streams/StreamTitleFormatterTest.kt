package com.sza.fastmediasorter.ui.streams

import com.sza.fastmediasorter.core.playback.NowPlayingMetadata
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

    // S1142: unified now-playing line (station - Artist - Title), station reuses display() dedup.

    @Test
    fun `nowPlayingLine with null meta shows station alone`() {
        assertEquals("Jazz FM", StreamTitleFormatter.nowPlayingLine("Jazz FM", null))
    }

    @Test
    fun `nowPlayingLine appends artist and title`() {
        val meta = NowPlayingMetadata.parse("Miles Davis - So What")
        assertEquals("Jazz FM - Miles Davis - So What", StreamTitleFormatter.nowPlayingLine("Jazz FM", meta))
    }

    @Test
    fun `nowPlayingLine appends bare title when no artist`() {
        val meta = NowPlayingMetadata.parse("News bulletin")
        assertEquals("Jazz FM - News bulletin", StreamTitleFormatter.nowPlayingLine("Jazz FM", meta))
    }

    @Test
    fun `nowPlayingLine dedups station via display`() {
        val meta = NowPlayingMetadata.parse("A - B")
        assertEquals("Radio X - A - B", StreamTitleFormatter.nowPlayingLine("Radio X (Radio X)", meta))
    }
}
