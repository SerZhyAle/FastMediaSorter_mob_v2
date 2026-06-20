package com.sza.fastmediasorter.core.input

/**
 * One discrete outcome of an analog-stick sample, produced by [GamepadNavigationTranslator].
 *
 * A plain data model with no Android View/Activity behaviour: the consumer ([com.sza.fastmediasorter.core.ui.BaseActivity])
 * decides how to apply it (move focus, scroll a container). S0508.
 */
sealed interface GamepadNavIntent {

    /**
     * Move focus one step in [direction], a `android.view.View.FOCUS_*` constant
     * (FOCUS_UP / FOCUS_DOWN / FOCUS_LEFT / FOCUS_RIGHT). Left stick → D-pad equivalent.
     */
    data class FocusMove(val direction: Int) : GamepadNavIntent

    /** Scroll the active container by [dx]/[dy] pixels. Right stick → continuous scroll. */
    data class Scroll(val dx: Int, val dy: Int) : GamepadNavIntent
}
