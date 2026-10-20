package com.sza.fastmediasorter.util

import android.view.KeyEvent
import com.sza.fastmediasorter.ui.common.input.FocusDirection
import com.sza.fastmediasorter.ui.common.input.InputAction
import com.sza.fastmediasorter.ui.common.input.UiSurface
import timber.log.Timber

/**
 * Shared keyboard parser. Converts raw [KeyEvent] ACTION_DOWN events into
 * semantic [InputAction]s using a surface profile so file-manager, player
 * and dialog surfaces can share a single parser without leaking raw key
 * codes into business logic.
 *
 * Latin-only policy: letter shortcuts (`M`, `F`, `Y`, `Д` etc.) are
 * matched via [KeyEvent.KEYCODE_*] constants which represent the US
 * layout position, so Cyrillic / Ukrainian layouts are intentionally
 * ignored. See `PLAN/spec_keyboard-mouse-coverage.md` §5.1 and ADR-5 in
 * the impl spec.
 *
 * Android TV color keys map to file operations on file-oriented surfaces
 * only; other surfaces do not react to them.
 */
class KeyboardShortcutHandler(
    private val surface: UiSurface,
    private val dispatcher: ActionDispatcher,
) {

    /** Screen-specific receiver of semantic actions. */
    fun interface ActionDispatcher {
        /** @return true if the action was consumed. */
        fun dispatch(action: InputAction): Boolean
    }

    /**
     * Handle a raw key event. Returns true when the event is consumed.
     *
     * Long-press repeats are ignored for actions where repeat is not
     * meaningful; callers may still observe repeats by subscribing to
     * the dispatcher directly.
     */
    fun handleKeyEvent(keyCode: Int, event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return false

        val action = mapToAction(keyCode, event.metaState) ?: return false
        Timber.v("KeyboardShortcutHandler[%s]: %s", surface, action)
        return dispatcher.dispatch(action)
    }

    /**
     * Test-facing entry point. [metaState] must use `KeyEvent.META_*`
     * bit flags. Visible for unit tests to avoid depending on the
     * Android framework stubs - see `KeyboardShortcutHandlerTest`.
     */
    internal fun mapToAction(keyCode: Int, metaState: Int): InputAction? {
        val ctrl = (metaState and KeyEvent.META_CTRL_ON) != 0
        val shift = (metaState and KeyEvent.META_SHIFT_ON) != 0
        val alt = (metaState and KeyEvent.META_ALT_ON) != 0

        // Surface-specific mapping runs first so per-surface overrides
        // (e.g. dialog Enter/Escape, settings arrows) win over
        // the global defaults.
        val surfaceAction: InputAction? = when (surface) {
            UiSurface.MAIN -> mapMain(keyCode, ctrl, shift, alt)
            UiSurface.BROWSE -> mapBrowse(keyCode, ctrl, shift, alt)
            UiSurface.DUPLICATES -> mapFileList(keyCode, ctrl, shift, alt)
            UiSurface.SETTINGS -> mapSettings(keyCode, ctrl, shift, alt)
            UiSurface.DIALOG -> mapDialog(keyCode, ctrl, shift, alt)
            UiSurface.CLOUD_PICKER -> mapCloudPicker(keyCode, ctrl, shift, alt)
            UiSurface.ADD_RESOURCE -> mapAddResource(keyCode, ctrl, shift, alt)
            UiSurface.RESOURCE_EDITOR,
            UiSurface.RECEIVE_SHARE,
            UiSurface.WIDGET_CONFIG,
            UiSurface.WELCOME,
            UiSurface.SCHEDULED_OPS,
            UiSurface.STREAMS,
            UiSurface.AUTH_SESSIONS,
            UiSurface.KEYBINDING_REMAP,
            UiSurface.WEAR_COMPANION,
            // The player hosts parse keys in PlayerKeyboardHandler through the binding table.
            UiSurface.PLAYER,
            UiSurface.VR_PLAYER -> mapNavigation(keyCode, ctrl, shift, alt)
        }
        if (surfaceAction != null) return surfaceAction

        return mapGlobal(keyCode, ctrl, shift, alt)
    }

    // ---------------- main resource list ----------------

    // Main keeps a narrower contract than Browse: supported file-operation shortcuts
    // are parsed here, while add-resource remains a tiny local fallback in the helper
    // until the shared action set grows an explicit semantic for it.
    private fun mapMain(
        keyCode: Int,
        ctrl: Boolean,
        shift: Boolean,
        alt: Boolean,
    ): InputAction? {
        if (alt) return null

        mapNavigation(keyCode, ctrl, shift, alt)?.let { return it }

        val bare = !ctrl && !shift
        if (bare && keyCode == KeyEvent.KEYCODE_F2) return InputAction.RenameSelection
        if (ctrl && !shift && keyCode == KeyEvent.KEYCODE_R) return InputAction.RenameSelection

        if (bare && keyCode == KeyEvent.KEYCODE_F5) return InputAction.CopySelection
        if (ctrl && !shift && keyCode == KeyEvent.KEYCODE_C) return InputAction.CopySelection

        if (bare && keyCode == KeyEvent.KEYCODE_F8) return InputAction.DeleteSelection
        if (!ctrl && !shift && (keyCode == KeyEvent.KEYCODE_DEL || keyCode == KeyEvent.KEYCODE_FORWARD_DEL)) {
            return InputAction.DeleteSelection
        }
        if (ctrl && !shift && keyCode == KeyEvent.KEYCODE_D) return InputAction.DeleteSelection

        if ((ctrl && shift && keyCode == KeyEvent.KEYCODE_R) ||
            (ctrl && !shift && keyCode == KeyEvent.KEYCODE_F5)
        ) {
            return InputAction.RefreshCurrent
        }

        if (!ctrl && !shift) {
            when (keyCode) {
                KeyEvent.KEYCODE_PROG_RED -> return InputAction.DeleteSelection
                KeyEvent.KEYCODE_PROG_GREEN -> return InputAction.CopySelection
                KeyEvent.KEYCODE_PROG_BLUE -> return InputAction.RenameSelection
            }
        }

        return null
    }

    // ---------------- browse file list ----------------

    // KEYCODE_DEL (Backspace) navigates up in Browse (NC convention); Forward Delete still deletes.
    private fun mapBrowse(
        keyCode: Int,
        ctrl: Boolean,
        shift: Boolean,
        alt: Boolean,
    ): InputAction? {
        if (!ctrl && !shift && !alt && keyCode == KeyEvent.KEYCODE_DEL) {
            return InputAction.BackOneLevel
        }
        // Ctrl+P - Play Random (reshuffle + start player); only meaningful when the button is
        // visible (single-type library), but key is registered unconditionally and the handler
        // in BrowseManagerInitializer shows a toast when the action is unavailable.
        if (ctrl && !shift && !alt && keyCode == KeyEvent.KEYCODE_P) {
            return InputAction.PlayRandomCurrent
        }
        return mapFileList(keyCode, ctrl, shift, alt)
    }

    // ---------------- global ----------------

    private fun mapGlobal(
        keyCode: Int,
        ctrl: Boolean,
        shift: Boolean,
        alt: Boolean,
    ): InputAction? {
        // F1 help on every surface.
        if (keyCode == KeyEvent.KEYCODE_F1) return InputAction.ShowHelp

        // F10 / Alt+F4 exit the host surface.
        if (keyCode == KeyEvent.KEYCODE_F10) return InputAction.ExitSurface
        if (alt && keyCode == KeyEvent.KEYCODE_F4) return InputAction.ExitSurface

        // Escape exits or cancels (dialog / selection / fullscreen).
        if (keyCode == KeyEvent.KEYCODE_ESCAPE) return InputAction.ExitSurface

        // Tab focus movement.
        if (keyCode == KeyEvent.KEYCODE_TAB) {
            return InputAction.MoveFocus(
                if (shift) FocusDirection.PREVIOUS else FocusDirection.NEXT
            )
        }

        // Ctrl+F / Ctrl+Z / Ctrl+Y.
        if (ctrl && !shift && !alt) {
            when (keyCode) {
                KeyEvent.KEYCODE_F -> return InputAction.SearchRequested
                KeyEvent.KEYCODE_Z -> return InputAction.UndoRequested
                KeyEvent.KEYCODE_Y -> return InputAction.RedoRequested
            }
        }

        return null
    }

    // ---------------- file-oriented surfaces ----------------

    private fun mapFileList(
        keyCode: Int,
        ctrl: Boolean,
        shift: Boolean,
        alt: Boolean,
    ): InputAction? {
        // Let Alt+anything escape to global (Alt+F4 exit).
        if (alt) return null

        mapNavigation(keyCode, ctrl, shift, alt)?.let { return it }

        // Selection.
        if (!ctrl && keyCode == KeyEvent.KEYCODE_INSERT) return InputAction.ToggleSelection
        if (keyCode == KeyEvent.KEYCODE_SPACE && !ctrl && !shift) {
            return InputAction.ToggleSelection
        }
        if (keyCode == KeyEvent.KEYCODE_NUMPAD_ADD && !ctrl) return InputAction.SelectAll
        if (ctrl && keyCode == KeyEvent.KEYCODE_A && !shift) return InputAction.SelectAll
        if (keyCode == KeyEvent.KEYCODE_NUMPAD_SUBTRACT && !ctrl) return InputAction.ClearSelection
        if (ctrl && shift && keyCode == KeyEvent.KEYCODE_A) return InputAction.ClearSelection
        if (keyCode == KeyEvent.KEYCODE_NUMPAD_MULTIPLY) return InputAction.InvertSelection
        if (shift && keyCode == KeyEvent.KEYCODE_DPAD_UP) return InputAction.RangeExtendUp
        if (shift && keyCode == KeyEvent.KEYCODE_DPAD_DOWN) return InputAction.RangeExtendDown

        // File operations: NC F-keys + Windows Ctrl+duplicates.
        // Bare F-keys must not match when Ctrl/Shift are held (those
        // combos are reserved for future assignments).
        val bare = !ctrl && !shift
        if (bare && keyCode == KeyEvent.KEYCODE_F2) return InputAction.RenameSelection
        if (ctrl && !shift && keyCode == KeyEvent.KEYCODE_R) return InputAction.RenameSelection

        if (bare && keyCode == KeyEvent.KEYCODE_F3) return InputAction.ViewCurrent
        if (ctrl && !shift && keyCode == KeyEvent.KEYCODE_Q) return InputAction.ViewCurrent

        if (bare && keyCode == KeyEvent.KEYCODE_F4) return InputAction.EditCurrent
        if (ctrl && !shift && keyCode == KeyEvent.KEYCODE_E) return InputAction.EditCurrent

        if (bare && keyCode == KeyEvent.KEYCODE_F5) return InputAction.CopySelection
        if (ctrl && !shift && keyCode == KeyEvent.KEYCODE_C) return InputAction.CopySelection

        if (bare && keyCode == KeyEvent.KEYCODE_F6) return InputAction.MoveSelection
        if (ctrl && !shift && keyCode == KeyEvent.KEYCODE_X) return InputAction.MoveSelection

        if (bare && keyCode == KeyEvent.KEYCODE_F7) return InputAction.CreateFolder
        if (ctrl && shift && keyCode == KeyEvent.KEYCODE_N) return InputAction.CreateFolder

        if (bare && keyCode == KeyEvent.KEYCODE_F8) return InputAction.DeleteSelection
        if (!ctrl && !shift && (keyCode == KeyEvent.KEYCODE_DEL || keyCode == KeyEvent.KEYCODE_FORWARD_DEL)) {
            return InputAction.DeleteSelection
        }
        if (ctrl && !shift && keyCode == KeyEvent.KEYCODE_D) return InputAction.DeleteSelection

        if (bare && keyCode == KeyEvent.KEYCODE_F9) return InputAction.ShowContextMenu
        if (ctrl && !shift && keyCode == KeyEvent.KEYCODE_M) return InputAction.ShowContextMenu

        // Refresh moved off bare F5 in Browse, but keep both approved Ctrl variants.
        if ((ctrl && shift && keyCode == KeyEvent.KEYCODE_R) ||
            (ctrl && !shift && keyCode == KeyEvent.KEYCODE_F5)
        ) {
            return InputAction.RefreshCurrent
        }

        // Clipboard paste placeholder.
        if (ctrl && !shift && keyCode == KeyEvent.KEYCODE_V) return InputAction.PasteClipboard

        // Android TV color keys map to file operations.
        if (!ctrl && !shift) {
            when (keyCode) {
                KeyEvent.KEYCODE_PROG_RED -> return InputAction.DeleteSelection
                KeyEvent.KEYCODE_PROG_GREEN -> return InputAction.CopySelection
                KeyEvent.KEYCODE_PROG_YELLOW -> return InputAction.MoveSelection
                KeyEvent.KEYCODE_PROG_BLUE -> return InputAction.RenameSelection
            }
        }

        return null
    }

    // ---------------- settings ----------------

    private fun mapSettings(
        keyCode: Int,
        ctrl: Boolean,
        shift: Boolean,
        alt: Boolean,
    ): InputAction? {
        // S0289: native focus traversal on the settings form. Bare D-pad arrows are NOT
        // converted to MoveFocus here, so the framework moves focus and a focused Spinner /
        // dropdown receives the arrows itself (otherwise language / theme were not selectable
        // by D-pad). Tab switching stays on PageUp / PageDown (handled as PageJump downstream).
        mapNavigation(keyCode, ctrl, shift, alt, arrowFocus = false)?.let { return it }

        if (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_SPACE) {
            return InputAction.OpenCurrent
        }
        return null
    }

    // ---------------- dialog ----------------

    private fun mapDialog(
        keyCode: Int,
        ctrl: Boolean,
        shift: Boolean,
        alt: Boolean,
    ): InputAction? {
        if (keyCode == KeyEvent.KEYCODE_ENTER) return InputAction.DialogPrimary
        if (keyCode == KeyEvent.KEYCODE_ESCAPE) return InputAction.DialogDismiss

        if (keyCode == KeyEvent.KEYCODE_SPACE) return InputAction.ToggleSelection
        // S2894 / S0289: bare D-pad arrows are NOT converted to MoveFocus here, allowing native framework
        // focus traversal on ACTION_DOWN so DialogKeyboardDelegate does not double-step focus on ACTION_UP.
        if (keyCode == KeyEvent.KEYCODE_NUMPAD_ADD) return InputAction.SelectAll
        if (ctrl && keyCode == KeyEvent.KEYCODE_A && !shift) return InputAction.SelectAll
        if (keyCode == KeyEvent.KEYCODE_NUMPAD_SUBTRACT) return InputAction.ClearSelection
        if (ctrl && shift && keyCode == KeyEvent.KEYCODE_A) return InputAction.ClearSelection

        return null
    }

    // ---------------- cloud picker ----------------

    private fun mapCloudPicker(
        keyCode: Int,
        ctrl: Boolean,
        shift: Boolean,
        alt: Boolean,
    ): InputAction? {
        if (!ctrl && !shift && !alt && keyCode == KeyEvent.KEYCODE_DEL) {
            return InputAction.BackOneLevel
        }

        mapNavigation(keyCode, ctrl, shift, alt)?.let { return it }

        if ((ctrl && shift && keyCode == KeyEvent.KEYCODE_R) ||
            (ctrl && !shift && keyCode == KeyEvent.KEYCODE_F5)
        ) {
            return InputAction.RefreshCurrent
        }

        return null
    }

    // ---------------- add resource ----------------

    private fun mapAddResource(
        keyCode: Int,
        ctrl: Boolean,
        shift: Boolean,
        alt: Boolean,
    ): InputAction? {
        // S0289: native focus traversal on the add-resource form (arrowFocus = false), so
        // bare D-pad arrows traverse fields / type cards via the framework instead of the
        // manual focusSearch path that mis-navigated weighted horizontal rows.
        mapNavigation(keyCode, ctrl, shift, alt, arrowFocus = false)?.let { return it }

        if (!ctrl && !shift && !alt && keyCode == KeyEvent.KEYCODE_SPACE) {
            return InputAction.OpenCurrent
        }

        return null
    }

    // ---------------- shared navigation ----------------

    private fun mapNavigation(
        keyCode: Int,
        ctrl: Boolean,
        @Suppress("UNUSED_PARAMETER") shift: Boolean,
        @Suppress("UNUSED_PARAMETER") alt: Boolean,
        arrowFocus: Boolean = true,
    ): InputAction? {
        // Form surfaces (Settings / AddResource) pass arrowFocus = false so bare D-pad arrows
        // and Home / End fall through to native framework focus traversal instead of being
        // converted to MoveFocus. List surfaces keep the default (true). S0289.
        if (arrowFocus) {
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> return InputAction.MoveFocus(FocusDirection.UP)
                KeyEvent.KEYCODE_DPAD_DOWN -> return InputAction.MoveFocus(FocusDirection.DOWN)
                KeyEvent.KEYCODE_DPAD_LEFT -> return InputAction.MoveFocus(FocusDirection.LEFT)
                KeyEvent.KEYCODE_DPAD_RIGHT -> return InputAction.MoveFocus(FocusDirection.RIGHT)
                KeyEvent.KEYCODE_MOVE_HOME -> return InputAction.MoveFocus(FocusDirection.FIRST)
                KeyEvent.KEYCODE_MOVE_END -> return InputAction.MoveFocus(FocusDirection.LAST)
            }
        }
        when (keyCode) {
            KeyEvent.KEYCODE_PAGE_UP -> return InputAction.PageJump(-1)
            KeyEvent.KEYCODE_PAGE_DOWN -> return InputAction.PageJump(+1)
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_DPAD_CENTER -> return InputAction.OpenCurrent
            KeyEvent.KEYCODE_BACK -> return InputAction.BackOneLevel
        }
        if (!ctrl && keyCode == KeyEvent.KEYCODE_PLUS) return InputAction.ToggleSelection
        if (ctrl && keyCode == KeyEvent.KEYCODE_ENTER) return InputAction.OpenCurrent
        return null
    }
}
