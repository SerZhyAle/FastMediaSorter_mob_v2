package com.sza.fastmediasorter.core.input

import android.view.InputDevice
import android.view.KeyEvent
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pure JVM tests for [TvKeyRouter.route]. The router only reads `action`, `keyCode` and
 * `source` off the [KeyEvent], so the event is faked with mockk and no Robolectric runtime is
 * required (mirrors the approach in `KeyboardShortcutHandlerTest`). [KeyEvent.*] and
 * [InputDevice.SOURCE_*] are plain int constants, safe to reference under the framework stubs.
 *
 * This is the unit guard for the semantic key-routing table that the on-device test round used
 * to be the only check for (S0289 verification-gate repair).
 */
class TvKeyRouterTest {

    private val router = TvKeyRouter()

    private fun key(
        keyCode: Int,
        action: Int = KeyEvent.ACTION_DOWN,
        source: Int = InputDevice.SOURCE_KEYBOARD,
    ): KeyEvent {
        val event = mockk<KeyEvent>()
        every { event.action } returns action
        every { event.keyCode } returns keyCode
        every { event.source } returns source
        return event
    }

    // ── Nav ───────────────────────────────────────────────────────────────────

    @Test
    fun `dpad maps to nav actions`() {
        assertEquals(TvNavAction.Next, router.route(key(KeyEvent.KEYCODE_DPAD_RIGHT)))
        assertEquals(TvNavAction.Prev, router.route(key(KeyEvent.KEYCODE_DPAD_LEFT)))
        assertEquals(TvNavAction.Up, router.route(key(KeyEvent.KEYCODE_DPAD_UP)))
        assertEquals(TvNavAction.Down, router.route(key(KeyEvent.KEYCODE_DPAD_DOWN)))
    }

    @Test
    fun `center enter and numpad enter map to Select`() {
        assertEquals(TvNavAction.Select, router.route(key(KeyEvent.KEYCODE_DPAD_CENTER)))
        assertEquals(TvNavAction.Select, router.route(key(KeyEvent.KEYCODE_ENTER)))
        assertEquals(TvNavAction.Select, router.route(key(KeyEvent.KEYCODE_NUMPAD_ENTER)))
    }

    @Test
    fun `back maps to Back`() {
        assertEquals(TvNavAction.Back, router.route(key(KeyEvent.KEYCODE_BACK)))
    }

    @Test
    fun `tab is intentionally not routed so the framework keeps focus traversal`() {
        assertNull(router.route(key(KeyEvent.KEYCODE_TAB)))
    }

    // ── Media ─────────────────────────────────────────────────────────────────

    @Test
    fun `media transport keys map to media actions`() {
        assertEquals(TvNavAction.PlayPause, router.route(key(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)))
        assertEquals(TvNavAction.PlayPause, router.route(key(KeyEvent.KEYCODE_HEADSETHOOK)))
        assertEquals(TvNavAction.Play, router.route(key(KeyEvent.KEYCODE_MEDIA_PLAY)))
        assertEquals(TvNavAction.Pause, router.route(key(KeyEvent.KEYCODE_MEDIA_PAUSE)))
        assertEquals(TvNavAction.Stop, router.route(key(KeyEvent.KEYCODE_MEDIA_STOP)))
        assertEquals(TvNavAction.MediaNext, router.route(key(KeyEvent.KEYCODE_MEDIA_NEXT)))
        assertEquals(TvNavAction.MediaPrev, router.route(key(KeyEvent.KEYCODE_MEDIA_PREVIOUS)))
        assertEquals(TvNavAction.FastForward, router.route(key(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD)))
        assertEquals(TvNavAction.Rewind, router.route(key(KeyEvent.KEYCODE_MEDIA_REWIND)))
    }

    // ── Hardware ──────────────────────────────────────────────────────────────

    @Test
    fun `hardware keys map to hardware actions`() {
        assertEquals(TvNavAction.VolumeUp, router.route(key(KeyEvent.KEYCODE_VOLUME_UP)))
        assertEquals(TvNavAction.VolumeDown, router.route(key(KeyEvent.KEYCODE_VOLUME_DOWN)))
        assertEquals(TvNavAction.VolumeMute, router.route(key(KeyEvent.KEYCODE_VOLUME_MUTE)))
        assertEquals(TvNavAction.Menu, router.route(key(KeyEvent.KEYCODE_MENU)))
        assertEquals(TvNavAction.Search, router.route(key(KeyEvent.KEYCODE_SEARCH)))
    }

    // ── Guards ────────────────────────────────────────────────────────────────

    @Test
    fun `only ACTION_DOWN is routed`() {
        assertNull(router.route(key(KeyEvent.KEYCODE_DPAD_RIGHT, action = KeyEvent.ACTION_UP)))
    }

    @Test
    fun `gamepad source is ignored so GamepadInputManager keeps ownership`() {
        assertNull(
            router.route(
                key(KeyEvent.KEYCODE_DPAD_RIGHT, source = InputDevice.SOURCE_GAMEPAD)
            )
        )
        assertNull(
            router.route(
                key(KeyEvent.KEYCODE_DPAD_CENTER, source = InputDevice.SOURCE_JOYSTICK)
            )
        )
    }

    @Test
    fun `unrecognised keycode returns null`() {
        assertNull(router.route(key(KeyEvent.KEYCODE_A)))
    }
}
