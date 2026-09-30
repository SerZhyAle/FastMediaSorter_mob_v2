package com.sza.fastmediasorter.domain.input

import org.junit.Assert.assertEquals
import org.junit.Test

/** Unit tests for [CommandGroup.of] - the single command -> group mapping. */
class CommandGroupTest {

    @Test
    fun `each prefix maps to its group`() {
        val expected = mapOf(
            "playback.play_pause" to CommandGroup.PLAYBACK_CORE,
            "navigation.next" to CommandGroup.NAVIGATION,
            "view.zoom_in" to CommandGroup.VIEW_ZOOM,
            "audio.next_track" to CommandGroup.AUDIO_SUBTITLES,
            "system.show_help" to CommandGroup.SYSTEM_UI,
            "sorting.copy" to CommandGroup.SORTING_ACTIONS,
            "browser.refresh" to CommandGroup.BROWSER_ACTIONS,
            "vr.recenter" to CommandGroup.VR_ONLY,
        )

        expected.forEach { (commandId, group) -> assertEquals(commandId, group, CommandGroup.of(commandId)) }
    }

    @Test
    fun `operation slots win over sorting actions`() {
        assertEquals(CommandGroup.OPERATION_SLOTS, CommandGroup.of(CommandId.OPERATION_SLOT_1))
    }

    @Test
    fun `unknown prefix falls back to system ui`() {
        assertEquals(CommandGroup.SYSTEM_UI, CommandGroup.of(CommandId.BLACK_SCREEN))
    }
}
