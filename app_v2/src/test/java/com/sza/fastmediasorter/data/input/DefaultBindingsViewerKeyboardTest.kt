package com.sza.fastmediasorter.data.input

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/** The shipped binding table carries the INPUT-PARITY 0.4 VIEWER-KEYBOARD defaults (S4363). */
class DefaultBindingsViewerKeyboardTest {

    private val keyboardByCommand: Map<String, List<String>> by lazy {
        val root = JsonParser.parseString(File("src/main/assets/input/default_bindings.json").readText()).asJsonObject
        root.getAsJsonArray("bindings").associate { element ->
            val entry = element.asJsonObject
            val keys = entry.getAsJsonObject("triggers").getAsJsonArray("keyboard").map { it.asString }
            entry.get("command_id").asString to keys
        }
    }

    private fun commandsFor(trigger: String): List<String> =
        keyboardByCommand.filterValues { trigger in it }.keys.sorted()

    @Test
    fun `viewer keyboard rows resolve to the contract commands`() {
        val expected = mapOf(
            "key:22:0" to "navigation.next_or_seek_forward",
            "key:21:0" to "navigation.previous_or_seek_backward",
            "key:22:1" to "navigation.seek_forward_3s",
            "key:21:1" to "navigation.seek_backward_3s",
            "key:22:4096" to "navigation.seek_forward_30s",
            "key:21:4096" to "navigation.seek_backward_30s",
            "key:20:0" to "navigation.step_forward_ten",
            "key:19:0" to "navigation.step_back_ten",
            "key:93:1" to "navigation.step_forward_hundred",
            "key:92:1" to "navigation.step_back_hundred",
            "key:53:0" to "navigation.random_file",
            "key:72:0" to "playback.speed_up",
            "key:71:0" to "playback.speed_down",
            "key:39:0" to "playback.pause_play",
            "key:19:4096" to "audio.volume_up",
            "key:20:4096" to "audio.volume_down",
            "key:29:0" to "audio.cycle_track",
            "key:46:0" to "view.rotate",
            "key:46:1" to "view.rotate_counter",
            "key:43:0" to "view.rotation_toggle",
            "key:47:0" to "playback.slideshow_start",
            "key:37:0" to "playback.slideshow_random",
            "key:90:0" to "navigation.seek_forward_5s",
            "key:89:0" to "navigation.seek_backward_5s",
        )
        expected.forEach { (trigger, command) ->
            assertEquals("trigger $trigger", listOf(command), commandsFor(trigger))
        }
    }

    @Test
    fun `brackets no longer seek`() {
        assertEquals(listOf("key:90:0"), keyboardByCommand.getValue("navigation.seek_forward_5s"))
        assertEquals(listOf("key:89:0"), keyboardByCommand.getValue("navigation.seek_backward_5s"))
    }
}
