package com.sza.fastmediasorter.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [PlaybackOrderMode] cycling and prefs (de)serialization.
 */
class PlaybackOrderModeTest {

    @Test
    fun `next cycles through every mode and wraps around`() {
        assertEquals(PlaybackOrderMode.PLAY_THROUGH, PlaybackOrderMode.LOOP_LIST.next())
        assertEquals(PlaybackOrderMode.SHUFFLE, PlaybackOrderMode.PLAY_THROUGH.next())
        assertEquals(PlaybackOrderMode.REPEAT_ONE, PlaybackOrderMode.SHUFFLE.next())
        assertEquals(PlaybackOrderMode.LOOP_LIST, PlaybackOrderMode.REPEAT_ONE.next())
    }

    @Test
    fun `toPrefsString is the lowercased enum name`() {
        assertEquals("loop_list", PlaybackOrderMode.LOOP_LIST.toPrefsString())
        assertEquals("repeat_one", PlaybackOrderMode.REPEAT_ONE.toPrefsString())
    }

    @Test
    fun `fromPrefsString round-trips every mode`() {
        for (mode in PlaybackOrderMode.entries) {
            assertEquals(mode, PlaybackOrderMode.fromPrefsString(mode.toPrefsString()))
        }
    }

    @Test
    fun `fromPrefsString falls back to LOOP_LIST for unknown values`() {
        assertEquals(PlaybackOrderMode.LOOP_LIST, PlaybackOrderMode.fromPrefsString("garbage"))
        assertEquals(PlaybackOrderMode.LOOP_LIST, PlaybackOrderMode.fromPrefsString(""))
        // Case-sensitive: upper-case name does not match the lowercased serialization.
        assertEquals(PlaybackOrderMode.LOOP_LIST, PlaybackOrderMode.fromPrefsString("SHUFFLE"))
    }
}
