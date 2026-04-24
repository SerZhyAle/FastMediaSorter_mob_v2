package com.sza.fastmediasorter.util

import android.view.KeyEvent
import com.sza.fastmediasorter.ui.common.input.FocusDirection
import com.sza.fastmediasorter.ui.common.input.InputAction
import com.sza.fastmediasorter.ui.common.input.InputSurface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM tests for [KeyboardShortcutHandler]. The tests call the
 * internal [KeyboardShortcutHandler.mapToAction] overload that accepts
 * a raw metaState int, which avoids constructing real
 * [android.view.KeyEvent] instances and thus does not require
 * Robolectric.
 *
 * [KeyEvent.META_*] constants are plain int literals and are safe to
 * reference in unit tests even though the framework stubs are not
 * fully functional.
 */
class KeyboardShortcutHandlerTest {

    private val noop = KeyboardShortcutHandler.ActionDispatcher { true }

    private fun parse(surface: InputSurface, keyCode: Int, meta: Int = 0): InputAction? {
        val handler = KeyboardShortcutHandler(surface, noop)
        return handler.mapToAction(keyCode, meta)
    }

    @Test
    fun `browse F5 maps to copy (NC)`() {
        assertEquals(InputAction.CopySelection, parse(InputSurface.BROWSE, KeyEvent.KEYCODE_F5))
    }

    @Test
    fun `browse Ctrl+C maps to copy`() {
        assertEquals(
            InputAction.CopySelection,
            parse(InputSurface.BROWSE, KeyEvent.KEYCODE_C, KeyEvent.META_CTRL_ON),
        )
    }

    @Test
    fun `browse F6 maps to move`() {
        assertEquals(InputAction.MoveSelection, parse(InputSurface.BROWSE, KeyEvent.KEYCODE_F6))
    }

    @Test
    fun `browse F7 maps to create folder`() {
        assertEquals(InputAction.CreateFolder, parse(InputSurface.BROWSE, KeyEvent.KEYCODE_F7))
    }

    @Test
    fun `browse F8 maps to delete`() {
        assertEquals(InputAction.DeleteSelection, parse(InputSurface.BROWSE, KeyEvent.KEYCODE_F8))
    }

    @Test
    fun `browse Ctrl+Shift+R maps to refresh`() {
        val meta = KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON
        assertEquals(InputAction.RefreshCurrent, parse(InputSurface.BROWSE, KeyEvent.KEYCODE_R, meta))
    }

    @Test
    fun `browse F5 is not refresh anymore`() {
        assertEquals(InputAction.CopySelection, parse(InputSurface.BROWSE, KeyEvent.KEYCODE_F5))
    }

    @Test
    fun `F1 opens help on any surface`() {
        assertEquals(InputAction.ShowHelp, parse(InputSurface.MAIN, KeyEvent.KEYCODE_F1))
        assertEquals(InputAction.ShowHelp, parse(InputSurface.BROWSE, KeyEvent.KEYCODE_F1))
        assertEquals(InputAction.ShowHelp, parse(InputSurface.PLAYER, KeyEvent.KEYCODE_F1))
        assertEquals(InputAction.ShowHelp, parse(InputSurface.SETTINGS, KeyEvent.KEYCODE_F1))
    }

    @Test
    fun `Escape maps to ExitSurface`() {
        assertEquals(InputAction.ExitSurface, parse(InputSurface.MAIN, KeyEvent.KEYCODE_ESCAPE))
    }

    @Test
    fun `Alt+F4 exits`() {
        assertEquals(
            InputAction.ExitSurface,
            parse(InputSurface.MAIN, KeyEvent.KEYCODE_F4, KeyEvent.META_ALT_ON),
        )
    }

    @Test
    fun `player Up is volume up, Down is volume down`() {
        val up = parse(InputSurface.PLAYER, KeyEvent.KEYCODE_DPAD_UP)
        val down = parse(InputSurface.PLAYER, KeyEvent.KEYCODE_DPAD_DOWN)
        assertTrue(up is InputAction.ChangeVolume && up.delta > 0)
        assertTrue(down is InputAction.ChangeVolume && down.delta < 0)
    }

    @Test
    fun `player M mutes`() {
        assertEquals(InputAction.ToggleMute, parse(InputSurface.PLAYER, KeyEvent.KEYCODE_M))
    }

    @Test
    fun `player F toggles fullscreen`() {
        assertEquals(InputAction.ToggleFullscreen, parse(InputSurface.PLAYER, KeyEvent.KEYCODE_F))
    }

    @Test
    fun `player bracket keys seek by 10s`() {
        assertEquals(InputAction.SeekBy(-10), parse(InputSurface.PLAYER, KeyEvent.KEYCODE_LEFT_BRACKET))
        assertEquals(InputAction.SeekBy(10), parse(InputSurface.PLAYER, KeyEvent.KEYCODE_RIGHT_BRACKET))
    }

    @Test
    fun `player Shift+arrow seeks by 60s`() {
        val meta = KeyEvent.META_SHIFT_ON
        assertEquals(InputAction.SeekBy(-60), parse(InputSurface.PLAYER, KeyEvent.KEYCODE_DPAD_LEFT, meta))
        assertEquals(InputAction.SeekBy(60), parse(InputSurface.PLAYER, KeyEvent.KEYCODE_DPAD_RIGHT, meta))
    }

    @Test
    fun `TV red key deletes on file surface`() {
        assertEquals(InputAction.DeleteSelection, parse(InputSurface.BROWSE, KeyEvent.KEYCODE_PROG_RED))
    }

    @Test
    fun `TV green key copies on file surface`() {
        assertEquals(InputAction.CopySelection, parse(InputSurface.BROWSE, KeyEvent.KEYCODE_PROG_GREEN))
    }

    @Test
    fun `TV yellow key moves on file surface`() {
        assertEquals(InputAction.MoveSelection, parse(InputSurface.BROWSE, KeyEvent.KEYCODE_PROG_YELLOW))
    }

    @Test
    fun `TV blue key renames on file surface`() {
        assertEquals(InputAction.RenameSelection, parse(InputSurface.BROWSE, KeyEvent.KEYCODE_PROG_BLUE))
    }

    @Test
    fun `dialog Enter is primary action`() {
        assertEquals(InputAction.DialogPrimary, parse(InputSurface.DIALOG, KeyEvent.KEYCODE_ENTER))
    }

    @Test
    fun `dialog Escape dismisses`() {
        assertEquals(InputAction.DialogDismiss, parse(InputSurface.DIALOG, KeyEvent.KEYCODE_ESCAPE))
    }

    @Test
    fun `dialog Space toggles selection`() {
        assertEquals(InputAction.ToggleSelection, parse(InputSurface.DIALOG, KeyEvent.KEYCODE_SPACE))
    }

    @Test
    fun `navigation Home maps to FIRST`() {
        assertEquals(
            InputAction.MoveFocus(FocusDirection.FIRST),
            parse(InputSurface.BROWSE, KeyEvent.KEYCODE_MOVE_HOME),
        )
    }

    @Test
    fun `navigation End maps to LAST`() {
        assertEquals(
            InputAction.MoveFocus(FocusDirection.LAST),
            parse(InputSurface.BROWSE, KeyEvent.KEYCODE_MOVE_END),
        )
    }

    @Test
    fun `Ctrl+A on file surface selects all`() {
        assertEquals(
            InputAction.SelectAll,
            parse(InputSurface.BROWSE, KeyEvent.KEYCODE_A, KeyEvent.META_CTRL_ON),
        )
    }

    @Test
    fun `Ctrl+Shift+A clears selection`() {
        val meta = KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON
        assertEquals(InputAction.ClearSelection, parse(InputSurface.BROWSE, KeyEvent.KEYCODE_A, meta))
    }

    @Test
    fun `Ctrl+F opens search on every surface`() {
        val meta = KeyEvent.META_CTRL_ON
        assertEquals(InputAction.SearchRequested, parse(InputSurface.MAIN, KeyEvent.KEYCODE_F, meta))
        assertEquals(InputAction.SearchRequested, parse(InputSurface.BROWSE, KeyEvent.KEYCODE_F, meta))
        assertEquals(InputAction.SearchRequested, parse(InputSurface.PLAYER, KeyEvent.KEYCODE_F, meta))
        assertEquals(InputAction.SearchRequested, parse(InputSurface.SETTINGS, KeyEvent.KEYCODE_F, meta))
    }

    @Test
    fun `Ctrl+Z requests undo`() {
        assertEquals(
            InputAction.UndoRequested,
            parse(InputSurface.BROWSE, KeyEvent.KEYCODE_Z, KeyEvent.META_CTRL_ON),
        )
    }

    @Test
    fun `Tab moves focus next`() {
        assertEquals(
            InputAction.MoveFocus(FocusDirection.NEXT),
            parse(InputSurface.MAIN, KeyEvent.KEYCODE_TAB),
        )
    }

    @Test
    fun `Shift+Tab moves focus previous`() {
        assertEquals(
            InputAction.MoveFocus(FocusDirection.PREVIOUS),
            parse(InputSurface.MAIN, KeyEvent.KEYCODE_TAB, KeyEvent.META_SHIFT_ON),
        )
    }

    @Test
    fun `unknown key returns null`() {
        assertNull(parse(InputSurface.MAIN, KeyEvent.KEYCODE_UNKNOWN))
    }

    @Test
    fun `player TAB still moves focus globally`() {
        assertEquals(
            InputAction.MoveFocus(FocusDirection.NEXT),
            parse(InputSurface.PLAYER, KeyEvent.KEYCODE_TAB),
        )
    }

    // ── Phase 2: mapMain (MAIN surface) ──────────────────────────────────────

    @Test
    fun `main bare F2 maps to rename`() {
        assertEquals(InputAction.RenameSelection, parse(InputSurface.MAIN, KeyEvent.KEYCODE_F2))
    }

    @Test
    fun `main bare F3 is unhandled (legacy path)`() {
        assertNull(parse(InputSurface.MAIN, KeyEvent.KEYCODE_F3))
    }

    @Test
    fun `main bare F4 is unhandled (legacy path)`() {
        assertNull(parse(InputSurface.MAIN, KeyEvent.KEYCODE_F4))
    }

    @Test
    fun `main bare F5 maps to copy`() {
        assertEquals(InputAction.CopySelection, parse(InputSurface.MAIN, KeyEvent.KEYCODE_F5))
    }

    @Test
    fun `main F7 stays unsupported when the surface has no create-group flow`() {
        assertNull(parse(InputSurface.MAIN, KeyEvent.KEYCODE_F7))
    }

    @Test
    fun `main F8 maps to delete`() {
        assertEquals(InputAction.DeleteSelection, parse(InputSurface.MAIN, KeyEvent.KEYCODE_F8))
    }

    @Test
    fun `main Ctrl+Shift+R maps to refresh`() {
        val meta = KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON
        assertEquals(InputAction.RefreshCurrent, parse(InputSurface.MAIN, KeyEvent.KEYCODE_R, meta))
    }

    @Test
    fun `main Ctrl+F5 maps to refresh`() {
        assertEquals(
            InputAction.RefreshCurrent,
            parse(InputSurface.MAIN, KeyEvent.KEYCODE_F5, KeyEvent.META_CTRL_ON),
        )
    }

    @Test
    fun `main TV color keys work`() {
        assertEquals(InputAction.DeleteSelection, parse(InputSurface.MAIN, KeyEvent.KEYCODE_PROG_RED))
        assertEquals(InputAction.CopySelection, parse(InputSurface.MAIN, KeyEvent.KEYCODE_PROG_GREEN))
        assertNull(parse(InputSurface.MAIN, KeyEvent.KEYCODE_PROG_YELLOW))
        assertEquals(InputAction.RenameSelection, parse(InputSurface.MAIN, KeyEvent.KEYCODE_PROG_BLUE))
    }

    // ── Phase 2: mapBrowse (BROWSE surface) ──────────────────────────────────

    @Test
    fun `browse Backspace maps to BackOneLevel (not delete)`() {
        assertEquals(InputAction.BackOneLevel, parse(InputSurface.BROWSE, KeyEvent.KEYCODE_DEL))
    }

    @Test
    fun `browse ForwardDelete maps to delete`() {
        assertEquals(InputAction.DeleteSelection, parse(InputSurface.BROWSE, KeyEvent.KEYCODE_FORWARD_DEL))
    }

    @Test
    fun `browse F8 still maps to delete`() {
        assertEquals(InputAction.DeleteSelection, parse(InputSurface.BROWSE, KeyEvent.KEYCODE_F8))
    }
}
