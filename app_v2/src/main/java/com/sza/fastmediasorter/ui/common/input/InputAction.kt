package com.sza.fastmediasorter.ui.common.input

/**
 * Direction for focus movement produced by arrow keys and D-pad.
 */
enum class FocusDirection {
    UP, DOWN, LEFT, RIGHT, FIRST, LAST, NEXT, PREVIOUS
}

/**
 * Semantic input action emitted by the shared keyboard/mouse parsers.
 *
 * Screens must bind these actions, not raw [android.view.KeyEvent] key
 * codes. Semantics are screen-agnostic: a surface adapter decides what
 * [OpenCurrent] means (enter folder / start player / apply dialog), but
 * the parser stays uniform.
 *
 * Adding a new action: add it here, extend the parser mapping for the
 * relevant [InputSurface] profile, and wire it in the surface adapter.
 */
sealed interface InputAction {

    // -------- navigation --------
    /** Enter / open currently focused item. */
    data object OpenCurrent : InputAction

    /** Go one level up (Backspace). */
    data object BackOneLevel : InputAction

    /** Close current surface (Escape / F10 / Alt+F4). */
    data object ExitSurface : InputAction

    /** Move focus in [direction]. */
    data class MoveFocus(val direction: FocusDirection) : InputAction

    /** Page navigation; positive delta = forward, negative = backward. */
    data class PageJump(val deltaPages: Int) : InputAction

    // -------- selection --------
    /** Toggle selection of current item (Space / Insert). */
    data object ToggleSelection : InputAction

    /** Select all (Numpad+ / Ctrl+A). */
    data object SelectAll : InputAction

    /** Clear selection (Numpad- / Ctrl+Shift+A / Escape when selection active). */
    data object ClearSelection : InputAction

    /** Invert selection (Numpad*). */
    data object InvertSelection : InputAction

    /** Extend selection one step up / down (Shift+arrow). */
    data object RangeExtendUp : InputAction
    data object RangeExtendDown : InputAction

    // -------- file operations --------
    data object CopySelection : InputAction     // F5 / Ctrl+C
    data object MoveSelection : InputAction     // F6 / Ctrl+X
    data object RenameSelection : InputAction   // F2 / Ctrl+R
    data object DeleteSelection : InputAction   // F8 / Delete / Ctrl+D
    data object CreateFolder : InputAction      // F7 / Ctrl+Shift+N
    data object CreateTextNote : InputAction    // Ctrl+Alt+N
    data object ViewCurrent : InputAction       // F3 / Ctrl+Q
    data object EditCurrent : InputAction       // F4 / Ctrl+E
    data object PasteClipboard : InputAction    // Ctrl+V (future file buffer)
    data object SaveCurrent : InputAction       // Ctrl+S
    data object RefreshCurrent : InputAction    // Ctrl+Shift+R / F5 (resource list)
    data object SearchRequested : InputAction   // Ctrl+F
    data object UndoRequested : InputAction     // Ctrl+Z
    data object RedoRequested : InputAction     // Ctrl+Y
    data object ShowInfo : InputAction          // F3 alt / Ctrl+I in player
    data object ShowContextMenu : InputAction   // F9 / Ctrl+M / ПКМ without coordinates
    data object ShowHelp : InputAction          // F1
    data object ToggleFavourite : InputAction   // middle-click / Bookmark key

    // -------- browse / media-library actions --------
    /** Start slideshow / player from first file without reshuffling. */
    data object StartSlideshow : InputAction    // Ctrl+Enter in Browse (wired via existing onPlayClicked)
    /** Reshuffle and immediately start playback from first reshuffled file (Browse Ctrl+P). */
    data object PlayRandomCurrent : InputAction // Ctrl+P in Browse

    // -------- player specific --------
    data object PlayPause : InputAction          // Space / Enter / media play-pause
    data object ToggleMute : InputAction         // M
    data object ToggleFullscreen : InputAction   // F
    data object ShowPlaybackControls : InputAction
    data object NextTrack : InputAction          // PageDown / MEDIA_NEXT / right arrow in list contexts
    data object PreviousTrack : InputAction      // PageUp / MEDIA_PREVIOUS / left arrow in list contexts
    data class SeekBy(val seconds: Int) : InputAction
    data class ChangeVolume(val delta: Int) : InputAction
    data class FrameStep(val forward: Boolean) : InputAction   // `,` / `.`

    // -------- dialog primary / secondary --------
    data object DialogPrimary : InputAction      // Enter / Ctrl+Enter
    data object DialogDismiss : InputAction      // Escape

    // -------- mouse semantics --------
    /** Raw wheel action; surface decides scroll / zoom / seek. */
    data class ScrollWheel(
        val deltaY: Float,
        val deltaX: Float = 0f,
        val withShift: Boolean = false,
        val withCtrl: Boolean = false,
    ) : InputAction

    /** Right-click with cursor coordinates for anchored menus. */
    data class ShowContextMenuAt(val x: Float, val y: Float) : InputAction

    /** Double-click. Surface decides what "open" means. */
    data object DoubleClickOpen : InputAction

    /** Navigation back (XButton1). */
    data object MouseNavigateBack : InputAction

    /** Navigation forward (XButton2). */
    data object MouseNavigateForward : InputAction
}
