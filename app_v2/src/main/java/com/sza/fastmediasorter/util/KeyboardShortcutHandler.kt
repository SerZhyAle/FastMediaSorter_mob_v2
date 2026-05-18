package com.sza.fastmediasorter.util

import android.view.KeyEvent
import com.sza.fastmediasorter.core.input.KeyBindingManager
import com.sza.fastmediasorter.domain.input.CommandId
import com.sza.fastmediasorter.domain.input.InputSurface as DomainSurface
import com.sza.fastmediasorter.domain.input.InputTrigger
import com.sza.fastmediasorter.domain.input.fromKeyEvent
import com.sza.fastmediasorter.ui.common.input.FocusDirection
import com.sza.fastmediasorter.ui.common.input.InputAction
import com.sza.fastmediasorter.ui.common.input.InputSurface
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
    private val surface: InputSurface,
    private val dispatcher: ActionDispatcher,
    // Optional: when provided, PLAYER/VR_PLAYER surface resolves via the binding system.
    // Pass null (default) for all other surfaces - they retain legacy inline mapping.
    private val keyBindingManager: KeyBindingManager? = null,
) {

    /**
     * Legacy compatibility surface. Kept so [handleKeyEvent] with the
     * old callback style keeps compiling during the migration window.
     */
    interface KeyboardShortcutCallbacks {
        fun onSelectAll() {}
        fun onCopy() {}
        fun onCut() {}
        fun onDelete() {}
        fun onRename() {}
        fun onRefresh() {}
        fun onBack() {}
        fun onEscape() {}
        fun onSpace() {}
        fun onEnter() {}
    }

    /** Screen-specific receiver of semantic actions. */
    fun interface ActionDispatcher {
        /** @return true if the action was consumed. */
        fun dispatch(action: InputAction): Boolean
    }

    /**
     * Secondary constructor that adapts the old callback bag to the new
     * dispatcher. Temporary bridge, remove once all callers migrate.
     */
    constructor(
        callbacks: KeyboardShortcutCallbacks,
    ) : this(
        surface = InputSurface.BROWSE,
        dispatcher = LegacyAdapter(callbacks),
    )

    /**
     * Handle a raw key event. Returns true when the event is consumed.
     *
     * Long-press repeats are ignored for actions where repeat is not
     * meaningful; callers may still observe repeats by subscribing to
     * the dispatcher directly.
     */
    fun handleKeyEvent(keyCode: Int, event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return false

        // Player surfaces: try the binding system first so custom remappings take effect.
        // Falls through to legacy mapToAction when resolver returns null (uncovered keys)
        // or commandIdToAction returns null (surface-irrelevant commands like create_folder).
        if (keyBindingManager != null && isPlayerSurface()) {
            val domainSurface = if (surface == InputSurface.VR_PLAYER) DomainSurface.VR else DomainSurface.PLAYER
            val commandId = keyBindingManager.resolve(InputTrigger.fromKeyEvent(event), domainSurface)
            if (commandId != null) {
                val action = commandIdToAction(commandId)
                if (action != null) {
                    Timber.v("KeyboardShortcutHandler[%s]: %s via binding", surface, action)
                    return dispatcher.dispatch(action)
                }
            }
        }

        val action = mapToAction(keyCode, event.metaState) ?: return false
        Timber.v("KeyboardShortcutHandler[%s]: %s", surface, action)
        return dispatcher.dispatch(action)
    }

    private fun isPlayerSurface() =
        surface == InputSurface.PLAYER || surface == InputSurface.VR_PLAYER

    /**
     * Converts a [CommandId] string to the [InputAction] appropriate for the player surface.
     * Returns null for commands that are irrelevant on this surface (e.g. create_folder),
     * triggering a fallback to the legacy [mapToAction] path.
     *
     * Seek steps preserve pre-migration values: 5s commands map to ±10 s, 30s to ±60 s.
     * These names reflect the intended future step sizes once the UI ships; existing
     * behaviour is kept for this migration wave (Phase 03).
     */
    private fun commandIdToAction(commandId: String): InputAction? = when (commandId) {
        CommandId.PAUSE_PLAY, CommandId.PLAY, CommandId.PAUSE -> InputAction.PlayPause
        CommandId.FRAME_STEP_FORWARD -> InputAction.FrameStep(forward = true)
        CommandId.FRAME_STEP_BACKWARD -> InputAction.FrameStep(forward = false)
        CommandId.SEEK_FORWARD_5S -> InputAction.SeekBy(+10)   // legacy step preserved
        CommandId.SEEK_BACKWARD_5S -> InputAction.SeekBy(-10)
        CommandId.SEEK_FORWARD_30S -> InputAction.SeekBy(+60)  // legacy step preserved
        CommandId.SEEK_BACKWARD_30S -> InputAction.SeekBy(-60)
        CommandId.NEXT_FILE -> InputAction.NextTrack
        CommandId.PREVIOUS_FILE -> InputAction.PreviousTrack
        CommandId.JUMP_START -> InputAction.MoveFocus(FocusDirection.FIRST)
        CommandId.JUMP_END -> InputAction.MoveFocus(FocusDirection.LAST)
        CommandId.FULLSCREEN -> InputAction.ToggleFullscreen
        CommandId.VOLUME_UP -> InputAction.ChangeVolume(+1)
        CommandId.VOLUME_DOWN -> InputAction.ChangeVolume(-1)
        CommandId.MUTE -> InputAction.ToggleMute
        CommandId.TOGGLE_CONTROLS -> InputAction.ShowPlaybackControls
        CommandId.TOGGLE_INFO -> InputAction.ShowInfo
        CommandId.EXIT -> InputAction.ExitSurface
        CommandId.SHOW_HELP -> InputAction.ShowHelp
        CommandId.SEARCH -> InputAction.SearchRequested
        CommandId.UNDO -> InputAction.UndoRequested
        CommandId.REDO -> InputAction.RedoRequested
        CommandId.MOVE -> InputAction.MoveSelection
        CommandId.COPY -> InputAction.CopySelection
        CommandId.DELETE -> InputAction.DeleteSelection
        CommandId.RENAME -> InputAction.RenameSelection
        CommandId.FAVOURITE -> InputAction.ToggleFavourite
        CommandId.FILE_OPS -> InputAction.ShowContextMenu
        CommandId.SAVE -> InputAction.SaveCurrent
        else -> null   // create_folder, paste, stop, speed_*, vr_* → fall through to legacy
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
        // (e.g. dialog Enter/Escape, player volume on Up/Down) win over
        // the global defaults.
        val surfaceAction: InputAction? = when (surface) {
            InputSurface.MAIN -> mapMain(keyCode, ctrl, shift, alt)
            InputSurface.BROWSE -> mapBrowse(keyCode, ctrl, shift, alt)
            InputSurface.DUPLICATES -> mapFileList(keyCode, ctrl, shift, alt)
            InputSurface.PLAYER,
            InputSurface.VR_PLAYER -> mapPlayer(keyCode, ctrl, shift, alt)
            InputSurface.SETTINGS -> mapSettings(keyCode, ctrl, shift, alt)
            InputSurface.DIALOG -> mapDialog(keyCode, ctrl, shift, alt)
            InputSurface.CLOUD_PICKER -> mapCloudPicker(keyCode, ctrl, shift, alt)
            InputSurface.ADD_RESOURCE -> mapAddResource(keyCode, ctrl, shift, alt)
            InputSurface.RESOURCE_EDITOR,
            InputSurface.RECEIVE_SHARE,
            InputSurface.WIDGET_CONFIG,
            InputSurface.WELCOME -> mapNavigation(keyCode, ctrl, shift, alt)
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
        if (!ctrl && !shift && !alt && keyCode == KeyEvent.KEYCODE_SETTINGS) {
            return InputAction.OpenBrowseSettings
        }
        if (!ctrl && !shift && !alt && keyCode == KeyEvent.KEYCODE_MENU) {
            return InputAction.ShowContextMenu
        }
        if (ctrl && !shift && !alt && keyCode == KeyEvent.KEYCODE_COMMA) {
            return InputAction.OpenBrowseSettings
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

    // ---------------- player ----------------

    private fun mapPlayer(
        keyCode: Int,
        ctrl: Boolean,
        shift: Boolean,
        alt: Boolean,
    ): InputAction? {
        // Alt+F4 and similar Alt combos escape to global handling.
        if (alt) return null

        val bare = !ctrl && !shift

        // File operation subset used on the player surface.
        if (bare && keyCode == KeyEvent.KEYCODE_F2) return InputAction.RenameSelection
        if (ctrl && !shift && keyCode == KeyEvent.KEYCODE_R) return InputAction.RenameSelection
        if (bare && keyCode == KeyEvent.KEYCODE_F3) return InputAction.ShowInfo
        if (ctrl && !shift && keyCode == KeyEvent.KEYCODE_I) return InputAction.ShowInfo
        if (bare && keyCode == KeyEvent.KEYCODE_F4) return InputAction.EditCurrent
        if (ctrl && !shift && keyCode == KeyEvent.KEYCODE_E) return InputAction.EditCurrent
        if (bare && keyCode == KeyEvent.KEYCODE_F5) return InputAction.CopySelection
        if (ctrl && !shift && keyCode == KeyEvent.KEYCODE_C) return InputAction.CopySelection
        if (bare && keyCode == KeyEvent.KEYCODE_F6) return InputAction.MoveSelection
        if (ctrl && !shift && keyCode == KeyEvent.KEYCODE_X) return InputAction.MoveSelection
        if (bare && keyCode == KeyEvent.KEYCODE_F7) return InputAction.EditCurrent
        if (ctrl && !shift && keyCode == KeyEvent.KEYCODE_G) return InputAction.EditCurrent
        if (bare && keyCode == KeyEvent.KEYCODE_F8) return InputAction.DeleteSelection
        if (!ctrl && !shift && (keyCode == KeyEvent.KEYCODE_DEL || keyCode == KeyEvent.KEYCODE_FORWARD_DEL)) {
            return InputAction.DeleteSelection
        }
        if (ctrl && !shift && keyCode == KeyEvent.KEYCODE_D) return InputAction.DeleteSelection
        if (bare && keyCode == KeyEvent.KEYCODE_F9) return InputAction.ShowContextMenu
        if (ctrl && !shift && keyCode == KeyEvent.KEYCODE_M) return InputAction.ShowContextMenu
        if (ctrl && !shift && keyCode == KeyEvent.KEYCODE_S) return InputAction.SaveCurrent
        if (ctrl && shift && keyCode == KeyEvent.KEYCODE_R) return InputAction.RefreshCurrent

        // Bare-key playback control. Letter keys must not match when
        // Ctrl is held (Ctrl+F opens search, Ctrl+M is menu etc.).
        if (bare) {
            when (keyCode) {
                KeyEvent.KEYCODE_SPACE,
                KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                KeyEvent.KEYCODE_HEADSETHOOK -> return InputAction.PlayPause

                KeyEvent.KEYCODE_M -> return InputAction.ToggleMute
                KeyEvent.KEYCODE_F -> return InputAction.ToggleFullscreen

                KeyEvent.KEYCODE_DPAD_UP -> return InputAction.ChangeVolume(+1)
                KeyEvent.KEYCODE_DPAD_DOWN -> return InputAction.ChangeVolume(-1)

                KeyEvent.KEYCODE_LEFT_BRACKET -> return InputAction.SeekBy(-10)
                KeyEvent.KEYCODE_RIGHT_BRACKET -> return InputAction.SeekBy(+10)

                KeyEvent.KEYCODE_COMMA -> return InputAction.FrameStep(forward = false)
                KeyEvent.KEYCODE_PERIOD -> return InputAction.FrameStep(forward = true)

                KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> return InputAction.SeekBy(+10)
                KeyEvent.KEYCODE_MEDIA_REWIND -> return InputAction.SeekBy(-10)
            }
        }

        // Shift+arrows: minute seek.
        if (shift && !ctrl && keyCode == KeyEvent.KEYCODE_DPAD_LEFT) return InputAction.SeekBy(-60)
        if (shift && !ctrl && keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) return InputAction.SeekBy(+60)

        // File navigation inside player.
        if (bare && keyCode == KeyEvent.KEYCODE_DPAD_LEFT) return InputAction.PreviousTrack
        if (bare && keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) return InputAction.NextTrack
        if (bare && keyCode == KeyEvent.KEYCODE_PAGE_UP) return InputAction.PreviousTrack
        if (bare && keyCode == KeyEvent.KEYCODE_PAGE_DOWN) return InputAction.NextTrack
        if (bare && keyCode == KeyEvent.KEYCODE_CHANNEL_UP) return InputAction.NextTrack
        if (bare && keyCode == KeyEvent.KEYCODE_CHANNEL_DOWN) return InputAction.PreviousTrack
        if (bare && keyCode == KeyEvent.KEYCODE_MEDIA_NEXT) return InputAction.NextTrack
        if (bare && keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS) return InputAction.PreviousTrack
        if (bare && keyCode == KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD) return InputAction.NextTrack
        if (bare && keyCode == KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD) return InputAction.PreviousTrack

        // Home / End inside documents.
        if (bare && keyCode == KeyEvent.KEYCODE_MOVE_HOME) return InputAction.MoveFocus(FocusDirection.FIRST)
        if (bare && keyCode == KeyEvent.KEYCODE_MOVE_END) return InputAction.MoveFocus(FocusDirection.LAST)

        // Favourite (Bookmark key from TV remotes, Ctrl+B fallback).
        if (bare && keyCode == KeyEvent.KEYCODE_BOOKMARK) return InputAction.ToggleFavourite
        if (ctrl && !shift && keyCode == KeyEvent.KEYCODE_B) return InputAction.ToggleFavourite

        // Playback controls panel.
        if (ctrl && !shift && keyCode == KeyEvent.KEYCODE_P) return InputAction.ShowPlaybackControls

        return null
    }

    // ---------------- settings ----------------

    private fun mapSettings(
        keyCode: Int,
        ctrl: Boolean,
        shift: Boolean,
        alt: Boolean,
    ): InputAction? {
        mapNavigation(keyCode, ctrl, shift, alt)?.let { return it }

        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            return InputAction.MoveFocus(FocusDirection.LEFT)
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            return InputAction.MoveFocus(FocusDirection.RIGHT)
        }
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
        if (ctrl && keyCode == KeyEvent.KEYCODE_ENTER) return InputAction.DialogPrimary
        if (keyCode == KeyEvent.KEYCODE_ESCAPE) return InputAction.DialogDismiss

        if (keyCode == KeyEvent.KEYCODE_SPACE) return InputAction.ToggleSelection
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) return InputAction.MoveFocus(FocusDirection.UP)
        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) return InputAction.MoveFocus(FocusDirection.DOWN)
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
        mapNavigation(keyCode, ctrl, shift, alt)?.let { return it }

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
    ): InputAction? {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> return InputAction.MoveFocus(FocusDirection.UP)
            KeyEvent.KEYCODE_DPAD_DOWN -> return InputAction.MoveFocus(FocusDirection.DOWN)
            KeyEvent.KEYCODE_DPAD_LEFT -> return InputAction.MoveFocus(FocusDirection.LEFT)
            KeyEvent.KEYCODE_DPAD_RIGHT -> return InputAction.MoveFocus(FocusDirection.RIGHT)
            KeyEvent.KEYCODE_MOVE_HOME -> return InputAction.MoveFocus(FocusDirection.FIRST)
            KeyEvent.KEYCODE_MOVE_END -> return InputAction.MoveFocus(FocusDirection.LAST)
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

    /**
     * Bridge that forwards the new semantic actions to the legacy callback bag.
     * Retained only to keep accidental old callers compiling.
     */
    private class LegacyAdapter(
        private val callbacks: KeyboardShortcutCallbacks,
    ) : ActionDispatcher {
        override fun dispatch(action: InputAction): Boolean {
            when (action) {
                InputAction.SelectAll -> callbacks.onSelectAll()
                InputAction.CopySelection -> callbacks.onCopy()
                InputAction.MoveSelection -> callbacks.onCut()
                InputAction.DeleteSelection -> callbacks.onDelete()
                InputAction.RenameSelection -> callbacks.onRename()
                InputAction.RefreshCurrent -> callbacks.onRefresh()
                InputAction.BackOneLevel -> callbacks.onBack()
                InputAction.ExitSurface -> callbacks.onEscape()
                InputAction.ToggleSelection -> callbacks.onSpace()
                InputAction.OpenCurrent -> callbacks.onEnter()
                else -> return false
            }
            return true
        }
    }
}
