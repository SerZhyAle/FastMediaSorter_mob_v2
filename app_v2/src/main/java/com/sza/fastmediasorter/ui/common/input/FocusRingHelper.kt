package com.sza.fastmediasorter.ui.common.input

import android.view.View

/**
 * Reflects keyboard/D-pad focus as the view's activated state.
 *
 * S0943: the visible focus outline is now applied app-wide in-place by the window-level
 * `FocusDecorationController`, so this helper no longer paints its own foreground ring. It keeps only
 * the `isActivated` toggle that selection-state selectors still key off, so callers (e.g.
 * [com.sza.fastmediasorter.ui.common.FocusManager]) need no change.
 */
object FocusRingHelper {

    /** Reflect [focused] as the view's activated state; the app-wide decorator draws the outline itself. */
    fun setFocused(view: View, focused: Boolean) {
        view.isActivated = focused
    }

    /**
     * Convenience: drive [setFocused] from a focus listener for views that do not flow through
     * [com.sza.fastmediasorter.ui.common.FocusManager].
     */
    fun attach(view: View) {
        val existing = view.onFocusChangeListener
        view.setOnFocusChangeListener { v, hasFocus ->
            setFocused(v, hasFocus)
            existing?.onFocusChange(v, hasFocus)
        }
    }
}
